package com.galaxy.ring.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.ConnectionState
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.ManualMeasurementState
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.RingDevice
import com.galaxy.ring.data.RingHealthSnapshot
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.SleepStage
import com.galaxy.ring.data.SyncStatus
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRequestHealthPermissions: () -> Unit,
    onRequestBlePermissions: () -> Unit,
    onOpenRationale: () -> Unit,
    hasHealthPermissions: Boolean
) {
    val app = GalaxyRingApp.instance
    val bleRepo = app.bleRepository
    val healthWriter = app.healthConnectWriter

    val connectionState by bleRepo.connectionState.collectAsState()
    val snapshot by bleRepo.snapshot.collectAsState()
    val discoveredDevices by bleRepo.discoveredDevices.collectAsState()
    val measurementState by bleRepo.manualMeasurementState.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var syncStatus by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }
    var showScanSheet by remember { mutableStateOf(false) }
    var isFindingRing by remember { mutableStateOf(false) }
    var showAdminScreen by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState()

    LaunchedEffect(measurementState) {
        val current = measurementState
        if (current is ManualMeasurementState.Error) {
            snackbarHostState.showSnackbar(current.message)
        }
    }

    if (showAdminScreen) {
        AdminScreen(onNavigateBack = { showAdminScreen = false })
        return
    }

    Scaffold(
        topBar = {
            GalaxyRingTopBar(
                connectionState = connectionState,
                snapshot = snapshot,
                onScanClick = {
                    if (!bleRepo.hasBlePermissions()) {
                        onRequestBlePermissions()
                    } else {
                        bleRepo.startScan()
                        showScanSheet = true
                    }
                },
                onAdminClick = { showAdminScreen = true }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val items = listOf(
                    Triple(0, "Device", Icons.Default.Watch),
                    Triple(1, "History", Icons.Default.History),
                    Triple(2, "Sleep", Icons.Default.Nightlight),
                    Triple(3, "Settings", Icons.Default.Settings)
                )

                items.forEach { (index, title, icon) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = icon, contentDescription = title) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0F172A),
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    // Device / Home Dashboard
                    DeviceDashboard(
                        connectionState = connectionState,
                        snapshot = snapshot,
                        measurementState = measurementState,
                        hasHealthPermissions = hasHealthPermissions,
                        syncStatus = syncStatus,
                        isFindingRing = isFindingRing,
                        onFindMyRing = {
                            isFindingRing = true
                            bleRepo.findMyRing()
                            scope.launch {
                                snackbarHostState.showSnackbar("Pulsing ring sensor lights and haptics...")
                                kotlinx.coroutines.delay(4000)
                                isFindingRing = false
                            }
                        },
                        onConnectClick = {
                            if (!bleRepo.hasBlePermissions()) {
                                onRequestBlePermissions()
                            } else {
                                bleRepo.startScan()
                                showScanSheet = true
                            }
                        },
                        onMeasureHeartRate = {
                            bleRepo.measureHeartRate()
                        },
                        onMeasureSpo2 = {
                            bleRepo.measureOxygenSaturation()
                        },
                        onSyncNow = {
                            scope.launch {
                                syncStatus = SyncStatus.Syncing
                                try {
                                    val updated = bleRepo.requestSync()
                                    val success = healthWriter.writeSnapshot(updated)
                                    syncStatus = if (success) {
                                        SyncStatus.Success("Synced vitals to Health Connect")
                                    } else {
                                        SyncStatus.Success("Synced locally (Health Connect ready)")
                                    }
                                    snackbarHostState.showSnackbar("Vitals synchronized with Health Connect")
                                } catch (e: Exception) {
                                    syncStatus = SyncStatus.Error(e.message ?: "Sync error")
                                }
                            }
                        },
                        onRequestPermissions = onRequestHealthPermissions,
                        onOpenRationale = onOpenRationale,
                        onNavigateToSleep = { selectedTab = 2 },
                        onOpenAdmin = { showAdminScreen = true }
                    )
                }
                1 -> {
                    HistoryScreen()
                }
                2 -> {
                    SleepScreen()
                }
                3 -> {
                    SettingsScreen(
                        onOpenHealthRationale = onOpenRationale,
                        hasHealthPermissions = hasHealthPermissions,
                        onOpenAdmin = { showAdminScreen = true }
                    )
                }
            }
        }
    }

    // Modal Sheet for BLE Scanning & Device Pairing
    if (showScanSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                bleRepo.stopScan()
                showScanSheet = false
            },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ScanDevicesSheetContent(
                isScanning = connectionState is ConnectionState.Scanning,
                devices = discoveredDevices,
                onDeviceSelect = { device ->
                    if (!bleRepo.hasBlePermissions()) {
                        onRequestBlePermissions()
                    } else {
                        bleRepo.connect(device.address, device.name, onRequestPermissionsNeeded = onRequestBlePermissions)
                        showScanSheet = false
                    }
                },
                onRefresh = {
                    if (!bleRepo.hasBlePermissions()) {
                        onRequestBlePermissions()
                    } else {
                        bleRepo.startScan(onRequestPermissionsNeeded = onRequestBlePermissions)
                    }
                }
            )
        }
    }
}

