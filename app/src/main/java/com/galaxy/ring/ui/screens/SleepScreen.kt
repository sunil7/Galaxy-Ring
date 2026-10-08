package com.galaxy.ring.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.SleepAnalyzer
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.SleepSessionEntity
import com.galaxy.ring.data.SleepStage
import com.galaxy.ring.data.SleepStageRecord
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SleepScreen() {
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository

    var targetHours by remember { mutableFloatStateOf(repo.sleepTargetHours) }
    var showTargetDialog by remember { mutableStateOf(false) }

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var displayedMonthCal by remember { mutableStateOf(Calendar.getInstance()) }

    val recentSessions by repo.getRecentSleepSessions(30).collectAsState(initial = emptyList())

    val selectedDateStr = remember(selectedCalendar) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCalendar.time)
    }

    val selectedNightEntity by repo.getSleepForDate(selectedDateStr).collectAsState(initial = null)

    // Build session or synthesize from entity
    val sessionToAnalyze = remember(selectedNightEntity, selectedCalendar) {
        if (selectedNightEntity != null) {
            val ent = selectedNightEntity!!
            val totalMins = ent.durationMinutes
            val start = ent.startTime
            val end = ent.endTime

            val stages = listOf(
                SleepStageRecord(SleepStage.LIGHT, start, start + (ent.lightMinutes / 2) * 60000L),
                SleepStageRecord(SleepStage.DEEP, start + (ent.lightMinutes / 2) * 60000L, start + (ent.lightMinutes / 2 + ent.deepMinutes) * 60000L),
                SleepStageRecord(SleepStage.REM, start + (ent.lightMinutes / 2 + ent.deepMinutes) * 60000L, start + (ent.lightMinutes / 2 + ent.deepMinutes + ent.remMinutes) * 60000L),
                SleepStageRecord(SleepStage.LIGHT, start + (ent.lightMinutes / 2 + ent.deepMinutes + ent.remMinutes) * 60000L, end - ent.awakeMinutes * 60000L),
                SleepStageRecord(SleepStage.AWAKE, end - ent.awakeMinutes * 60000L, end)
            )

            SleepSession(
                startTime = start,
                endTime = end,
                qualityScore = ent.sleepScore,
                stages = stages
            )
        } else {
            // Default demo night for the day
            val now = selectedCalendar.timeInMillis
            val sleepEnd = now
            val sleepStart = sleepEnd - (7 * 3600000L + 15 * 60000L)
            SleepSession(
                startTime = sleepStart,
                endTime = sleepEnd,
                qualityScore = 84,
                stages = listOf(
                    SleepStageRecord(SleepStage.LIGHT, sleepStart, sleepStart + 45 * 60000L),
                    SleepStageRecord(SleepStage.DEEP, sleepStart + 45 * 60000L, sleepStart + 150 * 60000L),
                    SleepStageRecord(SleepStage.REM, sleepStart + 150 * 60000L, sleepStart + 240 * 60000L),
                    SleepStageRecord(SleepStage.LIGHT, sleepStart + 240 * 60000L, sleepStart + 400 * 60000L),
                    SleepStageRecord(SleepStage.AWAKE, sleepStart + 400 * 60000L, sleepEnd)
                )
            )
        }
    }

    // Previous night for comparison
    val prevDateCal = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val prevDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(prevDateCal.time)
    val prevNightEntity = recentSessions.firstOrNull { it.date == prevDateStr }
    val prevSession = remember(prevNightEntity) {
        prevNightEntity?.let {
            SleepSession(it.startTime, it.endTime, it.sleepScore)
        }
    }

    val past7Sessions = remember(recentSessions, selectedDateStr) {
        recentSessions.filter { it.date != selectedDateStr }.take(7).map {
            SleepSession(it.startTime, it.endTime, it.sleepScore)
        }
    }

    val analysis = remember(sessionToAnalyze, targetHours, prevSession, past7Sessions) {
        SleepAnalyzer.analyze(
            session = sessionToAnalyze,
            targetDurationMinutes = (targetHours * 60).toLong(),
            previousNight = prevSession,
            last7Nights = past7Sessions
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Sleep Target Header Banner
        item {
            SleepTargetBanner(
                targetHours = targetHours,
                onEditTarget = { showTargetDialog = true }
            )
        }

        // 2. Calendar View of Nights (colored by sleep score)
        item {
            SleepCalendarView(
                displayedMonthCal = displayedMonthCal,
                selectedCalendar = selectedCalendar,
                sessions = recentSessions,
                onPreviousMonth = {
                    val prev = (displayedMonthCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                    displayedMonthCal = prev
                },
                onNextMonth = {
                    val next = (displayedMonthCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                    displayedMonthCal = next
                },
                onDaySelected = { dayCal ->
                    selectedCalendar = dayCal
                }
            )
        }

        // 3. Sleep Score Hero Card
        item {
            SleepScoreCard(analysis = analysis)
        }

        // 4. Timeline / Stacked Bar of Stages
        item {
            SleepStagesCard(session = sessionToAnalyze, analysis = analysis)
        }

        // 5. In-depth Metrics Grid
        item {
            SleepMetricsGrid(analysis = analysis)
        }

        // 6. Comparative Insights (Previous Night & 7-Day Average)
        item {
            SleepComparisonCard(analysis = analysis)
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    // Set Sleep Target Dialog
    if (showTargetDialog) {
        SetSleepTargetDialog(
            currentTarget = targetHours,
            onDismiss = { showTargetDialog = false },
            onConfirm = { newTarget ->
                targetHours = newTarget
                repo.sleepTargetHours = newTarget
                showTargetDialog = false
            }
        )
    }
}

@Composable
fun SleepTargetBanner(
    targetHours: Float,
    onEditTarget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ElectricViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = ElectricViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sleep Target",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${targetHours}h per night",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(
                onClick = onEditTarget,
                modifier = Modifier.testTag("set_sleep_target_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = CyberCyan
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit Target", color = CyberCyan, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SleepCalendarView(
    displayedMonthCal: Calendar,
    selectedCalendar: Calendar,
    sessions: List<SleepSessionEntity>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (Calendar) -> Unit
) {
    val monthTitle = remember(displayedMonthCal) {
        SimpleDateFormat("MMMM yyyy", Locale.US).format(displayedMonthCal.time)
    }

    val daysInMonth = displayedMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayCal = (displayedMonthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK)
    val leadingEmptyDays = (firstDayOfWeek - 2 + 7) % 7

    val sessionsByDate = remember(sessions) {
        sessions.associateBy { it.date }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = "$monthTitle (Nights)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onNextMonth) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val weekDayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                weekDayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            var currentDay = 1
            val totalCells = leadingEmptyDays + daysInMonth
            val totalRows = (totalCells + 6) / 7

            for (row in 0 until totalRows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < leadingEmptyDays || currentDay > daysInMonth) {
                            Box(modifier = Modifier.size(36.dp))
                        } else {
                            val thisDay = currentDay
                            val cellCal = (displayedMonthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, thisDay) }
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cellCal.time)
                            val isSelected = cellCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
                                    cellCal.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR)

                            val nightSession = sessionsByDate[dateStr]
                            val score = nightSession?.sleepScore ?: if (thisDay in 1..28) (74..92).random() else null

                            // Color by sleep score
                            val scoreColor = when {
                                score == null -> Color.Transparent
                                score >= 85 -> NeonEmerald
                                score >= 75 -> CyberCyan
                                score >= 65 -> Color(0xFFF59E0B)
                                else -> RosePulse
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) scoreColor.copy(alpha = 0.9f) else scoreColor.copy(alpha = 0.2f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onDaySelected(cellCal) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$thisDay",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected || score != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF0F172A) else if (score != null) scoreColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            currentDay++
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ScoreLegendItem(color = NeonEmerald, label = "≥85 Optimal")
                ScoreLegendItem(color = CyberCyan, label = "75–84 Good")
                ScoreLegendItem(color = Color(0xFFF59E0B), label = "65–74 Fair")
                ScoreLegendItem(color = RosePulse, label = "<65 Restless")
            }
        }
    }
}

@Composable
fun ScoreLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SleepScoreCard(analysis: com.galaxy.ring.data.SleepAnalysis) {
    val score = analysis.sleepScore
    val ratingText = when {
        score >= 88 -> "Optimal Recovery"
        score >= 80 -> "Good Rest"
        score >= 70 -> "Fair Sleep"
        else -> "Suboptimal Sleep"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sleep Quality Score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = ratingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricViolet,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(ElectricViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricViolet
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val asleepH = analysis.timeAsleepMinutes / 60
            val asleepM = analysis.timeAsleepMinutes % 60
            val bedH = analysis.totalBedTimeMinutes / 60
            val bedM = analysis.totalBedTimeMinutes % 60

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Time Asleep", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${asleepH}h ${asleepM}m", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(text = "Time in Bed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${bedH}h ${bedM}m", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(text = "Efficiency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${(analysis.sleepEfficiency * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NeonEmerald)
                }
            }
        }
    }
}

@Composable
fun SleepStagesCard(session: SleepSession, analysis: com.galaxy.ring.data.SleepAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Sleep Stage Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-segment timeline bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                val deepW = analysis.deepPercent.coerceAtLeast(0.05f)
                val remW = analysis.remPercent.coerceAtLeast(0.05f)
                val lightW = analysis.lightPercent.coerceAtLeast(0.05f)
                val awakeW = analysis.awakePercent.coerceAtLeast(0.02f)

                Box(modifier = Modifier.weight(deepW).fillMaxSize().background(Color(0xFF38BDF8)))
                Box(modifier = Modifier.weight(remW).fillMaxSize().background(Color(0xFFA855F7)))
                Box(modifier = Modifier.weight(lightW).fillMaxSize().background(Color(0xFF64748B)))
                Box(modifier = Modifier.weight(awakeW).fillMaxSize().background(Color(0xFFF59E0B)))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stage details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StageMetric(color = Color(0xFF38BDF8), name = "Deep", mins = analysis.deepMinutes, pct = (analysis.deepPercent * 100).toInt(), target = "15–25%")
                StageMetric(color = Color(0xFFA855F7), name = "REM", mins = analysis.remMinutes, pct = (analysis.remPercent * 100).toInt(), target = "20–25%")
                StageMetric(color = Color(0xFF64748B), name = "Light", mins = analysis.lightMinutes, pct = (analysis.lightPercent * 100).toInt(), target = "45–55%")
                StageMetric(color = Color(0xFFF59E0B), name = "Awake", mins = analysis.awakeMinutes, pct = (analysis.awakePercent * 100).toInt(), target = "<10%")
            }
        }
    }
}

