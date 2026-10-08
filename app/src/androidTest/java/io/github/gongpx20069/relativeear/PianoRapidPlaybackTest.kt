package io.github.gongpx20069.relativeear

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.gongpx20069.relativeear.core.FixedTraining
import io.github.gongpx20069.relativeear.core.ToneVoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class PianoRapidPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private class DelayedCleanupAudio(private val output: AudioSession) : AudioSession by output {
        val started = CopyOnWriteArrayList<Pair<Int, ToneVoice>>()
        val active = AtomicInteger()
        val outputStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()

        override suspend fun play(chords: List<List<Int>>, a4: Double, soundMs: Int, gapMs: Long,
            voice: ToneVoice, onChord: (Int) -> Unit) {
            if (active.getAndIncrement() != 0) {
                active.decrementAndGet()
                throw AudioFailure("Overlapping playback before previous cleanup finished")
            }
            started.add(chords.single().single() to voice)
            try {
                output.play(chords, a4, soundMs, gapMs, voice) {
                    outputStarted.complete(Unit)
                    onChord(it)
                }
            } finally {
                if (started.size == 1) withContext(NonCancellable) {
                    cleanupStarted.complete(Unit)
                    releaseCleanup.await()
                }
                active.decrementAndGet()
            }
        }
    }

    @Test fun genuinePlaybackFailureIsStillReportedForTheCurrentNote() {
        val output = AudioEngine(compose.activity.applicationContext) {}
        val audio = object : AudioSession by output {
            override suspend fun play(chords: List<List<Int>>, a4: Double, soundMs: Int, gapMs: Long,
                voice: ToneVoice, onChord: (Int) -> Unit) {
                throw AudioFailure("fixture device failure")
            }
        }
        val model = EarViewModel(compose.activity.application, audioOverride = audio)
        compose.waitUntil(10_000) { model.state.value.loaded }
        compose.runOnIdle { model.select(Screen.PIANO) }
        compose.waitUntil(10_000) { model.state.value.screen == Screen.PIANO }
        compose.runOnIdle { model.previewNote(60) }
        compose.waitUntil(10_000) { model.state.value.message != null }
        assertTrue(requireNotNull(model.state.value.message).contains("fixture device failure"))
        assertEquals(Phase.IDLE, model.state.value.phase)
        assertNull(model.state.value.pianoNote)
    }

    @Test fun rapidReplacementWaitsForOldestCleanupAndOnlyPlaysTheLatestNoteWithTheSelectedVoice() {
        val context = compose.activity.applicationContext
        val originalVoice = HistoryStore(context).use { it.pianoVoice() }
        val before = HistoryStore(context).use { it.history().total }
        val audio = DelayedCleanupAudio(AudioEngine(context) {})
        val model = EarViewModel(compose.activity.application, audioOverride = audio)
        try {
            compose.waitUntil(10_000) { model.state.value.loaded }
            compose.runOnIdle { model.select(Screen.PIANO) }
            compose.waitUntil(10_000) { model.state.value.screen == Screen.PIANO }
            compose.runOnIdle { model.pianoVoice(ToneVoice.FLUTE) }
            compose.waitUntil(10_000) { model.state.value.pianoVoice == ToneVoice.FLUTE && !model.state.value.pianoVoiceSaving }
            compose.runOnIdle { model.previewNote(60) }
            compose.waitUntil(10_000) { audio.outputStarted.isCompleted }
            compose.runOnIdle { model.previewNote(62) }
            compose.waitUntil(10_000) { audio.cleanupStarted.isCompleted }
            compose.runOnIdle {
                repeat(32) { model.previewNote(FixedTraining.notes[it % 8]) }
                model.previewNote(72)
            }
            Thread.sleep(150)
            assertEquals(listOf(60 to ToneVoice.FLUTE), audio.started.toList())
            assertEquals(1, audio.active.get())
            assertNull(model.state.value.message)
            audio.releaseCleanup.complete(Unit)
            compose.waitUntil(10_000) { model.state.value.phase == Phase.IDLE && audio.active.get() == 0 }
            assertEquals(listOf(60 to ToneVoice.FLUTE, 72 to ToneVoice.FLUTE), audio.started.toList())
            assertNull(model.state.value.message)
            assertEquals(before, HistoryStore(context).use { it.history().total })
        } finally {
            audio.releaseCleanup.complete(Unit)
            compose.runOnIdle { model.interrupt() }
            HistoryStore(context).use { it.savePianoVoice(originalVoice) }
        }
    }
}
