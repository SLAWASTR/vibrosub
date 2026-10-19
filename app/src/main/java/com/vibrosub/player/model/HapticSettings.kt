package com.vibrosub.player.model

import android.net.Uri

enum class HapticEngineMode(val displayName: String, val description: String) {
    FULL_IMMERSION(
        "Полное погружение (Full Immersion)",
        "Комплексное тактильное сопровождение: глубокие удары бочки, четкие щелчки перкуссии и пульсирующая волна громкости трека"
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
    val engineMode: HapticEngineMode = HapticEngineMode.FULL_IMMERSION,
    val intensity: Float = 1.35f, // 0.0f to 2.0f (135% for noticeable tactile feel)
    val bassCutoffHz: Int = 150,   // 80, 120, 150, 200, 300 Hz
    val sensitivityThreshold: Float = 0.08f, // Responsive low threshold so feedback is always active
    val thudStrength: Float = 1.3f, // 0.0 to 2.0
    val clickStrength: Float = 1.0f, // 0.0 to 2.0
    val rumbleStrength: Float = 1.1f, // 0.0 to 2.0
    val enableClicksOnSnare: Boolean = true,
    val enableContinuousGroove: Boolean = true
)

data class AudioTrackInfo(
    val title: String = "Демо-бит 808 Sub-Bass",
    val artist: String = "VibroSub Demo Generator",
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val uri: Uri? = null
)
