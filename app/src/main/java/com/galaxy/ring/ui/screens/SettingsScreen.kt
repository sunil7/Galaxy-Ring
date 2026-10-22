package com.galaxy.ring.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.ConnectionState
import com.galaxy.ring.data.MonitorConfig
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSchedule
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.data.ScreenBrightness
import com.galaxy.ring.sync.RingScheduledService
import com.galaxy.ring.ui.components.SectionHeader
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse
import com.galaxy.ring.ui.theme.ShapeMedium
import com.galaxy.ring.ui.theme.ShapeSmall
import com.galaxy.ring.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenHealthRationale: () -> Unit,
    hasHealthPermissions: Boolean,
    onRequestHealthPermissions: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    onAdminClick: () -> Unit = onOpenAdmin
) {
    val context = LocalContext.current
    val app = GalaxyRingApp.instance
    val repo = app.healthRepository
    val scope = rememberCoroutineScope()

    var isKeepConnected by remember { mutableStateOf(repo.isKeepConnected) }
    var isBondingEnabled by remember { mutableStateOf(repo.isBondingEnabled) }
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
                Text("Background Battery Setting")
            },
            text = {
                Text("System power managers may pause background processes. To keep Galaxy Ring connected and run scheduled checks reliably when the screen is locked, exclude the app from battery restrictions.")
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Exclude App", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBatteryPromptDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Later")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { Spacer(modifier = Modifier.height(Spacing.xxs)) }

        // Section: Connection & Pairing
        item {
            SectionHeader(
                title = "Connection & Pairing",
                subtitle = "Manage BLE GATT link persistence and OS bonding"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    // Keep ring connected row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                            modifier = Modifier
                                .testTag("keep_connected_toggle")
                                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = CyberCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Pair / bond ring row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = ElectricViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                            modifier = Modifier
                                .testTag("bonding_toggle")
                                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = ElectricViolet
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "Note: Pairing improves reconnection; keeping the link while the app is closed requires the foreground service notification ('Keep ring connected').",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section: Display
        item {
            SectionHeader(
                title = "Display",
                subtitle = "Ring indicator brightness level"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                                modifier = Modifier
                                    .testTag("brightness_chip_${brightness.name.lowercase()}")
                                    .defaultMinSize(minHeight = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    if (brightnessStatusMessage != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
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

        // Section: Health Monitoring
        item {
            SectionHeader(
                title = "Health Monitoring",
                subtitle = "Automated background biometric logging schedules"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Automated Checks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Configurable measurement intervals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

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

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(Spacing.sm))

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

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(Spacing.sm))

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

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(Spacing.sm))

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

                    Spacer(modifier = Modifier.height(Spacing.md))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Last Run info
                    Text(
                        text = "Last Scheduled Run",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
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

        // Section: Breathing Symptoms
        item {
            SectionHeader(
                title = "Breathing Risk Factors",
                subtitle = "Self-reported symptoms for nocturnal breathing assessment"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Text(
                        text = "These indicators are used as weighted factors alongside your Galaxy Ring nocturnal SpO₂ and sleep continuity metrics in the Overnight Breathing Indicators screening card:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
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
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Habitual or loud snoring",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
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
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Excessive daytime tiredness / somnolence",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
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
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Observed breathing pauses or gasping during sleep",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Section: System Power Management
        item {
            SectionHeader(
                title = "Power Management",
                subtitle = "Battery restriction exclusion for background tasks"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Text(
                        text = "Android and manufacturer power managers put background processes into deep sleep. To keep scheduled checks running reliably when the screen is locked, exclude Galaxy Ring from battery restrictions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    OutlinedButton(
                        onClick = {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = ShapeSmall
                    ) {
                        Icon(imageVector = Icons.Outlined.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(if (isIgnoringBatteryOptimizations) "Battery Settings (Active)" else "Exclude from Battery Restrictions")
                    }
                }
            }
        }

        // Section: Health Connect
        item {
            SectionHeader(
                title = "Health Connect",
                subtitle = "Sync biometric readings with on-device Health Connect"
            )
        }
        item {
            val healthWriter = app.healthConnectWriter
            val lastWriteTime by healthWriter.lastWriteTimeFlow.collectAsState()
            val lastWriteError by healthWriter.lastWriteErrorFlow.collectAsState()
            val lastWriteStatus by healthWriter.lastWriteStatusFlow.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = ElectricViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    if (!hasHealthPermissions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeSmall)
                                .background(RosePulse.copy(alpha = 0.12f))
                                .padding(Spacing.sm)
                        ) {
                            Text(
                                text = "Health Connect permission required to write HR, SpO₂, steps, and sleep records to Android Health Connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Button(
                                onClick = onRequestHealthPermissions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("connect_health_connect_button"),
                                shape = ShapeSmall,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Connect Health Connect",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }

                    // Status Breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs + 2.dp)
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

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "Synchronizes steps, active and resting heart rate, oxygen saturation (SpO₂), skin temperature, and sleep stages directly with Android Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "Tip: If sleep is empty after overnight wear, open official companion once after a night, then sync here (some ring firmware only exposes sleep cache after vendor handshake).",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onOpenHealthRationale() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "View Permissions Rationale & Data Privacy",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }
                }
            }
        }

        // Section: More (Admin & Diagnostics strictly under More)
        item {
            SectionHeader(
                title = "More",
                subtitle = "Diagnostics, BLE logs, and advanced tools"
            )
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_debug_card"),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Admin / Diagnostics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "View BLE logs, local history database, export for troubleshooting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Button(
                        onClick = onAdminClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("open_admin_panel_button"),
                        shape = ShapeSmall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Open Admin Panel",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        // App version info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeSmall,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
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

        item { Spacer(modifier = Modifier.height(Spacing.lg)) }
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
                modifier = Modifier
                    .testTag("monitor_${metric.prefKey}_toggle")
                    .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = CyberCyan
                )
            )
        }

        if (config.enabled) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                MonitoringSchedule.INTERVAL_OPTIONS_MIN.forEach { minutes ->
                    val selected = config.intervalMin == minutes
                    val label = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"
                    FilterChip(
                        selected = selected,
                        onClick = { onIntervalChange(minutes) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}
