package io.github.gongpx20069.relativeear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gongpx20069.relativeear.core.Music
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryStudio(state: UiState, model: EarViewModel) {
    if (state.selectedPractice == null && state.selectedDetection == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistorySection.entries.forEach { section ->
                FilterChip(selected = state.historySection == section, enabled = state.phase == Phase.IDLE,
                    onClick = { model.historySection(section) }, modifier = Modifier.testTag("history-section-${section.name}"),
                    label = { Text(stringResource(if (section == HistorySection.PRACTICES)
                        R.string.history_practices else R.string.history_detections)) })
            }
        }
    }
    if (state.historySection == HistorySection.PRACTICES) {
        HistoryPage(state, model::openPractice, model::closePractice)
    } else {
        DetectionHistoryPage(state, model::openDetection, model::closeDetection, model::renameDetection,
            model::deleteDetection, model::replayDetected, model::stopReplay, model::detectionMode,
            model::detectionNotation) { model.expandListening(true) }
    }
}

@Composable
internal fun DetectionHistoryPage(state: UiState, onOpen: (DetectionSummary) -> Unit, onBack: () -> Unit,
    onRename: (String) -> Unit, onDelete: () -> Unit, onReplay: () -> Unit, onStopReplay: () -> Unit,
    onMode: (DetectionMode) -> Unit, onNotation: (io.github.gongpx20069.relativeear.core.AnswerNotation) -> Unit,
    onExpand: () -> Unit) {
    val selected = state.selectedDetection
    BackHandler(enabled = selected != null) { if (state.phase != Phase.SAVING) onBack() }
    val format = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
    if (selected == null) {
        Text(stringResource(R.string.detection_history_hint), style = MaterialTheme.typography.bodySmall)
        if (state.detections.isEmpty()) SectionCard(stringResource(R.string.history_detections)) {
            Text(stringResource(R.string.detection_history_empty))
        }
        state.detections.forEach { summary ->
            Card(onClick = { onOpen(summary) }, modifier = Modifier.fillMaxWidth().testTag("detection-${summary.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetectionOverview(summary, format)
                    val preview = summary.preview
                    if (preview.isNotEmpty()) Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                        val low = (summary.lowest ?: 60) - 2
                        val span = ((summary.highest ?: 72) + 2 - low).coerceAtLeast(8)
                        preview.forEach {
                            val x = size.width * it.startMs / summary.durationMs
                            val y = size.height * (1 - (it.midi - low).toFloat() / span)
                            val width = (size.width * it.durationMs / summary.durationMs).coerceAtLeast(2.dp.toPx())
                            drawRoundRect(Mint, Offset(x, y - 2.dp.toPx()), Size(width, 4.dp.toPx()))
                        }
                    }
                    Text(stringResource(R.string.detection_open), color = Pine, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }
    TextButton(onClick = onBack, enabled = state.phase != Phase.SAVING) {
        Text(stringResource(R.string.detection_back))
    }
    SectionCard(stringResource(R.string.history_detections)) { DetectionOverview(selected, format) }
    when {
        state.detectionLoading -> Text(stringResource(R.string.practice_loading))
        state.savedDetection == null -> OutlinedButton(onClick = { onOpen(selected) }) { Text(stringResource(R.string.reload)) }
        else -> {
            val take = state.savedDetection.take
            DetectionCanvas(take, take.clip, state, selected.id, onMode, onNotation, onExpand)
            ReplayControls(state, onReplay, onStopReplay)
            DetectionNoteDetails(take.clip, state.detectionNotation)
        }
    }
    var rename by rememberSaveable(selected.id) { mutableStateOf(false) }
    var delete by rememberSaveable(selected.id) { mutableStateOf(false) }
    var title by rememberSaveable(selected.id) { mutableStateOf(selected.title) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { title = selected.title; rename = true }, enabled = state.phase == Phase.IDLE && !state.detectionLoading) {
            Text(stringResource(R.string.detection_rename))
        }
        TextButton(onClick = { delete = true }, enabled = state.phase == Phase.IDLE && !state.detectionLoading) { Text(stringResource(R.string.delete)) }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false },
        title = { Text(stringResource(R.string.detection_rename)) },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text(stringResource(R.string.detection_name)) },
                isError = title.trim().isEmpty() || title.trim().length > 80,
                supportingText = { Text(stringResource(R.string.detection_name_error)) },
                modifier = Modifier.testTag("detection-name-input"))
        },
        confirmButton = { TextButton(onClick = { rename = false; onRename(title) },
            enabled = title.trim().isNotEmpty() && title.trim().length <= 80) {
            Text(stringResource(R.string.detection_confirm_name))
        } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel)) } })
    if (delete) AlertDialog(onDismissRequest = { delete = false },
        text = { Text(stringResource(R.string.detection_delete_warning)) },
        confirmButton = { TextButton(onClick = { delete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun DetectionOverview(summary: DetectionSummary, format: DateTimeFormatter) {
    Text(summary.title.ifEmpty { stringResource(R.string.detection_default_title) },
        style = MaterialTheme.typography.titleLarge)
    Text(format.format(Instant.ofEpochMilli(summary.timeMs)), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.detection_summary, summary.durationMs / 1000.0, summary.noteCount))
    if (summary.lowest != null && summary.highest != null) {
        Text(stringResource(R.string.detection_range, Music.name(summary.lowest), Music.name(summary.highest), summary.a4),
            style = MaterialTheme.typography.bodySmall)
    } else Text(stringResource(R.string.detection_tuning, summary.a4), style = MaterialTheme.typography.bodySmall)
}
