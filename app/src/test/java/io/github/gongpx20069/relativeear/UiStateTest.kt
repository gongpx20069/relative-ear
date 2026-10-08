package io.github.gongpx20069.relativeear

import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.NoteQuestion
import org.junit.Assert.*
import org.junit.Test

class UiStateTest {
    @Test fun startsInBeginnerSolfegeListeningNotIntervalOrSinging() {
        val state = UiState()
        assertEquals(Screen.EAR, state.screen)
        assertEquals(listOf(60, 62, 64), state.training.notes)
        assertEquals(AnswerNotation.SOLFEGE, state.training.notation)
        assertNull(state.question)
        assertNull(state.demoNote)
        assertEquals(Phase.IDLE, state.phase)
    }

    @Test fun fixedPitchDefaultsToStrictOctavesButKeepsAnExplicitOptOut() {
        assertFalse(Settings().ignoreOctave)
        assertTrue(Settings(ignoreOctave = true).ignoreOctave)
    }
    @Test fun answerPlaybackIsOnlyAvailableAfterScoring() {
        for (phase in Phase.entries) {
            assertEquals(phase in listOf(Phase.FEEDBACK, Phase.COMPLETE),
                UiState(question = NoteQuestion(64), phase = phase).canHearAnswer)
        }
        assertFalse(UiState(phase = Phase.COMPLETE).canHearAnswer)
    }
    @Test fun configurationCannotChangeMidRoundOrBeforeLoading() {
        for (phase in Phase.entries) {
            assertEquals(phase in listOf(Phase.IDLE, Phase.COMPLETE),
                UiState(loaded = true, phase = phase).configurable)
        }
        assertFalse(UiState().configurable)
    }
    @Test fun historyCountsPracticesButAccuracyRemainsWeightedByAnsweredNotes() {
        val history = History(total = 10, correct = 1, practiceCount = 2)
        assertEquals(2, history.practiceCount)
        assertEquals(10, history.accuracy)
        assertEquals(0, History().accuracy)
        val practice = PracticeSummary("round", "fixed_note", 1000, 3, 2)
        assertEquals(66, practice.accuracy)
        assertNull(UiState().selectedPractice)
        assertTrue(UiState().practiceAttempts.isEmpty())
        assertFalse(UiState().practiceLoading)
    }
}
