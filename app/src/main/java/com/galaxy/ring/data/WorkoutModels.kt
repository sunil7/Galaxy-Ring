package com.galaxy.ring.data

import java.util.Locale

data class PacePhase(
    val phaseIndex: Int,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val steps: Int,
    val paceSecondsPerKm: Int
)

data class SportHrPoint(
    val timestamp: Long,
    val bpm: Int
)

data class WorkoutSession(
    val id: Long = 0,
    val date: String, // "yyyy-MM-dd"
    val sportType: Int,
    val sportTypeName: String = WorkoutSportTypes.nameFor(sportType),
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val totalDistanceMeters: Int,
    val totalSteps: Int,
    val totalCaloriesKcal: Int,
    val avgHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val avgCadence: Int? = null,
    val avgPaceSecondsPerKm: Int? = null,
    val phases: List<PacePhase> = emptyList(),
    val hrPoints: List<SportHrPoint> = emptyList()
)

object WorkoutSportTypes {
    const val TYPE_OTHER = 0
    const val TYPE_WALK = 1
    const val TYPE_RUN = 2
    const val TYPE_RIDE = 3
    const val TYPE_HIKE = 4
    const val TYPE_JOG = 5
    const val TYPE_INDOOR_RUN = 6

    fun nameFor(type: Int): String = when (type) {
        1 -> "Walk"
        2 -> "Run"
        3 -> "Cycling"
        4 -> "Hike"
        5 -> "Jog"
        6 -> "Indoor Run"
        else -> "Activity"
    }
}

object WorkoutFormatter {
    fun formatDistance(meters: Int, useMiles: Boolean): String {
        return if (useMiles) {
            val miles = meters / 1609.344
            String.format(Locale.US, "%.2f mi", miles)
        } else {
            val km = meters / 1000.0
            String.format(Locale.US, "%.2f km", km)
        }
    }

    fun formatPace(paceSecPerKm: Int, useMiles: Boolean): String {
        if (paceSecPerKm <= 0) return "--"
        val sec = if (useMiles) (paceSecPerKm * 1.609344).toInt() else paceSecPerKm
        val m = sec / 60
        val s = sec % 60
        val unit = if (useMiles) "/mi" else "/km"
        return String.format(Locale.US, "%d'%02d\"%s", m, s, unit)
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    fun encodePhases(phases: List<PacePhase>): String {
        return phases.joinToString(";") {
            "${it.phaseIndex},${it.durationSeconds},${it.distanceMeters},${it.steps},${it.paceSecondsPerKm}"
        }
    }

    fun decodePhases(raw: String): List<PacePhase> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val tokens = part.split(",")
            if (tokens.size == 5) {
                runCatching {
                    PacePhase(
                        phaseIndex = tokens[0].toInt(),
                        durationSeconds = tokens[1].toInt(),
                        distanceMeters = tokens[2].toInt(),
                        steps = tokens[3].toInt(),
                        paceSecondsPerKm = tokens[4].toInt()
                    )
                }.getOrNull()
            } else null
        }
    }

    fun encodeHrPoints(points: List<SportHrPoint>): String {
        return points.joinToString(";") { "${it.timestamp},${it.bpm}" }
    }

    fun decodeHrPoints(raw: String): List<SportHrPoint> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val tokens = part.split(",")
            if (tokens.size == 2) {
                runCatching {
                    SportHrPoint(timestamp = tokens[0].toLong(), bpm = tokens[1].toInt())
                }.getOrNull()
            } else null
        }
    }
}

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
    avgCadence = avgCadence,
    avgPaceSecondsPerKm = avgPaceSecondsPerKm,
    phases = WorkoutFormatter.decodePhases(phasesJson),
    hrPoints = WorkoutFormatter.decodeHrPoints(hrPointsJson)
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
    avgCadence = avgCadence,
    avgPaceSecondsPerKm = avgPaceSecondsPerKm,
    phasesJson = WorkoutFormatter.encodePhases(phases),
    hrPointsJson = WorkoutFormatter.encodeHrPoints(hrPoints)
)
