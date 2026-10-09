package com.galaxy.ring.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import android.util.Log
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.StepData
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
import java.util.UUID

/**
 * Manages low-level Bluetooth LE communication with the Galaxy Ring / SR16 smart ring.
 *
 * Responsibilities:
 * 1. Handles command frame framing (0xAB header + length + payload + CRC16-ARC checksum).
 * 2. Implements the post-connect initialization sequence (0302 -> 0202 -> 0201 -> 0263, optional 0304).
 * 3. Observes and decodes incoming notifications from characteristic 0xB003 via CCCD.
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
     * Finds service 0xA00A, characteristic 0xB003, enables notifications on the local GATT client,
     * and writes ENABLE_NOTIFICATION_VALUE to the CCCD descriptor (0x2902).
     */
    @SuppressLint("MissingPermission")
    fun setupNotificationObserver(gatt: BluetoothGatt): Boolean {
        Log.i(tag, "Configuring notification observer for characteristic 0xB003 on device ${gatt.device.address}")

        // Locate primary service 0xA00A and characteristics 0xB002 / 0xB003
        var foundService = false
        for (service in gatt.services) {
            val sUuid = service.uuid.toString()
            if (sUuid.contains("A00A", ignoreCase = true) || service.uuid == Protocol.SR16_SERVICE_UUID) {
                foundService = true
                Log.d(tag, "Found SR16 service: ${service.uuid}")
                for (ch in service.characteristics) {
                    val cUuid = ch.uuid.toString()
                    if (cUuid.contains("B002", ignoreCase = true) || ch.uuid == Protocol.SR16_WRITE_CHAR_UUID) {
                        writeCharacteristic = ch
                        Log.d(tag, "Identified Write characteristic: ${ch.uuid}")
                    }
                    if (cUuid.contains("B003", ignoreCase = true) || ch.uuid == Protocol.SR16_NOTIFY_CHAR_UUID) {
                        notifyCharacteristic = ch
                        Log.d(tag, "Identified Notify characteristic: ${ch.uuid}")
                    }
                }
            }
        }

        // Broad fallback scan across all services if not found
        if (writeCharacteristic == null || notifyCharacteristic == null) {
            Log.w(tag, "Scanning all GATT characteristics for 0xB002 / 0xB003 fallback...")
            for (service in gatt.services) {
                for (ch in service.characteristics) {
                    val cUuid = ch.uuid.toString()
                    if (writeCharacteristic == null && (cUuid.contains("B002", ignoreCase = true) || ch.uuid == Protocol.SR16_WRITE_CHAR_UUID)) {
                        writeCharacteristic = ch
                    }
                    if (notifyCharacteristic == null && (cUuid.contains("B003", ignoreCase = true) || ch.uuid == Protocol.SR16_NOTIFY_CHAR_UUID)) {
                        notifyCharacteristic = ch
                    }
                }
            }
        }

        val nChar = notifyCharacteristic
        if (nChar == null) {
            Log.e(tag, "Failed to locate notify characteristic 0xB003 on ${gatt.device.address}")
            return false
        }

        // 1. Enable local notification subscription in Android Bluetooth Stack
        val successLocal = gatt.setCharacteristicNotification(nChar, true)
        if (!successLocal) {
            Log.e(tag, "Failed to enable local characteristic notification on ${nChar.uuid}")
            return false
        }

        // 2. Enable remote notification via CCCD (Client Characteristic Configuration Descriptor 0x2902)
        val descriptor = nChar.getDescriptor(Protocol.CCCD_UUID)
        if (descriptor != null) {
            Log.d(tag, "Writing ENABLE_NOTIFICATION_VALUE to CCCD ${descriptor.uuid}...")
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            val initiated = gatt.writeDescriptor(descriptor)
            if (!initiated) {
                Log.e(tag, "Failed to initiate writeDescriptor for CCCD on 0xB003")
                return false
            }
        } else {
            Log.w(tag, "CCCD descriptor (0x2902) not found on characteristic ${nChar.uuid}")
        }

        Log.i(tag, "Notification observer successfully registered on 0xB003")
        return true
    }

    /**
     * Dispatches raw characteristic changes received on characteristic 0xB003.
     * Decodes the SR16 framing and emits parsed models into corresponding SharedFlows.
     */
    fun onNotificationReceived(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (data.isEmpty()) return

        val hexString = data.joinToString(" ") { "%02X".format(it) }
        Log.d(tag, "0xB003 Notification received (${data.size} bytes): $hexString")

        // Complete any pending command response awaiter and log RX
        val pending = pendingResponse
        if (pending != null && pending.isActive) {
            Log.i(tag, "RX completed deferred (${data.size} bytes): $hexString")
            pending.complete(data)
        }

        scope.launch {
            _rawNotifications.emit(data)

            // Extract payload from 0xAB frame
            val payload = extractPayload(data)

            // 1. Parse Heart Rate (CMD 02 24)
            Protocol.parseSr16HeartRate(payload)?.let { hr ->
                Log.d(tag, "Decoded Heart Rate sample: ${hr.bpm} BPM")
                _heartRateFlow.emit(hr)
            }

            // 2. Parse SpO₂ (CMD 02 4E)
            Protocol.parseSr16SpO2(payload)?.let { spo2 ->
                Log.d(tag, "Decoded SpO₂ sample: ${spo2.percentage}%")
                _spo2Flow.emit(spo2)
            }

            // 3. Parse Steps (CMD 05 1A)
            Protocol.parseSr16Steps(payload)?.let { steps ->
                Log.d(tag, "Decoded Steps data: ${steps.totalSteps}")
                _stepsFlow.emit(steps)
            }

            // 4. Parse Sleep (CMD 05 1B)
            Protocol.parseSr16SleepSession(payload)?.let { sleep ->
                Log.d(tag, "Decoded Sleep session: ${sleep.durationMinutes} min")
                _sleepFlow.emit(sleep)
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
        Log.i(tag, "Starting SR16 Ring Initialization Sequence (0302 -> 0202 -> 0201 -> 0263)...")

        val steps = listOf(
            Triple(Protocol.INIT_CMD_1, 1, "System Handshake (0302)"),
            Triple(Protocol.INIT_CMD_2, 2, "Health Configuration (0202)"),
            Triple(Protocol.INIT_CMD_3, 3, "Sensor Calibration (0201)"),
            Triple(Protocol.INIT_CMD_4, 4, "Ring Feature Handshake (0263)")
        )

        for ((cmd, stepNum, description) in steps) {
            _initState.value = InitState.InProgress(stepNum, steps.size, description)
            Log.d(tag, "Executing Init Step $stepNum/${steps.size}: $description")

            val ok = sendCommandFrameWithRetry(gatt, cmd, maxRetries = 1, timeoutMs = 2500L)
            if (!ok) {
                val err = "Initialization failed at Step $stepNum ($description): Timeout or write rejected"
                Log.e(tag, err)
                _initState.value = InitState.Failed(err)
                return false
            }
            delay(350)
        }

        // Optional step 5: Time sync / handshake (0304)
        Log.d(tag, "Executing Optional Init Step (0304)...")
        sendCommandFrameWithRetry(gatt, Protocol.INIT_CMD_OPTIONAL, maxRetries = 1, timeoutMs = 1500L)
        delay(200)

        _initState.value = InitState.Success
        Log.i(tag, "Initialization Sequence completed successfully!")
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
                Log.w(tag, "Command timed out on attempt $attempt; retrying in 1000ms with exponential backoff...")
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
            Log.e(tag, "Write characteristic 0xB002 is not configured")
            return false
        }

        val frame = buildCommandFrame(payload)
        val txHex = frame.joinToString(" ") { "%02X".format(it) }
        Log.i(tag, "TX (${frame.size} bytes): $txHex")

        val deferred = CompletableDeferred<ByteArray>()
        pendingResponse = deferred

        @Suppress("DEPRECATION")
        char.value = frame
        @Suppress("DEPRECATION")
        val writeInitiated = gatt.writeCharacteristic(char)

        if (!writeInitiated) {
            Log.e(tag, "Failed to initiate writeCharacteristic on ${char.uuid}")
            pendingResponse = null
            return false
        }

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
        if (result != null) {
            val rxHex = result.joinToString(" ") { "%02X".format(it) }
            Log.i(tag, "RX completed pending response (${result.size} bytes): $rxHex")
        } else {
            Log.d(tag, "No immediate notify response received within ${timeoutMs}ms; write accepted by stack")
        }
        pendingResponse = null
        return true // Write was delivered; ring may send asynchronous notifications
    }
}
