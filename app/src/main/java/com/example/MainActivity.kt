package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.VideoDatabase
import com.example.data.database.VideoRecord
import com.example.data.VideoRepository
import com.example.ui.components.CustomVideoPlayer
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

sealed class Screen {
    object Dashboard : Screen()
    data class Player(val video: VideoRecord) : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var database: VideoDatabase
    private lateinit var repository: VideoRepository
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup Room & Repo
        database = VideoDatabase.getDatabase(this)
        repository = VideoRepository(database.videoDao)

        // Enable full transparent border & edge-to-edge
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "Screen Transition"
                    ) { target ->
                        when (target) {
                            Screen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = mainViewModel,
                                    onPlayVideo = { video ->
                                        // Enter video player fullscreen immersive
                                        toggleSystemBars(true)
                                        // Auto-request landscape orientation for best playback
                                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        currentScreen = Screen.Player(video)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            is Screen.Player -> {
                                val video = target.video
                                
                                // Direct back button handling
                                BackHandler {
                                    // Save current position & restore system layout
                                    mainViewModel.updatePlaybackPosition(
                                        id = video.id,
                                        path = video.path,
                                        title = video.title,
                                        position = 0L, // Handled internally inside returning player parameter
                                        duration = video.duration,
                                        isOnline = video.isOnline,
                                        folderName = video.folderName
                                    )
                                    toggleSystemBars(false)
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    currentScreen = Screen.Dashboard
                                }

                                CustomVideoPlayer(
                                    videoUrl = video.path,
                                    videoTitle = video.title,
                                    initialPosition = video.lastPosition,
                                    isOnline = video.isOnline,
                                    folderName = video.folderName,
                                    onBack = { lastPos ->
                                        // Back clicked from top bar -> sync state
                                        mainViewModel.updatePlaybackPosition(
                                            id = video.id,
                                            path = video.path,
                                            title = video.title,
                                            position = lastPos,
                                            duration = video.duration,
                                            isOnline = video.isOnline,
                                            folderName = video.folderName
                                        )
                                        toggleSystemBars(false)
                                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        currentScreen = Screen.Dashboard
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggleSystemBars(hide: Boolean) {
        val window: Window = window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

// Ensure Surface is fetched correctly matching colors
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = color,
        content = content
    )
}
