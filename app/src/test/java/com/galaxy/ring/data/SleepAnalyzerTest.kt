package com.galaxy.ring.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SleepAnalyzerTest {

    @Test
    fun testAnalyzeExecutesWithoutRecursion() {
        val now = System.currentTimeMillis()
        val session = SleepSession(
            startTime = now - 8 * 3600000L,
            endTime = now,
            qualityScore = 85,
            stages = listOf(
                SleepStageRecord(SleepStage.LIGHT, now - 8 * 3600000L, now - 6 * 3600000L),
                SleepStageRecord(SleepStage.DEEP, now - 6 * 3600000L, now - 4 * 3600000L),
                SleepStageRecord(SleepStage.REM, now - 4 * 3600000L, now - 2 * 3600000L),
                SleepStageRecord(SleepStage.AWAKE, now - 2 * 3600000L, now)
            )
        )

        val last7Nights = (1..6).map { i ->
            SleepSession(
                startTime = now - (i * 24 + 8) * 3600000L,
                endTime = now - (i * 24) * 3600000L,
                qualityScore = 80 + i
            )
        }

        val analysis = SleepAnalyzer.analyze(
            session = session,
            targetDurationMinutes = 480L,
            previousNight = last7Nights.first(),
            last7Nights = last7Nights
        )

        assertNotNull(analysis)
        assertTrue(analysis.sleepScore in 0..100)
        assertNotNull(analysis.sleepConsistencyStdDevMinutes)
        assertTrue(analysis.stageQualityTips.isNotEmpty())
        assertNotNull(analysis.sleepDebtHours)
    }
}
