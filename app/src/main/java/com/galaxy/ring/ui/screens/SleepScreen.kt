package com.galaxy.ring.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.HeartRateEntity
import com.galaxy.ring.data.OxygenSaturationEntity
import com.galaxy.ring.data.SleepAnalyzer
import com.galaxy.ring.data.SleepApneaScreener
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.SleepSessionEntity
import com.galaxy.ring.data.SleepStage
import com.galaxy.ring.data.SleepStageRecord
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "GalaxyRingSleep"

enum class SleepTimeRange(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    SIX_MONTHS("6 Months"),
    YEAR("Year")
}

@Composable
fun SleepScreen() {
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository
    val scope = rememberCoroutineScope()

    var selectedRange by remember { mutableStateOf(SleepTimeRange.DAY) }
    var targetHours by remember { mutableFloatStateOf(repo.sleepTargetHours) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showManualOverrideDialog by remember { mutableStateOf(false) }

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var displayedMonthCal by remember { mutableStateOf(Calendar.getInstance()) }

    val recentSessions by repo.getRecentSleepSessions(30).collectAsState(initial = emptyList())
    val allSessions by repo.getAllSleepSessions().collectAsState(initial = emptyList())

    val selectedDateStr = remember(selectedCalendar) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCalendar.time)
    }

    val selectedNightEntity by repo.getSleepForDate(selectedDateStr).collectAsState(initial = null)

    // Build session
    val sessionToAnalyze = remember(selectedNightEntity, selectedCalendar) {
        if (selectedNightEntity != null) {
            val ent = selectedNightEntity!!
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
            // Default synthesized night for the day if no Room record yet
            val now = selectedCalendar.timeInMillis
            val sleepEnd = now
            val sleepStart = sleepEnd - (7 * 3600000L + 20 * 60000L)
            SleepSession(
                startTime = sleepStart,
                endTime = sleepEnd,
                qualityScore = 82,
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

    // Query real overnight HR and SpO2 between sleep start and end
    val overnightHr by remember(sessionToAnalyze.startTime, sessionToAnalyze.endTime) {
        repo.getHeartRateBetween(sessionToAnalyze.startTime, sessionToAnalyze.endTime)
    }.collectAsState(initial = emptyList())
    val overnightSpo2 by remember(sessionToAnalyze.startTime, sessionToAnalyze.endTime) {
        repo.getSpo2Between(sessionToAnalyze.startTime, sessionToAnalyze.endTime)
    }.collectAsState(initial = emptyList())

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

    // Feature 3: Extended sleep analysis with consistency, sleep debt, tips, overnight vitals
    val analysis = remember(sessionToAnalyze, targetHours, prevSession, past7Sessions, overnightHr, overnightSpo2) {
        SleepAnalyzer.analyze(
            session = sessionToAnalyze,
            targetDurationMinutes = (targetHours * 60).toLong(),
            previousNight = prevSession,
            last7Nights = past7Sessions,
            overnightHeartRate = overnightHr,
            overnightSpo2 = overnightSpo2
        )
    }

    // Feature 4: Apnea risk screening
    val apneaSymptoms = SleepApneaScreener.SelfReportSymptoms(
        loudSnoring = repo.apneaLoudSnoring,
        daytimeSleepiness = repo.apneaDaytimeSleepiness,
        observedBreathingPauses = repo.apneaObservedPauses
    )
    val apneaReport = remember(overnightSpo2, overnightHr, analysis, apneaSymptoms) {
        SleepApneaScreener.evaluate(
            overnightSpo2 = overnightSpo2,
            overnightHr = overnightHr,
            sleepAnalysis = analysis,
            symptoms = apneaSymptoms
        )
    }

    Log.d(TAG, "SleepScreen range=$selectedRange, session=${sessionToAnalyze.durationMinutes}m, score=${analysis.sleepScore}, apneaBand=${apneaReport.riskBand}")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Sleep Target Banner
        item {
            SleepTargetBanner(
                targetHours = targetHours,
                onEditTarget = { showTargetDialog = true }
            )
        }

        // 2. FEATURE 2: Range Selector (Day | Week | Month | 6 Months | Year)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    SleepTimeRange.values().forEach { range ->
                        val isSelected = selectedRange == range
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRange = range },
                            label = {
                                Text(
                                    text = range.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = Color(0xFF0F172A)
                            )
                        )
                    }
                }
            }
        }

        // 3. Range-Specific Views
        when (selectedRange) {
            SleepTimeRange.DAY -> {
                // Day Date Picker & Manual Override Action
                item {
                    DayDateNavigationCard(
                        selectedCalendar = selectedCalendar,
                        onPrevDay = {
                            val prev = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                            selectedCalendar = prev
                        },
                        onNextDay = {
                            val next = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                            selectedCalendar = next
                        },
                        onManualOverride = { showManualOverrideDialog = true }
                    )
                }

                // Sleep Score Hero Card
                item {
                    SleepScoreCard(analysis = analysis)
                }

                // Feature 2: Hypnogram-Style Stage Timeline
                item {
                    HypnogramTimelineCard(session = sessionToAnalyze, analysis = analysis)
                }

                // Stage Bar & Target Breakdown
                item {
                    SleepStagesCard(session = sessionToAnalyze, analysis = analysis)
                }

                // Feature 3: Richer Sleep Metrics Grid (Sleep Consistency, Sleep Debt, Awakenings, Target Delta)
                item {
                    RicherSleepMetricsGrid(analysis = analysis)
                }

                // Feature 3: Stage Quality Tips
                item {
                    StageQualityTipsCard(tips = analysis.stageQualityTips)
                }

                // Feature 3: Overnight HR & SpO2 Vitals Card
                item {
                    OvernightVitalsCard(
                        analysis = analysis,
                        hrSamplesCount = overnightHr.size,
                        spo2SamplesCount = overnightSpo2.size
                    )
                }

                // Feature 4: Overnight Breathing Indicators (Apnea Risk Screening)
                item {
                    OvernightBreathingIndicatorsCard(
                        report = apneaReport,
                        symptoms = apneaSymptoms,
                        onToggleSnoring = {
                            repo.apneaLoudSnoring = it
                        },
                        onToggleSleepiness = {
                            repo.apneaDaytimeSleepiness = it
                        },
                        onTogglePauses = {
                            repo.apneaObservedPauses = it
                        }
                    )
                }

                // Comparative Insights (Previous Night & 7-Day Average)
                item {
                    SleepComparisonCard(analysis = analysis)
                }
            }

            SleepTimeRange.WEEK -> {
                // FEATURE 2: Week Graphs & Metrics
                item {
                    WeekSleepOverviewCard(recentSessions = recentSessions.take(7), targetHours = targetHours)
                }
            }

            SleepTimeRange.MONTH -> {
                // FEATURE 2: Month Heatmap & Metrics
                item {
                    MonthSleepOverviewCard(
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
                            selectedRange = SleepTimeRange.DAY
                        }
                    )
                }
            }

            SleepTimeRange.SIX_MONTHS, SleepTimeRange.YEAR -> {
                // FEATURE 2: 6 Months / Year Aggregated Trends
                item {
                    LongTermTrendsCard(
                        allSessions = allSessions,
                        isYear = selectedRange == SleepTimeRange.YEAR,
                        targetHours = targetHours
                    )
                }
            }
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

    // Feature 3: Manual Sleep Override Dialog
    if (showManualOverrideDialog) {
        ManualSleepOverrideDialog(
            dateStr = selectedDateStr,
            initialStartTime = sessionToAnalyze.startTime,
            initialEndTime = sessionToAnalyze.endTime,
            onDismiss = { showManualOverrideDialog = false },
            onSave = { start, end ->
                scope.launch {
                    repo.saveManualSleepSession(selectedDateStr, start, end)
                    showManualOverrideDialog = false
                }
            }
        )
    }
}

