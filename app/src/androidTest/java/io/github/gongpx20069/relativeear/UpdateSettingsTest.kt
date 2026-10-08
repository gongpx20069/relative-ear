package io.github.gongpx20069.relativeear

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.gongpx20069.relativeear.core.AppUpdates
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class UpdateSettingsTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(TestLocaleRule()).around(compose)

    @Test fun manualCheckFindsPreviewPromptsAndOpensTheDeviceApkWithoutAutoRequests() {
        val calls = AtomicInteger()
        val opened = AtomicReference<String>()
        val newCode = BuildConfig.VERSION_CODE + 1
        val model = EarViewModel(compose.activity.application, ReleaseUpdateClient {
            calls.incrementAndGet()
            JSONArray(listOf(releaseFixture(newCode))).toString()
        })
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val state by model.state.collectAsState()
                EarTheme { UpdateSettings(state.update, model::checkForUpdates, model::dismissUpdatePrompt) {
                    opened.set(it); true
                } }
            }
        }
        compose.onNodeWithText("当前版本：${BuildConfig.VERSION_NAME}").assertExists()
        assertEquals(0, calls.get())
        compose.onNodeWithText("检查更新").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("前往下载").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("前往下载").performClick()
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in AppUpdates.architectures } ?: "universal"
        assertEquals("https://github.com/${AppUpdates.REPOSITORY}/releases/download/v0.0.$newCode/relative-ear-0.0.$newCode-$abi.apk",
            opened.get())
        assertEquals(1, calls.get())
        assertFalse(model.state.value.update.prompt)
        compose.onNodeWithText("下载 APK").assertExists()
    }

    @Test fun offlineFailureIsExplicitAndRetryCanReportNoNewVersion() {
        val calls = AtomicInteger()
        val model = EarViewModel(compose.activity.application, ReleaseUpdateClient {
            if (calls.getAndIncrement() == 0) throw IOException("offline test")
            JSONArray(listOf(releaseFixture(BuildConfig.VERSION_CODE))).toString()
        })
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val state by model.state.collectAsState()
                EarTheme { UpdateSettings(state.update, model::checkForUpdates, model::dismissUpdatePrompt) { true } }
            }
        }
        compose.onNodeWithText("检查更新").performClick()
        compose.waitUntil(10_000) { model.state.value.update.error != null }
        compose.onNodeWithText("无法检查更新，请确认网络连接后重试。离线练习不受影响。").assertExists()
        compose.onNode(hasText("检查更新") and isEnabled()).performClick()
        compose.waitUntil(10_000) { model.state.value.update.checked }
        compose.onNodeWithText("暂无更新版本。").assertExists()
        assertEquals(2, calls.get())
    }
}
