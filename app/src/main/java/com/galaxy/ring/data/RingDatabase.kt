package com.galaxy.ring.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HeartRateEntity::class,
        OxygenSaturationEntity::class,
        DailyStepsEntity::class,
        SleepSessionEntity::class,
        HrvEntity::class,
        StressEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class RingDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao
    abstract fun oxygenSaturationDao(): OxygenSaturationDao
    abstract fun dailyStepsDao(): DailyStepsDao
    abstract fun sleepDao(): SleepDao
    abstract fun hrvDao(): HrvDao
    abstract fun stressDao(): StressDao

    companion object {
        @Volatile
        private var INSTANCE: RingDatabase? = null

        fun getDatabase(context: Context): RingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RingDatabase::class.java,
                    "galaxy_ring_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
