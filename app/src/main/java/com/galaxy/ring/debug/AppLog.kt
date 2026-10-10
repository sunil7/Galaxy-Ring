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
 * In-app logging system with thread-safe circular ring buffer (2000 lines),
 * dual-writing to Android logcat and exposing observable StateFlow for Admin/Debug UI.
 */
object AppLog {

    private const val MAX_ENTRIES = 2000
    private var nextId = 0L

    private val entriesLock = Any()
    private val entriesDeque = ArrayDeque<LogEntry>(MAX_ENTRIES + 16)

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    // Key telemetry and diagnostic states
    @Volatile var lastTxHex: String? = null
    @Volatile var lastRxHex: String? = null
    @Volatile var lastError: String? = null
    @Volatile var lastMeasureResult: String? = null

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
        }
        _logsFlow.value = emptyList()
    }

    fun getAllLogsText(): String {
        val list = synchronized(entriesLock) { entriesDeque.toList() }
        return list.joinToString("\n") { it.toLineString() }
    }

    fun getLastTxRxSnippet(): String {
        val tx = lastTxHex ?: "(None)"
        val rx = lastRxHex ?: "(None)"
        return "Last TX: $tx\nLast RX: $rx"
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
            appendLine("Last Measure:     ${lastMeasureResult ?: "(None)"}")
            appendLine("Last Error:       ${lastError ?: "(None)"}")
            appendLine("Last TX Hex:      ${lastTxHex ?: "(None)"}")
            appendLine("Last RX Hex:      ${lastRxHex ?: "(None)"}")
            appendLine("================================================================================")
            appendLine()
        }
    }
}
