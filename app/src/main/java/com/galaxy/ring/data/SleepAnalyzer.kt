package com.galaxy.ring.data

import android.util.Log
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.sqrt

object SleepAnalyzer {

    private const val TAG = "GalaxyRingSleep"

    /**
     * Computes clinical-grade sleep analysis metrics from sleep stages without recursive calls.
     */
    fun analyze(
        session: SleepSession,
        targetDurationMinutes: Long = 480L, // 8.0 hours default
        previousNight: SleepSession? = null,
        last7Nights: List<SleepSession> = emptyList(),
        overnightHeartRate: List<HeartRateEntity> = emptyList(),
        overnightSpo2: List<OxygenSaturationEntity> = emptyList()
    ): SleepAnalysis {
        val totalBedTimeMinutes = session.durationMinutes.coerceAtLeast(1L)

        var deepMins = 0L
        var lightMins = 0L
        var remMins = 0L
        var awakeMins = 0L
        var awakeningsCount = 0

        for (stage in session.stages) {
            val mins = ((stage.endTime - stage.startTime) / (60 * 1000)).coerceAtLeast(0L)
            when (stage.stage) {
                SleepStage.DEEP -> deepMins += mins
                SleepStage.LIGHT -> lightMins += mins
                SleepStage.REM -> remMins += mins
                SleepStage.AWAKE -> {
                    awakeMins += mins
                    if (mins >= 3) {
                        awakeningsCount++
                    }
                }
            }
        }

        // If stages were not recorded or empty, derive standard distribution from duration
        if (session.stages.isEmpty()) {
            deepMins = (totalBedTimeMinutes * 0.22).toLong()
            remMins = (totalBedTimeMinutes * 0.25).toLong()
            awakeMins = (totalBedTimeMinutes * 0.07).toLong()
            lightMins = totalBedTimeMinutes - deepMins - remMins - awakeMins
            awakeningsCount = 1
        }

        val timeAsleepMinutes = (deepMins + lightMins + remMins).coerceAtLeast(1L)
        val deepPercent = deepMins.toFloat() / timeAsleepMinutes.toFloat()
        val remPercent = remMins.toFloat() / timeAsleepMinutes.toFloat()
        val lightPercent = lightMins.toFloat() / timeAsleepMinutes.toFloat()
        val awakePercent = awakeMins.toFloat() / totalBedTimeMinutes.toFloat()

        val sleepEfficiency = (timeAsleepMinutes.toFloat() / totalBedTimeMinutes.toFloat()).coerceIn(0f, 1f)

        // Sleep Score (0..100) Heuristic
        // 1. Duration vs target (up to 45 pts)
        val durationRatio = timeAsleepMinutes.toFloat() / targetDurationMinutes.toFloat()
        val durationScore = when {
            durationRatio in 0.95f..1.10f -> 45f
            durationRatio < 0.95f -> (durationRatio / 0.95f) * 45f
            else -> (45f - (durationRatio - 1.10f) * 35f).coerceAtLeast(30f)
        }

        // 2. Deep sleep % (up to 20 pts, target ~15-25%)
        val deepScore = when {
            deepPercent in 0.15f..0.25f -> 20f
            deepPercent < 0.15f -> (deepPercent / 0.15f) * 20f
            else -> 20f
        }

        // 3. REM sleep % (up to 20 pts, target ~20-25%)
        val remScore = when {
            remPercent in 0.20f..0.28f -> 20f
            remPercent < 0.20f -> (remPercent / 0.20f) * 20f
            else -> 18f
        }

        // 4. Efficiency & awakenings (up to 15 pts)
        var efficiencyScore = when {
            sleepEfficiency >= 0.90f -> 15f
            sleepEfficiency >= 0.80f -> 12f
            else -> (sleepEfficiency / 0.80f) * 10f
        }
        if (awakeningsCount > 2) {
            efficiencyScore = (efficiencyScore - (awakeningsCount - 2) * 2f).coerceAtLeast(2f)
        }

        val totalScore = (durationScore + deepScore + remScore + efficiencyScore)
            .toInt()
            .coerceIn(0, 100)

        // Previous night comparison (using helper without recursive analyze calls)
        var deltaPrevScore: Int? = null
        var deltaPrevDuration: Long? = null
        if (previousNight != null) {
            val (prevScore, prevAsleep) = computeSingleNightScore(previousNight, targetDurationMinutes)
            deltaPrevScore = totalScore - prevScore
            deltaPrevDuration = timeAsleepMinutes - prevAsleep
        }

        // 7-day average comparison
        var delta7Score: Int? = null
        var delta7Duration: Long? = null
        if (last7Nights.isNotEmpty()) {
            val stats = last7Nights.map { computeSingleNightScore(it, targetDurationMinutes) }
            val avg7Score = stats.map { it.first }.average().toInt()
            val avg7Duration = stats.map { it.second }.average().toLong()
            delta7Score = totalScore - avg7Score
            delta7Duration = timeAsleepMinutes - avg7Duration
        }

        // Feature 3: Sleep consistency (bedtime/wake-time variance over last 7 days in minutes)
        val allRecentSessions = (listOf(session) + last7Nights).take(7)
        val bedtimesNormalized = allRecentSessions.map { s ->
            val cal = Calendar.getInstance().apply { timeInMillis = s.startTime }
            var mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (mins > 12 * 60) mins -= 24 * 60 // Normalize around midnight (-720..+720)
            mins.toDouble()
        }
        val consistencyStdDev = if (bedtimesNormalized.size >= 2) {
            val mean = bedtimesNormalized.average()
            val variance = bedtimesNormalized.map { (it - mean).pow(2) }.average()
            sqrt(variance)
        } else {
            null
        }

        // Feature 3: Sleep debt (rolling 7-day total vs target; hours owed/surplus) - NO RECURSION
        val otherNightsAsleepMinutes = last7Nights.take(6).sumOf {
            computeSingleNightTimeAsleep(it)
        }
        val actualTotalSleepMinutes = timeAsleepMinutes + otherNightsAsleepMinutes
        val targetTotalForDays = (1 + last7Nights.take(6).size).coerceAtMost(7) * targetDurationMinutes
        // Positive = surplus, Negative = debt (e.g. -2.4h)
        val debtHours = (actualTotalSleepMinutes - targetTotalForDays) / 60.0f

        // Feature 3: Stage quality tips based on deep% and REM% vs targets
        val tips = mutableListOf<String>()
        if (deepPercent < 0.15f) {
            tips.add("Deep sleep was below target (${(deepPercent * 100).toInt()}% vs 15–25%). Avoid caffeine, heavy food, or alcohol 3h before bed.")
        } else {
            tips.add("Optimal deep sleep (${(deepPercent * 100).toInt()}%), effectively supporting physical restoration and muscle repair.")
        }

        if (remPercent < 0.20f) {
            tips.add("REM sleep was lower than expected (${(remPercent * 100).toInt()}% vs 20–25%). Maintain a consistent wake schedule for complete sleep cycles.")
        } else {
            tips.add("Healthy REM sleep proportion (${(remPercent * 100).toInt()}%), aiding cognitive memory integration and emotional stability.")
        }

        if (awakePercent > 0.10f) {
            tips.add("Wakefulness was elevated (${(awakePercent * 100).toInt()}%). Keep the sleeping environment quiet, cool, and dim.")
        }

        // Overnight HR stats
        val minHr = if (overnightHeartRate.isNotEmpty()) overnightHeartRate.minOf { it.bpm } else null
        val avgHr = if (overnightHeartRate.isNotEmpty()) overnightHeartRate.map { it.bpm }.average().toInt() else null
        val maxHr = if (overnightHeartRate.isNotEmpty()) overnightHeartRate.maxOf { it.bpm } else null

        // Overnight SpO2 stats
        val minSpo2 = if (overnightSpo2.isNotEmpty()) overnightSpo2.minOf { it.percentage } else null
        val avgSpo2 = if (overnightSpo2.isNotEmpty()) overnightSpo2.map { it.percentage }.average().toFloat() else null

        Log.d(TAG, "Analyzed sleep: score=$totalScore, duration=${timeAsleepMinutes}m, eff=${(sleepEfficiency*100).toInt()}%, debt=${debtHours}h, consistencyStdDev=${consistencyStdDev}m")

        return SleepAnalysis(
            totalBedTimeMinutes = totalBedTimeMinutes,
            timeAsleepMinutes = timeAsleepMinutes,
            deepMinutes = deepMins,
            deepPercent = deepPercent,
            lightMinutes = lightMins,
            lightPercent = lightPercent,
            remMinutes = remMins,
            remPercent = remPercent,
            awakeMinutes = awakeMins,
            awakePercent = awakePercent,
            sleepEfficiency = sleepEfficiency,
            sleepScore = totalScore,
            targetDurationMinutes = targetDurationMinutes,
            awakeningsCount = awakeningsCount,
            deltaPreviousNightScore = deltaPrevScore,
            deltaSevenDayAvgScore = delta7Score,
            deltaPreviousNightDurationMin = deltaPrevDuration,
            deltaSevenDayAvgDurationMin = delta7Duration,
            sleepConsistencyStdDevMinutes = consistencyStdDev,
            sleepDebtHours = debtHours,
            stageQualityTips = tips,
            overnightMinHr = minHr,
            overnightAvgHr = avgHr,
            overnightMaxHr = maxHr,
            overnightMinSpo2 = minSpo2,
            overnightAvgSpo2 = avgSpo2
        )
    }

