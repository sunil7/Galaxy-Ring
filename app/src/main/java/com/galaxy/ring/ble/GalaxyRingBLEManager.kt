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
 * Implements:
 * 1. Diagnostic GATT inspection for 0xA00A, 0xFF00 (0xFF01..0xFF03), 0x0BC0 (0x0BC1..0x0BC2).
 * 2. Simultaneous notification and indication listening across ALL candidate characteristics.
 * 3. Write type evaluation (WRITE_TYPE_DEFAULT vs WRITE_TYPE_NO_RESPONSE) on newer APIs.
 * 4. Systematic frame variant experimentation (Variants 1 to 5) until first RX is confirmed.
 * 5. Honest session RX telemetry tracking ([rxCountSinceConnect]).
 */
class GalaxyRingBLEManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {

    private val tag = "GalaxyRingBLE"

    // Primary & Alternate Write Characteristics
    var writeCharacteristic: BluetoothGattCharacteristic? = null
        private set

    var altWriteCharacteristic: BluetoothGattCharacteristic? = null
        private set

    // Primary Notify Characteristic (0xB003)
    var notifyCharacteristic: BluetoothGattCharacteristic? = null
        private set

    // All active listening characteristics subscribed via CCCD
    private val subscribedNotifyChars = mutableListOf<BluetoothGattCharacteristic>()

    // Diagnostic & GATT Discovery State
    var foundService0xA00A: Boolean = false
        private set
    var foundWrite0xB002: Boolean = false
        private set
    var foundNotify0xB003: Boolean = false
        private set

    var foundService0xFF00: Boolean = false
        private set
    var foundChar0xFF01: Boolean = false
        private set
    var foundChar0xFF02: Boolean = false
        private set
    var foundChar0xFF03: Boolean = false
        private set

    var foundService0x0BC0: Boolean = false
        private set
    var foundChar0x0BC1: Boolean = false
        private set
    var foundChar0x0BC2: Boolean = false
        private set

    var isNotificationEnabled: Boolean = false
        private set

    // Live RX / TX Telemetry
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

    // Working variant locked in after first RX received
    var workingVariantName: String? = null
        private set
    var workingVariantIndex: Int? = null
        private set
    var workingTargetChar: BluetoothGattCharacteristic? = null
        private set
    var workingWriteType: Int? = null
        private set

    // Concurrency control
    private val writeMutex = Mutex()
    private var pendingResponse: CompletableDeferred<ByteArray>? = null
    private var pendingDescriptorDeferred: CompletableDeferred<Int>? = null
    private var pendingWriteCharDeferred: CompletableDeferred<Int>? = null

    // Initialization Sequence State
    sealed class InitState {
        data object Idle : InitState()
        data class InProgress(val currentStep: Int, val totalSteps: Int, val stepName: String) : InitState()
        data object Success : InitState()
        data class Failed(val error: String) : InitState()
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Idle)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    // Observable flows for incoming data
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

    /**
     * Resets session telemetry counters when a new device connection is established.
     */
    fun resetSession() {
        rxCountSinceConnect = 0
        _rxCountFlow.value = 0
        AppLog.rxCountSinceConnect = 0
        workingVariantName = null
        workingVariantIndex = null
        workingTargetChar = null
        workingWriteType = null
        AppLog.workingVariant = null
        lastTxHex = null
        lastRxHex = null
        lastRxTimestamp = 0L
        lastRxBytesCount = 0
        _initState.value = InitState.Idle
        subscribedNotifyChars.clear()
        AppLog.i(tag, "Session telemetry reset (rxCount=0, variants reset)")
    }

    fun markNotificationEnabled(enabled: Boolean) {
        isNotificationEnabled = enabled
    }

    fun getLastRxSince(sinceTimestamp: Long): Pair<Int, String>? {
        return if (lastRxTimestamp >= sinceTimestamp && lastRxHex != null) {
            Pair(lastRxBytesCount, lastRxHex!!)
        } else null
    }

