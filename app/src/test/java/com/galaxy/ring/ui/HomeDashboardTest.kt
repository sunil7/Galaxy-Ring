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
}
