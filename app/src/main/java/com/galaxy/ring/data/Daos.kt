package com.galaxy.ring.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HeartRateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HeartRateEntity>)

    @Query("SELECT * FROM heart_rate_records WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp ASC")
    fun getSamplesForDay(startOfDay: Long, endOfDay: Long): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rate_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getSamplesBetween(startTime: Long, endTime: Long): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rate_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSample(): Flow<HeartRateEntity?>

    @Query("SELECT * FROM heart_rate_records ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSamples(): Flow<List<HeartRateEntity>>

    @Query("DELETE FROM heart_rate_records")
    suspend fun deleteAll()
}

@Dao
interface OxygenSaturationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OxygenSaturationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<OxygenSaturationEntity>)

    @Query("SELECT * FROM oxygen_saturation_records WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp ASC")
    fun getSamplesForDay(startOfDay: Long, endOfDay: Long): Flow<List<OxygenSaturationEntity>>

    @Query("SELECT * FROM oxygen_saturation_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getSamplesBetween(startTime: Long, endTime: Long): Flow<List<OxygenSaturationEntity>>

    @Query("SELECT * FROM oxygen_saturation_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSample(): Flow<OxygenSaturationEntity?>

    @Query("SELECT * FROM oxygen_saturation_records ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSamples(): Flow<List<OxygenSaturationEntity>>

    @Query("DELETE FROM oxygen_saturation_records")
    suspend fun deleteAll()
}

@Dao
interface DailyStepsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DailyStepsEntity)

    @Query("SELECT * FROM daily_steps_records WHERE date = :date LIMIT 1")
    fun getStepsForDate(date: String): Flow<DailyStepsEntity?>

    @Query("SELECT * FROM daily_steps_records ORDER BY date DESC")
    fun getAllDailySteps(): Flow<List<DailyStepsEntity>>

    @Query("DELETE FROM daily_steps_records")
    suspend fun deleteAll()
}

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SleepSessionEntity): Long

    @Query("SELECT * FROM sleep_session_records WHERE date = :date ORDER BY endTime DESC LIMIT 1")
    fun getSleepForDate(date: String): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_session_records WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY startTime ASC")
    fun getSleepSessionsInRange(startTime: Long, endTime: Long): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_session_records ORDER BY endTime DESC LIMIT :limit")
    fun getRecentSleepSessions(limit: Int = 14): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_session_records ORDER BY endTime DESC")
    fun getAllSleepSessions(): Flow<List<SleepSessionEntity>>

    @Query("DELETE FROM sleep_session_records WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("DELETE FROM sleep_session_records")
    suspend fun deleteAll()
}
