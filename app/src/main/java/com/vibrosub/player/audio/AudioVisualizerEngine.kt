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
            if (value) {
                _activeHapticLabel.value = "Слушаю музыку..."
                startFallbackWatcher()
            } else {
                stopFallbackWatcher()
            }
        }

    // Observable states for UI animations and diagnostics
    private val _bassPulse = MutableStateFlow(0f)
    val bassPulse = _bassPulse.asStateFlow()

    private val _spectrumBars = MutableStateFlow(FloatArray(16))
    val spectrumBars = _spectrumBars.asStateFlow()

    private val _activeHapticLabel = MutableStateFlow("Ожидание музыки...")
    val activeHapticLabel = _activeHapticLabel.asStateFlow()

    // Real-time tracking & moving averages for transient detection
    @Volatile
    private var lastDataReceivedTime = 0L
    private var movingAverageEnergy = 0.12f
    private var movingAverageSnare = 0.10f
    private var movingAverageChime = 0.08f
    private var movingAverageAcoustic = 0.10f
    private var dynamicMaxEnergy = 0.25f

    private var fallbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (currentSessionId == audioSessionId && visualizer != null) {
            // Already attached to this session
            return
        }

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
                    true, // CAPTURE WAVEFORM
                    true  // CAPTURE FFT
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

        val rawRms = (sqrt(sumSquares / waveform.size) / 60.0).coerceIn(0.0, 1.0).toFloat()
        val rawPeak = (peakValue / 90.0).coerceIn(0.0, 1.0).toFloat()
        val instantEnergy = (rawRms * 0.65f + rawPeak * 0.45f).coerceIn(0f, 1.5f)

        // Smooth visualizer pulse
        _bassPulse.value = (_bassPulse.value * 0.35f + instantEnergy * 0.65f).coerceIn(0f, 1f)

        // Adaptive Dynamic Range Tracking (AGC): Automatically tunes sensitivity to track volume
        if (instantEnergy > dynamicMaxEnergy) {
            dynamicMaxEnergy = dynamicMaxEnergy * 0.7f + instantEnergy * 0.3f
        } else {
            dynamicMaxEnergy = (dynamicMaxEnergy * 0.995f).coerceAtLeast(0.10f)
        }

        val normalizedEnergy = (instantEnergy / dynamicMaxEnergy).coerceIn(0f, 1.5f)

        // Continuous envelope presence
        if (settings.enabled && isPlaying) {
            val isTransient = normalizedEnergy > (movingAverageEnergy * 1.25f) &&
                    normalizedEnergy > settings.sensitivityThreshold

            if (isTransient) {
                val thudAmp = (normalizedEnergy * settings.thudStrength).coerceIn(0.2f, 1.5f)
                hapticEngine.triggerSubwooferPunch(thudAmp, settings)
                _activeHapticLabel.value = "🥁 Динамический бит • ${(thudAmp * 100).toInt()}%"
            } else if (settings.enableContinuousGroove && normalizedEnergy > (settings.sensitivityThreshold * 0.6f)) {
                val rumbleAmp = (normalizedEnergy * settings.rumbleStrength * 0.7f).coerceIn(0.06f, 1.0f)
                hapticEngine.triggerContinuousRumble(rumbleAmp, 30L, settings)
                if (!_activeHapticLabel.value.startsWith("🔔") && !_activeHapticLabel.value.startsWith("✨") && !_activeHapticLabel.value.startsWith("🎸")) {
                    _activeHapticLabel.value = "🌊 Тактильное дыхание • ${(rumbleAmp * 100).toInt()}%"
                }
            }
        }

        movingAverageEnergy = movingAverageEnergy * 0.82f + normalizedEnergy * 0.18f
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

        // 16 Spectrum bars for visualizer UI
        val bars = FloatArray(16)
        val step = max(1, halfN / 16)
        for (i in 0 until 16) {
            var sum = 0f
            for (j in (i * step) until ((i + 1) * step).coerceAtMost(halfN)) {
                sum += magnitudes[j]
            }
            bars[i] = (sum / (step * 50f)).coerceIn(0.05f, 1f)
        }
        _spectrumBars.value = bars

        // Omni-Spectrum Frequency Separation:
        // 1. Sub-Bass (20 - 75 Hz): 808 subs, rumble
        // 2. Kick Bass (75 - cutoff Hz): Kick drum, bassline
        // 3. Acoustic Mid & Vocals (250 - 1200 Hz): Acoustic guitar, vocals, piano chords
        // 4. Melody & Glockenspiel (1200 - 4200 Hz): Bells, chimes, guitar plucks (Radiohead No Surprises!)
        // 5. Snare & Claps (150 - 400 Hz): Drum snare, rimshots, handclaps
        var subBassSum = 0f; var subBassCount = 0
        var kickBassSum = 0f; var kickBassCount = 0
        var snareSum = 0f; var snareCount = 0
        var acousticMidSum = 0f; var acousticMidCount = 0
        var chimeSum = 0f; var chimeCount = 0

        for (k in 1 until halfN) {
            val freqHz = k * binWidthHz * 1000f
            val mag = magnitudes[k]

            when {
                freqHz in 20f..75f -> { subBassSum += mag; subBassCount++ }
                freqHz in 75f..settings.bassCutoffHz.toFloat() -> { kickBassSum += mag; kickBassCount++ }
                freqHz in 150f..380f -> { snareSum += mag; snareCount++ }
                freqHz in 250f..1200f -> { acousticMidSum += mag; acousticMidCount++ }
                freqHz in 1200f..4200f -> { chimeSum += mag; chimeCount++ }
            }
        }

        val subEnergy = if (subBassCount > 0) (subBassSum / subBassCount) / 32f else 0f
        val kickEnergy = if (kickBassCount > 0) (kickBassSum / kickBassCount) / 32f else 0f
        val snareEnergy = if (snareCount > 0) (snareSum / snareCount) / 38f else 0f
        val acousticEnergy = if (acousticMidCount > 0) (acousticMidSum / acousticMidCount) / 35f else 0f
        val chimeEnergy = if (chimeCount > 0) (chimeSum / chimeCount) / 25f else 0f

        dispatchOmniHaptics(kickEnergy, snareEnergy, subEnergy, acousticEnergy, chimeEnergy)
    }

    private fun dispatchOmniHaptics(
        kick: Float,
        snare: Float,
        sub: Float,
        acoustic: Float,
        chime: Float
    ) {
        if (!settings.enabled || !isPlaying) return

        movingAverageSnare = movingAverageSnare * 0.85f + snare * 0.15f
        movingAverageChime = movingAverageChime * 0.82f + chime * 0.18f
        movingAverageAcoustic = movingAverageAcoustic * 0.85f + acoustic * 0.15f

        val thresh = settings.sensitivityThreshold

        when (settings.engineMode) {
            HapticEngineMode.ACOUSTIC_MELODY -> {
                // Tuned specifically for acoustic, indie and melodic songs like Radiohead - No Surprises!
                val isChime = settings.enableMelodyChimes &&
                        chime > (thresh * 0.7f) &&
                        chime > (movingAverageChime * 1.18f)

                val isAcousticStrum = acoustic > (thresh * 0.8f) &&
                        acoustic > (movingAverageAcoustic * 1.22f)

                val isBassNote = kick > (thresh * 0.8f) &&
                        kick > (movingAverageEnergy * 1.18f)

                if (isChime) {
                    _activeHapticLabel.value = "🔔 Колокольчики / Мелодия (TICK) • ${(chime * 100).toInt()}%"
                    hapticEngine.triggerMelodyChimeTick(chime, settings)
                } else if (isAcousticStrum) {
                    _activeHapticLabel.value = "🎸 Акустический перебор (CLICK) • ${(acoustic * 100).toInt()}%"
                    hapticEngine.triggerTransientClick(acoustic, settings)
                } else if (isBassNote) {
                    _activeHapticLabel.value = "🎸 Басовая нота (THUD) • ${(kick * 100).toInt()}%"
                    hapticEngine.triggerSubwooferPunch(kick * 0.9f, settings)
                } else if (settings.enableContinuousGroove) {
                    val breathAmp = (acoustic * 0.5f + sub * 0.5f).coerceIn(0.05f, 1.0f)
                    if (breathAmp > (thresh * 0.5f)) {
                        _activeHapticLabel.value = "🌊 Дыхание мелодии • ${(breathAmp * 100).toInt()}%"
                        hapticEngine.triggerContinuousRumble(breathAmp, 30L, settings)
                    }
                }
            }

            HapticEngineMode.FULL_IMMERSION -> {
                val isKick = kick > thresh && kick > (movingAverageEnergy * 1.20f)
                val isSnare = settings.enableClicksOnSnare &&
                        snare > thresh &&
                        snare > (movingAverageSnare * 1.25f) &&
                        snare > (kick * 0.85f)
                val isChime = settings.enableMelodyChimes &&
                        chime > thresh &&
                        chime > (movingAverageChime * 1.25f)

                if (isKick) {
                    _activeHapticLabel.value = "🥁 Бочка 808 (THUD) • ${(kick * 100).toInt()}%"
                    hapticEngine.triggerSubwooferPunch(kick, settings)
                } else if (isSnare) {
                    _activeHapticLabel.value = "✨ Снейр / Клэп (CLICK) • ${(snare * 100).toInt()}%"
                    hapticEngine.triggerTransientClick(snare, settings)
                } else if (isChime) {
                    _activeHapticLabel.value = "🔔 Мелодия / Колокольчики (TICK) • ${(chime * 100).toInt()}%"
                    hapticEngine.triggerMelodyChimeTick(chime, settings)
                } else if (settings.enableContinuousGroove && sub > (thresh * 0.7f)) {
                    _activeHapticLabel.value = "🌊 Саб-бас рокот • ${(sub * 100).toInt()}%"
                    hapticEngine.triggerContinuousRumble(sub, 30L, settings)
                }
            }

            HapticEngineMode.RHYTHM_BEAT -> {
                val isKick = kick > thresh && kick > (movingAverageEnergy * 1.18f)
                val isSnare = snare > thresh && snare > (movingAverageSnare * 1.22f)

                if (isKick) {
                    _activeHapticLabel.value = "🥁 Ритм-бочка (THUD) • ${(kick * 100).toInt()}%"
                    hapticEngine.triggerSubwooferPunch(kick, settings)
                } else if (isSnare) {
                    _activeHapticLabel.value = "✨ Ритм-снейр (CLICK) • ${(snare * 100).toInt()}%"
                    hapticEngine.triggerTransientClick(snare, settings)
                }
            }

            HapticEngineMode.DYNAMIC_FLOW -> {
                val flowEnergy = (kick * 0.4f + acoustic * 0.4f + sub * 0.2f).coerceIn(0f, 1f)
                if (flowEnergy > (thresh * 0.6f)) {
                    _activeHapticLabel.value = "🌊 Плавный пульс • ${(flowEnergy * 100).toInt()}%"
                    hapticEngine.triggerContinuousRumble(flowEnergy, 40L, settings)
                }
            }

            HapticEngineMode.MAX_PUNCH -> {
                val isImpact = kick > thresh || sub > thresh || chime > (thresh * 1.2f)
                if (isImpact) {
                    val maxAmp = max(kick, max(sub, chime)).coerceIn(0.3f, 1.0f)
                    _activeHapticLabel.value = "⚡ МАКСИМАЛЬНЫЙ УДАР • ${(maxAmp * 150).toInt()}%"
                    hapticEngine.triggerSubwooferPunch(maxAmp * 1.3f, settings)
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
                    // Visualizer is not sending data -> trigger companion accompaniment
                    val isKickStep = (stepCounter % 4 == 0)
                    val isSnareStep = (stepCounter % 4 == 2)
                    val isChimeStep = (stepCounter % 4 == 1 || stepCounter % 4 == 3)

                    if (settings.engineMode == HapticEngineMode.ACOUSTIC_MELODY) {
                        if (isChimeStep && settings.enableMelodyChimes) {
                            _activeHapticLabel.value = "🔔 Мелодический шаг • 85%"
                            hapticEngine.triggerMelodyChimeTick(0.85f, settings)
                            _bassPulse.value = 0.5f
                        } else if (isKickStep) {
                            _activeHapticLabel.value = "🎸 Акустический пульс • 90%"
                            hapticEngine.triggerSubwooferPunch(0.8f, settings)
                            _bassPulse.value = 0.8f
                        }
                    } else {
                        if (isKickStep) {
                            _activeHapticLabel.value = "🥁 Ритм-компаньон (Kick) • 100%"
                            hapticEngine.triggerSubwooferPunch(0.85f, settings)
                            _bassPulse.value = 0.85f
                        } else if (isSnareStep && settings.enableClicksOnSnare) {
                            _activeHapticLabel.value = "✨ Ритм-компаньон (Snare) • 80%"
                            hapticEngine.triggerTransientClick(0.75f, settings)
                        }
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
