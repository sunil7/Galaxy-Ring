package com.galaxy.ring

import android.app.Application
import com.galaxy.ring.ble.BleRepository
import com.galaxy.ring.health.HealthConnectWriter
import com.galaxy.ring.sync.SyncWorker

class GalaxyRingApp : Application() {

    lateinit var bleRepository: BleRepository
        private set

    lateinit var healthConnectWriter: HealthConnectWriter
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        bleRepository = BleRepository(this)
        healthConnectWriter = HealthConnectWriter(this)

        // Schedule periodic 15-minute background sync to Health Connect
        try {
            SyncWorker.schedulePeriodicSync(this)
        } catch (e: Exception) {
            // WorkManager init safety
        }
    }

    companion object {
        lateinit var instance: GalaxyRingApp
            private set
    }
}
