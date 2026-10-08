package io.github.gongpx20069.relativeear

import android.os.SystemClock
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.PitchFrame
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File

class PianoPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualMainPageHasEightAccessiblePianoKeysAndPreviewDoesNotSaveAnAttempt() {
        compose.waitUntil(10_000) {
            compose.onNodeWithText("开始练习").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled).not()
        }
        val context = compose.activity.applicationContext
        val before = HistoryStore(context).use { it.history().total }
        val permission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithText("八键小钢琴").performScrollTo()
        for (note in listOf(60, 62, 64, 65, 67, 69, 71, 72)) compose.onNodeWithTag("piano-$note").assertExists()
        compose.onNodeWithTag("piano-72").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("piano-72").fetchSemanticsNode().config[SemanticsProperties.Selected] }
        compose.waitUntil(10_000) {
            !compose.onNodeWithTag("piano-72").fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        assertEquals(before, HistoryStore(context).use { it.history().total })
        assertEquals(permission, context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
        screenshot("piano-keys.png")
    }

    private class FixtureAudio(private val output: AudioSession, val gate: CompletableDeferred<Unit>? = null) :
        AudioSession by output {
        val positions = CopyOnWriteArrayList<Long>()
        override suspend fun capture(limitMs: Long?, onFrame: (PitchFrame) -> Boolean) {
            gate?.await()
            for (time in 128L..2560L step 32L) {
                val midi = when (time) { in 128L..736L -> 60; in 1280L..2144L -> 64; else -> null }
                if (!onFrame(PitchFrame(time, midi?.let { Music.frequency(it.toDouble()) },
                    if (midi == null) 0.0 else 0.99, 0.1))) break
            }
        }
        override suspend fun playMelody(clip: MelodyClip, onPosition: (Long) -> Unit) {
            output.playMelody(clip) { positions.add(it); onPosition(it) }
        }
    }
    private fun studio(audio: FixtureAudio): EarViewModel {
        val model = EarViewModel(compose.activity.application, audioOverride = audio)
        compose.activity.runOnUiThread {
            model.select(Screen.LISTEN)
            compose.activity.setContent {
                val state by model.state.collectAsState()
                EarTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ListeningPage(state, model::listen, model::stopListening,
                            model::replayDetected, model::stopReplay, model::previewNote)
                    }
                }
            }
        }
        compose.waitUntil(10_000) { model.state.value.loaded }
        return model
    }

    @Test fun realAudioTrackReplaysSynthesizedNotesForTheOriginalDurationAndDrivesTheRedCursor() {
        val audio = FixtureAudio(AudioEngine(compose.activity.applicationContext) {})
        val model = studio(audio)
        compose.onNodeWithText("开始监听").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.canReplay }
        assertEquals(2, model.state.value.notes.size)
        assertEquals(2560L, model.state.value.listeningClip?.durationMs)
        val since = SystemClock.elapsedRealtime()
        compose.onNodeWithText("回放识别音符").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.replayPositionMs >= 300 }
        compose.onNodeWithTag("piano-60").assertIsNotEnabled()
        compose.waitUntil(10_000) { model.state.value.phase == Phase.IDLE }
        val elapsed = SystemClock.elapsedRealtime() - since
        assertTrue("Playback ended too early: $elapsed", elapsed >= 2400)
        assertTrue("Playback took too long: $elapsed", elapsed <= 4500)
        assertEquals(2560L, model.state.value.replayPositionMs)
        assertTrue(audio.positions.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(2560L, audio.positions.last())
        assertEquals(1f, compose.onNodeWithTag("melody-timeline").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        compose.onNodeWithText("音符白板与回放").performScrollTo()
        screenshot("melody-playback.png")
    }

    @Test fun microphoneAndPianoAreGatedAndStoppingOrInterruptingReplayRetainsNotes() {
        val gate = CompletableDeferred<Unit>()
        val audio = FixtureAudio(AudioEngine(compose.activity.applicationContext) {}, gate)
        val model = studio(audio)
        compose.onNodeWithText("开始监听").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.phase == Phase.LISTENING }
        compose.onNodeWithTag("piano-60").assertIsNotEnabled()
        compose.onNodeWithText("回放识别音符").assertIsNotEnabled()
        gate.complete(Unit)
        compose.waitUntil(10_000) { model.state.value.canReplay }
        compose.onNodeWithText("回放识别音符").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.replayPositionMs >= 200 }
        compose.onNodeWithText("开始监听").assertIsNotEnabled()
        compose.onNodeWithText("停止回放").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.phase == Phase.IDLE }
        val stopped = model.state.value.replayPositionMs
        assertTrue(stopped < 2560)
        Thread.sleep(150)
        assertEquals(stopped, model.state.value.replayPositionMs)
        assertTrue(model.state.value.canReplay)
        compose.onNodeWithText("回放识别音符").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model.state.value.replayPositionMs >= 200 }
        compose.activity.runOnUiThread { model.interrupt() }
        compose.waitUntil(10_000) { model.state.value.phase == Phase.IDLE }
        assertEquals(2, model.state.value.notes.size)
        compose.activity.runOnUiThread { model.select(Screen.EAR) }
        compose.waitUntil(10_000) { model.state.value.screen == Screen.EAR }
        assertNull(model.state.value.listeningClip)
    }
    private fun screenshot(name: String) {
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Screenshot encoding failed" }
        }
    }
}
