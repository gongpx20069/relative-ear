package io.github.gongpx20069.relativeear

import android.content.res.Configuration
import android.content.res.Resources
import android.app.LocaleManager
import android.os.Build
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.util.Locale

class LanguageSettingsTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(TestLocaleRule(""))

    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun settings() {
        val model = ViewModelProvider(compose.activity)[EarViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        compose.onNodeWithText(compose.activity.getString(R.string.settings_tab)).performClick()
        compose.onNodeWithTag("language-SYSTEM").performScrollTo()
    }

    @Test fun switchingLanguagesPersistsAcrossRecreationAndReturningToSystemPreservesLocalDataAndMessages() {
        val context = compose.activity.applicationContext
        val before = HistoryStore(context).use { Triple(it.settings(), it.training(), it.history()) }
        settings()
        compose.onNodeWithTag("language-SYSTEM").assertIsSelected()
        val model = ViewModelProvider(compose.activity)[EarViewModel::class.java]
        compose.runOnIdle { model.permissionDenied(); model.updateOpenFailed() }
        compose.onNodeWithTag("language-CHINESE").performScrollTo().performClick()
        waitFor("跟随系统")
        compose.onNodeWithTag("language-CHINESE").assertIsSelected()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.permission_denied)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.update_open_error)).assertExists()
        assertEquals("zh", compose.activity.resources.configuration.locales[0].language)
        compose.activityRule.scenario.recreate()
        waitFor("跟随系统")
        compose.onNodeWithTag("language-CHINESE").assertIsSelected()
        assertEquals(AppLanguage.CHINESE, AppLanguage.current(compose.activity))
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        waitFor("Follow system")
        compose.onNodeWithTag("language-ENGLISH").assertIsSelected()
        assertEquals("en", compose.activity.resources.configuration.locales[0].language)
        compose.onNodeWithText(compose.activity.getString(R.string.permission_denied)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.update_open_error)).assertExists()
        val directory = requireNotNull(compose.activity.getExternalFilesDir("ui-snapshots"))
        File(directory, "language-settings-en.png").outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNodeWithTag("language-CHINESE").performScrollTo().performClick()
        waitFor("跟随系统")
        compose.onNodeWithTag("language-SYSTEM").performScrollTo().performClick()
        val systemLanguage = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales[0].language
        } else Resources.getSystem().configuration.locales[0].language
        waitFor(if (systemLanguage == "zh") "跟随系统" else "Follow system")
        compose.onNodeWithTag("language-SYSTEM").assertIsSelected()
        assertEquals(AppLanguage.SYSTEM, AppLanguage.current(compose.activity))
        assertEquals(if (systemLanguage == "zh") "zh" else "en",
            compose.activity.resources.configuration.locales[0].language)
        assertEquals(before, HistoryStore(context).use { Triple(it.settings(), it.training(), it.history()) })
    }

    @Test fun englishPagesAndFullscreenUseTranslatedControlsAndNestedErrorsUseTheCurrentLocale() {
        settings()
        compose.onNodeWithTag("language-ENGLISH").performScrollTo().performClick()
        waitFor("Follow system")
        for ((tab, heading) in listOf("Train" to "Turn sounds into notes", "Sing" to "Bring notes to life",
            "Detect" to "Hear the melody around you", "History" to "Every practice counts",
            "Piano" to "Learn sounds with the keys")) {
            compose.onNodeWithText(tab).performClick()
            compose.onNodeWithText(heading).assertIsDisplayed()
        }
        compose.onNodeWithText("Expand fullscreen").performScrollTo().performClick()
        waitFor("Exit fullscreen")
        compose.onNodeWithText("Flute voice").assertIsDisplayed()
        compose.onNodeWithText("Exit fullscreen").performClick()
        waitFor("Expand fullscreen")
        val message = UiMessage(R.string.audio_error, listOf(UiMessage(R.string.audio_play_write, listOf(42))))
        fun resolve(language: String): String {
            val configuration = Configuration(compose.activity.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language))
            return message.resolve(compose.activity.createConfigurationContext(configuration))
        }
        assertTrue(resolve("en").contains("Playback failed, error code 42"))
        assertTrue(resolve("zh").contains("播放失败，错误码 42"))
        val french = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.FRENCH) }
        assertEquals("Settings", compose.activity.createConfigurationContext(french).getString(R.string.settings_tab))
    }
}
