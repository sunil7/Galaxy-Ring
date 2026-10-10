package com.galaxy.ring.data

/**
 * Represents a discovered or connected Galaxy Ring device.
 */
data class RingDevice(
    val name: String,
    val address: String,
    val rssi: Int = -60,
    val isConnected: Boolean = false,
    val isBonded: Boolean = false
)

/**
 * Battery telemetry from the ring.
 */
data class RingBattery(
    val level: Int,
    val isCharging: Boolean = false,
    val estimatedDaysLeft: Float = (level * 7f) / 100f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Real-time or historical heart rate measurement.
 */
data class HeartRateSample(
    val bpm: Int,
    val confidence: Int = 100,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * SpO₂ (Blood Oxygen) measurement.
 */
data class OxygenSaturationSample(
    val percentage: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Step count and activity metrics.
 */
data class StepData(
    val totalSteps: Long,
    val goalSteps: Long = 10000,
    val caloriesKcal: Int = (totalSteps * 0.04).toInt(),
    val distanceMeters: Double = totalSteps * 0.76,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Skin temperature telemetry in Celsius.
 */
data class SkinTemperature(
    val temperatureCelsius: Float,
    val baselineDelta: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Sleep stage record.
 */
data class SleepStageRecord(
    val stage: SleepStage,
    val startTime: Long,
    val endTime: Long
)

enum class SleepStage {
    DEEP, LIGHT, REM, AWAKE
}

/**
 * Comprehensive sleep session data.
 */
data class SleepSession(
    val startTime: Long,
    val endTime: Long,
    val qualityScore: Int = 85,
    val stages: List<SleepStageRecord> = emptyList()
) {
    val durationMinutes: Long
        get() = (endTime - startTime) / (1000 * 60)
}

/**
 * In-depth sleep analysis calculated using clinical standard heuristics.
 */
data class SleepAnalysis(
    val totalBedTimeMinutes: Long,
    val timeAsleepMinutes: Long,
    val deepMinutes: Long,
    val deepPercent: Float,
    val lightMinutes: Long,
    val lightPercent: Float,
    val remMinutes: Long,
    val remPercent: Float,
    val awakeMinutes: Long,
    val awakePercent: Float,
    val sleepEfficiency: Float, // (timeAsleep / totalBedTime)
    val sleepScore: Int, // 0..100
    val targetDurationMinutes: Long = 480L, // default 8 hours
    val awakeningsCount: Int = 0,
    val deltaPreviousNightScore: Int? = null,
    val deltaSevenDayAvgScore: Int? = null,
    val deltaPreviousNightDurationMin: Long? = null,
    val deltaSevenDayAvgDurationMin: Long? = null,
    val sleepConsistencyStdDevMinutes: Double? = null,
    val sleepDebtHours: Float = 0f,
    val stageQualityTips: List<String> = emptyList(),
    val overnightMinHr: Int? = null,
    val overnightAvgHr: Int? = null,
    val overnightMaxHr: Int? = null,
    val overnightMinSpo2: Float? = null,
    val overnightAvgSpo2: Float? = null
)

/**
 * Combined telemetry snapshot from the Galaxy Ring.
 */
data class RingHealthSnapshot(
    val battery: RingBattery = RingBattery(level = 0, isCharging = false, timestamp = 0L),
    val latestHeartRate: HeartRateSample = HeartRateSample(bpm = 0, confidence = 0, timestamp = 0L),
    val heartRateHistory: List<HeartRateSample> = emptyList(),
    val latestOxygenSaturation: OxygenSaturationSample? = null,
    val oxygenSaturationHistory: List<OxygenSaturationSample> = emptyList(),
    val steps: StepData = StepData(totalSteps = 0L, caloriesKcal = 0, distanceMeters = 0.0, timestamp = 0L),
    val temperature: SkinTemperature = SkinTemperature(temperatureCelsius = 0f, baselineDelta = 0.0f, timestamp = 0L),
    val latestSleep: SleepSession? = null,
    val latestSleepAnalysis: SleepAnalysis? = null,
    val lastSyncTimestamp: Long = 0L,
    val isSimulatedTelemetry: Boolean = false
)

/**
 * BLE Connection states including granular post-connect phases.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val device: RingDevice) : ConnectionState()
    data class Initializing(val currentStep: Int, val totalSteps: Int) : ConnectionState()
    data class Syncing(val message: String) : ConnectionState()
    data class Ready(val device: RingDevice, val rxCountSinceConnect: Int = 0) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Status of active on-demand manual measurements.
 */
sealed class ManualMeasurementState {
    data object Idle : ManualMeasurementState()
    data class Measuring(val metric: String, val progress: Float = 0f) : ManualMeasurementState()
    data class Success(val metric: String, val displayValue: String, val timestamp: Long) : ManualMeasurementState()
    data class Error(val metric: String, val message: String) : ManualMeasurementState()
}

/**
 * Sync status with Health Connect.
 */
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
