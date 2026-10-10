package com.galaxy.ring

import android.app.Application
import com.galaxy.ring.ble.BleRepository
import com.galaxy.ring.data.RingHealthRepository
import com.galaxy.ring.debug.AppLog
import com.galaxy.ring.health.HealthConnectWriter
import com.galaxy.ring.sync.SyncWorker

class GalaxyRingApp : Application() {

    lateinit var bleRepository: BleRepository
        private set

    lateinit var healthConnectWriter: HealthConnectWriter
        private set

    lateinit var healthRepository: RingHealthRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val tag = "GalaxyRingApp"
        AppLog.i(tag, "GalaxyRingApp initializing...")

        try {
            healthRepository = RingHealthRepository(this)
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to initialize RingHealthRepository", e)
        }

        try {
            healthConnectWriter = HealthConnectWriter(this)
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to initialize HealthConnectWriter", e)
        }

        try {
            bleRepository = BleRepository(this, healthRepository, healthConnectWriter)
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to initialize BleRepository", e)
        }

        // Periodic WorkManager backup sync to Health Connect
        try {
            SyncWorker.schedulePeriodicSync(this)
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to schedule WorkManager periodic sync", e)
        }
    }

    companion object {
        lateinit var instance: GalaxyRingApp
            private set
    }
}