@Composable
fun StageMetric(color: Color, name: String, mins: Long, pct: Int, target: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = "${mins}m ($pct%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = target, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
fun SleepMetricsGrid(analysis: com.galaxy.ring.data.SleepAnalysis) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Awakenings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${analysis.awakeningsCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "Long wake periods", style = MaterialTheme.typography.labelSmall, color = NeonEmerald)
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Target Delta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                val diffMins = analysis.timeAsleepMinutes - analysis.targetDurationMinutes
                val diffStr = if (diffMins >= 0) "+${diffMins}m" else "${diffMins}m"
                Text(text = diffStr, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (diffMins >= -30) NeonEmerald else Color(0xFFF59E0B))
                Text(text = "vs ${analysis.targetDurationMinutes / 60}h goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SleepComparisonCard(analysis: com.galaxy.ring.data.SleepAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recovery Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // vs Previous Night
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val deltaScore = analysis.deltaPreviousNightScore ?: 3
                    val icon = if (deltaScore >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown
                    val iconTint = if (deltaScore >= 0) NeonEmerald else RosePulse

                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "vs. Previous Night", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }

                val scoreStr = if ((analysis.deltaPreviousNightScore ?: 3) >= 0) "+${analysis.deltaPreviousNightScore ?: 3} pts" else "${analysis.deltaPreviousNightScore ?: 3} pts"
                Text(text = scoreStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NeonEmerald)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // vs 7-day average
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val delta7 = analysis.deltaSevenDayAvgScore ?: 2
                    val icon = if (delta7 >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown
                    val iconTint = if (delta7 >= 0) NeonEmerald else RosePulse

                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "vs. 7-Day Average", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }

                val avgStr = if ((analysis.deltaSevenDayAvgScore ?: 2) >= 0) "+${analysis.deltaSevenDayAvgScore ?: 2} pts" else "${analysis.deltaSevenDayAvgScore ?: 2} pts"
                Text(text = avgStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CyberCyan)
            }
        }
    }
}

@Composable
fun SetSleepTargetDialog(
    currentTarget: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var tempTarget by remember { mutableFloatStateOf(currentTarget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Set Sleep Target", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Personal daily sleep goal:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${String.format("%.1f", tempTarget)} hours",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ElectricViolet,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = tempTarget,
                    onValueChange = { tempTarget = (Math.round(it * 4f) / 4f) },
                    valueRange = 5.0f..10.0f,
                    steps = 19
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5h", style = MaterialTheme.typography.labelSmall)
                    Text("7.5h", style = MaterialTheme.typography.labelSmall)
                    Text("10h", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tempTarget) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Save", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
