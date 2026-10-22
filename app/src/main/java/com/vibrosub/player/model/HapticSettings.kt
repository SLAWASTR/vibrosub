package com.vibrosub.player.model

import android.net.Uri

enum class HapticEngineMode(val displayName: String, val description: String) {
    ACOUSTIC_MELODY(
        "Мелодия и акустика (Radiohead / Инди)",
        "Специально для песен вроде No Surprises: деликатный кристальный отклик на колокольчики, переборы струн, вокал и мягкий бас"
    ),
    FULL_IMMERSION(
        "Полное погружение (Full Immersion)",
        "Универсальное тактильное сопровождение: мощная бочка 808, щелчки снейра, колокольчики мелодии и пульсирующий саб-бас"
    ),
    RHYTHM_BEAT(
        "Ритм и грув (Beat & Groove)",
        "Четкие тактильные импульсы точно в такт барабанам и басовым транзиентам"
    ),
    DYNAMIC_FLOW(
        "Плавная волна энергии (Dynamic Flow)",
        "Бархатная непрерывная вибрация, повторяющая динамику и нарастания трека"
    ),
    MAX_PUNCH(
        "Экстремальный вибро-буст (Max Punch)",
        "Максимально усиленный отклик с двойными импульсами мотора, сотрясающий смартфон"
    )
}

data class HapticSettings(
    val enabled: Boolean = true,
    val engineMode: HapticEngineMode = HapticEngineMode.ACOUSTIC_MELODY,
    val intensity: Float = 1.35f, // 0.0f to 2.0f
    val bassCutoffHz: Int = 180,   // 80, 120, 150, 180, 250 Hz
    val sensitivityThreshold: Float = 0.05f, // Lower threshold so quiet tracks trigger easily
    val thudStrength: Float = 1.3f, // 0.0 to 2.0
    val clickStrength: Float = 1.1f, // 0.0 to 2.0
    val chimeStrength: Float = 1.35f, // 0.0 to 2.0 (for bells, melody, guitar plucks)
    val rumbleStrength: Float = 1.15f, // 0.0 to 2.0
    val enableClicksOnSnare: Boolean = true,
    val enableMelodyChimes: Boolean = true,
    val enableContinuousGroove: Boolean = true
)

data class AudioTrackInfo(
    val title: String = "Radiohead - No Surprises (Акустический тест)",
    val artist: String = "VibroSub Omni-Spectrum Engine",
    val album: String = "VibroSub Haptic Master",
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val uri: Uri? = null,
    val albumArtUri: Uri? = null
)
