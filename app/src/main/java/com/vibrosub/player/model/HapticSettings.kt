package com.vibrosub.player.model

import android.net.Uri

enum class HapticEngineMode(val displayName: String, val description: String) {
    PIXEL_SUBWOOFER(
        "Pixel Sub-Woofer",
        "Комбинация глубокого рокота сабвуфера и резких импульсов удара бочки (PRIMITIVE_THUD)"
    ),
    TRANSIENT_BEAT(
        "Динамический бит (Beat Punch)",
        "Точные аппаратные импульсы на каждый удар барабана и басовые акценты"
    ),
    CONTINUOUS_WAVEFORM(
        "Непрерывная волна баса",
        "Плавная модуляция амплитуды вибрации в реальном времени по кривой низких частот"
    ),
    NATIVE_HAPTIC_GENERATOR(
        "Google DSP HapticGenerator",
        "Аппаратный алгоритм Android 12+, привязанный напрямую к аудиопотоку Pixel"
    )
}

data class HapticSettings(
    val enabled: Boolean = true,
    val engineMode: HapticEngineMode = HapticEngineMode.PIXEL_SUBWOOFER,
    val intensity: Float = 1.25f, // 0.0f to 2.0f (125% default for rich punch)
    val bassCutoffHz: Int = 120,   // 60, 100, 120, 180, 250 Hz
    val sensitivityThreshold: Float = 0.22f, // noise floor gate
    val thudStrength: Float = 1.0f, // 0.0 to 1.5
    val clickStrength: Float = 0.6f, // 0.0 to 1.5
    val rumbleStrength: Float = 0.85f // 0.0 to 1.5
)

data class AudioTrackInfo(
    val title: String = "Демо-бит 808 Sub-Bass",
    val artist: String = "VibroSub Demo Generator",
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val uri: Uri? = null
)
