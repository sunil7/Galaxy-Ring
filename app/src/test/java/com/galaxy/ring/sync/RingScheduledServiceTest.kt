package com.galaxy.ring.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RingScheduledServiceTest {

    private lateinit var context: Context
    private lateinit var settings: MonitoringSettings

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("galaxy_ring_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        settings = MonitoringSettings(context)
    }

    @Test
    fun wakeShortestIntervalMinFollowsEnabledMetrics() {
        // Defaults: HR=30m (enabled), SpO2=60m (enabled), HRV=60m (disabled), Stress=60m (disabled)
        assertEquals(30, RingScheduledService.computeWakeIntervalMin(settings))

        // Change HR interval to 15m
        settings.setIntervalMin(MonitorMetric.HEART_RATE, 15)
        assertEquals(15, RingScheduledService.computeWakeIntervalMin(settings))

        // Disable HR -> shortest should become 60m (SpO2)
        settings.setEnabled(MonitorMetric.HEART_RATE, false)
        assertEquals(60, RingScheduledService.computeWakeIntervalMin(settings))

        // Disable SpO2 -> no metrics enabled -> null
        settings.setEnabled(MonitorMetric.SPO2, false)
        assertNull(RingScheduledService.computeWakeIntervalMin(settings))
    }

    @Test
    fun measureDueNowOnlyAndMarkRunAfter() = runBlocking {
        // Initially, both HR and SpO2 are due since lastRunMs = 0
        val measured = mutableListOf<MonitorMetric>()
        val executed = RingScheduledService.executeDueMeasurements(settings) { metric ->
            measured.add(metric)
        }

        assertEquals(listOf(MonitorMetric.HEART_RATE, MonitorMetric.SPO2), executed)
        assertEquals(listOf(MonitorMetric.HEART_RATE, MonitorMetric.SPO2), measured)

        // Immediately after markRun, dueNow() should be empty
        assertTrue(settings.dueNow().isEmpty())

        // Executing again immediately measures nothing
        measured.clear()
        val secondRun = RingScheduledService.executeDueMeasurements(settings) { metric ->
            measured.add(metric)
        }
        assertTrue(secondRun.isEmpty())
        assertTrue(measured.isEmpty())
    }

    @Test
    fun markRunIsRecordedEvenOnMeasurementFailure() = runBlocking {
        var failed = false
        try {
            RingScheduledService.executeDueMeasurements(settings) { metric ->
                if (metric == MonitorMetric.HEART_RATE) {
                    throw RuntimeException("Simulated ring timeout")
                }
            }
        } catch (e: RuntimeException) {
            failed = true
        }
        assertTrue(failed)

        // Heart rate's run was marked, so it shouldn't immediately re-trigger
        assertTrue(settings.lastRunMs(MonitorMetric.HEART_RATE) > 0L)
    }
}
