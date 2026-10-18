package com.vibrosub.player.haptics

import android.media.audiofx.HapticGenerator
import android.os.Build
import android.util.Log

class HapticGeneratorManager {

    private val tag = "HapticGeneratorManager"
    private var hapticGenerator: HapticGenerator? = null
    var isEnabled: Boolean = false
        private set

    fun attachToSession(audioSessionId: Int, enable: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.d(tag, "HapticGenerator requires Android 12+ (API 31+)")
            return
        }

        release()

        try {
            if (audioSessionId > 0 && HapticGenerator.isAvailable(audioSessionId)) {
                hapticGenerator = HapticGenerator.create(audioSessionId).apply {
                    this.enabled = enable
                }
                isEnabled = enable
                Log.d(tag, "HapticGenerator attached to audio session $audioSessionId, enabled=$enable")
            } else {
                Log.d(tag, "HapticGenerator is not available for audio session $audioSessionId on this device")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize HapticGenerator: ${e.message}")
        }
    }

    fun setEnabled(enable: Boolean) {
        try {
            hapticGenerator?.enabled = enable
            isEnabled = enable
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle HapticGenerator: ${e.message}")
        }
    }

    fun release() {
        try {
            hapticGenerator?.release()
        } catch (e: Exception) {
            Log.e(tag, "Failed releasing HapticGenerator: ${e.message}")
        } finally {
            hapticGenerator = null
            isEnabled = false
        }
    }
}
