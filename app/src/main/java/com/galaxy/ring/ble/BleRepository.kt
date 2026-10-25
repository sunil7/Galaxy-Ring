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
import com.galaxy.ring.data.HrvSample
import com.galaxy.ring.data.ManualMeasurementState
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.ScreenBrightness
import com.galaxy.ring.data.StressSample
import com.galaxy.ring.data.RingBattery
import com.galaxy.ring.data.RingDevice
import com.galaxy.ring.data.RingHealthRepository
import com.galaxy.ring.data.RingHealthSnapshot
import com.galaxy.ring.data.SkinTemperature
import com.galaxy.ring.data.SleepAnalyzer
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.StepData
import com.galaxy.ring.data.SyncResult
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        get() = try {
            currentGatt?.device?.address ?: healthRepository.lastConnectedAddress
        } catch (_: Exception) {
            healthRepository.lastConnectedAddress
        }

    val currentGattName: String?
        get() = try {
            currentGatt?.device?.name ?: healthRepository.lastConnectedName
        } catch (_: Exception) {
            healthRepository.lastConnectedName
        }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<RingDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RingDevice>> = _discoveredDevices.asStateFlow()

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    val snapshot: StateFlow<RingHealthSnapshot> = _snapshot.asStateFlow()

    private val _manualMeasurementState = MutableStateFlow<ManualMeasurementState>(ManualMeasurementState.Idle)
    val manualMeasurementState: StateFlow<ManualMeasurementState> = _manualMeasurementState.asStateFlow()

    val rxCountSinceConnect: StateFlow<Int> = bleManager.rxCountFlow

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgressMessage = MutableStateFlow<String?>(null)
    val syncProgressMessage: StateFlow<String?> = _syncProgressMessage.asStateFlow()

    private var lastAutoSyncTimestamp: Long = 0L

    val lastWriteError: String? get() = healthWriter.lastWriteError
    val lastWriteTime: Long? get() = healthWriter.lastWriteTime
    val lastWriteStatus: String get() = healthWriter.lastWriteStatus

    private var simulationJob: Job? = null
    private var reconnectJob: Job? = null
    private var isUserDisconnect = false

    /**
     * True when the user explicitly disconnected from the ring during this session.
     * Read by the scheduled/keep-alive loop (RingScheduledService) so it does not
     * auto-reconnect against the user's choice. Cleared when connect() is called.
     * Read-only: this property does not write any preference.
     */
    val isUserDisconnected: Boolean
        get() = isUserDisconnect

    private var mtuFallbackRunnable: Runnable? = null

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

    private val bondReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                val stateName = when (bondState) {
                    BluetoothDevice.BOND_BONDED -> "BONDED"
                    BluetoothDevice.BOND_BONDING -> "BONDING"
                    BluetoothDevice.BOND_NONE -> "BOND_NONE"
                    else -> "BOND_$bondState"
                }
                AppLog.i(tagBle, "Bluetooth bond state changed for ${device?.address}: $stateName (prev=$prevBondState)")
            }
        }
    }

    init {
        try {
            val filter = android.content.IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(bondReceiver, filter)
        } catch (e: Exception) {
            AppLog.e(tagBle, "Failed to register bondReceiver: ${e.message}")
        }
        // Observe decoded biometric data from GalaxyRingBLEManager
        observeBLEManagerData()
    }

    private var lastObservedHrBpm: Int? = null
    private var lastObservedHrTimestamp: Long = 0L
    private var lastObservedSpo2Percentage: Float? = null
    private var lastObservedSpo2Timestamp: Long = 0L

    private fun observeBLEManagerData() {
        // 1. Observe Heart Rate (in-memory streaming only for live UI; single final row saved on completion)
        scope.launch {
            bleManager.heartRateFlow.collect { hr ->
                // Range check: reject values outside 30..240 bpm. Drop or clamp, do not emit.
                if (hr.bpm !in 30..240) {
                    AppLog.w(tagSync, "Dropping out-of-range HR live sample: ${hr.bpm} BPM (valid bounds: 30..240)")
                    return@collect
                }
                // Spike filter: drop single-sample spikes (delta > 40 bpm from previous sample when interval < 2 s)
                val prevBpm = lastObservedHrBpm
                val prevTime = lastObservedHrTimestamp
                val interval = hr.timestamp - prevTime
                if (prevBpm != null && kotlin.math.abs(hr.bpm - prevBpm) > 40 && interval in 0 until 2000L) {
                    AppLog.w(
                        tagSync,
                        "Dropping HR live spike: ${hr.bpm} BPM (delta: ${kotlin.math.abs(hr.bpm - prevBpm)} > 40 from $prevBpm BPM, interval: ${interval}ms < 2000ms)"
                    )
                    return@collect
                }
                lastObservedHrBpm = hr.bpm
                lastObservedHrTimestamp = hr.timestamp

                AppLog.i(tagSync, "Heart Rate live sample from 0224: ${hr.bpm} BPM")
                val updatedHistory = _snapshot.value.heartRateHistory.toMutableList().apply {
                    add(hr)
                    if (size > 25) removeAt(0)
                }
                _snapshot.value = _snapshot.value.copy(latestHeartRate = hr, heartRateHistory = updatedHistory)
            }
        }

        // 2. Observe SpO₂ (in-memory streaming only for live UI; single final row saved on completion)
        scope.launch {
            bleManager.spo2Flow.collect { spo2 ->
                // Range check: reject values outside 70..100%. Drop, do not emit.
                if (spo2.percentage !in 70f..100f) {
                    AppLog.w(tagSync, "Dropping out-of-range SpO2 live sample: ${spo2.percentage}% (valid bounds: 70..100)")
                    return@collect
                }
                // Spike filter: drop single-sample spikes (delta > 8% from previous sample when interval < 2 s)
                val prevSpo2 = lastObservedSpo2Percentage
                val prevTime = lastObservedSpo2Timestamp
                val interval = spo2.timestamp - prevTime
                if (prevSpo2 != null && kotlin.math.abs(spo2.percentage - prevSpo2) > 8f && interval in 0 until 2000L) {
                    AppLog.w(
                        tagSync,
                        "Dropping SpO2 live spike: ${spo2.percentage}% (delta: ${kotlin.math.abs(spo2.percentage - prevSpo2)} > 8 from $prevSpo2%, interval: ${interval}ms < 2000ms)"
                    )
                    return@collect
                }
                lastObservedSpo2Percentage = spo2.percentage
                lastObservedSpo2Timestamp = spo2.timestamp

                AppLog.i(tagSync, "SpO₂ live sample from 024E: ${spo2.percentage}%")
                val updatedHistory = _snapshot.value.oxygenSaturationHistory.toMutableList().apply {
                    add(spo2)
                    if (size > 25) removeAt(0)
                }
                _snapshot.value = _snapshot.value.copy(latestOxygenSaturation = spo2, oxygenSaturationHistory = updatedHistory)
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

        // 4b. Observe Workout Sessions
        scope.launch {
            bleManager.workoutFlow.collect { workouts ->
                AppLog.i(tagSync, "Workout sessions received from BLEManager: ${workouts.size} session(s)")
                healthRepository.saveWorkoutSessions(workouts)
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
        mtuFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        mtuFallbackRunnable = null
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
        mtuFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        mtuFallbackRunnable = null
        reconnectJob?.cancel()
        stopSimulation()
        try {
            currentGatt?.disconnect()
            currentGatt?.close()
        } catch (e: Exception) {
            AppLog.e(tagBle, "Error during GATT disconnect: ${e.message}", e)
        }
        currentGatt = null
        bleManager.resetSession()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Executes post-connection initialization and initial vitals sync.
     */
    private fun startPostConnectionFlow(gatt: BluetoothGatt, device: RingDevice) {
        if (bleManager.initStarted) {
            AppLog.i(tagBle, "startPostConnectionFlow: Init already started for this session. Ignoring duplicate call.")
            return
        }
        scope.launch {
            try {
                // 1. Run strict Initialization Sequence via GalaxyRingBLEManager
                _connectionState.value = ConnectionState.Initializing(1, 4)
                val initOk = bleManager.runInitializationSequence(gatt)
                val rxTotal = bleManager.rxCountSinceConnect

                if (!initOk || rxTotal == 0) {
                    val failReason = bleManager.lastFailedInitStep ?: "0 RX frames received from ring during init"
                    AppLog.e(tagBle, "Initialization failed: $failReason. Remaining in Error state.")
                    _connectionState.value = ConnectionState.Error("Init failed: $failReason. Export logs.")
                    return@launch
                }

                // Only set Ready if real RX was received during init!
                _connectionState.value = ConnectionState.Ready(device, rxTotal)
                AppLog.i(tagBle, "Galaxy Ring marked READY. Total RX frames since connect: $rxTotal")

                // Re-apply brightness setting after each successful init
                scope.launch {
                    delay(300)
                    val settings = MonitoringSettings(context)
                    setScreenBrightness(settings.screenBrightness)
                }

                // Automatically trigger sync once after init reaches Ready (debounced 30s)
                val nowTime = System.currentTimeMillis()
                if (nowTime - lastAutoSyncTimestamp > 30000L) {
                    lastAutoSyncTimestamp = nowTime
                    scope.launch {
                        delay(1200)
                        AppLog.i(tagSync, "Auto-triggering telemetry sync after reaching READY...")
                        syncRingData(isAutomatic = true)
                    }
                }

                // B2: Optional Bluetooth bonding after successful initialization
                if (healthRepository.isBondingEnabled) {
                    try {
                        val btDevice = gatt.device
                        if (btDevice != null && btDevice.bondState == BluetoothDevice.BOND_NONE) {
                            AppLog.i(tagBle, "Bonding enabled in settings and ring ${btDevice.address} is not bonded. Calling createBond()...")
                            val bondStarted = btDevice.createBond()
                            AppLog.i(tagBle, "createBond() result for ${btDevice.address}: $bondStarted")
                        } else if (btDevice != null && btDevice.bondState == BluetoothDevice.BOND_BONDED) {
                            AppLog.i(tagBle, "Ring ${btDevice.address} is already BONDED.")
                        }
                    } catch (e: Exception) {
                        AppLog.e(tagBle, "Error checking/triggering bonding: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception during startPostConnectionFlow: ${e.message}", e)
                _connectionState.value = ConnectionState.Error("Post-connect failed: ${e.message ?: "Unknown error"}")
            }
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
        if (bleManager.writeCharacteristic == null) {
            return "Write characteristic (0xB002) missing."
        }
        val hasNotificationsOrRx = bleManager.isNotificationEnabled || bleManager.cccdEnabled || bleManager.rxCountSinceConnect > 0
        if (!hasNotificationsOrRx) {
            return "Notifications not enabled on ring (RX count: 0). Check Admin logs."
        }
        return null
    }

    fun measureHeartRate() {
        scope.launch {
            executeHeartRateMeasurement(isManual = true)
        }
    }

    suspend fun executeHeartRateMeasurement(isManual: Boolean): HeartRateSample? {
        val prereqError = validateMeasurementPrerequisites("Heart Rate")
        if (prereqError != null) {
            AppLog.e(tagBle, "Measure Heart Rate rejected: $prereqError")
            AppLog.lastMeasureResult = "Heart Rate: Error - $prereqError"
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "Heart Rate",
                    message = prereqError
                )
            }
            return null
        }

        val gatt = currentGatt ?: return null
        return try {
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Measuring("Heart Rate", progress = 0.05f)
            }
            val startTime = System.currentTimeMillis()
            AppLog.i(tagBle, "Measure Heart Rate START (0609 type=0x03, isManual=$isManual)")

            val startOk = bleManager.startHeartRateMeasurement(gatt)
            if (!startOk) {
                val writeErr = "Ring rejected Heart Rate start command (0609)."
                AppLog.e(tagBle, "Measure Heart Rate write error: $writeErr")
                AppLog.lastMeasureResult = "Heart Rate: Error - $writeErr"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Error(
                        metric = "Heart Rate",
                        message = writeErr
                    )
                }
                return null
            }

            // Wait up to ~60s or until reading arrives
            val maxWaitMs = 60000L
            val pollStepMs = 500L
            val totalSteps = (maxWaitMs / pollStepMs).toInt()
            var latestNonzeroHr: HeartRateSample? = null

            var statusCompleteReceived = false
            val completionJob = scope.launch {
                bleManager.measureCompletedFlow.collect { type ->
                    if (type == Protocol.MEASURE_TYPE_HR || type == 0x00.toByte()) {
                        statusCompleteReceived = true
                    }
                }
            }

            for (step in 1..totalSteps) {
                delay(pollStepMs)
                val current = _snapshot.value.latestHeartRate
                if (current != null && current.timestamp >= startTime && current.bpm in 30..240) {
                    latestNonzeroHr = current
                }
                if (statusCompleteReceived || !bleManager.isMeasuringActive) {
                    AppLog.i(tagBle, "Heart Rate measurement finished by ring 0609 status complete")
                    break
                }
                if (isManual) {
                    val progress = (step.toFloat() / totalSteps.toFloat()).coerceIn(0.05f, 0.95f)
                    if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                        _manualMeasurementState.value = ManualMeasurementState.Measuring("Heart Rate", progress = progress)
                    }
                }
            }

            completionJob.cancel()

            // Always send stop 0609 when finishing
            try {
                bleManager.stopHeartRateMeasurement(gatt)
            } catch (e: Exception) {
                AppLog.w(tagBle, "Error stopping Heart Rate measurement: ${e.message}")
            }

            // Final check on latest reading
            val finalCurrent = _snapshot.value.latestHeartRate
            if (finalCurrent != null && finalCurrent.timestamp >= startTime && finalCurrent.bpm in 30..240) {
                latestNonzeroHr = finalCurrent
            }

            if (latestNonzeroHr != null && latestNonzeroHr.bpm in 30..240) {
                // Save single final sample to Room and Health Connect
                healthRepository.saveHeartRate(bpm = latestNonzeroHr.bpm, timestamp = latestNonzeroHr.timestamp, isManual = isManual)
                healthWriter.writeHeartRateSample(latestNonzeroHr, isManual = isManual)
                val displayStr = "${latestNonzeroHr.bpm} BPM"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Success(
                        metric = "Heart Rate",
                        displayValue = displayStr,
                        timestamp = latestNonzeroHr.timestamp
                    )
                }
                AppLog.lastMeasureResult = "Heart Rate: $displayStr (Success, isManual=$isManual)"
                AppLog.i(tagBle, "Heart Rate measurement succeeded: $displayStr (isManual=$isManual, single row saved)")
                latestNonzeroHr
            } else {
                val errMsg = "sensor ran but value stayed 0"
                AppLog.w(tagBle, "Heart Rate measurement ended without nonzero sample: $errMsg")
                AppLog.lastMeasureResult = "Heart Rate: Error - $errMsg"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Error(
                        metric = "Heart Rate",
                        message = errMsg
                    )
                }
                null
            }
        } catch (e: Exception) {
            AppLog.e(tagBle, "Exception in executeHeartRateMeasurement: ${e.message}", e)
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "Heart Rate",
                    message = "Error: ${e.message ?: "Measurement failed"}"
                )
            }
            null
        }
    }

    fun measureOxygenSaturation() {
        scope.launch {
            executeOxygenSaturationMeasurement(isManual = true)
        }
    }

    suspend fun executeOxygenSaturationMeasurement(isManual: Boolean): OxygenSaturationSample? {
        val prereqError = validateMeasurementPrerequisites("SpO₂")
        if (prereqError != null) {
            AppLog.e(tagBle, "Measure SpO₂ rejected: $prereqError")
            AppLog.lastMeasureResult = "SpO₂: Error - $prereqError"
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "SpO₂",
                    message = prereqError
                )
            }
            return null
        }

        val gatt = currentGatt ?: return null
        return try {
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Measuring("SpO₂", progress = 0.05f)
            }
            val startTime = System.currentTimeMillis()
            AppLog.i(tagBle, "Measure SpO₂ START (0609 type=0x09, isManual=$isManual)")

            val startOk = bleManager.startSpO2Measurement(gatt)
            if (!startOk) {
                val writeErr = "Ring rejected SpO₂ start command (0609)."
                AppLog.e(tagBle, "Measure SpO₂ write error: $writeErr")
                AppLog.lastMeasureResult = "SpO₂: Error - $writeErr"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Error(
                        metric = "SpO₂",
                        message = writeErr
                    )
                }
                return null
            }

            // Wait up to ~60s or until reading arrives
            val maxWaitMs = 60000L
            val pollStepMs = 500L
            val totalSteps = (maxWaitMs / pollStepMs).toInt()
            var latestNonzeroSpo2: OxygenSaturationSample? = null

            var statusCompleteReceived = false
            val completionJob = scope.launch {
                bleManager.measureCompletedFlow.collect { type ->
                    if (type == Protocol.MEASURE_TYPE_SPO2 || type == 0x00.toByte()) {
                        statusCompleteReceived = true
                    }
                }
            }

            for (step in 1..totalSteps) {
                delay(pollStepMs)
                val current = _snapshot.value.latestOxygenSaturation
                if (current != null && current.timestamp >= startTime && current.percentage in 70f..100f) {
                    latestNonzeroSpo2 = current
                }
                if (statusCompleteReceived || !bleManager.isMeasuringActive) {
                    AppLog.i(tagBle, "SpO2 measurement finished by ring 0609 status complete")
                    break
                }
                if (isManual) {
                    val progress = (step.toFloat() / totalSteps.toFloat()).coerceIn(0.05f, 0.95f)
                    if (_manualMeasurementState.value is ManualMeasurementState.Measuring) {
                        _manualMeasurementState.value = ManualMeasurementState.Measuring("SpO₂", progress = progress)
                    }
                }
            }

            completionJob.cancel()

            // Always send stop 0609 when finishing
            try {
                bleManager.stopSpO2Measurement(gatt)
            } catch (e: Exception) {
                AppLog.w(tagBle, "Error stopping SpO2 measurement: ${e.message}")
            }

            // Final check on latest reading
            val finalCurrent = _snapshot.value.latestOxygenSaturation
            if (finalCurrent != null && finalCurrent.timestamp >= startTime && finalCurrent.percentage in 70f..100f) {
                latestNonzeroSpo2 = finalCurrent
            }

            if (latestNonzeroSpo2 != null && latestNonzeroSpo2.percentage in 70f..100f) {
                // Save single final sample to Room and Health Connect
                healthRepository.saveOxygenSaturation(percentage = latestNonzeroSpo2.percentage, timestamp = latestNonzeroSpo2.timestamp, isManual = isManual)
                healthWriter.writeOxygenSaturationSample(latestNonzeroSpo2, isManual = isManual)
                val displayStr = "${latestNonzeroSpo2.percentage.toInt()}%"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Success(
                        metric = "SpO₂",
                        displayValue = displayStr,
                        timestamp = latestNonzeroSpo2.timestamp
                    )
                }
                AppLog.lastMeasureResult = "SpO₂: $displayStr (Success, isManual=$isManual)"
                AppLog.i(tagBle, "SpO₂ measurement succeeded: $displayStr (isManual=$isManual, single row saved)")
                latestNonzeroSpo2
            } else {
                val errMsg = "sensor ran but value stayed 0"
                AppLog.w(tagBle, "SpO₂ measurement ended without nonzero sample: $errMsg")
                AppLog.lastMeasureResult = "SpO₂: Error - $errMsg"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Error(
                        metric = "SpO₂",
                        message = errMsg
                    )
                }
                null
            }
        } catch (e: Exception) {
            AppLog.e(tagBle, "Exception in executeOxygenSaturationMeasurement: ${e.message}", e)
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "SpO₂",
                    message = "Error: ${e.message ?: "Measurement failed"}"
                )
            }
            null
        }
    }

    fun measureHrv() {
        scope.launch {
            executeHrvMeasurement(isManual = true)
        }
    }

    suspend fun executeHrvMeasurement(isManual: Boolean): HrvSample? {
        val prereqError = validateMeasurementPrerequisites("HRV")
        if (prereqError != null) {
            AppLog.e(tagBle, "Measure HRV rejected: $prereqError")
            AppLog.lastMeasureResult = "HRV: Error - $prereqError"
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "HRV",
                    message = prereqError
                )
            }
            return null
        }

        val gatt = currentGatt ?: return null
        return try {
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Measuring("HRV", progress = 0.05f)
            }

            val cachedType = healthRepository.hrvWorkingType
            val candidateTypes = if (cachedType != null) {
                listOf(cachedType) + Protocol.HRV_PROBE_TYPES.filter { it != cachedType }
            } else {
                Protocol.HRV_PROBE_TYPES
            }

            AppLog.i(tagBle, "Measure HRV START (candidates: ${candidateTypes.map { "0x%02X".format(it) }}, cached=0x${cachedType?.let { "%02X".format(it) } ?: "none"})")

            var finalHrvSample: HrvSample? = null
            var successfulType: Byte? = null
            var successfulPushKey: Int? = null

            for ((index, type) in candidateTypes.withIndex()) {
                val isCached = (type == cachedType)
                val attemptWaitMs = if (isCached) 60000L else 9000L
                AppLog.i(tagBle, "HRV probe: Starting type 0x${"%02X".format(type)} (candidate ${index + 1}/${candidateTypes.size}, timeout=${attemptWaitMs / 1000}s)...")

                val startOk = bleManager.startMeasurementType(gatt, type)
                if (!startOk) {
                    AppLog.w(tagBle, "HRV start command unacknowledged for type 0x${"%02X".format(type)}")
                    continue
                }

                var statusCompleteReceived = false
                var sampleForThisType: HrvSample? = null

                val completionJob = scope.launch {
                    bleManager.measureCompletedFlow.collect { compType ->
                        if (compType == type || compType == 0x00.toByte()) {
                            statusCompleteReceived = true
                        }
                    }
                }

                val frameCollectJob = scope.launch {
                    bleManager.incomingFrameFlow.collect { frame ->
                        if (frame.key != Protocol.KEY_HR_PUSH &&
                            frame.key != Protocol.KEY_SPO2_PUSH &&
                            frame.key != Protocol.KEY_STEPS_PUSH &&
                            frame.key != Protocol.KEY_SLEEP_PUSH &&
                            frame.key != Protocol.KEY_SESSION_INIT &&
                            frame.key != Protocol.KEY_MEASURE_CONTROL) {
                            val parsed = Protocol.parseGenericMetricValue(frame, 5, 250)
                            if (parsed != null) {
                                AppLog.i(tagBle, "HRV sample parsed: ${parsed.value} from key=0x${"%04X".format(parsed.key)}")
                                sampleForThisType = HrvSample(hrvMs = parsed.value, timestamp = parsed.timestamp)
                                successfulPushKey = parsed.key
                                _snapshot.value = _snapshot.value.copy(latestHrv = sampleForThisType)
                            }
                        }
                    }
                }

                val pollStepMs = 500L
                val maxSteps = (attemptWaitMs / pollStepMs).toInt()
                for (step in 1..maxSteps) {
                    delay(pollStepMs)
                    if (sampleForThisType != null) {
                        finalHrvSample = sampleForThisType
                        successfulType = type
                        if (!isCached) {
                            AppLog.i(tagBle, "HRV probe SUCCESS on type 0x${"%02X".format(type)}! Finishing measurement...")
                            for (extraStep in 1..30) {
                                delay(500)
                                if (statusCompleteReceived || !bleManager.isMeasuringActive) break
                                if (isManual) {
                                    val p = (0.3f + (extraStep.toFloat() / 30f) * 0.65f).coerceIn(0.1f, 0.95f)
                                    _manualMeasurementState.value = ManualMeasurementState.Measuring("HRV", progress = p)
                                }
                            }
                        }
                        break
                    }
                    if (statusCompleteReceived || !bleManager.isMeasuringActive) break
                    if (isManual) {
                        val p = ((index.toFloat() + (step.toFloat() / maxSteps)) / candidateTypes.size).coerceIn(0.05f, 0.95f)
                        _manualMeasurementState.value = ManualMeasurementState.Measuring("HRV", progress = p)
                    }
                }

                completionJob.cancel()
                frameCollectJob.cancel()

                try {
                    bleManager.stopMeasurementType(gatt, type)
                } catch (e: Exception) {
                    AppLog.w(tagBle, "Error stopping HRV measurement type 0x${"%02X".format(type)}: ${e.message}")
                }

                if (finalHrvSample != null) break
            }

            if (finalHrvSample != null && successfulType != null && finalHrvSample.hrvMs in 5..250) {
                healthRepository.hrvWorkingType = successfulType
                if (successfulPushKey != null) {
                    healthRepository.hrvWorkingPushKey = successfulPushKey
                }
                healthRepository.saveHrv(hrvMs = finalHrvSample.hrvMs, timestamp = finalHrvSample.timestamp, isManual = isManual)
                val displayStr = "${finalHrvSample.hrvMs} ms"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Success(
                        metric = "HRV",
                        displayValue = displayStr,
                        timestamp = finalHrvSample.timestamp
                    )
                }
                AppLog.lastMeasureResult = "HRV: $displayStr (Success, isManual=$isManual, type=0x${"%02X".format(successfulType)})"
                AppLog.i(tagBle, "HRV measurement succeeded: $displayStr (single row saved)")
                finalHrvSample
            } else {
                val errMsg = "HRV not supported / no RX — export logs"
                AppLog.w(tagBle, "HRV measurement ended without reading: $errMsg")
                AppLog.lastMeasureResult = "HRV: Error - $errMsg"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Error(
                        metric = "HRV",
                        message = errMsg
                    )
                }
                null
            }
        } catch (e: Exception) {
            AppLog.e(tagBle, "Exception in executeHrvMeasurement: ${e.message}", e)
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "HRV",
                    message = "Error: ${e.message ?: "Measurement failed"}"
                )
            }
            null
        }
    }

    fun measureStress() {
        scope.launch {
            executeStressMeasurement(isManual = true)
        }
    }

    suspend fun executeStressMeasurement(isManual: Boolean): StressSample? {
        val prereqError = validateMeasurementPrerequisites("Stress")
        if (prereqError != null) {
            AppLog.e(tagBle, "Measure Stress rejected: $prereqError")
            AppLog.lastMeasureResult = "Stress: Error - $prereqError"
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "Stress",
                    message = prereqError
                )
            }
            return null
        }

        val gatt = currentGatt ?: return null
        return try {
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Measuring("Stress", progress = 0.05f)
            }

            val cachedType = healthRepository.stressWorkingType
            val candidateTypes = if (cachedType != null) {
                listOf(cachedType) + Protocol.getStressCandidateTypes(healthRepository.hrvWorkingType).filter { it != cachedType }
            } else {
                Protocol.getStressCandidateTypes(healthRepository.hrvWorkingType)
            }

            AppLog.i(tagBle, "Measure Stress START (candidates: ${candidateTypes.map { "0x%02X".format(it) }}, cached=0x${cachedType?.let { "%02X".format(it) } ?: "none"})")

            var finalStressSample: StressSample? = null
            var successfulType: Byte? = null
            var successfulPushKey: Int? = null

            for ((index, type) in candidateTypes.withIndex()) {
                val isCached = (type == cachedType)
                val attemptWaitMs = if (isCached) 60000L else 9000L
                AppLog.i(tagBle, "Stress probe: Starting type 0x${"%02X".format(type)} (candidate ${index + 1}/${candidateTypes.size}, timeout=${attemptWaitMs / 1000}s)...")

                val startOk = bleManager.startMeasurementType(gatt, type)
                if (!startOk) {
                    AppLog.w(tagBle, "Stress start command unacknowledged for type 0x${"%02X".format(type)}")
                    continue
                }

                var statusCompleteReceived = false
                var sampleForThisType: StressSample? = null

                val completionJob = scope.launch {
                    bleManager.measureCompletedFlow.collect { compType ->
                        if (compType == type || compType == 0x00.toByte()) {
                            statusCompleteReceived = true
                        }
                    }
                }

                val frameCollectJob = scope.launch {
                    bleManager.incomingFrameFlow.collect { frame ->
                        if (frame.key != Protocol.KEY_HR_PUSH &&
                            frame.key != Protocol.KEY_SPO2_PUSH &&
                            frame.key != Protocol.KEY_STEPS_PUSH &&
                            frame.key != Protocol.KEY_SLEEP_PUSH &&
                            frame.key != Protocol.KEY_SESSION_INIT &&
                            frame.key != Protocol.KEY_MEASURE_CONTROL) {
                            val parsed = Protocol.parseGenericMetricValue(frame, 0, 100)
                            if (parsed != null) {
                                AppLog.i(tagBle, "Stress sample parsed: ${parsed.value} from key=0x${"%04X".format(parsed.key)}")
                                sampleForThisType = StressSample(score = parsed.value, timestamp = parsed.timestamp)
                                successfulPushKey = parsed.key
                                _snapshot.value = _snapshot.value.copy(latestStress = sampleForThisType)
                            }
                        }
                    }
                }

                val pollStepMs = 500L
                val maxSteps = (attemptWaitMs / pollStepMs).toInt()
                for (step in 1..maxSteps) {
                    delay(pollStepMs)
                    if (sampleForThisType != null) {
                        finalStressSample = sampleForThisType
                        successfulType = type
                        if (!isCached) {
                            AppLog.i(tagBle, "Stress probe SUCCESS on type 0x${"%02X".format(type)}! Finishing measurement...")
                            for (extraStep in 1..30) {
                                delay(500)
                                if (statusCompleteReceived || !bleManager.isMeasuringActive) break
                                if (isManual) {
                                    val p = (0.3f + (extraStep.toFloat() / 30f) * 0.65f).coerceIn(0.1f, 0.95f)
                                    _manualMeasurementState.value = ManualMeasurementState.Measuring("Stress", progress = p)
                                }
                            }
                        }
                        break
                    }
                    if (statusCompleteReceived || !bleManager.isMeasuringActive) break
                    if (isManual) {
                        val p = ((index.toFloat() + (step.toFloat() / maxSteps)) / candidateTypes.size).coerceIn(0.05f, 0.95f)
                        _manualMeasurementState.value = ManualMeasurementState.Measuring("Stress", progress = p)
                    }
                }

                completionJob.cancel()
                frameCollectJob.cancel()

                try {
                    bleManager.stopMeasurementType(gatt, type)
                } catch (e: Exception) {
                    AppLog.w(tagBle, "Error stopping Stress measurement type 0x${"%02X".format(type)}: ${e.message}")
                }

                if (finalStressSample != null) break
            }

            if (finalStressSample != null && successfulType != null && finalStressSample.score in 1..100) {
                healthRepository.stressWorkingType = successfulType
                if (successfulPushKey != null) {
                    healthRepository.stressWorkingPushKey = successfulPushKey
                }
                healthRepository.saveStress(stressScore = finalStressSample.score, timestamp = finalStressSample.timestamp, isManual = isManual)
                val displayStr = "${finalStressSample.score}/100 (${finalStressSample.label})"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Success(
                        metric = "Stress",
                        displayValue = displayStr,
                        timestamp = finalStressSample.timestamp
                    )
                }
                AppLog.lastMeasureResult = "Stress: $displayStr (Success, isManual=$isManual, type=0x${"%02X".format(successfulType)})"
                AppLog.i(tagBle, "Stress measurement succeeded: $displayStr (single row saved)")
                finalStressSample
            } else {
                val errMsg = "Stress not supported / no RX — export logs"
                AppLog.w(tagBle, "Stress measurement ended without reading: $errMsg")
                AppLog.lastMeasureResult = "Stress: Error - $errMsg"
                if (isManual) {
                    _manualMeasurementState.value = ManualMeasurementState.Error(
                        metric = "Stress",
                        message = errMsg
                    )
                }
                null
            }
        } catch (e: Exception) {
            AppLog.e(tagBle, "Exception in executeStressMeasurement: ${e.message}", e)
            if (isManual) {
                _manualMeasurementState.value = ManualMeasurementState.Error(
                    metric = "Stress",
                    message = "Error: ${e.message ?: "Measurement failed"}"
                )
            }
            null
        }
    }

    suspend fun setScreenBrightness(brightness: ScreenBrightness): Boolean {
        val settings = MonitoringSettings(context)
        settings.screenBrightness = brightness
        val gatt = currentGatt
        val state = _connectionState.value
        if (gatt == null || state !is ConnectionState.Ready) {
            AppLog.i(tagBle, "Screen brightness preference set to ${brightness.label} (ring not currently Ready; will apply upon connect)")
            return true
        }
        val confirmedCandidate = bleManager.sendBrightnessCommand(
            gatt,
            brightness,
            settings.brightnessKey,
            settings.brightnessShape
        )
        return if (confirmedCandidate != null) {
            settings.brightnessKey = confirmedCandidate.key
            settings.brightnessShape = confirmedCandidate.shape
            healthRepository.isRingDisplayEnabled = (brightness != ScreenBrightness.OFF)
            AppLog.i(tagBle, "Screen brightness set to ${brightness.label} (confirmed key=0x${"%04X".format(confirmedCandidate.key)}, shape=${confirmedCandidate.shape})")
            true
        } else {
            AppLog.w(tagBle, "no RX for any brightness candidate")
            false
        }
    }

    suspend fun setRingDisplay(enabled: Boolean): Boolean {
        healthRepository.isRingDisplayEnabled = enabled
        val settings = MonitoringSettings(context)
        val targetBrightness = if (enabled) {
            if (settings.screenBrightness == ScreenBrightness.OFF) ScreenBrightness.STRONG else settings.screenBrightness
        } else {
            ScreenBrightness.OFF
        }
        return setScreenBrightness(targetBrightness)
    }

    /**
     * Executes scheduled background measurements for due metrics (isManual = false).
     * Saves ONE final sample per metric per scheduled tick, and calls settings.markRun(metric) after each.
     */
    suspend fun performScheduledMeasurement(
        dueMetrics: List<MonitorMetric>,
        settings: MonitoringSettings
    ): String {
        AppLog.i(tagSync, "=== Scheduled Biometric Check Starting: ${dueMetrics.map { it.name }} ===")
        val gatt = currentGatt
        val lastAddr = healthRepository.lastConnectedAddress
        if (gatt == null && lastAddr != null) {
            AppLog.i(tagSync, "Reconnecting to ring $lastAddr for scheduled check...")
            connect(lastAddr, healthRepository.lastConnectedName ?: "Galaxy Ring")
        }

        // Wait up to 20 seconds for Ready state
        var waitSec = 0
        while (waitSec < 20 && _connectionState.value !is ConnectionState.Ready) {
            delay(1000)
            waitSec++
        }

        if (_connectionState.value !is ConnectionState.Ready) {
            val stateName = _connectionState.value::class.simpleName ?: "Disconnected"
            val msg = "Scheduled run skipped: Ring not Ready ($stateName after ${waitSec}s)"
            AppLog.w(tagSync, msg)
            healthRepository.lastScheduleRunTimestamp = System.currentTimeMillis()
            healthRepository.lastScheduleRunResult = msg
            return msg
        }

        val results = mutableListOf<String>()

        for (metric in dueMetrics) {
            try {
                when (metric) {
                    MonitorMetric.HEART_RATE -> {
                        AppLog.i(tagSync, "Scheduled check: measuring Heart Rate (single final sample will be saved)...")
                        val hrSample = executeHeartRateMeasurement(isManual = false)
                        if (hrSample != null) {
                            results.add("HR: ${hrSample.bpm} BPM")
                        } else {
                            results.add("HR: No reading")
                        }
                    }
                    MonitorMetric.SPO2 -> {
                        AppLog.i(tagSync, "Scheduled check: measuring SpO2 (single final sample will be saved)...")
                        val spo2Sample = executeOxygenSaturationMeasurement(isManual = false)
                        if (spo2Sample != null) {
                            results.add("SpO₂: ${spo2Sample.percentage.toInt()}%")
                        } else {
                            results.add("SpO₂: No reading")
                        }
                    }
                    MonitorMetric.HRV -> {
                        AppLog.i(tagSync, "Scheduled check: measuring HRV (single final sample will be saved)...")
                        val hrvSample = executeHrvMeasurement(isManual = false)
                        if (hrvSample != null) {
                            results.add("HRV: ${hrvSample.hrvMs} ms")
                        } else {
                            results.add("HRV: No reading")
                        }
                    }
                    MonitorMetric.STRESS -> {
                        AppLog.i(tagSync, "Scheduled check: measuring Stress (single final sample will be saved)...")
                        val stressSample = executeStressMeasurement(isManual = false)
                        if (stressSample != null) {
                            results.add("Stress: ${stressSample.score}/100")
                        } else {
                            results.add("Stress: No reading")
                        }
                    }
                }
            } finally {
                settings.markRun(metric)
            }
            delay(2000)
        }

        // Sync steps (051A) and sleep (0x0505 paged history)
        val synced = requestSync()
        if (synced.steps.totalSteps > 0) {
            results.add("Steps: ${synced.steps.totalSteps}")
        }

        val summary = if (results.isNotEmpty()) results.joinToString(", ") else "Completed (no readings)"
        AppLog.i(tagSync, "=== Scheduled Biometric Check Finished: $summary ===")
        healthRepository.lastScheduleRunTimestamp = System.currentTimeMillis()
        healthRepository.lastScheduleRunResult = summary
        return summary
    }

    suspend fun performScheduledMeasurement(checkHr: Boolean, checkSpo2: Boolean): String {
        val settings = MonitoringSettings(context)
        val due = mutableListOf<MonitorMetric>()
        if (checkHr) due.add(MonitorMetric.HEART_RATE)
        if (checkSpo2) due.add(MonitorMetric.SPO2)
        return performScheduledMeasurement(due, settings)
    }

    fun resetManualMeasurementState() {
        _manualMeasurementState.value = ManualMeasurementState.Idle
    }

    fun findMyRing(): Boolean {
        val state = _connectionState.value
        if (state !is ConnectionState.Ready) return false

        scope.launch {
            try {
                currentGatt?.let { gatt ->
                    AppLog.i(tagBle, "Sending Find My Ring command (0x0305)")
                    val frame = Protocol.buildFindRingCommand()
                    bleManager.executeCommand(gatt, frame, Protocol.KEY_FIND_RING, timeoutMs = 3000L)
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Error in findMyRing: ${e.message}", e)
            }
        }
        return true
    }

    /**
     * Executes sequential sync job:
     * A) Steps (0x051A, keyFlag 0x10)
     * B) Sleep History (0x0505 paged query)
     * C) Telemetry update
     * D) Writes newly parsed data to Room + Health Connect
     */
    suspend fun syncRingData(isAutomatic: Boolean = false): SyncResult {
        val state = _connectionState.value
        if (state !is ConnectionState.Ready) {
            val msg = "Ring is not in Ready state (current: $state)"
            AppLog.w(tagSync, "Sync skipped: $msg")
            return SyncResult(success = false, message = msg, hcStatus = "Ring not Ready")
        }

        val gatt = currentGatt ?: run {
            val msg = "No active BLE GATT connection"
            AppLog.w(tagSync, "Sync skipped: $msg")
            return SyncResult(success = false, message = msg, hcStatus = "No connection")
        }

        if (_isSyncing.value) {
            AppLog.w(tagSync, "Sync already in progress; ignoring duplicate request")
            return SyncResult(success = false, message = "Sync already in progress", hcStatus = "Busy")
        }

        _isSyncing.value = true
        _syncProgressMessage.value = "Syncing steps (0x051A)..."
        AppLog.i(tagSync, "=== Sequential Sync Started (isAutomatic=$isAutomatic) ===")

        var stepsCount = 0L
        var sleepSession: SleepSession? = null
        var hcSuccess = false

        try {
            // A) Steps query (0x051A, keyFlag 0x10)
            AppLog.i(tagSync, "A) Requesting daily steps from ring (0x051A)...")
            val stepsData = bleManager.fetchSteps(gatt)
            if (stepsData != null) {
                stepsCount = stepsData.totalSteps
                _snapshot.value = _snapshot.value.copy(steps = stepsData)
                healthRepository.saveDailySteps(stepsData.totalSteps, stepsData.caloriesKcal, stepsData.distanceMeters)
                val sOk = healthWriter.writeSteps(stepsData)
                if (sOk) hcSuccess = true
                AppLog.i(tagSync, "Steps sync completed: $stepsCount steps (HC write: $sOk)")
            } else {
                AppLog.w(tagSync, "Steps query did not return valid data from 0x051A")
            }

            delay(300)

            // Cancel if disconnected
            if (currentGatt == null || _connectionState.value is ConnectionState.Disconnected) {
                AppLog.w(tagSync, "Ring disconnected during steps sync. Aborting remaining sync.")
                return SyncResult(success = false, steps = stepsCount, message = "Ring disconnected mid-sync", hcStatus = "Disconnected")
            }

            // B) Sleep query (0x0505 paged history fetch)
            _syncProgressMessage.value = "Syncing sleep (0x0505)..."
            AppLog.i(tagSync, "B) Requesting sleep history from ring (0x0505 paged)...")
            val sleepResult = bleManager.syncSleepHistory(gatt, healthRepository.sleepRequiresDeleteToAdvance)

            // Cache working path
            healthRepository.sleepWorkingKey = sleepResult.workingKey
            healthRepository.sleepRequiresDeleteToAdvance = sleepResult.requiresDeleteToAdvance

            val importedSessions = sleepResult.sessions
            if (importedSessions.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val sessionsByNight = importedSessions.groupBy { dateFormat.format(Date(it.endTime)) }

                for ((dateStr, nightSessions) in sessionsByNight) {
                    val bestSession = nightSessions.maxByOrNull { it.durationMinutes } ?: nightSessions.first()
                    healthRepository.saveSleepSession(bestSession)
                    val slOk = healthWriter.writeSleepSession(bestSession)
                    if (slOk) hcSuccess = true
                    AppLog.i(tagSync, "Sleep session saved for night $dateStr: ${bestSession.durationMinutes} min (HC write: $slOk)")
                }

                val latestSession = importedSessions.maxByOrNull { it.endTime }!!
                val analysis = SleepAnalyzer.analyze(latestSession, (healthRepository.sleepTargetHours * 60).toLong())
                _snapshot.value = _snapshot.value.copy(latestSleep = latestSession, latestSleepAnalysis = analysis)
                sleepSession = latestSession
                AppLog.i(tagSync, "Sleep sync completed: ${importedSessions.size} session(s) across ${sessionsByNight.size} night(s). Latest: ${latestSession.durationMinutes} mins")
            } else {
                AppLog.i(tagSync, "No sleep data returned from 0x0505 paged query. Last TX: ${bleManager.lastTxHex}")
            }

            delay(200)

            // Cancel if disconnected
            if (currentGatt == null || _connectionState.value is ConnectionState.Disconnected) {
                AppLog.w(tagSync, "Ring disconnected during sleep sync. Aborting remaining sync.")
                return SyncResult(success = false, steps = stepsCount, hasSleep = sleepSession != null, sleepMinutes = sleepSession?.durationMinutes ?: 0L, message = "Ring disconnected mid-sync", hcStatus = "Disconnected")
            }

            // C) Heart Rate history query (0x0503)
            _syncProgressMessage.value = "Syncing heart rate (0x0503)..."
            AppLog.i(tagSync, "C) Requesting HR history from ring (0x0503)...")
            val hrSamples = bleManager.fetchHeartRateHistory(gatt)
            if (hrSamples.isNotEmpty()) {
                for (sample in hrSamples) {
                    healthRepository.saveHeartRate(bpm = sample.value, timestamp = sample.timestamp, isManual = false)
                }
                val latestHrSample = hrSamples.maxByOrNull { it.timestamp }?.let { HeartRateSample(bpm = it.value, confidence = 100, timestamp = it.timestamp) }
                if (latestHrSample != null) {
                    _snapshot.value = _snapshot.value.copy(latestHeartRate = latestHrSample)
                }
                AppLog.i(tagSync, "Heart rate sync completed: saved ${hrSamples.size} sample(s) to Room")
            } else {
                AppLog.i(tagSync, "No HR history data returned from 0x0503; leaving HR records unchanged")
            }

            delay(200)

            // Cancel if disconnected
            if (currentGatt == null || _connectionState.value is ConnectionState.Disconnected) {
                AppLog.w(tagSync, "Ring disconnected during HR sync. Aborting remaining sync.")
                return SyncResult(success = false, steps = stepsCount, hasSleep = sleepSession != null, sleepMinutes = sleepSession?.durationMinutes ?: 0L, message = "Ring disconnected mid-sync", hcStatus = "Disconnected")
            }

            // D) SpO2 history query (0x0509)
            _syncProgressMessage.value = "Syncing SpO2 (0x0509)..."
            AppLog.i(tagSync, "D) Requesting SpO2 history from ring (0x0509)...")
            val spo2Samples = bleManager.fetchSpo2History(gatt)
            if (spo2Samples.isNotEmpty()) {
                for (sample in spo2Samples) {
                    healthRepository.saveOxygenSaturation(percentage = sample.value.toFloat(), timestamp = sample.timestamp, isManual = false)
                }
                val latestSpo2Sample = spo2Samples.maxByOrNull { it.timestamp }?.let { OxygenSaturationSample(percentage = it.value.toFloat(), timestamp = it.timestamp) }
                if (latestSpo2Sample != null) {
                    _snapshot.value = _snapshot.value.copy(latestOxygenSaturation = latestSpo2Sample)
                }
                AppLog.i(tagSync, "SpO2 sync completed: saved ${spo2Samples.size} sample(s) to Room")
            } else {
                AppLog.i(tagSync, "No SpO2 history data returned from 0x0509; leaving SpO2 records unchanged")
            }

            delay(200)

            // Cancel if disconnected
            if (currentGatt == null || _connectionState.value is ConnectionState.Disconnected) {
                AppLog.w(tagSync, "Ring disconnected during SpO2 sync. Aborting remaining sync.")
                return SyncResult(success = false, steps = stepsCount, hasSleep = sleepSession != null, sleepMinutes = sleepSession?.durationMinutes ?: 0L, message = "Ring disconnected mid-sync", hcStatus = "Disconnected")
            }

            // E) HRV history query (0x050A)
            _syncProgressMessage.value = "Syncing HRV (0x050A)..."
            AppLog.i(tagSync, "E) Requesting HRV history from ring (0x050A)...")
            val hrvSamples = bleManager.fetchHrvHistory(gatt)
            if (hrvSamples.isNotEmpty()) {
                for (sample in hrvSamples) {
                    healthRepository.saveHrv(hrvMs = sample.value, timestamp = sample.timestamp, isManual = false)
                }
                val latestHrvSample = hrvSamples.maxByOrNull { it.timestamp }?.let { HrvSample(it.value, it.timestamp) }
                if (latestHrvSample != null) {
                    _snapshot.value = _snapshot.value.copy(latestHrv = latestHrvSample)
                }
                AppLog.i(tagSync, "HRV sync completed: saved ${hrvSamples.size} sample(s) to Room")
            } else {
                AppLog.i(tagSync, "No HRV history data returned from 0x050A; leaving HRV records empty")
            }

            delay(200)

            // Cancel if disconnected
            if (currentGatt == null || _connectionState.value is ConnectionState.Disconnected) {
                AppLog.w(tagSync, "Ring disconnected during HRV sync. Aborting remaining sync.")
                return SyncResult(success = false, steps = stepsCount, hasSleep = sleepSession != null, sleepMinutes = sleepSession?.durationMinutes ?: 0L, message = "Ring disconnected mid-sync", hcStatus = "Disconnected")
            }

            // F) Stress history query (0x050D)
            _syncProgressMessage.value = "Syncing stress (0x050D)..."
            AppLog.i(tagSync, "F) Requesting stress history from ring (0x050D)...")
            val stressSamples = bleManager.fetchStressHistory(gatt)
            if (stressSamples.isNotEmpty()) {
                for (sample in stressSamples) {
                    healthRepository.saveStress(stressScore = sample.value, timestamp = sample.timestamp, isManual = false)
                }
                val latestStressSample = stressSamples.maxByOrNull { it.timestamp }?.let { StressSample(it.value, timestamp = it.timestamp) }
                if (latestStressSample != null) {
                    _snapshot.value = _snapshot.value.copy(latestStress = latestStressSample)
                }
                AppLog.i(tagSync, "Stress sync completed: saved ${stressSamples.size} sample(s) to Room")
            } else {
                AppLog.i(tagSync, "No stress history data returned from 0x050D; leaving stress records empty")
            }

            delay(200)

            // Cancel if disconnected
            if (currentGatt == null || _connectionState.value is ConnectionState.Disconnected) {
                AppLog.w(tagSync, "Ring disconnected during stress sync. Aborting remaining sync.")
                return SyncResult(success = false, steps = stepsCount, hasSleep = sleepSession != null, sleepMinutes = sleepSession?.durationMinutes ?: 0L, message = "Ring disconnected mid-sync", hcStatus = "Disconnected")
            }

            // G) Workout history query (0x0506)
            _syncProgressMessage.value = "Syncing workouts (0x0506)..."
            AppLog.i(tagSync, "G) Requesting workout history from ring (0x0506)...")
            val workoutSessions = bleManager.fetchWorkoutHistory(gatt)
            if (workoutSessions.isNotEmpty()) {
                healthRepository.saveWorkoutSessions(workoutSessions)
                AppLog.i(tagSync, "Workout sync completed: saved ${workoutSessions.size} session(s) to Room")
            } else {
                AppLog.i(tagSync, "No workout history data returned from 0x0506; leaving workout records unchanged")
            }

            delay(200)

            // H) Status check & snapshot update
            _syncProgressMessage.value = "Updating vitals..."
            val now = System.currentTimeMillis()
            _snapshot.value = _snapshot.value.copy(
                lastSyncTimestamp = now,
                isSimulatedTelemetry = false
            )

            // G) Health Connect verification
            val hasHcPerms = healthWriter.hasAllPermissions()
            val hcStatus = if (!healthWriter.isHealthConnectAvailable()) {
                "unavailable"
            } else if (!hasHcPerms) {
                "denied"
            } else if (healthWriter.lastWriteError != null && !hcSuccess && (stepsCount > 0 || sleepSession != null)) {
                "error: ${healthWriter.lastWriteError}"
            } else {
                "ok"
            }

            val sleepStr = if (sleepSession != null) "yes" else "no"
            val hrvStr = if (hrvSamples.isNotEmpty()) "${hrvSamples.size} samples" else "none"
            val stressStr = if (stressSamples.isNotEmpty()) "${stressSamples.size} samples" else "none"
            val workoutsStr = if (workoutSessions.isNotEmpty()) "${workoutSessions.size} sessions" else "none"
            val resultSummary = "Steps: $stepsCount, Sleep: $sleepStr, HRV: $hrvStr, Stress: $stressStr, Workouts: $workoutsStr, HC: $hcStatus"
            AppLog.i(tagSync, "=== Sequential Sync Finished: $resultSummary ===")

            return SyncResult(
                success = true,
                steps = stepsCount,
                hasSleep = sleepSession != null,
                sleepMinutes = sleepSession?.durationMinutes ?: 0L,
                workoutsCount = workoutSessions.size,
                healthConnectWritten = hcSuccess,
                hcStatus = hcStatus,
                message = resultSummary
            )
        } catch (e: Exception) {
            AppLog.e(tagSync, "Exception during sequential sync: ${e.message}", e)
            return SyncResult(
                success = false,
                steps = stepsCount,
                hasSleep = sleepSession != null,
                sleepMinutes = sleepSession?.durationMinutes ?: 0L,
                healthConnectWritten = hcSuccess,
                hcStatus = "Error: ${e.message}",
                message = e.message ?: "Sync failed"
            )
        } finally {
            _isSyncing.value = false
            _syncProgressMessage.value = null
        }
    }

    suspend fun requestSync(): RingHealthSnapshot {
        syncRingData(isAutomatic = false)
        return _snapshot.value
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    private var idleDisconnectJob: Job? = null

    /**
     * Called when the app enters the foreground.
     * Cancels any pending idle disconnect timer and auto-reconnects if keep-connected is enabled.
     */
    fun onAppForegrounded() {
        idleDisconnectJob?.cancel()
        idleDisconnectJob = null
        AppLog.d(tagBle, "App foregrounded: cancelled idle disconnect timer")
        val shouldKeep = healthRepository.isKeepConnected || healthRepository.isScheduleEnabled
        if (shouldKeep && _connectionState.value is ConnectionState.Disconnected) {
            val lastAddr = healthRepository.lastConnectedAddress
            if (lastAddr != null) {
                AppLog.i(tagBle, "Auto-connecting to $lastAddr on foreground...")
                connect(lastAddr, healthRepository.lastConnectedName ?: "Galaxy Ring")
            }
        }
    }

    /**
     * Called when the app leaves the foreground.
     * If keep-connected and schedule are both OFF, starts an idle disconnect timer (2.5 min) to save ring battery.
     */
    fun onAppBackgrounded() {
        val shouldKeep = healthRepository.isKeepConnected || healthRepository.isScheduleEnabled
        if (shouldKeep) {
            AppLog.d(tagBle, "App backgrounded: keep-connected/schedule is ON, maintaining GATT connection")
            return
        }
        AppLog.i(tagBle, "App backgrounded with keep-connected and schedule OFF. Starting 2.5 min idle disconnect timer...")
        idleDisconnectJob?.cancel()
        idleDisconnectJob = scope.launch {
            delay(150_000L) // 2.5 minutes
            if (!healthRepository.isKeepConnected && !healthRepository.isScheduleEnabled && currentGatt != null) {
                AppLog.i(tagBle, "Idle disconnect timeout reached (2.5 min). Disconnecting to conserve ring battery.")
                disconnect()
            }
        }
    }

    private fun scheduleAutoReconnect() {
        if (isUserDisconnect) return
        val shouldKeep = healthRepository.isKeepConnected || healthRepository.isScheduleEnabled
        if (!shouldKeep) {
            AppLog.i(tagBle, "Ring disconnected and keep-connected/schedule are OFF. Not auto-reconnecting to save ring battery.")
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        val lastAddr = healthRepository.lastConnectedAddress ?: return
        val lastName = healthRepository.lastConnectedName ?: "Galaxy Ring"

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            try {
                AppLog.i(tagBle, "Ring disconnected unexpectedly with keep-connected/schedule ON. Scheduling auto-reconnect backoff...")
                var delayMs = 3000L
                while (isActive && !isUserDisconnect && _connectionState.value is ConnectionState.Disconnected) {
                    delay(delayMs)
                    AppLog.d(tagBle, "Attempting auto-reconnect to $lastAddr...")
                    connect(lastAddr, lastName)
                    delayMs = (delayMs * 1.8).toLong().coerceAtMost(30000L)
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Auto-reconnect cancelled or failed: ${e.message}", e)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                val deviceAddress = try { gatt.device?.address ?: "Unknown" } catch (_: Exception) { "Unknown" }
                val deviceName = try { gatt.device?.name ?: healthRepository.lastConnectedName ?: "Galaxy Ring" } catch (_: Exception) { "Galaxy Ring" }

                val ringDevice = RingDevice(
                    name = deviceName,
                    address = deviceAddress,
                    isConnected = (newState == BluetoothProfile.STATE_CONNECTED)
                )

                val stateString = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                    BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                    BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                    BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                    else -> "STATE_$newState"
                }
                AppLog.i(tagBle, "onConnectionStateChange: status=$status, newState=$stateString for $deviceAddress")

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = ConnectionState.Connecting(ringDevice.name)
                    AppLog.i(tagBle, "Connected to GATT server on $deviceAddress. Resetting session and requesting MTU 247...")
                    bleManager.resetSession()

                    // Request MTU 247 safely; ignore MTU failure
                    try {
                        val mtuInitiated = gatt.requestMtu(247)
                        AppLog.i(tagBle, "requestMtu(247) initiated: $mtuInitiated")
                    } catch (e: Exception) {
                        AppLog.w(tagBle, "requestMtu exception ignored: ${e.message}")
                    }

                    // Fallback timer: if onMtuChanged is delayed or skipped by device, discover services once
                    mtuFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
                    val fallback = Runnable {
                        try {
                            if (!bleManager.servicesConfigured) {
                                AppLog.d(tagBle, "MTU fallback trigger: discovering services on $deviceAddress...")
                                gatt.discoverServices()
                            } else {
                                AppLog.d(tagBle, "MTU fallback trigger: services already configured, skipping.")
                            }
                        } catch (e: Exception) {
                            AppLog.e(tagBle, "discoverServices fallback failed: ${e.message}", e)
                        }
                    }
                    mtuFallbackRunnable = fallback
                    mainHandler.postDelayed(fallback, 1500)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mtuFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
                    mtuFallbackRunnable = null
                    AppLog.w(tagBle, "Disconnected from GATT server (status=$status)")
                    bleManager.resetSession()
                    _connectionState.value = ConnectionState.Disconnected
                    stopSimulation()
                    if (!isUserDisconnect) {
                        if (status == 8) {
                            AppLog.w(tagBle, "GATT connection timeout (status=8). Ring went out of range or entered sleep.")
                        }
                        scheduleAutoReconnect()
                    }
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in onConnectionStateChange: ${e.message}", e)
                _connectionState.value = ConnectionState.Error("Connect failed: ${e.message ?: "State error"}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            try {
                mtuFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
                mtuFallbackRunnable = null
                AppLog.i(tagBle, "onMtuChanged: mtu=$mtu, status=$status")
                AppLog.mtuNegotiated = mtu
                if (!bleManager.servicesConfigured) {
                    mainHandler.post {
                        try {
                            if (!bleManager.servicesConfigured) {
                                AppLog.i(tagBle, "Triggering discoverServices after MTU negotiation...")
                                gatt.discoverServices()
                            } else {
                                AppLog.i(tagBle, "Services already configured, skipping discoverServices in onMtuChanged")
                            }
                        } catch (e: Exception) {
                            AppLog.e(tagBle, "discoverServices failed in onMtuChanged: ${e.message}", e)
                        }
                    }
                } else {
                    AppLog.i(tagBle, "onMtuChanged: services already configured, skipping discoverServices")
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in onMtuChanged: ${e.message}", e)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                AppLog.i(tagBle, "onServicesDiscovered: status=$status (${gatt.services.size} services)")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val err = "Service discovery failed ($status)"
                    AppLog.e(tagBle, err)
                    _connectionState.value = ConnectionState.Error(err)
                    return
                }

                if (bleManager.servicesConfigured) {
                    AppLog.i(tagBle, "onServicesDiscovered: Services already configured for this connection. Ignoring duplicate callback.")
                    return
                }

                mtuFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
                mtuFallbackRunnable = null

                val safeAddress = try { gatt.device?.address ?: "Unknown" } catch (_: Exception) { "Unknown" }
                val safeName = try { gatt.device?.name ?: "Galaxy Ring" } catch (_: Exception) { "Galaxy Ring" }
                val device = RingDevice(safeName, safeAddress, isConnected = true)

                // Attempt to read Battery Level if standard Battery Service (0x180F) is present
                try {
                    val batteryService = gatt.getService(Protocol.BATTERY_SERVICE_UUID)
                    val batteryChar = batteryService?.getCharacteristic(Protocol.BATTERY_LEVEL_CHAR_UUID)
                    if (batteryChar != null) {
                        AppLog.d(tagBle, "Discovered standard Battery characteristic 0x2A19, reading level...")
                        @Suppress("DEPRECATION")
                        gatt.readCharacteristic(batteryChar)
                    }
                } catch (e: Exception) {
                    AppLog.w(tagBle, "Error reading battery char: ${e.message}")
                }

                // Setup notification observer: primary 0xB003 once, then startPostConnectionFlow once
                scope.launch {
                    try {
                        val observerConfigured = bleManager.setupNotificationObserver(gatt)
                        AppLog.i(tagBle, "Notification observer configured: $observerConfigured. Starting post connection flow...")
                        if (observerConfigured && !bleManager.initStarted) {
                            startPostConnectionFlow(gatt, device)
                        } else if (!observerConfigured) {
                            AppLog.e(tagBle, "Failed to configure notification observer on B003")
                        }
                    } catch (e: Exception) {
                        AppLog.e(tagBle, "Exception during setupNotificationObserver: ${e.message}", e)
                        _connectionState.value = ConnectionState.Error("Setup notification failed: ${e.message ?: "Unknown"}")
                    }
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in onServicesDiscovered: ${e.message}", e)
                _connectionState.value = ConnectionState.Error("Discovery error: ${e.message ?: "Unknown"}")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            try {
                AppLog.i(tagBle, "onDescriptorWrite [${descriptor.characteristic?.uuid} / ${descriptor.uuid}]: status=$status")
                bleManager.onDescriptorWriteCompleted(descriptor, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    bleManager.markNotificationEnabled(true)
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in onDescriptorWrite: ${e.message}", e)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            try {
                val statusStr = if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "STATUS_$status"
                AppLog.d(tagBle, "onCharacteristicWrite [${characteristic.uuid}]: $statusStr")
                bleManager.onCharacteristicWriteCompleted(characteristic, status)
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in onCharacteristicWrite: ${e.message}", e)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            try {
                val data = characteristic.value ?: return
                handleIncomingData(characteristic, data)
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in onCharacteristicRead: ${e.message}", e)
            }
        }

        // Newer Android 13+ (API 33+) callback
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            try {
                handleIncomingData(characteristic, value)
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in onCharacteristicChanged: ${e.message}", e)
            }
        }

        // Legacy pre-Android 13 callback
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            try {
                val data = characteristic.value ?: return
                handleIncomingData(characteristic, data)
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in legacy onCharacteristicChanged: ${e.message}", e)
            }
        }

        private fun handleIncomingData(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            try {
                bleManager.onNotificationReceived(characteristic, data)
                Protocol.parseBatteryLevel(data)?.let { battery ->
                    if (battery.level > 0) {
                        _snapshot.value = _snapshot.value.copy(battery = battery)
                    }
                }
            } catch (e: Exception) {
                AppLog.e(tagBle, "Exception in handleIncomingData: ${e.message}", e)
            }
        }
    }
}
