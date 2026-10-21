package com.galaxy.ring.data

import android.content.Context
import android.content.SharedPreferences
import com.galaxy.ring.debug.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RingHealthRepository(private val context: Context) {

    private val tag = "GalaxyRingSync"
    private val database = RingDatabase.getDatabase(context)
    private val hrDao = database.heartRateDao()
    private val spo2Dao = database.oxygenSaturationDao()
    private val stepsDao = database.dailyStepsDao()
    private val sleepDao = database.sleepDao()
    private val hrvDao = database.hrvDao()
    private val stressDao = database.stressDao()
    private val workoutDao = database.workoutDao()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("galaxy_ring_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Sleep target (in hours, e.g. 8.0f)
    var sleepTargetHours: Float
        get() = prefs.getFloat("sleep_target_hours", 8.0f)
        set(value) = prefs.edit().putFloat("sleep_target_hours", value).apply()

    // Scheduled checks preferences
    var isScheduleEnabled: Boolean
        get() = prefs.getBoolean("schedule_enabled", false)
        set(value) = prefs.edit().putBoolean("schedule_enabled", value).apply()

    var scheduleIntervalMinutes: Int
        get() = prefs.getInt("schedule_interval_minutes", 30)
        set(value) = prefs.edit().putInt("schedule_interval_minutes", value).apply()

    var scheduleCheckHeartRate: Boolean
        get() = prefs.getBoolean("schedule_check_hr", true)
        set(value) = prefs.edit().putBoolean("schedule_check_hr", value).apply()

    var scheduleCheckSpo2: Boolean
        get() = prefs.getBoolean("schedule_check_spo2", true)
        set(value) = prefs.edit().putBoolean("schedule_check_spo2", value).apply()

    var lastConnectedAddress: String?
        get() = prefs.getString("last_connected_address", null)
        set(value) = prefs.edit().putString("last_connected_address", value).apply()

    var lastConnectedName: String?
        get() = prefs.getString("last_connected_name", "Galaxy Ring")
        set(value) = prefs.edit().putString("last_connected_name", value).apply()

    // Keep ring connected foreground persistence
    var isKeepConnected: Boolean
        get() = prefs.getBoolean("keep_connected", false)
        set(value) = prefs.edit().putBoolean("keep_connected", value).apply()

    // Bluetooth bonding preference
    var isBondingEnabled: Boolean
        get() = prefs.getBoolean("bonding_enabled", true)
        set(value) = prefs.edit().putBoolean("bonding_enabled", value).apply()

    // Scheduled background checks run tracking
    var lastScheduleRunTimestamp: Long
        get() = prefs.getLong("last_schedule_run_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_schedule_run_timestamp", value).apply()

    var lastScheduleRunResult: String?
        get() = prefs.getString("last_schedule_run_result", null)
        set(value) = prefs.edit().putString("last_schedule_run_result", value).apply()

    // Sleep apnea screening self-reported checklist
    var apneaLoudSnoring: Boolean
        get() = prefs.getBoolean("apnea_loud_snoring", false)
        set(value) = prefs.edit().putBoolean("apnea_loud_snoring", value).apply()

    var apneaDaytimeSleepiness: Boolean
        get() = prefs.getBoolean("apnea_daytime_sleepiness", false)
        set(value) = prefs.edit().putBoolean("apnea_daytime_sleepiness", value).apply()

    var apneaObservedPauses: Boolean
        get() = prefs.getBoolean("apnea_observed_pauses", false)
        set(value) = prefs.edit().putBoolean("apnea_observed_pauses", value).apply()

    // Ring display ON/OFF toggle (default ON = true)
    var isRingDisplayEnabled: Boolean
        get() = prefs.getBoolean("ring_display_enabled", true)
        set(value) = prefs.edit().putBoolean("ring_display_enabled", value).apply()

    // Cached confirmed key for ring display toggle
    var displayCommandKey: Int?
        get() = if (prefs.contains("display_command_key")) prefs.getInt("display_command_key", -1).takeIf { it != -1 } else null
        set(value) {
            if (value != null) prefs.edit().putInt("display_command_key", value).apply()
            else prefs.edit().remove("display_command_key").apply()
        }

    // Cached confirmed working path for sleep sync (0x0505) and advance mechanism
    var sleepWorkingKey: Int
        get() = prefs.getInt("sleep_working_key", 0x0505)
        set(value) = prefs.edit().putInt("sleep_working_key", value).apply()

    var sleepRequiresDeleteToAdvance: Boolean
        get() = prefs.getBoolean("sleep_requires_delete", false)
        set(value) = prefs.edit().putBoolean("sleep_requires_delete", value).apply()

    // Cached confirmed measurement type & push key for HRV
    var hrvWorkingType: Byte?
        get() = if (prefs.contains("hrv_working_type")) prefs.getInt("hrv_working_type", -1).takeIf { it != -1 }?.toByte() else null
        set(value) {
            if (value != null) prefs.edit().putInt("hrv_working_type", value.toInt() and 0xFF).apply()
            else prefs.edit().remove("hrv_working_type").apply()
        }

    var hrvWorkingPushKey: Int?
        get() = if (prefs.contains("hrv_working_push_key")) prefs.getInt("hrv_working_push_key", -1).takeIf { it != -1 } else null
        set(value) {
            if (value != null) prefs.edit().putInt("hrv_working_push_key", value).apply()
            else prefs.edit().remove("hrv_working_push_key").apply()
        }

    // Cached confirmed measurement type & push key for Stress
    var stressWorkingType: Byte?
        get() = if (prefs.contains("stress_working_type")) prefs.getInt("stress_working_type", -1).takeIf { it != -1 }?.toByte() else null
        set(value) {
            if (value != null) prefs.edit().putInt("stress_working_type", value.toInt() and 0xFF).apply()
            else prefs.edit().remove("stress_working_type").apply()
        }

    var stressWorkingPushKey: Int?
        get() = if (prefs.contains("stress_working_push_key")) prefs.getInt("stress_working_push_key", -1).takeIf { it != -1 } else null
        set(value) {
            if (value != null) prefs.edit().putInt("stress_working_push_key", value).apply()
            else prefs.edit().remove("stress_working_push_key").apply()
        }

    /**
     * Saves a single Heart Rate sample to Room DB.
     * Safety net: If a sample exists within +-60s, updates that row instead of inserting a duplicate.
     */
    suspend fun saveHeartRate(bpm: Int, timestamp: Long = System.currentTimeMillis(), isManual: Boolean = false) {
        val existing = hrDao.findSampleBetween(timestamp - 60000L, timestamp + 60000L)
        if (existing != null) {
            val updated = existing.copy(
                timestamp = timestamp,
                bpm = bpm,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            hrDao.update(updated)
            AppLog.d(tag, "Updated existing HR sample within 60s in Room: $bpm bpm (manual=$isManual)")
        } else {
            val entity = HeartRateEntity(
                timestamp = timestamp,
                bpm = bpm,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            hrDao.insert(entity)
            AppLog.d(tag, "Saved HR sample to Room: $bpm bpm (manual=$isManual)")
        }
    }

    /**
     * Saves a single SpO2 sample to Room DB.
     * Safety net: If a sample exists within +-60s, updates that row instead of inserting a duplicate.
     */
    suspend fun saveOxygenSaturation(percentage: Float, timestamp: Long = System.currentTimeMillis(), isManual: Boolean = false) {
        val existing = spo2Dao.findSampleBetween(timestamp - 60000L, timestamp + 60000L)
        if (existing != null) {
            val updated = existing.copy(
                timestamp = timestamp,
                percentage = percentage,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            spo2Dao.update(updated)
            AppLog.d(tag, "Updated existing SpO2 sample within 60s in Room: $percentage% (manual=$isManual)")
        } else {
            val entity = OxygenSaturationEntity(
                timestamp = timestamp,
                percentage = percentage,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            spo2Dao.insert(entity)
            AppLog.d(tag, "Saved SpO2 sample to Room: $percentage% (manual=$isManual)")
        }
    }

    /**
     * Saves a single HRV sample to Room DB.
     * Safety net: If a sample exists within +-60s, updates that row instead of inserting a duplicate.
     */
    suspend fun saveHrv(hrvMs: Int, timestamp: Long = System.currentTimeMillis(), isManual: Boolean = false) {
        val existing = hrvDao.findSampleBetween(timestamp - 60000L, timestamp + 60000L)
        if (existing != null) {
            val updated = existing.copy(
                timestamp = timestamp,
                hrvMs = hrvMs,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            hrvDao.update(updated)
            AppLog.d(tag, "Updated existing HRV sample within 60s in Room: $hrvMs ms (manual=$isManual)")
        } else {
            val entity = HrvEntity(
                timestamp = timestamp,
                hrvMs = hrvMs,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            hrvDao.insert(entity)
            AppLog.d(tag, "Saved HRV sample to Room: $hrvMs ms (manual=$isManual)")
        }
    }

    /**
     * Saves a single Stress score sample (0..100) to Room DB.
     * Safety net: If a sample exists within +-60s, updates that row instead of inserting a duplicate.
     */
    suspend fun saveStress(stressScore: Int, timestamp: Long = System.currentTimeMillis(), isManual: Boolean = false) {
        val existing = stressDao.findSampleBetween(timestamp - 60000L, timestamp + 60000L)
        if (existing != null) {
            val updated = existing.copy(
                timestamp = timestamp,
                stressScore = stressScore,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            stressDao.update(updated)
            AppLog.d(tag, "Updated existing Stress sample within 60s in Room: $stressScore (manual=$isManual)")
        } else {
            val entity = StressEntity(
                timestamp = timestamp,
                stressScore = stressScore,
                recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
            )
            stressDao.insert(entity)
            AppLog.d(tag, "Saved Stress sample to Room: $stressScore (manual=$isManual)")
        }
    }

    suspend fun saveDailySteps(steps: Long, calories: Int, distanceMeters: Double, timestamp: Long = System.currentTimeMillis()) {
        val dateStr = dateFormat.format(Date(timestamp))
        val entity = DailyStepsEntity(
            date = dateStr,
            timestamp = timestamp,
            steps = steps,
            caloriesKcal = calories,
            distanceMeters = distanceMeters
        )
        stepsDao.insertOrUpdate(entity)
        AppLog.d(tag, "Saved Daily Steps to Room: $steps on $dateStr")
    }

    suspend fun saveSleepSession(session: SleepSession): Long {
        val dateStr = dateFormat.format(Date(session.endTime))
        val analysis = SleepAnalyzer.analyze(session, (sleepTargetHours * 60).toLong())
        val entity = SleepSessionEntity(
            date = dateStr,
            startTime = session.startTime,
            endTime = session.endTime,
            durationMinutes = session.durationMinutes,
            deepMinutes = analysis.deepMinutes,
            lightMinutes = analysis.lightMinutes,
            remMinutes = analysis.remMinutes,
            awakeMinutes = analysis.awakeMinutes,
            sleepScore = analysis.sleepScore,
            sleepEfficiency = analysis.sleepEfficiency,
            // Real minute-level segments ("start,end,STAGE;..."), read back with SleepSessionEntity.toSegments()
            stagesJson = SleepRecordParser.encodeStages(session.stages)
        )
        // Ensure single row per night by clearing old entry for date
        sleepDao.deleteForDate(dateStr)
        val id = sleepDao.insert(entity)
        AppLog.d(tag, "Saved Sleep Session to Room: $dateStr (single row deduplicated), score=${analysis.sleepScore}, segments=${session.stages.size}")
        return id
    }

    fun getHeartRateForDay(date: Date): Flow<List<HeartRateEntity>> {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis - 1
        return hrDao.getSamplesForDay(startOfDay, endOfDay)
    }

    fun getSpo2ForDay(date: Date): Flow<List<OxygenSaturationEntity>> {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis - 1
        return spo2Dao.getSamplesForDay(startOfDay, endOfDay)
    }

    fun getDailySteps(dateStr: String): Flow<DailyStepsEntity?> = stepsDao.getStepsForDate(dateStr)

    fun getAllDailySteps(): Flow<List<DailyStepsEntity>> = stepsDao.getAllDailySteps()

    fun getHeartRateBetween(startTime: Long, endTime: Long): Flow<List<HeartRateEntity>> =
        hrDao.getSamplesBetween(startTime, endTime)

    fun getSpo2Between(startTime: Long, endTime: Long): Flow<List<OxygenSaturationEntity>> =
        spo2Dao.getSamplesBetween(startTime, endTime)

    fun getHrvForDay(date: Date): Flow<List<HrvEntity>> {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis - 1
        return hrvDao.getSamplesForDay(startOfDay, endOfDay)
    }

    fun getStressForDay(date: Date): Flow<List<StressEntity>> {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis - 1
        return stressDao.getSamplesForDay(startOfDay, endOfDay)
    }

    fun getRecentHrv(): Flow<List<HrvEntity>> = hrvDao.getRecentSamples()

    fun getRecentStress(): Flow<List<StressEntity>> = stressDao.getRecentSamples()

    fun getSleepForDate(dateStr: String): Flow<SleepSessionEntity?> = sleepDao.getSleepForDate(dateStr)

    fun getAllSleepSessions(): Flow<List<SleepSessionEntity>> = sleepDao.getAllSleepSessions()

    fun getRecentSleepSessions(limit: Int = 14): Flow<List<SleepSessionEntity>> = sleepDao.getRecentSleepSessions(limit)

    fun getSleepSessionsInRange(startTime: Long, endTime: Long): Flow<List<SleepSessionEntity>> =
        sleepDao.getSleepSessionsInRange(startTime, endTime)

    fun getLatestHeartRate(): Flow<HeartRateEntity?> = hrDao.getLatestSample()

    fun getLatestSpo2(): Flow<OxygenSaturationEntity?> = spo2Dao.getLatestSample()

    fun getLatestSleepSession(): Flow<SleepSessionEntity?> =
        sleepDao.getRecentSleepSessions(1).map { it.firstOrNull() }

    suspend fun saveManualSleepSession(dateStr: String, startTime: Long, endTime: Long): Long {
        val durationMinutes = ((endTime - startTime) / 60000L).coerceAtLeast(30L)
        // Synthesize standard sleep stage distribution for manually logged sleep
        val deepMins = (durationMinutes * 0.22).toLong()
        val remMins = (durationMinutes * 0.24).toLong()
        val awakeMins = (durationMinutes * 0.06).toLong()
        val lightMins = durationMinutes - deepMins - remMins - awakeMins

        val stages = listOf(
            SleepStageRecord(SleepStage.LIGHT, startTime, startTime + (lightMins / 2) * 60000L),
            SleepStageRecord(SleepStage.DEEP, startTime + (lightMins / 2) * 60000L, startTime + (lightMins / 2 + deepMins) * 60000L),
            SleepStageRecord(SleepStage.REM, startTime + (lightMins / 2 + deepMins) * 60000L, startTime + (lightMins / 2 + deepMins + remMins) * 60000L),
            SleepStageRecord(SleepStage.LIGHT, startTime + (lightMins / 2 + deepMins + remMins) * 60000L, endTime - awakeMins * 60000L),
            SleepStageRecord(SleepStage.AWAKE, endTime - awakeMins * 60000L, endTime)
        )
        val session = SleepSession(
            startTime = startTime,
            endTime = endTime,
            stages = stages
        )
        val analysis = SleepAnalyzer.analyze(session, (sleepTargetHours * 60).toLong())
        // Delete any existing session for this date to avoid duplicate records
        sleepDao.deleteForDate(dateStr)

        val entity = SleepSessionEntity(
            date = dateStr,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            deepMinutes = deepMins,
            lightMinutes = lightMins,
            remMinutes = remMins,
            awakeMinutes = awakeMins,
            sleepScore = analysis.sleepScore,
            sleepEfficiency = analysis.sleepEfficiency,
            stagesJson = ""
        )
        val id = sleepDao.insert(entity)
        AppLog.d(tag, "Saved manual sleep override for date=$dateStr ($durationMinutes mins, score=${analysis.sleepScore})")
        return id
    }

    /**
     * Deduplicates history: collapses samples of same metric within 60s keeping the latest nonzero value.
     * Returns Pair(hrDuplicatesRemoved, spo2DuplicatesRemoved).
     */
    suspend fun deduplicateHistory(): Pair<Int, Int> {
        var hrRemoved = 0
        var spo2Removed = 0

        val allHr = hrDao.getAllSamplesAsc()
        var lastHr: HeartRateEntity? = null
        for (record in allHr) {
            if (lastHr != null && Math.abs(record.timestamp - lastHr.timestamp) <= 60000L) {
                // If current record has valid reading, keep it and delete older record
                if (record.bpm > 0) {
                    hrDao.deleteById(lastHr.id)
                    lastHr = record
                } else {
                    hrDao.deleteById(record.id)
                }
                hrRemoved++
            } else {
                lastHr = record
            }
        }

        val allSpo2 = spo2Dao.getAllSamplesAsc()
        var lastSpo2: OxygenSaturationEntity? = null
        for (record in allSpo2) {
            if (lastSpo2 != null && Math.abs(record.timestamp - lastSpo2.timestamp) <= 60000L) {
                if (record.percentage > 0) {
                    spo2Dao.deleteById(lastSpo2.id)
                    lastSpo2 = record
                } else {
                    spo2Dao.deleteById(record.id)
                }
                spo2Removed++
            } else {
                lastSpo2 = record
            }
        }

        val allHrv = hrvDao.getAllSamplesAsc()
        var lastHrv: HrvEntity? = null
        for (record in allHrv) {
            if (lastHrv != null && Math.abs(record.timestamp - lastHrv.timestamp) <= 60000L) {
                if (record.hrvMs > 0) {
                    hrvDao.deleteById(lastHrv.id)
                    lastHrv = record
                } else {
                    hrvDao.deleteById(record.id)
                }
            } else {
                lastHrv = record
            }
        }

        val allStress = stressDao.getAllSamplesAsc()
        var lastStress: StressEntity? = null
        for (record in allStress) {
            if (lastStress != null && Math.abs(record.timestamp - lastStress.timestamp) <= 60000L) {
                stressDao.deleteById(lastStress.id)
                lastStress = record
            } else {
                lastStress = record
            }
        }

        AppLog.i(tag, "History deduplication completed: removed $hrRemoved HR records and $spo2Removed SpO2 records")
        return Pair(hrRemoved, spo2Removed)
    }

    /**
     * Saves a list of workout sessions to Room DB.
     * Never invents synthetic sessions.
     */
    suspend fun saveWorkoutSessions(sessions: List<WorkoutSession>) {
        if (sessions.isEmpty()) return
        val entities = sessions.map { it.toEntity() }
        workoutDao.insertAll(entities)
        AppLog.i(tag, "Saved ${sessions.size} workout session(s) to Room DB")
    }

    /**
     * Saves a single workout session to Room DB.
     */
    suspend fun saveWorkoutSession(session: WorkoutSession): Long {
        val entity = session.toEntity()
        val id = workoutDao.insert(entity)
        AppLog.i(tag, "Saved workout session: id=$id, type=${session.sportTypeName}, dur=${session.durationSeconds}s")
        return id
    }

    /**
     * Returns workout sessions for a given date string ("yyyy-MM-dd").
     */
    fun getWorkoutsForDay(date: String): Flow<List<WorkoutSession>> {
        return workoutDao.getWorkoutsForDate(date).map { list ->
            list.map { it.toWorkoutSession() }
        }
    }

    /**
     * Returns workout sessions between two epoch timestamps.
     */
    fun getWorkoutsBetween(startTime: Long, endTime: Long): Flow<List<WorkoutSession>> {
        return workoutDao.getWorkoutsBetween(startTime, endTime).map { list ->
            list.map { it.toWorkoutSession() }
        }
    }

    /**
     * Returns all workout sessions sorted by newest first.
     */
    fun getAllWorkouts(): Flow<List<WorkoutSession>> {
        return workoutDao.getAllWorkouts().map { list ->
            list.map { it.toWorkoutSession() }
        }
    }

    /**
     * Returns a specific workout session by id.
     */
    fun getWorkoutById(id: Long): Flow<WorkoutSession?> {
        return workoutDao.getWorkoutById(id).map { it?.toWorkoutSession() }
    }

    /**
     * Clears all local biometric and workout history records from Room DB.
     */
    suspend fun clearAllHistory() {
        hrDao.deleteAll()
        spo2Dao.deleteAll()
        hrvDao.deleteAll()
        stressDao.deleteAll()
        stepsDao.deleteAll()
        sleepDao.deleteAll()
        workoutDao.deleteAll()
        AppLog.i(tag, "Cleared all local biometric and workout history from Room DB")
    }
}
