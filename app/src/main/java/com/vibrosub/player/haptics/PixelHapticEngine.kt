package com.vibrosub.player.haptics

import android.content.Context
import android.media.audiofx.HapticGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.vibrosub.player.model.HapticSettings
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
    private val lastRumbleTime = AtomicLong(0L)

    val hardwareStatus: HapticHardwareStatus by lazy {
        queryHardwareCapabilities()
    }

    private val mediaVibrationAttributes: VibrationAttributes? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_MEDIA)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setFlags(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY)
                    }
                }
                .build()
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
                HapticGenerator.isAvailable(0)
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
     * Triggers the Pixel 8 Pro deep bass kick impact (PRIMITIVE_THUD)
     * Normalized velocity 0.0f..1.0f
     */
    fun triggerSubwooferPunch(velocity: Float, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        // 55ms minimum interval to allow the mechanical LRA motor to finish full displacement
        if (now - lastThudTime.get() < 55) return
        lastThudTime.set(now)

        val scaledVelocity = (velocity * settings.intensity * settings.thudStrength).coerceIn(0.05f, 1.0f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hardwareStatus.supportsThud) {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scaledVelocity)
                    .compose()

                vibrateWithAttributes(composition)
            } else if (hardwareStatus.hasAmplitudeControl) {
                // High precision amplitude fallback
                val amp = (scaledVelocity * 255).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(35, amp)
                vibrateWithAttributes(effect)
            } else {
                val effect = VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrateWithAttributes(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering bass punch: ${e.message}")
        }
    }

    /**
     * Triggers crisp snare/transient click (PRIMITIVE_CLICK or PRIMITIVE_LOW_TICK)
     */
    fun triggerTransientClick(velocity: Float, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        if (now - lastClickTime.get() < 40) return
        lastClickTime.set(now)

        val scaledVelocity = (velocity * settings.intensity * settings.clickStrength).coerceIn(0.05f, 1.0f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hardwareStatus.supportsClick) {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scaledVelocity)
                    .compose()

                vibrateWithAttributes(composition)
            } else if (hardwareStatus.hasAmplitudeControl) {
                val amp = (scaledVelocity * 220).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(15, amp)
                vibrateWithAttributes(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering click: ${e.message}")
        }
    }

    /**
     * Modulated continuous rumble for sub-bass tones and rolling 808 bass lines
     */
    fun triggerContinuousRumble(amplitude: Float, durationMs: Long, settings: HapticSettings) {
        if (!settings.enabled || vibrator == null) return

        val now = System.currentTimeMillis()
        if (now - lastRumbleTime.get() < 25) return
        lastRumbleTime.set(now)

        val scaledAmp = (amplitude * settings.intensity * settings.rumbleStrength).coerceIn(0.0f, 1.0f)
        if (scaledAmp < 0.05f) return

        try {
            if (hardwareStatus.hasAmplitudeControl) {
                val ampInt = (scaledAmp * 255).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(durationMs.coerceIn(15, 60), ampInt)
                vibrateWithAttributes(effect)
            } else {
                val effect = VibrationEffect.createOneShot(durationMs.coerceIn(15, 60), VibrationEffect.DEFAULT_AMPLITUDE)
                vibrateWithAttributes(effect)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error triggering rumble: ${e.message}")
        }
    }

    /**
     * Direct test trigger for any hardware primitive (for the Haptic Benchmark UI)
     */
    fun testPrimitive(primitiveId: Int, scale: Float = 1.0f) {
        if (vibrator == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (vibrator.areAllPrimitivesSupported(primitiveId)) {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(primitiveId, scale.coerceIn(0.01f, 1.0f))
                        .compose()
                    vibrateWithAttributes(composition)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to test primitive $primitiveId: ${e.message}")
            }
        }
    }

    /**
     * Tests continuous custom vibration envelope
     */
    fun testWaveformDemo() {
        if (vibrator == null) return
        try {
            if (hardwareStatus.hasAmplitudeControl) {
                // Synthesizes a riser and sudden bass explosion
                val timings = longArrayOf(0, 30, 30, 30, 30, 30, 30, 100)
                val amplitudes = intArrayOf(0, 20, 50, 90, 140, 200, 255, 120)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrateWithAttributes(effect)
            } else {
                val effect = VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrateWithAttributes(effect)
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

    private fun vibrateWithAttributes(effect: VibrationEffect) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mediaVibrationAttributes != null) {
            vib.vibrate(effect, mediaVibrationAttributes!!)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(30)
        }
    }
}
