package com.galaxy.ring.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.data.toMetricPoint
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.HrvTeal
import com.galaxy.ring.ui.theme.RosePulse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HeartScreen(
    onSyncFromRing: () -> Unit = {},
    isSyncing: Boolean = false,
    syncProgressText: String? = null
) {
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val dayHrSamples by repo.getHeartRateForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val dayHrvSamples by repo.getHrvForDay(selectedCalendar.time).collectAsState(initial = emptyList())

    val friendlyDateStr = remember(selectedCalendar.timeInMillis) {
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(selectedCalendar.time)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Date Selector Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val prev = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                            selectedCalendar = prev
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Day",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = friendlyDateStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                            selectedCalendar = next
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Day",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Heart Health Overview Card
        item {
            val hrValues = remember(dayHrSamples) { dayHrSamples.map { it.bpm }.filter { it > 0 } }
            val minHr = hrValues.minOrNull()
            val maxHr = hrValues.maxOrNull()
            val avgHr = if (hrValues.isNotEmpty()) hrValues.average().toInt() else null

            val hrvValues = remember(dayHrvSamples) { dayHrvSamples.map { it.hrvMs }.filter { it > 0 } }
            val avgHrv = if (hrvValues.isNotEmpty()) hrvValues.average().toInt() else null

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("heart_health_overview_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Heart health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = RosePulse,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Based on today's measurements",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (hrValues.isEmpty() && hrvValues.isEmpty()) {
                        Text(
                            text = "No readings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = minHr?.toString() ?: "--",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "bpm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AVG",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = avgHr?.toString() ?: "--",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "bpm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MAX",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = maxHr?.toString() ?: "--",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "bpm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AVG HRV",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = avgHrv?.toString() ?: "--",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = HrvTeal
                                )
                                Text(
                                    text = "ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Heart Rate Day Detail Panel
        item {
            val settings = remember { MonitoringSettings(app) }
            var hrConfig by remember { mutableStateOf(settings.config(MonitorMetric.HEART_RATE)) }
            val hrPoints = remember(dayHrSamples) { dayHrSamples.map { it.toMetricPoint() } }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("heart_rate_detail_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                MetricDetailPanel(
                    title = "Heart Rate",
                    unit = "BPM",
                    points = hrPoints,
                    modifier = Modifier.padding(16.dp),
                    monitoringLabel = "All-day Heart Rate",
                    monitoringEnabled = hrConfig.enabled,
                    intervalMinutes = hrConfig.intervalMin,
                    onMonitoringEnabledChange = { enabled ->
                        settings.setEnabled(MonitorMetric.HEART_RATE, enabled)
                        hrConfig = settings.config(MonitorMetric.HEART_RATE)
                    },
                    onIntervalChange = { interval ->
                        settings.setIntervalMin(MonitorMetric.HEART_RATE, interval)
                        hrConfig = settings.config(MonitorMetric.HEART_RATE)
                    },
                    valueFormat = { it.toInt().toString() }
                )
            }
        }

        // HRV Day Detail Panel
        item {
            val settings = remember { MonitoringSettings(app) }
            var hrvConfig by remember { mutableStateOf(settings.config(MonitorMetric.HRV)) }
            val hrvPoints = remember(dayHrvSamples) { dayHrvSamples.map { it.toMetricPoint() } }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hrv_detail_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                MetricDetailPanel(
                    title = "HRV",
                    unit = "ms",
                    points = hrvPoints,
                    modifier = Modifier.padding(16.dp),
                    monitoringLabel = "Regular HRV",
                    monitoringEnabled = hrvConfig.enabled,
                    intervalMinutes = hrvConfig.intervalMin,
                    onMonitoringEnabledChange = { enabled ->
                        settings.setEnabled(MonitorMetric.HRV, enabled)
                        hrvConfig = settings.config(MonitorMetric.HRV)
                    },
                    onIntervalChange = { interval ->
                        settings.setIntervalMin(MonitorMetric.HRV, interval)
                        hrvConfig = settings.config(MonitorMetric.HRV)
                    },
                    valueFormat = { it.toInt().toString() }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
