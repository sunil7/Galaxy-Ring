package com.galaxy.ring.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.units.Temperature
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.RingHealthSnapshot
import com.galaxy.ring.data.SkinTemperature
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.StepData
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectWriter(private val context: Context) {

    private val tag = "HealthConnectWriter"

    val healthConnectClient: HealthConnectClient? by lazy {
        if (isHealthConnectAvailable()) {
            try {
                HealthConnectClient.getOrCreate(context)
            } catch (e: Exception) {
                Log.w(tag, "Failed to get HealthConnectClient", e)
                null
            }
        } else {
            null
        }
    }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class)
    )

    fun isHealthConnectAvailable(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (e: Exception) {
            Log.e(tag, "Error checking permissions", e)
            false
        }
    }

    suspend fun writeSnapshot(snapshot: RingHealthSnapshot): Boolean {
        val client = healthConnectClient ?: run {
            Log.w(tag, "Health Connect client not available")
            return false
        }

        var writeCount = 0

        // 1. Write Heart Rate
        try {
            val hrSample = snapshot.latestHeartRate
            val sampleInstant = Instant.ofEpochMilli(hrSample.timestamp)
            val hrRecord = HeartRateRecord(
                startTime = sampleInstant.minusSeconds(2),
                startZoneOffset = ZoneOffset.UTC,
                endTime = sampleInstant,
                endZoneOffset = ZoneOffset.UTC,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = sampleInstant,
                        beatsPerMinute = hrSample.bpm.toLong()
                    )
                )
            )
            client.insertRecords(listOf(hrRecord))
            writeCount++
        } catch (e: Exception) {
            Log.e(tag, "Error inserting HeartRateRecord", e)
        }

        // 2. Write Steps
        try {
            val stepData = snapshot.steps
            val now = Instant.ofEpochMilli(stepData.timestamp)
            val stepsRecord = StepsRecord(
                startTime = now.minusSeconds(3600),
                startZoneOffset = ZoneOffset.UTC,
                endTime = now,
                endZoneOffset = ZoneOffset.UTC,
                count = stepData.totalSteps
            )
            client.insertRecords(listOf(stepsRecord))
            writeCount++
        } catch (e: Exception) {
            Log.e(tag, "Error inserting StepsRecord", e)
        }

        // 3. Write Body/Skin Temperature
        try {
            val temp = snapshot.temperature
            val time = Instant.ofEpochMilli(temp.timestamp)
            val tempRecord = BodyTemperatureRecord(
                time = time,
                zoneOffset = ZoneOffset.UTC,
                temperature = Temperature.celsius(temp.temperatureCelsius.toDouble())
            )
            client.insertRecords(listOf(tempRecord))
            writeCount++
        } catch (e: Exception) {
            Log.e(tag, "Error inserting BodyTemperatureRecord", e)
        }

        // 4. Write Sleep if present
        snapshot.latestSleep?.let { sleep ->
            try {
                val sleepRecord = SleepSessionRecord(
                    startTime = Instant.ofEpochMilli(sleep.startTime),
                    startZoneOffset = ZoneOffset.UTC,
                    endTime = Instant.ofEpochMilli(sleep.endTime),
                    endZoneOffset = ZoneOffset.UTC,
                    title = "Galaxy Ring Sleep Tracking",
                    notes = "Sleep Quality: ${sleep.qualityScore}%"
                )
                client.insertRecords(listOf(sleepRecord))
                writeCount++
            } catch (e: Exception) {
                Log.e(tag, "Error inserting SleepSessionRecord", e)
            }
        }

        return writeCount > 0
    }
}
