package io.github.gongpx20069.relativeear

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.content.res.Configuration
import android.content.res.Resources
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class TestLocaleRule(private val tags: String = "zh-Hans") : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val previous = if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            } else AppCompatDelegate.getApplicationLocales().toLanguageTags()
            fun setLocales(value: String) = instrumentation.runOnMainSync {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(value)
                } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value))
            }
            fun awaitLocale(value: String) {
                val system = if (Build.VERSION.SDK_INT >= 33) {
                    context.getSystemService(LocaleManager::class.java).systemLocales
                } else Resources.getSystem().configuration.locales
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocales(if (value.isEmpty()) system else LocaleList.forLanguageTags(value))
                }
                val expected = context.createConfigurationContext(configuration).getString(R.string.settings_tab)
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (true) {
                    var ready = false
                    instrumentation.runOnMainSync {
                        ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .any { it is MainActivity && it.getString(R.string.settings_tab) == expected }
                    }
                    if (ready) break
                    check(SystemClock.elapsedRealtime() < deadline) { "Activity did not apply locale $value" }
                    Thread.sleep(25)
                }
                instrumentation.waitForIdleSync()
            }
            setLocales(tags)
            awaitLocale(tags)
            try { base.evaluate() } finally { setLocales(previous); awaitLocale(previous) }
        }
    }
}
