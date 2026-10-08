package com.galaxy.ring

import android.app.Application
import com.galaxy.ring.ble.BleRepository
import com.galaxy.ring.data.RingHealthRepository
import com.galaxy.ring.health.HealthConnectWriter
import com.galaxy.ring.sync.RingScheduledService
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

        healthRepository = RingHealthRepository(this)
        healthConnectWriter = HealthConnectWriter(this)
        bleRepository = BleRepository(this, healthRepository, healthConnectWriter)

        // Start background scheduled monitoring service if enabled
        try {
            if (healthRepository.isScheduleEnabled) {
                RingScheduledService.start(this)
            }
        } catch (e: Exception) {
            // Service safety
        }

        // Periodic WorkManager backup sync to Health Connect
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
