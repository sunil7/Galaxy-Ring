package com.galaxy.ring.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.data.ConnectionState
import com.galaxy.ring.data.ManualMeasurementState
import com.galaxy.ring.debug.AppLog
import com.galaxy.ring.debug.LogEntry
import com.galaxy.ring.ui.theme.CyberCyan
import com.galaxy.ring.ui.theme.ElectricViolet
import com.galaxy.ring.ui.theme.NeonEmerald
import com.galaxy.ring.ui.theme.RosePulse
import com.galaxy.ring.ui.theme.ShapeMedium
import com.galaxy.ring.ui.theme.ShapeSmall
import com.galaxy.ring.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = GalaxyRingApp.instance
    val bleRepo = app.bleRepository
    val bleManager = bleRepo.bleManager

    val connectionState by bleRepo.connectionState.collectAsState()
    val measurementState by bleRepo.manualMeasurementState.collectAsState()
    val allLogs by AppLog.logsFlow.collectAsState()
    val rxCountSinceConnect by bleManager.rxCountFlow.collectAsState()
    val allTxRecords by AppLog.txFlow.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") } // "All", "BLE", "Sync", "Error"
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showDeduplicateDialog by remember { mutableStateOf(false) }

    val filteredLogs = remember(allLogs, selectedFilter) {
        val list = when (selectedFilter) {
            "BLE" -> allLogs.filter { it.tag.contains("BLE", ignoreCase = true) }
            "Sync" -> allLogs.filter { it.tag.contains("Sync", ignoreCase = true) }
            "Sleep" -> allLogs.filter { it.tag.contains("Sleep", ignoreCase = true) }
            "Error" -> allLogs.filter { it.level == "E" }
            else -> allLogs
        }
        // Reverse chronological (newest on top)
        list.asReversed()
    }

    val connDesc = when (val c = connectionState) {
        is ConnectionState.Ready -> "Ready (${if (c.device.name.contains("SR16", ignoreCase = true)) "Galaxy Ring" else c.device.name})"
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Connecting -> "Connecting to ${if (c.deviceName.contains("SR16", ignoreCase = true)) "Galaxy Ring" else c.deviceName}"
        is ConnectionState.Initializing -> "Initializing (${c.currentStep}/${c.totalSteps})"
        is ConnectionState.Syncing -> "Syncing (${c.message})"
        is ConnectionState.Scanning -> "Scanning"
        is ConnectionState.Error -> "Error: ${c.message}"
        is ConnectionState.Disconnected -> "Disconnected"
    }

    if (showDeduplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDeduplicateDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = CyberCyan
                )
            },
            title = {
                Text(
                    text = "Deduplicate History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This action removes duplicate samples within 60 seconds from this app's local database only and does NOT change Health Connect data.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeduplicateDialog = false
                        scope.launch {
                            val (hrRemoved, spo2Removed) = app.healthRepository.deduplicateHistory()
                            Toast.makeText(
                                context,
                                "Deduplicated local history: removed $hrRemoved HR and $spo2Removed SpO₂ duplicate samples",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("admin_dedup_confirm_button"),
                    shape = ShapeSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Deduplicate", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeduplicateDialog = false },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("admin_dedup_cancel_button"),
                    shape = ShapeSmall
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = RosePulse
                )
            },
            title = {
                Text(
                    text = "Clear Local History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This action removes all recorded samples from this app's local database only and does NOT change Health Connect data.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        scope.launch {
                            app.healthRepository.clearAllHistory()
                            Toast.makeText(context, "Local Room database history cleared", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("admin_clear_history_confirm_button"),
                    shape = ShapeSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = RosePulse)
                ) {
                    Text("Clear Local History", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearHistoryDialog = false },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("admin_clear_history_cancel_button"),
                    shape = ShapeSmall
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Admin / Debug Panel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Log buffer active (${allLogs.size} lines)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("admin_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyberCyan
                        )
                    }
                    IconButton(
                        onClick = {
                            exportAndShareLogs(context, connDesc, bleRepo)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("admin_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export & Share",
                            tint = CyberCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Connection & Device Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = "1. Connection Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "State:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = connDesc,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (connectionState is ConnectionState.Ready) NeonEmerald else CyberCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xxs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last Device:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${bleRepo.currentGattName ?: "None"} (${bleRepo.currentGattAddress ?: "None"})",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xxs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RX Count Since Connect:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (rxCountSinceConnect > 0) "$rxCountSinceConnect frames" else "0 (No response yet)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (rxCountSinceConnect > 0) NeonEmerald else Color(0xFFF59E0B)
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xxs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Negotiated MTU:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${AppLog.mtuNegotiated} bytes",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xxs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last RX Frame:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = bleManager.lastRxHex ?: "None yet",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (bleManager.lastRxHex != null) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xxs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Protocol Framing:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "RWfit (AB 01 len2 crc2)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonEmerald
                            )
                        }

                        if (bleManager.lastFailedInitStep != null) {
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Last Failed Init Step:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = bleManager.lastFailedInitStep ?: "None",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }

            // 2. GATT Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = "2. GATT Discovery Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        GattRow(
                            label = "Service 0xA00A found",
                            found = bleManager.foundService0xA00A,
                            uuid = "0000A00A-0000-1000-8000-00805F9B34FB"
                        )
                        GattRow(
                            label = "Write 0xB002 found",
                            found = bleManager.foundWrite0xB002,
                            uuid = "0000B002-0000-1000-8000-00805F9B34FB"
                        )
                        GattRow(
                            label = "Notify 0xB003 found",
                            found = bleManager.foundNotify0xB003,
                            uuid = "0000B003-0000-1000-8000-00805F9B34FB"
                        )
                        GattRow(
                            label = "CCCD Notifications active",
                            found = bleManager.isNotificationEnabled,
                            uuid = "Subscribed to 0xB003 (CCCD 0x2902)"
                        )
                    }
                }
            }

            // 3. Last Measurement Result Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = "3. Last Measurement Result",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        val resultText = when (val m = measurementState) {
                            is ManualMeasurementState.Success -> "${m.metric}: ${m.displayValue} (Success)"
                            is ManualMeasurementState.Error -> "${m.metric}: Error - ${m.message}"
                            is ManualMeasurementState.Measuring -> "Currently measuring ${m.metric} (${(m.progress * 100).toInt()}%)"
                            is ManualMeasurementState.Idle -> AppLog.lastMeasureResult ?: "No measurement run yet"
                        }

                        val isErr = measurementState is ManualMeasurementState.Error
                        val isSucc = measurementState is ManualMeasurementState.Success

                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isErr) RosePulse else if (isSucc) NeonEmerald else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Short TX / RX snippet
                        val txSnippet = AppLog.lastTxHex ?: "(None)"
                        val rxSnippet = AppLog.lastRxHex ?: "(None)"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeSmall)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(Spacing.sm)
                        ) {
                            Text(
                                text = "TX: $txSnippet",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = "RX: $rxSnippet",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = NeonEmerald
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "Last TX/RX", AppLog.getLastTxRxSnippet())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("admin_copy_last_tx_rx_button"),
                            shape = ShapeSmall
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("Copy Last TX/RX Only", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // 4. Last 20 TX Hex Frames Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "4. Last 20 TX Frames",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Text(
                                text = "${allTxRecords.size} Total TX",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        val recentTx = allTxRecords.takeLast(20).asReversed()
                        if (recentTx.isEmpty()) {
                            Text(
                                text = "No transmissions logged in this session yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                recentTx.forEach { tx ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(ShapeSmall)
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(Spacing.sm)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${tx.formattedTime} [${tx.writeType}]",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = CyberCyan
                                            )
                                            Text(
                                                text = tx.variantDescription,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Target: ${tx.targetUuid.takeLast(8)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tx.hexString,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            OutlinedButton(
                                onClick = {
                                    val fullTxText = recentTx.joinToString("\n") { it.toLineString() }
                                    copyToClipboard(context, "Last 20 TX Hex", fullTxText)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("admin_copy_last_20_tx_button"),
                                shape = ShapeSmall
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Copy Last 20 TX Hex", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // 5. Quick Actions Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val full = AppLog.getAllLogsText()
                                    copyToClipboard(context, "Galaxy Ring Logs", full)
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("admin_copy_all_logs_button"),
                                shape = ShapeSmall,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Copy All Logs", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    exportAndShareLogs(context, connDesc, bleRepo)
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("admin_export_share_logs_button"),
                                shape = ShapeSmall,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Export / Share Logs", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    AppLog.clear()
                                    Toast.makeText(context, "Log buffer cleared", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("admin_clear_logs_button"),
                                shape = ShapeSmall
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Clear Logs")
                            }

                            OutlinedButton(
                                onClick = {
                                    showDeduplicateDialog = true
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("admin_deduplicate_history_button"),
                                shape = ShapeSmall,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Deduplicate History")
                            }

                            OutlinedButton(
                                onClick = {
                                    showClearHistoryDialog = true
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("admin_clear_history_button"),
                                shape = ShapeSmall,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RosePulse)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Clear Local History")
                            }
                        }
                    }
                }
            }

            // 5. Live Log List Card with Filter Chips
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Log Buffer (${filteredLogs.size}/${allLogs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Newest first",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            val filters = listOf("All", "BLE", "Sync", "Sleep", "Error")
                            filters.forEach { f ->
                                val isSelected = selectedFilter == f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedFilter = f },
                                    label = { Text(f) },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("admin_filter_chip_${f.lowercase()}"),
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

            // Log entries rendered directly as list items
            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No logs recorded yet for filter '$selectedFilter'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { entry ->
                    LogEntryRow(entry = entry)
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun GattRow(label: String, found: Boolean, uuid: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = uuid,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (found) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (found) NeonEmerald else RosePulse,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (found) "YES" else "NO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (found) NeonEmerald else RosePulse
            )
        }
    }
}

@Composable
fun LogEntryRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        "E" -> RosePulse
        "W" -> Color(0xFFF59E0B)
        "I" -> CyberCyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeSmall)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[${entry.level}/${entry.tag}]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = levelColor,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp
            )
            if (entry.throwableSnippet != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.throwableSnippet,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = RosePulse,
                    fontSize = 10.sp
                )
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
}

fun exportAndShareLogs(context: Context, connectionStateDesc: String, bleRepo: com.galaxy.ring.ble.BleRepository) {
    val gattSummary = buildString {
        append("Service 0xA00A=")
        append(bleRepo.bleManager.foundService0xA00A)
        append(", Write 0xB002=")
        append(bleRepo.bleManager.foundWrite0xB002)
        append(", Notify 0xB003=")
        append(bleRepo.bleManager.foundNotify0xB003)
        append(", CCCD Enabled=")
        append(bleRepo.bleManager.isNotificationEnabled)
    }

    val header = AppLog.generateExportHeader(
        appName = "Galaxy Ring",
        appVersion = "1.0",
        connectionState = connectionStateDesc,
        lastDevice = "${bleRepo.currentGattName ?: "None"} (${bleRepo.currentGattAddress ?: "None"})",
        gattSummary = gattSummary
    )

    val logsBody = AppLog.getAllLogsText()
    val fullExport = header + logsBody

    // Save to app cache file
    val fileDate = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    val fileName = "galaxy-ring-logs-$fileDate.txt"
    try {
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeText(fullExport)
    } catch (_: Exception) {}

    // Share via Intent
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Galaxy Ring Logs: $fileName")
        putExtra(Intent.EXTRA_TEXT, fullExport)
    }

    val chooser = Intent.createChooser(sendIntent, "Export Galaxy Ring Diagnostic Logs")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not launch share chooser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