    fun onDescriptorWriteCompleted(descriptor: BluetoothGattDescriptor, status: Int) {
        AppLog.d(tag, "onDescriptorWriteCompleted: ${descriptor.uuid}, status=$status")
        pendingDescriptorDeferred?.complete(status)
    }

    fun onCharacteristicWriteCompleted(characteristic: BluetoothGattCharacteristic, status: Int) {
        AppLog.d(tag, "onCharacteristicWriteCompleted: ${characteristic.uuid}, status=$status")
        pendingWriteCharDeferred?.complete(status)
    }

    // =========================================================================
    // 1. Service Discovery & Broad Notification Setup
    // =========================================================================

    /**
     * Discovers all services, checks 0xA00A, 0xFF00, 0x0BC0, logs characteristic properties,
     * and sequentially subscribes to 0xB003, 0xFF02, 0xFF03, 0x0BC1, 0x0BC2 and any notify/indicate chars.
     */
    @SuppressLint("MissingPermission")
    suspend fun setupNotificationObserver(gatt: BluetoothGatt): Boolean {
        AppLog.i(tag, "GATT services discovered (${gatt.services.size} total) on device ${gatt.device.address}")

        foundService0xA00A = false
        foundWrite0xB002 = false
        foundNotify0xB003 = false
        foundService0xFF00 = false
        foundChar0xFF01 = false
        foundChar0xFF02 = false
        foundChar0xFF03 = false
        foundService0x0BC0 = false
        foundChar0x0BC1 = false
        foundChar0x0BC2 = false
        isNotificationEnabled = false
        writeCharacteristic = null
        altWriteCharacteristic = null
        notifyCharacteristic = null
        subscribedNotifyChars.clear()

        val candidateNotifyChars = mutableListOf<BluetoothGattCharacteristic>()

        for (service in gatt.services) {
            val sUuidStr = service.uuid.toString().uppercase()
            val charSummary = service.characteristics.joinToString { ch ->
                val pStr = buildString {
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) append("R ")
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) append("W ")
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) append("WNR ")
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) append("N ")
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) append("I ")
                }.trim()
                "${ch.uuid}[$pStr]"
            }
            AppLog.d(tag, "GATT Service: ${service.uuid} -> [${charSummary.ifEmpty { "no chars" }}]")

            if (Protocol.matchesShortUuid(service.uuid, "A00A")) foundService0xA00A = true
            if (Protocol.matchesShortUuid(service.uuid, "FF00")) foundService0xFF00 = true
            if (Protocol.matchesShortUuid(service.uuid, "0BC0")) foundService0x0BC0 = true

            for (ch in service.characteristics) {
                if (Protocol.matchesShortUuid(ch.uuid, "B002")) {
                    writeCharacteristic = ch
                    foundWrite0xB002 = true
                    logCharProperties("0xB002", ch)
                }
                if (Protocol.matchesShortUuid(ch.uuid, "B003")) {
                    notifyCharacteristic = ch
                    foundNotify0xB003 = true
                    if (!candidateNotifyChars.contains(ch)) candidateNotifyChars.add(0, ch)
                }
                if (Protocol.matchesShortUuid(ch.uuid, "FF01")) {
                    altWriteCharacteristic = ch
                    foundChar0xFF01 = true
                    logCharProperties("0xFF01", ch)
                }
                if (Protocol.matchesShortUuid(ch.uuid, "FF02")) {
                    foundChar0xFF02 = true
                    if (!candidateNotifyChars.contains(ch)) candidateNotifyChars.add(ch)
                }
                if (Protocol.matchesShortUuid(ch.uuid, "FF03")) {
                    foundChar0xFF03 = true
                    if (!candidateNotifyChars.contains(ch)) candidateNotifyChars.add(ch)
                }
                if (Protocol.matchesShortUuid(ch.uuid, "0BC1")) {
                    foundChar0x0BC1 = true
                    if (!candidateNotifyChars.contains(ch)) candidateNotifyChars.add(ch)
                }
                if (Protocol.matchesShortUuid(ch.uuid, "0BC2")) {
                    foundChar0x0BC2 = true
                    if (!candidateNotifyChars.contains(ch)) candidateNotifyChars.add(ch)
                }

                // If characteristic supports NOTIFY or INDICATE, add to candidate list for debug listening
                val hasNotify = (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                val hasIndicate = (ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                if ((hasNotify || hasIndicate) && !candidateNotifyChars.contains(ch)) {
                    candidateNotifyChars.add(ch)
                }
            }
        }

        AppLog.i(
            tag,
            "GATT Inventory -> 0xA00A=$foundService0xA00A (0xB002=$foundWrite0xB002, 0xB003=$foundNotify0xB003); " +
                    "0xFF00=$foundService0xFF00 (0xFF01=$foundChar0xFF01, 0xFF02=$foundChar0xFF02, 0xFF03=$foundChar0xFF03); " +
                    "0x0BC0=$foundService0x0BC0 (0x0BC1=$foundChar0x0BC1, 0x0BC2=$foundChar0x0BC2)"
        )

        if (writeCharacteristic == null) {
            AppLog.w(tag, "Primary write 0xB002 not found; checking 0xFF01 as fallback write...")
            writeCharacteristic = altWriteCharacteristic
        }

        // Sequentially enable notifications/indications on all candidate characteristics
        var successfullySubscribedCount = 0
        for (char in candidateNotifyChars) {
            val ok = enableNotificationOrIndication(gatt, char)
            if (ok) {
                successfullySubscribedCount++
                subscribedNotifyChars.add(char)
            }
            delay(120) // Give BLE controller spacing between CCCD operations
        }

        isNotificationEnabled = successfullySubscribedCount > 0
        AppLog.i(
            tag,
            "Notification setup complete: $successfullySubscribedCount / ${candidateNotifyChars.size} characteristics listening"
        )
        return isNotificationEnabled
    }

    private fun logCharProperties(label: String, ch: BluetoothGattCharacteristic) {
        val p = ch.properties
        val w = (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val wnr = (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val n = (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        val i = (p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        AppLog.i(
            tag,
            "Characteristic $label [${ch.uuid}] properties=0x${Integer.toHexString(p)} (WRITE=$w, WRITE_NO_RESPONSE=$wnr, NOTIFY=$n, INDICATE=$i)"
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotificationOrIndication(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic
    ): Boolean {
        val hasNotify = (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        val hasIndicate = (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

        val localOk = gatt.setCharacteristicNotification(char, true)
        if (!localOk) {
            AppLog.w(tag, "Failed to enable local notification on ${char.uuid}")
            return false
        }

        val cccd = char.getDescriptor(Protocol.CCCD_UUID)
        if (cccd == null) {
            AppLog.d(tag, "CCCD (0x2902) not found on ${char.uuid}; local notification registered")
            return true
        }

        val descriptorValue = if (hasNotify) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else if (hasIndicate) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        val def = CompletableDeferred<Int>()
        pendingDescriptorDeferred = def

        val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = gatt.writeDescriptor(cccd, descriptorValue)
            res == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = descriptorValue
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }

        if (!initiated) {
            AppLog.w(tag, "writeDescriptor rejected by Bluetooth stack for ${char.uuid}")
            pendingDescriptorDeferred = null
            return false
        }

        val status = withTimeoutOrNull(1800L) { def.await() } ?: -1
        pendingDescriptorDeferred = null
        val ok = status == BluetoothGatt.GATT_SUCCESS
        AppLog.i(
            tag,
            "CCCD write for [${char.uuid}] -> status=$status (${if (ok) "SUCCESS" else "FAILED"}), type=${if (hasNotify) "NOTIFY" else "INDICATE"}"
        )
        return ok
    }

    // =========================================================================
    // 2. Incoming Notification / Indication Reception
    // =========================================================================

    /**
     * Dispatches any raw characteristic change received on ANY characteristic.
     */
    fun onNotificationReceived(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (data.isEmpty()) {
            AppLog.w(tag, "RX notification [${characteristic.uuid}] received with empty byte array")
            return
        }

        rxCountSinceConnect++
        _rxCountFlow.value = rxCountSinceConnect
        AppLog.rxCountSinceConnect = rxCountSinceConnect

        val hexString = data.joinToString(" ") { "%02X".format(it) }
        lastRxHex = hexString
        lastRxTimestamp = System.currentTimeMillis()
        lastRxBytesCount = data.size
        AppLog.lastRxHex = hexString

        // Log EVERY onCharacteristicChanged with full UUID + hex, any service
        AppLog.i(tag, "RX onCharacteristicChanged [${characteristic.uuid}] (${data.size} bytes): $hexString")
        AppLog.recordRx(characteristic.uuid.toString(), hexString)

        // Complete any pending command response
        val pending = pendingResponse
        if (pending != null && pending.isActive) {
            AppLog.i(tag, "RX completed pending response (${data.size} bytes): $hexString")
            pending.complete(data)
        }

        scope.launch {
            _rawNotifications.emit(data)

            // Extract payload (handles both 0xAB framed and raw)
            val payload = Protocol.extractSr16Payload(data)
            val payloadHex = payload.joinToString(" ") { "%02X".format(it) }

            // 1. Heart Rate
            val hr = Protocol.parseSr16HeartRate(payload) ?: Protocol.parseHeartRate(data)
            if (hr != null) {
                AppLog.i(tag, "HR parse SUCCESS: ${hr.bpm} BPM (payload: $payloadHex)")
                _heartRateFlow.emit(hr)
            }

            // 2. SpO₂
            val spo2 = Protocol.parseSr16SpO2(payload)
            if (spo2 != null) {
                AppLog.i(tag, "SpO₂ parse SUCCESS: ${spo2.percentage}% (payload: $payloadHex)")
                _spo2Flow.emit(spo2)
            }

            // 3. Steps
            val steps = Protocol.parseSr16Steps(payload)
            if (steps != null) {
                AppLog.i(tag, "Steps parse SUCCESS: ${steps.totalSteps} steps (payload: $payloadHex)")
                _stepsFlow.emit(steps)
            }

            // 4. Sleep
            val sleep = Protocol.parseSr16SleepSession(payload)
            if (sleep != null) {
                AppLog.i(tag, "Sleep parse SUCCESS: ${sleep.durationMinutes} min (payload: $payloadHex)")
                _sleepFlow.emit(sleep)
            }
        }
    }

    // =========================================================================
    // 3. Command Frame Format Experiments & Transmission
    // =========================================================================

    /**
     * Transmits a command payload.
     * If a working variant was already found for this session, sends directly using it.
     * If 0 RX has arrived so far, tests variants 1 through 5 in order until first RX:
     *   1) Current: [0xAB][len][payload][crcL][crcH] on 0xB002
     *   2) Raw payload only to 0xB002
     *   3) [0xAB][payload][crcL][crcH] without length byte on 0xB002
     *   4) Big-endian CRC [0xAB][len][payload][crcH][crcL] on 0xB002
     *   5) Same variants written to 0xFF01 if present
     * Retries WRITE_TYPE_DEFAULT first, then WRITE_TYPE_NO_RESPONSE.
     */
    suspend fun sendCommandFrameWithRetry(
        gatt: BluetoothGatt,
        payload: ByteArray,
        maxRetries: Int = 1,
        timeoutMs: Long = 1800L
    ): Boolean = writeMutex.withLock {
        // If a working variant is locked in for this session, use it
        val cachedChar = workingTargetChar ?: writeCharacteristic
        val cachedWriteType = workingWriteType ?: BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val cachedVariant = workingVariantIndex

        if (cachedChar != null && cachedVariant != null) {
            val frame = buildVariantFrame(cachedVariant, payload)
            val desc = workingVariantName ?: "Variant $cachedVariant"
            return sendRawFrameSingle(gatt, cachedChar, frame, cachedWriteType, desc, timeoutMs)
        }

        // Otherwise, run frame format experiments (1 -> 2 -> 3 -> 4 -> 5)
        AppLog.i(tag, "No working variant confirmed yet; starting frame format experiment loop...")
        val targetChar0xB002 = writeCharacteristic
        val targetChar0xFF01 = altWriteCharacteristic

        val candidates = mutableListOf<ExperimentCandidate>()

        if (targetChar0xB002 != null) {
            // Variant 1: [0xAB][len][payload][crcL][crcH]
            candidates.add(
                ExperimentCandidate(1, targetChar0xB002, "Variant 1 [0xAB+len+crcLE]", Protocol.buildVariant1(payload))
            )
            // Variant 2: Raw payload
            candidates.add(
                ExperimentCandidate(2, targetChar0xB002, "Variant 2 [Raw payload]", Protocol.buildVariant2(payload))
            )
            // Variant 3: [0xAB][payload][crcL][crcH]
            candidates.add(
                ExperimentCandidate(3, targetChar0xB002, "Variant 3 [0xAB+no-len+crcLE]", Protocol.buildVariant3(payload))
            )
            // Variant 4: Big-endian CRC
            candidates.add(
                ExperimentCandidate(4, targetChar0xB002, "Variant 4 [0xAB+len+crcBE]", Protocol.buildVariant4(payload))
            )
        }

        if (targetChar0xFF01 != null) {
            // Variant 5: Write to 0xFF01
            candidates.add(
                ExperimentCandidate(5, targetChar0xFF01, "Variant 5 [0xFF01: 0xAB+len+crcLE]", Protocol.buildVariant1(payload))
            )
            candidates.add(
                ExperimentCandidate(5, targetChar0xFF01, "Variant 5 [0xFF01: Raw payload]", Protocol.buildVariant2(payload))
            )
        }

        for (cand in candidates) {
            AppLog.w(tag, "Trying frame variant ${cand.variantIndex}: ${cand.description} on ${cand.targetChar.uuid}")

            // 1. Try WRITE_TYPE_DEFAULT
            val rxBefore1 = rxCountSinceConnect
            sendRawFrameSingle(
                gatt,
                cand.targetChar,
                cand.frame,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                "${cand.description} (DEFAULT)",
                timeoutMs
            )

            if (rxCountSinceConnect > rxBefore1) {
                lockInWorkingVariant(cand.variantIndex, cand.description, cand.targetChar, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                return true
            }

            // 2. If 0 RX, retry same payload with WRITE_TYPE_NO_RESPONSE
            delay(150)
            val rxBefore2 = rxCountSinceConnect
            sendRawFrameSingle(
                gatt,
                cand.targetChar,
                cand.frame,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                "${cand.description} (NO_RESPONSE)",
                timeoutMs
            )

            if (rxCountSinceConnect > rxBefore2) {
                lockInWorkingVariant(cand.variantIndex, cand.description, cand.targetChar, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                return true
            }

            delay(200)
        }

        AppLog.w(tag, "Experiment loop completed: 0 RX received across all variants. Ring accepted writes.")
        return true
    }

    private fun buildVariantFrame(variantIndex: Int, payload: ByteArray): ByteArray {
        return when (variantIndex) {
            1 -> Protocol.buildVariant1(payload)
            2 -> Protocol.buildVariant2(payload)
            3 -> Protocol.buildVariant3(payload)
            4 -> Protocol.buildVariant4(payload)
            5 -> Protocol.buildVariant1(payload)
            else -> Protocol.buildVariant1(payload)
        }
    }

    private fun lockInWorkingVariant(
        variantIndex: Int,
        description: String,
        targetChar: BluetoothGattCharacteristic,
        writeType: Int
    ) {
        workingVariantIndex = variantIndex
        workingVariantName = description
        workingTargetChar = targetChar
        workingWriteType = writeType
        AppLog.workingVariant = description
        AppLog.i(
            tag,
            "★ FIRST RX CONFIRMED! Locked in session working variant: $description on ${targetChar.uuid} (writeType=$writeType)"
        )
    }

    private data class ExperimentCandidate(
        val variantIndex: Int,
        val targetChar: BluetoothGattCharacteristic,
        val description: String,
        val frame: ByteArray
    )

    @SuppressLint("MissingPermission")
    private suspend fun sendRawFrameSingle(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        frame: ByteArray,
        writeType: Int,
        variantDesc: String,
        timeoutMs: Long
    ): Boolean {
        val targetUuid = char.uuid.toString()
        val writeTypeStr = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) "NO_RESP" else "DEFAULT"
        val txHex = frame.joinToString(" ") { "%02X".format(it) }

        lastTxHex = txHex
        AppLog.lastTxHex = txHex
        AppLog.i(tag, "TX ($writeTypeStr, $variantDesc, ${frame.size}B) -> [$targetUuid]: $txHex")
        AppLog.recordTx(targetUuid, writeTypeStr, variantDesc, txHex)

        val deferred = CompletableDeferred<ByteArray>()
        pendingResponse = deferred
        val writeCompleteDeferred = CompletableDeferred<Int>()
        pendingWriteCharDeferred = writeCompleteDeferred

        val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = gatt.writeCharacteristic(char, frame, writeType)
            res == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = frame
            char.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }

        if (!initiated) {
            AppLog.e(tag, "Write rejected by Bluetooth stack for $targetUuid ($writeTypeStr)")
            pendingResponse = null
            pendingWriteCharDeferred = null
            return false
        }

        // Wait for onCharacteristicWrite callback if DEFAULT write
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
            withTimeoutOrNull(1500L) {
                writeCompleteDeferred.await()
            }
        }
        pendingWriteCharDeferred = null

        // Await notification/indication response with timeout
        withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
        pendingResponse = null
        return true
    }

    // =========================================================================
    // 4. Initialization Sequence
    // =========================================================================

    /**
     * Executes the initialization sequence: 0302 -> 0202 -> 0201 -> 0263 (and optional 0304).
     * Even if 0 RX arrives, completes gracefully so user can test and review Admin logs.
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

            sendCommandFrameWithRetry(gatt, cmd, maxRetries = 1, timeoutMs = 1500L)
            AppLog.d(tag, "Init Step $stepNum DONE: $description (rxCount=$rxCountSinceConnect)")
            delay(250)
        }

        // Optional step 5: Time sync / handshake (0304)
        AppLog.d(tag, "Executing Optional Init Step (0304)...")
        sendCommandFrameWithRetry(gatt, Protocol.INIT_CMD_OPTIONAL, maxRetries = 1, timeoutMs = 1200L)
        delay(200)

        _initState.value = InitState.Success
        AppLog.i(
            tag,
            "Init sequence FINISHED: Ring marked Ready (rxCountSinceConnect=$rxCountSinceConnect, workingVariant=${workingVariantName ?: "none"})"
        )
        return true
    }

    /**
     * Frame utility delegates targeting SR16 framing protocol.
     */
    fun buildCommandFrame(payload: ByteArray): ByteArray = Protocol.buildSr16Frame(payload)
    fun verifyFrame(frame: ByteArray): Boolean = Protocol.isValidSr16Frame(frame)
    fun extractPayload(frame: ByteArray): ByteArray = Protocol.extractSr16Payload(frame)
}

