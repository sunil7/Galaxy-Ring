package com.galaxy.ring.debug

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic log entry recorded by [AppLog].
 */
data class LogEntry(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "D", "I", "W", "E"
    val tag: String,
    val message: String,
    val throwableSnippet: String? = null
) {
    val formattedTime: String by lazy {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        sdf.format(Date(timestamp))
    }

    fun toLineString(): String {
        val base = "$formattedTime [$level/$tag] $message"
        return if (throwableSnippet != null) "$base\n$throwableSnippet" else base
    }
}

/**
 * Record of an outgoing BLE transmission frame.
 */
data class TxRecord(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val targetUuid: String,
    val writeType: String,
    val variantDescription: String,
    val hexString: String
) {
    val formattedTime: String by lazy {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        sdf.format(Date(timestamp))
    }

    fun toLineString(): String = "$formattedTime [TX -> $targetUuid | $writeType | $variantDescription] $hexString"
}

/**
 * Record of an incoming BLE characteristic notification/indication.
 */
data class RxRecord(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceUuid: String,
    val hexString: String
) {
    val formattedTime: String by lazy {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        sdf.format(Date(timestamp))
    }

    fun toLineString(): String = "$formattedTime [RX <- $sourceUuid] $hexString"
}

/**
 * In-app logging system with thread-safe circular ring buffer (2000 lines),
 * dual-writing to Android logcat and exposing observable StateFlow for Admin/Debug UI.
 */
object AppLog {

    private const val MAX_ENTRIES = 2000
    private const val MAX_TX_RX_HISTORY = 200
    private var nextId = 0L
    private var nextTxId = 0L
    private var nextRxId = 0L

    private val entriesLock = Any()
    private val entriesDeque = ArrayDeque<LogEntry>(MAX_ENTRIES + 16)
    private val txDeque = ArrayDeque<TxRecord>(MAX_TX_RX_HISTORY + 16)
    private val rxDeque = ArrayDeque<RxRecord>(MAX_TX_RX_HISTORY + 16)

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val _txFlow = MutableStateFlow<List<TxRecord>>(emptyList())
    val txFlow: StateFlow<List<TxRecord>> = _txFlow.asStateFlow()

    private val _rxFlow = MutableStateFlow<List<RxRecord>>(emptyList())
    val rxFlow: StateFlow<List<RxRecord>> = _rxFlow.asStateFlow()

    // Key telemetry and diagnostic states
    @Volatile var lastTxHex: String? = null
    @Volatile var lastRxHex: String? = null
    @Volatile var lastError: String? = null
    @Volatile var lastMeasureResult: String? = null

    // Session statistics
    @Volatile var rxCountSinceConnect: Int = 0
    @Volatile var workingVariant: String? = null
    @Volatile var mtuNegotiated: Int = 23

    fun recordTx(targetUuid: String, writeType: String, variant: String, hexString: String) {
        lastTxHex = hexString
        val currentTxList: List<TxRecord>
        synchronized(entriesLock) {
            val rec = TxRecord(
                id = ++nextTxId,
                timestamp = System.currentTimeMillis(),
                targetUuid = targetUuid,
                writeType = writeType,
                variantDescription = variant,
                hexString = hexString
            )
            if (txDeque.size >= MAX_TX_RX_HISTORY) {
                txDeque.removeFirst()
            }
            txDeque.addLast(rec)
            currentTxList = txDeque.toList()
        }
        _txFlow.value = currentTxList
    }

    fun recordRx(sourceUuid: String, hexString: String) {
        lastRxHex = hexString
        val currentRxList: List<RxRecord>
        synchronized(entriesLock) {
            val rec = RxRecord(
                id = ++nextRxId,
                timestamp = System.currentTimeMillis(),
                sourceUuid = sourceUuid,
                hexString = hexString
            )
            if (rxDeque.size >= MAX_TX_RX_HISTORY) {
                rxDeque.removeFirst()
            }
            rxDeque.addLast(rec)
            currentRxList = rxDeque.toList()
        }
        _rxFlow.value = currentRxList
    }