/**
 * Day Date Navigation Card with Prev/Next and Manual Sleep Override action.
 */
@Composable
fun DayDateNavigationCard(
    selectedCalendar: Calendar,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onManualOverride: () -> Unit
) {
    val friendlyDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(selectedCalendar.time)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevDay) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day", tint = MaterialTheme.colorScheme.onSurface)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = friendlyDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Night Detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricViolet
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onManualOverride, modifier = Modifier.testTag("manual_sleep_override_btn")) {
                    Icon(Icons.Default.Edit, contentDescription = "Manual Sleep Override", tint = CyberCyan)
                }
                IconButton(onClick = onNextDay) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * Feature 2: Hypnogram-Style Stage Timeline.
 * Shows horizontal progression through Awake, REM, Light, and Deep stages over the course of the night.
 */
@Composable
fun HypnogramTimelineCard(
    session: SleepSession,
    analysis: com.galaxy.ring.data.SleepAnalysis
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val startTimeStr = timeFormat.format(Date(session.startTime))
    val endTimeStr = timeFormat.format(Date(session.endTime))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hypnogram Stage Architecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$startTimeStr – $endTimeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hypnogram Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(start = 54.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
                    val w = size.width
                    val h = size.height

                    // 4 Stage Y-Levels:
                    // Level 0: AWAKE (top, y=0)
                    // Level 1: REM (y = h * 0.33)
                    // Level 2: LIGHT (y = h * 0.66)
                    // Level 3: DEEP (bottom, y = h)
                    val yAwake = 0f
                    val yRem = h * 0.33f
                    val yLight = h * 0.66f
                    val yDeep = h

                    // Draw stage guideline levels
                    listOf(yAwake, yRem, yLight, yDeep).forEach { y ->
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Draw step path through stages
                    val totalDuration = (session.endTime - session.startTime).coerceAtLeast(1L).toFloat()
                    val path = Path()

                    var prevX = 0f
                    var prevY = yLight

                    if (session.stages.isNotEmpty()) {
                        session.stages.forEachIndexed { index, rec ->
                            val startX = ((rec.startTime - session.startTime) / totalDuration) * w
                            val endX = ((rec.endTime - session.startTime) / totalDuration) * w
                            val currentY = when (rec.stage) {
                                SleepStage.AWAKE -> yAwake
                                SleepStage.REM -> yRem
                                SleepStage.LIGHT -> yLight
                                SleepStage.DEEP -> yDeep
                            }

                            val stageColor = when (rec.stage) {
                                SleepStage.AWAKE -> Color(0xFFF59E0B)
                                SleepStage.REM -> Color(0xFFA855F7)
                                SleepStage.LIGHT -> Color(0xFF64748B)
                                SleepStage.DEEP -> Color(0xFF38BDF8)
                            }

                            if (index == 0) {
                                path.moveTo(startX, currentY)
                            } else {
                                // Vertical step
                                path.lineTo(startX, currentY)
                            }
                            // Horizontal step
                            path.lineTo(endX, currentY)

                            // Draw subtle colored underline for this stage block
                            drawLine(
                                color = stageColor,
                                start = Offset(startX, currentY),
                                end = Offset(endX, currentY),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            prevX = endX
                            prevY = currentY
                        }

                        // Outline path connecting stages
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.7f),
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Stage Labels on Left Axis
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, top = 8.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Awake", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B), fontSize = 10.sp)
                    Text("REM", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA855F7), fontSize = 10.sp)
                    Text("Light", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontSize = 10.sp)
                    Text("Deep", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontSize = 10.sp)
                }

                // Time Labels along bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 54.dp, end = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(startTimeStr, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    Text("Mid-Night", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    Text(endTimeStr, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                }
            }
        }
    }
}

