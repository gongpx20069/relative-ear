package io.github.gongpx20069.relativeear

import io.github.gongpx20069.relativeear.core.SolfegeLesson
import org.junit.Assert.*
import org.junit.Test

class UiStateTest {
    @Test fun startsInBeginnerSolfegeListeningNotIntervalOrSinging() {
        val state = UiState()
        assertEquals(Screen.EAR, state.screen)
        assertEquals(SolfegeLesson.THREE_NOTES, state.lesson)
        assertEquals(listOf(0, 1, 2), state.lesson.degrees)
        assertNull(state.question)
        assertNull(state.doRoot)
        assertEquals(Phase.IDLE, state.phase)
    }

    @Test fun newSettingsAllowOctaveEquivalentSolfegeAnswers() {
        assertTrue(Settings().ignoreOctave)
        assertFalse(Settings(ignoreOctave = false).ignoreOctave)
    }
}
