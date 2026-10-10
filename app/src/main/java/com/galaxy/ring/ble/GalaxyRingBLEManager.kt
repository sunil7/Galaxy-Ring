package com.galaxy.ring.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.StepData
import com.galaxy.ring.debug.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages low-level Bluetooth LE communication with the Galaxy Ring / SR16 smart ring.
 *
 * Responsibilities:
 * 1. Handles command frame framing (0xAB header + length + payload + CRC16-ARC checksum).
 * 2. Implements the post-connect initialization sequence (0302 -> 0202 -> 0201 -> 0263, optional 0304).
 * 3. Observes and decodes incoming notifications from characteristic 0xB003 via CCCD.
 * 4. Logs all frames (TX/RX), discovery summaries, and parse outcomes to [AppLog].
 */
class GalaxyRingBLEManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {

    private val tag = "GalaxyRingBLE"

    // Discovered Characteristics for SR16 Ring
    var writeCharacteristic: BluetoothGattCharacteristic? = null
        private set

    var notifyCharacteristic: BluetoothGattCharacteristic? = null
        private set

    // Diagnostic & GATT Discovery State
    var foundService0xA00A: Boolean = false
        private set

    var foundWrite0xB002: Boolean = false
        private set

    var foundNotify0xB003: Boolean = false
        private set

    var isNotificationEnabled: Boolean = false
        private set

    var lastTxHex: String? = null
        private set

    var lastRxHex: String? = null
        private set

    var lastRxTimestamp: Long = 0L
        private set

    var lastRxBytesCount: Int = 0
        private set

    // Concurrency control for write commands
    private val writeMutex = Mutex()
    private var pendingResponse: CompletableDeferred<ByteArray>? = null

    // Initialization Sequence State
    sealed class InitState {
        data object Idle : InitState()
        data class InProgress(val currentStep: Int, val totalSteps: Int, val stepName: String) : InitState()
        data object Success : InitState()
        data class Failed(val error: String) : InitState()
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Idle)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    // Observable flows for incoming data on characteristic 0xB003
    private val _rawNotifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val rawNotifications: SharedFlow<ByteArray> = _rawNotifications.asSharedFlow()

    private val _heartRateFlow = MutableSharedFlow<HeartRateSample>(extraBufferCapacity = 16)
    val heartRateFlow: SharedFlow<HeartRateSample> = _heartRateFlow.asSharedFlow()

    private val _spo2Flow = MutableSharedFlow<OxygenSaturationSample>(extraBufferCapacity = 16)
    val spo2Flow: SharedFlow<OxygenSaturationSample> = _spo2Flow.asSharedFlow()

    private val _stepsFlow = MutableSharedFlow<StepData>(extraBufferCapacity = 16)
    val stepsFlow: SharedFlow<StepData> = _stepsFlow.asSharedFlow()

    private val _sleepFlow = MutableSharedFlow<SleepSession>(extraBufferCapacity = 16)
    val sleepFlow: SharedFlow<SleepSession> = _sleepFlow.asSharedFlow()

    fun markNotificationEnabled(enabled: Boolean) {
        isNotificationEnabled = enabled
    }

    fun getLastRxSince(sinceTimestamp: Long): Pair<Int, String>? {
        return if (lastRxTimestamp >= sinceTimestamp && lastRxHex != null) {
            Pair(lastRxBytesCount, lastRxHex!!)
        } else null
    }

    // =========================================================================
    // 1. Command Frame Encoding & Verification (0xAB + CRC16-ARC)
    // =========================================================================

    /**
     * Builds an outgoing command frame for the SR16 ring:
     * - Byte 0: 0xAB (Start of frame)
     * - Byte 1: Payload length (N)
     * - Bytes 2..(2+N-1): Payload
     * - Bytes (2+N)..(2+N+1): CRC16-ARC (little-endian: low byte, high byte)
     */
    fun buildCommandFrame(payload: ByteArray): ByteArray {
        val length = payload.size
        val packet = ByteArray(1 + 1 + length + 2)
        packet[0] = Protocol.FRAME_HEADER_SR16
        packet[1] = length.toByte()
        System.arraycopy(payload, 0, packet, 2, length)

        val crc = Protocol.crc16Arc(packet, 0, 2 + length)
        packet[2 + length] = (crc and 0xFF).toByte()
        packet[2 + length + 1] = ((crc ushr 8) and 0xFF).toByte()
        return packet
    }

