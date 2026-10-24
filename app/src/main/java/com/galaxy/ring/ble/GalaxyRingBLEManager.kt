package com.galaxy.ring.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.ScreenBrightness
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.StepData
import com.galaxy.ring.data.WorkoutSession
import com.galaxy.ring.debug.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Manages low-level Bluetooth LE communication with the SR16 smart ring using the RWfit protocol.
 *
 * Hard rules enforced:
 * 1. ONE frame format only: AB | flag | len(2 BE) | crc16(2 BE) | payload
 * 2. Only service 0xA00A: write WITH response to 0xB002 (chunk to MTU-3); notify only from 0xB003.
 * 3. Single-threaded command queue + state machine.
 * 4. CCCD enabled on 0xB003 once; wait for onDescriptorWrite before sending commands.
 * 5. Automatic ACK (flag 0x11) sent for incoming command frames (flag 0x01).
 * 6. Pushes (flag 0x21) parsed without ACK or completing pending command.
 */
class GalaxyRingBLEManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {

    private val tag = "GalaxyRingBLE"

    // Target GATT Characteristics (0xA00A only)
    var writeCharacteristic: BluetoothGattCharacteristic? = null
        private set
    var notifyCharacteristic: BluetoothGattCharacteristic? = null
        private set

    // Diagnostic GATT flags
    var foundService0xA00A: Boolean = false
        private set
    var foundWrite0xB002: Boolean = false
        private set
    var foundNotify0xB003: Boolean = false
        private set
    var isNotificationEnabled: Boolean = false
        private set

    // One-shot lifecycle flags per connection
    @Volatile var servicesConfigured: Boolean = false
    @Volatile var cccdEnabled: Boolean = false
    @Volatile var initStarted: Boolean = false

    // Telemetry & Diagnostic state
    var rxCountSinceConnect: Int = 0
        private set

    private val _rxCountFlow = MutableStateFlow(0)
    val rxCountFlow: StateFlow<Int> = _rxCountFlow.asStateFlow()

    var lastTxHex: String? = null
        private set
    var lastRxHex: String? = null
        private set
    var lastRxTimestamp: Long = 0L
        private set
    var lastRxBytesCount: Int = 0
        private set

    // Diagnostic tracking for Admin panel
    var lastFailedInitStep: String? = null
        private set
    val workingVariantName: String = "RWfit Standard (AB 01 len2 crc2)"

    // Negotiated MTU (default 23, updated on MTU callback)
    var negotiatedMtu: Int = 23

    // RX buffer for characteristic 0xB003 (max 8 KiB)
    private val rxBufferLock = Any()
    private var rxBuffer = ByteArray(0)

    // Single-threaded command queue & serialization
    private val commandMutex = Mutex()
    private var pendingDescriptorDeferred: CompletableDeferred<Int>? = null
    private var pendingWriteCharDeferred: CompletableDeferred<Int>? = null
    private var pendingCommandKey: Int? = null
    private var pendingCommandDeferred: CompletableDeferred<Protocol.Frame>? = null

    // Measurement active tracker
    @Volatile var isMeasuringActive: Boolean = false
        private set
    @Volatile var activeMeasureType: Byte? = null
        private set

    // Measurement completion flow (emits completed metric type: 0x03 HR or 0x09 SpO2, etc.)
    private val _measureCompletedFlow = MutableSharedFlow<Byte>(extraBufferCapacity = 16)
    val measureCompletedFlow: SharedFlow<Byte> = _measureCompletedFlow.asSharedFlow()

    // Generic parsed frame broadcast flow for metric probes (HRV, Stress)
    private val _incomingFrameFlow = MutableSharedFlow<Protocol.Frame>(extraBufferCapacity = 32)
    val incomingFrameFlow: SharedFlow<Protocol.Frame> = _incomingFrameFlow.asSharedFlow()

    // Initialization State
    sealed class InitState {
        data object Idle : InitState()
        data class InProgress(val currentStep: Int, val totalSteps: Int, val stepName: String) : InitState()
        data object Success : InitState()
        data class Failed(val error: String) : InitState()
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Idle)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    // Biometric data flows
    private val _heartRateFlow = MutableSharedFlow<HeartRateSample>(extraBufferCapacity = 16)
    val heartRateFlow: SharedFlow<HeartRateSample> = _heartRateFlow.asSharedFlow()

    private val _spo2Flow = MutableSharedFlow<OxygenSaturationSample>(extraBufferCapacity = 16)
    val spo2Flow: SharedFlow<OxygenSaturationSample> = _spo2Flow.asSharedFlow()

    private val _stepsFlow = MutableSharedFlow<StepData>(extraBufferCapacity = 16)
    val stepsFlow: SharedFlow<StepData> = _stepsFlow.asSharedFlow()

    private val _sleepFlow = MutableSharedFlow<SleepSession>(extraBufferCapacity = 16)
    val sleepFlow: SharedFlow<SleepSession> = _sleepFlow.asSharedFlow()

    private val _workoutFlow = MutableSharedFlow<List<WorkoutSession>>(extraBufferCapacity = 16)
    val workoutFlow: SharedFlow<List<WorkoutSession>> = _workoutFlow.asSharedFlow()

    // Heart rate tracking for range and spike filtering (30..240 bpm, spike delta > 40 when interval < 2s)
    private var lastValidHrBpm: Int? = null
    private var lastValidHrTimestamp: Long = 0L

    // SpO2 tracking for range and spike filtering (70..100%, spike delta > 15% when interval < 2s)
    private var lastValidSpo2Percentage: Float? = null
    private var lastValidSpo2Timestamp: Long = 0L