@Composable
fun DeviceDashboard(
    connectionState: ConnectionState,
    snapshot: RingHealthSnapshot,
    measurementState: ManualMeasurementState,
    hasHealthPermissions: Boolean,
    syncStatus: SyncStatus,
    isFindingRing: Boolean,
    onFindMyRing: () -> Unit,
    onConnectClick: () -> Unit,
    onMeasureHeartRate: () -> Unit,
    onMeasureSpo2: () -> Unit,
    onSyncNow: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenRationale: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onOpenAdmin: () -> Unit = {}
) {
    val isReady = connectionState is ConnectionState.Ready || connectionState is ConnectionState.Connected

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Connection Status Banner
        item {
            ConnectionStatusBanner(
                connectionState = connectionState,
                onOpenAdmin = onOpenAdmin
            )
        }

        // 2. Interactive Ring Hero Card
        item {
            RingHeroCard(
                connectionState = connectionState,
                snapshot = snapshot,
                isFindingRing = isFindingRing,
                onFindMyRing = onFindMyRing,
                onConnectClick = onConnectClick
            )
        }

        // 3. Two Prominent Manual Measurement Buttons (Heart Rate & SpO₂)
        item {
            ManualMeasurementsCard(
                isReady = isReady,
                measurementState = measurementState,
                latestHeartRate = snapshot.latestHeartRate,
                latestSpo2 = snapshot.latestOxygenSaturation,
                onMeasureHeartRate = onMeasureHeartRate,
                onMeasureSpo2 = onMeasureSpo2,
                onOpenAdmin = onOpenAdmin
            )
        }

        // 4. Sleep Goal Progress Ring Banner
        item {
            SleepGoalProgressRingCard(
                snapshot = snapshot,
                onCardClick = onNavigateToSleep
            )
        }

        // 5. Health Connect Sync Card
        item {
            HealthConnectSyncCard(
                hasPermissions = hasHealthPermissions,
                syncStatus = syncStatus,
                lastSyncTime = snapshot.lastSyncTimestamp,
                onSyncNow = onSyncNow,
                onRequestPermissions = onRequestPermissions,
                onOpenRationale = onOpenRationale
            )
        }

        // 6. Heart Rate Card with Live Pulse & Waveform
        item {
            HeartRateCard(
                latestSample = snapshot.latestHeartRate,
                history = snapshot.heartRateHistory
            )
        }

        // 7. SpO₂ Blood Oxygen Card
        item {
            Spo2Card(
                latestSample = snapshot.latestOxygenSaturation,
                history = snapshot.oxygenSaturationHistory
            )
        }

        // 8. Daily Steps Card
        item {
            StepsCard(steps = snapshot.steps)
        }

        // 9. Split Grid: Skin Temp & Battery
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SkinTempCard(temp = snapshot.temperature)
                }
                Box(modifier = Modifier.weight(1f)) {
                    BatteryCard(battery = snapshot.battery)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun ConnectionStatusBanner(
    connectionState: ConnectionState,
    onOpenAdmin: () -> Unit = {}
) {
    val isZeroRxReady = connectionState is ConnectionState.Ready && connectionState.rxCountSinceConnect == 0
    val (bgColor, textColor, icon, label) = when (connectionState) {
        is ConnectionState.Ready -> {
            if (connectionState.rxCountSinceConnect == 0) {
                Quadruple(
                    Color(0xFFF59E0B).copy(alpha = 0.15f),
                    Color(0xFFF59E0B),
                    Icons.Default.Error,
                    "Connected but 0 RX from ring. Commands may be ignored. Open Admin logs."
                )
            } else {
                Quadruple(
                    NeonEmerald.copy(alpha = 0.15f),
                    NeonEmerald,
                    Icons.Default.CheckCircle,
                    "Ready – Active Telemetry (${connectionState.rxCountSinceConnect} RX received)"
                )
            }
        }
        is ConnectionState.Initializing -> Quadruple(
            CyberCyan.copy(alpha = 0.15f),
            CyberCyan,
            Icons.Default.Sync,
            "Initializing (Step ${connectionState.currentStep}/${connectionState.totalSteps})..."
        )
        is ConnectionState.Syncing -> Quadruple(
            CyberCyan.copy(alpha = 0.15f),
            CyberCyan,
            Icons.Default.Sync,
            "Syncing – ${connectionState.message}"
        )
        is ConnectionState.Connecting -> Quadruple(
            CyberCyan.copy(alpha = 0.15f),
            CyberCyan,
            Icons.Default.Bluetooth,
            "Connecting to ${connectionState.deviceName}..."
        )
        is ConnectionState.Connected -> Quadruple(
            NeonEmerald.copy(alpha = 0.15f),
            NeonEmerald,
            Icons.Default.BluetoothConnected,
            "Connected – Handshake in progress"
        )
        is ConnectionState.Scanning -> Quadruple(
            CyberCyan.copy(alpha = 0.15f),
            CyberCyan,
            Icons.Default.Refresh,
            "Scanning for Galaxy Ring / SR16..."
        )
        is ConnectionState.Error -> Quadruple(
            RosePulse.copy(alpha = 0.15f),
            RosePulse,
            Icons.Default.Error,
            connectionState.message
        )
        is ConnectionState.Disconnected -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Bluetooth,
            "Disconnected – Pair ring to start sync"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isZeroRxReady) { onOpenAdmin() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (connectionState is ConnectionState.Initializing || connectionState is ConnectionState.Syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = textColor,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ManualMeasurementsCard(
    isReady: Boolean,
    measurementState: ManualMeasurementState,
    latestHeartRate: HeartRateSample,
    latestSpo2: OxygenSaturationSample?,
    onMeasureHeartRate: () -> Unit,
    onMeasureSpo2: () -> Unit,
    onOpenAdmin: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(
                    CyberCyan.copy(alpha = 0.3f),
                    ElectricViolet.copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Manual Biometric Measurements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isReady) "Trigger real-time PPG sensor capture from ring" else "Available when connection state is READY",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Measurement Active Progress Indicator
            if (measurementState is ManualMeasurementState.Measuring) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCyan.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Measuring ${measurementState.metric}...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                        Text(
                            text = "${(measurementState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { measurementState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CyberCyan,
                        trackColor = CyberCyan.copy(alpha = 0.2f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            } else if (measurementState is ManualMeasurementState.Success) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonEmerald.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recorded ${measurementState.metric}: ${measurementState.displayValue}  (${DateFormat.format("h:mm a", Date(measurementState.timestamp))})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonEmerald
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            } else if (measurementState is ManualMeasurementState.Error) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RosePulse.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = RosePulse,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = measurementState.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = RosePulse,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "→ View TX/RX hex & export logs in Admin / Debug",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        modifier = Modifier
                            .clickable { onOpenAdmin() }
                            .padding(start = 26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Two Prominent Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isHrMeasuring = measurementState is ManualMeasurementState.Measuring && measurementState.metric == "Heart Rate"
                Button(
                    onClick = onMeasureHeartRate,
                    enabled = isReady && measurementState !is ManualMeasurementState.Measuring,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("measure_heart_rate_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RosePulse)
                ) {
                    if (isHrMeasuring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Measure HR", fontWeight = FontWeight.Bold)
                    }
                }

                val isSpo2Measuring = measurementState is ManualMeasurementState.Measuring && measurementState.metric == "SpO₂"
                Button(
                    onClick = onMeasureSpo2,
                    enabled = isReady && measurementState !is ManualMeasurementState.Measuring,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("measure_spo2_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    if (isSpo2Measuring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF0F172A), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Bloodtype, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Measure SpO₂", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SleepGoalProgressRingCard(
    snapshot: RingHealthSnapshot,
    onCardClick: () -> Unit
) {
    val app = GalaxyRingApp.instance
    val targetHours = app.healthRepository.sleepTargetHours
    val sleep = snapshot.latestSleep
    val hasSleepData = sleep != null && sleep.durationMinutes > 0

    val actualMinutes = if (hasSleepData) sleep!!.durationMinutes else 0L
    val targetMinutes = (targetHours * 60).toLong()
    val progress = if (hasSleepData && targetMinutes > 0) {
        (actualMinutes.toFloat() / targetMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val actualH = actualMinutes / 60
    val actualM = actualMinutes % 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Nightlight,
                        contentDescription = null,
                        tint = ElectricViolet,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sleep Goal Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (hasSleepData) {
                    Text(
                        text = "Last night: ${actualH}h ${actualM}m / ${targetHours.toInt()}h target",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Quality score: ${sleep?.qualityScore ?: 0}/100 • Restorative sleep",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricViolet
                    )
                } else {
                    Text(
                        text = "No sleep data yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Target: ${targetHours.toInt()}h • Wear ring overnight to track sleep",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Circular Progress Ring
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = (size.minDimension - stroke) / 2

                    // Track
                    drawCircle(
                        color = ElectricViolet.copy(alpha = 0.2f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = stroke)
                    )

                    // Arc (only drawn if there is actual sleep recorded)
                    if (progress > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(CyberCyan, ElectricViolet, CyberCyan)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }

                Text(
                    text = if (hasSleepData) "${(progress * 100).toInt()}%" else "--%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasSleepData) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Spo2Card(
    latestSample: OxygenSaturationSample?,
    history: List<OxygenSaturationSample>
) {
    val hasSample = latestSample != null
    val spo2Val = if (hasSample) latestSample!!.percentage else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bloodtype,
                        contentDescription = "SpO2",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Blood Oxygen (SpO₂)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Normal: 95–100%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hasSample) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${spo2Val.toInt()}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyberCyan,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val isOptimal = spo2Val >= 95f
                    Text(
                        text = if (isOptimal) "Optimal Oxygenation" else "Sub-optimal",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOptimal) NeonEmerald else Color(0xFFF59E0B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background((if (isOptimal) NeonEmerald else Color(0xFFF59E0B)).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (spo2Val / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = CyberCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "No SpO₂ recordings yet • Tap Measure below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyRingTopBar(
    connectionState: ConnectionState,
    snapshot: RingHealthSnapshot,
    onScanClick: () -> Unit,
    onAdminClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onAdminClick() }
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Bluetooth Ring",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Galaxy Ring",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Ready -> "Ready"
                            is ConnectionState.Connected -> "Connected"
                            is ConnectionState.Initializing -> "Initializing..."
                            is ConnectionState.Syncing -> "Syncing..."
                            is ConnectionState.Scanning -> "Scanning..."
                            is ConnectionState.Connecting -> "Connecting..."
                            is ConnectionState.Error -> "Error"
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (connectionState) {
                            is ConnectionState.Ready -> NeonEmerald
                            is ConnectionState.Connected -> NeonEmerald
                            is ConnectionState.Initializing, is ConnectionState.Syncing -> CyberCyan
                            is ConnectionState.Error -> RosePulse
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        actions = {
            Button(
                onClick = onAdminClick,
                modifier = Modifier.testTag("topbar_admin_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Admin / Debug",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Admin",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            val hasBattery = snapshot.battery.timestamp != 0L || snapshot.battery.level > 0
            val batteryText = if (hasBattery) "${snapshot.battery.level}%" else "--%"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = if (snapshot.battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    contentDescription = "Battery",
                    tint = if (hasBattery && snapshot.battery.level > 20) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = batteryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onScanClick,
                modifier = Modifier.testTag("scan_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.BluetoothSearching,
                    contentDescription = "Pair Ring",
                    tint = CyberCyan
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun RingHeroCard(
    connectionState: ConnectionState,
    snapshot: RingHealthSnapshot,
    isFindingRing: Boolean,
    onFindMyRing: () -> Unit,
    onConnectClick: () -> Unit
) {
    val isConnected = connectionState is ConnectionState.Ready || connectionState is ConnectionState.Connected || connectionState is ConnectionState.Initializing || connectionState is ConnectionState.Syncing

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_glow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    CyberCyan.copy(alpha = 0.4f),
                    ElectricViolet.copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = (size.minDimension / 2) - 16.dp.toPx()

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isConnected) CyberCyan.copy(alpha = 0.25f * pulseScale) else Color.Transparent,
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 1.4f
                        )
                    )

                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF64748B),
                                Color(0xFFE2E8F0),
                                Color(0xFF334155),
                                Color(0xFF94A3B8),
                                Color(0xFF64748B)
                            ),
                            center = center
                        ),
                        radius = radius,
                        style = Stroke(width = 16.dp.toPx())
                    )

                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = radius - 8.dp.toPx(),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    val sensorCount = 3
                    for (i in 0 until sensorCount) {
                        val angle = (i * (360f / sensorCount) + 30) * (Math.PI / 180.0)
                        val sensorR = radius - 4.dp.toPx()
                        val sensorCenter = Offset(
                            (center.x + sensorR * Math.cos(angle)).toFloat(),
                            (center.y + sensorR * Math.sin(angle)).toFloat()
                        )
                        drawCircle(
                            color = if (isConnected || isFindingRing) NeonEmerald else Color(0xFF475569),
                            radius = 4.dp.toPx(),
                            center = sensorCenter
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isConnected) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isConnected) "SR16 Active" else "No Ring",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isConnected) "Galaxy Ring / SR16 Smart Ring" else "Galaxy Ring Not Connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isConnected) "Optical PPG & Temperature Sensors Active" else "Pair your ring to stream real-time biometric metrics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isConnected) {
                    FilledTonalButton(
                        onClick = onFindMyRing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("find_my_ring_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isFindingRing) Icons.Default.NotificationsActive else Icons.Default.Vibration,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isFindingRing) "Pulsing..." else "Find Ring")
                    }
                } else {
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_ring_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Ring", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HealthConnectSyncCard(
    hasPermissions: Boolean,
    syncStatus: SyncStatus,
    lastSyncTime: Long,
    onSyncNow: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenRationale: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(NeonEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Health Connect Sync",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val formattedTime = DateFormat.format("h:mm a", Date(lastSyncTime))
                        Text(
                            text = "Last synced: $formattedTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!hasPermissions) {
                    FilledTonalButton(
                        onClick = onRequestPermissions,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CyberCyan.copy(alpha = 0.2f))
                    ) {
                        Text("Grant", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onSyncNow,
                        enabled = syncStatus !is SyncStatus.Syncing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        if (syncStatus is SyncStatus.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF0F172A),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!hasPermissions) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRationale() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Why Galaxy Ring needs Health Connect permissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun HeartRateCard(
    latestSample: HeartRateSample,
    history: List<HeartRateSample>
) {
    val hasHr = latestSample.bpm > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Heart Rate",
                        tint = RosePulse,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Heart Rate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (hasHr) "Resting PPG" else "No Data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hasHr) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${latestSample.bpm}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BPM",
                        style = MaterialTheme.typography.titleMedium,
                        color = RosePulse,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Zone: Resting",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BPM",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No heart rate readings yet • Tap Measure below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (history.size < 2) return@Canvas
                    val minBpm = (history.minOfOrNull { it.bpm } ?: 60).toFloat() - 5f
                    val maxBpm = (history.maxOfOrNull { it.bpm } ?: 90).toFloat() + 5f
                    val bpmRange = (maxBpm - minBpm).coerceAtLeast(10f)

                    val stepX = size.width / (history.size - 1)
                    val path = Path()
                    val fillPath = Path()

                    history.forEachIndexed { index, sample ->
                        val x = index * stepX
                        val normY = 1f - ((sample.bpm.toFloat() - minBpm) / bpmRange)
                        val y = normY * (size.height - 12.dp.toPx()) + 6.dp.toPx()

                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }

                        if (index == history.lastIndex) {
                            fillPath.lineTo(x, size.height)
                            fillPath.close()
                        }
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RosePulse.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )

                    drawPath(
                        path = path,
                        color = RosePulse,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun StepsCard(steps: com.galaxy.ring.data.StepData) {
    val progress = (steps.totalSteps.toFloat() / steps.goalSteps.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = "Steps",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Steps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Goal: ${steps.goalSteps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format("%,d", steps.totalSteps),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "steps",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberCyan,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = CyberCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(title = "Calories", value = "${steps.caloriesKcal} kcal")
                MetricColumn(title = "Distance", value = String.format("%.2f km", steps.distanceMeters / 1000.0))
                MetricColumn(title = "Progress", value = "${(progress * 100).toInt()}%")
            }
        }
    }
}

@Composable
fun SkinTempCard(temp: com.galaxy.ring.data.SkinTemperature) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = "Skin Temperature",
                tint = CyberCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${temp.temperatureCelsius}°C",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Skin Temp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            val deltaStr = if (temp.baselineDelta >= 0) "+${temp.baselineDelta}°C" else "${temp.baselineDelta}°C"
            Text(
                text = "$deltaStr from baseline",
                style = MaterialTheme.typography.labelSmall,
                color = NeonEmerald
            )
        }
    }
}

@Composable
fun BatteryCard(battery: com.galaxy.ring.data.RingBattery) {
    val hasBattery = battery.timestamp != 0L || battery.level > 0

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = if (battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                contentDescription = "Battery",
                tint = if (hasBattery && battery.level > 20) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (hasBattery) "${battery.level}%" else "--%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Ring Battery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (!hasBattery) {
                    "Awaiting ring status"
                } else if (battery.isCharging) {
                    "Charging now"
                } else {
                    "~${battery.estimatedDaysLeft.toInt()} days left"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (hasBattery) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricColumn(title: String, value: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ScanDevicesSheetContent(
    isScanning: Boolean,
    devices: List<RingDevice>,
    onDeviceSelect: (RingDevice) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Smart Rings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Searching for SR16 / Galaxy Ring..." else "No devices found. Tap refresh to scan again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { dev ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceSelect(dev) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val safeDisplayName = try {
                                    dev.name.ifEmpty { dev.address }
                                } catch (_: Exception) {
                                    dev.address
                                }
                                Text(
                                    text = safeDisplayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = dev.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${dev.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
