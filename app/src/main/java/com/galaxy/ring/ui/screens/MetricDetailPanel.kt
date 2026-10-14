package com.galaxy.ring.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.galaxy.ring.data.MetricPoint
import com.galaxy.ring.data.MetricStats
import com.galaxy.ring.data.MonitoringSchedule
import com.galaxy.ring.data.SleepRecordParser
import java.util.Calendar

private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun intervalLabel(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"

/**
 * RWFit-style detail panel for one metric (HR, SpO2, HRV or Stress) and one selected day:
 *  - optional scheduled-monitoring toggle + interval
 *  - day line chart (00:00 - 24:00)
 *  - Min | Avg | Max
 *  - "Detailed data" list: time + value, newest first
 * Only real readings are drawn or listed; an empty day shows a message, never placeholder data.
 */
@Composable
fun MetricDetailPanel(
    title: String,
    unit: String,
    points: List<MetricPoint>,
    modifier: Modifier = Modifier,
    monitoringLabel: String? = null,
    monitoringEnabled: Boolean = true,
    intervalMinutes: Int = 30,
    onMonitoringEnabledChange: ((Boolean) -> Unit)? = null,
    onIntervalChange: ((Int) -> Unit)? = null,
    valueFormat: (Float) -> String = { it.toInt().toString() }
) {
    val sorted = points.sortedBy { it.timestamp }
    val stats = MetricStats.compute(sorted)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)

        if (onMonitoringEnabledChange != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(monitoringLabel ?: "Scheduled monitoring", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = monitoringEnabled, onCheckedChange = onMonitoringEnabledChange)
            }
            if (onIntervalChange != null && monitoringEnabled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (option in MonitoringSchedule.INTERVAL_OPTIONS_MIN) {
                        val selected = option == intervalMinutes
                        TextButton(onClick = { onIntervalChange(option) }) {
                            Text(
                                text = intervalLabel(option),
                                style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sorted.isEmpty() || stats == null) {
            Text("No readings for this day.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        val dayStart = startOfDay(sorted.first().timestamp)
        val dayMs = 24L * 60L * 60L * 1000L
        var lo = stats.min
        var hi = stats.max
        if (hi - lo < 1f) {
            lo -= 1f
            hi += 1f
        }
        val pad = (hi - lo) * 0.1f
        lo -= pad
        hi += pad

        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            for (r in 0..4) {
                val y = size.height * r / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            fun xOf(ts: Long) = ((ts - dayStart).coerceIn(0L, dayMs).toFloat() / dayMs) * size.width
            fun yOf(v: Float) = size.height - ((v - lo) / (hi - lo)) * size.height

            val path = Path()
            sorted.forEachIndexed { i, p ->
                val x = xOf(p.timestamp)
                val y = yOf(p.value)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3f))
            for (p in sorted) {
                drawCircle(lineColor, radius = 4f, center = Offset(xOf(p.timestamp), yOf(p.value)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (label in listOf("00:00", "06:00", "12:00", "18:00", "24:00")) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell("Min", valueFormat(stats.min), unit)
            StatCell("Avg", valueFormat(stats.avg), unit)
            StatCell("Max", valueFormat(stats.max), unit)
        }

        Spacer(Modifier.height(16.dp))
        Text("Detailed data", style = MaterialTheme.typography.titleSmall)
        for (p in sorted.asReversed()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(SleepRecordParser.formatClock(p.timestamp), style = MaterialTheme.typography.bodyMedium)
                Text("${valueFormat(p.value)} $unit", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(unit, style = MaterialTheme.typography.labelSmall)
    }
}
