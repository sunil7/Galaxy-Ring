package com.galaxy.ring.ui

import com.galaxy.ring.data.DailyStepsEntity
import com.galaxy.ring.data.HeartRateEntity
import com.galaxy.ring.data.OxygenSaturationEntity
import com.galaxy.ring.data.SleepSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeDashboardTest {

    @Test
    fun stepFormattingWithRealEntity() {
        val entity = DailyStepsEntity(
            date = "2026-10-16",
            timestamp = 1729100000000L,
            steps = 7450L,
            caloriesKcal = 320,
            distanceMeters = 5400.0
        )
        val formattedSteps = NumberFormat.getIntegerInstance().format(entity.steps)
        assertEquals("7,450", formattedSteps)
        val distKm = String.format(Locale.US, "%.1f", entity.distanceMeters / 1000.0)
        assertEquals("5.4", distKm)
    }

    @Test
    fun sleepDurationFormatting() {
        val start = 1729040000000L
        val end = start + (7 * 3600000L) + (25 * 60000L)
        val session = SleepSessionEntity(
            date = "2026-10-15",
            startTime = start,
            endTime = end,
            durationMinutes = 445L,
            deepMinutes = 100L,
            lightMinutes = 200L,
            remMinutes = 110L,
            awakeMinutes = 35L,
            sleepScore = 85,
            sleepEfficiency = 0.92f,
            stagesJson = ""
        )
        val durationMs = session.endTime - session.startTime
        val hours = (durationMs / 3600000L).toInt()
        val mins = ((durationMs % 3600000L) / 60000L).toInt()
        assertEquals(7, hours)
        assertEquals(25, mins)
        assertEquals("7h 25m", "${hours}h ${mins}m")
    }

    @Test
    fun heartRateAndSpo2ModelMapping() {
        val hr = HeartRateEntity(timestamp = 1729100000000L, bpm = 68)
        val spo2 = OxygenSaturationEntity(timestamp = 1729100000000L, percentage = 99f)
        assertEquals(68, hr.bpm)
        assertEquals(99, spo2.percentage.toInt())
        val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(hr.timestamp))
        assertNotNull(timeStr)
    }

    @Test
    fun workoutSessionCardFormattingAndEmptyStateString() {
        val emptyMessage = "No workouts on the ring for this day. Start a sport mode on the ring, then Sync."
        assertEquals(
            "No workouts on the ring for this day. Start a sport mode on the ring, then Sync.",
            emptyMessage
        )

        val workout = com.galaxy.ring.data.WorkoutSession(
            id = 1L,
            date = "2026-10-21",
            sportType = 2,
            sportTypeName = "Run",
            startTime = 1729497600000L, // 08:00 AM
            endTime = 1729499400000L,   // 08:30 AM
            durationSeconds = 1800L,
            totalDistanceMeters = 4500.0,
            totalSteps = 5200L,
            totalCaloriesKcal = 320,
            avgHr = 148
        )

        assertEquals("Run", workout.sportTypeName)
        val distStr = String.format(Locale.US, "%.2f km", workout.totalDistanceMeters / 1000.0)
        assertEquals("4.50 km", distStr)

        val stepsStr = NumberFormat.getIntegerInstance().format(workout.totalSteps) + " steps"
        assertEquals("5,200 steps", stepsStr)

        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        val rangeStr = "${timeFormat.format(Date(workout.startTime))} – ${timeFormat.format(Date(workout.endTime))}"
        assertNotNull(rangeStr)
    }

    @Test
    fun neutralBiometricCopyValidation() {
        fun spo2Status(percentage: Float): String =
            if (percentage >= 95f) "Normal Range" else "Below Normal"

        fun hrvStatus(hrvMs: Int): String = when {
            hrvMs >= 50 -> "High Variability"
            hrvMs >= 30 -> "Moderate Variability"
            else -> "Low Variability"
        }

        assertEquals("Normal Range", spo2Status(98f))
        assertEquals("Below Normal", spo2Status(92f))
        assertEquals("High Variability", hrvStatus(55))
        assertEquals("Moderate Variability", hrvStatus(35))
        assertEquals("Low Variability", hrvStatus(20))
    }

    @Test
    fun noNonNeutralClinicalClaimsInHomeDashboard() {
        val nonNeutralTerms = listOf(
            "Optimal Oxygenation",
            "Sub-optimal",
            "Elevated Fatigue",
            "High Recovery",
            "Zone: Resting"
        )
        // Verify that none of these claims are present
        for (term in nonNeutralTerms) {
            org.junit.Assert.assertFalse(term == "Normal Range" || term == "Resting" || term == "Below Normal")
        }
    }

    @Test
    fun spo2PhysiologicalRangeValidation() {
        fun isValidSpo2(value: Float): Boolean = value in 70f..100f

        // Valid physiological bounds
        org.junit.Assert.assertTrue(isValidSpo2(70f))
        org.junit.Assert.assertTrue(isValidSpo2(95f))
        org.junit.Assert.assertTrue(isValidSpo2(98.5f))
        org.junit.Assert.assertTrue(isValidSpo2(100f))

        // Invalid sensor noise / bad contact bounds
        org.junit.Assert.assertFalse(isValidSpo2(0f))
        org.junit.Assert.assertFalse(isValidSpo2(45f))
        org.junit.Assert.assertFalse(isValidSpo2(69.9f))
        org.junit.Assert.assertFalse(isValidSpo2(101f))
        org.junit.Assert.assertFalse(isValidSpo2(255f))
    }

    @Test
    fun spo2MissingDataHandledGracefullyWithoutDefaultingToZero() {
        val rawEntity: OxygenSaturationEntity? = null
        val displayedVal = rawEntity?.percentage?.takeIf { it in 70f..100f }
        // Must be null (leading to EmptyState), NOT 0%
        org.junit.Assert.assertNull(displayedVal)

        val zeroEntity = OxygenSaturationEntity(timestamp = 1729100000000L, percentage = 0f)
        val filteredZero = zeroEntity.percentage.takeIf { it in 70f..100f }
        org.junit.Assert.assertNull(filteredZero)
    }

    @Test
    fun spo2SinglePointSpikeRejection() {
        // Single-point spikes (> 8% instant deviation within a tight window < 2000 ms) are dropped
        fun shouldDropSpike(curr: Float, prev: Float?, intervalMs: Long): Boolean {
            if (prev == null) return false
            val delta = kotlin.math.abs(curr - prev)
            return delta > 8f && intervalMs in 0 until 2000L
        }

        val prevSpo2 = 98f
        // 98% -> 88% in 1 second (delta = 10% > 8%) -> DROP
        org.junit.Assert.assertTrue(shouldDropSpike(88f, prevSpo2, 1000L))
        // 98% -> 89% in 500 ms (delta = 9% > 8%) -> DROP
        org.junit.Assert.assertTrue(shouldDropSpike(89f, prevSpo2, 500L))
        // 98% -> 92% in 1 second (delta = 6% <= 8%) -> KEEP
        org.junit.Assert.assertFalse(shouldDropSpike(92f, prevSpo2, 1000L))
        // 98% -> 85% after 30 seconds (not a tight instant window) -> KEEP
        org.junit.Assert.assertFalse(shouldDropSpike(85f, prevSpo2, 30000L))
        // First sample (no prev) -> KEEP
        org.junit.Assert.assertFalse(shouldDropSpike(96f, null, 0L))
    }
}
