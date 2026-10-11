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
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.StepData
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

    // Measurement completion flow (emits completed metric type: 0x03 HR or 0x09 SpO2)
    private val _measureCompletedFlow = MutableSharedFlow<Byte>(extraBufferCapacity = 16)
    val measureCompletedFlow: SharedFlow<Byte> = _measureCompletedFlow.asSharedFlow()

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

                // 1. Parse ANY biometric frame with key 0x0224 (HR) or 0x024E (SpO2) REGARDLESS OF FLAG
                if (frame.key == Protocol.KEY_HR_PUSH || frame.key == Protocol.KEY_SPO2_PUSH) {
                    handleBiometricFrame(frame)
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

                // 3. Reply to pending command (only complete if matching key and not unsolicited push)
                val pendingKey = pendingCommandKey
                if (pendingKey != null && pendingKey == frame.key && frame.flag != Protocol.FLAG_PUSH) {
                    AppLog.i(tag, "Matching reply received for pending command key 0x${"%04X".format(pendingKey)}")
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
                    AppLog.i(tag, "HR sample parsed: ${hr.bpm} BPM from 0224")
                    _heartRateFlow.emit(hr)
                }
                if (spo2 != null) {
                    AppLog.i(tag, "SpO2 sample parsed: ${spo2.percentage.toInt()}% from 024E")
                    _spo2Flow.emit(spo2)
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error handling biometric frame: ${e.message}", e)
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
}
