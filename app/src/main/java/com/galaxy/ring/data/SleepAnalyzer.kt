package com.galaxy.ring.data

import android.util.Log

object SleepAnalyzer {

    private const val TAG = "GalaxyRingSleep"

    /**
     * Computes clinical-grade sleep analysis metrics from sleep stages.
     */
    fun analyze(
        session: SleepSession,
        targetDurationMinutes: Long = 480L, // 8.0 hours default
        previousNight: SleepSession? = null,
        last7Nights: List<SleepSession> = emptyList()
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

        // Previous night comparison
        var deltaPrevScore: Int? = null
        var deltaPrevDuration: Long? = null
        if (previousNight != null) {
            val prevAnalysis = analyze(previousNight, targetDurationMinutes)
            deltaPrevScore = totalScore - prevAnalysis.sleepScore
            deltaPrevDuration = timeAsleepMinutes - prevAnalysis.timeAsleepMinutes
        }

        // 7-day average comparison
        var delta7Score: Int? = null
        var delta7Duration: Long? = null
        if (last7Nights.isNotEmpty()) {
            val avg7Score = last7Nights.map { analyze(it, targetDurationMinutes).sleepScore }.average().toInt()
            val avg7Duration = last7Nights.map {
                val a = analyze(it, targetDurationMinutes)
                a.timeAsleepMinutes
            }.average().toLong()
            delta7Score = totalScore - avg7Score
            delta7Duration = timeAsleepMinutes - avg7Duration
        }

        Log.d(TAG, "Analyzed sleep: score=$totalScore, duration=${timeAsleepMinutes}m, eff=${(sleepEfficiency*100).toInt()}%")

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
            deltaSevenDayAvgDurationMin = delta7Duration
        )
    }
}
