package com.galaxy.ring.data

/** One real stored reading (no interpolation, no dummy points). */
data class MetricPoint(val timestamp: Long, val value: Float)

/** Min | Avg | Max for the selected day; null when there are no readings. */
data class MetricStats(val min: Float, val avg: Float, val max: Float, val count: Int) {
    companion object {
        fun compute(points: List<MetricPoint>): MetricStats? {
            if (points.isEmpty()) return null
            val values = points.map { it.value }
            return MetricStats(
                min = values.min(),
                avg = values.sum() / values.size,
                max = values.max(),
                count = values.size
            )
        }
    }
}

fun HeartRateEntity.toMetricPoint(): MetricPoint = MetricPoint(timestamp, bpm.toFloat())

fun OxygenSaturationEntity.toMetricPoint(): MetricPoint = MetricPoint(timestamp, percentage)

fun HrvEntity.toMetricPoint(): MetricPoint = MetricPoint(timestamp, hrvMs.toFloat())

fun StressEntity.toMetricPoint(): MetricPoint = MetricPoint(timestamp, stressScore.toFloat())
