package com.vibrosub.player.audio

import android.media.audiofx.Visualizer
import android.util.Log
import com.vibrosub.player.haptics.PixelHapticEngine
import com.vibrosub.player.model.HapticEngineMode
import com.vibrosub.player.model.HapticSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

class AudioVisualizerEngine(
    private val hapticEngine: PixelHapticEngine
) {
    private val tag = "AudioVisualizerEngine"

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = 0

    var settings: HapticSettings = HapticSettings()
    var isPlaying: Boolean = false
        set(value) {
            field = value
            if (value) startFallbackWatcher() else stopFallbackWatcher()
        }

    // Observable states for UI animations and diagnostics
    private val _bassPulse = MutableStateFlow(0f)
    val bassPulse = _bassPulse.asStateFlow()

    private val _spectrumBars = MutableStateFlow(FloatArray(16))
    val spectrumBars = _spectrumBars.asStateFlow()

    private val _activeHapticLabel = MutableStateFlow("Ожидание музыки...")
    val activeHapticLabel = _activeHapticLabel.asStateFlow()

    // Real-time tracking
    @Volatile
    private var lastDataReceivedTime = 0L
    private var movingAverageEnergy = 0.15f
    private var movingAverageSnare = 0.12f
    private var fallbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (currentSessionId == audioSessionId && visualizer != null) return

        release()
        currentSessionId = audioSessionId

        try {
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            val targetCaptureSize = 512.coerceIn(captureSizeRange[0], captureSizeRange[1])

            visualizer = Visualizer(audioSessionId).apply {
                captureSize = targetCaptureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform != null && waveform.isNotEmpty()) {
                                lastDataReceivedTime = System.currentTimeMillis()
                                processWaveformData(waveform)
                            }
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft != null && fft.isNotEmpty()) {
                                lastDataReceivedTime = System.currentTimeMillis()
                                processFftData(fft, samplingRate)
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    true, // CAPTURE WAVEFORM!
                    true  // CAPTURE FFT!
                )
                enabled = true
            }
            Log.d(tag, "Visualizer attached to audio session: $audioSessionId")
        } catch (e: SecurityException) {
            Log.e(tag, "RECORD_AUDIO permission missing for Visualizer: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Visualizer: ${e.message}")
        }
    }

    private fun processWaveformData(waveform: ByteArray) {
        var sumSquares = 0.0
        var peakValue = 0

        for (b in waveform) {
            val value = Math.abs((b.toInt() and 0xFF) - 128)
            sumSquares += value * value
            if (value > peakValue) peakValue = value
        }

        val rms = (sqrt(sumSquares / waveform.size) / 80.0).coerceIn(0.0, 1.0).toFloat()
        val peak = (peakValue / 115.0).coerceIn(0.0, 1.0).toFloat()
        val combinedWaveformEnergy = (rms * 0.7f + peak * 0.5f).coerceIn(0f, 1.2f)

        // Update animated pulse
        _bassPulse.value = (_bassPulse.value * 0.45f + combinedWaveformEnergy * 0.55f).coerceIn(0f, 1f)

        // If FFT is quiet or delayed, waveform immediately drives accompaniment
        if (settings.enabled && combinedWaveformEnergy > settings.sensitivityThreshold) {
            val isBeatTransient = combinedWaveformEnergy > (movingAverageEnergy * 1.25f)
            if (isBeatTransient) {
                _activeHapticLabel.value = "🥁 Удар бита (THUD) • ${(combinedWaveformEnergy * 100).toInt()}%"
                hapticEngine.triggerSubwooferPunch(combinedWaveformEnergy, settings)
            } else if (settings.enableContinuousGroove) {
                _activeHapticLabel.value = "🌊 Волна энергии (GROOVE) • ${(rms * 100).toInt()}%"
                hapticEngine.triggerContinuousRumble(rms, 35L, settings)
            }
        }
        movingAverageEnergy = movingAverageEnergy * 0.85f + combinedWaveformEnergy * 0.15f
    }

    private fun processFftData(fft: ByteArray, samplingRate: Int) {
        val n = fft.size
        val halfN = n / 2
        val binWidthHz = (samplingRate / 1000f) / n

        val magnitudes = FloatArray(halfN)
        magnitudes[0] = Math.abs(fft[0].toFloat())
        for (k in 1 until halfN) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            magnitudes[k] = hypot(re, im)
        }

        // Spectrum bars (16 bands)
        val bars = FloatArray(16)
        val step = max(1, halfN / 16)
        for (i in 0 until 16) {
            var sum = 0f
            for (j in (i * step) until ((i + 1) * step).coerceAtMost(halfN)) {
                sum += magnitudes[j]
            }
            bars[i] = (sum / (step * 70f)).coerceIn(0.05f, 1f)
        }
        _spectrumBars.value = bars

        // Frequency separation
        var subBassSum = 0f
        var subBassCount = 0
        var kickBassSum = 0f
        var kickBassCount = 0
        var snareSum = 0f
        var snareCount = 0

        for (k in 1 until halfN) {
            val freqHz = k * binWidthHz * 1000f
            val mag = magnitudes[k]

            if (freqHz in 20f..70f) {
                subBassSum += mag
                subBassCount++
            } else if (freqHz in 70f..settings.bassCutoffHz.toFloat()) {
                kickBassSum += mag
                kickBassCount++
            } else if (freqHz in 150f..380f) {
                snareSum += mag
                snareCount++
            }
        }

        val kickEnergy = if (kickBassCount > 0) (kickBassSum / kickBassCount) / 45f else 0f
        val snareEnergy = if (snareCount > 0) (snareSum / snareCount) / 55f else 0f
        val subEnergy = if (subBassCount > 0) (subBassSum / subBassCount) / 45f else 0f

        dispatchAcousticHaptics(kickEnergy, snareEnergy, subEnergy)
    }

    private fun dispatchAcousticHaptics(
        kick: Float,
        snare: Float,
        sub: Float
    ) {
        if (!settings.enabled) return

        movingAverageSnare = movingAverageSnare * 0.85f + snare * 0.15f

        when (settings.engineMode) {
            HapticEngineMode.FULL_IMMERSION -> {
                // Layer 1: Kick Impact
                val isKick = kick > settings.sensitivityThreshold && kick > (movingAverageEnergy * 1.22f)
                // Layer 2: Snare / Percussion Accent
                val isSnare = settings.enableClicksOnSnare &&
                        snare > settings.sensitivityThreshold &&
                        snare > (movingAverageSnare * 1.30f) &&
                        snare > (kick * 0.85f)

                if (isKick) {
                    _activeHapticLabel.value = "🥁 Бочка 808 (THUD) • ${(kick * 100).toInt()}%"
                    hapticEngine.triggerSubwooferPunch(kick, settings)
                } else if (isSnare) {
                    _activeHapticLabel.value = "✨ Снейр / Клэп (CLICK) • ${(snare * 100).toInt()}%"
                    hapticEngine.triggerTransientClick(snare, settings)
                } else if (settings.enableContinuousGroove && sub > (settings.sensitivityThreshold * 0.8f)) {
                    _activeHapticLabel.value = "🌊 Саб-бас рокот • ${(sub * 100).toInt()}%"
                    hapticEngine.triggerContinuousRumble(sub, 30L, settings)
                }
            }

            HapticEngineMode.RHYTHM_BEAT -> {
                val isKick = kick > settings.sensitivityThreshold && kick > (movingAverageEnergy * 1.20f)
                val isSnare = snare > settings.sensitivityThreshold && snare > (movingAverageSnare * 1.25f)

                if (isKick) {
                    _activeHapticLabel.value = "🥁 Ритм-бочка (THUD) • ${(kick * 100).toInt()}%"
                    hapticEngine.triggerSubwooferPunch(kick, settings)
                } else if (isSnare) {
                    _activeHapticLabel.value = "✨ Ритм-снейр (CLICK) • ${(snare * 100).toInt()}%"
                    hapticEngine.triggerTransientClick(snare, settings)
                }
            }

            HapticEngineMode.DYNAMIC_FLOW -> {
                val flowEnergy = (kick * 0.5f + sub * 0.5f).coerceIn(0f, 1f)
                if (flowEnergy > (settings.sensitivityThreshold * 0.7f)) {
                    _activeHapticLabel.value = "🌊 Плавный пульс • ${(flowEnergy * 100).toInt()}%"
                    hapticEngine.triggerContinuousRumble(flowEnergy, 40L, settings)
                }
            }

            HapticEngineMode.MAX_PUNCH -> {
                val isImpact = kick > settings.sensitivityThreshold || sub > settings.sensitivityThreshold
                if (isImpact) {
                    val maxAmp = max(kick, sub).coerceIn(0.2f, 1.0f)
                    _activeHapticLabel.value = "⚡ МАКСИМАЛЬНЫЙ УДАР • ${(maxAmp * 150).toInt()}%"
                    hapticEngine.triggerSubwooferPunch(maxAmp * 1.2f, settings)
                }
            }
        }
    }

    /**
     * Fallback Rhythmic Companion:
     * If visualizer callbacks stop or audio is in hardware offload,
     * this guarantees that tactile beats ALWAYS accompany the music.
     */
    private fun startFallbackWatcher() {
        fallbackJob?.cancel()
        fallbackJob = scope.launch {
            var stepCounter = 0
            while (isActive) {
                delay(125) // 8th note grid at 120 BPM
                if (!isPlaying || !settings.enabled) continue

                val timeSinceLastData = System.currentTimeMillis() - lastDataReceivedTime
                if (timeSinceLastData > 350) {
                    // Visualizer is not sending data -> trigger companion beat
                    val isKickStep = (stepCounter % 4 == 0)
                    val isSnareStep = (stepCounter % 4 == 2)

                    if (isKickStep) {
                        _activeHapticLabel.value = "🥁 Ритм-компаньон (Kick) • 100%"
                        hapticEngine.triggerSubwooferPunch(0.85f, settings)
                        _bassPulse.value = 0.85f
                    } else if (isSnareStep && settings.enableClicksOnSnare) {
                        _activeHapticLabel.value = "✨ Ритм-компаньон (Snare) • 80%"
                        hapticEngine.triggerTransientClick(0.75f, settings)
                    }
                    stepCounter++
                }
            }
        }
    }

    private fun stopFallbackWatcher() {
        fallbackJob?.cancel()
        fallbackJob = null
        _activeHapticLabel.value = "Музыка на паузе"
        _bassPulse.value = 0f
    }

    fun release() {
        stopFallbackWatcher()
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing visualizer: ${e.message}")
        } finally {
            visualizer = null
            currentSessionId = 0
            _bassPulse.value = 0f
            _spectrumBars.value = FloatArray(16)
        }
    }
}
