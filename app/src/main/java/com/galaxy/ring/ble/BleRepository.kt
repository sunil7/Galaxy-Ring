package com.galaxy.ring.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.galaxy.ring.data.ConnectionState
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.ManualMeasurementState
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.RingBattery
import com.galaxy.ring.data.RingDevice
import com.galaxy.ring.data.RingHealthRepository
import com.galaxy.ring.data.RingHealthSnapshot
import com.galaxy.ring.data.SkinTemperature
import com.galaxy.ring.data.SleepAnalyzer
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.StepData
import com.galaxy.ring.debug.AppLog
import com.galaxy.ring.health.HealthConnectWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BleRepository(
    private val context: Context,
    val healthRepository: RingHealthRepository,
    val healthWriter: HealthConnectWriter
) {

    private val tagBle = "GalaxyRingBLE"
    private val tagSync = "GalaxyRingSync"
    private val tagSleep = "GalaxyRingSleep"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Dedicated BLE Manager for command framing, initialization, and notification observer
    val bleManager = GalaxyRingBLEManager(context, scope)

    private var currentGatt: BluetoothGatt? = null

    val currentGattAddress: String?
        get() = currentGatt?.device?.address ?: healthRepository.lastConnectedAddress

    val currentGattName: String?
        get() = currentGatt?.device?.name ?: healthRepository.lastConnectedName

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<RingDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RingDevice>> = _discoveredDevices.asStateFlow()

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    val snapshot: StateFlow<RingHealthSnapshot> = _snapshot.asStateFlow()

    private val _manualMeasurementState = MutableStateFlow<ManualMeasurementState>(ManualMeasurementState.Idle)
    val manualMeasurementState: StateFlow<ManualMeasurementState> = _manualMeasurementState.asStateFlow()

    val rxCountSinceConnect: StateFlow<Int> = bleManager.rxCountFlow

    private var simulationJob: Job? = null
    private var reconnectJob: Job? = null
    private var isUserDisconnect = false

    /**
     * Checks if all required runtime Bluetooth permissions are granted.
     * Logs permission states to [AppLog].
     */
    fun hasBlePermissions(): Boolean {
        val scanOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        val connectOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        AppLog.i(tagBle, "Permission status check: BLUETOOTH_SCAN=$scanOk, BLUETOOTH_CONNECT=$connectOk (Android API ${Build.VERSION.SDK_INT})")
        return scanOk && connectOk
    }

    init {
        // Observe decoded biometric data from GalaxyRingBLEManager
        observeBLEManagerData()
    }

    private fun observeBLEManagerData() {
        // 1. Observe Heart Rate
        scope.launch {
            bleManager.heartRateFlow.collect { hr ->
                AppLog.i(tagSync, "Heart Rate received from BLEManager: ${hr.bpm} BPM")
                val updatedHistory = _snapshot.value.heartRateHistory.toMutableList().apply {
                    add(hr)
                    if (size > 25) removeAt(0)
                }
                _snapshot.value = _snapshot.value.copy(latestHeartRate = hr, heartRateHistory = updatedHistory)

                val measuringHR = (_manualMeasurementState.value as? ManualMeasurementState.Measuring)?.metric == "Heart Rate"
                if (!measuringHR) {
                    healthRepository.saveHeartRate(bpm = hr.bpm, timestamp = hr.timestamp, isManual = false)
                    healthWriter.writeHeartRateSample(hr, isManual = false)
                }
            }
        }

        // 2. Observe SpO₂
        scope.launch {
            bleManager.spo2Flow.collect { spo2 ->
                AppLog.i(tagSync, "SpO₂ received from BLEManager: ${spo2.percentage}%")
                val updatedHistory = _snapshot.value.oxygenSaturationHistory.toMutableList().apply {
                    add(spo2)
                    if (size > 25) removeAt(0)
                }
                _snapshot.value = _snapshot.value.copy(latestOxygenSaturation = spo2, oxygenSaturationHistory = updatedHistory)

                val measuringSpo2 = (_manualMeasurementState.value as? ManualMeasurementState.Measuring)?.metric == "SpO₂"
                if (!measuringSpo2) {
                    healthRepository.saveOxygenSaturation(percentage = spo2.percentage, timestamp = spo2.timestamp, isManual = false)
                    healthWriter.writeOxygenSaturationSample(spo2, isManual = false)
                }
            }
        }

        // 3. Observe Steps
        scope.launch {
            bleManager.stepsFlow.collect { steps ->
                AppLog.i(tagSync, "Steps received from BLEManager: ${steps.totalSteps}")
                _snapshot.value = _snapshot.value.copy(steps = steps)
                healthRepository.saveDailySteps(steps = steps.totalSteps, calories = steps.caloriesKcal, distanceMeters = steps.distanceMeters)
                healthWriter.writeSteps(steps)
            }
        }

        // 4. Observe Sleep Sessions
        scope.launch {
            bleManager.sleepFlow.collect { sleep ->
                AppLog.i(tagSleep, "Sleep session received from BLEManager: ${sleep.durationMinutes} min")
                val analysis = SleepAnalyzer.analyze(sleep, (healthRepository.sleepTargetHours * 60).toLong())
                _snapshot.value = _snapshot.value.copy(latestSleep = sleep, latestSleepAnalysis = analysis)
                healthRepository.saveSleepSession(sleep)
                healthWriter.writeSleepSession(sleep)
            }
        }

        // 5. Observe Initialization Sequence Progress
        scope.launch {
            bleManager.initState.collect { state ->
                when (state) {
                    is GalaxyRingBLEManager.InitState.InProgress -> {
                        _connectionState.value = ConnectionState.Initializing(state.currentStep, state.totalSteps)
                        AppLog.i(tagBle, "Initialization in progress: Step ${state.currentStep}/${state.totalSteps}")
                    }
                    is GalaxyRingBLEManager.InitState.Failed -> {
                        _connectionState.value = ConnectionState.Error(state.error)
                        AppLog.e(tagBle, "Initialization sequence failed: ${state.error}")
                    }
                    is GalaxyRingBLEManager.InitState.Success -> {
                        AppLog.i(tagBle, "Initialization sequence complete")
                    }
                    is GalaxyRingBLEManager.InitState.Idle -> {}
                }
            }
        }
    }

    private fun createInitialSnapshot(): RingHealthSnapshot {
        return RingHealthSnapshot(
            battery = RingBattery(level = 0, isCharging = false, timestamp = 0L),
            latestHeartRate = HeartRateSample(bpm = 0, confidence = 0, timestamp = 0L),
            heartRateHistory = emptyList(),
            latestOxygenSaturation = null,
            oxygenSaturationHistory = emptyList(),
            steps = StepData(totalSteps = 0L, caloriesKcal = 0, distanceMeters = 0.0, timestamp = 0L),
            temperature = SkinTemperature(temperatureCelsius = 0f, baselineDelta = 0f, timestamp = 0L),
            latestSleep = null,
            latestSleepAnalysis = null,
            lastSyncTimestamp = 0L,
            isSimulatedTelemetry = false
        )
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val deviceName = device.name ?: "Unknown BLE Device"
            val address = device.address

            val isRing = deviceName.contains("Galaxy Ring", ignoreCase = true) ||
                    deviceName.contains("SR16", ignoreCase = true) ||
                    deviceName.contains("Ring", ignoreCase = true) ||
                    deviceName.contains("Samsung", ignoreCase = true)

            val currentList = _discoveredDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.address == address }

            val item = RingDevice(
                name = deviceName,
                address = address,
                rssi = result.rssi,
                isConnected = false
            )

            if (existingIndex >= 0) {
                currentList[existingIndex] = item
            } else {
                if (isRing) {
                    AppLog.i(tagBle, "Discovered Smart Ring candidate: $deviceName ($address) RSSI=${result.rssi}")
                    currentList.add(0, item)
                } else {
                    currentList.add(item)
                }
            }
            _discoveredDevices.value = currentList
        }

        override fun onScanFailed(errorCode: Int) {
            val err = "BLE scan failed with error code: $errorCode"
            AppLog.e(tagBle, err)
            _connectionState.value = ConnectionState.Error("BLE scan failed ($errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(onRequestPermissionsNeeded: (() -> Unit)? = null) {
        if (!hasBlePermissions()) {
            val msg = "Missing Bluetooth permissions (BLUETOOTH_SCAN / CONNECT)"
            AppLog.w(tagBle, "startScan ABORTED: $msg. Please grant permissions before scanning.")
            _discoveredDevices.value = emptyList()
            _connectionState.value = ConnectionState.Error("Bluetooth permission required")
            onRequestPermissionsNeeded?.invoke()
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            val msg = "Bluetooth is disabled or unavailable"
            AppLog.w(tagBle, msg)
            _discoveredDevices.value = emptyList()
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
            return
        }

        try {
            AppLog.i(tagBle, "Starting BLE discovery scan...")
            _discoveredDevices.value = emptyList()
            _connectionState.value = ConnectionState.Scanning
            val scanner = adapter.bluetoothLeScanner
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val filters = listOf<ScanFilter>()
            scanner?.startScan(filters, settings, scanCallback)

            mainHandler.postDelayed({
                stopScan()
            }, 15000)
        } catch (e: Exception) {
            AppLog.e(tagBle, "Failed to start BLE scan: ${e.message}", e)
            _connectionState.value = ConnectionState.Error("Scan error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            AppLog.d(tagBle, "Stopping BLE scan")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            if (_connectionState.value is ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }
        } catch (e: Exception) {
            AppLog.e(tagBle, "Error stopping scan: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, deviceName: String = "Galaxy Ring", onRequestPermissionsNeeded: (() -> Unit)? = null) {
        if (!hasBlePermissions()) {
            val msg = "Missing Bluetooth permissions (BLUETOOTH_CONNECT)"
            AppLog.w(tagBle, "connect ABORTED: $msg. Please grant permissions before connecting.")
            _connectionState.value = ConnectionState.Error("Bluetooth permission required")
            onRequestPermissionsNeeded?.invoke()
            return
        }

        isUserDisconnect = false
        reconnectJob?.cancel()
        stopScan()
        _connectionState.value = ConnectionState.Connecting(deviceName)
        AppLog.i(tagBle, "Initiating connection to $deviceName at $address...")

        healthRepository.lastConnectedAddress = address
        healthRepository.lastConnectedName = deviceName

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            val msg = "Bluetooth is off"
            AppLog.w(tagBle, "Cannot connect: $msg")
            _connectionState.value = ConnectionState.Error(msg)
            return
        }

        try {
            val remoteDevice = adapter.getRemoteDevice(address)
            currentGatt?.close()
            currentGatt = remoteDevice.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: Exception) {
            val err = "Connection error: ${e.message}"
            AppLog.e(tagBle, "Failed to connect to $address: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(err)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        AppLog.i(tagBle, "User requested disconnect from ring")
        isUserDisconnect = true
        reconnectJob?.cancel()
        stopSimulation()
        try {
            currentGatt?.disconnect()
            currentGatt?.close()
        } catch (e: Exception) {
            AppLog.e(tagBle, "Error during GATT disconnect: ${e.message}", e)
        }
        currentGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Executes post-connection initialization and initial vitals sync.
     */
    private fun startPostConnectionFlow(gatt: BluetoothGatt, device: RingDevice) {
        scope.launch {
            // 1. Run Initialization Sequence via GalaxyRingBLEManager (tries frame variants & write types)
            bleManager.runInitializationSequence(gatt)

            // 2. Sync Initial Vitals
            _connectionState.value = ConnectionState.Syncing("Syncing steps & latest vitals...")
            delay(200)

            // Request steps today (05 1A)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_STEPS_TODAY, timeoutMs = 1800L)
            delay(250)

            // Request Heart Rate (02 24)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_HEART_RATE, timeoutMs = 1800L)
            delay(250)

            // Request SpO2 (02 4E)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_SPO2, timeoutMs = 1800L)
            delay(250)

            // Request Sleep (05 1B)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_PULL_SLEEP, timeoutMs = 1800L)
            delay(200)

            val rxTotal = bleManager.rxCountSinceConnect
            _connectionState.value = ConnectionState.Ready(device, rxTotal)
            AppLog.i(tagBle, "SR16 Ring marked READY. Total RX frames since connect: $rxTotal")
        }
    }

    private fun validateMeasurementPrerequisites(metric: String): String? {
        val gatt = currentGatt
        if (gatt == null) {
            return "No BLE GATT connection."
        }
        val state = _connectionState.value
        if (state !is ConnectionState.Ready) {
            val stateLabel = when (state) {
                is ConnectionState.Connecting -> "Connecting"
                is ConnectionState.Initializing -> "Initializing"
                is ConnectionState.Scanning -> "Scanning"
                is ConnectionState.Syncing -> "Syncing"
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Error -> "Error"
                else -> "Disconnected"
            }
            return "Not READY (current: $stateLabel). Wait until Ready."
        }
        if (bleManager.writeCharacteristic == null && bleManager.altWriteCharacteristic == null) {
            return "Write characteristic (0xB002/0xFF01) missing."
        }
        if (!bleManager.isNotificationEnabled) {
            return "Notifications not enabled on ring. Check Admin logs."
        }
        return null
    }

    fun measureHeartRate() {
        val prereqError = validateMeasurementPrerequisites("Heart Rate")
        if (prereqError != null) {
            AppLog.e(tagBle, "Measure Heart Rate rejected: $prereqError")
            AppLog.lastMeasureResult = "Heart Rate: Error - $prereqError"
            _manualMeasurementState.value = ManualMeasurementState.Error(
                metric = "Heart Rate",
                message = prereqError
            )
            return
        }

        val gatt = currentGatt ?: return
        scope.launch {
            _manualMeasurementState.value = ManualMeasurementState.Measuring("Heart Rate", progress = 0.05f)
            val startTime = System.currentTimeMillis()
            AppLog.i(tagBle, "Measure Heart Rate START (CMD 02 24)")

            val writeOk = bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_HEART_RATE, timeoutMs = 3000L)
            if (!writeOk) {
                val writeErr = "BLE write failed (stack rejected command)."
                AppLog.e(tagBle, "Measure Heart Rate write error: $writeErr")
                AppLog.lastMeasureResult = "Heart Rate: Error - $writeErr"
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "Heart Rate",
                    message = writeErr
                )
                return@launch
            }

            val maxWaitMs = 20000L
            val pollStepMs = 500L
            val totalSteps = (maxWaitMs / pollStepMs).toInt()
            var sampleFound: HeartRateSample? = null

            for (step in 1..totalSteps) {
                delay(pollStepMs)
                val latest = _snapshot.value.latestHeartRate
                if (latest.timestamp >= startTime && latest.bpm in 30..240) {
                    sampleFound = latest
                    break
                }
                val progress = (step.toFloat() / totalSteps.toFloat()).coerceIn(0.1f, 0.95f)
                if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                    _manualMeasurementState.value = ManualMeasurementState.Measuring("Heart Rate", progress = progress)
                }
            }

            if (sampleFound != null) {
                healthRepository.saveHeartRate(bpm = sampleFound.bpm, timestamp = sampleFound.timestamp, isManual = true)
                healthWriter.writeHeartRateSample(sampleFound, isManual = true)
                val displayStr = "${sampleFound.bpm} BPM"
                _manualMeasurementState.value = ManualMeasurementState.Success(
                    metric = "Heart Rate",
                    displayValue = displayStr,
                    timestamp = sampleFound.timestamp
                )
                AppLog.lastMeasureResult = "Heart Rate: $displayStr (Success)"
                AppLog.i(tagBle, "Manual Heart Rate measurement succeeded: $displayStr")
            } else {
                val rxSince = bleManager.getLastRxSince(startTime)
                val errorReason = if (rxSince != null) {
                    val (byteCount, hex) = rxSince
                    val truncatedHex = if (hex.length > 36) hex.take(33) + "…" else hex
                    "Ring replied ($byteCount bytes) but HR/SpO₂ parse failed. Last RX: $truncatedHex. Export logs."
                } else {
                    val lastKnownRx = bleManager.lastRxHex
                    if (lastKnownRx != null) {
                        val truncatedHex = if (lastKnownRx.length > 36) lastKnownRx.take(33) + "…" else lastKnownRx
                        "No notify from ring within 20s. Last RX: $truncatedHex. See Admin → Logs for TX/RX hex."
                    } else {
                        "No notify from ring within 20s. See Admin → Logs for TX/RX hex."
                    }
                }
                AppLog.w(tagBle, "Manual Heart Rate measurement TIMEOUT (20s): $errorReason")
                AppLog.lastMeasureResult = "Heart Rate: Error - $errorReason"
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "Heart Rate",
                    message = errorReason
                )
            }
        }
    }

    fun measureOxygenSaturation() {
        val prereqError = validateMeasurementPrerequisites("SpO₂")
        if (prereqError != null) {
            AppLog.e(tagBle, "Measure SpO₂ rejected: $prereqError")
            AppLog.lastMeasureResult = "SpO₂: Error - $prereqError"
            _manualMeasurementState.value = ManualMeasurementState.Error(
                metric = "SpO₂",
                message = prereqError
            )
            return
        }

        val gatt = currentGatt ?: return
        scope.launch {
            _manualMeasurementState.value = ManualMeasurementState.Measuring("SpO₂", progress = 0.05f)
            val startTime = System.currentTimeMillis()
            AppLog.i(tagBle, "Measure SpO₂ START (CMD 02 4E)")

            val writeOk = bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_SPO2, timeoutMs = 3000L)
            if (!writeOk) {
                val writeErr = "BLE write failed (stack rejected command)."
                AppLog.e(tagBle, "Measure SpO₂ write error: $writeErr")
                AppLog.lastMeasureResult = "SpO₂: Error - $writeErr"
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "SpO₂",
                    message = writeErr
                )
                return@launch
            }

            val maxWaitMs = 20000L
            val pollStepMs = 500L
            val totalSteps = (maxWaitMs / pollStepMs).toInt()
            var sampleFound: OxygenSaturationSample? = null

            for (step in 1..totalSteps) {
                delay(pollStepMs)
                val latest = _snapshot.value.latestOxygenSaturation
                if (latest != null && latest.timestamp >= startTime && latest.percentage in 70f..100f) {
                    sampleFound = latest
                    break
                }
                val progress = (step.toFloat() / totalSteps.toFloat()).coerceIn(0.1f, 0.95f)
                if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                    _manualMeasurementState.value = ManualMeasurementState.Measuring("SpO₂", progress = progress)
                }
            }

            if (sampleFound != null) {
                healthRepository.saveOxygenSaturation(percentage = sampleFound.percentage, timestamp = sampleFound.timestamp, isManual = true)
                healthWriter.writeOxygenSaturationSample(sampleFound, isManual = true)
                val displayStr = "${sampleFound.percentage.toInt()}%"
                _manualMeasurementState.value = ManualMeasurementState.Success(
                    metric = "SpO₂",
                    displayValue = displayStr,
                    timestamp = sampleFound.timestamp
                )
                AppLog.lastMeasureResult = "SpO₂: $displayStr (Success)"
                AppLog.i(tagBle, "Manual SpO₂ measurement succeeded: $displayStr")
            } else {
                val rxSince = bleManager.getLastRxSince(startTime)
                val errorReason = if (rxSince != null) {
                    val (byteCount, hex) = rxSince
                    val truncatedHex = if (hex.length > 36) hex.take(33) + "…" else hex
                    "Ring replied ($byteCount bytes) but HR/SpO₂ parse failed. Last RX: $truncatedHex. Export logs."
                } else {
                    val lastKnownRx = bleManager.lastRxHex
                    if (lastKnownRx != null) {
                        val truncatedHex = if (lastKnownRx.length > 36) lastKnownRx.take(33) + "…" else lastKnownRx
                        "No notify from ring within 20s. Last RX: $truncatedHex. See Admin → Logs for TX/RX hex."
                    } else {
                        "No notify from ring within 20s. See Admin → Logs for TX/RX hex."
                    }
                }
                AppLog.w(tagBle, "Manual SpO₂ measurement TIMEOUT (20s): $errorReason")
                AppLog.lastMeasureResult = "SpO₂: Error - $errorReason"
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "SpO₂",
                    message = errorReason
                )
            }
        }
    }

    fun resetManualMeasurementState() {
        _manualMeasurementState.value = ManualMeasurementState.Idle
    }

    fun findMyRing(): Boolean {
        val state = _connectionState.value
        if (state !is ConnectionState.Ready && state !is ConnectionState.Connected) return false

        scope.launch {
            currentGatt?.let { gatt ->
                AppLog.i(tagBle, "Sending Find My Ring command (03 05)")
                bleManager.sendCommandFrameWithRetry(gatt, byteArrayOf(0x03, 0x05))
            }
        }
        return true
    }

    suspend fun requestSync(): RingHealthSnapshot {
        val gatt = currentGatt ?: run {
            AppLog.w(tagSync, "No active GATT connection; returning current snapshot unchanged")
            return _snapshot.value
        }

        AppLog.i(tagSync, "Requesting real telemetry sync from SR16 ring...")

        // Steps today (05 1A)
        bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_STEPS_TODAY, timeoutMs = 2000L)
        delay(300)

        // Heart Rate (02 24)
        bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_HEART_RATE, timeoutMs = 2000L)
        delay(300)

        // SpO₂ (02 4E)
        bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_SPO2, timeoutMs = 2000L)
        delay(300)

        // Sleep (05 1B)
        bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_PULL_SLEEP, timeoutMs = 2000L)
        delay(200)

        val now = System.currentTimeMillis()
        val updated = _snapshot.value.copy(
            lastSyncTimestamp = now,
            isSimulatedTelemetry = false
        )
        _snapshot.value = updated
        return updated
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    private fun scheduleAutoReconnect() {
        if (isUserDisconnect) return
        val lastAddr = healthRepository.lastConnectedAddress ?: return
        val lastName = healthRepository.lastConnectedName ?: "Galaxy Ring"

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            AppLog.i(tagBle, "Ring disconnected unexpectedly. Scheduling auto-reconnect backoff...")
            var delayMs = 3000L
            while (isActive && !isUserDisconnect && _connectionState.value is ConnectionState.Disconnected) {
                delay(delayMs)
                AppLog.d(tagBle, "Attempting auto-reconnect to $lastAddr...")
                connect(lastAddr, lastName)
                delayMs = (delayMs * 1.8).toLong().coerceAtMost(30000L)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            val ringDevice = RingDevice(
                name = device.name ?: healthRepository.lastConnectedName ?: "Galaxy Ring",
                address = device.address,
                isConnected = (newState == BluetoothProfile.STATE_CONNECTED)
            )

            val stateString = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "STATE_$newState"
            }
            AppLog.i(tagBle, "onConnectionStateChange: status=$status, newState=$stateString for ${device.address}")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.Connecting(ringDevice.name)
                AppLog.i(tagBle, "Connected to GATT server on ${device.address}. Resetting session and requesting MTU 247...")
                bleManager.resetSession()

                // Request MTU 247 as per Requirement B
                val mtuInitiated = gatt.requestMtu(247)
                AppLog.i(tagBle, "requestMtu(247) initiated: $mtuInitiated")

                // Fallback timer: if onMtuChanged is delayed or skipped by device, discover services
                mainHandler.removeCallbacksAndMessages("MTU_TIMEOUT")
                mainHandler.postDelayed({
                    AppLog.d(tagBle, "MTU fallback trigger: discovering services on ${device.address}...")
                    gatt.discoverServices()
                }, 1200)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.removeCallbacksAndMessages("MTU_TIMEOUT")
                AppLog.w(tagBle, "Disconnected from GATT server")
                _connectionState.value = ConnectionState.Disconnected
                stopSimulation()
                scheduleAutoReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mainHandler.removeCallbacksAndMessages("MTU_TIMEOUT")
            AppLog.i(tagBle, "onMtuChanged: mtu=$mtu, status=$status")
            AppLog.mtuNegotiated = mtu
            mainHandler.post {
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            AppLog.i(tagBle, "onServicesDiscovered: status=$status (${gatt.services.size} services)")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val err = "Service discovery failed ($status)"
                AppLog.e(tagBle, err)
                _connectionState.value = ConnectionState.Error(err)
                return
            }

            val device = RingDevice(gatt.device.name ?: "Galaxy Ring", gatt.device.address, isConnected = true)

            // Attempt to read Battery Level if standard Battery Service (0x180F) is present
            val batteryService = gatt.getService(Protocol.BATTERY_SERVICE_UUID)
            val batteryChar = batteryService?.getCharacteristic(Protocol.BATTERY_LEVEL_CHAR_UUID)
            if (batteryChar != null) {
                AppLog.d(tagBle, "Discovered standard Battery characteristic 0x2A19, reading level...")
                @Suppress("DEPRECATION")
                gatt.readCharacteristic(batteryChar)
            }

            // Setup notification and indication listeners across 0xB003, 0xFF02, 0xFF03, 0x0BC1, 0x0BC2
            scope.launch {
                val observerConfigured = bleManager.setupNotificationObserver(gatt)
                AppLog.i(tagBle, "Broad notification observer configured: $observerConfigured")
                startPostConnectionFlow(gatt, device)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            AppLog.i(tagBle, "onDescriptorWrite [${descriptor.characteristic.uuid} / ${descriptor.uuid}]: status=$status")
            bleManager.onDescriptorWriteCompleted(descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bleManager.markNotificationEnabled(true)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val statusStr = if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "STATUS_$status"
            AppLog.d(tagBle, "onCharacteristicWrite [${characteristic.uuid}]: $statusStr")
            bleManager.onCharacteristicWriteCompleted(characteristic, status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val data = characteristic.value ?: return
            handleIncomingData(characteristic, data)
        }

        // Newer Android 13+ (API 33+) callback
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleIncomingData(characteristic, value)
        }

        // Legacy pre-Android 13 callback
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            handleIncomingData(characteristic, data)
        }

        private fun handleIncomingData(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            bleManager.onNotificationReceived(characteristic, data)
            Protocol.parseBatteryLevel(data)?.let { battery ->
                if (battery.level > 0) {
                    _snapshot.value = _snapshot.value.copy(battery = battery)
                }
            }
        }
    }
}
