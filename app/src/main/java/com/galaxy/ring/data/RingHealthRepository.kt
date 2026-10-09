package com.galaxy.ring.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private val prefs: SharedPreferences =
        context.getSharedPreferences("galaxy_ring_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            seedSampleHistoryIfEmpty()
        }
    }

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

    suspend fun saveHeartRate(bpm: Int, timestamp: Long = System.currentTimeMillis(), isManual: Boolean = false) {
        val entity = HeartRateEntity(
            timestamp = timestamp,
            bpm = bpm,
            recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
        )
        hrDao.insert(entity)
        Log.d(tag, "Saved HR sample to Room: $bpm bpm (manual=$isManual)")
    }

    suspend fun saveOxygenSaturation(percentage: Float, timestamp: Long = System.currentTimeMillis(), isManual: Boolean = false) {
        val entity = OxygenSaturationEntity(
            timestamp = timestamp,
            percentage = percentage,
            recordingMethod = if (isManual) "MANUAL" else "AUTOMATIC"
        )
        spo2Dao.insert(entity)
        Log.d(tag, "Saved SpO2 sample to Room: $percentage% (manual=$isManual)")
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
        Log.d(tag, "Saved Daily Steps to Room: $steps on $dateStr")
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
            stagesJson = "" // serialized if needed
        )
        val id = sleepDao.insert(entity)
        Log.d(tag, "Saved Sleep Session to Room: $dateStr, score=${analysis.sleepScore}")
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

    fun getSleepForDate(dateStr: String): Flow<SleepSessionEntity?> = sleepDao.getSleepForDate(dateStr)

    fun getAllSleepSessions(): Flow<List<SleepSessionEntity>> = sleepDao.getAllSleepSessions()

    fun getRecentSleepSessions(limit: Int = 14): Flow<List<SleepSessionEntity>> = sleepDao.getRecentSleepSessions(limit)

    fun getSleepSessionsInRange(startTime: Long, endTime: Long): Flow<List<SleepSessionEntity>> =
        sleepDao.getSleepSessionsInRange(startTime, endTime)

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
        Log.d(tag, "Saved manual sleep override for date=$dateStr ($durationMinutes mins, score=${analysis.sleepScore})")
        return id
    }

    /**
     * Seeds past 7 days of realistic biometric data if Room database is empty on first run.
     */
    private suspend fun seedSampleHistoryIfEmpty() {
        try {
            val existingSteps = stepsDao.getAllDailySteps().first()
            if (existingSteps.isNotEmpty()) return

            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()

            for (i in 6 downTo 0) {
                cal.timeInMillis = now - (i * 86400000L)
                val dateStr = dateFormat.format(cal.time)
                val dayStart = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis

                // Seed Steps
                val stepCount = (6000L..11500L).random()
                stepsDao.insertOrUpdate(
                    DailyStepsEntity(
                        date = dateStr,
                        timestamp = dayStart + 20 * 3600000L,
                        steps = stepCount,
                        caloriesKcal = (stepCount * 0.04).toInt(),
                        distanceMeters = stepCount * 0.76
                    )
                )

                // Seed HR Samples for the day
                val hrSamples = mutableListOf<HeartRateEntity>()
                for (hour in 7..22 step 2) {
                    val bpm = (62..86).random()
                    hrSamples.add(
                        HeartRateEntity(
                            timestamp = dayStart + hour * 3600000L + (10..50).random() * 60000L,
                            bpm = bpm,
                            recordingMethod = "AUTOMATIC"
                        )
                    )
                }
                hrDao.insertAll(hrSamples)

                // Seed SpO2 Samples
                val spo2Samples = mutableListOf<OxygenSaturationEntity>()
                for (hour in listOf(8, 14, 20)) {
                    val spo2 = (96..99).random().toFloat()
                    spo2Samples.add(
                        OxygenSaturationEntity(
                            timestamp = dayStart + hour * 3600000L,
                            percentage = spo2,
                            recordingMethod = "AUTOMATIC"
                        )
                    )
                }
                spo2Dao.insertAll(spo2Samples)

                // Seed Sleep for previous night
                val sleepEnd = dayStart + 7 * 3600000L + (10..30).random() * 60000L
                val durationMins = (420L..495L).random() // 7h - 8.2h
                val sleepStart = sleepEnd - durationMins * 60000L

                val deepMins = (durationMins * (18..24).random() / 100.0).toLong()
                val remMins = (durationMins * (20..26).random() / 100.0).toLong()
                val awakeMins = (durationMins * (4..7).random() / 100.0).toLong()
                val lightMins = durationMins - deepMins - remMins - awakeMins

                val efficiency = (durationMins - awakeMins).toFloat() / durationMins.toFloat()
                val score = (78..94).random()

                sleepDao.insert(
                    SleepSessionEntity(
                        date = dateStr,
                        startTime = sleepStart,
                        endTime = sleepEnd,
                        durationMinutes = durationMins,
                        deepMinutes = deepMins,
                        lightMinutes = lightMins,
                        remMinutes = remMins,
                        awakeMinutes = awakeMins,
                        sleepScore = score,
                        sleepEfficiency = efficiency,
                        stagesJson = ""
                    )
                )
            }
            Log.d(tag, "Successfully seeded initial biometric history into Room DB")
        } catch (e: Exception) {
            Log.w(tag, "Notice seeding initial history: ${e.message}")
        }
    }
}
