package com.galaxy.ring.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringScheduleTest {

    private val min = 60_000L
    private val now = 1_000_000_000_000L

    private fun configs(
        hr: MonitorConfig = MonitorConfig(true, 30),
        spo2: MonitorConfig = MonitorConfig(true, 60),
        hrv: MonitorConfig = MonitorConfig(false, 60),
        stress: MonitorConfig = MonitorConfig(false, 60)
    ) = mapOf(
        MonitorMetric.HEART_RATE to hr,
        MonitorMetric.SPO2 to spo2,
        MonitorMetric.HRV to hrv,
        MonitorMetric.STRESS to stress
    )

    @Test
    fun defaultIntervalsMatchRwfit() {
        assertEquals(30, MonitorMetric.HEART_RATE.defaultIntervalMin)
        assertEquals(60, MonitorMetric.SPO2.defaultIntervalMin)
        assertEquals(60, MonitorMetric.HRV.defaultIntervalMin)
        assertEquals(60, MonitorMetric.STRESS.defaultIntervalMin)
    }

    @Test
    fun neverRunMetricsAreDueWhenEnabled() {
        val due = MonitoringSchedule.dueMetrics(configs(), emptyMap(), now)
        assertEquals(listOf(MonitorMetric.HEART_RATE, MonitorMetric.SPO2), due)
    }

    @Test
    fun eachMetricUsesItsOwnInterval() {
        val last = mapOf(
            MonitorMetric.HEART_RATE to now - 31 * min, // 30 min interval elapsed
            MonitorMetric.SPO2 to now - 31 * min        // 60 min interval not elapsed
        )
        val due = MonitoringSchedule.dueMetrics(configs(), last, now)
        assertEquals(listOf(MonitorMetric.HEART_RATE), due)
    }

    @Test
    fun disabledMetricsAreNeverDue() {
        val due = MonitoringSchedule.dueMetrics(
            configs(hr = MonitorConfig(false, 30), spo2 = MonitorConfig(false, 60)),
            emptyMap(),
            now
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun jitterSlackStillCountsAsDue() {
        val last = mapOf(MonitorMetric.HEART_RATE to now - (30 * min - 10_000L))
        assertTrue(MonitorMetric.HEART_RATE in MonitoringSchedule.dueMetrics(configs(), last, now))
    }

    @Test
    fun shortestIntervalOnlyCountsEnabledMetrics() {
        assertEquals(30, MonitoringSchedule.shortestIntervalMin(configs()))
        assertEquals(
            60,
            MonitoringSchedule.shortestIntervalMin(configs(hr = MonitorConfig(false, 30)))
        )
        assertNull(
            MonitoringSchedule.shortestIntervalMin(
                configs(hr = MonitorConfig(false, 30), spo2 = MonitorConfig(false, 60))
            )
        )
    }

    @Test
    fun statsUseOnlyRealPoints() {
        val stats = MetricStats.compute(
            listOf(MetricPoint(1L, 60f), MetricPoint(2L, 80f), MetricPoint(3L, 70f))
        )
        assertEquals(60f, stats!!.min, 0.001f)
        assertEquals(70f, stats.avg, 0.001f)
        assertEquals(80f, stats.max, 0.001f)
        assertEquals(3, stats.count)
        assertNull(MetricStats.compute(emptyList()))
    }

    @Test
    fun brightnessHasFourRwfitLevels() {
        assertEquals(listOf("Off", "Dim light", "Soft light", "Strong light"), ScreenBrightness.values().map { it.label })
        assertEquals(listOf(0, 1, 2, 3), ScreenBrightness.values().map { it.level })
        assertEquals(ScreenBrightness.SOFT, ScreenBrightness.fromLevel(2))
        assertFalse(ScreenBrightness.fromLevel(99) == ScreenBrightness.OFF)
    }
}
