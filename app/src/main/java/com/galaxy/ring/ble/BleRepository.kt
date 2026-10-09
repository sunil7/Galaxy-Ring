package com.galaxy.ring.ble

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
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val healthRepository: RingHealthRepository,
    private val healthWriter: HealthConnectWriter
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

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<RingDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RingDevice>> = _discoveredDevices.asStateFlow()

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    val snapshot: StateFlow<RingHealthSnapshot> = _snapshot.asStateFlow()

    private val _manualMeasurementState = MutableStateFlow<ManualMeasurementState>(ManualMeasurementState.Idle)
    val manualMeasurementState: StateFlow<ManualMeasurementState> = _manualMeasurementState.asStateFlow()

    private var simulationJob: Job? = null
    private var reconnectJob: Job? = null
    private var isUserDisconnect = false

    init {
        // Compute initial sleep analysis if available
        val initialSleep = _snapshot.value.latestSleep
        if (initialSleep != null) {
            val analysis = SleepAnalyzer.analyze(initialSleep, (healthRepository.sleepTargetHours * 60).toLong())
            _snapshot.value = _snapshot.value.copy(latestSleepAnalysis = analysis)
        }

        // Observe decoded biometric data from GalaxyRingBLEManager
        observeBLEManagerData()
    }

    private fun observeBLEManagerData() {
        // 1. Observe Heart Rate
        scope.launch {
            bleManager.heartRateFlow.collect { hr ->
                Log.i(tagSync, "Heart Rate received from BLEManager: ${hr.bpm} BPM")
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
                Log.i(tagSync, "SpO₂ received from BLEManager: ${spo2.percentage}%")
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
                Log.i(tagSync, "Steps received from BLEManager: ${steps.totalSteps}")
                _snapshot.value = _snapshot.value.copy(steps = steps)
                healthRepository.saveDailySteps(steps = steps.totalSteps, calories = steps.caloriesKcal, distanceMeters = steps.distanceMeters)
                healthWriter.writeSteps(steps)
            }
        }

        // 4. Observe Sleep Sessions
        scope.launch {
            bleManager.sleepFlow.collect { sleep ->
                Log.i(tagSleep, "Sleep session received from BLEManager: ${sleep.durationMinutes} min")
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
                    }
                    is GalaxyRingBLEManager.InitState.Failed -> {
                        _connectionState.value = ConnectionState.Error(state.error)
                    }
                    is GalaxyRingBLEManager.InitState.Success -> {
                        // Handshake complete
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
                    currentList.add(0, item)
                } else {
                    currentList.add(item)
                }
            }
            _discoveredDevices.value = currentList
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(tagBle, "Scan failed with error code: $errorCode")
            _connectionState.value = ConnectionState.Error("BLE scan failed ($errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(tagBle, "Bluetooth is disabled or unavailable")
            _discoveredDevices.value = emptyList()
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
            return
        }

        try {
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
            Log.e(tagBle, "Failed to start BLE scan", e)
            _connectionState.value = ConnectionState.Error("Scan error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            if (_connectionState.value is ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }
        } catch (e: Exception) {
            Log.e(tagBle, "Error stopping scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, deviceName: String = "Galaxy Ring") {
        isUserDisconnect = false
        reconnectJob?.cancel()
        stopScan()
        _connectionState.value = ConnectionState.Connecting(deviceName)

        healthRepository.lastConnectedAddress = address
        healthRepository.lastConnectedName = deviceName

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(tagBle, "Cannot connect: Bluetooth is disabled or unavailable")
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
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
            Log.e(tagBle, "Connection failed to $address", e)
            _connectionState.value = ConnectionState.Error("Connection error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isUserDisconnect = true
        reconnectJob?.cancel()
        stopSimulation()
        try {
            currentGatt?.disconnect()
            currentGatt?.close()
        } catch (e: Exception) {
            Log.e(tagBle, "Error disconnecting GATT", e)
        }
        currentGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Executes post-connection initialization and initial vitals sync.
     */
    private fun startPostConnectionFlow(gatt: BluetoothGatt, device: RingDevice) {
        scope.launch {
            // 1. Run Initialization Sequence (0302 -> 0202 -> 0201 -> 0263) via GalaxyRingBLEManager
            val initSuccess = bleManager.runInitializationSequence(gatt)
            if (!initSuccess) {
                Log.e(tagBle, "Post-connection initialization sequence aborted")
                return@launch
            }

            // 2. Sync Initial Vitals
            _connectionState.value = ConnectionState.Syncing("Syncing steps & latest vitals...")
            delay(200)

            // Request steps today (05 1A)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_STEPS_TODAY, timeoutMs = 2000L)
            delay(300)

            // Request Heart Rate (02 24)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_HEART_RATE, timeoutMs = 2000L)
            delay(300)

            // Request SpO2 (02 4E)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_SPO2, timeoutMs = 2000L)
            delay(300)

            // Request Sleep (05 1B)
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_PULL_SLEEP, timeoutMs = 2000L)
            delay(200)

            _connectionState.value = ConnectionState.Ready(device)
            Log.i(tagBle, "SR16 Ring is successfully INITIALIZED and READY.")
        }
    }

    fun measureHeartRate() {
        val state = _connectionState.value
        val gatt = currentGatt
        if (state !is ConnectionState.Ready || gatt == null) {
            _manualMeasurementState.value = ManualMeasurementState.Error(
                metric = "Heart Rate",
                message = "Ring must be READY to measure"
            )
            return
        }

        scope.launch {
            _manualMeasurementState.value = ManualMeasurementState.Measuring("Heart Rate", progress = 0.05f)
            val startTime = System.currentTimeMillis()
            Log.i(tagBle, "Sending manual Heart Rate measurement command (CMD 02 24)...")

            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_HEART_RATE, timeoutMs = 3000L)

            val maxWaitMs = 20000L
            val pollStepMs = 500L
            val totalSteps = (maxWaitMs / pollStepMs).toInt()
            var sampleFound: HeartRateSample? = null

            for (step in 1..totalSteps) {
                delay(pollStepMs)
                val latest = _snapshot.value.latestHeartRate
                if (latest.timestamp > startTime && latest.bpm in 30..240) {
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
                _manualMeasurementState.value = ManualMeasurementState.Success(
                    metric = "Heart Rate",
                    displayValue = "${sampleFound.bpm} BPM",
                    timestamp = sampleFound.timestamp
                )
                Log.i(tagBle, "Manual Heart Rate measurement succeeded: ${sampleFound.bpm} BPM")
            } else {
                Log.w(tagBle, "Manual Heart Rate measurement timed out after ${maxWaitMs / 1000}s")
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "Heart Rate",
                    message = "No response from ring (check logcat GalaxyRingBLE for TX/RX hex)"
                )
            }
        }
    }

    fun measureOxygenSaturation() {
        val state = _connectionState.value
        val gatt = currentGatt
        if (state !is ConnectionState.Ready || gatt == null) {
            _manualMeasurementState.value = ManualMeasurementState.Error(
                metric = "SpO₂",
                message = "Ring must be READY to measure"
            )
            return
        }

        scope.launch {
            _manualMeasurementState.value = ManualMeasurementState.Measuring("SpO₂", progress = 0.05f)
            val startTime = System.currentTimeMillis()
            Log.i(tagBle, "Sending manual SpO₂ measurement command (CMD 02 4E)...")

            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_SPO2, timeoutMs = 3000L)

            val maxWaitMs = 20000L
            val pollStepMs = 500L
            val totalSteps = (maxWaitMs / pollStepMs).toInt()
            var sampleFound: OxygenSaturationSample? = null

            for (step in 1..totalSteps) {
                delay(pollStepMs)
                val latest = _snapshot.value.latestOxygenSaturation
                if (latest != null && latest.timestamp > startTime && latest.percentage in 70f..100f) {
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
                _manualMeasurementState.value = ManualMeasurementState.Success(
                    metric = "SpO₂",
                    displayValue = "${sampleFound.percentage.toInt()}%",
                    timestamp = sampleFound.timestamp
                )
                Log.i(tagBle, "Manual SpO₂ measurement succeeded: ${sampleFound.percentage.toInt()}%")
            } else {
                Log.w(tagBle, "Manual SpO₂ measurement timed out after ${maxWaitMs / 1000}s")
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "SpO₂",
                    message = "No response from ring (check logcat GalaxyRingBLE for TX/RX hex)"
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
                bleManager.sendCommandFrameWithRetry(gatt, byteArrayOf(0x03, 0x05))
            }
        }
        return true
    }

    suspend fun requestSync(): RingHealthSnapshot {
        val gatt = currentGatt ?: run {
            Log.w(tagSync, "No active GATT connection; returning current snapshot unchanged")
            return _snapshot.value
        }

        Log.i(tagSync, "Requesting real telemetry sync from SR16 ring...")

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
            Log.i(tagBle, "Ring disconnected unexpectedly. Scheduling auto-reconnect backoff...")
            var delayMs = 3000L
            while (isActive && !isUserDisconnect && _connectionState.value is ConnectionState.Disconnected) {
                delay(delayMs)
                Log.d(tagBle, "Attempting auto-reconnect to $lastAddr...")
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

            Log.d(tagBle, "onConnectionStateChange: status=$status, newState=$newState for ${device.address}")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.Connecting(ringDevice.name)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected
                stopSimulation()
                scheduleAutoReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(tagBle, "onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Error("Service discovery failed ($status)")
                return
            }

            val device = RingDevice(gatt.device.name ?: "Galaxy Ring", gatt.device.address, isConnected = true)

            // Setup notification observer on characteristic 0xB003 via GalaxyRingBLEManager
            val observerConfigured = bleManager.setupNotificationObserver(gatt)
            if (!observerConfigured) {
                Log.w(tagBle, "0xB003 notification observer configuration deferred; starting init flow")
                startPostConnectionFlow(gatt, device)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(tagBle, "onDescriptorWrite: ${descriptor.uuid}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val device = RingDevice(gatt.device.name ?: "Galaxy Ring", gatt.device.address, isConnected = true)
                startPostConnectionFlow(gatt, device)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val data = characteristic.value ?: return
            bleManager.onNotificationReceived(characteristic, data)
            Protocol.parseBatteryLevel(data)?.let { battery ->
                _snapshot.value = _snapshot.value.copy(battery = battery)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            bleManager.onNotificationReceived(characteristic, data)
            Protocol.parseBatteryLevel(data)?.let { battery ->
                _snapshot.value = _snapshot.value.copy(battery = battery)
            }
        }
    }
}
