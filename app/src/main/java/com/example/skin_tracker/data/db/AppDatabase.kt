package com.example.skin_tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.skin_tracker.data.entity.ComparisonEntity
import com.example.skin_tracker.data.entity.PhotoEntity

@Database(
    entities = [PhotoEntity::class, ComparisonEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun comparisonDao(): ComparisonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "skin_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
