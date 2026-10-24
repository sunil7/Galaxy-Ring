package com.galaxy.ring.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.data.WorkoutSession
import com.galaxy.ring.data.toMetricPoint
import com.galaxy.ring.ui.theme.ActivityGreen
import com.galaxy.ring.ui.theme.AmberGlow
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.DarkCardBorder
import com.galaxy.ring.ui.theme.DarkSurface
import com.galaxy.ring.ui.theme.DarkSurfaceVariant
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.RosePulse
import com.galaxy.ring.ui.theme.SectionHeader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    onSyncFromRing: () -> Unit = {},
    isSyncing: Boolean = false,
    syncProgressText: String? = null,
    onWorkoutClick: (WorkoutSession) -> Unit = {}
) {
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val selectedDateStr = remember(selectedCalendar.timeInMillis) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCalendar.time)
    }

    val daySteps by repo.getDailySteps(selectedDateStr).collectAsState(initial = null)
    val dayWorkouts by repo.getWorkoutsForDay(selectedDateStr).collectAsState(initial = emptyList())
    val daySpo2Samples by repo.getSpo2ForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val dayStressSamples by repo.getStressForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val friendlyDateStr = remember(selectedCalendar.timeInMillis) {
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(selectedCalendar.time)
    }

    var selectedWorkout by remember { mutableStateOf<WorkoutSession?>(null) }

    if (selectedWorkout != null) {
        WorkoutDetailScreen(
            session = selectedWorkout!!,
            onBack = { selectedWorkout = null }
        )
        return
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

        // Daily Steps Summary Card (kept separate from activity workouts)
        item {
            HistoryStepsCard(steps = daySteps)
        }

        // Workouts Header
        item {
            SectionHeader(
                title = "Workouts",
                subtitle = if (dayWorkouts.isNotEmpty()) "${dayWorkouts.size} session(s) recorded" else null
            )
        }

        // Workouts list or empty state
        if (dayWorkouts.isEmpty()) {
            item {
                WorkoutEmptyState(
                    onSyncClick = onSyncFromRing,
                    isSyncing = isSyncing
                )
            }
        } else {
            items(dayWorkouts.size) { idx ->
                val workout = dayWorkouts[idx]
                WorkoutSessionCard(
                    session = workout,
                    onClick = {
                        selectedWorkout = workout
                        onWorkoutClick(workout)
                    }
                )
            }
        }

        // Blood Oxygen (SpO2) Day Detail Panel
        item {
            val settings = remember { MonitoringSettings(app) }
            var spo2Config by remember { mutableStateOf(settings.config(MonitorMetric.SPO2)) }
            val spo2Points = remember(daySpo2Samples) { daySpo2Samples.map { it.toMetricPoint() } }

            Card(
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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

/**
 * Empty state card for workouts matching the protocol requirement:
 * "No workouts on the ring for this day. Start a sport mode on the ring, then Sync."
 */
@Composable
fun WorkoutEmptyState(
    onSyncClick: () -> Unit = {},
    isSyncing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, DarkCardBorder.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No workouts on the ring for this day. Start a sport mode on the ring, then Sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF021B2D)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (isSyncing) "Syncing..." else "Sync Now",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Displays a real WorkoutSession with type, time range, distance, steps, and telemetry metrics.
 */
@Composable
fun WorkoutSessionCard(
    session: WorkoutSession,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val timeRangeStr = remember(session.startTime, session.endTime) {
        val startStr = timeFormat.format(Date(session.startTime))
        val endStr = timeFormat.format(Date(session.endTime))
        "$startStr – $endStr"
    }

    val durationMin = session.durationSeconds / 60
    val durationRemSec = session.durationSeconds % 60
    val durationStr = if (durationMin > 0) "${durationMin}m ${durationRemSec}s" else "${durationRemSec}s"

    val distanceKm = session.totalDistanceMeters / 1000.0
    val distanceStr = if (distanceKm > 0.05) String.format(Locale.US, "%.2f km", distanceKm) else null
    val stepsStr = if (session.totalSteps > 0) NumberFormat.getIntegerInstance().format(session.totalSteps) + " steps" else null

    val (icon, tintColor) = when (session.sportTypeName.lowercase()) {
        "run", "jog" -> Icons.Default.DirectionsRun to CyberCyan
        "walk" -> Icons.Default.DirectionsWalk to ActivityGreen
        "hike" -> Icons.Default.Hiking to AmberGlow
        else -> Icons.Default.FitnessCenter to ElectricViolet
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkCardBorder.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Sport Type + Time Range + Duration Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = tintColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = session.sportTypeName,
                                tint = tintColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = session.sportTypeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = timeRangeStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = durationStr,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tintColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics row: Distance, Steps, Calories, Avg HR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance
                Column {
                    Text(
                        text = "DISTANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = distanceStr ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Steps
                Column {
                    Text(
                        text = "STEPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stepsStr ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Calories
                if (session.totalCaloriesKcal > 0) {
                    Column {
                        Text(
                            text = "CALORIES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${session.totalCaloriesKcal} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Avg HR
                if (session.avgHr != null && session.avgHr > 0) {
                    Column {
                        Text(
                            text = "AVG HR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${session.avgHr} bpm",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RosePulse
                        )
                    }
                }
            }
        }
    }
}
