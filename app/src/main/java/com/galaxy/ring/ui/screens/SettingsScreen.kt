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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
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
import com.galaxy.ring.data.MonitorConfig
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSchedule
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.data.ScreenBrightness
import com.galaxy.ring.sync.RingScheduledService
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse
import androidx.compose.material.icons.filled.BugReport
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.galaxy.ring.data.ConnectionState

@Composable
fun SettingsScreen(
    onOpenHealthRationale: () -> Unit,
    hasHealthPermissions: Boolean,
    onRequestHealthPermissions: () -> Unit = {},
    onOpenAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository
    val scope = rememberCoroutineScope()
    var adminTapCount by remember { mutableIntStateOf(0) }
    var isAdminUnlocked by remember { mutableStateOf(true) }

    var isKeepConnected by remember { mutableStateOf(repo.isKeepConnected) }
    var isBondingEnabled by remember { mutableStateOf(repo.isBondingEnabled) }
    var isRingDisplayEnabled by remember { mutableStateOf(repo.isRingDisplayEnabled) }
    var displayStatusMessage by remember { mutableStateOf<String?>(null) }
    val monitoringSettings = remember { MonitoringSettings(context) }
    var selectedBrightness by remember { mutableStateOf(monitoringSettings.screenBrightness) }
    var brightnessStatusMessage by remember { mutableStateOf<String?>(null) }
    var showBatteryPromptDialog by remember { mutableStateOf(false) }

    var hrConfig by remember { mutableStateOf(monitoringSettings.config(MonitorMetric.HEART_RATE)) }
    var spo2Config by remember { mutableStateOf(monitoringSettings.config(MonitorMetric.SPO2)) }
    var hrvConfig by remember { mutableStateOf(monitoringSettings.config(MonitorMetric.HRV)) }
    var stressConfig by remember { mutableStateOf(monitoringSettings.config(MonitorMetric.STRESS)) }

    var apneaSnoring by remember { mutableStateOf(repo.apneaLoudSnoring) }
    var apneaSleepiness by remember { mutableStateOf(repo.apneaDaytimeSleepiness) }
    var apneaPauses by remember { mutableStateOf(repo.apneaObservedPauses) }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true

    fun checkAndPromptBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations) {
            showBatteryPromptDialog = true
        }
    }

    fun updateMonitoringService() {
        val anyEnabled = hrConfig.enabled || spo2Config.enabled || hrvConfig.enabled || stressConfig.enabled
        repo.isScheduleEnabled = anyEnabled
        repo.scheduleCheckHeartRate = hrConfig.enabled
        repo.scheduleCheckSpo2 = spo2Config.enabled
        val shortest = MonitoringSchedule.shortestIntervalMin(monitoringSettings.allConfigs())
        if (shortest != null) {
            repo.scheduleIntervalMinutes = shortest
        }
        if (anyEnabled) {
            checkAndPromptBatteryOptimization()
            RingScheduledService.start(context)
        } else {
            if (!isKeepConnected) {
                RingScheduledService.stop(context)
            }
        }
    }

    if (showBatteryPromptDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = NeonEmerald
                )
            },
            title = {
                Text("Samsung / Android Battery Setting")
            },
            text = {
                Text("Samsung One UI and Android power managers put background processes into deep sleep. To keep Galaxy Ring connected and run scheduled checks reliably when the screen is locked, exclude the app from battery restrictions.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryPromptDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val appIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(appIntent)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Exclude App", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatteryPromptDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Section Card: Admin / Debug (Clearly visible, single tap)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_debug_top_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCyan.copy(alpha = 0.12f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.45f))
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
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Admin / Debug",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "View BLE logs, export for troubleshooting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onOpenAdmin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_admin_panel_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Admin Panel",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        // 1. Connection Keep-Alive & Pairing Section
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Keep ring connected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hold GATT connection via foreground service",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isKeepConnected,
                            onCheckedChange = { enabled ->
                                isKeepConnected = enabled
                                repo.isKeepConnected = enabled
                                if (enabled) {
                                    checkAndPromptBatteryOptimization()
                                    RingScheduledService.start(context)
                                } else {
                                    val anyEnabled = hrConfig.enabled || spo2Config.enabled || hrvConfig.enabled || stressConfig.enabled
                                    if (!anyEnabled) {
                                        RingScheduledService.stop(context)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("keep_connected_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0F172A), checkedTrackColor = CyberCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = ElectricViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Pair / bond ring",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "OS-level bonding for faster reconnection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isBondingEnabled,
                            onCheckedChange = { enabled ->
                                isBondingEnabled = enabled
                                repo.isBondingEnabled = enabled
                            },
                            modifier = Modifier.testTag("bonding_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0F172A), checkedTrackColor = ElectricViolet)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Note: Pairing improves reconnection; keeping the link while the app is closed requires the foreground service notification ('Keep ring connected').",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1b. Screen Brightness Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Screen brightness",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Current: ${selectedBrightness.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenBrightness.values().forEach { brightness ->
                            val selected = selectedBrightness == brightness
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedBrightness = brightness
                                    monitoringSettings.screenBrightness = brightness
                                    scope.launch {
                                        val ok = app.bleRepository.setScreenBrightness(brightness)
                                        if (!ok && app.bleRepository.connectionState.value is ConnectionState.Ready) {
                                            brightnessStatusMessage = "No response from ring for brightness command — export Admin logs"
                                        } else {
                                            brightnessStatusMessage = null
                                        }
                                    }
                                },
                                label = { Text(brightness.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("brightness_chip_${brightness.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = Color(0xFF0F172A)
                                )
                            )
                        }
                    }

                    if (brightnessStatusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = brightnessStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = RosePulse
                        )
                    }
                }
            }
        }

        // 2. Health Monitoring Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Health Monitoring",
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. All-day Heart Rate
                    HealthMonitoringMetricRow(
                        metric = MonitorMetric.HEART_RATE,
                        config = hrConfig,
                        onEnabledChange = { enabled ->
                            monitoringSettings.setEnabled(MonitorMetric.HEART_RATE, enabled)
                            hrConfig = monitoringSettings.config(MonitorMetric.HEART_RATE)
                            updateMonitoringService()
                        },
                        onIntervalChange = { minutes ->
                            monitoringSettings.setIntervalMin(MonitorMetric.HEART_RATE, minutes)
                            hrConfig = monitoringSettings.config(MonitorMetric.HEART_RATE)
                            updateMonitoringService()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Regular Blood oxygen
                    HealthMonitoringMetricRow(
                        metric = MonitorMetric.SPO2,
                        config = spo2Config,
                        onEnabledChange = { enabled ->
                            monitoringSettings.setEnabled(MonitorMetric.SPO2, enabled)
                            spo2Config = monitoringSettings.config(MonitorMetric.SPO2)
                            updateMonitoringService()
                        },
                        onIntervalChange = { minutes ->
                            monitoringSettings.setIntervalMin(MonitorMetric.SPO2, minutes)
                            spo2Config = monitoringSettings.config(MonitorMetric.SPO2)
                            updateMonitoringService()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Regular HRV
                    HealthMonitoringMetricRow(
                        metric = MonitorMetric.HRV,
                        config = hrvConfig,
                        onEnabledChange = { enabled ->
                            monitoringSettings.setEnabled(MonitorMetric.HRV, enabled)
                            hrvConfig = monitoringSettings.config(MonitorMetric.HRV)
                            updateMonitoringService()
                        },
                        onIntervalChange = { minutes ->
                            monitoringSettings.setIntervalMin(MonitorMetric.HRV, minutes)
                            hrvConfig = monitoringSettings.config(MonitorMetric.HRV)
                            updateMonitoringService()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Scheduled Stress
                    HealthMonitoringMetricRow(
                        metric = MonitorMetric.STRESS,
                        config = stressConfig,
                        onEnabledChange = { enabled ->
                            monitoringSettings.setEnabled(MonitorMetric.STRESS, enabled)
                            stressConfig = monitoringSettings.config(MonitorMetric.STRESS)
                            updateMonitoringService()
                        },
                        onIntervalChange = { minutes ->
                            monitoringSettings.setIntervalMin(MonitorMetric.STRESS, minutes)
                            stressConfig = monitoringSettings.config(MonitorMetric.STRESS)
                            updateMonitoringService()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Last Run info
                    Text(
                        text = "Last Scheduled Run",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val lastRunTime = if (repo.lastScheduleRunTimestamp > 0) {
                        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(repo.lastScheduleRunTimestamp))
                    } else {
                        "Never run yet"
                    }
                    Text(
                        text = "Time: $lastRunTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (repo.lastScheduleRunResult != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Result: ${repo.lastScheduleRunResult}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (repo.lastScheduleRunResult?.contains("Error") == true || repo.lastScheduleRunResult?.contains("skipped") == true) RosePulse else NeonEmerald
                        )
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
            val healthWriter = app.healthConnectWriter
            val lastWriteTime by healthWriter.lastWriteTimeFlow.collectAsState()
            val lastWriteError by healthWriter.lastWriteErrorFlow.collectAsState()
            val lastWriteStatus by healthWriter.lastWriteStatusFlow.collectAsState()

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
                                    text = "Health Connect Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasHealthPermissions) "Permitted: Yes (All permissions granted)" else "Permitted: No (Permissions required)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasHealthPermissions) NeonEmerald else RosePulse
                                )
                            }
                        }

                        if (hasHealthPermissions) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!hasHealthPermissions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RosePulse.copy(alpha = 0.12f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Health Connect permission required to write HR, SpO₂, steps, and sleep records to Android Health Connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestHealthPermissions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_health_connect_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                            ) {
                                Text(
                                    text = "Connect Health Connect",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Status Breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Permission granted:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (hasHealthPermissions) "Granted" else "Denied",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasHealthPermissions) NeonEmerald else RosePulse
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last write time:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val timeText = if (lastWriteTime != null && lastWriteTime!! > 0L) {
                                SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).format(Date(lastWriteTime!!))
                            } else {
                                "No writes yet"
                            }
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last write result:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastWriteStatus,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lastWriteTime != null) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last error:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastWriteError ?: "None",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lastWriteError != null) RosePulse else NeonEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Synchronizes steps, active and resting heart rate, oxygen saturation (SpO₂), skin temperature, and sleep stages directly with Android Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Helpful FW Note for Sleep
                    Text(
                        text = "Tip: If sleep is empty after overnight wear, open official RWFit once after a night, then sync here (some ring firmware only exposes sleep cache after vendor app handshake).",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan.copy(alpha = 0.9f)
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

        // App version info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Galaxy Ring Companion v1.0",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Dedicated companion for Galaxy Ring BLE peripherals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun HealthMonitoringMetricRow(
    metric: MonitorMetric,
    config: MonitorConfig,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    val intervalStr = if (config.intervalMin % 60 == 0) "${config.intervalMin / 60}h" else "${config.intervalMin}m"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (config.enabled) "Every $intervalStr" else "Disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (config.enabled) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("monitor_${metric.prefKey}_toggle"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF0F172A),
                    checkedTrackColor = CyberCyan
                )
            )
        }

        if (config.enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MonitoringSchedule.INTERVAL_OPTIONS_MIN.forEach { minutes ->
                    val selected = config.intervalMin == minutes
                    val label = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"
                    FilterChip(
                        selected = selected,
                        onClick = { onIntervalChange(minutes) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan,
                            selectedLabelColor = Color(0xFF0F172A)
                        )
                    )
                }
            }
        }
    }
}

