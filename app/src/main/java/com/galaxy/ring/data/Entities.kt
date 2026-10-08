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
