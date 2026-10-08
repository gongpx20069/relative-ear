package io.github.gongpx20069.relativeear

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.TrainingSetup
import org.junit.After
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class TrainingUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(TestLocaleRule()).around(compose)

    companion object {
        @JvmStatic @BeforeClass fun resetBeforeLaunchingActivity() = resetData()
        private fun resetData() {
            HistoryStore(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
                store.clear()
                store.saveTraining(TrainingSetup())
                store.saveSettings(Settings())
            }
        }
    }
    @After fun resetAfterEachTest() = resetData()

    @Test fun beginnerHomeHasAnAccessiblePrimaryActionAndNoAnswerPitchLeak() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("开始练习") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("开始练习").assertIsEnabled().assertIsDisplayed()
        compose.onNodeWithText("八键小钢琴").assertDoesNotExist()
        compose.onNodeWithContentDescription("装饰性八分音符，答题前不显示真实音高").assertExists()
        screenshot("studio-home.png")
    }

    @Test fun eightNoteAssessmentUsesSelectedPitchNamesAndAllowsHearingTheScoredAnswer() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("开始练习") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("调整").performClick()
        compose.onNodeWithText("八音全练").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("八音全练") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("音名 C4 / D4").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("音名 C4 / D4") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("收起").performScrollTo().performClick()
        compose.onNodeWithText("开始练习").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("你听到的是").fetchSemanticsNodes().isNotEmpty()
        }
        for (note in listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")) {
            compose.onNode(hasClickAction() and hasText(note)).assertExists()
        }
        compose.onNodeWithContentDescription("装饰性八分音符，答题前不显示真实音高").assertExists()
        compose.onNode(hasClickAction() and hasText("C5")).performScrollTo()
        screenshot("eight-note-question.png")
        compose.onNode(hasClickAction() and hasText("C5")).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("听正确答案").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("听正确答案").assertIsEnabled()
        HistoryStore(compose.activity.applicationContext).use { store ->
            val attempt = store.attempts(store.history().practices.single()).single()
            assertEquals(72, attempt.answer)
            assertEquals(AnswerNotation.NOTE_NAME, attempt.training?.notation)
            assertEquals(8, attempt.training?.notes?.size)
        }
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Screenshot encoding failed" }
        }
    }
}
