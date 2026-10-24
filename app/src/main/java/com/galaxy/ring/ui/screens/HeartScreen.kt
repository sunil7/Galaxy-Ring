package com.galaxy.ring.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.HeartRateEntity
import com.galaxy.ring.data.HrvEntity
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.data.toMetricPoint
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.DarkCardBorder
import com.galaxy.ring.ui.theme.DarkSurface
import com.galaxy.ring.ui.theme.RosePulse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

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

        // Simple Heart Summary Card
        item {
            HeartSummaryCard(
                hrSamples = dayHrSamples,
                hrvSamples = dayHrvSamples
            )
        }

        // Heart Rate Day Detail Panel
        item {
            val settings = remember { MonitoringSettings(app) }
            var hrConfig by remember { mutableStateOf(settings.config(MonitorMetric.HEART_RATE)) }
            val hrPoints = remember(dayHrSamples) { dayHrSamples.map { it.toMetricPoint() } }

            Card(
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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

/**
 * Simple Heart Summary card showing cardiovascular (HR) and autonomic (HRV) telemetry for the day.
 */
@Composable
fun HeartSummaryCard(
    hrSamples: List<HeartRateEntity>,
    hrvSamples: List<HrvEntity>,
    modifier: Modifier = Modifier
) {
    val hasHr = hrSamples.isNotEmpty()
    val avgHr = if (hasHr) hrSamples.map { it.bpm }.average().roundToInt() else null
    val minHr = if (hasHr) hrSamples.minOf { it.bpm } else null
    val maxHr = if (hasHr) hrSamples.maxOf { it.bpm } else null

    val hasHrv = hrvSamples.isNotEmpty()
    val avgHrv = if (hasHrv) hrvSamples.map { it.hrvMs }.average().roundToInt() else null
    val minHrv = if (hasHrv) hrvSamples.minOf { it.hrvMs } else null
    val maxHrv = if (hasHrv) hrvSamples.maxOf { it.hrvMs } else null

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkCardBorder.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = RosePulse.copy(alpha = 0.15f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = RosePulse,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "HEART SUMMARY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Heart Rate Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HEART RATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = avgHr?.let { "$it" } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasHr) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        if (hasHr) {
                            Text(
                                text = "BPM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasHr) "Min $minHr • Max $maxHr" else "No readings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // HRV Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HEART RATE VARIABILITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = avgHrv?.let { "$it" } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasHrv) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        if (hasHrv) {
                            Text(
                                text = "ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasHrv) "Min $minHrv • Max $maxHrv" else "No readings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