    fun getLastTxRecords(count: Int = 20): List<TxRecord> {
        val list = synchronized(entriesLock) { txDeque.toList() }
        return list.takeLast(count)
    }

    fun getLastRxRecords(count: Int = 20): List<RxRecord> {
        val list = synchronized(entriesLock) { rxDeque.toList() }
        return list.takeLast(count)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        appendEntry("D", tag, message, null)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        appendEntry("I", tag, message, null)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        appendEntry("W", tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val snippet = throwable?.let {
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            it.printStackTrace(pw)
            sw.toString().take(600)
        }
        lastError = message
        appendEntry("E", tag, message, snippet)
    }

    private fun appendEntry(level: String, tag: String, message: String, snippet: String?) {
        val currentList: List<LogEntry>
        synchronized(entriesLock) {
            val entry = LogEntry(
                id = ++nextId,
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
                throwableSnippet = snippet
            )
            if (entriesDeque.size >= MAX_ENTRIES) {
                entriesDeque.removeFirst()
            }
            entriesDeque.addLast(entry)
            currentList = entriesDeque.toList()
        }
        _logsFlow.value = currentList
    }

    fun clear() {
        synchronized(entriesLock) {
            entriesDeque.clear()
            txDeque.clear()
            rxDeque.clear()
        }
        _logsFlow.value = emptyList()
        _txFlow.value = emptyList()
        _rxFlow.value = emptyList()
    }

    fun getAllLogsText(): String {
        val list = synchronized(entriesLock) { entriesDeque.toList() }
        return list.joinToString("\n") { it.toLineString() }
    }

    fun getLastTxRxSnippet(): String {
        val tx = lastTxHex ?: "(None)"
        val rx = lastRxHex ?: "(None)"
        return "Last TX: $tx\nLast RX: $rx\nRX Frames Count: $rxCountSinceConnect"
    }

    fun generateExportHeader(
        appName: String = "Galaxy Ring",
        appVersion: String = "1.0",
        connectionState: String = "Unknown",
        lastDevice: String = "None",
        gattSummary: String = "N/A"
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val exportTime = sdf.format(Date())
        val allTx = synchronized(entriesLock) { txDeque.toList() }
        val allRx = synchronized(entriesLock) { rxDeque.toList() }

        return buildString {
            appendLine("================================================================================")
            appendLine("                    GALAXY RING DIAGNOSTIC LOG EXPORT")
            appendLine("================================================================================")
            appendLine("Export Time:      $exportTime")
            appendLine("Application:      $appName v$appVersion")
            appendLine("Android OS:       Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device Hardware:  ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Connection State: $connectionState")
            appendLine("Last Device:      $lastDevice")
            appendLine("GATT Summary:     $gattSummary")
            appendLine("Negotiated MTU:   $mtuNegotiated")
            appendLine("RX Count Since Connect: $rxCountSinceConnect")
            appendLine("Working Variant:  ${workingVariant ?: "Testing / Not locked yet"}")
            appendLine("Last Measure:     ${lastMeasureResult ?: "(None)"}")
            appendLine("Last Error:       ${lastError ?: "(None)"}")
            appendLine("Last TX Hex:      ${lastTxHex ?: "(None)"}")
            appendLine("Last RX Hex:      ${lastRxHex ?: "(None)"}")
            appendLine("================================================================================")
            appendLine("RECENT TX FRAMES (Total ${allTx.size} recorded):")
            if (allTx.isEmpty()) {
                appendLine("  (None)")
            } else {
                allTx.takeLast(30).forEach { appendLine("  ${it.toLineString()}") }
            }
            appendLine("================================================================================")
            appendLine("RECENT RX FRAMES (Total ${allRx.size} recorded):")
            if (allRx.isEmpty()) {
                appendLine("  (None)")
            } else {
                allRx.takeLast(30).forEach { appendLine("  ${it.toLineString()}") }
            }
            appendLine("================================================================================")
            appendLine("FULL LOG BUFFER:")
            appendLine()
        }
    }
}
