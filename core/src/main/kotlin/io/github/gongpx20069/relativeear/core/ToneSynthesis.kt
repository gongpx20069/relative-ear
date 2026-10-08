package io.github.gongpx20069.relativeear.core

import kotlin.math.PI
import kotlin.math.sin

object ToneSynthesis {
    const val SAMPLE_RATE = 16_000
    fun samplesPerTone(soundMs: Int, gapMs: Long): Int {
        require(soundMs in 1..10_000 && gapMs in 0..10_000)
        return SAMPLE_RATE * (soundMs + gapMs.toInt()) / 1000
    }
    fun render(chords: List<List<Int>>, a4: Double, soundMs: Int, gapMs: Long): ShortArray {
        require(chords.isNotEmpty() && chords.size <= 64 && chords.all { it.isNotEmpty() })
        val slot = samplesPerTone(soundMs, gapMs)
        val sound = SAMPLE_RATE * soundMs / 1000
        val output = ShortArray(slot * chords.size)
        for ((chordIndex, chord) in chords.withIndex()) {
            val frequencies = chord.map { Music.frequency(it.toDouble(), a4) }
            writeTone(output, chordIndex * slot, sound, frequencies)
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
    private fun writeTone(output: ShortArray, offset: Int, sound: Int, frequencies: List<Double>) {
        for (index in 0 until sound) {
            val envelope = minOf(1.0, index / 160.0, (sound - 1 - index) / 320.0).coerceAtLeast(0.0)
            val wave = frequencies.sumOf { sin(2 * PI * it * index / SAMPLE_RATE) } / frequencies.size
            output[offset + index] = (wave * envelope * 12_000).toInt().toShort()
        }
    }
}
