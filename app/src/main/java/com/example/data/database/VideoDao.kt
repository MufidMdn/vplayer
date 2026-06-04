package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_records")
    fun getAllVideos(): Flow<List<VideoRecord>>

    @Query("SELECT * FROM video_records WHERE id = :id")
    suspend fun getVideoById(id: String): VideoRecord?

    @Query("SELECT * FROM video_records WHERE lastPlayed > 0 ORDER BY lastPlayed DESC")
    fun getPlaybackHistory(): Flow<List<VideoRecord>>

    @Query("SELECT * FROM video_records WHERE isFavorite = 1")
    fun getFavoriteVideos(): Flow<List<VideoRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoRecord)

    @Query("UPDATE video_records SET lastPosition = :position, lastPlayed = :lastPlayed WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, position: Long, lastPlayed: Long)

    @Query("UPDATE video_records SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM video_records WHERE id = :id")
    suspend fun deleteVideo(id: String)
}
