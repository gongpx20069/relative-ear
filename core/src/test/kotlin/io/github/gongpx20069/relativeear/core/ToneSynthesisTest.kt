package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test

class ToneSynthesisTest {
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
