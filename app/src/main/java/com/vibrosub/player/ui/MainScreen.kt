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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vibrosub.player.player.MusicPlayerController
import com.vibrosub.player.ui.components.HapticSettingsCard
import com.vibrosub.player.ui.components.HapticTestPad
import com.vibrosub.player.ui.components.PixelDiagnosticsCard
import com.vibrosub.player.ui.components.SubwooferVisualizer
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
import androidx.compose.ui.graphics.Color
import java.util.Locale

@Composable
fun MainScreen(controller: MusicPlayerController) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val trackInfo by controller.trackInfo.collectAsState()
    val settings by controller.settings.collectAsState()
    val bassPulse by controller.bassPulse.collectAsState()
    val spectrumBars by controller.spectrumBars.collectAsState()
    val activeHapticLabel by controller.activeHapticLabel.collectAsState()

    // Permission state
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
            controller.loadUri(uri, title = fileName, artist = "Файл из памяти")
        }
    }

    Scaffold(
        containerColor = DeepBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = "Logo",
                                tint = DeepBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "VibroSub",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Тактильный сабвуфер для Pixel 8 Pro",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Haptic Mode Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDarkElevated)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (settings.enabled) "${(settings.intensity * 100).toInt()}% BASS" else "MUTE",
                            color = if (settings.enabled) BassPunchOrange else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = ElectricCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ElectricCyan,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Плеер", fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Вибро-Тюнинг", fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Тест Pixel", fontSize = 13.sp)
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission warning if RECORD_AUDIO not granted
            AnimatedVisibility(visible = !hasRecordAudioPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331B1B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = BassPunchOrange,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Требуется разрешение на анализ звука",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Android требует 'Запись аудио' для работы Visualizer (анализа частот баса в плеере без микрофона).",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BassPunchOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Дать", fontSize = 12.sp)
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: PLAYER & SUBWOOFER VISUALIZER
                    SubwooferVisualizer(
                        bassPulse = bassPulse,
                        spectrumBars = spectrumBars,
                        isPlaying = trackInfo.isPlaying
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Tactile Activity Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDarkElevated)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (trackInfo.isPlaying) VividGreen else TextTertiary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeHapticLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (trackInfo.isPlaying) ElectricCyan else TextSecondary
                            )
                        }

                        Text(
                            text = "${(settings.intensity * 100).toInt()}% МОЩЬ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BassPunchOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Track Information Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkElevated)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = trackInfo.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = trackInfo.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Playback Progress Slider
                            val currentPos = trackInfo.currentPositionMs.toFloat()
                            val totalDur = trackInfo.durationMs.coerceAtLeast(1L).toFloat()

                            Slider(
                                value = (currentPos / totalDur).coerceIn(0f, 1f),
                                onValueChange = { frac ->
                                    controller.seekTo((frac * totalDur).toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricCyan,
                                    activeTrackColor = ElectricCyan,
                                    inactiveTrackColor = SurfaceBorder
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(trackInfo.currentPositionMs),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = formatTime(trackInfo.durationMs),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Playback Control Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        controller.seekTo(
                                            (trackInfo.currentPositionMs - 10000L).coerceAtLeast(0L)
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Rewind 10s",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(20.dp))

                                FloatingActionButton(
                                    onClick = { controller.togglePlayPause() },
                                    containerColor = ElectricCyan,
                                    contentColor = DeepBackground,
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Icon(
                                        imageVector = if (trackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(20.dp))

                                IconButton(
                                    onClick = {
                                        controller.seekTo(
                                            (trackInfo.currentPositionMs + 10000L).coerceAtMost(trackInfo.durationMs)
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Forward 10s",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Track Loader Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkElevated),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выбрать трек", color = TextPrimary, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { controller.loadDemoBeat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SubwooferViolet),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Demo",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("808 Демо-бит", color = TextPrimary, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Instant Haptic Showcase Button
                    Button(
                        onClick = { controller.testShowcase() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkElevated),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Test",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Проверить виброотклик Pixel (Kick ➔ Snare ➔ Rise)",
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Vibration Intensity Slider Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkElevated)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Vibration,
                                        contentDescription = null,
                                        tint = BassPunchOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Быстрая сила вибрации:", fontSize = 13.sp, color = TextSecondary)
                                }
                                Text(
                                    text = "${(settings.intensity * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    color = BassPunchOrange
                                )
                            }
                            Slider(
                                value = settings.intensity,
                                onValueChange = { controller.updateSettings(settings.copy(intensity = it)) },
                                valueRange = 0.0f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BassPunchOrange,
                                    activeTrackColor = BassPunchOrange
                                )
                            )
                        }
                    }
                }

                1 -> {
                    // TAB 1: ADVANCED HAPTICS TUNING
                    HapticSettingsCard(
                        settings = settings,
                        onSettingsChanged = { controller.updateSettings(it) }
                    )
                }

                2 -> {
                    // TAB 2: PIXEL 8 PRO HARDWARE DIAGNOSTICS & BENCHMARK
                    PixelDiagnosticsCard(status = controller.hardwareStatus)
                    Spacer(modifier = Modifier.height(16.dp))
                    HapticTestPad(hapticEngine = controller.hapticEngine)
                }
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
