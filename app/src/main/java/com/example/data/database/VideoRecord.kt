package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_records")
data class VideoRecord(
    @PrimaryKey val id: String, // Path or URL is the unique identifier
    val title: String,
    val path: String,
    val duration: Long = 0L,
    val lastPosition: Long = 0L,
    val lastPlayed: Long = 0L,
    val isFavorite: Boolean = false,
    val isOnline: Boolean = false,
    val folderName: String = "Online",
    val fileSize: Long = 0L,
    val resolution: String = ""
)
