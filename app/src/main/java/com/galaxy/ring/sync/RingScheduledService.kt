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
        AppLog.i(tag, "RingScheduledService starting foreground monitoring...")

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

        startScheduledLoop()
        return START_STICKY
    }

    private fun startScheduledLoop() {
        scheduledLoopJob?.cancel()
        scheduledLoopJob = scope.launch {
            val app = applicationContext as? GalaxyRingApp ?: return@launch
            val repo = app.healthRepository
            val bleRepo = app.bleRepository

            AppLog.i(tag, "Started scheduled monitoring loop. Interval: ${repo.scheduleIntervalMinutes}m")

            while (isActive && repo.isScheduleEnabled) {
                val intervalMillis = repo.scheduleIntervalMinutes * 60 * 1000L
                delay(intervalMillis)

                if (!repo.isScheduleEnabled) break

                AppLog.d(tag, "Scheduled check waking up. Executing biometric sampling...")
                try {
                    wakeLock?.acquire(15000L) // Safe 15-second partial wakelock

                    // Ensure connection if ring is disconnected and last address is known
                    val connState = bleRepo.connectionState.value
                    if (connState !is ConnectionState.Ready && connState !is ConnectionState.Connected) {
                        val lastAddr = repo.lastConnectedAddress
                        if (lastAddr != null) {
                            AppLog.d(tag, "Ring disconnected during scheduled check; connecting to $lastAddr")
                            bleRepo.connect(lastAddr, repo.lastConnectedName ?: "Galaxy Ring")
                            delay(3000)
                        }
                    }

                    // 1. Measure Heart Rate if enabled
                    if (repo.scheduleCheckHeartRate) {
                        AppLog.d(tag, "Running scheduled Heart Rate check...")
                        bleRepo.measureHeartRate()
                        delay(2500)
                    }

                    // 2. Measure SpO2 if enabled
                    if (repo.scheduleCheckSpo2) {
                        AppLog.d(tag, "Running scheduled SpO2 check...")
                        bleRepo.measureOxygenSaturation()
                        delay(2500)
                    }

                    // 3. Sync Daily Steps
                    bleRepo.requestSync()
                    AppLog.i(tag, "Scheduled biometric sampling completed successfully")
                } catch (e: Exception) {
                    AppLog.e(tag, "Error during scheduled biometric check: ${e.message}", e)
                } finally {
                    try {
                        if (wakeLock?.isHeld == true) {
                            wakeLock?.release()
                        }
                    } catch (e: Exception) {
                        // Safe release
                    }
                }
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Galaxy Ring – scheduled monitoring")
            .setContentText("Active background monitoring every $interval min (Heart Rate, SpO₂)")
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
