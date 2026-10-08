package io.github.gongpx20069.relativeear

import io.github.gongpx20069.relativeear.core.NoteEvent
import org.junit.Assert.*
import org.junit.Test

class PlaybackStateTest {
    @Test fun pianoCannotProvideAnswersOrRecordItsOwnOutput() {
        for (phase in Phase.entries) {
            assertEquals(phase in listOf(Phase.IDLE, Phase.COMPLETE),
                UiState(screen = Screen.PIANO, loaded = true, phase = phase).canPreview)
        }
        assertTrue(UiState(screen = Screen.PIANO, loaded = true, phase = Phase.DEMONSTRATING, pianoNote = 60).canPreview)
        Screen.entries.filter { it != Screen.PIANO }.forEach {
            assertFalse(UiState(screen = it, loaded = true).canPreview)
        }
        assertFalse(UiState().canPreview)
    }
    @Test fun replayRequiresRecognizedNotesAndAnIdleListeningPageWithoutMicrophoneCapture() {
        val ready = UiState(loaded = true, screen = Screen.LISTEN, listeningMs = 3000,
            notes = listOf(NoteEvent(60, 500, 1000)))
        assertTrue(ready.canReplay)
        assertEquals(3000L, ready.listeningClip?.durationMs)
        assertFalse(ready.copy(phase = Phase.LISTENING).canReplay)
        assertFalse(ready.copy(phase = Phase.REPLAYING).canPreview)
        assertFalse(ready.copy(screen = Screen.EAR).canReplay)
        assertFalse(ready.copy(notes = emptyList()).canReplay)
        assertFalse(ready.copy(listeningMs = 70_000).canReplay)
    }
}