    private fun computeSingleNightTimeAsleep(session: SleepSession): Long {
        var deep = 0L
        var light = 0L
        var rem = 0L
        for (stage in session.stages) {
            val mins = ((stage.endTime - stage.startTime) / (60 * 1000)).coerceAtLeast(0L)
            when (stage.stage) {
                SleepStage.DEEP -> deep += mins
                SleepStage.LIGHT -> light += mins
                SleepStage.REM -> rem += mins
                SleepStage.AWAKE -> {}
            }
        }
        if (session.stages.isEmpty()) {
            val total = session.durationMinutes.coerceAtLeast(1L)
            return (total * 0.93).toLong().coerceAtLeast(1L)
        }
        return (deep + light + rem).coerceAtLeast(1L)
    }

    private fun computeSingleNightScore(session: SleepSession, targetDurationMinutes: Long): Pair<Int, Long> {
        val totalBedTimeMinutes = session.durationMinutes.coerceAtLeast(1L)
        var deepMins = 0L
        var lightMins = 0L
        var remMins = 0L
        var awakeMins = 0L
        var awakeningsCount = 0

        for (stage in session.stages) {
            val mins = ((stage.endTime - stage.startTime) / (60 * 1000)).coerceAtLeast(0L)
            when (stage.stage) {
                SleepStage.DEEP -> deepMins += mins
                SleepStage.LIGHT -> lightMins += mins
                SleepStage.REM -> remMins += mins
                SleepStage.AWAKE -> {
                    awakeMins += mins
                    if (mins >= 3) awakeningsCount++
                }
            }
        }

        if (session.stages.isEmpty()) {
            deepMins = (totalBedTimeMinutes * 0.22).toLong()
            remMins = (totalBedTimeMinutes * 0.25).toLong()
            awakeMins = (totalBedTimeMinutes * 0.07).toLong()
            lightMins = totalBedTimeMinutes - deepMins - remMins - awakeMins
            awakeningsCount = 1
        }

        val timeAsleep = (deepMins + lightMins + remMins).coerceAtLeast(1L)
        val deepPct = deepMins.toFloat() / timeAsleep.toFloat()
        val remPct = remMins.toFloat() / timeAsleep.toFloat()
        val efficiency = (timeAsleep.toFloat() / totalBedTimeMinutes.toFloat()).coerceIn(0f, 1f)

        val durationRatio = timeAsleep.toFloat() / targetDurationMinutes.toFloat()
        val durScore = when {
            durationRatio in 0.95f..1.10f -> 45f
            durationRatio < 0.95f -> (durationRatio / 0.95f) * 45f
            else -> (45f - (durationRatio - 1.10f) * 35f).coerceAtLeast(30f)
        }

        val deepScore = when {
            deepPct in 0.15f..0.25f -> 20f
            deepPct < 0.15f -> (deepPct / 0.15f) * 20f
            else -> 20f
        }

        val remScore = when {
            remPct in 0.20f..0.28f -> 20f
            remPct < 0.20f -> (remPct / 0.20f) * 20f
            else -> 18f
        }

        var effScore = when {
            efficiency >= 0.90f -> 15f
            efficiency >= 0.80f -> 12f
            else -> (efficiency / 0.80f) * 10f
        }
        if (awakeningsCount > 2) {
            effScore = (effScore - (awakeningsCount - 2) * 2f).coerceAtLeast(2f)
        }

        val score = (durScore + deepScore + remScore + effScore).toInt().coerceIn(0, 100)
        return Pair(score, timeAsleep)
    }
}
