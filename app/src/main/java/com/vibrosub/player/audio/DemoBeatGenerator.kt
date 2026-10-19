package com.vibrosub.player.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object DemoBeatGenerator {

    /**
     * Generates a 12-second looping electronic track with punchy 808 kicks,
     * low-frequency sub-bass drops (40-60Hz), and crisp snare transients.
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

            val clamped = sample.coerceIn(-1.0, 1.0)
            pcmData[i] = (clamped * 30000.0).toInt().toShort()
        }

        writeWavFile(file, pcmData, sampleRate)
        return Uri.fromFile(file)
    }

    /**
     * Generates a 14-second looping melodic acoustic track inspired by Radiohead - No Surprises:
     * - Glockenspiel / Music Box bells (pure crystalline tones at 1400 - 3000 Hz)
     * - Plucked acoustic guitar arpeggio (200 - 600 Hz)
     * - Gentle melodic bass (85 Hz)
     * - Soft brushed percussion
     * Perfect for testing tactile bell and melodic accompaniment on Pixel 8 Pro!
     */
    fun getOrCreateRadioheadAcousticDemo(context: Context): Uri {
        val file = File(context.cacheDir, "vibrosub_demo_no_surprises_acoustic.wav")
        if (file.exists() && file.length() > 1000) {
            return Uri.fromFile(file)
        }

        val sampleRate = 44100
        val bpm = 76.0 // Radiohead No Surprises tempo
        val beatsPerSecond = bpm / 60.0
        val totalBeats = 16 // 4 bars of 4/4
        val durationSeconds = (totalBeats / beatsPerSecond).toInt() + 1
        val totalSamples = sampleRate * durationSeconds
        val samplesPerBeat = (sampleRate / beatsPerSecond).toInt()
        val samplesPerEighth = samplesPerBeat / 2

        val pcmData = ShortArray(totalSamples)

        // Melodic bell notes (frequencies in Hz): F5, Bb5, C6, D6
        val bellFrequencies = doubleArrayOf(
            698.46 * 2, // F6 1396.9 Hz
            932.33 * 2, // Bb6 1864.6 Hz
            1046.50 * 2, // C7 2093.0 Hz
            1174.66 * 2, // D7 2349.3 Hz
            1046.50 * 2, // C7
            932.33 * 2,  // Bb6
            698.46 * 2,  // F6
            880.00 * 2   // A6 1760.0 Hz
        )

        // Guitar arpeggio frequencies (Hz)
        val guitarFrequencies = doubleArrayOf(
            174.61 * 2, // F4 349.2 Hz
            220.00 * 2, // A4 440.0 Hz
            261.63 * 2, // C5 523.2 Hz
            293.66 * 2  // D5 587.3 Hz
        )

        for (i in 0 until totalSamples) {
            val eighthIndex = (i / samplesPerEighth) % 32
            val sampleInEighth = i % samplesPerEighth
            val tInEighth = sampleInEighth.toDouble() / sampleRate

            val beatIndex = (i / samplesPerBeat) % 16
            val sampleInBeat = i % samplesPerBeat
            val tInBeat = sampleInBeat.toDouble() / sampleRate

            var sample = 0.0

            // 1. Glockenspiel / Music Box Bells (Iconic No Surprises motif)
            val bellPitch = bellFrequencies[eighthIndex % bellFrequencies.size]
            if (tInEighth < 0.40) {
                // Bell physics: fundamental + shimmer harmonics
                val bellEnvelope = exp(-tInEighth * 9.0)
                val bellTone = sin(2.0 * PI * bellPitch * tInEighth) * 0.7 +
                        sin(2.0 * PI * (bellPitch * 2.76) * tInEighth) * 0.25 +
                        sin(2.0 * PI * (bellPitch * 5.4) * tInEighth) * 0.1
                sample += bellTone * bellEnvelope * 0.65
            }

            // 2. Plucked Acoustic Guitar
            val guitarPitch = guitarFrequencies[(eighthIndex / 2) % guitarFrequencies.size]
            if (tInEighth < 0.35) {
                val guitarEnvelope = exp(-tInEighth * 7.5)
                val guitarTone = sin(2.0 * PI * guitarPitch * tInEighth) * 0.5 +
                        sin(2.0 * PI * (guitarPitch * 2.0) * tInEighth) * 0.25
                sample += guitarTone * guitarEnvelope * 0.40
            }

            // 3. Gentle Acoustic Bassline (Melodic notes at 87 Hz and 116 Hz)
            val isBassBeat = (beatIndex % 2 == 0)
            if (isBassBeat && tInBeat < 0.65) {
                val bassFreq = if (beatIndex % 4 == 0) 87.31 else 116.54 // F2 / Bb2
                val bassEnvelope = exp(-tInBeat * 3.5)
                val bassTone = sin(2.0 * PI * bassFreq * tInBeat) * 0.7 +
                        sin(2.0 * PI * (bassFreq * 2.0) * tInBeat) * 0.3
                sample += bassTone * bassEnvelope * 0.50
            }

            // 4. Soft Brushed Snare (Gentle backbeat on beats 2 and 4)
            val isBrushBeat = (beatIndex % 4 == 1 || beatIndex % 4 == 3)
            if (isBrushBeat && tInBeat < 0.22) {
                val brushNoise = (Random.nextDouble() * 2.0 - 1.0) * exp(-tInBeat * 24.0) * 0.18
                sample += brushNoise
            }

            val clamped = sample.coerceIn(-1.0, 1.0)
            pcmData[i] = (clamped * 28000.0).toInt().toShort()
        }

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
