package com.vibrosub.player.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object DemoBeatGenerator {

    /**
     * Generates a 16-second looping electronic track with punchy 808 kicks,
     * low-frequency sub-bass drops (40-60Hz), and crisp snare transients.
     * Saved to app cache directory as a standard PCM 16-bit WAV file.
     */
    fun getOrCreateDemoTrack(context: Context): Uri {
        val file = File(context.cacheDir, "vibrosub_demo_808_beat.wav")
        if (file.exists() && file.length() > 1000) {
            return Uri.fromFile(file)
        }

        val sampleRate = 44100
        val durationSeconds = 12
        val totalSamples = sampleRate * durationSeconds
        val bpm = 125
        val samplesPerBeat = (sampleRate * 60) / bpm

        val pcmData = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val beatIndex = (i / samplesPerBeat) % 16
            val sampleInBeat = i % samplesPerBeat
            val tInBeat = sampleInBeat.toDouble() / sampleRate

            var sample = 0.0

            // 1. Kick Drum (Beats 0, 4, 8, 10, 12)
            val isKickBeat = beatIndex in listOf(0, 4, 8, 10, 12)
            if (isKickBeat && tInBeat < 0.45) {
                // Pitch drop from 150 Hz down to 45 Hz
                val kickFreq = 45.0 + 105.0 * exp(-tInBeat * 18.0)
                val kickPhase = 2.0 * PI * kickFreq * tInBeat
                val kickAmp = exp(-tInBeat * 6.0)
                sample += sin(kickPhase) * kickAmp * 0.85
            }

            // 2. Rolling 808 Sub-Bass (Beats 2, 6, 14)
            val isSubBeat = beatIndex in listOf(2, 3, 6, 7, 14, 15)
            if (isSubBeat && tInBeat < 0.7) {
                val subFreq = if (beatIndex in listOf(6, 7)) 40.0 else 52.0
                val subPhase = 2.0 * PI * subFreq * tInBeat
                val subAmp = 0.75 * exp(-tInBeat * 2.5)
                // Add warm 2nd harmonic
                sample += (sin(subPhase) * 0.8 + sin(subPhase * 2.0) * 0.2) * subAmp
            }

            // 3. Crisp Snare / Clap (Beats 4, 12)
            val isSnareBeat = beatIndex in listOf(4, 12)
            if (isSnareBeat && tInBeat < 0.25) {
                val noise = (Random.nextDouble() * 2.0 - 1.0) * exp(-tInBeat * 20.0) * 0.4
                val tone = sin(2.0 * PI * 210.0 * tInBeat) * exp(-tInBeat * 15.0) * 0.4
                sample += noise + tone
            }

            // 4. Subtle Hi-Hat every half-beat
            val sampleInHalfBeat = i % (samplesPerBeat / 2)
            val tInHalfBeat = sampleInHalfBeat.toDouble() / sampleRate
            if (tInHalfBeat < 0.04) {
                val hatNoise = (Random.nextDouble() * 2.0 - 1.0) * exp(-tInHalfBeat * 90.0) * 0.12
                sample += hatNoise
            }

            // Master soft clip
            val clamped = sample.coerceIn(-1.0, 1.0)
            pcmData[i] = (clamped * 30000.0).toInt().toShort()
        }

        // Write WAV file
        writeWavFile(file, pcmData, sampleRate)
        return Uri.fromFile(file)
    }

    private fun writeWavFile(file: File, pcmData: ShortArray, sampleRate: Int) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val dataSize = pcmData.size * 2
        val totalSize = 36 + dataSize

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // AudioFormat (1 = PCM)
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((numChannels * bitsPerSample / 8).toShort()) // BlockAlign
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            val buffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                buffer.putShort(sample)
            }
            fos.write(buffer.array())
        }
    }
}