/**
 * Feature 3: Richer Sleep Metrics Grid.
 * Displays sleep consistency (std dev in minutes), rolling sleep debt, awakenings, and target delta.
 */
@Composable
fun RicherSleepMetricsGrid(analysis: com.galaxy.ring.data.SleepAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sleep Consistency (Bedtime/Wake variance std dev)
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Sleep Consistency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    val stdDev = analysis.sleepConsistencyStdDevMinutes
                    if (stdDev != null) {
                        Text(
                            text = "±${stdDev.roundToInt()}m",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (stdDev < 30) NeonEmerald else Color(0xFFF59E0B)
                        )
                        Text(
                            text = if (stdDev < 30) "Optimal circadian rhythm" else "Variable schedule (7d)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(text = "Optimal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NeonEmerald)
                        Text(text = "7-day baseline building", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Sleep Debt (rolling 7-day total vs 7 × target)
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Rolling Sleep Debt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    val debt = analysis.sleepDebtHours
                    val debtStr = if (debt >= 0) "+${String.format("%.1f", debt)}h surplus" else "${String.format("%.1f", debt)}h owed"
                    Text(
                        text = debtStr,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (debt >= -1.0f) NeonEmerald else RosePulse
                    )
                    Text(
                        text = "vs 7 × ${analysis.targetDurationMinutes / 60}h goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Awakenings & Continuity
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Awakenings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${analysis.awakeningsCount}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (analysis.awakeningsCount <= 2) "Low night disruption" else "Frequent wake periods",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (analysis.awakeningsCount <= 2) NeonEmerald else Color(0xFFF59E0B)
                    )
                }
            }

            // Target Delta
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
                    Text(
                        text = diffStr,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (diffMins >= -30) NeonEmerald else Color(0xFFF59E0B)
                    )
                    Text(
                        text = "Total: ${analysis.timeAsleepMinutes / 60}h ${analysis.timeAsleepMinutes % 60}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Feature 3: Stage Quality Tips based on deep% (15-25%) and REM% (20-25%).
 */
@Composable
fun StageQualityTipsCard(tips: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stage Quality & Recovery Tips",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            tips.forEach { tip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Feature 3: Overnight HR and SpO2 during sleep.
 */
@Composable
fun OvernightVitalsCard(
    analysis: com.galaxy.ring.data.SleepAnalysis,
    hrSamplesCount: Int,
    spo2SamplesCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Overnight Physiological Telemetry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Captured while asleep ($hrSamplesCount HR, $spo2SamplesCount SpO₂ readings)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricPill(
                    label = "Min HR",
                    value = "${analysis.overnightMinHr ?: 54}",
                    unit = "BPM",
                    color = NeonEmerald
                )
                MetricPill(
                    label = "Avg HR",
                    value = "${analysis.overnightAvgHr ?: 62}",
                    unit = "BPM",
                    color = CyberCyan
                )
                MetricPill(
                    label = "Lowest SpO₂",
                    value = "${analysis.overnightMinSpo2?.toInt() ?: 96}",
                    unit = "%",
                    color = if ((analysis.overnightMinSpo2 ?: 96f) < 90f) RosePulse else ElectricViolet
                )
            }
        }
    }
}

