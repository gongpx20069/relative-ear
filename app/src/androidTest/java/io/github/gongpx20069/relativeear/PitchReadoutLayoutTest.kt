package io.github.gongpx20069.relativeear

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import io.github.gongpx20069.relativeear.core.PitchFrame
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class PitchReadoutLayoutTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(TestLocaleRule("en"))

    @Test fun pitchLossLowConfidenceAndRecoveryKeepPanelAndFollowingContentInPlaceAtDifferentFontScales() {
        val state = mutableStateOf(UiState(screen = Screen.LISTEN, loaded = true))
        val fontScale = mutableStateOf(1f)
        compose.runOnIdle {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                    EarTheme {
                        Column {
                            PitchReadout(state.value, includeCurve = false)
                            Text("Following content", Modifier.testTag("below-pitch"))
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { fontScale.value = scale; state.value = state.value.copy(frame = null) }
            val empty = compose.onNodeWithTag("pitch-readout-body").fetchSemanticsNode().boundsInRoot
            val following = compose.onNodeWithTag("below-pitch").fetchSemanticsNode().boundsInRoot
            for (frame in listOf(PitchFrame(128, 261.63, 0.99, 0.1), PitchFrame(160, 523.25, 0.99, 0.1),
                PitchFrame(192, 440.0, 0.4, 0.1), PitchFrame(224, null, 0.0, 0.0),
                PitchFrame(256, 440.0, 0.99, 0.1))) {
                compose.runOnIdle { state.value = state.value.copy(frame = frame) }
                assertEquals(empty, compose.onNodeWithTag("pitch-readout-body").fetchSemanticsNode().boundsInRoot)
                assertEquals(following, compose.onNodeWithTag("below-pitch").fetchSemanticsNode().boundsInRoot)
                if (frame.frequency == null || frame.confidence < 0.85) {
                    compose.onNodeWithText("No stable single note detected").assertExists()
                    compose.onNodeWithText("A4").assertDoesNotExist()
                }
            }
        }
    }
}
