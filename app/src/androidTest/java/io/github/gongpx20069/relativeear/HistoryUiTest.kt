package io.github.gongpx20069.relativeear

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.gongpx20069.relativeear.core.TrainingSetup
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class HistoryUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(TestLocaleRule()).around(compose)

    companion object {
        @JvmStatic @BeforeClass fun prepareBeforeLaunchingActivity() = seed()
        private fun seed() {
            HistoryStore(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
                store.clear()
                val training = TrainingSetup()
                val time = System.currentTimeMillis() - 60_000
                repeat(10) { index ->
                    val target = training.notes[index % 3]
                    val correct = index < 8
                    store.save("complete", Attempt("fixed_note", 60, target,
                        if (correct) target else training.notes[(index + 1) % 3], correct,
                        reactionMs = 1500, timeMs = time + index * 1000L, training = training), Settings())
                }
                store.save("partial", Attempt("sing_fixed", 60, 64, 64, true, cents = -12.0,
                    training = training), Settings())
                store.save("partial", Attempt("sing_fixed", 60, 60, 60, false, timeout = true,
                    training = training), Settings())
            }
        }
        @JvmStatic @AfterClass fun clearFixtures() {
            HistoryStore(InstrumentationRegistry.getInstrumentation().targetContext).use { it.clear() }
        }
    }
    @After fun prepareForNextLaunch() = seed()

    private fun openHistory() {
        compose.onNodeWithText("记录").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("累计练习次数").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("加载本地数据 / 重试").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun listContainsPracticesAndOnlyDrillDownShowsIndividualNotes() {
        openHistory()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("75%").assertExists()
        compose.onNodeWithText("已答 10 题 · 答对 8 题 · 正确率 80%").assertExists()
        compose.onNodeWithText("第 1 题").assertDoesNotExist()
        screenshot("practice-history.png")
        compose.onNodeWithTag("practice-complete-fixed_note").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("第 1 题").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("第 1 题").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("你的选择：Do (C4)").assertCountEquals(4)
        screenshot("practice-details.png")
        compose.onNodeWithText("第 10 题").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("返回练习记录").performScrollTo().performClick()
        compose.onNodeWithText("累计练习次数").assertIsDisplayed()
        compose.onNodeWithText("第 1 题").assertDoesNotExist()
    }

    @Test fun partialSingingPracticePreservesTimeoutAndSystemBackReturnsToList() {
        openHistory()
        compose.onNodeWithTag("practice-partial-sing_fixed").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("第 1 题").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("部分练习").assertExists()
        compose.onNodeWithText("第 2 题").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("超时").assertExists()
        compose.onNodeWithText("偏差 -12 cents").assertExists()
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("累计练习次数").assertIsDisplayed()
        compose.onNodeWithText("第 1 题").assertDoesNotExist()
    }

    @Test fun switchingTabsClosesDetailsAndClearRemovesAllPracticeRecords() {
        openHistory()
        compose.onNodeWithTag("practice-complete-fixed_note").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("第 1 题").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("设置").performClick()
        openHistory()
        compose.onNodeWithText("第 1 题").assertDoesNotExist()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("删除全部训练记录").performScrollTo().performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        openHistory()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("还没有训练记录。完成听辨或回唱后会自动保存。")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("第 1 题").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        File(directory, name).outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) {
                "Screenshot encoding failed"
            }
        }
    }
}
