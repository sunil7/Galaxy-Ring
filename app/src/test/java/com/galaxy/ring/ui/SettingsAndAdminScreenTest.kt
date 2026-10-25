package com.galaxy.ring.ui

import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSchedule
import com.galaxy.ring.data.ScreenBrightness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAndAdminScreenTest {

    @Test
    fun adminAndSettingsClassesExist() {
        assertNotNull(Class.forName("com.galaxy.ring.ui.screens.AdminScreenKt"))
        assertNotNull(Class.forName("com.galaxy.ring.ui.screens.SettingsScreenKt"))
    }

    @Test
    fun screenBrightnessValuesAreValid() {
        val values = ScreenBrightness.values()
        assertEquals(4, values.size)
        assertEquals("Off", ScreenBrightness.OFF.label)
        assertEquals("Dim light", ScreenBrightness.DIM.label)
        assertEquals("Soft light", ScreenBrightness.SOFT.label)
        assertEquals("Strong light", ScreenBrightness.STRONG.label)
    }

    @Test
    fun monitoringScheduleIntervalOptions() {
        val options = MonitoringSchedule.INTERVAL_OPTIONS_MIN
        assertTrue(options.contains(15))
        assertTrue(options.contains(30))
        assertTrue(options.contains(60))
        assertTrue(options.contains(120))
    }

    @Test
    fun monitorMetricsKeysAreUnique() {
        val metrics = MonitorMetric.values()
        val keys = metrics.map { it.prefKey }.toSet()
        assertEquals(metrics.size, keys.size)
    }
}
