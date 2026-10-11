package io.github.gongpx20069.relativeear

import io.github.gongpx20069.relativeear.core.NoteEvent
import io.github.gongpx20069.relativeear.core.DetectionTake
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.PitchPoint
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
        assertTrue(ready.copy(listeningMs = 70_000).canReplay)
        assertEquals(70_000L, ready.copy(listeningMs = 70_000).listeningClip?.durationMs)
    }
    @Test fun savedTuningAndHistoryReplayStayIndependentOfCurrentSettingsAndDraftSaving() {
        val take = DetectionTake(MelodyClip(listOf(NoteEvent(61, 128, 640)), 90_000, 415.0),
            listOf(PitchPoint(128, 61.0, 0.99)))
        val draft = UiState(screen = Screen.LISTEN, loaded = true, listeningTake = take, settings = Settings(a4 = 466))
        assertEquals(415.0, draft.listeningClip!!.a4, 0.0)
        assertTrue(draft.canSaveDetection)
        assertFalse(draft.copy(listeningSaved = true).canSaveDetection)
        assertFalse(draft.copy(phase = Phase.LISTENING).canSaveDetection)
        val summary = DetectionSummary("test", "", 1234, 90_000, 415.0, 1, 61, 61)
        val history = draft.copy(screen = Screen.HISTORY, selectedDetection = summary, savedDetection = SavedDetection(summary, take))
        assertTrue(history.canReplay)
        assertFalse(history.canSaveDetection)
        assertFalse(history.copy(detectionLoading = true).canReplay)
        assertFalse(history.copy(phase = Phase.SAVING).canReplay)
        assertEquals(take.clip, history.activeClip)
        val curve = DetectionTake(MelodyClip(emptyList(), 128), listOf(PitchPoint(128, 60.0, 0.99)))
        assertTrue(draft.copy(listeningTake = curve).canSaveDetection)
        assertFalse(draft.copy(listeningTake = curve).canReplay)
    }
}
