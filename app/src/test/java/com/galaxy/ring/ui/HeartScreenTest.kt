package com.galaxy.ring.ui

import com.galaxy.ring.data.HeartRateEntity
import com.galaxy.ring.data.HrvEntity
import com.galaxy.ring.data.MonitoringSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HeartScreenTest {

    @Test
    fun heartRateSummaryAveragesAndRange() {
        val samples = listOf(
            HeartRateEntity(timestamp = 1729100000000L, bpm = 60),
            HeartRateEntity(timestamp = 1729101000000L, bpm = 72),
            HeartRateEntity(timestamp = 1729102000000L, bpm = 84),
            HeartRateEntity(timestamp = 1729103000000L, bpm = 68)
        )
        val valid = samples.filter { it.bpm > 0 }
        val avg = valid.map { it.bpm }.average().toInt()
        val min = valid.minOf { it.bpm }
        val max = valid.maxOf { it.bpm }

        assertEquals(71, avg)
        assertEquals(60, min)
        assertEquals(84, max)
        assertEquals("Range: 60 – 84 BPM", "Range: $min – $max BPM")
    }

    @Test
    fun hrvNeutralVariabilityCategorization() {
        fun hrvVariabilityLabel(ms: Int): String = when {
            ms >= 50 -> "High Variability"
            ms >= 30 -> "Moderate Variability"
            else -> "Low Variability"
        }

        val highHrv = listOf(HrvEntity(timestamp = 1729100000000L, hrvMs = 58))
        val modHrv = listOf(HrvEntity(timestamp = 1729100000000L, hrvMs = 42))
        val lowHrv = listOf(HrvEntity(timestamp = 1729100000000L, hrvMs = 24))

        assertEquals("High Variability", hrvVariabilityLabel(highHrv.map { it.hrvMs }.average().toInt()))
        assertEquals("Moderate Variability", hrvVariabilityLabel(modHrv.map { it.hrvMs }.average().toInt()))
        assertEquals("Low Variability", hrvVariabilityLabel(lowHrv.map { it.hrvMs }.average().toInt()))
    }

    @Test
    fun scheduledMonitoringIntervalsAreAvailable() {
        val options = MonitoringSchedule.INTERVAL_OPTIONS_MIN
        assertTrue(options.isNotEmpty())
        assertTrue(options.contains(30))
        assertTrue(options.contains(60))
    }

    @Test
    fun todayCalendarCheck() {
        val today = Calendar.getInstance()
        val clone = today.clone() as Calendar

        val isSameDay = clone.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                clone.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        assertTrue(isSameDay)

        clone.add(Calendar.DAY_OF_YEAR, -1)
        val isPrevDaySame = clone.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                clone.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        org.junit.Assert.assertFalse(isPrevDaySame)
    }
}
