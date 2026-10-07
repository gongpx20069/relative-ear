package io.github.gongpx20069.relativeear.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class FixedTrainingTest {
    @Test fun defaultIsFixedC4DoReMi() {
        val setup = TrainingSetup()
        assertEquals(listOf(60, 62, 64), setup.notes)
        assertEquals(80, setup.bpm)
        assertEquals(AnswerNotation.SOLFEGE, setup.notation)
        assertEquals(261.625565, Music.frequency(60.0), 0.00001)
        assertEquals(listOf("Do", "Re", "Mi"), setup.notes.map(FixedTraining::syllable))
    }
    @Test fun customRangeAndNotationNeverChangeTheQuestionPitchOrAnswerIdentity() {
        val range = listOf(64, 67, 72)
        for (notation in AnswerNotation.entries) {
            val setup = TrainingSetup(range, notation)
            val observed = mutableSetOf<Int>()
            repeat(500) {
                val question = FixedTraining.question(setup, Random(it))
                assertEquals(60, question.root)
                assertTrue(question.target in range)
                assertEquals(question.target, question.answer)
                observed.add(question.target)
            }
            assertEquals(range.toSet(), observed)
        }
    }
    @Test fun octaveDoAnswersHaveDifferentIdentities() {
        assertEquals("Do", FixedTraining.syllable(60))
        assertEquals("Do", FixedTraining.syllable(72))
        assertEquals(0, FixedTraining.degree(60))
        assertEquals(7, FixedTraining.degree(72))
        assertNotEquals(NoteQuestion(60).answer, NoteQuestion(72).answer)
        assertNull(FixedTraining.degree(61))
    }
    @Test fun referenceCoversExactlyTheSelectedNotesPlusC4Anchor() {
        val setup = TrainingSetup(listOf(62, 67, 72))
        assertEquals(listOf(60, 62, 67, 72, 60), FixedTraining.reference(setup))
        assertEquals(listOf(60, 62, 64, 60), FixedTraining.reference(TrainingSetup()))
    }
    @Test fun eighthNotesOccupyHalfAQuarterNoteBeatIncludingArticulation() {
        for (bpm in FixedTraining.tempos) {
            val timing = TrainingSetup(bpm = bpm).timing
            assertEquals(30_000.0 / bpm, timing.slotMs.toDouble(), 0.001)
            assertEquals(timing.slotMs.toLong(), timing.soundMs + timing.gapMs)
            assertTrue(timing.soundMs > 0 && timing.gapMs > 0)
        }
        assertEquals(375, EighthTiming(80).slotMs)
        assertEquals(250, EighthTiming(120).slotMs)
    }
    @Test fun invalidConfigurationsAreRejected() {
        for (range in listOf(emptyList(), listOf(60, 60), listOf(64, 60), listOf(61), listOf(48))) {
            assertThrows(IllegalArgumentException::class.java) { TrainingSetup(notes = range) }
        }
        assertThrows(IllegalArgumentException::class.java) { TrainingSetup(bpm = 0) }
        assertThrows(IllegalArgumentException::class.java) { NoteQuestion(61) }
    }
}
