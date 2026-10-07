package io.github.gongpx20069.relativeear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gongpx20069.relativeear.core.AppUpdates

@Composable
internal fun UpdateSettings(
    state: UpdateState, onCheck: () -> Unit, onDismiss: () -> Unit, onOpen: (String) -> Boolean,
) {
    val available = state.available
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.update_current, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.update_manual_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.checking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_checking))
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (available != null) {
                Text(stringResource(R.string.update_found, available.versionName),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.update_asset, available.apk.name, available.apk.size / 1048576.0),
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onOpen(available.apk.url) }) { Text(stringResource(R.string.update_download)) }
                TextButton(onClick = { onOpen(available.releaseUrl) }) { Text(stringResource(R.string.update_release_notes)) }
            } else if (state.checked && state.error == null) {
                Text(stringResource(R.string.update_none))
            }
            OutlinedButton(onClick = onCheck, enabled = !state.checking) { Text(stringResource(R.string.update_check)) }
            if (available == null) {
                TextButton(onClick = { onOpen(AppUpdates.RELEASES_URL) }) { Text(stringResource(R.string.update_all_releases)) }
            }
        }
    }
    if (available != null && state.prompt) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_found, available.versionName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.update_download_hint))
                    Text(available.apk.name, style = MaterialTheme.typography.bodySmall)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (onOpen(available.apk.url)) onDismiss() }) {
                    Text(stringResource(R.string.update_confirm_download))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
        )
    }
}