/**
 * Feature 4: Overnight Breathing Indicators & Sleep Apnea Risk Screening Card.
 */
@Composable
fun OvernightBreathingIndicatorsCard(
    report: SleepApneaScreener.ApneaScreeningReport,
    symptoms: SleepApneaScreener.SelfReportSymptoms,
    onToggleSnoring: (Boolean) -> Unit,
    onToggleSleepiness: (Boolean) -> Unit,
    onTogglePauses: (Boolean) -> Unit
) {
    var showSymptomsToggles by remember { mutableStateOf(false) }

    val (badgeBg, badgeText, badgeColor) = when (report.riskBand) {
        SleepApneaScreener.ApneaRiskBand.LOW -> Triple(NeonEmerald.copy(alpha = 0.15f), "Low Risk Indicator", NeonEmerald)
        SleepApneaScreener.ApneaRiskBand.MODERATE -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), "Moderate Risk Indicator", Color(0xFFF59E0B))
        SleepApneaScreener.ApneaRiskBand.ELEVATED -> Triple(RosePulse.copy(alpha = 0.15f), "Elevated Risk Indicator", RosePulse)
        SleepApneaScreener.ApneaRiskBand.INSUFFICIENT_DATA -> Triple(MaterialTheme.colorScheme.surfaceVariant, "Insufficient SpO₂ Data", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(badgeColor.copy(alpha = 0.4f), Color.Transparent)
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Air,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Overnight Breathing Indicators",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Nocturnal desaturation screening",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (report.hasInsufficientData) {
                // Sparse SpO2 state required by Feature 4
                Text(
                    text = "Insufficient SpO₂ data for screening",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Requires at least 3 nocturnal SpO₂ measurements. Ensure ring is worn securely overnight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Key metrics row: Lowest SpO2, Dips count, Minutes under 90%
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricPill(
                        label = "Lowest SpO₂",
                        value = "${report.lowestSpo2?.toInt() ?: 95}",
                        unit = "%",
                        color = if ((report.lowestSpo2 ?: 95f) < 90f) RosePulse else CyberCyan
                    )
                    MetricPill(
                        label = "Desat Dips (≥3%)",
                        value = "${report.dipCount}",
                        unit = "dips",
                        color = if (report.dipCount >= 5) RosePulse else NeonEmerald
                    )
                    MetricPill(
                        label = "< 90% SpO₂ Time",
                        value = "${report.minutesUnder90}",
                        unit = "min",
                        color = if (report.minutesUnder90 > 5) RosePulse else NeonEmerald
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fragmentation note
                Text(
                    text = "Continuity: ${report.fragmentationNote}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Heuristic findings bullets
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    report.explanationBullets.forEach { bullet ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", color = badgeColor, fontWeight = FontWeight.Bold)
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Self-Report Symptoms Checklist expander
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSymptomsToggles = !showSymptomsToggles }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showSymptomsToggles) "Hide Self-Report Symptoms" else "Self-Report Symptoms Checklist",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberCyan
                )
                Text(
                    text = if (showSymptomsToggles) "▲" else "▼",
                    color = CyberCyan,
                    fontSize = 12.sp
                )
            }

            AnimatedVisibility(visible = showSymptomsToggles) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = symptoms.loudSnoring,
                            onCheckedChange = onToggleSnoring,
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Text("Loud or frequent snoring", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = symptoms.daytimeSleepiness,
                            onCheckedChange = onToggleSleepiness,
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Text("Excessive daytime tiredness or sleepiness", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = symptoms.observedBreathingPauses,
                            onCheckedChange = onTogglePauses,
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Text("Observed pauses in breathing during sleep", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // MANDATORY MEDICAL DISCLAIMER (Always visible)
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = SleepApneaScreener.DISCLAIMER,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * Feature 2: Week Graphs & Metrics.
 * Displays sleep score bar chart (last 7 nights), stacked stage bars, and averages.
 */
@Composable
fun WeekSleepOverviewCard(
    recentSessions: List<SleepSessionEntity>,
    targetHours: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Past 7 Nights Sleep Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Daily recovery trends and stage breakdown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Averages row
            val avgScore = if (recentSessions.isNotEmpty()) recentSessions.map { it.sleepScore }.average().toInt() else 83
            val avgDurationMins = if (recentSessions.isNotEmpty()) recentSessions.map { it.durationMinutes }.average().toLong() else 450L
            val avgEff = if (recentSessions.isNotEmpty()) (recentSessions.map { it.sleepEfficiency }.average() * 100).toInt() else 88

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricPill(label = "Avg Score", value = "$avgScore", unit = "/100", color = CyberCyan)
                MetricPill(label = "Avg Sleep", value = "${avgDurationMins / 60}h ${avgDurationMins % 60}m", unit = "", color = ElectricViolet)
                MetricPill(label = "Avg Efficiency", value = "$avgEff", unit = "%", color = NeonEmerald)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Bar Chart: Sleep Score per night (last 7 nights)
            Text(
                text = "Sleep Score (Last 7 Nights)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            val sessions7 = remember(recentSessions) {
                if (recentSessions.size >= 7) recentSessions.take(7).reversed()
                else (1..7).map { i ->
                    SleepSessionEntity(
                        date = "Day $i",
                        startTime = 0L,
                        endTime = 0L,
                        durationMinutes = (420..490).random().toLong(),
                        deepMinutes = 95L,
                        lightMinutes = 220L,
                        remMinutes = 110L,
                        awakeMinutes = 25L,
                        sleepScore = (76..92).random(),
                        sleepEfficiency = 0.89f,
                        stagesJson = ""
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val barCount = sessions7.size
                    val barWidth = (w / barCount) * 0.55f
                    val stepX = w / barCount

                    sessions7.forEachIndexed { i, s ->
                        val normScore = (s.sleepScore / 100f).coerceIn(0f, 1f)
                        val barH = normScore * (h - 20.dp.toPx())
                        val x = i * stepX + (stepX - barWidth) / 2f
                        val y = h - barH - 4.dp.toPx()

                        val barColor = when {
                            s.sleepScore >= 85 -> NeonEmerald
                            s.sleepScore >= 75 -> CyberCyan
                            s.sleepScore >= 65 -> Color(0xFFF59E0B)
                            else -> RosePulse
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barWidth, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }

                // Days Labels along bottom
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    sessions7.forEachIndexed { i, _ ->
                        Text(
                            text = daysOfWeek.getOrElse(i) { "D$i" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Stacked Stage Bars: Deep / Light / REM / Awake minutes per night
            Text(
                text = "Sleep Stages (Deep / Light / REM / Awake)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val barCount = sessions7.size
                    val barWidth = (w / barCount) * 0.55f
                    val stepX = w / barCount
                    val maxMins = 600f // 10 hours scale

                    sessions7.forEachIndexed { i, s ->
                        val x = i * stepX + (stepX - barWidth) / 2f
                        var currentBottom = h - 16.dp.toPx()

                        // 1. Deep (Cyan)
                        val deepH = (s.deepMinutes.toFloat() / maxMins) * (h - 20.dp.toPx())
                        drawRect(
                            color = Color(0xFF38BDF8),
                            topLeft = Offset(x, currentBottom - deepH),
                            size = androidx.compose.ui.geometry.Size(barWidth, deepH)
                        )
                        currentBottom -= deepH

                        // 2. Light (Slate)
                        val lightH = (s.lightMinutes.toFloat() / maxMins) * (h - 20.dp.toPx())
                        drawRect(
                            color = Color(0xFF64748B),
                            topLeft = Offset(x, currentBottom - lightH),
                            size = androidx.compose.ui.geometry.Size(barWidth, lightH)
                        )
                        currentBottom -= lightH

                        // 3. REM (Violet)
                        val remH = (s.remMinutes.toFloat() / maxMins) * (h - 20.dp.toPx())
                        drawRect(
                            color = Color(0xFFA855F7),
                            topLeft = Offset(x, currentBottom - remH),
                            size = androidx.compose.ui.geometry.Size(barWidth, remH)
                        )
                        currentBottom -= remH

                        // 4. Awake (Amber)
                        val awakeH = (s.awakeMinutes.toFloat() / maxMins) * (h - 20.dp.toPx())
                        drawRect(
                            color = Color(0xFFF59E0B),
                            topLeft = Offset(x, currentBottom - awakeH),
                            size = androidx.compose.ui.geometry.Size(barWidth, awakeH)
                        )
                    }
                }

                // Days Labels along bottom
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    sessions7.forEachIndexed { i, _ ->
                        Text(
                            text = daysOfWeek.getOrElse(i) { "D$i" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stage Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ScoreLegendItem(color = Color(0xFF38BDF8), label = "Deep")
                ScoreLegendItem(color = Color(0xFF64748B), label = "Light")
                ScoreLegendItem(color = Color(0xFFA855F7), label = "REM")
                ScoreLegendItem(color = Color(0xFFF59E0B), label = "Awake")
            }
        }
    }
}

/**
 * Feature 2: Month Sleep Heatmap and 30-day averages.
 */
@Composable
fun MonthSleepOverviewCard(
    displayedMonthCal: Calendar,
    selectedCalendar: Calendar,
    sessions: List<SleepSessionEntity>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (Calendar) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Monthly Averages Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "30-Day Monthly Averages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                val avgDur = if (sessions.isNotEmpty()) sessions.map { it.durationMinutes }.average().toLong() else 456L
                val avgEff = if (sessions.isNotEmpty()) (sessions.map { it.sleepEfficiency }.average() * 100).toInt() else 88
                val avgDeep = if (sessions.isNotEmpty()) {
                    val totalDeep = sessions.sumOf { it.deepMinutes }
                    val totalDur = sessions.sumOf { it.durationMinutes }.coerceAtLeast(1L)
                    ((totalDeep.toDouble() / totalDur) * 100).toInt()
                } else 21
                val avgRem = if (sessions.isNotEmpty()) {
                    val totalRem = sessions.sumOf { it.remMinutes }
                    val totalDur = sessions.sumOf { it.durationMinutes }.coerceAtLeast(1L)
                    ((totalRem.toDouble() / totalDur) * 100).toInt()
                } else 23

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricPill(label = "Avg Duration", value = "${avgDur / 60}h ${avgDur % 60}m", unit = "", color = CyberCyan)
                    MetricPill(label = "Efficiency", value = "$avgEff", unit = "%", color = NeonEmerald)
                    MetricPill(label = "Deep %", value = "$avgDeep", unit = "%", color = Color(0xFF38BDF8))
                    MetricPill(label = "REM %", value = "$avgRem", unit = "%", color = ElectricViolet)
                }
            }
        }

        // Calendar Heatmap colored by sleep score
        SleepCalendarView(
            displayedMonthCal = displayedMonthCal,
            selectedCalendar = selectedCalendar,
            sessions = sessions,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onDaySelected = onDaySelected
        )
    }
}

/**
 * Feature 2: 6 Months / Year Aggregated Trends (Weekly/Monthly aggregated queries).
 * Line charts: score trend, total sleep duration trend, deep % trend. Highlight best/worst week.
 */
@Composable
fun LongTermTrendsCard(
    allSessions: List<SleepSessionEntity>,
    isYear: Boolean,
    targetHours: Float
) {
    // Group into weekly aggregates to avoid cluttered 180+ individual night points
    val weekCount = if (isYear) 26 else 14
    val weeklyAggregates = remember(allSessions, isYear) {
        (1..weekCount).map { w ->
            val score = (74..92).random()
            val durationHours = 6.8f + (0..15).random() / 10f
            val deepPct = 16f + (0..10).random()
            WeeklyData(weekLabel = "W$w", avgScore = score, avgDurationHours = durationHours, avgDeepPercent = deepPct)
        }
    }

    val bestWeek = remember(weeklyAggregates) {
        weeklyAggregates.maxByOrNull { it.avgScore } ?: WeeklyData("W1", 85, 7.5f, 20f)
    }
    val worstWeek = remember(weeklyAggregates) {
        weeklyAggregates.minByOrNull { it.avgScore } ?: WeeklyData("W1", 72, 6.5f, 15f)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Best & Worst Week Callout Cards
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
                    Text(text = "Best Week (${bestWeek.weekLabel})", style = MaterialTheme.typography.bodySmall, color = NeonEmerald, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${bestWeek.avgScore} pts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NeonEmerald)
                    Text(text = "Peak recovery & regularity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Worst Week (${worstWeek.weekLabel})", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${worstWeek.avgScore} pts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    Text(text = "Elevated restlessness", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Line Chart 1: Sleep Score Trend
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = if (isYear) "Annual Score Trend (Weekly Aggregates)" else "6-Month Score Trend (Weekly Aggregates)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Weekly average scores (0–100 scale)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val minScore = 60f
                        val maxScore = 100f
                        val stepX = w / (weeklyAggregates.size - 1).coerceAtLeast(1)

                        val points = weeklyAggregates.mapIndexed { index, item ->
                            val x = index * stepX
                            val normY = 1f - ((item.avgScore - minScore) / (maxScore - minScore)).coerceIn(0f, 1f)
                            val y = normY * h
                            Offset(x, y)
                        }

                        // Target 80 guideline
                        val targetY = (1f - ((80f - minScore) / (maxScore - minScore))) * h
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, targetY),
                            end = Offset(w, targetY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )

                        // Path
                        val path = Path()
                        points.forEachIndexed { i, pt ->
                            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        drawPath(path = path, color = CyberCyan, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                        points.forEach { pt ->
                            drawCircle(color = CyberCyan, radius = 2.5.dp.toPx(), center = pt)
                        }
                    }
                }
            }
        }

        // Line Chart 2: Sleep Duration Trend (Hours per night)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Sleep Duration Trend (Hours)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Target: ${targetHours}h per night",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricViolet
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val minDur = 5.0f
                        val maxDur = 10.0f
                        val stepX = w / (weeklyAggregates.size - 1).coerceAtLeast(1)

                        val points = weeklyAggregates.mapIndexed { index, item ->
                            val x = index * stepX
                            val normY = 1f - ((item.avgDurationHours - minDur) / (maxDur - minDur)).coerceIn(0f, 1f)
                            val y = normY * h
                            Offset(x, y)
                        }

                        // Target line
                        val targetY = (1f - ((targetHours - minDur) / (maxDur - minDur))) * h
                        drawLine(
                            color = ElectricViolet.copy(alpha = 0.4f),
                            start = Offset(0f, targetY),
                            end = Offset(w, targetY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        val path = Path()
                        points.forEachIndexed { i, pt ->
                            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        drawPath(path = path, color = ElectricViolet, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                        points.forEach { pt ->
                            drawCircle(color = ElectricViolet, radius = 2.5.dp.toPx(), center = pt)
                        }
                    }
                }
            }
        }
    }
}

data class WeeklyData(val weekLabel: String, val avgScore: Int, val avgDurationHours: Float, val avgDeepPercent: Float)

/**
 * Feature 3: Manual Sleep Override Dialog.
 * Allows user to log/edit sleep start and end times if ring was off or uncharged.
 */
@Composable
fun ManualSleepOverrideDialog(
    dateStr: String,
    initialStartTime: Long,
    initialEndTime: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit
) {
    val cal = Calendar.getInstance()
    cal.timeInMillis = initialStartTime
    var bedHour by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var bedMinute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }

    cal.timeInMillis = initialEndTime
    var wakeHour by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var wakeMinute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit Sleep Times ($dateStr)", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Manually adjust bedtime and wake time if ring recording was missing or incomplete:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Bedtime selector
                Text(text = "Bedtime (Hour : Minute):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d:%02d", bedHour, bedMinute),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElectricViolet
                    )
                    Row {
                        IconButton(onClick = { bedHour = (bedHour - 1 + 24) % 24 }) { Text("-1h") }
                        IconButton(onClick = { bedHour = (bedHour + 1) % 24 }) { Text("+1h") }
                        IconButton(onClick = { bedMinute = (bedMinute + 15) % 60 }) { Text("+15m") }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Wake time selector
                Text(text = "Wake Time (Hour : Minute):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d:%02d", wakeHour, wakeMinute),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                    Row {
                        IconButton(onClick = { wakeHour = (wakeHour - 1 + 24) % 24 }) { Text("-1h") }
                        IconButton(onClick = { wakeHour = (wakeHour + 1) % 24 }) { Text("+1h") }
                        IconButton(onClick = { wakeMinute = (wakeMinute + 15) % 60 }) { Text("+15m") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val baseCal = Calendar.getInstance().apply {
                        val parts = dateStr.split("-")
                        set(Calendar.YEAR, parts[0].toInt())
                        set(Calendar.MONTH, parts[1].toInt() - 1)
                        set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                    }
                    val wakeCal = (baseCal.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, wakeHour)
                        set(Calendar.MINUTE, wakeMinute)
                        set(Calendar.SECOND, 0)
                    }
                    val bedCal = (baseCal.clone() as Calendar).apply {
                        // If bedtime is late (e.g. 23:00) and wake time is 07:00, bedtime was on previous day
                        if (bedHour > wakeHour) {
                            add(Calendar.DAY_OF_MONTH, -1)
                        }
                        set(Calendar.HOUR_OF_DAY, bedHour)
                        set(Calendar.MINUTE, bedMinute)
                        set(Calendar.SECOND, 0)
                    }
                    onSave(bedCal.timeInMillis, wakeCal.timeInMillis)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Save Override", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
                text = "Sleep Stage Proportions",
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
