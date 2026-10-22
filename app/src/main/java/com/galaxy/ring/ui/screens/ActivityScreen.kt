package com.galaxy.ring.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.WorkoutSession
import com.galaxy.ring.ui.components.EmptyState
import com.galaxy.ring.ui.components.MetricCard
import com.galaxy.ring.ui.components.SectionHeader
import com.galaxy.ring.ui.theme.ActivityGreen
import com.galaxy.ring.ui.theme.AmberGlow
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.RosePulse
import com.galaxy.ring.ui.theme.ShapeMedium
import com.galaxy.ring.ui.theme.ShapeSmall
import com.galaxy.ring.ui.theme.Spacing
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
    val friendlyDateStr = remember(selectedCalendar.timeInMillis) {
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(selectedCalendar.time)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { Spacer(modifier = Modifier.height(Spacing.xs)) }

        // Date Selector Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val prev = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                            selectedCalendar = prev
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Day",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                        },
                        modifier = Modifier.size(48.dp)
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

        // Daily Steps Summary: MetricCard
        item {
            val stepsValue = if (daySteps != null) {
                NumberFormat.getIntegerInstance().format(daySteps!!.steps)
            } else {
                "—"
            }
            val distanceKm = (daySteps?.distanceMeters ?: 0.0) / 1000.0
            val subtitleText = if (daySteps != null) {
                buildString {
                    if (distanceKm > 0.05) append(String.format(Locale.US, "%.2f km", distanceKm))
                    if (daySteps!!.caloriesKcal > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${daySteps!!.caloriesKcal} kcal")
                    }
                }.ifEmpty { "Daily total recorded by ring" }
            } else {
                "No steps data recorded for this day"
            }

            MetricCard(
                title = "Steps",
                value = stepsValue,
                unit = if (daySteps != null) "steps" else "",
                subtitle = subtitleText,
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                accentColor = ActivityGreen,
                testTag = "activity_steps_card"
            )
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
                    onClick = { onWorkoutClick(workout) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(Spacing.lg)) }
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
    EmptyState(
        message = "No workouts on the ring for this day. Start a sport mode on the ring, then Sync.",
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        actionLabel = if (isSyncing) "Syncing..." else "Sync Now",
        onAction = onSyncClick,
        testTag = "workout_empty_state",
        modifier = modifier
    )
}

/**
 * Displays a real WorkoutSession with type, time range, distance, steps, and telemetry metrics.
 * Styled with Material 3 theme tokens and >= 48dp touch target accessibility.
 */
@Composable
fun WorkoutSessionCard(
    session: WorkoutSession,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .testTag("workout_session_card")
        .then(
            if (onClick != null) {
                Modifier
                    .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                    .clip(ShapeMedium)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick
                    )
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
        "run", "jog" -> Icons.AutoMirrored.Filled.DirectionsRun to CyberCyan
        "walk" -> Icons.AutoMirrored.Filled.DirectionsWalk to ActivityGreen
        "hike" -> Icons.Default.Hiking to AmberGlow
        else -> Icons.Default.FitnessCenter to ElectricViolet
    }

    Card(
        modifier = cardModifier,
        shape = ShapeMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            // Header: Sport Type + Time Range + Duration Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                    shape = ShapeSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = durationStr,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tintColor,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

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
