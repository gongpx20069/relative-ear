package io.github.gongpx20069.relativeear

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import io.github.gongpx20069.relativeear.core.DetectionTake
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.NoteEvent
import io.github.gongpx20069.relativeear.core.PitchPoint
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.util.UUID

class DetectionFullscreenTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(TestLocaleRule("en"))

    @Test fun realDetectionPageExpandsWithModesAndBothExitPathsRestoreNavigationAndOrientation() {
        val original = compose.activity.resources.configuration.orientation
        compose.onNodeWithText("Detect").performClick()
        compose.onNodeWithTag("detection-mode-CURVE").performScrollTo().performClick()
        compose.onNodeWithTag("detection-expand").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                compose.onAllNodesWithTag("detection-fullscreen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("History").assertDoesNotExist()
        compose.onNodeWithTag("detection-mode-CURVE").assertIsSelected()
        compose.onNodeWithTag("detection-mode-NOTES").performClick().assertIsSelected()
        compose.onNodeWithTag("melody-timeline").performScrollTo().assertIsDisplayed()
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        File(directory, "detection-fullscreen.png").outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNodeWithTag("detection-expand").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == original &&
                compose.onAllNodesWithTag("detection-fullscreen").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithTag("detection-expand").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == original &&
                compose.onAllNodesWithTag("detection-fullscreen").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithTag("detection-mode-NOTES").performScrollTo().assertIsSelected()
    }

    @Test fun savedClipReplaySurvivesFullscreenOrientationChangesWithoutLosingCurveOrTuning() {
        val model = ViewModelProvider(compose.activity)[EarViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        val original = compose.activity.resources.configuration.orientation
        val id = UUID.randomUUID().toString()
        val notes = listOf(NoteEvent(60, 128, 1800), NoteEvent(64, 4000, 2500),
            NoteEvent(67, 7000, 2000), NoteEvent(73, 11_000, 1000))
        val take = DetectionTake(MelodyClip(notes, 30_000, 415.0),
            (0L..30_000L step 96).map { time ->
                val note = notes.firstOrNull { time in it.startMs until it.startMs + it.durationMs }
                PitchPoint(time, note?.let { it.midi + 0.12 * kotlin.math.sin(time / 100.0) },
                    if (note == null) 0.0 else 0.99)
            })
        HistoryStore(compose.activity).use { it.saveDetection(id, System.currentTimeMillis(), take) }
        try {
            compose.runOnIdle { model.reload() }
            compose.waitUntil(10_000) { model.state.value.detections.any { it.id == id } }
            compose.onNodeWithText("History").performClick()
            compose.onNodeWithTag("history-section-DETECTIONS").performClick()
            compose.onNodeWithTag("detection-$id").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.state.value.savedDetection != null }
            compose.onNodeWithTag("detection-mode-CURVE").performScrollTo().performClick()
            compose.onNodeWithText("Replay detected notes").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.state.value.replayPositionMs >= 200 }
            compose.onNodeWithTag("detection-expand").performScrollTo().performClick()
            compose.waitUntil(15_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    compose.onAllNodesWithTag("detection-fullscreen").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(Phase.REPLAYING, model.state.value.phase)
            assertEquals(take, model.state.value.savedDetection?.take)
            val position = model.state.value.replayPositionMs
            compose.waitUntil(10_000) { model.state.value.replayPositionMs > position + 200 }
            compose.onNodeWithTag("detection-mode-CURVE").assertIsSelected()
            compose.onNodeWithTag("melody-timeline").performScrollTo()
            val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
            File(directory, "detection-fullscreen-replay.png").outputStream().use {
                check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.onNodeWithTag("detection-expand").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == original }
            assertEquals(Phase.REPLAYING, model.state.value.phase)
            compose.onNodeWithText("Stop replay").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.state.value.phase == Phase.IDLE }
            assertEquals(415.0, model.state.value.activeClip!!.a4, 0.0)
        } finally {
            compose.runOnIdle { model.interrupt(); model.expandListening(false) }
            HistoryStore(compose.activity).use { it.deleteDetection(id) }
        }
    }
}
