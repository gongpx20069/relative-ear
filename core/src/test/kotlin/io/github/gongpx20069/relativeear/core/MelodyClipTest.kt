package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test

class MelodyClipTest {
    @Test fun actualPcmPreservesDifferentDurationsLeadingSilenceGapsAndTrailingSilence() {
        val clip = MelodyClip(listOf(NoteEvent(60, 128, 640), NoteEvent(64, 1280, 896)), 2560)
        val samples = ToneSynthesis.render(clip)
        assertEquals(40_960, samples.size)
        for (range in listOf(0 until 2048, 12_288 until 20_480, 34_816 until 40_960)) {
            assertTrue(samples.sliceArray(range).all { it == 0.toShort() })
        }
        for (note in clip.notes) {
            val start = (note.startMs * 16).toInt()
            val length = (note.durationMs * 16).toInt()
            assertTrue(samples.sliceArray(start until start + length).any { it != 0.toShort() })
            val frame = PitchDetector().detect(FloatArray(2048) { samples[start + 320 + it] / 32768f }, 0)
            assertEquals(note.midi.toDouble(), Music.midi(requireNotNull(frame.frequency)), 0.1)
        }
        assertNull(clip.noteAt(127))
        assertEquals(60, clip.noteAt(128))
        assertNull(clip.noteAt(768))
        assertEquals(64, clip.noteAt(1280))
        assertNull(clip.noteAt(2176))
        assertNull(clip.noteAt(2560))
    }

    @Test fun actualSynthesizedPitchesPreserveDetectedOctavesChromaticNotesAndTuning() {
        for (a4 in listOf(415.0, 440.0, 466.0)) {
            for (midi in listOf(38, 60, 61, 64, 72, 81)) {
                val samples = ToneSynthesis.render(MelodyClip(listOf(NoteEvent(midi, 0, 500)), 500, a4))
                val frame = PitchDetector().detect(FloatArray(2048) { samples[it + 320] / 32768f }, 0)
                assertEquals(midi.toDouble(), Music.midi(requireNotNull(frame.frequency), a4), 0.1)
            }
        }
    }

    @Test fun rollingWindowCropsCrossingNotesWithoutCompressingGapsOrChangingOrder() {
        val clip = MelodyClip.recent(listOf(NoteEvent(60, 0, 70_000),
            NoteEvent(64, 75_000, 20_000), NoteEvent(67, 110_000, 20_000)), 125_000, 440.0)
        assertEquals(60_000L, clip.durationMs)
        assertEquals(listOf(NoteEvent(60, 0, 5000), NoteEvent(64, 10_000, 20_000),
            NoteEvent(67, 45_000, 15_000)), clip.notes)
        assertEquals(960_000, ToneSynthesis.render(clip).size)
    }

    @Test fun longSustainedToneAndExpiredNotesStillHaveBoundedPlaybackMemory() {
        val clip = MelodyClip.recent(listOf(NoteEvent(60, 0, 180_032)), 180_000, 440.0)
        assertEquals(listOf(NoteEvent(60, 0, 60_000)), clip.notes)
        assertEquals(960_000, ToneSynthesis.render(clip).size)
        assertTrue(MelodyClip.recent(listOf(NoteEvent(60, 0, 1000)), 180_000, 440.0).notes.isEmpty())
    }

    @Test fun silenceIsNotInventedAsAPitchedNoteAndInvalidTimelinesAreRejected() {
        assertTrue(ToneSynthesis.render(MelodyClip(emptyList(), 1000)).all { it == 0.toShort() })
        assertThrows(IllegalArgumentException::class.java) { MelodyClip(emptyList(), 60_001) }
        assertThrows(IllegalArgumentException::class.java) { MelodyClip(emptyList(), 0) }
        assertThrows(IllegalArgumentException::class.java) { MelodyClip(emptyList(), 1000, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) {
            MelodyClip(listOf(NoteEvent(60, 0, 500), NoteEvent(64, 400, 500)), 1000)
        }
        assertThrows(IllegalArgumentException::class.java) { MelodyClip(listOf(NoteEvent(60, 900, 200)), 1000) }
        assertThrows(IllegalArgumentException::class.java) { MelodyClip.recent(listOf(NoteEvent(60, -1, 100)), 1000, 440.0) }
    }
}
