package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.database.VideoDao
import com.example.data.database.VideoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(private val videoDao: VideoDao) {

    val allDbVideos: Flow<List<VideoRecord>> = videoDao.getAllVideos()
    val playbackHistory: Flow<List<VideoRecord>> = videoDao.getPlaybackHistory()
    val favoriteVideos: Flow<List<VideoRecord>> = videoDao.getFavoriteVideos()

    suspend fun insertVideo(video: VideoRecord) {
        withContext(Dispatchers.IO) {
            videoDao.insertVideo(video)
        }
    }

    suspend fun getVideoById(id: String): VideoRecord? {
        return withContext(Dispatchers.IO) {
            videoDao.getVideoById(id)
        }
    }

    suspend fun updatePlaybackPosition(id: String, position: Long) {
        withContext(Dispatchers.IO) {
            videoDao.updatePlaybackPosition(id, position, System.currentTimeMillis())
        }
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            // First check if video exists in DB (local videos might not be in the DB yet)
            val existing = videoDao.getVideoById(id)
            if (existing != null) {
                videoDao.updateFavorite(id, isFavorite)
            } else {
                // If it is a local video, we insert it first, then set favorite
                // (This happens if we favorite a local video that isn't saved in database yet)
                // Let's rely on the caller passing a completed VideoRecord or handle it in insertion.
            }
        }
    }

    suspend fun deleteVideo(id: String) {
        withContext(Dispatchers.IO) {
            videoDao.deleteVideo(id)
        }
    }

    // Direct MediaStore query to read real local videos from the device.
    suspend fun fetchLocalVideos(context: Context): List<VideoRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoRecord>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA, // Path
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Video_$id"
                    val path = cursor.getString(pathColumn) ?: ""
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    
                    // Folder name is the parent folder of the file
                    val folderName = try {
                        val file = File(path)
                        file.parentFile?.name ?: "Penyimpanan Eksternal"
                    } catch (e: Exception) {
                        "Video"
                    }

                    // Look up if this item has history in DB before merging
                    val dbRecord = videoDao.getVideoById(uri) ?: videoDao.getVideoById(path)
                    
                    list.add(
                        VideoRecord(
                            id = uri, // Use MediaUri as the ID for content resolver playback
                            title = name,
                            path = uri, // Play via android content URI
                            duration = duration,
                            lastPosition = dbRecord?.lastPosition ?: 0L,
                            lastPlayed = dbRecord?.lastPlayed ?: 0L,
                            isFavorite = dbRecord?.isFavorite ?: false,
                            isOnline = false,
                            folderName = folderName,
                            fileSize = size,
                            resolution = dbRecord?.resolution ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error querying MediaStore", e)
        }

        list
    }
}
