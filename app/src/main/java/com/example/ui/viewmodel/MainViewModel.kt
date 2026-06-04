package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SampleVideos
import com.example.data.VideoRepository
import com.example.data.database.VideoRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val repository: VideoRepository) : ViewModel() {

    // Current tab routing state
    private val _currentTab = MutableStateFlow(0) // 0: Local Videos, 1: Folders, 2: Streaming, 3: History & Favorites
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Query Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected folder for filtering
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    // Local videos fetched from MediaStore
    private val _localVideos = MutableStateFlow<List<VideoRecord>>(emptyList())
    val localVideos: StateFlow<List<VideoRecord>> = _localVideos.asStateFlow()

    // DB elements (all known history, favorites, online etc)
    val historyVideos: StateFlow<List<VideoRecord>> = repository.playbackHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteVideos: StateFlow<List<VideoRecord>> = repository.favoriteVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All videos merged: Online samples + Local videos + Database records (merged for favorites & position)
    val allVideosCombined: StateFlow<List<VideoRecord>> = combine(
        _localVideos,
        repository.allDbVideos,
        _searchQuery
    ) { localList, dbList, query ->
        val map = dbList.associateBy { it.id }
        
        // Merge local videos with their DB equivalents (to get favorite status, last playback position, etc.)
        val mergedLocal = localList.map { local ->
            val match = map[local.id]
            if (match != null) {
                local.copy(
                    lastPosition = match.lastPosition,
                    lastPlayed = match.lastPlayed,
                    isFavorite = match.isFavorite,
                    resolution = match.resolution.ifEmpty { local.resolution }
                )
            } else {
                local
            }
        }

        // Add online items (if in DB, get updated position or favorite status)
        val onlineList = SampleVideos.list.map { online ->
            val match = map[online.id]
            if (match != null) {
                online.copy(
                    lastPosition = match.lastPosition,
                    lastPlayed = match.lastPlayed,
                    isFavorite = match.isFavorite
                )
            } else {
                online
            }
        }

        val combined = mergedLocal + onlineList

        if (query.isBlank()) {
            combined
        } else {
            combined.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SampleVideos.list)

    // Folder listing grouped from combined lists
    val folderedVideos: StateFlow<Map<String, List<VideoRecord>>> = allVideosCombined
        .map { list ->
            // Filter out online items from folders or list them under "Streaming Online"
            list.groupBy { it.folderName }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Automatically insert online videos if not preset in DB so they are registered for Favorites / Position
        viewModelScope.launch {
            for (sample in SampleVideos.list) {
                val existing = repository.getVideoById(sample.id)
                if (existing == null) {
                    repository.insertVideo(sample)
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _currentTab.value = index
        if (index != 1) {
            _selectedFolder.value = null // clear folder selection when switching context
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    fun refreshLocalVideos(context: Context) {
        viewModelScope.launch {
            val list = repository.fetchLocalVideos(context)
            _localVideos.value = list
        }
    }

    fun toggleFavorite(video: VideoRecord) {
        viewModelScope.launch {
            val updatedVal = !video.isFavorite
            // Ensure video exists in Room so that Room update statement touches it
            val existing = repository.getVideoById(video.id)
            if (existing == null) {
                repository.insertVideo(video.copy(isFavorite = updatedVal))
            } else {
                repository.toggleFavorite(video.id, updatedVal)
            }
        }
    }

    fun updatePlaybackPosition(id: String, path: String, title: String, position: Long, duration: Long, isOnline: Boolean, folderName: String) {
        viewModelScope.launch {
            val existing = repository.getVideoById(id)
            if (existing == null) {
                repository.insertVideo(
                    VideoRecord(
                        id = id,
                        title = title,
                        path = path,
                        duration = duration,
                        lastPosition = position,
                        lastPlayed = System.currentTimeMillis(),
                        isOnline = isOnline,
                        folderName = folderName
                    )
                )
            } else {
                repository.updatePlaybackPosition(id, position)
            }
        }
    }

    fun clearHistory(video: VideoRecord) {
        viewModelScope.launch {
            // Revert position to 0 and lastPlayed to 0
            val existing = repository.getVideoById(video.id)
            if (existing != null) {
                repository.insertVideo(existing.copy(lastPosition = 0L, lastPlayed = 0L))
            }
        }
    }

    class Factory(private val repository: VideoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