    /**
     * Resets session telemetry counters when a new device connection is established.
     */
    fun resetSession() {
        rxCountSinceConnect = 0
        _rxCountFlow.value = 0
        AppLog.rxCountSinceConnect = 0
        lastTxHex = null
        lastRxHex = null
        lastRxTimestamp = 0L
        lastRxBytesCount = 0
        isMeasuringActive = false
        activeMeasureType = null
        lastValidHrBpm = null
        lastValidHrTimestamp = 0L
        lastValidSpo2Percentage = null
        lastValidSpo2Timestamp = 0L
        _initState.value = InitState.Idle

        servicesConfigured = false
        cccdEnabled = false
        initStarted = false
        isNotificationEnabled = false
        foundService0xA00A = false
        foundWrite0xB002 = false
        foundNotify0xB003 = false
        writeCharacteristic = null
        notifyCharacteristic = null

        synchronized(rxBufferLock) {
            rxBuffer = ByteArray(0)
        }

        pendingDescriptorDeferred?.cancel()
        pendingDescriptorDeferred = null
        pendingWriteCharDeferred?.cancel()
        pendingWriteCharDeferred = null
        pendingCommandDeferred?.cancel()
        pendingCommandDeferred = null
        pendingCommandKey = null

        AppLog.workingVariant = workingVariantName
        AppLog.i(tag, "Session reset: RWfit single-frame state machine initialized")
    }

    fun markNotificationEnabled(enabled: Boolean) {
        if (enabled) {
            isNotificationEnabled = true
            cccdEnabled = true
        }
    }

    fun getLastRxSince(sinceTimestamp: Long): Pair<Int, String>? {
        return if (lastRxTimestamp >= sinceTimestamp && lastRxHex != null) {
            Pair(lastRxBytesCount, lastRxHex!!)
        } else null
    }

    fun onDescriptorWriteCompleted(descriptor: BluetoothGattDescriptor, status: Int) {
        AppLog.d(tag, "onDescriptorWriteCompleted: ${descriptor.uuid}, status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            isNotificationEnabled = true
            cccdEnabled = true
        }
        pendingDescriptorDeferred?.complete(status)
    }

    fun onCharacteristicWriteCompleted(characteristic: BluetoothGattCharacteristic, status: Int) {
        AppLog.d(tag, "onCharacteristicWriteCompleted: ${characteristic.uuid}, status=$status")
        pendingWriteCharDeferred?.complete(status)
    }

    // =========================================================================
    // 1. Service Discovery & Safe B003 Notification Setup
    // =========================================================================

    /**
     * Discovers 0xA00A service, binds 0xB002 write and 0xB003 notify.
     * Enables CCCD only on 0xB003 once and waits for onDescriptorWrite callback.
     * If already configured, returns immediately without re-triggering descriptor writes.
     */
    @SuppressLint("MissingPermission")
    suspend fun setupNotificationObserver(gatt: BluetoothGatt): Boolean {
        if (servicesConfigured && (cccdEnabled || isNotificationEnabled)) {
            AppLog.i(tag, "setupNotificationObserver: Service 0xA00A and CCCD already configured for this connection. Skipping duplicate setup.")
            return true
        }

        return try {
            val deviceAddr = try { gatt.device?.address ?: "Unknown" } catch (_: Exception) { "Unknown" }
            AppLog.i(tag, "Configuring RWfit service 0xA00A for $deviceAddr")

            if (writeCharacteristic == null || notifyCharacteristic == null) {
                foundService0xA00A = false
                foundWrite0xB002 = false
                foundNotify0xB003 = false

                for (service in gatt.services) {
                    if (Protocol.matchesShortUuid(service.uuid, "A00A")) {
                        foundService0xA00A = true
                        for (ch in service.characteristics) {
                            if (Protocol.matchesShortUuid(ch.uuid, "B002")) {
                                writeCharacteristic = ch
                                foundWrite0xB002 = true
                                logCharProperties("0xB002", ch)
                            }
                            if (Protocol.matchesShortUuid(ch.uuid, "B003")) {
                                notifyCharacteristic = ch
                                foundNotify0xB003 = true
                                logCharProperties("0xB003", ch)
                            }
                        }
                    }
                }
            }

            if (!foundService0xA00A || writeCharacteristic == null || notifyCharacteristic == null) {
                val err = "Service 0xA00A or characteristics (B002/B003) missing on ring"
                AppLog.e(tag, err)
                lastFailedInitStep = "GATT Discovery: $err"
                return false
            }

            // Enable notify on 0xB003 once; wait for onDescriptorWrite success
            val targetNotify = notifyCharacteristic!!
            if (!cccdEnabled && !isNotificationEnabled) {
                AppLog.i(tag, "Subscribing CCCD on 0xB003 (${targetNotify.uuid})...")
                val cccdOk = enableB003Notification(gatt, targetNotify)
                if (!cccdOk) {
                    if (isNotificationEnabled || rxCountSinceConnect > 0) {
                        AppLog.w(tag, "CCCD returned false but notifications/RX already active. Continuing.")
                        cccdEnabled = true
                        servicesConfigured = true
                        return true
                    }
                    AppLog.e(tag, "CCCD descriptor write failed or timed out on 0xB003")
                    lastFailedInitStep = "CCCD Enable Failed"
                    return false
                }
                isNotificationEnabled = true
                cccdEnabled = true
            } else {
                AppLog.i(tag, "CCCD already enabled on 0xB003; skipping redundant descriptor write.")
            }

            servicesConfigured = true
            AppLog.i(tag, "CCCD on 0xB003 write CONFIRMED. Ready for RWfit init sequence.")
            true
        } catch (e: Exception) {
            AppLog.e(tag, "Exception during setupNotificationObserver: ${e.message}", e)
            lastFailedInitStep = "Notification Setup Exception: ${e.message}"
            false
        }
    }

