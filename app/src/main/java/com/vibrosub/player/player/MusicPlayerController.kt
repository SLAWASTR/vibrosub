package com.vibrosub.player.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vibrosub.player.audio.AudioVisualizerEngine
import com.vibrosub.player.audio.DemoBeatGenerator
import com.vibrosub.player.audio.DeviceAudioItem
import com.vibrosub.player.audio.DeviceAudioScanner
import com.vibrosub.player.haptics.HapticGeneratorManager
import com.vibrosub.player.haptics.HapticHardwareStatus
import com.vibrosub.player.haptics.PixelHapticEngine
import com.vibrosub.player.model.AudioTrackInfo
import com.vibrosub.player.model.HapticSettings
import com.vibrosub.player.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MusicPlayerController(private val context: Context) {

    private val tag = "MusicPlayerController"

    val hapticEngine = PixelHapticEngine(context)
    val hapticGeneratorManager = HapticGeneratorManager()
    val visualizerEngine = AudioVisualizerEngine(hapticEngine)

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private var playbackService: PlaybackService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? PlaybackService.LocalBinder
            playbackService = binder?.getService()
            isServiceBound = true
            updateNotification()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isServiceBound = false
        }
    }

    private val _trackInfo = MutableStateFlow(
        AudioTrackInfo(
            title = "Radiohead - No Surprises",
            artist = "Акустический тест (Колокольчики)",
            album = "VibroSub Haptic Master",
            durationMs = 0L,
            currentPositionMs = 0L,
            isPlaying = false
        )
    )
    val trackInfo: StateFlow<AudioTrackInfo> = _trackInfo.asStateFlow()

    private val _settings = MutableStateFlow(HapticSettings())
    val settings: StateFlow<HapticSettings> = _settings.asStateFlow()

    private val _deviceTracks = MutableStateFlow<List<DeviceAudioItem>>(emptyList())
    val deviceTracks: StateFlow<List<DeviceAudioItem>> = _deviceTracks.asStateFlow()

    private var currentPlaylist: List<DeviceAudioItem> = emptyList()
    private var currentTrackIndex: Int = -1

    val bassPulse: StateFlow<Float> = visualizerEngine.bassPulse
    val spectrumBars: StateFlow<FloatArray> = visualizerEngine.spectrumBars
    val activeHapticLabel: StateFlow<String> = visualizerEngine.activeHapticLabel
    val hardwareStatus: HapticHardwareStatus = hapticEngine.hardwareStatus

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(audioAttributes, true)
            repeatMode = Player.REPEAT_MODE_ALL

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _trackInfo.value = _trackInfo.value.copy(isPlaying = isPlaying)
                    visualizerEngine.isPlaying = isPlaying
                    if (isPlaying) {
                        startProgressUpdates()
                        attachAudioEffects()
                    } else {
                        stopProgressUpdates()
                        hapticEngine.stop()
                    }
                    updateNotification()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _trackInfo.value = _trackInfo.value.copy(
                            durationMs = duration.coerceAtLeast(0L)
                        )
                        attachAudioEffects()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachAudioEffects()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(tag, "ExoPlayer error: ${error.errorCodeName} - ${error.message}")
                    _trackInfo.value = _trackInfo.value.copy(isPlaying = false)
                    visualizerEngine.isPlaying = false
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val metadata = mediaItem?.mediaMetadata
                    _trackInfo.value = _trackInfo.value.copy(
                        title = metadata?.title?.toString() ?: _trackInfo.value.title,
                        artist = metadata?.artist?.toString() ?: _trackInfo.value.artist,
                        album = metadata?.albumTitle?.toString() ?: _trackInfo.value.album,
                        durationMs = duration.coerceAtLeast(0L),
                        currentPositionMs = 0L
                    )
                }
            })
        }
    }

    init {
        visualizerEngine.settings = _settings.value
        bindPlaybackService()
        refreshDeviceTracks()
    }

    fun refreshDeviceTracks() {
        scope.launch(Dispatchers.IO) {
            val scanned = DeviceAudioScanner.scanDeviceAudio(context)
            withContext(Dispatchers.Main) {
                _deviceTracks.value = scanned
                if (currentPlaylist.isEmpty()) {
                    currentPlaylist = scanned
                }
            }
        }
    }

    private fun bindPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNotification() {
        val current = _trackInfo.value
        playbackService?.startForegroundWithNotification(
            trackTitle = "${current.title} • ${current.artist}",
            isPlaying = current.isPlaying
        )
    }

    fun attachAudioEffects() {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId > 0) {
            visualizerEngine.attachToSession(sessionId)
            hapticGeneratorManager.attachToSession(sessionId, _settings.value.enabled)
        }
    }

    fun testShowcase() {
        hapticEngine.playFullShowcaseSequence()
    }

    /**
     * Resolves and caches content:// URIs to local app cache so ExoPlayer
     * has 100% unrestricted direct file access without Android 13/14 permission drops.
     */
    private suspend fun resolvePlayableUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        if (uri.scheme != "content") return@withContext uri

        try {
            val mimeType = context.contentResolver.getType(uri)
            val ext = when {
                mimeType?.contains("mp3") == true -> "mp3"
                mimeType?.contains("wav") == true -> "wav"
                mimeType?.contains("flac") == true -> "flac"
                mimeType?.contains("ogg") == true -> "ogg"
                mimeType?.contains("m4a") == true || mimeType?.contains("mp4") == true -> "m4a"
                else -> "audio"
            }
            val cacheFile = File(context.cacheDir, "current_playback_stream.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return@withContext Uri.fromFile(cacheFile)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to cache content URI, falling back to direct URI: ${e.message}")
        }
        return@withContext uri
    }

    fun loadUri(
        uri: Uri,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtUri: Uri? = null
    ) {
        val resolvedTitle = title ?: uri.lastPathSegment ?: "Аудиозапись"
        val resolvedArtist = artist ?: "Локальный файл"
        val resolvedAlbum = album ?: "Память устройства"

        _trackInfo.value = _trackInfo.value.copy(
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedAlbum,
            uri = uri,
            albumArtUri = albumArtUri,
            currentPositionMs = 0L,
            isPlaying = false
        )

        scope.launch {
            val playableUri = resolvePlayableUri(uri)

            val metadata = MediaMetadata.Builder()
                .setTitle(resolvedTitle)
                .setArtist(resolvedArtist)
                .setAlbumTitle(resolvedAlbum)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(playableUri)
                .setMediaMetadata(metadata)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun playTrack(item: DeviceAudioItem) {
        val index = currentPlaylist.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentTrackIndex = index
        }
        loadUri(
            uri = item.uri,
            title = item.title,
            artist = item.artist,
            album = item.album,
            albumArtUri = item.albumArtUri
        )
    }

    fun playNext() {
        if (currentPlaylist.isEmpty()) return
        currentTrackIndex = (currentTrackIndex + 1) % currentPlaylist.size
        playTrack(currentPlaylist[currentTrackIndex])
    }

    fun playPrevious() {
        if (currentPlaylist.isEmpty()) return
        currentTrackIndex = if (currentTrackIndex - 1 < 0) currentPlaylist.size - 1 else currentTrackIndex - 1
        playTrack(currentPlaylist[currentTrackIndex])
    }

    fun loadRadioheadDemo() {
        val uri = DemoBeatGenerator.getOrCreateRadioheadAcousticDemo(context)
        loadUri(
            uri = uri,
            title = "Radiohead - No Surprises",
            artist = "Акустика & Колокольчики (Demo)",
            album = "VibroSub Haptic Master"
        )
    }

    fun loadDemoBeat() {
        val demoUri = DemoBeatGenerator.getOrCreateDemoTrack(context)
        loadUri(
            uri = demoUri,
            title = "808 Sub-Bass Trap Drop",
            artist = "VibroSub Generator",
            album = "VibroSub Haptic Test"
        )
    }

    fun togglePlayPause() {
        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount == 0) {
            loadRadioheadDemo()
            return
        }
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _trackInfo.value = _trackInfo.value.copy(currentPositionMs = positionMs)
    }

    fun updateSettings(newSettings: HapticSettings) {
        _settings.value = newSettings
        visualizerEngine.settings = newSettings
        hapticGeneratorManager.setEnabled(newSettings.enabled)

        if (!newSettings.enabled) {
            hapticEngine.stop()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _trackInfo.value = _trackInfo.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                }
                delay(200)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdates()
        visualizerEngine.release()
        hapticGeneratorManager.release()
        hapticEngine.stop()
        exoPlayer.release()

        if (isServiceBound) {
            try {
                playbackService?.stopForegroundService()
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isServiceBound = false
        }
    }
}
