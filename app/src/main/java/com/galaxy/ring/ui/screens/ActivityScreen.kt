package com.galaxy.ring.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ActivityScreen(
    onSyncFromRing: () -> Unit = {},
    isSyncing: Boolean = false,
    syncProgressText: String? = null
) {
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val selectedDateStr = remember(selectedCalendar.timeInMillis) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCalendar.time)
    }

    val daySteps by repo.getDailySteps(selectedDateStr).collectAsState(initial = null)
    val daySpo2Samples by repo.getSpo2ForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val dayStressSamples by repo.getStressForDay(selectedCalendar.time).collectAsState(initial = emptyList())

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

        // Daily Steps Activity Card
        item {
            HistoryStepsCard(steps = daySteps)
        }

        // SpO2 Day Detail Panel
        item {
            val settings = remember { MonitoringSettings(app) }
            var spo2Config by remember { mutableStateOf(settings.config(MonitorMetric.SPO2)) }
            val spo2Points = remember(daySpo2Samples) { daySpo2Samples.map { it.toMetricPoint() } }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spo2_detail_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                MetricDetailPanel(
                    title = "Blood Oxygen",
                    unit = "%",
                    points = spo2Points,
                    modifier = Modifier.padding(16.dp),
                    monitoringLabel = "Regular Blood oxygen",
                    monitoringEnabled = spo2Config.enabled,
                    intervalMinutes = spo2Config.intervalMin,
                    onMonitoringEnabledChange = { enabled ->
                        settings.setEnabled(MonitorMetric.SPO2, enabled)
                        spo2Config = settings.config(MonitorMetric.SPO2)
                    },
                    onIntervalChange = { interval ->
                        settings.setIntervalMin(MonitorMetric.SPO2, interval)
                        spo2Config = settings.config(MonitorMetric.SPO2)
                    },
                    valueFormat = { it.toInt().toString() }
                )
            }
        }

        // Stress Day Detail Panel
        item {
            val settings = remember { MonitoringSettings(app) }
            var stressConfig by remember { mutableStateOf(settings.config(MonitorMetric.STRESS)) }
            val stressPoints = remember(dayStressSamples) { dayStressSamples.map { it.toMetricPoint() } }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stress_detail_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                MetricDetailPanel(
                    title = "Stress",
                    unit = "",
                    points = stressPoints,
                    modifier = Modifier.padding(16.dp),
                    monitoringLabel = "Scheduled Stress",
                    monitoringEnabled = stressConfig.enabled,
                    intervalMinutes = stressConfig.intervalMin,
                    onMonitoringEnabledChange = { enabled ->
                        settings.setEnabled(MonitorMetric.STRESS, enabled)
                        stressConfig = settings.config(MonitorMetric.STRESS)
                    },
                    onIntervalChange = { interval ->
                        settings.setIntervalMin(MonitorMetric.STRESS, interval)
                        stressConfig = settings.config(MonitorMetric.STRESS)
                    },
                    valueFormat = { it.toInt().toString() }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
