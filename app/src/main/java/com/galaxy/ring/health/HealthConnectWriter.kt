package com.galaxy.ring.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Temperature
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.RingHealthSnapshot
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
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class)
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

    suspend fun writeHeartRateSample(sample: HeartRateSample, isManual: Boolean = false): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val sampleInstant = Instant.ofEpochMilli(sample.timestamp)
            val recordingMethod = if (isManual) {
                Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
            } else {
                Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED
            }
            val record = HeartRateRecord(
                startTime = sampleInstant.minusSeconds(2),
                startZoneOffset = ZoneOffset.UTC,
                endTime = sampleInstant,
                endZoneOffset = ZoneOffset.UTC,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = sampleInstant,
                        beatsPerMinute = sample.bpm.toLong()
                    )
                ),
                metadata = Metadata(recordingMethod = recordingMethod)
            )
            client.insertRecords(listOf(record))
            Log.d(tag, "Inserted HeartRateRecord to Health Connect: ${sample.bpm} bpm (manual=$isManual)")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error inserting HeartRateRecord", e)
            false
        }
    }

    suspend fun writeOxygenSaturationSample(sample: OxygenSaturationSample, isManual: Boolean = false): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val sampleInstant = Instant.ofEpochMilli(sample.timestamp)
            val recordingMethod = if (isManual) {
                Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
            } else {
                Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED
            }
            val record = OxygenSaturationRecord(
                time = sampleInstant,
                zoneOffset = ZoneOffset.UTC,
                percentage = Percentage(sample.percentage.toDouble()),
                metadata = Metadata(recordingMethod = recordingMethod)
            )
            client.insertRecords(listOf(record))
            Log.d(tag, "Inserted OxygenSaturationRecord to Health Connect: ${sample.percentage}% (manual=$isManual)")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error inserting OxygenSaturationRecord", e)
            false
        }
    }

    suspend fun writeSteps(stepData: StepData): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val now = Instant.ofEpochMilli(stepData.timestamp)
            val record = StepsRecord(
                startTime = now.minusSeconds(3600),
                startZoneOffset = ZoneOffset.UTC,
                endTime = now,
                endZoneOffset = ZoneOffset.UTC,
                count = stepData.totalSteps,
                metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED)
            )
            client.insertRecords(listOf(record))
            Log.d(tag, "Inserted StepsRecord to Health Connect: ${stepData.totalSteps}")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error inserting StepsRecord", e)
            false
        }
    }

    suspend fun writeSleepSession(sleep: SleepSession): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val sleepRecord = SleepSessionRecord(
                startTime = Instant.ofEpochMilli(sleep.startTime),
                startZoneOffset = ZoneOffset.UTC,
                endTime = Instant.ofEpochMilli(sleep.endTime),
                endZoneOffset = ZoneOffset.UTC,
                title = "Galaxy Ring Sleep Tracking",
                notes = "Sleep Quality: ${sleep.qualityScore}%"
            )
            client.insertRecords(listOf(sleepRecord))
            Log.d(tag, "Inserted SleepSessionRecord to Health Connect")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error inserting SleepSessionRecord", e)
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
        if (writeHeartRateSample(snapshot.latestHeartRate, isManual = false)) {
            writeCount++
        }

        // 2. Write SpO2 if present
        snapshot.latestOxygenSaturation?.let { spo2 ->
            if (writeOxygenSaturationSample(spo2, isManual = false)) {
                writeCount++
            }
        }

        // 3. Write Steps
        if (writeSteps(snapshot.steps)) {
            writeCount++
        }

        // 4. Write Body/Skin Temperature
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

        // 5. Write Sleep if present
        snapshot.latestSleep?.let { sleep ->
            if (writeSleepSession(sleep)) {
                writeCount++
            }
        }

        return writeCount > 0
    }
}
