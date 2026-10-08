package io.github.gongpx20069.relativeear

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration

data class UiMessage(@StringRes val resource: Int, val arguments: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(resource,
        *arguments.map { if (it is UiMessage) it.resolve(context) else it }.toTypedArray())

    @Composable
    fun localized(): String {
        LocalConfiguration.current
        return resolve(LocalContext.current)
    }
}
