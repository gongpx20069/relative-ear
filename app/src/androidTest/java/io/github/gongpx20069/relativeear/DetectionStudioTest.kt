package io.github.gongpx20069.relativeear

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.PitchFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class DetectionStudioTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(TestLocaleRule("en"))

    private class FixtureAudio(private val output: AudioSession, private val durationMs: Long,
        val gate: CompletableDeferred<Unit>? = null) : AudioSession by output {
        @Volatile var limit: Long? = null
        @Volatile var captures = 0
        override suspend fun capture(limitMs: Long?, onFrame: (PitchFrame) -> Boolean) {
            limit = limitMs
            captures++
            for (time in 128L..(durationMs + 32) step 32L) {
                val midi = when (time) {
                    in 128L..736L -> 60.0
                    in 1280L..5504L -> 64.0 + 0.1 * kotlin.math.sin(time / 100.0)
                    in 6016L..9984L -> 67.0
                    in 70_016L..70_848L -> 61.0
                    else -> null
                }
                if (!onFrame(PitchFrame(time, midi?.let { Music.frequency(it) },
                        if (midi == null) 0.0 else 0.99, 0.1))) break
                if (time == 10_016L) gate?.await()
            }
        }
    }

    private fun studio(audio: FixtureAudio): EarViewModel {
        val model = EarViewModel(compose.activity.application, audioOverride = audio)
        compose.runOnIdle {
            model.select(Screen.LISTEN)
            compose.activity.setContent {
                val state by model.state.collectAsState(context = Dispatchers.Main.immediate)
                EarTheme {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.message?.let { TextMessage(it) }
                        if (state.screen == Screen.HISTORY) HistoryStudio(state, model)
                        else ListeningPage(state, { model.listen(discardUnsaved = true) }, model::stopListening,
                            model::replayDetected, model::stopReplay, model::saveDetection,
                            model::detectionMode, model::detectionNotation)
                    }
                }
            }
        }
        compose.waitUntil(10_000) { model.state.value.loaded && model.state.value.screen == Screen.LISTEN }
        return model
    }

    @Test fun curveAndNotesReviewFullClipManualSaveRenameReplayAndDeleteWithoutChangingPracticeScores() {
        val before = HistoryStore(compose.activity).use { it.history() }
        val audio = FixtureAudio(AudioEngine(compose.activity) {}, 90_000, CompletableDeferred())
        val model = studio(audio)
        var savedId: String? = null
        try {
            compose.onNodeWithText("Start listening").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.state.value.listeningMs == 10_016L }
            compose.onNodeWithTag("detection-mode-CURVE").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Save detection clip").assertIsNotEnabled()
            compose.onNodeWithText("Replay detected notes").assertIsNotEnabled()
            compose.onNodeWithTag("melody-timeline").performScrollTo()
            screenshot("detection-live-curve.png")
            audio.gate!!.complete(Unit)
            compose.waitUntil(10_000) { model.state.value.phase == Phase.IDLE }
            assertEquals(MelodyClip.MAX_DURATION_MS, audio.limit)
            val take = checkNotNull(model.state.value.listeningTake)
            assertTrue(take.clip.durationMs > 60_000)
            assertEquals(128L, take.clip.notes.first().startMs)
            compose.onNodeWithTag("detection-review-slider").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(65_000f) }
            val description = compose.onNodeWithTag("melody-timeline").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single()
            assertTrue(description.contains("65.0"))
            compose.onNodeWithTag("melody-timeline").performScrollTo().performTouchInput { swipeLeft() }
            assertTrue(compose.onNodeWithTag("detection-review-slider").fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo].current > 65_000f)
            compose.onNodeWithTag("detection-mode-NOTES").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("detection-label-NOTE_NAME").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Save detection clip").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(10_000) { model.state.value.listeningSaved && model.state.value.phase == Phase.IDLE }
            savedId = model.state.value.listeningId
            compose.onNodeWithText("Clip saved").assertIsNotEnabled()
            assertEquals(before, HistoryStore(compose.activity).use { it.history() })
            compose.runOnIdle { model.select(Screen.HISTORY) }
            compose.waitUntil(10_000) { model.state.value.screen == Screen.HISTORY }
            compose.onNodeWithTag("history-section-DETECTIONS").performClick()
            compose.onNodeWithTag("detection-$savedId").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.state.value.savedDetection != null }
            assertEquals(take, model.state.value.savedDetection?.take)
            compose.onNodeWithText("Rename clip").performScrollTo().performClick()
            compose.onNodeWithTag("detection-name-input").performTextReplacement("My melody")
            compose.onNodeWithText("Save name").performClick()
            compose.waitUntil(10_000) { model.state.value.selectedDetection?.title == "My melody" && model.state.value.phase == Phase.IDLE }
            compose.onNodeWithTag("detection-mode-CURVE").performScrollTo().performClick()
            compose.onNodeWithTag("melody-timeline").performScrollTo()
            screenshot("detection-saved-curve.png")
            compose.onNodeWithText("Replay detected notes").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.state.value.replayPositionMs >= 200 }
            compose.onNodeWithText("Stop replay").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.state.value.phase == Phase.IDLE }
            assertTrue(model.state.value.replayPositionMs < take.clip.durationMs)
            compose.onNodeWithText("Delete").performScrollTo().performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertNotNull(model.state.value.selectedDetection)
            compose.onNodeWithText("Delete").performScrollTo().performClick()
            val deletes = compose.onAllNodesWithText("Delete")
            deletes[deletes.fetchSemanticsNodes().lastIndex].performClick()
            compose.waitUntil(10_000) { model.state.value.selectedDetection == null && model.state.value.phase == Phase.IDLE }
            assertFalse(model.state.value.detections.any { it.id == savedId })
            assertEquals(before, HistoryStore(compose.activity).use { it.history() })
        } finally {
            audio.gate?.complete(Unit)
            compose.runOnIdle { model.interrupt() }
            savedId?.let { id ->
                HistoryStore(compose.activity).use { if (it.detections().any { summary -> summary.id == id }) it.deleteDetection(id) }
            }
        }
    }

    @Test fun autoLimitRetainsWholeTakeAndUnsavedRestartRequiresConfirmationWhileNavigationPreservesDraft() {
        val audio = FixtureAudio(AudioEngine(compose.activity) {}, 180_000)
        val model = studio(audio)
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value.phase == Phase.IDLE && model.state.value.listeningMs == 180_000L }
        assertEquals(180_000L, audio.limit)
        assertEquals(128L, model.state.value.listeningClip?.notes?.first()?.startMs)
        assertTrue(model.state.value.hasUnsavedDetection)
        val take = model.state.value.listeningTake
        val identity = model.state.value.listeningId
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(identity, model.state.value.listeningId)
        assertEquals(1, audio.captures)
        compose.runOnIdle { model.select(Screen.HISTORY) }
        compose.waitUntil(10_000) { model.state.value.screen == Screen.HISTORY }
        compose.runOnIdle { model.select(Screen.LISTEN) }
        compose.waitUntil(10_000) { model.state.value.screen == Screen.LISTEN }
        assertEquals(take, model.state.value.listeningTake)
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        compose.onNodeWithText("Discard and start").performClick()
        compose.waitUntil(15_000) { audio.captures == 2 && model.state.value.phase == Phase.IDLE }
        assertNotEquals(identity, model.state.value.listeningId)
        assertTrue(model.state.value.hasUnsavedDetection)
    }

    @androidx.compose.runtime.Composable
    private fun TextMessage(message: UiMessage) { androidx.compose.material3.Text(message.localized()) }

    private fun screenshot(name: String) {
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        File(directory, name).outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
