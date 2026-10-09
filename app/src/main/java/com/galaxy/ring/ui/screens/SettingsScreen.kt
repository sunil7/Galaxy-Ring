package com.galaxy.ring.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.sync.RingScheduledService
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse

@Composable
fun SettingsScreen(
    onOpenHealthRationale: () -> Unit,
    hasHealthPermissions: Boolean
) {
    val context = LocalContext.current
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository

    var isScheduleEnabled by remember { mutableStateOf(repo.isScheduleEnabled) }
    var selectedInterval by remember { mutableIntStateOf(repo.scheduleIntervalMinutes) }
    var checkHr by remember { mutableStateOf(repo.scheduleCheckHeartRate) }
    var checkSpo2 by remember { mutableStateOf(repo.scheduleCheckSpo2) }

    var apneaSnoring by remember { mutableStateOf(repo.apneaLoudSnoring) }
    var apneaSleepiness by remember { mutableStateOf(repo.apneaDaytimeSleepiness) }
    var apneaPauses by remember { mutableStateOf(repo.apneaObservedPauses) }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Scheduled Checks Section
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Scheduled Checks",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Automated background biometric logging",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isScheduleEnabled,
                            onCheckedChange = { enabled ->
                                isScheduleEnabled = enabled
                                repo.isScheduleEnabled = enabled
                                if (enabled) {
                                    RingScheduledService.start(context)
                                } else {
                                    RingScheduledService.stop(context)
                                }
                            },
                            modifier = Modifier.testTag("schedule_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0F172A), checkedTrackColor = CyberCyan)
                        )
                    }

                    if (isScheduleEnabled) {
                        Spacer(modifier = Modifier.height(18.dp))

                        // Interval Picker
                        Text(
                            text = "Measurement Interval",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val intervals = listOf(10, 15, 30, 45, 60)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            intervals.forEach { minutes ->
                                val selected = selectedInterval == minutes
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedInterval = minutes
                                        repo.scheduleIntervalMinutes = minutes
                                        RingScheduledService.restart(context)
                                    },
                                    label = { Text("${minutes}m") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberCyan,
                                        selectedLabelColor = Color(0xFF0F172A)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Metrics Selection Checkboxes
                        Text(
                            text = "Metrics to measure",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !checkHr
                                    checkHr = next
                                    repo.scheduleCheckHeartRate = next
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checkHr,
                                onCheckedChange = { next ->
                                    checkHr = next
                                    repo.scheduleCheckHeartRate = next
                                },
                                colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Heart Rate (Resting / Active PPG)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !checkSpo2
                                    checkSpo2 = next
                                    repo.scheduleCheckSpo2 = next
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checkSpo2,
                                onCheckedChange = { next ->
                                    checkSpo2 = next
                                    repo.scheduleCheckSpo2 = next
                                },
                                colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SpO₂ (Blood Oxygen Saturation)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Sleep Breathing & Apnea Screening Self-Report Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sleep Apnea Screening Symptoms",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Self-reported symptoms for overnight breathing risk heuristic",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "These indicators are used as weighted factors alongside your Galaxy Ring nocturnal SpO₂ and sleep continuity metrics in the Overnight Breathing Indicators screening card:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !apneaSnoring
                                apneaSnoring = next
                                repo.apneaLoudSnoring = next
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = apneaSnoring,
                            onCheckedChange = { next ->
                                apneaSnoring = next
                                repo.apneaLoudSnoring = next
                            },
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Habitual or loud snoring",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !apneaSleepiness
                                apneaSleepiness = next
                                repo.apneaDaytimeSleepiness = next
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = apneaSleepiness,
                            onCheckedChange = { next ->
                                apneaSleepiness = next
                                repo.apneaDaytimeSleepiness = next
                            },
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Excessive daytime tiredness / somnolence",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !apneaPauses
                                apneaPauses = next
                                repo.apneaObservedPauses = next
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = apneaPauses,
                            onCheckedChange = { next ->
                                apneaPauses = next
                                repo.apneaObservedPauses = next
                            },
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Observed breathing pauses or gasping during sleep",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 2. Battery Optimization & Background Execution Card (Samsung One UI Guidance)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Battery Optimization Exclusion",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "Exclusion active (Unrestricted)" else "Optimization may pause checks",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isIgnoringBatteryOptimizations) NeonEmerald else RosePulse
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Samsung One UI and Android power managers put background processes into deep sleep. To keep scheduled checks running reliably when the screen is locked, exclude Galaxy Ring from battery restrictions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val appIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(appIntent)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isIgnoringBatteryOptimizations) "Battery Settings (Active)" else "Exclude from Battery Restrictions")
                    }
                }
            }
        }

        // 3. Health Connect Integration Status Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = ElectricViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Health Connect Permissions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasHealthPermissions) "All permissions granted" else "Permissions required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasHealthPermissions) NeonEmerald else RosePulse
                                )
                            }
                        }

                        if (hasHealthPermissions) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Synchronizes steps, active and resting heart rate, oxygen saturation (SpO₂), skin temperature, and sleep stages directly with Android Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenHealthRationale() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Permissions Rationale & Data Privacy",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
