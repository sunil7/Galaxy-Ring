package com.galaxy.ring.data

import java.time.Instant

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
 * Combined telemetry snapshot from the Galaxy Ring.
 */
data class RingHealthSnapshot(
    val battery: RingBattery = RingBattery(level = 82, isCharging = false),
    val latestHeartRate: HeartRateSample = HeartRateSample(bpm = 68),
    val heartRateHistory: List<HeartRateSample> = emptyList(),
    val steps: StepData = StepData(totalSteps = 6420),
    val temperature: SkinTemperature = SkinTemperature(temperatureCelsius = 36.4f, baselineDelta = 0.1f),
    val latestSleep: SleepSession? = null,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val isSimulatedTelemetry: Boolean = false
)

/**
 * BLE Connection states.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val device: RingDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
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
