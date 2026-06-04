package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.database.VideoRecord
import com.example.ui.components.FolderListItem
import com.example.ui.components.VideoListItem
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onPlayVideo: (VideoRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()

    // Combined/Filtered Video states
    val allVideosCombined by viewModel.allVideosCombined.collectAsState()
    val folderedVideos by viewModel.folderedVideos.collectAsState()
    val historyVideos by viewModel.historyVideos.collectAsState()
    val favoriteVideos by viewModel.favoriteVideos.collectAsState()

    // Pasting URL dialog state
    var showUrlDialog by remember { mutableStateOf(false) }

    // Dynamic Permission String based on API level
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Permission state tracker
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Register permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.refreshLocalVideos(context)
        }
    }

    // Automatically trigger local video refresh upon launch of first screen if permission is available
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.refreshLocalVideos(context)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                // Header Brand Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Video Player Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Network Link quick launch button
                    IconButton(
                        onClick = { showUrlDialog = true },
                        modifier = Modifier.testTag("network_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = "Putar URL Jaringan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Modern Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Cari video lokal & online...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Pencarian") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("main_search_bar")
                )

                Spacer(modifier = Modifier.height(10.dp))
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(if (currentTab == 0) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary, contentDescription = "Lokal") },
                    label = { Text("Lokal") },
                    modifier = Modifier.testTag("tab_local")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(if (currentTab == 1) Icons.Filled.FolderCopy else Icons.Outlined.FolderCopy, contentDescription = "Folder") },
                    label = { Text("Folder") },
                    modifier = Modifier.testTag("tab_folders")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(if (currentTab == 2) Icons.Filled.WifiChannel else Icons.Outlined.WifiChannel, contentDescription = "Streaming") },
                    label = { Text("Streaming") },
                    modifier = Modifier.testTag("tab_streaming")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(if (currentTab == 3) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = "Saya") },
                    label = { Text("Saya") },
                    modifier = Modifier.testTag("tab_profile")
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 2) {
                FloatingActionButton(
                    onClick = { showUrlDialog = true },
                    modifier = Modifier.testTag("fab_streaming"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Tambah Link Online")
                }
            }
        }
    ) { innerPadding ->
        // Render content tabs dynamically inside Scaffold container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> {
                    // TAB 0: LOCAL VIDEOS
                    if (!hasPermission) {
                        PermissionRequestLayout(
                            onRequestPermission = { permissionLauncher.launch(storagePermission) }
                        )
                    } else {
                        // Filter local videos
                        val localList = allVideosCombined.filter { !it.isOnline }
                        
                        if (localList.isEmpty()) {
                            EmptyStateLayout(
                                title = "Tidak Ada Video Lokal",
                                subtitle = "Kami tidak mendeteksi video di memori perangkat ini. Silakan tambahkan file video atau coba Tab Streaming!",
                                icon = Icons.Filled.VideoFile
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(localList, key = { it.id }) { video ->
                                    VideoListItem(
                                        video = video,
                                        onClick = { onPlayVideo(video) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(video) }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: FOLDERS
                    if (selectedFolder != null) {
                        // Viewing inside a folder
                        val folderVideos = folderedVideos[selectedFolder] ?: emptyList()
                        
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sub-header back tracker
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.selectFolder(null) },
                                    modifier = Modifier.testTag("back_from_folder")
                                ) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali ke folder")
                                }
                                Text(
                                    text = selectedFolder ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            if (folderVideos.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tidak ada video di folder ini")
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(folderVideos, key = { it.id }) { video ->
                                        VideoListItem(
                                            video = video,
                                            onClick = { onPlayVideo(video) },
                                            onFavoriteToggle = { viewModel.toggleFavorite(video) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Listing all directories
                        val folders = folderedVideos.keys.toList()
                        if (folders.isEmpty()) {
                            EmptyStateLayout(
                                title = "Belum Ada Folder Media",
                                subtitle = "Aktifkan izin penyimpanan atau putar video online untuk mengisi folder.",
                                icon = Icons.Filled.FolderOpen
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(folders) { folder ->
                                    val count = folderedVideos[folder]?.size ?: 0
                                    FolderListItem(
                                        name = folder,
                                        videoCount = count,
                                        onClick = { viewModel.selectFolder(folder) }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: STREAMING HUB
                    val onlineList = allVideosCombined.filter { it.isOnline }
                    
                    if (onlineList.isEmpty()) {
                        EmptyStateLayout(
                            title = "Tidak Ada Hasil",
                            subtitle = "Tidak ada tautan streaming yang cocok dengan pencarian Anda.",
                            icon = Icons.Filled.WifiOff
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Online Streaming Info Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VpnLock,
                                        contentDescription = "Streaming",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Streaming Video Instan",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Putar video HD langsung dari server publik atau masukkan tautan video milik Anda sendiri!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(onlineList, key = { it.id }) { video ->
                                    VideoListItem(
                                        video = video,
                                        onClick = { onPlayVideo(video) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(video) }
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // TAB 3: PERSONAL HISTORY & FAVORITES (SAYA)
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Section: History
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = "Riwayat",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Terakhir Diputar",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Clear history button
                            if (historyVideos.isNotEmpty()) {
                                TextButton(
                                    onClick = { 
                                        historyVideos.forEach { viewModel.clearHistory(it) }
                                    }
                                ) {
                                    Text("Hapus Semua", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (historyVideos.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = "Belum ada riwayat tontonan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // List horizontal or simple vertical history limit 3 items to save space
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(historyVideos, key = { "history_${it.id}" }) { video ->
                                    VideoListItem(
                                        video = video,
                                        onClick = { onPlayVideo(video) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(video) }
                                    )
                                }
                            }
                        }

                        // Section: Favorites
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Favorit",
                                tint = Color.Red
                            )
                            Text(
                                text = "Favorit Saya",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (favoriteVideos.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = "Ketuk ikon hati pada video untuk memfavoritkan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(favoriteVideos, key = { "favorite_${it.id}" }) { video ->
                                    VideoListItem(
                                        video = video,
                                        onClick = { onPlayVideo(video) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(video) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // STREAMING URL PASTER DIALOG
    if (showUrlDialog) {
        var urlValue by remember { mutableStateOf(TextFieldValue("")) }
        var titleValue by remember { mutableStateOf(TextFieldValue("")) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = "URL",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Putar Tautan Jaringan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Masukkan direct stream URL (misal: mp4, m3u8, mkv, ksp, dll.) untuk diputar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = titleValue,
                        onValueChange = { titleValue = it },
                        label = { Text("Nama Video (Opsional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_video_name")
                    )

                    OutlinedTextField(
                        value = urlValue,
                        onValueChange = { 
                            urlValue = it
                            isError = false
                        },
                        label = { Text("Alamat URL Video") },
                        placeholder = { Text("https://example.com/video.mp4") },
                        singleLine = true,
                        isError = isError,
                        supportingText = { if (isError) Text("URL video tidak boleh kosong!") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_video_url")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = urlValue.text.trim()
                        if (path.isEmpty()) {
                            isError = true
                        } else {
                            val finalTitle = titleValue.text.trim().ifEmpty { "Aliran Jaringan Kustom" }
                            val id = "stream_${System.currentTimeMillis()}"
                            val customRecord = VideoRecord(
                                id = id,
                                title = finalTitle,
                                path = path,
                                isOnline = true,
                                folderName = "Putar Tautan Kustom",
                                resolution = "Web Stream"
                            )

                            showUrlDialog = false
                            onPlayVideo(customRecord)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("dialog_confirm_play")
                ) {
                    Text("Putar Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun PermissionRequestLayout(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Permission Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Akses Penyimpanan Diperlukan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Untuk mendeteksi dan memutar video lokal dari folder perangkat Anda seperti MX Player, berikan izin akses ke galeri video.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("request_permission_button")
                ) {
                    Text("Berikan Izin Akses", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyStateLayout(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Empty icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