    /**
     * Verifies that the frame begins with 0xAB / 0xBA and passes CRC16-ARC validation.
     */
    fun verifyFrame(data: ByteArray): Boolean {
        return Protocol.isValidSr16Frame(data)
    }

    /**
     * Extracts payload bytes by stripping the 0xAB header, length, and CRC16-ARC footer.
     */
    fun extractPayload(data: ByteArray): ByteArray {
        return Protocol.extractSr16Payload(data)
    }

    // =========================================================================
    // 2. Notification Observer Setup for Characteristic 0xB003
    // =========================================================================

    /**
     * Configures notifications for characteristic 0xB003.
     * Logs every GATT service and characteristic UUID discovered,
     * locates service 0xA00A, characteristic 0xB002 (write), 0xB003 (notify),
     * enables notifications on the local GATT client, and writes CCCD.
     */
    @SuppressLint("MissingPermission")
    fun setupNotificationObserver(gatt: BluetoothGatt): Boolean {
        AppLog.i(tag, "GATT services discovered (${gatt.services.size} total) on device ${gatt.device.address}")

        // Log every GATT service + characteristic UUID after discovery
        for (service in gatt.services) {
            val charSummary = service.characteristics.joinToString { it.uuid.toString() }
            AppLog.d(tag, "Discovered GATT Service: ${service.uuid} -> [${charSummary.ifEmpty { "no chars" }}]")
        }

        // Reset discovery status flags
        foundService0xA00A = false
        foundWrite0xB002 = false
        foundNotify0xB003 = false
        isNotificationEnabled = false
        writeCharacteristic = null
        notifyCharacteristic = null

        // Locate primary service 0xA00A and characteristics 0xB002 / 0xB003
        for (service in gatt.services) {
            val sUuid = service.uuid.toString()
            if (sUuid.contains("A00A", ignoreCase = true) || service.uuid == Protocol.SR16_SERVICE_UUID) {
                foundService0xA00A = true
                AppLog.i(tag, "Identified SR16 primary service 0xA00A: ${service.uuid}")
                for (ch in service.characteristics) {
                    val cUuid = ch.uuid.toString()
                    if (cUuid.contains("B002", ignoreCase = true) || ch.uuid == Protocol.SR16_WRITE_CHAR_UUID) {
                        writeCharacteristic = ch
                        foundWrite0xB002 = true
                        AppLog.i(tag, "Identified SR16 Write characteristic 0xB002: ${ch.uuid}")
                    }
                    if (cUuid.contains("B003", ignoreCase = true) || ch.uuid == Protocol.SR16_NOTIFY_CHAR_UUID) {
                        notifyCharacteristic = ch
                        foundNotify0xB003 = true
                        AppLog.i(tag, "Identified SR16 Notify characteristic 0xB003: ${ch.uuid}")
                    }
                }
            }
        }

        // Broad fallback scan across all services if not found
        if (writeCharacteristic == null || notifyCharacteristic == null) {
            AppLog.w(tag, "0xB002 or 0xB003 not in 0xA00A; scanning all services as fallback...")
            for (service in gatt.services) {
                for (ch in service.characteristics) {
                    val cUuid = ch.uuid.toString()
                    if (writeCharacteristic == null && (cUuid.contains("B002", ignoreCase = true) || ch.uuid == Protocol.SR16_WRITE_CHAR_UUID)) {
                        writeCharacteristic = ch
                        foundWrite0xB002 = true
                        AppLog.i(tag, "Found Write characteristic 0xB002 via fallback: ${ch.uuid}")
                    }
                    if (notifyCharacteristic == null && (cUuid.contains("B003", ignoreCase = true) || ch.uuid == Protocol.SR16_NOTIFY_CHAR_UUID)) {
                        notifyCharacteristic = ch
                        foundNotify0xB003 = true
                        AppLog.i(tag, "Found Notify characteristic 0xB003 via fallback: ${ch.uuid}")
                    }
                }
            }
        }

        // Log whether 0xB002 / 0xB003 were found
        AppLog.i(
            tag,
            "GATT Summary -> Service 0xA00A: $foundService0xA00A, Write 0xB002: $foundWrite0xB002, Notify 0xB003: $foundNotify0xB003"
        )

        val nChar = notifyCharacteristic
        if (nChar == null) {
            val err = "Failed to locate notify characteristic 0xB003 on ${gatt.device.address}"
            AppLog.e(tag, err)
            return false
        }

        // 1. Enable local notification subscription in Android Bluetooth Stack
        val successLocal = gatt.setCharacteristicNotification(nChar, true)
        if (!successLocal) {
            val err = "Failed to enable local characteristic notification on ${nChar.uuid}"
            AppLog.e(tag, err)
            return false
        }

        // 2. Enable remote notification via CCCD (Client Characteristic Configuration Descriptor 0x2902)
        val descriptor = nChar.getDescriptor(Protocol.CCCD_UUID)
        if (descriptor != null) {
            AppLog.d(tag, "Writing ENABLE_NOTIFICATION_VALUE to CCCD ${descriptor.uuid}...")
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            val initiated = gatt.writeDescriptor(descriptor)
            if (!initiated) {
                val err = "Failed to initiate writeDescriptor for CCCD on 0xB003"
                AppLog.e(tag, err)
                return false
            }
            isNotificationEnabled = true
        } else {
            AppLog.w(tag, "CCCD descriptor (0x2902) not found on characteristic ${nChar.uuid}")
            isNotificationEnabled = true // Local set, descriptor might be auto-subscribed or not exposed
        }

        AppLog.i(tag, "Notification observer successfully configured on 0xB003")
        return true
    }

