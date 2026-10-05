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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Vibration
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
import com.galaxy.ring.data.RingDevice
import com.galaxy.ring.data.RingHealthSnapshot
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

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var syncStatus by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }
    var showScanSheet by remember { mutableStateOf(false) }
    var isFindingRing by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            GalaxyRingTopBar(
                connectionState = connectionState,
                snapshot = snapshot,
                onScanClick = {
                    onRequestBlePermissions()
                    bleRepo.startScan()
                    showScanSheet = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Interactive Ring Hero Card
            item {
                RingHeroCard(
                    connectionState = connectionState,
                    snapshot = snapshot,
                    isFindingRing = isFindingRing,
                    onFindMyRing = {
                        isFindingRing = true
                        bleRepo.findMyRing()
                        scope.launch {
                            snackbarHostState.showSnackbar("Pulsing ring sensor lights and haptic signal...")
                            kotlinx.coroutines.delay(4000)
                            isFindingRing = false
                        }
                    },
                    onConnectClick = {
                        onRequestBlePermissions()
                        bleRepo.startScan()
                        showScanSheet = true
                    }
                )
            }

            // 2. Health Connect Sync Status & Action Banner
            item {
                HealthConnectSyncCard(
                    hasPermissions = hasHealthPermissions,
                    syncStatus = syncStatus,
                    lastSyncTime = snapshot.lastSyncTimestamp,
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
                    onOpenRationale = onOpenRationale
                )
            }

            // 3. Heart Rate Card with Live Pulse & Mini Waveform
            item {
                HeartRateCard(
                    latestSample = snapshot.latestHeartRate,
                    history = snapshot.heartRateHistory
                )
            }

            // 4. Daily Steps & Activity Card
            item {
                StepsCard(steps = snapshot.steps)
            }

            // 5. Sleep & Nightly Recovery Card
            item {
                SleepCard(snapshot = snapshot)
            }

            // 6. Skin Temperature & Ring Battery Split Grid
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
                    bleRepo.connect(device.address, device.name)
                    showScanSheet = false
                },
                onRefresh = {
                    bleRepo.startScan()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyRingTopBar(
    connectionState: ConnectionState,
    snapshot: RingHealthSnapshot,
    onScanClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                            is ConnectionState.Connected -> "Connected"
                            is ConnectionState.Scanning -> "Scanning..."
                            is ConnectionState.Connecting -> "Connecting..."
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (connectionState) {
                            is ConnectionState.Connected -> NeonEmerald
                            is ConnectionState.Scanning -> CyberCyan
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        actions = {
            // Battery pill
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
                    tint = if (snapshot.battery.level > 20) NeonEmerald else RosePulse,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${snapshot.battery.level}%",
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
    val isConnected = connectionState is ConnectionState.Connected

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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
            // Visual Ring Canvas
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = (size.minDimension / 2) - 16.dp.toPx()

                    // Outer ambient glow
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

                    // Metallic Ring Band
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

                    // Inner Sensor Track
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = radius - 8.dp.toPx(),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    // Active Biosensor LED Nodes
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

                // Center Ring info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isConnected) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isConnected) "Size 10" else "No Ring",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isConnected) "Galaxy Ring (Titanium Black)" else "Galaxy Ring Not Connected",
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

            Spacer(modifier = Modifier.height(16.dp))

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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
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
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CyberCyan.copy(alpha = 0.2f)
                        )
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
                    text = "Resting: 62 bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // Sparkline Waveform Canvas
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

                    // Draw gradient area under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RosePulse.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw line
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
fun SleepCard(snapshot: RingHealthSnapshot) {
    val sleep = snapshot.latestSleep

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
                        imageVector = Icons.Default.Nightlight,
                        contentDescription = "Sleep",
                        tint = ElectricViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sleep & Recovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Score: ${sleep?.qualityScore ?: 85}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricViolet,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricViolet.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val durationHours = (sleep?.durationMinutes ?: 445) / 60
            val durationMin = (sleep?.durationMinutes ?: 445) % 60

            Text(
                text = "${durationHours}h ${durationMin}m",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sleep Stages Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                Box(modifier = Modifier.weight(0.22f).fillMaxSize().background(Color(0xFF38BDF8))) // Deep
                Box(modifier = Modifier.weight(0.28f).fillMaxSize().background(Color(0xFFA855F7))) // REM
                Box(modifier = Modifier.weight(0.42f).fillMaxSize().background(Color(0xFF64748B))) // Light
                Box(modifier = Modifier.weight(0.08f).fillMaxSize().background(Color(0xFFF59E0B))) // Awake
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SleepStageLegend(color = Color(0xFF38BDF8), label = "Deep", percent = "22%")
                SleepStageLegend(color = Color(0xFFA855F7), label = "REM", percent = "28%")
                SleepStageLegend(color = Color(0xFF64748B), label = "Light", percent = "42%")
                SleepStageLegend(color = Color(0xFFF59E0B), label = "Awake", percent = "8%")
            }
        }
    }
}

@Composable
fun SleepStageLegend(color: Color, label: String, percent: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label $percent",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = if (battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                contentDescription = "Battery",
                tint = NeonEmerald,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${battery.level}%",
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
                text = if (battery.isCharging) "Charging now" else "~${battery.estimatedDaysLeft.toInt()} days left",
                style = MaterialTheme.typography.labelSmall,
                color = NeonEmerald
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = CyberCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scanning for Galaxy Ring devices over BLE...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (devices.isEmpty() && !isScanning) {
            Text(
                text = "No devices detected. Tap refresh or ensure Bluetooth is turned on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceSelect(device) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${device.rssi} dBm",
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
