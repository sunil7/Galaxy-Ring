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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.galaxy.ring.data.DailyStepsEntity
import com.galaxy.ring.data.HeartRateEntity
import com.galaxy.ring.data.OxygenSaturationEntity
import com.galaxy.ring.data.SleepSessionEntity
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "GalaxyRingHistory"

@Composable
fun HistoryScreen() {
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var displayedMonthCal by remember { mutableStateOf(Calendar.getInstance()) }

    val allSteps by repo.getAllDailySteps().collectAsState(initial = emptyList())
    val allSleep by repo.getAllSleepSessions().collectAsState(initial = emptyList())

    val selectedDateStr = remember(selectedCalendar) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCalendar.time)
    }

    val scheduleInterval = repo.scheduleIntervalMinutes

    val daySteps by repo.getDailySteps(selectedDateStr).collectAsState(initial = null)
    val dayHrSamples by repo.getHeartRateForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val daySpo2Samples by repo.getSpo2ForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val daySleep by repo.getSleepForDate(selectedDateStr).collectAsState(initial = null)

    Log.d(TAG, "Displaying history for $selectedDateStr: HR=${dayHrSamples.size}, SpO2=${daySpo2Samples.size}, interval=${scheduleInterval}m")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // 1. Calendar Month Card
        item {
            CalendarMonthView(
                displayedMonthCal = displayedMonthCal,
                selectedCalendar = selectedCalendar,
                datesWithSteps = allSteps.map { it.date }.toSet(),
                datesWithSleep = allSleep.map { it.date }.toSet(),
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

        // 2. Selected Day Header
        item {
            val friendlyDateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(selectedCalendar.time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = friendlyDateStr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 3. FEATURE 1: Heart Rate Day Timeline & 24h Chart
        item {
            HeartRateDayTimelineCard(
                samples = dayHrSamples,
                scheduleIntervalMinutes = scheduleInterval,
                selectedCalendar = selectedCalendar
            )
        }

        // 4. FEATURE 1: SpO₂ Day Timeline & 24h Chart
        item {
            Spo2DayTimelineCard(
                samples = daySpo2Samples,
                scheduleIntervalMinutes = scheduleInterval,
                selectedCalendar = selectedCalendar
            )
        }

        // 5. Day Summary: Steps Activity
        item {
            HistoryStepsCard(steps = daySteps)
        }

        // 6. Day Summary: Sleep
        item {
            HistorySleepCard(sleep = daySleep)
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * Feature 1.A & 1.C: Heart Rate 24h line chart and period-grouped timeline list.
 */
@Composable
fun HeartRateDayTimelineCard(
    samples: List<HeartRateEntity>,
    scheduleIntervalMinutes: Int,
    selectedCalendar: Calendar
) {
    var showTimelineList by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RosePulse.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = RosePulse,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Heart Rate (24h Timeline)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Expected interval: every ${scheduleIntervalMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }
                }

                Text(
                    text = "${samples.size} samples",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (samples.isEmpty()) {
                // Empty state required by Feature 1.A
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No heart rate data for this day",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Connect ring or enable scheduled background checks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Metric Pills
                val minBpm = samples.minOf { it.bpm }
                val avgBpm = samples.map { it.bpm }.average().toInt()
                val maxBpm = samples.maxOf { it.bpm }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricPill(label = "Min", value = "$minBpm", unit = "BPM", color = NeonEmerald)
                    MetricPill(label = "Avg", value = "$avgBpm", unit = "BPM", color = CyberCyan)
                    MetricPill(label = "Max", value = "$maxBpm", unit = "BPM", color = RosePulse)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 24h Line Chart Canvas
                Day24hLineChart(
                    samples = samples.map { ChartPoint(timestamp = it.timestamp, value = it.bpm.toFloat()) },
                    scheduleIntervalMinutes = scheduleIntervalMinutes,
                    selectedCalendar = selectedCalendar,
                    minValue = 40f,
                    maxValue = 140f,
                    unit = "BPM",
                    lineColor = RosePulse,
                    guidelineValues = listOf(60f, 80f, 100f, 120f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle for detailed timeline list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showTimelineList = !showTimelineList }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showTimelineList) "Hide Sample Readings" else "View Timeline Readings (${samples.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CyberCyan
                        )
                    }
                    Icon(
                        imageVector = if (showTimelineList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showTimelineList) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        GroupedDayTimelineList(
                            samples = samples.map {
                                TimelineSample(
                                    timestamp = it.timestamp,
                                    valueDisplay = "${it.bpm} BPM",
                                    method = it.recordingMethod,
                                    color = RosePulse
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Feature 1.B & 1.C: SpO₂ 24h line chart and period-grouped timeline list.
 */
@Composable
fun Spo2DayTimelineCard(
    samples: List<OxygenSaturationEntity>,
    scheduleIntervalMinutes: Int,
    selectedCalendar: Calendar
) {
    var showTimelineList by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bloodtype,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SpO₂ (24h Timeline)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Expected interval: every ${scheduleIntervalMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }
                }

                Text(
                    text = "${samples.size} samples",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (samples.isEmpty()) {
                // Empty state required by Feature 1.B
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bloodtype,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No SpO₂ data for this day",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Take a manual reading or enable automatic SpO₂ checks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Metric Pills
                val minSpo2 = samples.minOf { it.percentage }.toInt()
                val avgSpo2 = samples.map { it.percentage }.average().toInt()
                val maxSpo2 = samples.maxOf { it.percentage }.toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricPill(label = "Min", value = "$minSpo2", unit = "%", color = if (minSpo2 < 90) RosePulse else CyberCyan)
                    MetricPill(label = "Avg", value = "$avgSpo2", unit = "%", color = NeonEmerald)
                    MetricPill(label = "Max", value = "$maxSpo2", unit = "%", color = ElectricViolet)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 24h Line Chart Canvas (85% - 100%)
                Day24hLineChart(
                    samples = samples.map { ChartPoint(timestamp = it.timestamp, value = it.percentage) },
                    scheduleIntervalMinutes = scheduleIntervalMinutes,
                    selectedCalendar = selectedCalendar,
                    minValue = 85f,
                    maxValue = 100f,
                    unit = "%",
                    lineColor = CyberCyan,
                    guidelineValues = listOf(90f, 95f, 100f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle for detailed timeline list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showTimelineList = !showTimelineList }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showTimelineList) "Hide Sample Readings" else "View Timeline Readings (${samples.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CyberCyan
                        )
                    }
                    Icon(
                        imageVector = if (showTimelineList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showTimelineList) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        GroupedDayTimelineList(
                            samples = samples.map {
                                TimelineSample(
                                    timestamp = it.timestamp,
                                    valueDisplay = "${it.percentage.toInt()}%",
                                    method = it.recordingMethod,
                                    color = CyberCyan
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

data class ChartPoint(val timestamp: Long, val value: Float)
data class TimelineSample(val timestamp: Long, val valueDisplay: String, val method: String, val color: Color)

/**
 * 24-hour Canvas Line Chart with day period shading, ideal slot ticks, and gap markers.
 */
@Composable
fun Day24hLineChart(
    samples: List<ChartPoint>,
    scheduleIntervalMinutes: Int,
    selectedCalendar: Calendar,
    minValue: Float,
    maxValue: Float,
    unit: String,
    lineColor: Color,
    guidelineValues: List<Float>
) {
    val dayCal = (selectedCalendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDayMillis = dayCal.timeInMillis
    val totalDayMillis = 24 * 60 * 60 * 1000L
    val maxGapMillis = (scheduleIntervalMinutes * 2 * 60 * 1000L).coerceAtLeast(40 * 60 * 1000L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                val chartW = size.width
                val chartH = size.height

                // 1. Draw 4 period shaded background bands:
                // Night (00:00 - 06:00), Morning (06:00 - 12:00), Afternoon (12:00 - 18:00), Evening (18:00 - 24:00)
                val qW = chartW / 4f

                // Night band shading
                drawRect(
                    color = Color(0xFF1E1B4B).copy(alpha = 0.5f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(qW, chartH)
                )
                // Afternoon subtle tint
                drawRect(
                    color = Color(0xFF1E293B).copy(alpha = 0.3f),
                    topLeft = Offset(qW * 2, 0f),
                    size = androidx.compose.ui.geometry.Size(qW, chartH)
                )

                // 2. Draw Horizontal Guidelines
                guidelineValues.forEach { gVal ->
                    val normY = 1f - ((gVal - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
                    val y = normY * chartH
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(chartW, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // 3. Draw Vertical time grid lines (00:00, 06:00, 12:00, 18:00, 24:00)
                for (i in 0..4) {
                    val x = i * qW
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(x, 0f),
                        end = Offset(x, chartH),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 4. Ideal Slot Ticks along bottom (every scheduleIntervalMinutes)
                val slotCount = (24 * 60) / scheduleIntervalMinutes
                for (s in 0..slotCount) {
                    val slotProgress = s.toFloat() / slotCount.toFloat()
                    val sx = slotProgress * chartW
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = 1.2.dp.toPx(),
                        center = Offset(sx, chartH - 2.dp.toPx())
                    )
                }

                // 5. Plot samples and connect with lines + detect gaps > 2x interval
                if (samples.isNotEmpty()) {
                    val sorted = samples.sortedBy { it.timestamp }
                    val points = sorted.map { pt ->
                        val progressX = ((pt.timestamp - startOfDayMillis).toFloat() / totalDayMillis.toFloat()).coerceIn(0f, 1f)
                        val x = progressX * chartW
                        val normY = 1f - ((pt.value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
                        val y = normY * chartH
                        Offset(x, y)
                    }

                    // Draw connecting lines with gap detection
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val gap = sorted[i + 1].timestamp - sorted[i].timestamp

                        if (gap > maxGapMillis) {
                            // Large gap detected (> 2x scheduleInterval) -> draw dashed warning line & marker
                            drawLine(
                                color = Color(0xFFF59E0B).copy(alpha = 0.4f),
                                start = p1,
                                end = p2,
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                            // Gap marker halfway
                            val midX = (p1.x + p2.x) / 2f
                            val midY = (p1.y + p2.y) / 2f
                            drawCircle(
                                color = Color(0xFFF59E0B),
                                radius = 2.5.dp.toPx(),
                                center = Offset(midX, midY)
                            )
                        } else {
                            // Normal continuous line
                            drawLine(
                                color = lineColor,
                                start = p1,
                                end = p2,
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw sample dots
                    points.forEach { pt ->
                        drawCircle(
                            color = Color(0xFF0F172A),
                            radius = 4.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time Axis Labels (Night, Morning, Afternoon, Evening)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "00:00 (Night)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "06:00 (Morn)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "12:00 (Aft)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "18:00 (Eve)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "24:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Gap indicator legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Gap marker (> 2× interval)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Period-grouped timeline list (Night 00-06, Morning 06-12, Afternoon 12-18, Evening 18-24).
 */
@Composable
fun GroupedDayTimelineList(samples: List<TimelineSample>) {
    val cal = Calendar.getInstance()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

    val periods = listOf(
        "Night (00:00 – 06:00)" to samples.filter {
            cal.timeInMillis = it.timestamp
            cal.get(Calendar.HOUR_OF_DAY) in 0..5
        },
        "Morning (06:00 – 12:00)" to samples.filter {
            cal.timeInMillis = it.timestamp
            cal.get(Calendar.HOUR_OF_DAY) in 6..11
        },
        "Afternoon (12:00 – 18:00)" to samples.filter {
            cal.timeInMillis = it.timestamp
            cal.get(Calendar.HOUR_OF_DAY) in 12..17
        },
        "Evening (18:00 – 24:00)" to samples.filter {
            cal.timeInMillis = it.timestamp
            cal.get(Calendar.HOUR_OF_DAY) in 18..23
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        periods.forEach { (periodLabel, periodSamples) ->
            if (periodSamples.isNotEmpty()) {
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                periodSamples.sortedBy { it.timestamp }.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(s.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timeFormat.format(Date(s.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${s.method.lowercase()})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = s.valueDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = s.color
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun CalendarMonthView(
    displayedMonthCal: Calendar,
    selectedCalendar: Calendar,
    datesWithSteps: Set<String>,
    datesWithSleep: Set<String>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (Calendar) -> Unit
) {
    val monthTitle = remember(displayedMonthCal) {
        SimpleDateFormat("MMMM yyyy", Locale.US).format(displayedMonthCal.time)
    }

    val daysInMonth = displayedMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayCal = (displayedMonthCal.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
    val leadingEmptyDays = (firstDayOfWeek - 2 + 7) % 7 // Monday-first offset

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month Header with arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day of week labels (M T W T F S S)
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

            // Day Grid (up to 6 rows)
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
                            val cellCal = (displayedMonthCal.clone() as Calendar).apply {
                                set(Calendar.DAY_OF_MONTH, thisDay)
                            }
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cellCal.time)
                            val isSelected = cellCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
                                    cellCal.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR)

                            val hasSteps = datesWithSteps.contains(dateStr)
                            val hasSleep = datesWithSleep.contains(dateStr)

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) CyberCyan else Color.Transparent
                                    )
                                    .border(
                                        width = if (isSelected) 0.dp else 1.dp,
                                        color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    )
                                    .clickable { onDaySelected(cellCal) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$thisDay",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface
                                    )
                                    // Dots indicator
                                    if (hasSteps || hasSleep) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(top = 1.dp)
                                        ) {
                                            if (hasSteps) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color(0xFF0F172A) else NeonEmerald)
                                                )
                                            }
                                            if (hasSleep) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color(0xFF0F172A) else ElectricViolet)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            currentDay++
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryStepsCard(steps: DailyStepsEntity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Steps Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (steps != null) "Goal: 10,000" else "No Data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (steps != null) {
                Text(
                    text = String.format("%,d steps", steps.steps),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Calories: ${steps.caloriesKcal} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("Distance: %.2f km", steps.distanceMeters / 1000.0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "No step samples recorded for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HistorySleepCard(sleep: SleepSessionEntity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Nightlight,
                        contentDescription = null,
                        tint = ElectricViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sleep Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (sleep != null) {
                    Text(
                        text = "Score: ${sleep.sleepScore}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricViolet,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricViolet.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (sleep != null) {
                val hours = sleep.durationMinutes / 60
                val mins = sleep.durationMinutes % 60
                Text(
                    text = "${hours}h ${mins}m in bed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Deep: ${sleep.deepMinutes}m  •  REM: ${sleep.remMinutes}m  •  Light: ${sleep.lightMinutes}m  •  Awake: ${sleep.awakeMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No sleep session recorded for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetricPill(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = unit, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}
