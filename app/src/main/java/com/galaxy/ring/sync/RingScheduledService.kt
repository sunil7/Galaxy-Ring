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
        val shouldRun = (repo?.isKeepConnected == true) || (repo?.isScheduleEnabled == true)

        if (!shouldRun) {
            AppLog.i(tag, "RingScheduledService: both schedule and keep-connected are OFF. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        AppLog.i(tag, "RingScheduledService starting foreground monitoring (keepConnected=${repo?.isKeepConnected}, schedule=${repo?.isScheduleEnabled})...")

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

            AppLog.i(tag, "Foreground service monitoring loop started. Schedule=${repo.isScheduleEnabled} (interval=${repo.scheduleIntervalMinutes}m), KeepConnected=${repo.isKeepConnected}")

            var lastScheduleCheckTime = System.currentTimeMillis()

            while (isActive && (repo.isScheduleEnabled || repo.isKeepConnected)) {
                // If disconnected and last address known, keep connection alive
                val connState = bleRepo.connectionState.value
                if (connState is ConnectionState.Disconnected) {
                    val lastAddr = repo.lastConnectedAddress
                    if (lastAddr != null) {
                        AppLog.i(tag, "FGS keep-alive: ring is disconnected, auto-connecting to $lastAddr...")
                        bleRepo.connect(lastAddr, repo.lastConnectedName ?: "Galaxy Ring")
                    }
                }

                // If schedule is enabled, check if interval has elapsed
                if (repo.isScheduleEnabled) {
                    val intervalMillis = repo.scheduleIntervalMinutes * 60 * 1000L
                    val now = System.currentTimeMillis()
                    if (now - lastScheduleCheckTime >= intervalMillis) {
                        lastScheduleCheckTime = now
                        AppLog.i(tag, "Scheduled check timer fired! Executing scheduled biometric check...")
                        try {
                            wakeLock?.acquire(45000L) // 45s safe partial wakelock
                            bleRepo.performScheduledMeasurement(
                                checkHr = repo.scheduleCheckHeartRate,
                                checkSpo2 = repo.scheduleCheckSpo2
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

            if (!repo.isScheduleEnabled && !repo.isKeepConnected) {
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
        val interval = app?.healthRepository?.scheduleIntervalMinutes ?: 30

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
        val isSchedule = repo?.isScheduleEnabled == true
        val isKeep = repo?.isKeepConnected == true
        val statusText = when {
            isSchedule && isKeep -> "Active background monitoring every $interval min (Heart Rate, SpO₂)"
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
    }
}
