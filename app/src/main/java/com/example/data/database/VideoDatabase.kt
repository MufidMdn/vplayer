package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VideoRecord::class], version = 1, exportSchema = false)
abstract class VideoDatabase : RoomDatabase() {
    abstract val videoDao: VideoDao

    companion object {
        @Volatile
        private var INSTANCE: VideoDatabase? = null

        fun getDatabase(context: Context): VideoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VideoDatabase::class.java,
                    "video_player_pro_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
