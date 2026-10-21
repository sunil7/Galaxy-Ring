package com.galaxy.ring.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate_records")
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val recordingMethod: String = "AUTOMATIC" // "MANUAL" or "AUTOMATIC"
)

@Entity(tableName = "oxygen_saturation_records")
data class OxygenSaturationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val percentage: Float,
    val recordingMethod: String = "AUTOMATIC" // "MANUAL" or "AUTOMATIC"
)

@Entity(tableName = "daily_steps_records")
data class DailyStepsEntity(
    @PrimaryKey
    val date: String, // e.g. "2026-10-07"
    val timestamp: Long,
    val steps: Long,
    val caloriesKcal: Int,
    val distanceMeters: Double
)

@Entity(tableName = "sleep_session_records")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // e.g. "2026-10-07"
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Long,
    val deepMinutes: Long,
    val lightMinutes: Long,
    val remMinutes: Long,
    val awakeMinutes: Long,
    val sleepScore: Int,
    val sleepEfficiency: Float,
    val stagesJson: String
)

@Entity(tableName = "hrv_records")
data class HrvEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val hrvMs: Int,
    val recordingMethod: String = "MANUAL" // "MANUAL" or "AUTOMATIC"
)

@Entity(tableName = "stress_records")
data class StressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val stressScore: Int,
    val recordingMethod: String = "MANUAL" // "MANUAL" or "AUTOMATIC"
)

@Entity(tableName = "workout_session_records")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // e.g. "2026-10-07"
    val sportType: Int,
    val sportTypeName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val totalDistanceMeters: Double,
    val totalSteps: Long,
    val totalCaloriesKcal: Int,
    val avgHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val avgPaceSecondsPerKm: Double? = null,
    val avgSpeedKmH: Double? = null,
    val avgCadenceSpm: Int? = null,
    val pacePhasesJson: String = "",
    val hrPointsJson: String = "",
    val rawPayloadHex: String = ""
)

fun WorkoutSessionEntity.toWorkoutSession(): WorkoutSession = WorkoutSession(
    id = id,
    date = date,
    sportType = sportType,
    sportTypeName = sportTypeName,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    totalDistanceMeters = totalDistanceMeters,
    totalSteps = totalSteps,
    totalCaloriesKcal = totalCaloriesKcal,
    avgHr = avgHr,
    minHr = minHr,
    maxHr = maxHr,
    avgPaceSecondsPerKm = avgPaceSecondsPerKm,
    avgSpeedKmH = avgSpeedKmH,
    avgCadenceSpm = avgCadenceSpm,
    pacePhases = WorkoutRecordParser.decodePacePhases(pacePhasesJson),
    hrPoints = WorkoutRecordParser.decodeHrPoints(hrPointsJson),
    rawPayloadHex = rawPayloadHex
)

fun WorkoutSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
    id = id,
    date = date,
    sportType = sportType,
    sportTypeName = sportTypeName,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    totalDistanceMeters = totalDistanceMeters,
    totalSteps = totalSteps,
    totalCaloriesKcal = totalCaloriesKcal,
    avgHr = avgHr,
    minHr = minHr,
    maxHr = maxHr,
    avgPaceSecondsPerKm = avgPaceSecondsPerKm,
    avgSpeedKmH = avgSpeedKmH,
    avgCadenceSpm = avgCadenceSpm,
    pacePhasesJson = WorkoutRecordParser.encodePacePhases(pacePhases),
    hrPointsJson = WorkoutRecordParser.encodeHrPoints(hrPoints),
    rawPayloadHex = rawPayloadHex
)
