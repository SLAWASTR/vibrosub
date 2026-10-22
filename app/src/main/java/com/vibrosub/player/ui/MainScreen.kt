package com.vibrosub.player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vibrosub.player.audio.DeviceAudioItem
import com.vibrosub.player.model.HapticEngineMode
import com.vibrosub.player.player.MusicPlayerController
import com.vibrosub.player.ui.components.HapticSettingsCard
import com.vibrosub.player.ui.components.HapticTestPad
import com.vibrosub.player.ui.components.PixelDiagnosticsCard
import com.vibrosub.player.ui.theme.BassPunchOrange
import com.vibrosub.player.ui.theme.DeepBackground
import com.vibrosub.player.ui.theme.ElectricCyan
import com.vibrosub.player.ui.theme.SubwooferViolet
import com.vibrosub.player.ui.theme.SurfaceBorder
import com.vibrosub.player.ui.theme.SurfaceDark
import com.vibrosub.player.ui.theme.SurfaceDarkElevated
import com.vibrosub.player.ui.theme.TextPrimary
import com.vibrosub.player.ui.theme.TextSecondary
import com.vibrosub.player.ui.theme.TextTertiary
import com.vibrosub.player.ui.theme.VividGreen
import java.util.Locale

@Composable
fun MainScreen(controller: MusicPlayerController) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Now Playing (1)

    val trackInfo by controller.trackInfo.collectAsState()
    val settings by controller.settings.collectAsState()
    val bassPulse by controller.bassPulse.collectAsState()
    val spectrumBars by controller.spectrumBars.collectAsState()
    val activeHapticLabel by controller.activeHapticLabel.collectAsState()
    val deviceTracks by controller.deviceTracks.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Permissions
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRecordAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        controller.refreshDeviceTracks()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    LaunchedEffect(hasRecordAudioPermission) {
        if (hasRecordAudioPermission) {
            controller.attachAudioEffects()
        }
    }

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri)
            controller.loadUri(uri, title = fileName, artist = "Файл с устройства")
            selectedTab = 1 // Go to Now Playing
        }
    }

    Scaffold(
        containerColor = DeepBackground,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(SurfaceDark)) {
                // Spotify-Style Mini Player Bar (shown on other tabs when a track is active)
                if (selectedTab != 1) {
                    SpotifyMiniPlayer(
                        title = trackInfo.title,
                        artist = trackInfo.artist,
                        isPlaying = trackInfo.isPlaying,
                        bassPulse = bassPulse,
                        activeHapticLabel = activeHapticLabel,
                        onBarClick = { selectedTab = 1 },
                        onPlayPauseClick = { controller.togglePlayPause() }
                    )
                }

                // Spotify Bottom Navigation Bar
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Медиатека") },
                        label = { Text("Медиатека", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VividGreen,
                            selectedTextColor = VividGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceDarkElevated
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Плеер") },
                        label = { Text("Плеер", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VividGreen,
                            selectedTextColor = VividGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceDarkElevated
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Вибро-Тюнинг") },
                        label = { Text("Вибро-Тюнинг", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            selectedTextColor = ElectricCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceDarkElevated
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Vibration, contentDescription = "Тест вибро") },
                        label = { Text("Тест вибро", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BassPunchOrange,
                            selectedTextColor = BassPunchOrange,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceDarkElevated
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: SPOTIFY-STYLE MEDIA LIBRARY
                    SpotifyLibraryTab(
                        deviceTracks = deviceTracks,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onPickFileClick = { audioPickerLauncher.launch("audio/*") },
                        onPlayRadioheadDemo = {
                            controller.loadRadioheadDemo()
                            selectedTab = 1
                        },
                        onPlay808Demo = {
                            controller.loadDemoBeat()
                            selectedTab = 1
                        },
                        onTrackSelected = { track ->
                            controller.playTrack(track)
                            selectedTab = 1
                        },
                        currentTrackUri = trackInfo.uri,
                        isPlaying = trackInfo.isPlaying,
                        onRefreshTracks = { controller.refreshDeviceTracks() }
                    )
                }

                1 -> {
                    // TAB 1: FULL SPOTIFY-STYLE NOW PLAYING VIEW
                    SpotifyNowPlayingTab(
                        controller = controller,
                        trackTitle = trackInfo.title,
                        artist = trackInfo.artist,
                        album = trackInfo.album,
                        isPlaying = trackInfo.isPlaying,
                        currentPositionMs = trackInfo.currentPositionMs,
                        durationMs = trackInfo.durationMs,
                        bassPulse = bassPulse,
                        spectrumBars = spectrumBars,
                        activeHapticLabel = activeHapticLabel,
                        settings = settings,
                        onSeek = { controller.seekTo(it) },
                        onTogglePlay = { controller.togglePlayPause() },
                        onPrev = { controller.playPrevious() },
                        onNext = { controller.playNext() },
                        onSettingsChange = { controller.updateSettings(it) }
                    )
                }

                2 -> {
                    // TAB 2: HAPTICS TUNING STUDIO
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        HapticSettingsCard(
                            settings = settings,
                            onSettingsChanged = { controller.updateSettings(it) }
                        )
                    }
                }

                3 -> {
                    // TAB 3: PIXEL 8 PRO HARDWARE DIAGNOSTICS & TEST
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        PixelDiagnosticsCard(status = controller.hardwareStatus)
                        Spacer(modifier = Modifier.height(16.dp))
                        HapticTestPad(hapticEngine = controller.hapticEngine)
                    }
                }
            }
        }
    }
}