    /**
     * Dispatches raw characteristic changes received on characteristic 0xB003.
     * Decodes the SR16 framing and emits parsed models into corresponding SharedFlows.
     */
    fun onNotificationReceived(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (data.isEmpty()) {
            AppLog.w(tag, "0xB003 Notification received with empty byte array")
            return
        }

        val hexString = data.joinToString(" ") { "%02X".format(it) }
        lastRxHex = hexString
        lastRxTimestamp = System.currentTimeMillis()
        lastRxBytesCount = data.size
        AppLog.lastRxHex = hexString

        // Always log every RX / notification as hex (even if parse fails)
        AppLog.d(tag, "0xB003 RX Notification (${data.size} bytes): $hexString")

        // Complete any pending command response awaiter and log RX
        val pending = pendingResponse
        if (pending != null && pending.isActive) {
            AppLog.i(tag, "RX completed pending response (${data.size} bytes): $hexString")
            pending.complete(data)
        }

        scope.launch {
            _rawNotifications.emit(data)

            // Extract payload from 0xAB frame
            val payload = extractPayload(data)
            val payloadHex = payload.joinToString(" ") { "%02X".format(it) }

            // 1. Parse Heart Rate (CMD 02 24)
            val hr = Protocol.parseSr16HeartRate(payload)
            if (hr != null) {
                AppLog.i(tag, "HR parse SUCCESS: ${hr.bpm} BPM (payload: $payloadHex)")
                _heartRateFlow.emit(hr)
            } else {
                AppLog.d(tag, "HR parse: no matching HR record in payload")
            }

            // 2. Parse SpO₂ (CMD 02 4E)
            val spo2 = Protocol.parseSr16SpO2(payload)
            if (spo2 != null) {
                AppLog.i(tag, "SpO₂ parse SUCCESS: ${spo2.percentage}% (payload: $payloadHex)")
                _spo2Flow.emit(spo2)
            } else {
                AppLog.d(tag, "SpO₂ parse: no matching SpO₂ record in payload")
            }

            // 3. Parse Steps (CMD 05 1A)
            val steps = Protocol.parseSr16Steps(payload)
            if (steps != null) {
                AppLog.i(tag, "Steps parse SUCCESS: ${steps.totalSteps} steps (payload: $payloadHex)")
                _stepsFlow.emit(steps)
            } else {
                AppLog.d(tag, "Steps parse: no matching Steps record in payload")
            }

            // 4. Parse Sleep (CMD 05 1B)
            val sleep = Protocol.parseSr16SleepSession(payload)
            if (sleep != null) {
                AppLog.i(tag, "Sleep parse SUCCESS: ${sleep.durationMinutes} min (payload: $payloadHex)")
                _sleepFlow.emit(sleep)
            } else {
                AppLog.d(tag, "Sleep parse: no matching Sleep record in payload")
            }
        }
    }

    // =========================================================================
    // 3. Initialization Sequence Execution (0302 -> 0202 -> 0201 -> 0263)
    // =========================================================================