    private fun logCharProperties(label: String, ch: BluetoothGattCharacteristic) {
        val p = ch.properties
        val w = (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val wnr = (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val n = (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        val i = (p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        AppLog.i(
            tag,
            "Char $label [${ch.uuid}] properties=0x${Integer.toHexString(p)} (WRITE=$w, WNR=$wnr, NOTIFY=$n, IND=$i)"
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableB003Notification(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic
    ): Boolean {
        if (cccdEnabled || isNotificationEnabled) {
            AppLog.i(tag, "enableB003Notification: CCCD already active on ${char.uuid}, skipping writeDescriptor.")
            return true
        }
        return try {
            val localDeferred = CompletableDeferred<Int>()
            pendingDescriptorDeferred = localDeferred

            val setSuccess = gatt.setCharacteristicNotification(char, true)
            if (!setSuccess) {
                AppLog.e(tag, "setCharacteristicNotification returned false for ${char.uuid}")
                pendingDescriptorDeferred = null
                return false
            }

            val descriptor = char.getDescriptor(Protocol.CCCD_UUID)
            if (descriptor == null) {
                AppLog.e(tag, "Descriptor 0x2902 not found on ${char.uuid}")
                pendingDescriptorDeferred = null
                return false
            }

            val descBytes = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeDescriptor(descriptor, descBytes)
                status == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = descBytes
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }

            if (!writeSuccess) {
                AppLog.e(tag, "writeDescriptor rejected by Bluetooth stack for ${char.uuid}")
                pendingDescriptorDeferred = null
                return false
            }

            val writeStatus = withTimeoutOrNull(3000L) {
                localDeferred.await()
            }
            pendingDescriptorDeferred = null

            val success = writeStatus == BluetoothGatt.GATT_SUCCESS
            AppLog.i(tag, "0xB003 CCCD write result: status=$writeStatus (success=$success)")
            success
        } catch (e: Exception) {
            AppLog.e(tag, "Error enabling 0xB003 notification: ${e.message}", e)
            pendingDescriptorDeferred = null
            false
        }
    }

    // =========================================================================
    // 2. RX Path: Buffering, Frame Assembly, CRC Verification & Dispatch
    // =========================================================================

    /**
     * Called whenever raw notification bytes arrive from characteristic 0xB003.
     */
    fun onNotificationReceived(characteristic: BluetoothGattCharacteristic, data: ByteArray, gatt: BluetoothGatt? = null) {
        try {
            if (data.isEmpty()) return

            val now = System.currentTimeMillis()
            lastRxTimestamp = now
            lastRxBytesCount = data.size
            val hex = data.joinToString(" ") { "%02X".format(it) }
            lastRxHex = hex
            AppLog.recordRx(characteristic.uuid.toString(), hex)

            val newStream: ByteArray
            synchronized(rxBufferLock) {
                val combined = ByteArray(rxBuffer.size + data.size)
                System.arraycopy(rxBuffer, 0, combined, 0, rxBuffer.size)
                System.arraycopy(data, 0, combined, rxBuffer.size, data.size)

                // Limit buffer to 8 KiB
                if (combined.size > 8192) {
                    val excess = combined.size - 8192
                    AppLog.w(tag, "RX buffer exceeded 8 KiB; dropping $excess oldest bytes")
                    rxBuffer = combined.copyOfRange(excess, combined.size)
                } else {
                    rxBuffer = combined
                }
                newStream = rxBuffer
            }

            val (parsedFrames, remainder) = Protocol.parseFramesFromStream(
                stream = newStream,
                onDroppedBytes = { dropped ->
                    val droppedHex = dropped.joinToString(" ") { "%02X".format(it) }
                    AppLog.w(tag, "RX dropped ${dropped.size} invalid bytes: $droppedHex")
                },
                onCrcFailure = { exp, act, badFrame ->
                    val badHex = badFrame.joinToString(" ") { "%02X".format(it) }
                    AppLog.e(tag, "RX CRC mismatch: expected=0x${Integer.toHexString(exp)}, calculated=0x${Integer.toHexString(act)} on frame: $badHex")
                }
            )

            synchronized(rxBufferLock) {
                rxBuffer = remainder
            }

            for (frame in parsedFrames) {
                rxCountSinceConnect++
                _rxCountFlow.value = rxCountSinceConnect
                AppLog.rxCountSinceConnect = rxCountSinceConnect

                val flagStr = when (frame.flag) {
                    Protocol.FLAG_COMMAND -> "CMD (0x01)"
                    Protocol.FLAG_ACK -> "ACK (0x11)"
                    Protocol.FLAG_PUSH -> "PUSH (0x21)"
                    else -> "FLAG_0x${"%02X".format(frame.flag)}"
                }
                val payloadHex = frame.fullPayload.joinToString(" ") { "%02X".format(it) }
                AppLog.i(
                    tag,
                    "RX Frame #${rxCountSinceConnect} [key=0x${"%04X".format(frame.key)}, keyFlag=0x${"%02X".format(frame.keyFlag)}, $flagStr, CRC OK]: $payloadHex"
                )

                _incomingFrameFlow.tryEmit(frame)

                // 1. Parse ANY biometric frame with key 0x0224 (HR) or 0x024E (SpO2) REGARDLESS OF FLAG
                if (frame.key == Protocol.KEY_HR_PUSH || frame.key == Protocol.KEY_SPO2_PUSH) {
                    handleBiometricFrame(frame)
                }

                // 1b. Parse Steps (Key 0x051A)
                if (frame.key == Protocol.KEY_STEPS_PUSH) {
                    handleStepsFrame(frame)
                }

                // 1c. Parse Sleep (Key 0x0505 - BLE_KEY_SLEEP)
                if (frame.key == Protocol.KEY_SLEEP) {
                    handleSleepFrame(frame)
                }

                // 1d. Workout / Sport history (Key 0x0506 - KEY_WORKOUT)
                if (frame.key == Protocol.KEY_WORKOUT) {
                    handleWorkoutFrame(frame)
                }

                // 2. Status updates (Key 0x0609 measurement completed: payload ends with type, 0x05, 0x00)
                if (frame.key == Protocol.KEY_MEASURE_CONTROL) {
                    val completedType = Protocol.getCompletedMeasurementType(frame)
                    if (completedType != null) {
                        val typeName = when (completedType) {
                            Protocol.MEASURE_TYPE_HR -> "Heart Rate"
                            Protocol.MEASURE_TYPE_SPO2 -> "SpO2"
                            else -> "0x${"%02X".format(completedType)}"
                        }
                        AppLog.i(tag, "Measurement status frame (0x0609): complete=true for $typeName")
                        isMeasuringActive = false
                        activeMeasureType = null
                        _measureCompletedFlow.tryEmit(completedType)
                    } else {
                        val isComplete = Protocol.isMeasurementCompletedStatus(frame)
                        if (isComplete) {
                            AppLog.i(tag, "Measurement status frame (0x0609): complete=true")
                            isMeasuringActive = false
                            activeMeasureType = null
                            _measureCompletedFlow.tryEmit(0x00)
                        }
                    }
                }

                // 3. Reply to pending command (complete if matching key regardless of flag)
                val pendingKey = pendingCommandKey
                if (pendingKey != null && pendingKey == frame.key) {
                    AppLog.i(tag, "Matching reply received for pending command key 0x${"%04X".format(pendingKey)} [flag=0x${"%02X".format(frame.flag)}]")
                    pendingCommandDeferred?.complete(frame)
                }

                // 4. If incoming flag == 0x01: immediately send ACK (AB 11 00 03 crc16 key(2) keyFlag)
                if (frame.flag == Protocol.FLAG_COMMAND && gatt != null) {
                    scope.launch {
                        try {
                            val ackFrame = Protocol.buildAckFrame(frame.key, frame.keyFlag)
                            val ackHex = ackFrame.joinToString(" ") { "%02X".format(it) }
                            AppLog.i(tag, "Sending ACK for command 0x${"%04X".format(frame.key)}: $ackHex")
                            sendRawChunked(gatt, ackFrame)
                        } catch (e: Exception) {
                            AppLog.e(tag, "Failed to send ACK: ${e.message}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Exception in onNotificationReceived: ${e.message}", e)
        }
    }

    private fun handleBiometricFrame(frame: Protocol.Frame) {
        scope.launch {
            try {
                val (hr, spo2) = Protocol.parseBiometricFrame(frame)
                if (hr != null) {
                    // Range check: reject values outside 30..240 bpm. Drop or clamp, do not emit.
                    if (hr.bpm !in 30..240) {
                        AppLog.w(tag, "Dropping out-of-range HR sample: ${hr.bpm} BPM (valid bounds: 30..240)")
                    } else {
                        // Spike filter: drop single-sample spikes (delta > 40 bpm from previous sample when interval < 2 s)
                        val prevBpm = lastValidHrBpm
                        val prevTime = lastValidHrTimestamp
                        val interval = hr.timestamp - prevTime
                        if (prevBpm != null && kotlin.math.abs(hr.bpm - prevBpm) > 40 && interval in 0 until 2000L) {
                            AppLog.w(
                                tag,
                                "Dropping HR spike: ${hr.bpm} BPM (delta: ${kotlin.math.abs(hr.bpm - prevBpm)} > 40 from $prevBpm BPM, interval: ${interval}ms < 2000ms)"
                            )
                        } else {
                            lastValidHrBpm = hr.bpm
                            lastValidHrTimestamp = hr.timestamp
                            AppLog.i(tag, "HR sample parsed: ${hr.bpm} BPM from 0224")
                            _heartRateFlow.emit(hr)
                        }
                    }
                }
                if (spo2 != null) {
                    // Range check: reject values outside 70..100%. Drop or clamp, do not emit.
                    if (spo2.percentage !in 70f..100f) {
                        AppLog.w(tag, "Dropping out-of-range SpO2 sample: ${spo2.percentage}% (valid bounds: 70..100)")
                    } else {
                        // Spike filter: drop single-sample spikes (delta > 15% from previous sample when interval < 2 s)
                        val prevSpo2 = lastValidSpo2Percentage
                        val prevTime = lastValidSpo2Timestamp
                        val interval = spo2.timestamp - prevTime
                        if (prevSpo2 != null && kotlin.math.abs(spo2.percentage - prevSpo2) > 15f && interval in 0 until 2000L) {
                            AppLog.w(
                                tag,
                                "Dropping SpO2 spike: ${spo2.percentage}% (delta: ${kotlin.math.abs(spo2.percentage - prevSpo2)} > 15 from $prevSpo2%, interval: ${interval}ms < 2000ms)"
                            )
                        } else {
                            lastValidSpo2Percentage = spo2.percentage
                            lastValidSpo2Timestamp = spo2.timestamp
                            AppLog.i(tag, "SpO2 sample parsed: ${spo2.percentage.toInt()}% from 024E")
                            _spo2Flow.emit(spo2)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error handling biometric frame: ${e.message}", e)
            }
        }
    }

    private fun handleStepsFrame(frame: Protocol.Frame) {
        scope.launch {
            try {
                val hex = frame.fullPayload.joinToString(" ") { "%02X".format(it) }
                AppLog.i(tag, "0x051A Steps RX: $hex")
                val steps = Protocol.parseStepsFrame(frame)
                if (steps != null) {
                    AppLog.i(tag, "Steps successfully parsed from 051A: ${steps.totalSteps} steps")
                    _stepsFlow.emit(steps)
                } else {
                    AppLog.w(tag, "0x051A: uncertain step data payload, leaving raw steps unparsed: $hex")
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error handling steps frame: ${e.message}", e)
            }
        }
    }

    private fun handleSleepFrame(frame: Protocol.Frame) {
        scope.launch {
            try {
                val hex = frame.fullPayload.joinToString(" ") { "%02X".format(it) }
                AppLog.i(tag, "0x0505 Sleep RX: $hex")
                val sleep = Protocol.parseSleepFrame(frame)
                if (sleep != null) {
                    AppLog.i(tag, "Sleep successfully parsed from 0505: ${sleep.durationMinutes} mins")
                    _sleepFlow.emit(sleep)
                } else {
                    AppLog.i(tag, "0x0505: payload received but duration 0 or empty, leaving sleep record empty")
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error handling sleep frame: ${e.message}", e)
            }
        }
    }

    private fun handleWorkoutFrame(frame: Protocol.Frame) {
        scope.launch {
            try {
                val hex = frame.fullPayload.joinToString(" ") { "%02X".format(it) }
                AppLog.i(tag, "0x0506 Workout RX: $hex")
                val workouts = Protocol.parseWorkoutFrame(frame)
                if (workouts.isNotEmpty()) {
                    AppLog.i(tag, "Workout parsed from 0506: ${workouts.size} session(s)")
                    _workoutFlow.emit(workouts)
                } else {
                    AppLog.i(tag, "0x0506: payload received but no valid workout session parsed")
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error handling workout frame: ${e.message}", e)
            }
        }
    }

    // =========================================================================
    // 3. Command Queue: Transmission WITH Response Chunked to MTU-3
    // =========================================================================

    /**
     * Sends a command frame and waits for the matching reply (same key).
     * Retries up to 2 times with a 5000ms timeout per attempt.
     */
    suspend fun executeCommand(
        gatt: BluetoothGatt,
        frame: ByteArray,
        expectedKey: Int,
        timeoutMs: Long = 5000L,
        maxRetries: Int = 2
    ): Protocol.Frame? = commandMutex.withLock {
        for (attempt in 0..maxRetries) {
            val txHex = frame.joinToString(" ") { "%02X".format(it) }
            lastTxHex = txHex
            AppLog.lastTxHex = txHex

            val deferred = CompletableDeferred<Protocol.Frame>()
            pendingCommandKey = expectedKey
            pendingCommandDeferred = deferred

            val attemptStr = if (attempt > 0) " (retry $attempt/$maxRetries)" else ""
            AppLog.i(tag, "TX -> [0xB002] Key=0x${"%04X".format(expectedKey)}$attemptStr: $txHex")
            AppLog.recordTx("0000B002", "DEFAULT", "RWfit", txHex)

            val writeOk = sendRawChunked(gatt, frame)
            if (!writeOk) {
                AppLog.e(tag, "TX failed: Bluetooth stack rejected write for Key=0x${"%04X".format(expectedKey)}")
                pendingCommandKey = null
                pendingCommandDeferred = null
                if (attempt < maxRetries) {
                    delay(300)
                    continue
                }
                return@withLock null
            }

            try {
                val response = withTimeout(timeoutMs) {
                    deferred.await()
                }
                pendingCommandKey = null
                pendingCommandDeferred = null
                AppLog.i(tag, "Command 0x${"%04X".format(expectedKey)} completed successfully")
                return@withLock response
            } catch (e: TimeoutCancellationException) {
                AppLog.w(tag, "Command 0x${"%04X".format(expectedKey)} timed out after ${timeoutMs}ms$attemptStr")
                pendingCommandKey = null
                pendingCommandDeferred = null
                if (attempt < maxRetries) {
                    delay(400)
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Exception waiting for command 0x${"%04X".format(expectedKey)}: ${e.message}", e)
                pendingCommandKey = null
                pendingCommandDeferred = null
                return@withLock null
            }
        }

        AppLog.e(tag, "Command 0x${"%04X".format(expectedKey)} failed after $maxRetries retries (0 matching RX)")
        null
    }

    /**
     * Chunks and writes raw bytes to characteristic 0xB002 WITH response (WRITE_TYPE_DEFAULT).
     * Chunk size is (negotiatedMtu - 3).coerceIn(20, 512).
     */
    @SuppressLint("MissingPermission")
    private suspend fun sendRawChunked(gatt: BluetoothGatt, frame: ByteArray): Boolean {
        val char = writeCharacteristic ?: run {
            AppLog.e(tag, "Write characteristic 0xB002 is null!")
            return false
        }

        val maxChunk = (negotiatedMtu - 3).coerceIn(20, 512)
        var offset = 0

        while (offset < frame.size) {
            val chunkSize = (frame.size - offset).coerceAtMost(maxChunk)
            val chunk = ByteArray(chunkSize)
            System.arraycopy(frame, offset, chunk, 0, chunkSize)

            val writeDeferred = CompletableDeferred<Int>()
            pendingWriteCharDeferred = writeDeferred

            val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeCharacteristic(char, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                status == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = chunk
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }

            if (!initiated) {
                AppLog.e(tag, "Write chunk rejected by stack (offset=$offset, size=$chunkSize)")
                pendingWriteCharDeferred = null
                return false
            }

            val status = withTimeoutOrNull(2000L) {
                writeDeferred.await()
            }
            pendingWriteCharDeferred = null

            if (status != BluetoothGatt.GATT_SUCCESS) {
                AppLog.e(tag, "onCharacteristicWrite failed with status=$status for chunk at $offset")
                return false
            }

            offset += chunkSize
        }

        return true
    }

    // =========================================================================
    // 4. Initialization Sequence (Strict Step-by-Step State Machine)
    // =========================================================================

    /**
     * Runs the strict RWfit initialization sequence:
     * 1. Key 0x0302 (Session init)
     * 2. Key 0x0202 (Timezone)
     * 3. Key 0x0201 (Local time)
     * 4. Key 0x0263 (Function list) -> check bit0 at index 0x2C
     * 5. Optional Key 0x0304 ("0000") if password required
     *
     * Sets ConnectionState.Ready only if REAL RX was received and init completed.
     */
    suspend fun runInitializationSequence(gatt: BluetoothGatt): Boolean {
        if (initStarted && _initState.value is InitState.Success) {
            AppLog.i(tag, "runInitializationSequence: Already successfully initialized. Skipping duplicate run.")
            return true
        }
        if (initStarted && _initState.value is InitState.InProgress) {
            AppLog.w(tag, "runInitializationSequence: Initialization already in progress. Ignoring duplicate run.")
            return false
        }
        initStarted = true
        AppLog.i(tag, "Starting RWfit Initialization Sequence...")
        val totalSteps = 4

        // Step 1: Session init (0x0302)
        _initState.value = InitState.InProgress(1, totalSteps, "Session Init (0x0302)")
        AppLog.i(tag, "Init Step 1/4: Session Init (Key 0x0302)")
        val initFrame = Protocol.buildSessionInit()
        val reply1 = executeCommand(gatt, initFrame, Protocol.KEY_SESSION_INIT, timeoutMs = 5000L)
        if (reply1 == null) {
            val err = "Init Step 1 (0x0302) failed: 0 replies received from ring"
            lastFailedInitStep = err
            AppLog.e(tag, err)
            _initState.value = InitState.Failed(err)
            return false
        }
        delay(150)

        // Step 2: Timezone (0x0202)
        _initState.value = InitState.InProgress(2, totalSteps, "Timezone Sync (0x0202)")
        AppLog.i(tag, "Init Step 2/4: Timezone (Key 0x0202)")
        val tzFrame = Protocol.buildTimezoneCommand()
        val reply2 = executeCommand(gatt, tzFrame, Protocol.KEY_TIMEZONE, timeoutMs = 5000L)
        if (reply2 == null) {
            val err = "Init Step 2 (0x0202) failed: No timezone reply"
            lastFailedInitStep = err
            AppLog.e(tag, err)
            _initState.value = InitState.Failed(err)
            return false
        }
        delay(150)

        // Step 3: Local Time (0x0201)
        _initState.value = InitState.InProgress(3, totalSteps, "Time Sync (0x0201)")
        AppLog.i(tag, "Init Step 3/4: Time Sync (Key 0x0201)")
        val timeFrame = Protocol.buildTimeCommand()
        val reply3 = executeCommand(gatt, timeFrame, Protocol.KEY_TIME, timeoutMs = 5000L)
        if (reply3 == null) {
            val err = "Init Step 3 (0x0201) failed: No time sync reply"
            lastFailedInitStep = err
            AppLog.e(tag, err)
            _initState.value = InitState.Failed(err)
            return false
        }
        delay(150)

        // Step 4: Function List (0x0263)
        _initState.value = InitState.InProgress(4, totalSteps, "Function Query (0x0263)")
        AppLog.i(tag, "Init Step 4/4: Function List (Key 0x0263)")
        val fnFrame = Protocol.buildFunctionListCommand()
        val reply4 = executeCommand(gatt, fnFrame, Protocol.KEY_FUNCTION_LIST, timeoutMs = 5000L)
        if (reply4 == null) {
            val err = "Init Step 4 (0x0263) failed: No function list reply"
            lastFailedInitStep = err
            AppLog.e(tag, err)
            _initState.value = InitState.Failed(err)
            return false
        }

        // Check if password required (byte 0x2C bit 0 set in reply payload)
        val passwordRequired = Protocol.isPasswordRequired(reply4.fullPayload)
        if (passwordRequired) {
            AppLog.w(tag, "Password required by ring; sending password command (0x0304 '0000')...")
            val pwdFrame = Protocol.buildPasswordCommand("0000")
            val replyPwd = executeCommand(gatt, pwdFrame, Protocol.KEY_PASSWORD, timeoutMs = 5000L)
            if (replyPwd == null) {
                val err = "Password authentication timed out"
                lastFailedInitStep = err
                AppLog.e(tag, err)
                _initState.value = InitState.Failed(err)
                return false
            }

            // Reply payload byte 3 must be 0 or report auth failure
            val authStatus = if (replyPwd.fullPayload.size > 3) replyPwd.fullPayload[3].toInt() else -1
            if (authStatus != 0) {
                val err = "Password authentication failed (status=$authStatus)"
                lastFailedInitStep = err
                AppLog.e(tag, err)
                _initState.value = InitState.Failed(err)
                return false
            }
            AppLog.i(tag, "Password authentication SUCCESS")
        }

        _initState.value = InitState.Success
        lastFailedInitStep = null
        AppLog.i(tag, "★ RWfit Initialization COMPLETED successfully! Real RX count = $rxCountSinceConnect")
        return true
    }

    // =========================================================================
    // 5. Measurements API: Start / Stop with Key 0x0609
    // =========================================================================

    suspend fun startHeartRateMeasurement(gatt: BluetoothGatt): Boolean {
        isMeasuringActive = true
        activeMeasureType = Protocol.MEASURE_TYPE_HR
        val frame = Protocol.buildHeartRateStartCommand()
        val reply = executeCommand(gatt, frame, Protocol.KEY_MEASURE_CONTROL, timeoutMs = 5000L)
        return reply != null
    }

    suspend fun stopHeartRateMeasurement(gatt: BluetoothGatt): Boolean {
        isMeasuringActive = false
        activeMeasureType = null
        val frame = Protocol.buildHeartRateStopCommand()
        val reply = executeCommand(gatt, frame, Protocol.KEY_MEASURE_CONTROL, timeoutMs = 5000L)
        return reply != null
    }

    suspend fun startSpO2Measurement(gatt: BluetoothGatt): Boolean {
        isMeasuringActive = true
        activeMeasureType = Protocol.MEASURE_TYPE_SPO2
        val frame = Protocol.buildSpO2StartCommand()
        val reply = executeCommand(gatt, frame, Protocol.KEY_MEASURE_CONTROL, timeoutMs = 5000L)
        return reply != null
    }

    suspend fun stopSpO2Measurement(gatt: BluetoothGatt): Boolean {
        isMeasuringActive = false
        activeMeasureType = null
        val frame = Protocol.buildSpO2StopCommand()
        val reply = executeCommand(gatt, frame, Protocol.KEY_MEASURE_CONTROL, timeoutMs = 5000L)
        return reply != null
    }

    suspend fun startMeasurementType(gatt: BluetoothGatt, type: Byte): Boolean {
        isMeasuringActive = true
        activeMeasureType = type
        val frame = Protocol.buildMeasurementCommand(type, true)
        val reply = executeCommand(gatt, frame, Protocol.KEY_MEASURE_CONTROL, timeoutMs = 4000L)
        return reply != null
    }

    suspend fun stopMeasurementType(gatt: BluetoothGatt, type: Byte): Boolean {
        isMeasuringActive = false
        activeMeasureType = null
        val frame = Protocol.buildMeasurementCommand(type, false)
        val reply = executeCommand(gatt, frame, Protocol.KEY_MEASURE_CONTROL, timeoutMs = 4000L)
        return reply != null
    }

    /**
     * Executes the ordered probe for ring display ON/OFF:
     * Tries cachedKey first if valid, else probes candidate keys A..E:
     *   A) key 0x0306 data [0x00]=off [0x01]=on
     *   B) key 0x0307 data [0x00]/[0x01]
     *   C) key 0x0203 data [0x00]/[0x01]
     *   D) key 0x0204 data [0x00]/[0x01]
     *   E) key 0x0264 data [0x00]/[0x01]
     * Stops on first ACK / matching response.
     * Returns Pair(success, confirmedKey).
     */
    suspend fun sendDisplayCommand(
        gatt: BluetoothGatt,
        enabled: Boolean,
        cachedKey: Int?
    ): Pair<Boolean, Int?> {
        AppLog.i(tag, "sendDisplayCommand: enabled=$enabled, cachedKey=0x${cachedKey?.let { "%04X".format(it) } ?: "none"}")

        // 1. If cachedKey is present and in candidate list, try it first
        if (cachedKey != null && cachedKey in Protocol.DISPLAY_CANDIDATE_KEYS) {
            val frame = Protocol.buildDisplayCommand(cachedKey, enabled)
            val reply = executeCommand(gatt, frame, cachedKey, timeoutMs = 2500L, maxRetries = 1)
            if (reply != null) {
                val hex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
                AppLog.i(tag, "Display command SUCCESS using cached key 0x${"%04X".format(cachedKey)}: RX $hex")
                return Pair(true, cachedKey)
            }
            AppLog.w(tag, "Cached display key 0x${"%04X".format(cachedKey)} timed out. Falling back to probe candidate keys...")
        }

        // 2. Probe candidate keys in order
        for (candidateKey in Protocol.DISPLAY_CANDIDATE_KEYS) {
            if (candidateKey == cachedKey) continue // already tried above
            val frame = Protocol.buildDisplayCommand(candidateKey, enabled)
            AppLog.i(tag, "Probing display command with key 0x${"%04X".format(candidateKey)} (enabled=$enabled)...")
            val reply = executeCommand(gatt, frame, candidateKey, timeoutMs = 2500L, maxRetries = 1)
            if (reply != null) {
                val hex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
                AppLog.i(tag, "Display command PROBE SUCCESS on key 0x${"%04X".format(candidateKey)}! RX: $hex")
                return Pair(true, candidateKey)
            }
        }

        AppLog.w(tag, "Display command probe exhausted: no candidate key confirmed on this firmware.")
        return Pair(false, null)
    }

    /**
     * Executes the ordered probe for ring screen brightness (Off | Dim | Soft | Strong):
     * Tries cachedKey/cachedShape first if present, then probes candidate keys/shapes.
     * Logs each candidate TX with BrightnessProbe.describe(...)
     * Stops on first matching RX where BrightnessProbe.isResponseFor(reply, candidate).
     * If nothing answers, logs "no RX for any brightness candidate".
     * Returns the confirmed Candidate, or null if no candidate answered.
     */
    suspend fun sendBrightnessCommand(
        gatt: BluetoothGatt,
        brightness: ScreenBrightness,
        cachedKey: Int?,
        cachedShape: Int?
    ): BrightnessProbe.Candidate? {
        val candidates = BrightnessProbe.orderedCandidates(cachedKey, cachedShape)
        for (candidate in candidates) {
            AppLog.i(tag, BrightnessProbe.describe(candidate, brightness))
            val cmd = BrightnessProbe.buildCommand(candidate, brightness)
            val reply = executeCommand(gatt, cmd, candidate.key, timeoutMs = 2500L, maxRetries = 1)
            if (reply != null && BrightnessProbe.isResponseFor(reply, candidate)) {
                val hex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
                AppLog.i(tag, "Brightness probe RX key=0x${"%04X".format(candidate.key)} shape=${candidate.shape}: $hex")
                return candidate
            }
        }
        AppLog.w(tag, "no RX for any brightness candidate")
        return null
    }

    // =========================================================================
    // 6. Steps & Sleep Queries (0x051A and 0x0505)
    // =========================================================================

    /**
     * Sends working TX: AB 01 00 03 AD 1A 05 1A 10 and parses real steps.
     */
    suspend fun fetchSteps(gatt: BluetoothGatt): com.galaxy.ring.data.StepData? {
        val frame = Protocol.buildStepsCommand()
        val txHex = frame.joinToString(" ") { "%02X".format(it) }
        AppLog.i(tag, "TX -> 0x051A Steps Query: $txHex")
        val reply = executeCommand(gatt, frame, Protocol.KEY_STEPS_PUSH, timeoutMs = 5000L)
        return if (reply != null) {
            val hex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
            AppLog.i(tag, "0x051A Steps reply received: $hex")
            val steps = Protocol.parseStepsFrame(reply)
            if (steps != null) {
                AppLog.i(tag, "Real steps parsed from 051A: ${steps.totalSteps}")
                _stepsFlow.emit(steps)
            } else {
                AppLog.w(tag, "0x051A Steps reply could not be parsed into plausible steps (0..100000): $hex")
            }
            steps
        } else {
            AppLog.w(tag, "0x051A Steps Query timed out (0 matching RX)")
            null
        }
    }

    /**
     * Result of paged sleep sync containing parsed sessions and working path metadata.
     */
    data class SleepSyncHistoryResult(
        val sessions: List<com.galaxy.ring.data.SleepSession>,
        val requiresDeleteToAdvance: Boolean,
        val workingKey: Int = Protocol.KEY_SLEEP
    )

    /**
     * Executes official RWFit SDK paged sleep history fetch (BLE_KEY_SLEEP = 0x0505):
     * 1) key 0x0505, keyFlag 0x10 (READ), no extra data first
     * 2) Wait for matching RX (any flag 0x11/0x01/0x21 with key 0x0505)
     * 3) History is PAGED: keep requesting 0x0505 until empty page or no more data
     * 4) Log full payload hex every page
     * 5) Parse best-effort: start/end or duration + stages; save one SleepSession per night to Room + Health Connect
     * 6) No DELETE calls: re-sync avoids duplicate nights via SleepPageStore keying by timestamp and Room DAO delete-then-insert.
     * 7) Timeout 12s per page, 1 retry; do not invent sleep if empty
     *
     * Also caches working path metadata (key and advance mechanism).
     */
    suspend fun syncSleepHistory(
        gatt: BluetoothGatt,
        cachedRequiresDelete: Boolean = false
    ): SleepSyncHistoryResult {
        AppLog.i(tag, "Starting 0x0505 Paged Sleep History Fetch (timeout 12s/page, 1 retry)...")
        val importedSessions = mutableListOf<com.galaxy.ring.data.SleepSession>()
        var pageNum = 1
        val maxPages = 30
        var previousPagePayloadHex: String? = null

        val readCommandFrame = Protocol.buildSleepCommand(Protocol.KEY_FLAG_READ)
        val readTxHex = readCommandFrame.joinToString(" ") { "%02X".format(it) }

        while (pageNum <= maxPages) {
            AppLog.i(tag, "Requesting 0x0505 Sleep History Page $pageNum [TX: $readTxHex]...")
            val reply = executeCommand(
                gatt,
                readCommandFrame,
                Protocol.KEY_SLEEP,
                timeoutMs = 12000L,
                maxRetries = 1
            )

            if (reply == null) {
                AppLog.i(tag, "Page $pageNum received 0 RX from ring after 12s timeout and 1 retry. End of paged history.")
                break
            }

            val rxHex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
            AppLog.i(tag, "0x0505 Sleep Page $pageNum RX: $rxHex (flag=0x${"%02X".format(reply.flag)}, len=${reply.data.size})")

            // Empty or all zeroes indicates end of sleep history
            if (reply.data.isEmpty() || reply.data.all { it.toInt() == 0 }) {
                AppLog.i(tag, "0x0505 Sleep Page $pageNum payload is empty or all zeroes. Reached end of sleep history.")
                break
            }

            // Check if ring returned identical page payload to previous page (reached end of distinct history)
            if (rxHex == previousPagePayloadHex) {
                AppLog.i(tag, "0x0505 Sleep Page $pageNum returned duplicate data; end of history.")
                break
            }

            // Parse best-effort: start/end or duration + stages
            val session = Protocol.parseSleepFrame(reply)
            if (session != null) {
                AppLog.i(tag, "Page $pageNum parsed successfully: ${session.durationMinutes} mins (${session.stages.size} stages)")
                importedSessions.add(session)
                previousPagePayloadHex = rxHex
                pageNum++
                delay(150)
            } else {
                AppLog.i(tag, "0x0505 Page $pageNum received ($rxHex) but contains 0 sleep duration or invalid sleep (<15m). Leaving sleep record empty.")
                break
            }
        }

        AppLog.i(tag, "0x0505 Paged Sleep Fetch finished: ${importedSessions.size} session(s) imported.")
        return SleepSyncHistoryResult(
            sessions = importedSessions,
            requiresDeleteToAdvance = false,
            workingKey = Protocol.KEY_SLEEP
        )
    }

    suspend fun syncSleepSequence(gatt: BluetoothGatt): com.galaxy.ring.data.SleepSession? {
        val result = syncSleepHistory(gatt)
        return result.sessions.maxByOrNull { it.endTime }
    }

    /**
     * Queries HRV history over BLE using key 0x050A (KEY_HISTORY_HRV, flag 0x10).
     * Returns parsed real samples (~6B: Y2K timestamp + 1-byte value), or empty list if no data (no fakes).
     */
    suspend fun fetchHrvHistory(gatt: BluetoothGatt): List<Protocol.HistorySample> {
        val frame = Protocol.buildHistoryRead(Protocol.KEY_HISTORY_HRV)
        val txHex = frame.joinToString(" ") { "%02X".format(it) }
        AppLog.i(tag, "TX -> 0x050A HRV History Query: $txHex")
        val reply = executeCommand(gatt, frame, Protocol.KEY_HISTORY_HRV, timeoutMs = 5000L)
        return if (reply != null) {
            val rxHex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
            AppLog.i(tag, "0x050A HRV History RX: $rxHex")
            val samples = Protocol.parseHistorySamples(reply, minVal = 5, maxVal = 250)
            if (samples.isNotEmpty()) {
                AppLog.i(tag, "Parsed ${samples.size} real HRV history sample(s) from 0x050A")
            } else {
                AppLog.i(tag, "0x050A HRV History reply was empty or contained no valid samples: $rxHex")
            }
            samples
        } else {
            AppLog.i(tag, "0x050A HRV History query timed out (0 matching RX)")
            emptyList()
        }
    }

    /**
     * Queries Stress history over BLE using key 0x050D (KEY_HISTORY_STRESS, flag 0x10).
     * Returns parsed real samples (~6B: Y2K timestamp + 1-byte value), or empty list if no data (no fakes).
     */
    suspend fun fetchStressHistory(gatt: BluetoothGatt): List<Protocol.HistorySample> {
        val frame = Protocol.buildHistoryRead(Protocol.KEY_HISTORY_STRESS)
        val txHex = frame.joinToString(" ") { "%02X".format(it) }
        AppLog.i(tag, "TX -> 0x050D Stress History Query: $txHex")
        val reply = executeCommand(gatt, frame, Protocol.KEY_HISTORY_STRESS, timeoutMs = 5000L)
        return if (reply != null) {
            val rxHex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
            AppLog.i(tag, "0x050D Stress History RX: $rxHex")
            val samples = Protocol.parseHistorySamples(reply, minVal = 1, maxVal = 100)
            if (samples.isNotEmpty()) {
                AppLog.i(tag, "Parsed ${samples.size} real Stress history sample(s) from 0x050D")
            } else {
                AppLog.i(tag, "0x050D Stress History reply was empty or contained no valid samples: $rxHex")
            }
            samples
        } else {
            AppLog.i(tag, "0x050D Stress History query timed out (0 matching RX)")
            emptyList()
        }
    }

    /**
     * Queries Workout / Sport history over BLE using key 0x0506 (KEY_WORKOUT, flag 0x10 READ).
     * Returns parsed real sessions via Protocol.parseWorkoutFrame, or empty list if no data (no fakes).
     */
    suspend fun fetchWorkoutHistory(gatt: BluetoothGatt): List<WorkoutSession> {
        val frame = Protocol.buildWorkoutHistoryCommand()
        val txHex = frame.joinToString(" ") { "%02X".format(it) }
        AppLog.i(tag, "TX -> 0x0506 Workout History Query: $txHex")
        val reply = executeCommand(gatt, frame, Protocol.KEY_WORKOUT, timeoutMs = 5000L)
        return if (reply != null) {
            val rxHex = reply.fullPayload.joinToString(" ") { "%02X".format(it) }
            AppLog.i(tag, "0x0506 Workout History RX: $rxHex")
            val sessions = Protocol.parseWorkoutFrame(reply)
            if (sessions.isNotEmpty()) {
                AppLog.i(tag, "Parsed ${sessions.size} real Workout history session(s) from 0x0506")
            } else {
                AppLog.i(tag, "0x0506 Workout History reply was empty or contained no valid sessions: $rxHex")
            }
            sessions
        } else {
            AppLog.i(tag, "0x0506 Workout History query timed out (0 matching RX)")
            emptyList()
        }
    }
}