/**
 * Spotify-Style Now Playing Full View
 */
@Composable
private fun SpotifyNowPlayingTab(
    controller: MusicPlayerController,
    trackTitle: String,
    artist: String,
    album: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bassPulse: Float,
    spectrumBars: FloatArray,
    activeHapticLabel: String,
    settings: com.vibrosub.player.model.HapticSettings,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettingsChange: (com.vibrosub.player.model.HapticSettings) -> Unit
) {
    val animatedPulse by animateFloatAsState(
        targetValue = if (isPlaying) bassPulse else 0f,
        animationSpec = tween(durationMillis = 50, easing = FastOutSlowInEasing),
        label = "pulse_anim"
    )

    val auraColor by animateColorAsState(
        targetValue = when {
            settings.engineMode == HapticEngineMode.ACOUSTIC_MELODY -> ElectricCyan
            animatedPulse > 0.6f -> BassPunchOrange
            animatedPulse > 0.3f -> SubwooferViolet
            else -> VividGreen
        },
        label = "aura_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "СЕЙЧАС ИГРАЕТ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontSize = 11.sp
                )
                Text(
                    text = settings.engineMode.displayName.split("(")[0].trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Tactile Power Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkElevated)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (settings.enabled) "${(settings.intensity * 100).toInt()}% МОЩЬ" else "MUTE",
                    color = if (settings.enabled) BassPunchOrange else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Large Album Art Box with Tactile Neon Glow Ring
        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(
                    elevation = (12.dp + (24.dp * animatedPulse)),
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = auraColor,
                    spotColor = auraColor
                )
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceDarkElevated,
                            Color(0xFF13151F),
                            DeepBackground
                        )
                    )
                )
                .border(
                    width = (2.dp + (3.5.dp * animatedPulse)),
                    brush = Brush.linearGradient(
                        colors = listOf(auraColor, SubwooferViolet, auraColor)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp + (30.dp * animatedPulse))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(auraColor.copy(alpha = 0.35f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (settings.engineMode == HapticEngineMode.ACOUSTIC_MELODY) Icons.Default.MusicNote else Icons.Default.Vibration,
                        contentDescription = "Cover Icon",
                        tint = auraColor,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "VIBROSUB OMNI-HAPTICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Live Tactile Status Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDarkElevated)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) VividGreen else TextTertiary)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activeHapticLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPlaying) ElectricCyan else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${(settings.intensity * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BassPunchOrange
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Track Title & Artist
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$artist • $album",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Playback Scrubber Slider
        val curPos = currentPositionMs.toFloat()
        val totalDur = durationMs.coerceAtLeast(1L).toFloat()

        Slider(
            value = (curPos / totalDur).coerceIn(0f, 1f),
            onValueChange = { frac ->
                onSeek((frac * totalDur).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = VividGreen,
                activeTrackColor = VividGreen,
                inactiveTrackColor = SurfaceBorder
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = formatTime(durationMs), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Spotify Controls Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Shuffle toggle */ }) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = TextSecondary, modifier = Modifier.size(22.dp))
            }

            IconButton(onClick = onPrev) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(34.dp))
            }

            FloatingActionButton(
                onClick = onTogglePlay,
                containerColor = VividGreen,
                contentColor = DeepBackground,
                shape = CircleShape,
                modifier = Modifier.size(66.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(38.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(34.dp))
            }

            IconButton(onClick = { /* Repeat toggle */ }) {
                Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Tactile Intensity Slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkElevated)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, contentDescription = null, tint = BassPunchOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Тактильная сила мотора:", fontSize = 13.sp, color = TextSecondary)
                    }
                    Text(
                        text = "${(settings.intensity * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = BassPunchOrange,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = settings.intensity,
                    onValueChange = { onSettingsChange(settings.copy(intensity = it)) },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = BassPunchOrange, activeTrackColor = BassPunchOrange)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Chips
        Text(
            text = "БЫСТРЫЕ РЕЖИМЫ ТАКТИЛЬНОСТИ:",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                title = "🔔 Акустика",
                isSelected = settings.engineMode == HapticEngineMode.ACOUSTIC_MELODY,
                accentColor = ElectricCyan,
                onClick = { onSettingsChange(settings.copy(engineMode = HapticEngineMode.ACOUSTIC_MELODY)) },
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                title = "🎧 Погружение",
                isSelected = settings.engineMode == HapticEngineMode.FULL_IMMERSION,
                accentColor = VividGreen,
                onClick = { onSettingsChange(settings.copy(engineMode = HapticEngineMode.FULL_IMMERSION)) },
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                title = "⚡ Макс Буст",
                isSelected = settings.engineMode == HapticEngineMode.MAX_PUNCH,
                accentColor = BassPunchOrange,
                onClick = { onSettingsChange(settings.copy(engineMode = HapticEngineMode.MAX_PUNCH)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mini Spectrum Bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDarkElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val bars = if (isPlaying && spectrumBars.isNotEmpty()) spectrumBars else FloatArray(16) { 0.08f }
            bars.forEachIndexed { i, v ->
                val h = (v.coerceIn(0.06f, 1f) * 20f).dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < 4) BassPunchOrange else if (i < 9) ElectricCyan else SubwooferViolet)
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    title: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else SurfaceDarkElevated)
            .border(1.dp, if (isSelected) accentColor else SurfaceBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) accentColor else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Spotify-Style Library Tab with Local Songs and Demo Tracks
 */
@Composable
private fun SpotifyLibraryTab(
    deviceTracks: List<DeviceAudioItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPickFileClick: () -> Unit,
    onPlayRadioheadDemo: () -> Unit,
    onPlay808Demo: () -> Unit,
    onTrackSelected: (DeviceAudioItem) -> Unit,
    currentTrackUri: Uri?,
    isPlaying: Boolean,
    onRefreshTracks: () -> Unit
) {
    val filteredTracks = remember(deviceTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            deviceTracks
        } else {
            deviceTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Library Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ваша медиатека",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                IconButton(onClick = onPickFileClick) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Pick file",
                        tint = ElectricCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск песен или исполнителей...", color = TextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDarkElevated,
                    unfocusedContainerColor = SurfaceDarkElevated,
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Demo Tracks Section
            Text(
                text = "ДЕМО-ТРЕКИ VIBROSUB",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 1.2.sp,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Radiohead No Surprises Demo Card
            DemoTrackCard(
                title = "Radiohead - No Surprises",
                subtitle = "Акустика, переборы струн и колокольчики",
                tag = "🔔 АКУСТИКА",
                accentColor = ElectricCyan,
                onClick = onPlayRadioheadDemo
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 808 Trap Sub-Bass Demo Card
            DemoTrackCard(
                title = "808 Sub-Bass Trap Drop",
                subtitle = "Ударная бочка и рокот сабвуфера",
                tag = "🥁 СУПЕР-БИТ",
                accentColor = BassPunchOrange,
                onClick = onPlay808Demo
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "МУЗЫКА НА УСТРОЙСТВЕ (${filteredTracks.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp,
                    fontSize = 11.sp
                )

                Text(
                    text = "Обновить",
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onRefreshTracks() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredTracks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkElevated)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Аудиофайлы не найдены в памяти" else "Ничего не найдено по запросу",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Вы можете выбрать любой трек через проводник",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onPickFileClick,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Выбрать файл", color = DeepBackground, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(filteredTracks, key = { it.id }) { track ->
                val isCurrent = currentTrackUri == track.uri
                TrackRowItem(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying && isCurrent,
                    onClick = { onTrackSelected(track) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DemoTrackCard(
    title: String,
    subtitle: String,
    tag: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkElevated),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tag,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrackRowItem(
    track: DeviceAudioItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) SurfaceDarkElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isCurrent) VividGreen.copy(alpha = 0.2f) else SurfaceDarkElevated),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Playing",
                    tint = VividGreen,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) VividGreen else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) VividGreen else TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${formatTime(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Spotify-Style Floating Mini Player Bar
 */
@Composable
private fun SpotifyMiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    bassPulse: Float,
    activeHapticLabel: String,
    onBarClick: () -> Unit,
    onPlayPauseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDarkElevated)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onBarClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPlaying) ElectricCyan else SurfaceBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = DeepBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isPlaying) activeHapticLabel else artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPlaying) ElectricCyan else TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = VividGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
    }
    return result ?: uri.lastPathSegment ?: "Аудиозапись"
}
