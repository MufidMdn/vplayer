package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ScaleMode {
    FIT, FILL, ZOOM
}

@SuppressLint("SourceLockedOrientationActivity")
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun CustomVideoPlayer(
    videoUrl: String,
    videoTitle: String,
    initialPosition: Long,
    isOnline: Boolean,
    folderName: String,
    onBack: (Long) -> Unit, // Returns final played position when returning back
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Exoplayer State
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableStateOf(initialPosition) }
    var videoDuration by remember { mutableStateOf(0L) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var scaleMode by remember { mutableStateOf(ScaleMode.FIT) }
    var isBuffering by remember { mutableStateOf(true) }

    // Audio & Brightness managers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var currentBrightness by remember {
        mutableStateOf(
            activity?.window?.attributes?.screenBrightness?.let { if (it < 0f) 0.5f else it } ?: 0.5f
        )
    }

    // UI State
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var audioTrackSelectorOpen by remember { mutableStateOf(false) }
    var speedSelectorOpen by remember { mutableStateOf(false) }

    // Gesture Popups overlay indicators
    var gestureOverlayType by remember { mutableStateOf<String?>(null) } // "volume", "brightness", "seek"
    var gestureOverlayValue by remember { mutableStateOf("") }
    var gestureOverlayIcon by remember { mutableStateOf(Icons.Filled.VolumeUp) }

    // Track selections
    var availableAudioTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var activeAudioTrackIdx by remember { mutableStateOf(-1) }

    // Initialize ExoPlayer
    LaunchedEffect(videoUrl) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(videoUrl))
            seekTo(initialPosition)
            prepare()
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    videoDuration = exoPlayer.duration
                    
                    // Fetch tracks
                    val list = mutableListOf<Pair<Int, String>>()
                    val groups = exoPlayer.currentTracks.groups
                    var idx = 0
                    for (group in groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                val trackName = group.getTrackFormat(i).language ?: "Track Audio ${idx + 1}"
                                list.add(idx to trackName)
                                if (group.isTrackSelected(i)) {
                                    activeAudioTrackIdx = idx
                                }
                                idx++
                            }
                        }
                    }
                    availableAudioTracks = list
                }
            }
        })

        player = exoPlayer
    }

    // Track state periodically to persist the current playback position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let {
                playbackPosition = it.currentPosition
            }
            delay(1000L)
        }
    }

    // Dispose player when exiting Composable
    DisposableEffect(Unit) {
        onDispose {
            player?.let {
                it.stop()
                it.release()
            }
            // Restore default screen brightness
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = -1f // System default
            }
        }
    }

    // Hide controls automatically after timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000L)
            showControls = false
        }
    }

    // Handle gesture updates
    var dragAccumulatorX = 0f
    var dragAccumulatorY = 0f

    val triggerOverlayTimer: (String, String, androidx.compose.ui.graphics.vector.ImageVector) -> Unit =
        { type, valStr, icon ->
            gestureOverlayType = type
            gestureOverlayValue = valStr
            gestureOverlayIcon = icon
            scope.launch {
                delay(1200L)
                if (gestureOverlayType == type) {
                    gestureOverlayType = null
                }
            }
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val third = size.width / 3f
                            if (offset.x < third) {
                                // Double tap LEFT -> Seek back 10s
                                player?.let {
                                    val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                    it.seekTo(newPos)
                                    playbackPosition = newPos
                                    triggerOverlayTimer(
                                        "seek",
                                        "-10s (${formatDuration(newPos)})",
                                        Icons.Filled.Replay10
                                    )
                                }
                            } else if (offset.x > third * 2) {
                                // Double tap RIGHT -> Seek forward 10s
                                player?.let {
                                    val newPos = (it.currentPosition + 10000).coerceAtMost(videoDuration)
                                    it.seekTo(newPos)
                                    playbackPosition = newPos
                                    triggerOverlayTimer(
                                        "seek",
                                        "+10s (${formatDuration(newPos)})",
                                        Icons.Filled.Forward10
                                    )
                                }
                            } else {
                                // Double tap CENTER -> Play/Pause
                                player?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                }
                            }
                        }
                    },
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragAccumulatorX = 0f
                        dragAccumulatorY = 0f
                    },
                    onDragEnd = {
                        dragAccumulatorX = 0f
                        dragAccumulatorY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatorX += dragAmount.x
                        dragAccumulatorY += dragAmount.y

                        val width = size.width
                        val height = size.height

                        if (abs(dragAccumulatorY) > abs(dragAccumulatorX)) {
                            // Vertical Drag -> Volume or Brightness
                            val swipeThreshold = 10f
                            if (abs(dragAmount.y) > 1) {
                                if (change.position.x < width / 2) {
                                    // Left side -> Brightness
                                    val delta = -dragAmount.y / height
                                    currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                    activity?.window?.attributes = activity?.window?.attributes?.apply {
                                        screenBrightness = currentBrightness
                                    }
                                    val pct = (currentBrightness * 100).roundToInt()
                                    triggerOverlayTimer(
                                        "brightness",
                                        "$pct%",
                                        if (pct < 30) Icons.Outlined.BrightnessLow else if (pct < 70) Icons.Outlined.BrightnessMedium else Icons.Outlined.BrightnessHigh
                                    )
                                } else {
                                    // Right side -> Volume
                                    val delta = -dragAmount.y / height
                                    val volStep = maxVolume * delta
                                    currentVolume = (currentVolume + volStep).coerceIn(0f, maxVolume)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        currentVolume.roundToInt(),
                                        0
                                    )
                                    val pct = ((currentVolume / maxVolume) * 100).roundToInt()
                                    triggerOverlayTimer(
                                        "volume",
                                        "$pct%",
                                        if (pct == 0) Icons.Filled.VolumeMute else if (pct < 50) Icons.Filled.VolumeDown else Icons.Filled.VolumeUp
                                    )
                                }
                            }
                        } else {
                            // Horizontal Drag -> Seeking
                            if (abs(dragAmount.x) > 2) {
                                player?.let {
                                    // Fast seek based on horizontal drag delta (multiplier of video duration to scale nicely)
                                    val scale = (videoDuration.toFloat() / width) * 0.75f
                                    val seekDelta = (dragAmount.x * scale).toLong()
                                    val newPos = (it.currentPosition + seekDelta).coerceIn(0L, videoDuration)
                                    it.seekTo(newPos)
                                    playbackPosition = newPos
                                    triggerOverlayTimer(
                                        "seek",
                                        "${if (dragAmount.x > 0) "+" else ""}${formatDuration(newPos)}",
                                        if (dragAmount.x > 0) Icons.Filled.FastForward else Icons.Filled.FastRewind
                                    )
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Exoplayer Core View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // We design all controllers in gorgeous compose
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = when (scaleMode) {
                    ScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    ScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            }
        )

        // Loading/Buffer indicator
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                modifier = Modifier.size(56.dp)
            )
        }

        // Gesture Info Popup Overlay
        AnimatedVisibility(
            visible = gestureOverlayType != null,
            enter = scaleIn(animationSpec = tween(150)) + fadeIn(),
            exit = scaleOut(animationSpec = tween(150)) + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = gestureOverlayIcon,
                        contentDescription = "Overlay Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = gestureOverlayValue,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Gorgeous custom controls HUD
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            ) {
                // LOCK state handling
                if (isLocked) {
                    // Locked Overlay: only show a small cute Padlock button to unlock
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(24.dp)
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("unlock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Unlock screen controls",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    // --- FULL HUD ---
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { onBack(playbackPosition) },
                                modifier = Modifier.testTag("app_bar_back")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = Color.White
                                )
                            }
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = videoTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Right Top Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Aspect Ratio Mode Cycle
                            IconButton(
                                onClick = {
                                    scaleMode = when (scaleMode) {
                                        ScaleMode.FIT -> ScaleMode.FILL
                                        ScaleMode.FILL -> ScaleMode.ZOOM
                                        ScaleMode.ZOOM -> ScaleMode.FIT
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = when (scaleMode) {
                                        ScaleMode.FIT -> Icons.Filled.AspectRatio
                                        ScaleMode.FILL -> Icons.Filled.Fullscreen
                                        ScaleMode.ZOOM -> Icons.Filled.FilterCenterFocus
                                    },
                                    contentDescription = "Aspek Rasio",
                                    tint = Color.White
                                )
                            }

                            // Playback Speed Trigger
                            IconButton(onClick = { speedSelectorOpen = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = "Kecepatan Putar",
                                    tint = Color.White
                                )
                            }

                            // Audio Selection Indicator (if has multitrack)
                            if (availableAudioTracks.isNotEmpty()) {
                                IconButton(onClick = { audioTrackSelectorOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Audiotrack,
                                        contentDescription = "Pilih Audio track",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Lock GUI Button
                            IconButton(onClick = { isLocked = true }) {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = "Kunci Layar",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // CENTER PLAYBACK COMMANDS
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        IconButton(
                            onClick = {
                                player?.let {
                                    it.seekTo((it.currentPosition - 10000).coerceAtLeast(0))
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Mundur 10 detik",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Big Play/Pause Ring Button
                        IconButton(
                            onClick = {
                                player?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                }
                            },
                            modifier = Modifier
                                .size(76.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    CircleShape
                                )
                                .testTag("center_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play Pause",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                player?.let {
                                    it.seekTo((it.currentPosition + 10000).coerceAtMost(videoDuration))
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Maju 10 detik",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // BOTTOM TIME TRACK SLIDER
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(playbackPosition),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                text = formatDuration(videoDuration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = if (videoDuration > 0) playbackPosition.toFloat() / videoDuration else 0f,
                            onValueChange = { fraction ->
                                player?.let {
                                    val target = (fraction * videoDuration).toLong()
                                    it.seekTo(target)
                                    playbackPosition = target
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .testTag("playback_slider")
                        )
                    }
                }
            }
        }
    }

    // SPEED SELECTOR DIALOG
    if (speedSelectorOpen) {
        AlertDialog(
            onDismissRequest = { speedSelectorOpen = false },
            title = { Text("Pilih Kecepatan Putar", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSpeed = speed
                                    player?.setPlaybackSpeed(speed)
                                    speedSelectorOpen = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${speed}x ${if (speed == 1.0f) "(Normal)" else ""}",
                                color = if (currentSpeed == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (currentSpeed == speed) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Terpilih",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // AUDIO TRACK SELECTOR DIALOG
    if (audioTrackSelectorOpen) {
        AlertDialog(
            onDismissRequest = { audioTrackSelectorOpen = false },
            title = { Text("Pilih Saluran Audio", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    availableAudioTracks.forEach { (index, langName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    player?.let {
                                        // Set specific track override
                                        val builder = it.trackSelectionParameters.buildUpon()
                                        // Simple track overriding
                                        it.trackSelectionParameters = builder
                                            .setPreferredAudioLanguage(langName)
                                            .build()
                                        activeAudioTrackIdx = index
                                    }
                                    audioTrackSelectorOpen = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = langName,
                                color = if (activeAudioTrackIdx == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (activeAudioTrackIdx == index) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Terpilih",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// Format milliseconds to digital style HH:MM:SS or MM:SS
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
