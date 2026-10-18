package com.vibrosub.player.audio

import android.media.audiofx.Visualizer
import android.util.Log
import com.vibrosub.player.haptics.PixelHapticEngine
import com.vibrosub.player.model.HapticEngineMode
import com.vibrosub.player.model.HapticSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max

class AudioVisualizerEngine(
    private val hapticEngine: PixelHapticEngine
) {
    private val tag = "AudioVisualizerEngine"

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = 0

    // Settings reference
    var settings: HapticSettings = HapticSettings()

    // Observable states for UI animations
    private val _bassPulse = MutableStateFlow(0f)
    val bassPulse = _bassPulse.asStateFlow()

    private val _spectrumBars = MutableStateFlow(FloatArray(16))
    val spectrumBars = _spectrumBars.asStateFlow()

    // Transient detection state
    private var movingAverageBass = 0.1f
    private var movingAverageSnare = 0.1f
    private val decayFactor = 0.88f

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
                            // Waveform unused, using FFT for accurate frequency bands
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.isEmpty()) return
                            processFftData(fft, samplingRate)
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true
                )
                enabled = true
            }
            Log.d(tag, "Visualizer successfully attached to audio session: $audioSessionId")
        } catch (e: SecurityException) {
            Log.e(tag, "RECORD_AUDIO permission missing for Visualizer: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Visualizer: ${e.message}")
        }
    }

    private fun processFftData(fft: ByteArray, samplingRate: Int) {
        val n = fft.size
        val halfN = n / 2
        val binWidthHz = (samplingRate / 1000f) / n // in kHz -> Hz

        val magnitudes = FloatArray(halfN)
        magnitudes[0] = Math.abs(fft[0].toFloat()) // DC
        for (k in 1 until halfN) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            magnitudes[k] = hypot(re, im)
        }

        // Frequency band aggregations
        var subBassSum = 0f
        var subBassCount = 0
        var kickBassSum = 0f
        var kickBassCount = 0
        var snareSum = 0f
        var snareCount = 0

        // Spectrum bars (16 bins)
        val bars = FloatArray(16)
        val step = max(1, halfN / 16)
        for (i in 0 until 16) {
            var sum = 0f
            for (j in (i * step) until ((i + 1) * step).coerceAtMost(halfN)) {
                sum += magnitudes[j]
            }
            bars[i] = (sum / (step * 80f)).coerceIn(0f, 1f)
        }
        _spectrumBars.value = bars

        // Calculate specific acoustic frequencies
        for (k in 1 until halfN) {
            val freqHz = k * binWidthHz * 1000f
            val mag = magnitudes[k]

            if (freqHz in 20f..70f) {
                subBassSum += mag
                subBassCount++
            } else if (freqHz in 70f..settings.bassCutoffHz.toFloat()) {
                kickBassSum += mag
                kickBassCount++
            } else if (freqHz in 150f..350f) {
                snareSum += mag
                snareCount++
            }
        }

        val rawSubBass = if (subBassCount > 0) (subBassSum / subBassCount) / 60f else 0f
        val rawKickBass = if (kickBassCount > 0) (kickBassSum / kickBassCount) / 60f else 0f
        val rawSnare = if (snareCount > 0) (snareSum / snareCount) / 75f else 0f

        val combinedBass = (rawSubBass * 0.6f + rawKickBass * 0.8f).coerceIn(0f, 1.5f)
        val normalizedBass = combinedBass.coerceIn(0f, 1f)

        // Smooth pulse for UI animations
        _bassPulse.value = (_bassPulse.value * 0.6f + normalizedBass * 0.4f).coerceIn(0f, 1f)

        // Haptic routing based on engine mode
        if (settings.enabled) {
            dispatchHaptics(rawSubBass, rawKickBass, rawSnare, combinedBass)
        }
    }

    private fun dispatchHaptics(
        subBass: Float,
        kickBass: Float,
        snare: Float,
        totalBass: Float
    ) {
        movingAverageBass = movingAverageBass * decayFactor + totalBass * (1f - decayFactor)
        movingAverageSnare = movingAverageSnare * decayFactor + snare * (1f - decayFactor)

        when (settings.engineMode) {
            HapticEngineMode.PIXEL_SUBWOOFER -> {
                // Kick drum transient peak detector
                val isKickTransient = totalBass > settings.sensitivityThreshold &&
                        totalBass > (movingAverageBass * 1.28f)

                if (isKickTransient) {
                    val velocity = (totalBass * 1.1f).coerceIn(0.1f, 1.0f)
                    hapticEngine.triggerSubwooferPunch(velocity, settings)
                }

                // Rolling continuous sub-bass rumble
                if (subBass > (settings.sensitivityThreshold * 0.9f)) {
                    val rumbleAmp = (subBass * 0.75f).coerceIn(0.1f, 0.9f)
                    hapticEngine.triggerContinuousRumble(rumbleAmp, 30L, settings)
                }
            }

            HapticEngineMode.TRANSIENT_BEAT -> {
                // Focus strictly on punchy kick and crisp snare strikes
                val isKick = totalBass > settings.sensitivityThreshold &&
                        totalBass > (movingAverageBass * 1.35f)
                val isSnare = snare > (settings.sensitivityThreshold * 1.1f) &&
                        snare > (movingAverageSnare * 1.4f)

                if (isKick) {
                    hapticEngine.triggerSubwooferPunch(totalBass.coerceIn(0.2f, 1.0f), settings)
                } else if (isSnare) {
                    hapticEngine.triggerTransientClick(snare.coerceIn(0.2f, 0.9f), settings)
                }
            }

            HapticEngineMode.CONTINUOUS_WAVEFORM -> {
                // Direct amplitude tracking
                if (totalBass > settings.sensitivityThreshold) {
                    val amp = totalBass.coerceIn(0.05f, 1.0f)
                    hapticEngine.triggerContinuousRumble(amp, 40L, settings)
                }
            }

            HapticEngineMode.NATIVE_HAPTIC_GENERATOR -> {
                // Handled in hardware by DSP, no manual vibration needed
            }
        }
    }

    fun release() {
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
