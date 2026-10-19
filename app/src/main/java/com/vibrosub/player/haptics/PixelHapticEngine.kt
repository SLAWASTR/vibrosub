package com.vibrosub.player.haptics

import android.content.Context
import android.media.audiofx.HapticGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.vibrosub.player.model.HapticEngineMode
import com.vibrosub.player.model.HapticSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class HapticHardwareStatus(
    val deviceName: String,
    val androidVersion: String,
    val hasAmplitudeControl: Boolean,
    val supportsThud: Boolean,
    val supportsClick: Boolean,
    val supportsLowTick: Boolean,
    val supportsSpin: Boolean,
    val supportsQuickRise: Boolean,
    val supportsSlowRise: Boolean,
    val supportsQuickFall: Boolean,
    val supportsHapticGenerator: Boolean
)

class PixelHapticEngine(private val context: Context) {

    private val tag = "PixelHapticEngine"

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val lastThudTime = AtomicLong(0L)
    private val lastClickTime = AtomicLong(0L)
    private val lastTickTime = AtomicLong(0L)
    private val lastRumbleTime = AtomicLong(0L)

    val hardwareStatus: HapticHardwareStatus by lazy {
        queryHardwareCapabilities()
    }

    // Use USAGE_TOUCH for responsive, unsilenced tactile feedback on Pixel devices
    private val touchVibrationAttributes: VibrationAttributes? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun queryHardwareCapabilities(): HapticHardwareStatus {
        val vib = vibrator
        val hasAmp = vib?.hasAmplitudeControl() ?: false
        val device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        var thud = false
        var click = false
        var lowTick = false
        var spin = false
        var qRise = false
        var sRise = false
        var qFall = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vib != null) {
            try {
                thud = vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
                click = vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
                lowTick = vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                spin = vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SPIN)
                qRise = vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
                sRise = vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)
                qFall = vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
            } catch (e: Exception) {
                Log.w(tag, "Failed checking primitives: ${e.message}")
            }
        }

        val hapticGen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                HapticGenerator.isAvailable()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        return HapticHardwareStatus(
            deviceName = device,
            androidVersion = androidVer,
            hasAmplitudeControl = hasAmp,
            supportsThud = thud,
            supportsClick = click,
            supportsLowTick = lowTick,
            supportsSpin = spin,
            supportsQuickRise = qRise,
            supportsSlowRise = sRise,
            supportsQuickFall = qFall,
            supportsHapticGenerator = hapticGen
        )
    }

    /**
     * Tactile Kick Drum / Sub-Bass Impact (PRIMITIVE_THUD or reinforced amplitude pulse)
     */
    fun triggerSubwooferPunch(velocity: Float, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        if (now - lastThudTime.get() < 45) return
        lastThudTime.set(now)

        val scaledVelocity = (velocity * settings.intensity * settings.thudStrength).coerceIn(0.15f, 2.0f)

        try {
            if (settings.engineMode == HapticEngineMode.MAX_PUNCH) {
                // Maximum tactile displacement for physical thump
                val amp = (scaledVelocity * 255f).toInt().coerceIn(120, 255)
                val effect = VibrationEffect.createOneShot(45, amp)
                vibrateWithFallback(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hardwareStatus.supportsThud) {
                val primitiveScale = (scaledVelocity / 1.4f).coerceIn(0.1f, 1.0f)
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, primitiveScale)
                    .compose()
                vibrateWithFallback(composition)
            } else if (hardwareStatus.hasAmplitudeControl) {
                val amp = (scaledVelocity * 220f).toInt().coerceIn(60, 255)
                val effect = VibrationEffect.createOneShot(35, amp)
                vibrateWithFallback(effect)
            } else {
                val effect = VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrateWithFallback(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering punch: ${e.message}")
        }
    }

    /**
     * Tactile Snare / Clap transient click (PRIMITIVE_CLICK)
     */
    fun triggerTransientClick(velocity: Float, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        if (now - lastClickTime.get() < 35) return
        lastClickTime.set(now)

        val scaledVelocity = (velocity * settings.intensity * settings.clickStrength).coerceIn(0.1f, 1.5f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hardwareStatus.supportsClick) {
                val primitiveScale = (scaledVelocity / 1.3f).coerceIn(0.1f, 1.0f)
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, primitiveScale)
                    .compose()
                vibrateWithFallback(composition)
            } else if (hardwareStatus.hasAmplitudeControl) {
                val amp = (scaledVelocity * 180f).toInt().coerceIn(40, 255)
                val effect = VibrationEffect.createOneShot(20, amp)
                vibrateWithFallback(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering click: ${e.message}")
        }
    }

    /**
     * Tactile Hi-Hat / Percussion Micro-Tick (PRIMITIVE_LOW_TICK)
     */
    fun triggerSubtleTick(velocity: Float, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        if (now - lastTickTime.get() < 30) return
        lastTickTime.set(now)

        val scaledVelocity = (velocity * settings.intensity).coerceIn(0.08f, 1.0f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hardwareStatus.supportsLowTick) {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scaledVelocity)
                    .compose()
                vibrateWithFallback(composition)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering tick: ${e.message}")
        }
    }

    /**
     * Tactile Melodic Chime / Glockenspiel / Guitar Pluck:
     * High-frequency crystal-clear response specifically for tracks like Radiohead - No Surprises!
     */
    fun triggerMelodyChimeTick(velocity: Float, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        if (now - lastTickTime.get() < 28) return
        lastTickTime.set(now)

        val scaledVelocity = (velocity * settings.intensity * settings.chimeStrength).coerceIn(0.12f, 1.4f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hardwareStatus.supportsLowTick) {
                val primitiveScale = (scaledVelocity / 1.15f).coerceIn(0.15f, 1.0f)
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, primitiveScale)
                    .compose()
                vibrateWithFallback(composition)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hardwareStatus.supportsClick) {
                val primitiveScale = (scaledVelocity / 1.3f).coerceIn(0.1f, 0.85f)
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, primitiveScale)
                    .compose()
                vibrateWithFallback(composition)
            } else if (hardwareStatus.hasAmplitudeControl) {
                val amp = (scaledVelocity * 150f).toInt().coerceIn(30, 210)
                val effect = VibrationEffect.createOneShot(18, amp)
                vibrateWithFallback(effect)
            } else {
                val effect = VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrateWithFallback(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering chime tick: ${e.message}")
        }
    }

    /**
     * Continuous dynamic energy groove: modulates amplitude smoothly along the volume curve
     */
    fun triggerContinuousRumble(amplitude: Float, durationMs: Long, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        if (now - lastRumbleTime.get() < 22) return
        lastRumbleTime.set(now)

        val effectiveAmp = (amplitude * settings.intensity * settings.rumbleStrength).coerceIn(0.05f, 1.5f)
        if (effectiveAmp < 0.04f) return

        try {
            if (hardwareStatus.hasAmplitudeControl) {
                val ampInt = (effectiveAmp * 200f).toInt().coerceIn(15, 255)
                val effect = VibrationEffect.createOneShot(durationMs.coerceIn(15, 55), ampInt)
                vibrateWithFallback(effect)
            } else {
                val effect = VibrationEffect.createOneShot(durationMs.coerceIn(15, 50), VibrationEffect.DEFAULT_AMPLITUDE)
                vibrateWithFallback(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering groove: ${e.message}")
        }
    }

    /**
     * Direct test trigger for any hardware primitive
     */
    fun testPrimitive(primitiveId: Int, scale: Float = 1.0f) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (vib.areAllPrimitivesSupported(primitiveId)) {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(primitiveId, scale.coerceIn(0.1f, 1.0f))
                        .compose()
                    vibrateWithFallback(composition)
                    return
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed primitive test: ${e.message}")
            }
        }
        // Fallback test pulse
        testWaveformDemo()
    }

    /**
     * Multi-stage showcase sequence demonstrating Pixel 8 Pro LRA motor capabilities:
     * Kick Thud -> Snare Click -> Low Tick -> Quick Rise Swell -> Heavy Drop
     */
    fun playFullShowcaseSequence() {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            // 1. Kick Thud
            testPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            delay(130)
            // 2. Snare Click
            testPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
            delay(120)
            // 3. Low Tick
            testPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.8f)
            delay(100)
            // 4. Quick Rise Swell
            testPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1.0f)
            delay(140)
            // 5. Heavy Double Drop
            if (hardwareStatus.hasAmplitudeControl) {
                val effect = VibrationEffect.createOneShot(60, 255)
                vibrateWithFallback(effect)
            } else {
                testPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            }
        }
    }

    fun testWaveformDemo() {
        if (vibrator == null) return
        try {
            if (hardwareStatus.hasAmplitudeControl) {
                val timings = longArrayOf(0, 30, 30, 30, 30, 40, 60)
                val amplitudes = intArrayOf(0, 40, 90, 150, 220, 255, 160)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrateWithFallback(effect)
            } else {
                val effect = VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrateWithFallback(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed test waveform: ${e.message}")
        }
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(tag, "Error canceling vibration: ${e.message}")
        }
    }

    private fun vibrateWithFallback(effect: VibrationEffect) {
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && touchVibrationAttributes != null) {
                vib.vibrate(effect, touchVibrationAttributes!!)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(40)
            }
        } catch (e: Exception) {
            // Unrestricted fallback directly to vib.vibrate
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(effect)
                }
            } catch (e2: Exception) {
                Log.e(tag, "Vibration failed completely: ${e2.message}")
            }
        }
    }
}
