package com.galaxy.ring.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.MetricPoint
import com.galaxy.ring.data.MetricStats
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.data.PacePhase
import com.galaxy.ring.data.SleepRecordParser
import com.galaxy.ring.data.WorkoutFormatter
import com.galaxy.ring.data.WorkoutSession
import com.galaxy.ring.ui.theme.ActivityGreen
import com.galaxy.ring.ui.theme.AmberGlow
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.DarkCardBorder
import com.galaxy.ring.ui.theme.DarkSurface
import com.galaxy.ring.ui.theme.DarkSurfaceVariant
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.HrvTeal
import com.galaxy.ring.ui.theme.RosePulse
import com.galaxy.ring.ui.theme.SectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity Detail Screen (F6):
 *  - Top bar with back to day list navigation
 *  - Summary header & metric cards (duration, distance, steps, calories, avg/min/max HR, pace, speed)
 *  - Phase list (pace/steps/distance per split)
 *  - Heart rate chart during workout session
 *  - SpO2-in-window chart during workout window
 *  - km/mi distance unit switch synced with MonitoringSettings
 */
@Composable
fun WorkoutDetailScreen(
    session: WorkoutSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings = remember { MonitoringSettings(context) }
    var useMiles by remember { mutableStateOf(settings.useMiles) }

    val app = GalaxyRingApp.instance
    val repo = app.healthRepository

    // Query room for HR and SpO2 in the workout window as supplement/fallback
    val windowHrEntities by repo.getHeartRateBetween(session.startTime, session.endTime)
        .collectAsState(initial = emptyList())
    // 10-minute buffer around workout window for SpO2 readings
    val windowSpo2Entities by repo.getSpo2Between(
        session.startTime - 10 * 60 * 1000L,
        session.endTime + 10 * 60 * 1000L
    ).collectAsState(initial = emptyList())

    // Prepare HR points: Prefer session.hrPoints if present, else window entities
    val hrPoints = remember(session.hrPoints, windowHrEntities) {
        if (session.hrPoints.isNotEmpty()) {
            session.hrPoints.map { MetricPoint(it.timestamp, it.bpm.toFloat()) }.sortedBy { it.timestamp }
        } else {
            windowHrEntities.map { MetricPoint(it.timestamp, it.bpm.toFloat()) }.sortedBy { it.timestamp }
        }
    }

    // Prepare SpO2 points
    val spo2Points = remember(windowSpo2Entities) {
        windowSpo2Entities
            .filter { it.percentage in 70f..100f }
            .map { MetricPoint(it.timestamp, it.percentage) }
            .sortedBy { it.timestamp }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US) }
    val dateStr = remember(session.startTime) {
        if (session.startTime > 0) dateFormat.format(Date(session.startTime)) else session.date
    }
    val timeRangeStr = remember(session.startTime, session.endTime) {
        val s = timeFormat.format(Date(session.startTime))
        val e = timeFormat.format(Date(session.endTime))
        "$s – $e"
    }

    val (sportIcon, tintColor) = when (session.sportTypeName.lowercase()) {
        "run", "jog" -> Icons.Default.DirectionsRun to CyberCyan
        "walk" -> Icons.Default.DirectionsWalk to ActivityGreen
        "hike" -> Icons.Default.Hiking to AmberGlow
        else -> Icons.Default.FitnessCenter to ElectricViolet
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("workout_detail_screen")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Top Navigation Bar: Back to Day List & Unit Toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("workout_detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to day list",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "${session.sportTypeName} Workout",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Unit Chip (km / mi toggle from settings)
                    FilterChip(
                        selected = useMiles,
                        onClick = {
                            val next = !useMiles
                            useMiles = next
                            settings.useMiles = next
                        },
                        label = {
                            Text(
                                text = if (useMiles) "Unit: mi" else "Unit: km",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("workout_detail_unit_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }
            }
        }

        // Summary Card
        item {
            WorkoutSummaryCard(
                session = session,
                timeRangeStr = timeRangeStr,
                useMiles = useMiles,
                sportIcon = sportIcon,
                tintColor = tintColor
            )
        }

        // Phase List Section (pace / steps / distance)
        item {
            SectionHeader(
                title = "Split Phases",
                subtitle = if (session.pacePhases.isNotEmpty()) "${session.pacePhases.size} phase(s) recorded" else null
            )
        }

        item {
            WorkoutPhaseListCard(
                phases = session.pacePhases,
                useMiles = useMiles
            )
        }

        // Heart Rate Chart During Workout
        item {
            SectionHeader(
                title = "Heart Rate During Workout",
                subtitle = if (hrPoints.isNotEmpty()) "${hrPoints.size} readings" else null
            )
        }

        item {
            WorkoutHrChartCard(
                points = hrPoints,
                session = session
            )
        }

        // SpO2-in-Window Chart During Workout Window
        item {
            SectionHeader(
                title = "Blood Oxygen (SpO2) During Window",
                subtitle = if (spo2Points.isNotEmpty()) "${spo2Points.size} readings in window" else null
            )
        }

        item {
            WorkoutSpo2ChartCard(
                points = spo2Points,
                session = session
            )
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * Detailed Workout Summary Card
 */
@Composable
fun WorkoutSummaryCard(
    session: WorkoutSession,
    timeRangeStr: String,
    useMiles: Boolean,
    sportIcon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout_summary_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Icon + Sport Type + Time Range + Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = tintColor.copy(alpha = 0.18f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = sportIcon,
                                contentDescription = session.sportTypeName,
                                tint = tintColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = session.sportTypeName,
                            style = MaterialTheme.typography.titleLarge,
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
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant
                ) {
                    Text(
                        text = WorkoutFormatter.formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = tintColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Metrics 2x2 Grid: Distance, Steps, Calories, Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetricItem(
                    label = "DISTANCE",
                    value = WorkoutFormatter.formatDistance(session.totalDistanceMeters, useMiles),
                    icon = Icons.Default.Straighten,
                    tint = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricItem(
                    label = "TOTAL STEPS",
                    value = WorkoutFormatter.formatSteps(session.totalSteps),
                    icon = Icons.Default.DirectionsWalk,
                    tint = ActivityGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetricItem(
                    label = "CALORIES",
                    value = if (session.totalCaloriesKcal > 0) "${session.totalCaloriesKcal} kcal" else "—",
                    icon = Icons.Default.LocalFireDepartment,
                    tint = AmberGlow,
                    modifier = Modifier.weight(1f)
                )
                val paceSec = session.avgPaceSecondsPerKm ?: 0.0
                SummaryMetricItem(
                    label = "AVG PACE",
                    value = WorkoutFormatter.formatPace(paceSec, useMiles),
                    icon = Icons.Default.Timer,
                    tint = ElectricViolet,
                    modifier = Modifier.weight(1f)
                )
            }

            // Speed & Cadence (if present)
            if ((session.avgSpeedKmH != null && session.avgSpeedKmH > 0) ||
                (session.avgCadenceSpm != null && session.avgCadenceSpm > 0)
            ) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (session.avgSpeedKmH != null && session.avgSpeedKmH > 0) {
                        SummaryMetricItem(
                            label = "AVG SPEED",
                            value = WorkoutFormatter.formatSpeed(session.avgSpeedKmH, useMiles),
                            icon = Icons.Default.Speed,
                            tint = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (session.avgCadenceSpm != null && session.avgCadenceSpm > 0) {
                        SummaryMetricItem(
                            label = "AVG CADENCE",
                            value = "${session.avgCadenceSpm} spm",
                            icon = Icons.Default.DirectionsRun,
                            tint = HrvTeal,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Heart Rate Summary Row
            if (session.avgHr != null && session.avgHr > 0) {
                Spacer(modifier = Modifier.height(18.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = RosePulse,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Heart Rate Stats",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (session.minHr != null && session.minHr > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("MIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${session.minHr}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AVG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${session.avgHr}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RosePulse)
                            }
                            if (session.maxHr != null && session.maxHr > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("MAX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${session.maxHr}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Phase List Card showing split phases (pace, steps, distance)
 */
@Composable
fun WorkoutPhaseListCard(
    phases: List<PacePhase>,
    useMiles: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout_phase_list_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (phases.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No split phases recorded for this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PHASE / SPLIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "PACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "DIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "STEPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }

                phases.forEachIndexed { idx, phase ->
                    val isEven = idx % 2 == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isEven) DarkSurfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Phase Label
                        Text(
                            text = if (phase.phaseIndex > 0) "Split ${phase.phaseIndex}" else "Split ${idx + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.2f)
                        )

                        // Pace
                        Text(
                            text = WorkoutFormatter.formatPace(phase.paceSeconds.toDouble(), useMiles),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )

                        // Distance (null = not reported by ring; show placeholder, never a default)
                        Text(
                            text = phase.distanceMeters?.let { WorkoutFormatter.formatDistance(it, useMiles) } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        // Steps (null = not reported by ring; show placeholder, never 0)
                        Text(
                            text = phase.steps?.toString() ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

/**
 * HR Chart Card during workout session
 */
@Composable
fun WorkoutHrChartCard(
    points: List<MetricPoint>,
    session: WorkoutSession,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout_hr_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (points.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RosePulse.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No heart rate readings recorded during this workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val stats = MetricStats.compute(points)
            val startTime = session.startTime.takeIf { it > 0 } ?: points.first().timestamp
            val endTime = session.endTime.takeIf { it > startTime } ?: points.last().timestamp
            val timeSpan = (endTime - startTime).coerceAtLeast(60_000L)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Min / Avg / Max Row
                if (stats != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ChartStatCell("MIN HR", "${stats.min.toInt()} bpm", RosePulse.copy(alpha = 0.8f))
                        ChartStatCell("AVG HR", "${stats.avg.toInt()} bpm", RosePulse)
                        ChartStatCell("MAX HR", "${stats.max.toInt()} bpm", RosePulse)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Canvas line chart
                val lineColor = RosePulse
                val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                val minVal = (stats?.min ?: 60f).coerceAtLeast(40f) - 5f
                val maxVal = (stats?.max ?: 160f) + 5f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    for (r in 0..3) {
                        val y = size.height * r / 3f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }

                    fun xOf(ts: Long) =
                        (((ts - startTime).toFloat() / timeSpan).coerceIn(0f, 1f)) * size.width

                    fun yOf(v: Float) =
                        size.height - (((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)) * size.height

                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val x = xOf(p.timestamp)
                        val y = yOf(p.value)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3f))

                    for (p in points) {
                        drawCircle(lineColor, radius = 4f, center = Offset(xOf(p.timestamp), yOf(p.value)))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time axis labels
                val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(timeFmt.format(Date(startTime)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeFmt.format(Date(startTime + timeSpan / 2)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeFmt.format(Date(endTime)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Recent / Peak detailed list preview
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Samples Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                points.takeLast(4).reversed().forEach { pt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = SleepRecordParser.formatClock(pt.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${pt.value.toInt()} bpm",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = RosePulse
                        )
                    }
                }
            }
        }
    }
}

/**
 * SpO2 Chart Card during workout window
 */
@Composable
fun WorkoutSpo2ChartCard(
    points: List<MetricPoint>,
    session: WorkoutSession,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout_spo2_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (points.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Air,
                    contentDescription = null,
                    tint = CyberCyan.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No blood oxygen readings recorded during this workout window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val stats = MetricStats.compute(points)
            val startTime = points.first().timestamp.coerceAtMost(session.startTime)
            val endTime = points.last().timestamp.coerceAtLeast(session.endTime)
            val timeSpan = (endTime - startTime).coerceAtLeast(60_000L)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (stats != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ChartStatCell("MIN SpO2", "${stats.min.toInt()}%", CyberCyan)
                        ChartStatCell("AVG SpO2", "${stats.avg.toInt()}%", CyberCyan)
                        ChartStatCell("MAX SpO2", "${stats.max.toInt()}%", CyberCyan)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Canvas line chart
                val lineColor = CyberCyan
                val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                val minVal = 90f
                val maxVal = 100f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    for (r in 0..2) {
                        val y = size.height * r / 2f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }

                    fun xOf(ts: Long) =
                        (((ts - startTime).toFloat() / timeSpan).coerceIn(0f, 1f)) * size.width

                    fun yOf(v: Float) =
                        size.height - (((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)) * size.height

                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val x = xOf(p.timestamp)
                        val y = yOf(p.value)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3f))

                    for (p in points) {
                        drawCircle(lineColor, radius = 4f, center = Offset(xOf(p.timestamp), yOf(p.value)))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time axis labels
                val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(timeFmt.format(Date(startTime)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeFmt.format(Date(startTime + timeSpan / 2)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeFmt.format(Date(endTime)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Readings list
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Window Readings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                points.takeLast(4).reversed().forEach { pt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = SleepRecordParser.formatClock(pt.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${pt.value.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = CyberCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartStatCell(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
    }
}
