package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.LocationDao
import com.example.data.model.CheckpointReport
import com.example.data.model.SavedLocation
import com.example.data.model.SavedRoute

@Database(
    entities = [SavedLocation::class, CheckpointReport::class, SavedRoute::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gps_setter_pro.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
