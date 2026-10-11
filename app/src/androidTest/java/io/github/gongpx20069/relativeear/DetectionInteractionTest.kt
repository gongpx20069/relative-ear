package io.github.gongpx20069.relativeear

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
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

class DetectionInteractionTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(TestLocaleRule("en"))

    @Test fun realPageKeepsActionsVisibleWhileBrowsingAndHelpStartsCollapsed() {
        val model = ViewModelProvider(compose.activity)[EarViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        compose.onNodeWithText("Detect").performClick()
        val actions = compose.onNodeWithTag("detection-actions").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("detection-capture").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("detection-replay").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("detection-save").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Detection guide and data").assertDoesNotExist()
        compose.onNodeWithTag("detection-help").performScrollTo().performClick()
        compose.onNodeWithText("Detection guide and data").performScrollTo().assertIsDisplayed()
        assertEquals(actions, compose.onNodeWithTag("detection-actions").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("detection-capture").assertIsDisplayed()
        compose.onNodeWithTag("detection-save").assertIsDisplayed()
        compose.onNodeWithTag("detection-help").performScrollTo().performClick()
        compose.onNodeWithText("Detection guide and data").assertDoesNotExist()
        compose.onNodeWithTag("melody-timeline").performScrollTo()
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        File(directory, "detection-persistent-actions.png").outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun actionsRemainReachableAtLargeFontsAndReflectCaptureDraftReplayAndSavedStates() {
        val take = DetectionTake(MelodyClip(listOf(NoteEvent(60, 128, 1000)), 2000, 440.0),
            listOf(PitchPoint(128, 60.0, 0.99)))
        val initial = UiState(screen = Screen.LISTEN, loaded = true)
        val draft = initial.copy(listeningTake = take, listeningId = "interaction", listeningMs = 2000)
        val state = mutableStateOf(initial)
        val scale = mutableStateOf(1f)
        var starts = 0
        var stops = 0
        var replays = 0
        var replayStops = 0
        var saves = 0
        compose.runOnIdle {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                    EarTheme {
                        AppShell(state.value, {}, {}, bottomActions = {
                            ListeningActions(state.value, { starts++ }, { stops++ },
                                { replays++ }, { replayStops++ }, { saves++ })
                        }) {
                            ListeningPage(state.value, {}, {}, {}, {}, showActions = false)
                        }
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { scale.value = fontScale; state.value = initial }
            compose.onNodeWithTag("detection-capture").performClick()
            compose.onNodeWithText("Ready · Start listening, then hum or play a single note.").assertExists()
            compose.runOnIdle { state.value = draft.copy(phase = Phase.LISTENING) }
            compose.onNodeWithText("Listening · Stop to review, replay or save.").assertExists()
            compose.onNodeWithTag("detection-replay").assertIsNotEnabled()
            compose.onNodeWithTag("detection-save").assertIsNotEnabled()
            compose.onNodeWithTag("detection-capture").performClick()
            compose.runOnIdle { state.value = draft }
            compose.onNodeWithText("Not saved · Review or save this clip before starting again.").assertExists()
            compose.onNodeWithTag("detection-save").assertIsEnabled().performClick()
            compose.onNodeWithTag("detection-replay").assertIsEnabled().performClick()
            compose.runOnIdle { state.value = draft.copy(phase = Phase.REPLAYING) }
            compose.onNodeWithTag("detection-capture").assertIsNotEnabled()
            compose.onNodeWithTag("detection-save").assertIsNotEnabled()
            compose.onNodeWithTag("detection-replay").assertIsEnabled().performClick()
            compose.runOnIdle { state.value = draft.copy(listeningSaved = true) }
            compose.onNodeWithText("Saved in History → Detection clips. You can start another clip.").assertExists()
            compose.onNodeWithTag("detection-save").assertIsNotEnabled()
            compose.onNodeWithTag("detection-capture").assertIsEnabled()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            for (tag in listOf("detection-capture", "detection-replay", "detection-save")) {
                compose.onNodeWithTag(tag).assertIsDisplayed()
                val button = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag remains fully inside the viewport at $fontScale",
                    button.left >= root.left && button.top >= root.top &&
                        button.right <= root.right && button.bottom <= root.bottom)
            }
            compose.runOnIdle { state.value = initial.copy(listeningMs = 2000) }
            compose.onNodeWithText("No reliable pitch captured. Try a clear single voice or instrument.").assertExists()
            compose.onNodeWithTag("detection-save").assertIsNotEnabled()
            compose.onNodeWithTag("detection-replay").assertIsNotEnabled()
        }
        assertEquals(3, starts)
        assertEquals(3, stops)
        assertEquals(3, replays)
        assertEquals(3, replayStops)
        assertEquals(3, saves)
    }
}
