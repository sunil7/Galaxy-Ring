package com.galaxy.ring.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.galaxy.ring.GalaxyRingApp
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "GalaxyRingSyncWorker"

    override suspend fun doWork(): Result {
        Log.d(tag, "Starting background sync of Galaxy Ring vitals to Health Connect...")

        val app = applicationContext as? GalaxyRingApp ?: return Result.failure()
        val bleRepo = app.bleRepository
        val healthWriter = app.healthConnectWriter

        return try {
            val snapshot = bleRepo.requestSync()
            val success = healthWriter.writeSnapshot(snapshot)
            Log.d(tag, "Sync completed with status: $success (HR: ${snapshot.latestHeartRate.bpm}, Steps: ${snapshot.steps.totalSteps})")
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync ring vitals to Health Connect", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "galaxy_ring_periodic_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
