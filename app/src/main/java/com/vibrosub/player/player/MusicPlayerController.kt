package com.vibrosub.player.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vibrosub.player.audio.AudioVisualizerEngine
import com.vibrosub.player.audio.DemoBeatGenerator
import com.vibrosub.player.haptics.HapticGeneratorManager
import com.vibrosub.player.haptics.HapticHardwareStatus
import com.vibrosub.player.haptics.PixelHapticEngine
import com.vibrosub.player.model.AudioTrackInfo
import com.vibrosub.player.model.HapticEngineMode
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

class MusicPlayerController(private val context: Context) {

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
            title = "Демо-бит 808 Sub-Bass",
            artist = "VibroSub Demo Generator",
            durationMs = 0L,
            currentPositionMs = 0L,
            isPlaying = false
        )
    )
    val trackInfo: StateFlow<AudioTrackInfo> = _trackInfo.asStateFlow()

    private val _settings = MutableStateFlow(HapticSettings())
    val settings: StateFlow<HapticSettings> = _settings.asStateFlow()

    val bassPulse: StateFlow<Float> = visualizerEngine.bassPulse
    val spectrumBars: StateFlow<FloatArray> = visualizerEngine.spectrumBars
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

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val metadata = mediaItem?.mediaMetadata
                    _trackInfo.value = _trackInfo.value.copy(
                        title = metadata?.title?.toString() ?: "Неизвестный трек",
                        artist = metadata?.artist?.toString() ?: "VibroSub",
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

    private fun attachAudioEffects() {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId > 0) {
            visualizerEngine.attachToSession(sessionId)
            val useHapticGen = _settings.value.engineMode == HapticEngineMode.NATIVE_HAPTIC_GENERATOR
            hapticGeneratorManager.attachToSession(sessionId, useHapticGen)
        }
    }

    fun loadUri(uri: Uri, title: String? = null, artist: String? = null) {
        val resolvedTitle = title ?: uri.lastPathSegment ?: "Аудиозапись"
        val resolvedArtist = artist ?: "Локальный файл"

        val metadata = MediaMetadata.Builder()
            .setTitle(resolvedTitle)
            .setArtist(resolvedArtist)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        _trackInfo.value = _trackInfo.value.copy(
            title = resolvedTitle,
            artist = resolvedArtist,
            uri = uri,
            currentPositionMs = 0L
        )

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun loadDemoBeat() {
        val demoUri = DemoBeatGenerator.getOrCreateDemoTrack(context)
        loadUri(demoUri, "Демо-бит 808 Sub-Bass", "Pixel 8 Pro Haptic Test")
    }

    fun togglePlayPause() {
        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount == 0) {
            loadDemoBeat()
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

        // Handle native HapticGenerator mode toggle
        val useHapticGen = newSettings.engineMode == HapticEngineMode.NATIVE_HAPTIC_GENERATOR && newSettings.enabled
        hapticGeneratorManager.setEnabled(useHapticGen)

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
