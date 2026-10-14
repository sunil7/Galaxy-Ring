package com.galaxy.ring.data

import android.content.Context

/** Four RWFit-style Health Monitoring metrics. */
enum class MonitorMetric(
    val prefKey: String,
    val label: String,
    val defaultEnabled: Boolean,
    val defaultIntervalMin: Int
) {
    HEART_RATE("hr", "All-day Heart Rate", true, 30),
    SPO2("spo2", "Regular Blood oxygen", true, 60),
    HRV("hrv", "Regular HRV", false, 60),
    STRESS("stress", "Scheduled Stress", false, 60)
}

data class MonitorConfig(val enabled: Boolean, val intervalMin: Int)

/** Pure scheduling rules so the existing background service can drive per-metric intervals. */
object MonitoringSchedule {
    val INTERVAL_OPTIONS_MIN = listOf(15, 30, 60, 120, 240)
    const val MIN_INTERVAL_MIN = 5
    private const val SLACK_MS = 30_000L // tolerate scheduler jitter

    /** Metrics that are enabled and whose own interval has elapsed since their last run. */
    fun dueMetrics(
        configs: Map<MonitorMetric, MonitorConfig>,
        lastRunMs: Map<MonitorMetric, Long>,
        nowMs: Long
    ): List<MonitorMetric> = MonitorMetric.values().filter { m ->
        val c = configs[m] ?: return@filter false
        c.enabled && nowMs - (lastRunMs[m] ?: 0L) >= c.intervalMin * 60_000L - SLACK_MS
    }

    /** How often the single background service has to wake up (null when nothing is enabled). */
    fun shortestIntervalMin(configs: Map<MonitorMetric, MonitorConfig>): Int? =
        configs.filter { it.value.enabled }.minOfOrNull { it.value.intervalMin }
}

/** Ring screen brightness (RWFit: Off | Dim light | Soft light | Strong light). */
enum class ScreenBrightness(val level: Int, val label: String) {
    OFF(0, "Off"),
    DIM(1, "Dim light"),
    SOFT(2, "Soft light"),
    STRONG(3, "Strong light");

    companion object {
        fun fromLevel(level: Int): ScreenBrightness =
            values().firstOrNull { it.level == level } ?: STRONG
    }
}

/**
 * SharedPreferences-backed Health Monitoring + brightness settings.
 * Uses the same prefs file as RingHealthRepository, so there is no second settings store.
 */
class MonitoringSettings(context: Context) {

    private val prefs = context.getSharedPreferences("galaxy_ring_prefs", Context.MODE_PRIVATE)

    fun config(metric: MonitorMetric): MonitorConfig = MonitorConfig(
        enabled = prefs.getBoolean("monitor_${metric.prefKey}_enabled", metric.defaultEnabled),
        intervalMin = prefs.getInt("monitor_${metric.prefKey}_interval_min", metric.defaultIntervalMin)
    )

    fun allConfigs(): Map<MonitorMetric, MonitorConfig> =
        MonitorMetric.values().associateWith { config(it) }

    fun setEnabled(metric: MonitorMetric, enabled: Boolean) {
        prefs.edit().putBoolean("monitor_${metric.prefKey}_enabled", enabled).apply()
    }

    fun setIntervalMin(metric: MonitorMetric, minutes: Int) {
        prefs.edit()
            .putInt("monitor_${metric.prefKey}_interval_min", minutes.coerceAtLeast(MonitoringSchedule.MIN_INTERVAL_MIN))
            .apply()
    }

    fun lastRunMs(metric: MonitorMetric): Long =
        prefs.getLong("monitor_${metric.prefKey}_last_run", 0L)

    fun markRun(metric: MonitorMetric, timestampMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("monitor_${metric.prefKey}_last_run", timestampMs).apply()
    }

    /** Metrics the scheduler should measure right now. */
    fun dueNow(nowMs: Long = System.currentTimeMillis()): List<MonitorMetric> =
        MonitoringSchedule.dueMetrics(
            allConfigs(),
            MonitorMetric.values().associateWith { lastRunMs(it) },
            nowMs
        )

    // ---- Screen brightness (selected level + cached working key/payload shape from probing) ----

    var screenBrightness: ScreenBrightness
        get() = ScreenBrightness.fromLevel(prefs.getInt("screen_brightness_level", ScreenBrightness.STRONG.level))
        set(value) = prefs.edit().putInt("screen_brightness_level", value.level).apply()

    var brightnessKey: Int?
        get() = if (prefs.contains("brightness_key")) prefs.getInt("brightness_key", -1).takeIf { it != -1 } else null
        set(value) {
            if (value != null) prefs.edit().putInt("brightness_key", value).apply()
            else prefs.edit().remove("brightness_key").apply()
        }

    var brightnessShape: Int?
        get() = if (prefs.contains("brightness_shape")) prefs.getInt("brightness_shape", -1).takeIf { it != -1 } else null
        set(value) {
            if (value != null) prefs.edit().putInt("brightness_shape", value).apply()
            else prefs.edit().remove("brightness_shape").apply()
        }
}
