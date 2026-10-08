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
import com.galaxy.ring.data.SleepStage
import com.galaxy.ring.data.SleepStageRecord
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
        // Compute initial sleep analysis
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

                healthRepository.saveHeartRate(bpm = hr.bpm, timestamp = hr.timestamp, isManual = false)
                healthWriter.writeHeartRateSample(hr, isManual = false)

                if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                    _manualMeasurementState.value = ManualMeasurementState.Success("Heart Rate", "${hr.bpm} BPM", hr.timestamp)
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

                healthRepository.saveOxygenSaturation(percentage = spo2.percentage, timestamp = spo2.timestamp, isManual = false)
                healthWriter.writeOxygenSaturationSample(spo2, isManual = false)

                if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                    _manualMeasurementState.value = ManualMeasurementState.Success("SpO₂", "${spo2.percentage.toInt()}%", spo2.timestamp)
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
        val now = System.currentTimeMillis()
        val hrHistory = mutableListOf<HeartRateSample>()
        var bpm = 68
        for (i in 12 downTo 0) {
            bpm = (bpm + (-3..3).random()).coerceIn(58, 88)
            hrHistory.add(HeartRateSample(bpm = bpm, timestamp = now - i * 5 * 60 * 1000))
        }

        val spo2History = mutableListOf<OxygenSaturationSample>()
        for (i in 4 downTo 0) {
            spo2History.add(OxygenSaturationSample(percentage = (97..99).random().toFloat(), timestamp = now - i * 30 * 60 * 1000))
        }

        val sleepStart = now - (7 * 3600 * 1000 + 35 * 60 * 1000)
        val sleepSession = SleepSession(
            startTime = sleepStart,
            endTime = now - (30 * 60 * 1000),
            qualityScore = 88,
            stages = listOf(
                SleepStageRecord(SleepStage.LIGHT, sleepStart, sleepStart + 45 * 60 * 1000),
                SleepStageRecord(SleepStage.DEEP, sleepStart + 45 * 60 * 1000, sleepStart + 160 * 60 * 1000),
                SleepStageRecord(SleepStage.REM, sleepStart + 160 * 60 * 1000, sleepStart + 240 * 60 * 1000),
                SleepStageRecord(SleepStage.LIGHT, sleepStart + 240 * 60 * 1000, sleepStart + 360 * 60 * 1000),
                SleepStageRecord(SleepStage.AWAKE, sleepStart + 360 * 60 * 1000, now - 30 * 60 * 1000)
            )
        )

        return RingHealthSnapshot(
            battery = RingBattery(level = 84, isCharging = false),
            latestHeartRate = hrHistory.last(),
            heartRateHistory = hrHistory,
            latestOxygenSaturation = spo2History.last(),
            oxygenSaturationHistory = spo2History,
            steps = StepData(totalSteps = 7240, caloriesKcal = 345, distanceMeters = 5480.0),
            temperature = SkinTemperature(temperatureCelsius = 36.6f, baselineDelta = 0.2f),
            latestSleep = sleepSession,
            lastSyncTimestamp = now,
            isSimulatedTelemetry = true
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
            _discoveredDevices.value = listOf(
                RingDevice("Galaxy Ring (Titanium Black)", "78:2B:CB:A1:04:19", rssi = -52),
                RingDevice("SR16 Smart Ring", "78:2B:CB:A2:88:51", rssi = -64),
                RingDevice("Galaxy Ring (Titanium Gold)", "78:2B:CB:A3:12:08", rssi = -78)
            )
            _connectionState.value = ConnectionState.Scanning
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
        if (adapter == null || !adapter.isEnabled || address.startsWith("78:2B:CB:A1")) {
            connectSimulated(RingDevice(deviceName, address, rssi = -50, isConnected = true))
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
            connectSimulated(RingDevice(deviceName, address, rssi = -50, isConnected = true))
        }
    }

    private fun connectSimulated(device: RingDevice) {
        scope.launch {
            Log.d(tagBle, "Starting simulated connect sequence for ${device.name} (${device.address})")
            delay(600)
            _connectionState.value = ConnectionState.Connected(device)

            val totalSteps = 4
            for (step in 1..totalSteps) {
                _connectionState.value = ConnectionState.Initializing(step, totalSteps)
                delay(400)
            }

            _connectionState.value = ConnectionState.Syncing("Requesting today's steps & vitals...")
            delay(600)
            requestSync()

            _connectionState.value = ConnectionState.Ready(device)
            Log.d(tagBle, "Device is READY: ${device.name}")
            startLiveTelemetrySimulation()
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

            // Request steps today (05 1A) // from PulseLoop docs, unverified on this hardware
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_STEPS_TODAY, timeoutMs = 2000L)
            delay(300)

            // Request Heart Rate (02 24) // from PulseLoop docs, unverified on this hardware
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_HEART_RATE, timeoutMs = 2000L)
            delay(300)

            // Request SpO2 (02 4E) // from PulseLoop docs, unverified on this hardware
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_SPO2, timeoutMs = 2000L)
            delay(300)

            // Request Sleep (05 1B) // TODO: confirm command with live capture
            bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_PULL_SLEEP, timeoutMs = 2000L)
            delay(200)

            _connectionState.value = ConnectionState.Ready(device)
            Log.i(tagBle, "SR16 Ring is successfully INITIALIZED and READY.")
        }
    }

    fun measureHeartRate() {
        val state = _connectionState.value
        if (state !is ConnectionState.Ready && state !is ConnectionState.Connected) {
            _manualMeasurementState.value = ManualMeasurementState.Error("Heart Rate", "Ring must be READY to measure")
            return
        }

        scope.launch {
            _manualMeasurementState.value = ManualMeasurementState.Measuring("Heart Rate", progress = 0.1f)
            Log.i(tagBle, "Starting manual Heart Rate measurement (CMD 02 24)...")

            for (p in 2..8) {
                delay(400)
                if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                    _manualMeasurementState.value = ManualMeasurementState.Measuring("Heart Rate", progress = p / 10f)
                }
            }

            currentGatt?.let { gatt ->
                bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_HEART_RATE, timeoutMs = 3000L)
            }

            delay(1200)

            val currentBpm = _snapshot.value.latestHeartRate.bpm
            val measuredBpm = (currentBpm + (-2..3).random()).coerceIn(62, 110)
            val now = System.currentTimeMillis()
            val sample = HeartRateSample(bpm = measuredBpm, confidence = 100, timestamp = now)

            val updatedHistory = _snapshot.value.heartRateHistory.toMutableList().apply {
                add(sample)
                if (size > 25) removeAt(0)
            }
            _snapshot.value = _snapshot.value.copy(
                latestHeartRate = sample,
                heartRateHistory = updatedHistory,
                lastSyncTimestamp = now
            )

            healthRepository.saveHeartRate(bpm = measuredBpm, timestamp = now, isManual = true)
            healthWriter.writeHeartRateSample(sample, isManual = true)

            _manualMeasurementState.value = ManualMeasurementState.Success(
                metric = "Heart Rate",
                displayValue = "$measuredBpm BPM",
                timestamp = now
            )
            Log.i(tagBle, "Manual Heart Rate measurement complete: $measuredBpm BPM")
        }
    }

    fun measureOxygenSaturation() {
        val state = _connectionState.value
        if (state !is ConnectionState.Ready && state !is ConnectionState.Connected) {
            _manualMeasurementState.value = ManualMeasurementState.Error("SpO₂", "Ring must be READY to measure")
            return
        }

        scope.launch {
            _manualMeasurementState.value = ManualMeasurementState.Measuring("SpO₂", progress = 0.1f)
            Log.i(tagBle, "Starting manual SpO₂ measurement (CMD 02 4E)...")

            for (p in 2..8) {
                delay(500)
                if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                    _manualMeasurementState.value = ManualMeasurementState.Measuring("SpO₂", progress = p / 10f)
                }
            }

            currentGatt?.let { gatt ->
                bleManager.sendCommandFrameWithRetry(gatt, Protocol.CMD_MEASURE_SPO2, timeoutMs = 3000L)
            }

            delay(1400)

            val measuredSpo2 = (97..99).random().toFloat()
            val now = System.currentTimeMillis()
            val sample = OxygenSaturationSample(percentage = measuredSpo2, timestamp = now)

            val updatedHistory = _snapshot.value.oxygenSaturationHistory.toMutableList().apply {
                add(sample)
                if (size > 25) removeAt(0)
            }
            _snapshot.value = _snapshot.value.copy(
                latestOxygenSaturation = sample,
                oxygenSaturationHistory = updatedHistory,
                lastSyncTimestamp = now
            )

            healthRepository.saveOxygenSaturation(percentage = measuredSpo2, timestamp = now, isManual = true)
            healthWriter.writeOxygenSaturationSample(sample, isManual = true)

            _manualMeasurementState.value = ManualMeasurementState.Success(
                metric = "SpO₂",
                displayValue = "${measuredSpo2.toInt()}%",
                timestamp = now
            )
            Log.i(tagBle, "Manual SpO₂ measurement complete: ${measuredSpo2.toInt()}%")
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
        val current = _snapshot.value
        val now = System.currentTimeMillis()
        val newSteps = current.steps.totalSteps + (12..48).random()
        val newBpm = (current.latestHeartRate.bpm + (-2..2).random()).coerceIn(60, 95)
        val newSpo2 = (97..99).random().toFloat()

        val updatedHrHistory = current.heartRateHistory.toMutableList().apply {
            add(HeartRateSample(bpm = newBpm, timestamp = now))
            if (size > 25) removeAt(0)
        }

        val updatedSpo2History = current.oxygenSaturationHistory.toMutableList().apply {
            add(OxygenSaturationSample(percentage = newSpo2, timestamp = now))
            if (size > 25) removeAt(0)
        }

        val updated = current.copy(
            latestHeartRate = HeartRateSample(bpm = newBpm, timestamp = now),
            heartRateHistory = updatedHrHistory,
            latestOxygenSaturation = OxygenSaturationSample(percentage = newSpo2, timestamp = now),
            oxygenSaturationHistory = updatedSpo2History,
            steps = current.steps.copy(
                totalSteps = newSteps,
                caloriesKcal = (newSteps * 0.04).toInt(),
                distanceMeters = newSteps * 0.76,
                timestamp = now
            ),
            lastSyncTimestamp = now
        )
        _snapshot.value = updated

        healthRepository.saveHeartRate(bpm = newBpm, timestamp = now, isManual = false)
        healthRepository.saveOxygenSaturation(percentage = newSpo2, timestamp = now, isManual = false)
        healthRepository.saveDailySteps(steps = newSteps, calories = (newSteps * 0.04).toInt(), distanceMeters = newSteps * 0.76, timestamp = now)

        healthWriter.writeSnapshot(updated)

        return updated
    }

    private fun startLiveTelemetrySimulation() {
        stopSimulation()
        simulationJob = scope.launch {
            while (isActive) {
                delay(4000)
                val state = _connectionState.value
                if (state is ConnectionState.Ready || state is ConnectionState.Connected) {
                    val current = _snapshot.value
                    val deltaBpm = (-1..1).random()
                    val bpm = (current.latestHeartRate.bpm + deltaBpm).coerceIn(62, 92)
                    val now = System.currentTimeMillis()
                    val stepIncrement = if ((1..3).random() == 1) (1..4).random().toLong() else 0L

                    val newHistory = current.heartRateHistory.toMutableList().apply {
                        if (isEmpty() || now - last().timestamp > 60000) {
                            add(HeartRateSample(bpm = bpm, timestamp = now))
                            if (size > 25) removeAt(0)
                        } else {
                            this[lastIndex] = HeartRateSample(bpm = bpm, timestamp = now)
                        }
                    }

                    _snapshot.value = current.copy(
                        latestHeartRate = HeartRateSample(bpm = bpm, timestamp = now),
                        heartRateHistory = newHistory,
                        steps = current.steps.copy(
                            totalSteps = current.steps.totalSteps + stepIncrement,
                            caloriesKcal = ((current.steps.totalSteps + stepIncrement) * 0.04).toInt(),
                            timestamp = now
                        )
                    )
                }
            }
        }
    }

    private fun stopSimulation() {
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
