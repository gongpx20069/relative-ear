package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test

class ToneSynthesisTest {
    @Test fun instrumentVoicesHaveDistinctWaveformsWithoutChangingPitchTimingOrTuning() {
        for (a4 in listOf(415.0, 440.0, 466.0)) {
            for (note in FixedTraining.notes) {
                val sounds = ToneVoice.entries.map { voice ->
                    val samples = ToneSynthesis.render(listOf(listOf(note)), a4, 500, 50, voice)
                    assertEquals(8800, samples.size)
                    assertEquals(0, samples.first().toInt())
                    assertTrue(samples.sliceArray(7999 until samples.size).all { it == 0.toShort() })
                    assertTrue(samples.all { kotlin.math.abs(it.toInt()) <= 12000 })
                    for (offset in listOf(640, 2560)) {
                        val frame = PitchDetector().detect(FloatArray(2048) { samples[offset + it] / 32768f }, 0)
                        assertNotNull("$voice failed to detect $note at A4=$a4", frame.frequency)
                        assertEquals(note.toDouble(), Music.midi(requireNotNull(frame.frequency), a4), 0.1)
                    }
                    samples
                }
                for (i in sounds.indices) for (j in i + 1 until sounds.size) {
                    assertFalse(sounds[i].contentEquals(sounds[j]))
                }
            }
        }
        val piano = ToneSynthesis.render(listOf(listOf(60)), 440.0, 500, 0, ToneVoice.PIANO)
        fun energy(offset: Int) = (offset until offset + 1600).sumOf { piano[it].toDouble() * piano[it] }
        assertTrue("Piano should decay rather than sustain like the flute", energy(4800) < energy(800) * 0.5)
        val originalPure = ShortArray(8800) { index ->
            if (index >= 8000) 0 else {
                val envelope = minOf(1.0, index / 160.0, (7999 - index) / 320.0).coerceAtLeast(0.0)
                (kotlin.math.sin(2 * kotlin.math.PI * Music.frequency(60.0) * index / 16000) *
                    envelope * 12000).toInt().toShort()
            }
        }
        assertArrayEquals(originalPure, ToneSynthesis.render(listOf(listOf(60)), 440.0, 500, 50))
        assertArrayEquals(originalPure,
            ToneSynthesis.render(listOf(listOf(60)), 440.0, 500, 50, ToneVoice.PURE))
    }
    @Test fun actualPcmContainsPreciselyTimedEighthNoteSlotsAndSilentArticulation() {
        for (bpm in FixedTraining.tempos) {
            val timing = EighthTiming(bpm)
            val slot = ToneSynthesis.samplesPerTone(timing.soundMs, timing.gapMs)
            val samples = ToneSynthesis.render(FixedTraining.notes.map { listOf(it) }, 440.0,
                timing.soundMs, timing.gapMs)
            assertEquals(ToneSynthesis.SAMPLE_RATE * timing.slotMs / 1000, slot)
            assertEquals(slot * 8, samples.size)
            for (i in 0..7) {
                val soundEnd = i * slot + ToneSynthesis.SAMPLE_RATE * timing.soundMs / 1000
                assertTrue(samples.sliceArray(soundEnd until (i + 1) * slot).all { it == 0.toShort() })
                assertTrue(samples.sliceArray(i * slot until soundEnd).any { it != 0.toShort() })
            }
        }
    }
    @Test fun generatedAudioActuallyMatchesEveryAdvertisedC4ToC5Pitch() {
        for (note in FixedTraining.notes) {
            val timing = EighthTiming(120)
            val samples = ToneSynthesis.render(listOf(listOf(note)), 440.0, timing.soundMs, timing.gapMs)
            val window = FloatArray(2048) { samples[it + 320] / 32768f }
            val result = PitchDetector().detect(window, 0)
            assertNotNull(result.frequency)
            assertEquals(note.toDouble(), Music.midi(requireNotNull(result.frequency)), 0.1)
        }
    }
}
