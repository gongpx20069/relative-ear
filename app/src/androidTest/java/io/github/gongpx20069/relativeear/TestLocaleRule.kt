package io.github.gongpx20069.relativeear

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
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
            setLocales(tags)
            try { base.evaluate() } finally { setLocales(previous) }
        }
    }
}
