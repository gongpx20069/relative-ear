package io.github.gongpx20069.relativeear.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class ToneVoice { PURE, PIANO, FLUTE }

object ToneSynthesis {
    const val SAMPLE_RATE = 16_000
    fun samplesPerTone(soundMs: Int, gapMs: Long): Int {
        require(soundMs in 1..10_000 && gapMs in 0..10_000)
        return SAMPLE_RATE * (soundMs + gapMs.toInt()) / 1000
    }
    fun render(chords: List<List<Int>>, a4: Double, soundMs: Int, gapMs: Long,
        voice: ToneVoice = ToneVoice.PURE): ShortArray {
        require(chords.isNotEmpty() && chords.size <= 64 && chords.all { it.isNotEmpty() })
        val slot = samplesPerTone(soundMs, gapMs)
        val sound = SAMPLE_RATE * soundMs / 1000
        val output = ShortArray(slot * chords.size)
        for ((chordIndex, chord) in chords.withIndex()) {
            val frequencies = chord.map { Music.frequency(it.toDouble(), a4) }
            writeTone(output, chordIndex * slot, sound, frequencies, voice)
        }
        return output
    }
    fun render(clip: MelodyClip): ShortArray {
        val output = ShortArray((clip.durationMs * SAMPLE_RATE / 1000).toInt())
        clip.notes.forEach {
            writeTone(output, (it.startMs * SAMPLE_RATE / 1000).toInt(),
                (it.durationMs * SAMPLE_RATE / 1000).toInt(), listOf(Music.frequency(it.midi.toDouble(), clip.a4)))
        }
        return output
    }
    private fun writeTone(output: ShortArray, offset: Int, sound: Int, frequencies: List<Double>,
        voice: ToneVoice = ToneVoice.PURE) {
        for (index in 0 until sound) {
            val seconds = index.toDouble() / SAMPLE_RATE
            val attack = if (voice == ToneVoice.FLUTE) 560.0 else 160.0
            val envelope = minOf(1.0, index / attack, (sound - 1 - index) / 320.0).coerceAtLeast(0.0) *
                if (voice == ToneVoice.PIANO) exp(-3 * seconds) else 1.0
            val wave = frequencies.sumOf {
                val phase = 2 * PI * it * index / SAMPLE_RATE
                when (voice) {
                    ToneVoice.PURE -> sin(phase)
                    ToneVoice.PIANO -> (sin(phase) + 0.45 * exp(-4 * seconds) * sin(2 * phase) +
                        0.22 * exp(-6 * seconds) * sin(3 * phase) +
                        0.10 * exp(-8 * seconds) * sin(4 * phase)) / 1.77
                    ToneVoice.FLUTE -> (sin(phase) + 0.12 * sin(2 * phase) + 0.04 * sin(3 * phase)) / 1.16
                }
            } / frequencies.size
            output[offset + index] = (wave * envelope * 12_000).toInt().toShort()
        }
    }
}
