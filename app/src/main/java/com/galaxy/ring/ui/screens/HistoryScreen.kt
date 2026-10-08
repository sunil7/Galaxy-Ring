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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Nightlight
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

    val daySteps by repo.getDailySteps(selectedDateStr).collectAsState(initial = null)
    val dayHrSamples by repo.getHeartRateForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val daySpo2Samples by repo.getSpo2ForDay(selectedCalendar.time).collectAsState(initial = emptyList())
    val daySleep by repo.getSleepForDate(selectedDateStr).collectAsState(initial = null)

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

        // 3. Day Summary: Steps
        item {
            HistoryStepsCard(steps = daySteps)
        }

        // 4. Day Summary: Heart Rate
        item {
            HistoryHeartRateCard(samples = dayHrSamples)
        }

        // 5. Day Summary: SpO2
        item {
            HistorySpo2Card(samples = daySpo2Samples)
        }

        // 6. Day Summary: Sleep
        item {
            HistorySleepCard(sleep = daySleep)
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
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
fun HistoryHeartRateCard(samples: List<HeartRateEntity>) {
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
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = RosePulse,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Heart Rate Samples",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${samples.size} samples",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (samples.isNotEmpty()) {
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
            } else {
                Text(
                    text = "No heart-rate samples recorded on this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HistorySpo2Card(samples: List<OxygenSaturationEntity>) {
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
                        imageVector = Icons.Default.Bloodtype,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SpO₂ Samples",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${samples.size} samples",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (samples.isNotEmpty()) {
                val minSpo2 = samples.minOf { it.percentage }.toInt()
                val avgSpo2 = samples.map { it.percentage }.average().toInt()
                val maxSpo2 = samples.maxOf { it.percentage }.toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricPill(label = "Min", value = "$minSpo2", unit = "%", color = CyberCyan)
                    MetricPill(label = "Avg", value = "$avgSpo2", unit = "%", color = NeonEmerald)
                    MetricPill(label = "Max", value = "$maxSpo2", unit = "%", color = ElectricViolet)
                }
            } else {
                Text(
                    text = "No SpO₂ samples recorded on this date.",
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
