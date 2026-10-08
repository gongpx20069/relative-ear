package io.github.gongpx20069.relativeear

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PianoFullscreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun landscapeFullscreenShowsAllEightKeysAndBothExitPathsRestoreNavigationAndOrientation() {
        val original = compose.activity.resources.configuration.orientation
        val originalVoice = HistoryStore(compose.activity.applicationContext).use { it.pianoVoice() }
        compose.onNodeWithText("钢琴").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithText("展开全屏").fetchSemanticsNode()
            .config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
        compose.onNodeWithText("展开全屏").performClick()
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                compose.onAllNodesWithTag("piano-fullscreen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("记录").assertDoesNotExist()
        for (note in listOf(60, 62, 64, 65, 67, 69, 71, 72)) {
            compose.onNodeWithTag("piano-$note").assertIsDisplayed()
        }
        for (voice in listOf("PIANO", "PURE", "FLUTE")) {
            compose.onNodeWithTag("voice-$voice").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) {
                compose.onNodeWithTag("voice-$voice").fetchSemanticsNode().config[SemanticsProperties.Selected]
            }
        }
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        File(directory, "piano-fullscreen.png").outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNodeWithText("退出全屏").performClick()
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == original &&
                compose.onAllNodesWithTag("piano-fullscreen").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("记录").assertIsDisplayed()
        compose.onNodeWithTag("voice-FLUTE").assertIsSelected()
        compose.onNodeWithText("展开全屏").performClick()
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == original &&
                compose.onAllNodesWithTag("piano-fullscreen").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("钢琴").assertIsDisplayed()
        compose.onNodeWithTag("voice-FLUTE").assertIsSelected()
        compose.onNodeWithTag("voice-${originalVoice.name}").performClick()
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("voice-${originalVoice.name}").fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        compose.onNodeWithText("识音").performClick()
        compose.onNodeWithTag("piano-60").assertDoesNotExist()
    }
}
