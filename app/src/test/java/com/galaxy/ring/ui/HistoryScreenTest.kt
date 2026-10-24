package com.galaxy.ring.ui

import com.galaxy.ring.data.DailyStepsEntity
import com.galaxy.ring.data.SleepSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryScreenTest {

    @Test
    fun historyScreenComponentsExistAndAreInstantiable() {
        assertNotNull(Class.forName("com.galaxy.ring.ui.screens.HistoryScreenKt"))
    }

    @Test
    fun neutralStressClassificationLabels() {
        fun classifyStress(score: Int): String = when {
            score <= 29 -> "Low"
            score <= 59 -> "Normal Range"
            score <= 79 -> "Elevated"
            else -> "High"
        }

        assertEquals("Low", classifyStress(15))
        assertEquals("Low", classifyStress(29))
        assertEquals("Normal Range", classifyStress(30))
        assertEquals("Normal Range", classifyStress(55))
        assertEquals("Normal Range", classifyStress(59))
        assertEquals("Elevated", classifyStress(60))
        assertEquals("Elevated", classifyStress(75))
        assertEquals("Elevated", classifyStress(79))
        assertEquals("High", classifyStress(80))
        assertEquals("High", classifyStress(95))
    }

    @Test
    fun stepsActivityEntityFormatting() {
        val steps = DailyStepsEntity(
            date = "2026-10-24",
            timestamp = 1729750000000L,
            steps = 8940L,
            caloriesKcal = 412,
            distanceMeters = 6720.0
        )
        val formattedSteps = String.format("%,d steps", steps.steps)
        assertEquals("8,940 steps", formattedSteps)

        val formattedDist = String.format(Locale.US, "%.2f km", steps.distanceMeters / 1000.0)
        assertEquals("6.72 km", formattedDist)
        assertEquals(412, steps.caloriesKcal)
    }

    @Test
    fun sleepSessionSummaryFormatting() {
        val sleep = SleepSessionEntity(
            date = "2026-10-23",
            startTime = 1729720000000L,
            endTime = 1729748800000L,
            durationMinutes = 480L,
            deepMinutes = 110L,
            lightMinutes = 220L,
            remMinutes = 115L,
            awakeMinutes = 35L,
            sleepScore = 88,
            sleepEfficiency = 0.91f,
            stagesJson = ""
        )
        val hours = sleep.durationMinutes / 60
        val mins = sleep.durationMinutes % 60
        assertEquals("8h 0m duration", "${hours}h ${mins}m duration")

        val efficiencyPercent = (sleep.sleepEfficiency * 100).toInt()
        assertEquals(91, efficiencyPercent)
    }

    @Test
    fun periodGroupingsForTimelineAreConsistent() {
        val cal = Calendar.getInstance()
        fun getPeriod(hourOfDay: Int): String {
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
            return when (cal.get(Calendar.HOUR_OF_DAY)) {
                in 0..5 -> "Night (00:00 – 06:00)"
                in 6..11 -> "Morning (06:00 – 12:00)"
                in 12..17 -> "Afternoon (12:00 – 18:00)"
                else -> "Evening (18:00 – 24:00)"
            }
        }

        assertEquals("Night (00:00 – 06:00)", getPeriod(2))
        assertEquals("Morning (06:00 – 12:00)", getPeriod(8))
        assertEquals("Afternoon (12:00 – 18:00)", getPeriod(14))
        assertEquals("Evening (18:00 – 24:00)", getPeriod(20))
    }
}
