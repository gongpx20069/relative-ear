package io.github.gongpx20069.relativeear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.FixedTraining
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.TrainingSetup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryPage(state: UiState, onOpen: (PracticeSummary) -> Unit, onBack: () -> Unit) {
    val selected = state.selectedPractice
    BackHandler(enabled = selected != null, onBack = onBack)
    val format = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
    if (selected != null) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.practice_back)) }
        SectionCard(stringResource(R.string.practice_details)) { PracticeOverview(selected, format) }
        when {
            state.practiceLoading -> Text(stringResource(R.string.practice_loading))
            state.practiceAttempts.isEmpty() -> OutlinedButton(onClick = { onOpen(selected) }) {
                Text(stringResource(R.string.reload))
            }
            else -> state.practiceAttempts.forEachIndexed { index, attempt ->
                SectionCard(stringResource(R.string.practice_question, index + 1)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.practice_target), style = MaterialTheme.typography.labelMedium)
                            Text(historyNoteName(attempt.target, attempt.training?.notation),
                                style = MaterialTheme.typography.titleLarge)
                        }
                        Text(stringResource(when {
                            attempt.timeout -> R.string.history_timeout
                            attempt.correct -> R.string.correct
                            else -> R.string.history_wrong
                        }), color = if (attempt.correct) Pine else MaterialTheme.colorScheme.error)
                    }
                    if (attempt.mode == "fixed_note") {
                        Text(stringResource(R.string.practice_answer,
                            historyNoteName(attempt.answer, attempt.training?.notation)))
                    } else if (attempt.mode != "sing_fixed") {
                        Text(stringResource(R.string.target_readout, Music.name(attempt.root), Music.name(attempt.target)))
                    }
                    Text(stringResource(R.string.practice_timing, attempt.reactionMs / 1000.0, attempt.replays),
                        style = MaterialTheme.typography.bodySmall)
                    if (attempt.training != selected.training) attempt.training?.let { TrainingSnapshot(it) }
                    attempt.cents?.let { Text(stringResource(R.string.history_cents, it)) }
                }
            }
        }
        return
    }
    val history = state.history
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(R.string.total_practices to history.practiceCount.toString(),
            R.string.accuracy to "${history.accuracy}%").forEach { (label, value) ->
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(value, style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Text(stringResource(R.string.history_scope_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (history.practices.isEmpty()) SectionCard(stringResource(R.string.recent_practice)) {
        Text(stringResource(R.string.empty_history))
    }
    history.practices.forEach { practice ->
        Card(onClick = { onOpen(practice) },
            modifier = Modifier.fillMaxWidth().testTag("practice-${practice.session}-${practice.mode}"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PracticeOverview(practice, format)
                Text(stringResource(R.string.practice_open), color = Pine, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PracticeOverview(practice: PracticeSummary, format: DateTimeFormatter) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(when (practice.mode) {
            "fixed_note" -> R.string.ear_tab
            "sing_fixed" -> R.string.sing_tab
            "sing_degree", "degree" -> R.string.legacy_solfege
            "sing" -> R.string.legacy_singing
            else -> R.string.legacy_interval
        }), style = MaterialTheme.typography.titleLarge)
        if (practice.mode in listOf("fixed_note", "sing_fixed")) {
            Text(stringResource(if (practice.total >= 10) R.string.practice_complete else R.string.practice_partial),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
    Text(format.format(Instant.ofEpochMilli(practice.timeMs)), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.practice_result, practice.total, practice.correct, practice.accuracy))
    practice.training?.let { TrainingSnapshot(it) }
}

@Composable
private fun TrainingSnapshot(setup: TrainingSetup) {
    Text(stringResource(R.string.history_training, setup.notes.joinToString("/") { Music.name(it) },
        setup.bpm, stringResource(if (setup.notation == AnswerNotation.SOLFEGE)
            R.string.solfege_option else R.string.note_name_option)), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun historyNoteName(note: Int, notation: AnswerNotation?): String {
    val names = stringArrayResource(R.array.fixed_solfege_names)
    val degree = FixedTraining.degree(note)
    if (notation == null || degree == null) return Music.name(note)
    return if (notation == AnswerNotation.SOLFEGE) "${names[degree]} (${Music.name(note)})"
        else "${Music.name(note)} (${names[degree]})"
}