    /**
     * Executes the mandatory four-step SR16 initialization sequence:
     * Step 1: 0302 (System Handshake)
     * Step 2: 0202 (Health Configuration)
     * Step 3: 0201 (Sensor Calibration)
     * Step 4: 0263 (Ring Feature Handshake)
     * Followed by optional Step 5: 0304 (Time sync/handshake)
     */
    suspend fun runInitializationSequence(gatt: BluetoothGatt): Boolean {
        AppLog.i(tag, "Init sequence START: (0302 -> 0202 -> 0201 -> 0263)...")

        val steps = listOf(
            Triple(Protocol.INIT_CMD_1, 1, "System Handshake (0302)"),
            Triple(Protocol.INIT_CMD_2, 2, "Health Configuration (0202)"),
            Triple(Protocol.INIT_CMD_3, 3, "Sensor Calibration (0201)"),
            Triple(Protocol.INIT_CMD_4, 4, "Ring Feature Handshake (0263)")
        )

        for ((cmd, stepNum, description) in steps) {
            _initState.value = InitState.InProgress(stepNum, steps.size, description)
            AppLog.d(tag, "Init Step $stepNum/${steps.size} START: $description")

            val ok = sendCommandFrameWithRetry(gatt, cmd, maxRetries = 1, timeoutMs = 2500L)
            if (!ok) {
                val err = "Init Step $stepNum FAILED ($description): Timeout or write rejected"
                AppLog.e(tag, err)
                _initState.value = InitState.Failed(err)
                return false
            }
            AppLog.d(tag, "Init Step $stepNum SUCCESS: $description")
            delay(350)
        }

        // Optional step 5: Time sync / handshake (0304)
        AppLog.d(tag, "Executing Optional Init Step (0304)...")
        sendCommandFrameWithRetry(gatt, Protocol.INIT_CMD_OPTIONAL, maxRetries = 1, timeoutMs = 1500L)
        delay(200)

        _initState.value = InitState.Success
        AppLog.i(tag, "Init sequence SUCCESS: SR16 ring initialized")
        return true
    }

    // =========================================================================
    // 4. Command Frame Transmission with Retries & Exponential Backoff
    // =========================================================================

    /**
     * Transmits a command frame over characteristic 0xB002 with single retry and exponential backoff.
     */
    suspend fun sendCommandFrameWithRetry(
        gatt: BluetoothGatt,
        payload: ByteArray,
        maxRetries: Int = 1,
        timeoutMs: Long = 2500L
    ): Boolean {
        var attempt = 0
        var currentTimeout = timeoutMs

        while (attempt <= maxRetries) {
            attempt++
            val ok = sendRawFrame(gatt, payload, currentTimeout)
            if (ok) return true

            if (attempt <= maxRetries) {
                AppLog.w(tag, "Command write timed out on attempt $attempt; retrying in 1000ms with backoff...")
                delay(1000L)
                currentTimeout = (currentTimeout * 1.5).toLong()
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendRawFrame(
        gatt: BluetoothGatt,
        payload: ByteArray,
        timeoutMs: Long
    ): Boolean = writeMutex.withLock {
        val char = writeCharacteristic ?: run {
            AppLog.e(tag, "Write characteristic 0xB002 is not configured")
            return false
        }

        val frame = buildCommandFrame(payload)
        val txHex = frame.joinToString(" ") { "%02X".format(it) }
        lastTxHex = txHex
        AppLog.lastTxHex = txHex

        // Always log every TX frame as hex
        AppLog.i(tag, "TX (${frame.size} bytes): $txHex")

        val deferred = CompletableDeferred<ByteArray>()
        pendingResponse = deferred

        @Suppress("DEPRECATION")
        char.value = frame
        @Suppress("DEPRECATION")
        val writeInitiated = gatt.writeCharacteristic(char)

        if (!writeInitiated) {
            AppLog.e(tag, "BLE write failed (stack rejected command) on ${char.uuid}")
            pendingResponse = null
            return false
        }

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
        if (result != null) {
            val rxHex = result.joinToString(" ") { "%02X".format(it) }
            AppLog.i(tag, "RX completed pending response (${result.size} bytes): $rxHex")
        } else {
            AppLog.d(tag, "No immediate notify response received within ${timeoutMs}ms; write accepted by stack")
        }
        pendingResponse = null
        return true // Write was delivered; ring may send asynchronous notifications
    }
}
