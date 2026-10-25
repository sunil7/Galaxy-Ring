package com.galaxy.ring.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.galaxy.ring.GalaxyRingApp
import com.galaxy.ring.MainActivity
import com.galaxy.ring.data.ConnectionState
import com.galaxy.ring.data.MonitorMetric
import com.galaxy.ring.data.MonitoringSchedule
import com.galaxy.ring.data.MonitoringSettings
import com.galaxy.ring.debug.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RingScheduledService : Service() {

    private val tag = "GalaxyRingSync"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var scheduledLoopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.i(tag, "RingScheduledService created")
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GalaxyRing:ScheduledWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? GalaxyRingApp
        val repo = app?.healthRepository
        val settings = MonitoringSettings(applicationContext)
        val shortestInterval = MonitoringSchedule.shortestIntervalMin(settings.allConfigs())
        val isKeep = repo?.isKeepConnected == true
        val isSchedule = shortestInterval != null || repo?.isScheduleEnabled == true
        val shouldRun = isKeep || isSchedule

        if (!shouldRun) {
            AppLog.i(tag, "RingScheduledService: both schedule and keep-connected are OFF. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (shortestInterval != null) {
            repo?.scheduleIntervalMinutes = shortestInterval
            repo?.isScheduleEnabled = true
        }

        AppLog.i(tag, "RingScheduledService starting foreground monitoring (keepConnected=$isKeep, shortestInterval=${shortestInterval}m)...")

        try {
            val notification = buildPersistentNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to startForeground for RingScheduledService: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        startMonitoringLoop()
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        scheduledLoopJob?.cancel()
        scheduledLoopJob = scope.launch {
            val app = applicationContext as? GalaxyRingApp ?: return@launch
            val repo = app.healthRepository
            val bleRepo = app.bleRepository
            val settings = MonitoringSettings(applicationContext)

            val initialShortest = MonitoringSchedule.shortestIntervalMin(settings.allConfigs())
            AppLog.i(tag, "Foreground service monitoring loop started. ShortestInterval=${initialShortest}m, KeepConnected=${repo.isKeepConnected}")

            while (isActive) {
                val currentShortest = MonitoringSchedule.shortestIntervalMin(settings.allConfigs())
                val isKeep = repo.isKeepConnected
                if (currentShortest == null && !isKeep) {
                    AppLog.i(tag, "Monitoring loop exiting: no scheduled metrics and keep-connected is OFF.")
                    stopSelf()
                    break
                }

                // If disconnected and last address known, keep connection alive.
                // Respect a user-initiated disconnect: do not auto-reconnect for the rest of the session.
                val connState = bleRepo.connectionState.value
                if (connState is ConnectionState.Disconnected && !bleRepo.isUserDisconnected) {
                    val lastAddr = repo.lastConnectedAddress
                    if (lastAddr != null) {
                        AppLog.i(tag, "FGS keep-alive: ring is disconnected, auto-connecting to $lastAddr...")
                        bleRepo.connect(lastAddr, repo.lastConnectedName ?: "Galaxy Ring")
                    }
                }

                // If scheduled monitoring has due metrics, measure only settings.dueNow() and markRun after
                if (currentShortest != null) {
                    val due = settings.dueNow()
                    if (due.isNotEmpty()) {
                        AppLog.i(tag, "Scheduled check timer fired! Due metrics: ${due.map { it.name }}. Executing scheduled measurement...")
                        try {
                            wakeLock?.acquire(90000L) // 90s safe partial wakelock
                            bleRepo.performScheduledMeasurement(
                                dueMetrics = due,
                                settings = settings
                            )
                        } catch (e: Exception) {
                            AppLog.e(tag, "Error during scheduled biometric check: ${e.message}", e)
                        } finally {
                            try {
                                if (wakeLock?.isHeld == true) {
                                    wakeLock?.release()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Poll check every 15 seconds
                delay(15000L)
            }

            if (MonitoringSchedule.shortestIntervalMin(settings.allConfigs()) == null && !repo.isKeepConnected) {
                AppLog.i(tag, "Monitoring loop exiting: schedule and keep-connected are both OFF.")
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Galaxy Ring Scheduled Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent foreground service for scheduled smart ring health checks"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val app = applicationContext as? GalaxyRingApp
        val settings = MonitoringSettings(applicationContext)
        val shortest = MonitoringSchedule.shortestIntervalMin(settings.allConfigs())
        val interval = shortest ?: app?.healthRepository?.scheduleIntervalMinutes ?: 30

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val repo = app?.healthRepository
        val isSchedule = shortest != null || repo?.isScheduleEnabled == true
        val isKeep = repo?.isKeepConnected == true
        val statusText = when {
            isSchedule && isKeep -> "Active background monitoring every $interval min"
            isSchedule -> "Scheduled biometric logging active every $interval min"
            else -> "Ring link maintained in background"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Galaxy Ring connected — monitoring")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(tag, "RingScheduledService destroyed")
        scheduledLoopJob?.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // WakeLock safety
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "galaxy_ring_schedule_channel"
        private const val NOTIFICATION_ID = 4096

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasBtConnect = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                val hasBtScan = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasBtConnect && !hasBtScan) {
                    AppLog.w("GalaxyRingSync", "Cannot start RingScheduledService: Bluetooth permissions not granted")
                    return
                }
            }
            val intent = Intent(context, RingScheduledService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AppLog.e("GalaxyRingSync", "Failed to start RingScheduledService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RingScheduledService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                AppLog.e("GalaxyRingSync", "Failed to stop RingScheduledService: ${e.message}", e)
            }
        }

        fun restart(context: Context) {
            stop(context)
            start(context)
        }

        /**
         * Returns the shortest interval in minutes among enabled metrics, or null if none are enabled.
         */
        fun computeWakeIntervalMin(settings: MonitoringSettings): Int? =
            MonitoringSchedule.shortestIntervalMin(settings.allConfigs())

        /**
         * Evaluates due metrics from [settings.dueNow], executes [measureAction] for each,
         * and calls [settings.markRun] after each measurement.
         * Returns the list of metrics that were measured.
         */
        suspend fun executeDueMeasurements(
            settings: MonitoringSettings,
            measureAction: suspend (MonitorMetric) -> Unit
        ): List<MonitorMetric> {
            val due = settings.dueNow()
            for (metric in due) {
                try {
                    measureAction(metric)
                } finally {
                    settings.markRun(metric)
                }
            }
            return due
        }
    }
}
