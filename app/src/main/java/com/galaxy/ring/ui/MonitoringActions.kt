package com.galaxy.ring.ui

import android.content.Context
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSchedule
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.sync.RingScheduledService

/**
 * Single entry point that pushes the current monitoring settings to the ring scheduler.
 * Used by SettingsScreen and HeartScreen so that a change made on either screen produces
 * the same service state.
 *
 * Logic copied from SettingsScreen.updateMonitoringService() (A2-P2.5).
 *
 * @param onBatteryPromptNeeded called right before the service starts when monitoring is
 *   enabled. SettingsScreen passes its battery-exclusion dialog trigger. Callers without a
 *   dialog pass nothing.
 */
fun applyMonitoringChange(
    context: Context,
    onBatteryPromptNeeded: () -> Unit = {}
) {
    val repo = GalaxyRingApp.instance.healthRepository
    val settings = MonitoringSettings(context)

    val hrConfig = settings.config(MonitorMetric.HEART_RATE)
    val spo2Config = settings.config(MonitorMetric.SPO2)
    val hrvConfig = settings.config(MonitorMetric.HRV)
    val stressConfig = settings.config(MonitorMetric.STRESS)

    val anyEnabled = hrConfig.enabled || spo2Config.enabled || hrvConfig.enabled || stressConfig.enabled
    repo.isScheduleEnabled = anyEnabled
    repo.scheduleCheckHeartRate = hrConfig.enabled
    repo.scheduleCheckSpo2 = spo2Config.enabled
    val shortest = MonitoringSchedule.shortestIntervalMin(settings.allConfigs())
    if (shortest != null) {
        repo.scheduleIntervalMinutes = shortest
    }
    if (anyEnabled) {
        onBatteryPromptNeeded()
        RingScheduledService.start(context)
    } else {
        if (!repo.isKeepConnected) {
            RingScheduledService.stop(context)
        }
    }
}
