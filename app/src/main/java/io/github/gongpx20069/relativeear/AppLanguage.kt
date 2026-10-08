package io.github.gongpx20069.relativeear

import android.content.Context
import android.app.LocaleManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect

enum class AppLanguage(val tag: String, val label: Int) {
    SYSTEM("", R.string.language_system),
    CHINESE("zh-Hans", R.string.language_chinese),
    ENGLISH("en", R.string.language_english);

    companion object {
        fun current(context: Context): AppLanguage {
            val locales = if (Build.VERSION.SDK_INT >= 33) {
                LocaleListCompat.wrap(context.getSystemService(LocaleManager::class.java).applicationLocales)
            } else AppCompatDelegate.getApplicationLocales()
            return when (locales[0]?.language) {
                null -> SYSTEM
                "zh" -> CHINESE
                else -> ENGLISH
            }
        }
    }
    fun apply() = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

@Composable
internal fun LanguageSettings() {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    LifecycleResumeEffect(context) {
        language = AppLanguage.current(context)
        onPauseOrDispose {}
    }
    SectionCard(stringResource(R.string.language_title)) {
        for (option in AppLanguage.entries) {
            Row(Modifier.fillMaxWidth().testTag("language-${option.name}")
                .selectable(selected = language == option, role = Role.RadioButton,
                    onClick = { language = option; option.apply() }),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = language == option, onClick = null)
                Text(stringResource(option.label))
            }
        }
        Text(stringResource(R.string.language_hint))
    }
}
