package com.galaxy.ring.health

import android.content.Context
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
import com.galaxy.ring.data.SleepStage
import com.galaxy.ring.data.StepData
import com.galaxy.ring.debug.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HealthConnectWriter(private val context: Context) {

    private val tag = "HealthConnectWriter"

    private val _lastWriteTime = MutableStateFlow<Long?>(null)
    val lastWriteTimeFlow: StateFlow<Long?> = _lastWriteTime.asStateFlow()

    private val _lastWriteError = MutableStateFlow<String?>(null)
    val lastWriteErrorFlow: StateFlow<String?> = _lastWriteError.asStateFlow()

    private val _lastWriteStatus = MutableStateFlow("No writes yet")
    val lastWriteStatusFlow: StateFlow<String> = _lastWriteStatus.asStateFlow()

    var lastWriteTime: Long?
        get() = _lastWriteTime.value
        set(value) { _lastWriteTime.value = value }

    var lastWriteError: String?
        get() = _lastWriteError.value
        set(value) { _lastWriteError.value = value }

    var lastWriteStatus: String
        get() = _lastWriteStatus.value
        set(value) { _lastWriteStatus.value = value }

    val healthConnectClient: HealthConnectClient? by lazy {
        if (isHealthConnectAvailable()) {
            try {
                HealthConnectClient.getOrCreate(context)
            } catch (e: Exception) {
                AppLog.w(tag, "Failed to get HealthConnectClient: ${e.message}")
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

    val corePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class)
    )

    fun isHealthConnectAvailable(): Boolean {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Throwable) {
            AppLog.w(tag, "Health Connect service not available: ${e.message}")
            false
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val client = healthConnectClient ?: return false
            val granted = client.permissionController.getGrantedPermissions()
            val allOk = granted.containsAll(corePermissions)
            AppLog.i(tag, "Health Connect permissions check: coreGranted=$allOk (granted count: ${granted.size}/${requiredPermissions.size})")
            allOk
        } catch (e: Throwable) {
            AppLog.e(tag, "Error checking Health Connect permissions: ${e.message}", e)
            false
        }
    }

    suspend fun writeHeartRateSample(sample: HeartRateSample, isManual: Boolean = false): Boolean {
        if (sample.bpm !in 30..250) {
            AppLog.w(tag, "Skipping invalid HR sample for Health Connect: ${sample.bpm} bpm (valid: 30..250)")
            return false
        }
        val client = healthConnectClient ?: run {
            AppLog.w(tag, "Health Connect client unavailable for HR write")
            lastWriteError = "Health Connect not available on this device"
            lastWriteStatus = "Unavailable"
            return false
        }

        return try {
            val sampleInstant = Instant.ofEpochMilli(sample.timestamp)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(sampleInstant)
            val recordingMethod = if (isManual) {
                Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
            } else {
                Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED
            }

            val record = HeartRateRecord(
                startTime = sampleInstant.minusSeconds(1),
                startZoneOffset = zoneOffset,
                endTime = sampleInstant,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = sampleInstant,
                        beatsPerMinute = sample.bpm.toLong()
                    )
                ),
                metadata = Metadata(
                    recordingMethod = recordingMethod,
                    clientRecordId = "hr_${sample.timestamp}"
                )
            )
            client.insertRecords(listOf(record))
            val now = System.currentTimeMillis()
            lastWriteTime = now
            lastWriteError = null
            lastWriteStatus = "HR OK (${sample.bpm} BPM)"
            AppLog.i(tag, "Health Connect: HeartRateRecord written successfully: ${sample.bpm} BPM (manual=$isManual)")
            true
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown HR write error"
            lastWriteError = errMsg
            lastWriteStatus = "HR Error: $errMsg"
            AppLog.e(tag, "Health Connect: Failed to write HeartRateRecord: $errMsg", e)
            false
        }
    }

    suspend fun writeOxygenSaturationSample(sample: OxygenSaturationSample, isManual: Boolean = false): Boolean {
        if (sample.percentage !in 50f..100f) {
            AppLog.w(tag, "Skipping invalid SpO2 sample for Health Connect: ${sample.percentage}%")
            return false
        }
        val client = healthConnectClient ?: run {
            AppLog.w(tag, "Health Connect client unavailable for SpO2 write")
            lastWriteError = "Health Connect not available on this device"
            lastWriteStatus = "Unavailable"
            return false
        }

        return try {
            val sampleInstant = Instant.ofEpochMilli(sample.timestamp)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(sampleInstant)
            val recordingMethod = if (isManual) {
                Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
            } else {
                Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED
            }

            val record = OxygenSaturationRecord(
                time = sampleInstant,
                zoneOffset = zoneOffset,
                percentage = Percentage(sample.percentage.toDouble()),
                metadata = Metadata(
                    recordingMethod = recordingMethod,
                    clientRecordId = "spo2_${sample.timestamp}"
                )
            )
            client.insertRecords(listOf(record))
            val now = System.currentTimeMillis()
            lastWriteTime = now
            lastWriteError = null
            lastWriteStatus = "SpO₂ OK (${sample.percentage.toInt()}%)"
            AppLog.i(tag, "Health Connect: OxygenSaturationRecord written successfully: ${sample.percentage}% (manual=$isManual)")
            true
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown SpO2 write error"
            lastWriteError = errMsg
            lastWriteStatus = "SpO₂ Error: $errMsg"
            AppLog.e(tag, "Health Connect: Failed to write OxygenSaturationRecord: $errMsg", e)
            false
        }
    }

    suspend fun writeSteps(stepData: StepData): Boolean {
        if (stepData.totalSteps <= 0) {
            AppLog.w(tag, "Skipping steps write to Health Connect: ${stepData.totalSteps} (must be > 0)")
            return false
        }
        val client = healthConnectClient ?: run {
            AppLog.w(tag, "Health Connect client unavailable for steps write")
            lastWriteError = "Health Connect not available on this device"
            lastWriteStatus = "Unavailable"
            return false
        }

        return try {
            val endInstant = Instant.ofEpochMilli(stepData.timestamp)
            val cal = Calendar.getInstance().apply {
                timeInMillis = stepData.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var startInstant = Instant.ofEpochMilli(cal.timeInMillis)
            if (!startInstant.isBefore(endInstant)) {
                startInstant = endInstant.minusSeconds(60)
            }
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(endInstant)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(stepData.timestamp))

            val record = StepsRecord(
                startTime = startInstant,
                startZoneOffset = zoneOffset,
                endTime = endInstant,
                endZoneOffset = zoneOffset,
                count = stepData.totalSteps,
                metadata = Metadata(
                    recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
                    clientRecordId = "steps_$dateStr"
                )
            )
            try {
                client.deleteRecords(
                    recordType = StepsRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = listOf("steps_$dateStr")
                )
            } catch (_: Exception) {}
            client.insertRecords(listOf(record))
            val now = System.currentTimeMillis()
            lastWriteTime = now
            lastWriteError = null
            lastWriteStatus = "Steps OK (${stepData.totalSteps})"
            AppLog.i(tag, "Health Connect: StepsRecord written successfully: ${stepData.totalSteps} steps for $dateStr")
            true
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown steps write error"
            lastWriteError = errMsg
            lastWriteStatus = "Steps Error: $errMsg"
            AppLog.e(tag, "Health Connect: Failed to write StepsRecord: $errMsg", e)
            false
        }
    }

    suspend fun writeSleepSession(sleep: SleepSession): Boolean {
        if (sleep.startTime >= sleep.endTime || sleep.durationMinutes < 15L) {
            AppLog.w(tag, "Skipping invalid sleep session write to Health Connect (duration: ${sleep.durationMinutes} min)")
            return false
        }
        val client = healthConnectClient ?: run {
            AppLog.w(tag, "Health Connect client unavailable for sleep write")
            lastWriteError = "Health Connect not available on this device"
            lastWriteStatus = "Unavailable"
            return false
        }

        return try {
            val startInstant = Instant.ofEpochMilli(sleep.startTime)
            val endInstant = Instant.ofEpochMilli(sleep.endTime)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(endInstant)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sleep.endTime))

            val hcStages = sleep.stages.filter { it.startTime < it.endTime }.map { stageRecord ->
                val stageType = when (stageRecord.stage) {
                    SleepStage.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
                    SleepStage.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
                    SleepStage.REM -> SleepSessionRecord.STAGE_TYPE_REM
                    SleepStage.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
                }
                SleepSessionRecord.Stage(
                    startTime = Instant.ofEpochMilli(stageRecord.startTime),
                    endTime = Instant.ofEpochMilli(stageRecord.endTime),
                    stage = stageType
                )
            }

            val record = SleepSessionRecord(
                startTime = startInstant,
                startZoneOffset = zoneOffset,
                endTime = endInstant,
                endZoneOffset = zoneOffset,
                title = "Galaxy Ring Sleep Tracking",
                notes = "Sleep Duration: ${sleep.durationMinutes} min, Quality: ${sleep.qualityScore}%",
                stages = hcStages,
                metadata = Metadata(
                    recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
                    clientRecordId = "sleep_$dateStr"
                )
            )
            try {
                client.deleteRecords(
                    recordType = SleepSessionRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = listOf("sleep_$dateStr")
                )
            } catch (_: Exception) {}
            client.insertRecords(listOf(record))
            val now = System.currentTimeMillis()
            lastWriteTime = now
            lastWriteError = null
            lastWriteStatus = "Sleep OK (${sleep.durationMinutes}m)"
            AppLog.i(tag, "Health Connect: SleepSessionRecord written successfully: ${sleep.durationMinutes} mins (${hcStages.size} stages) for $dateStr")
            true
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown sleep write error"
            lastWriteError = errMsg
            lastWriteStatus = "Sleep Error: $errMsg"
            AppLog.e(tag, "Health Connect: Failed to write SleepSessionRecord: $errMsg", e)
            false
        }
    }

    suspend fun writeSnapshot(snapshot: RingHealthSnapshot): Boolean {
        val client = healthConnectClient ?: run {
            AppLog.w(tag, "Health Connect client not available for snapshot write")
            return false
        }

        var writeCount = 0

        // 1. Write Heart Rate if valid
        if (snapshot.latestHeartRate.bpm in 30..250) {
            if (writeHeartRateSample(snapshot.latestHeartRate, isManual = false)) {
                writeCount++
            }
        }

        // 2. Write SpO2 if present and valid
        snapshot.latestOxygenSaturation?.let { spo2 ->
            if (spo2.percentage in 50f..100f) {
                if (writeOxygenSaturationSample(spo2, isManual = false)) {
                    writeCount++
                }
            }
        }

        // 3. Write Steps if positive
        if (snapshot.steps.totalSteps > 0) {
            if (writeSteps(snapshot.steps)) {
                writeCount++
            }
        }

        // 4. Write Body/Skin Temperature
        try {
            val temp = snapshot.temperature
            if (temp.temperatureCelsius in 25f..45f) {
                val time = Instant.ofEpochMilli(temp.timestamp)
                val zoneOffset = ZoneId.systemDefault().rules.getOffset(time)
                val tempRecord = BodyTemperatureRecord(
                    time = time,
                    zoneOffset = zoneOffset,
                    temperature = Temperature.celsius(temp.temperatureCelsius.toDouble()),
                    metadata = Metadata(
                        recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
                        clientRecordId = "temp_${temp.timestamp}"
                    )
                )
                client.insertRecords(listOf(tempRecord))
                writeCount++
                AppLog.i(tag, "Health Connect: BodyTemperatureRecord written successfully: ${temp.temperatureCelsius}°C")
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Health Connect: Error inserting BodyTemperatureRecord: ${e.message}", e)
        }

        // 5. Write Sleep if present
        snapshot.latestSleep?.let { sleep ->
            if (sleep.durationMinutes >= 15L) {
                if (writeSleepSession(sleep)) {
                    writeCount++
                }
            }
        }

        AppLog.i(tag, "Health Connect writeSnapshot completed: $writeCount records written successfully")
        return writeCount > 0
    }
}
