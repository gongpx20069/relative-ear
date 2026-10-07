package io.github.gongpx20069.relativeear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.QuestionMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val model: EarViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF286A58))) {
                EarApp(model)
            }
        }
    }
    override fun onStop() {
        model.interrupt()
        super.onStop()
    }
}

@Composable
private fun EarApp(model: EarViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingScreen by remember { mutableStateOf<Screen?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingScreen
        pendingScreen = null
        if (requested == model.state.value.screen) {
            if (!granted) model.permissionDenied()
            else if (requested == Screen.LISTEN) model.listen() else model.startQuestion()
        }
    }
    fun microphoneAction() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (state.screen == Screen.LISTEN) model.listen() else model.startQuestion()
        } else {
            pendingScreen = state.screen
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val labels = listOf(R.string.sing_tab, R.string.ear_tab, R.string.listen_tab, R.string.report_tab, R.string.settings_tab)
    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEachIndexed { index, screen ->
                    val label = stringResource(labels[index])
                    NavigationBarItem(
                        selected = state.screen == screen,
                        onClick = { if (state.screen != screen) model.select(screen) },
                        icon = { Text(label.take(1)) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
            state.message?.let { message ->
                Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(16.dp)) }
            }
            if (!state.loaded) OutlinedButton(onClick = model::reload) { Text(stringResource(R.string.reload)) }
            when (state.screen) {
                Screen.SING, Screen.EAR -> TrainingPage(state, model, ::microphoneAction)
                Screen.LISTEN -> ListeningPage(state, model, ::microphoneAction)
                Screen.HISTORY -> HistoryPage(state)
                Screen.SETTINGS -> SettingsPage(state, model)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.test_status), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrainingPage(state: UiState, model: EarViewModel, microphoneAction: () -> Unit) {
    val singing = state.screen == Screen.SING
    Text(stringResource(if (singing) R.string.sing_intro else R.string.ear_intro))
    val configEnabled = state.phase == Phase.IDLE || state.phase == Phase.COMPLETE
    if (!singing) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.mode == QuestionMode.INTERVAL,
                onClick = { model.configure(QuestionMode.INTERVAL) }, enabled = configEnabled,
                label = { Text(stringResource(R.string.interval_mode)) })
            FilterChip(selected = state.mode == QuestionMode.DEGREE,
                onClick = { model.configure(QuestionMode.DEGREE) }, enabled = configEnabled,
                label = { Text(stringResource(R.string.degree_mode)) })
        }
    }
    if (singing || state.mode == QuestionMode.INTERVAL) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.descending, onClick = { model.configure(descending = false) },
                enabled = configEnabled, label = { Text(stringResource(R.string.up_option)) })
            FilterChip(selected = state.descending, onClick = { model.configure(descending = true) },
                enabled = configEnabled, label = { Text(stringResource(R.string.down_option)) })
        }
    }
    val intervalNames = stringArrayResource(R.array.interval_names)
    val question = state.question
    if (question != null) {
        Text(stringResource(R.string.question_count, state.count))
        if (singing) {
            val direction = stringResource(if (question.target < question.root) R.string.direction_down else R.string.direction_up)
            Text(stringResource(R.string.target_readout, Music.name(question.root),
                "$direction${intervalNames[abs(question.target - question.root)]}"),
                style = MaterialTheme.typography.titleLarge)
        }
    }
    Text(stringResource(when (state.phase) {
        Phase.PLAYING -> R.string.playing
        Phase.LISTENING -> R.string.sing_now
        Phase.ANSWERING -> R.string.answer_now
        Phase.SAVING -> R.string.saving
        else -> R.string.ready
    }))
    if (singing && state.phase == Phase.LISTENING) PitchReadout(state)
    when (state.phase) {
        Phase.IDLE, Phase.COMPLETE -> Button(
            onClick = if (singing) microphoneAction else model::startQuestion,
            enabled = state.loaded,
        ) { Text(stringResource(if (singing) R.string.start_singing else R.string.start_round)) }
        Phase.FEEDBACK -> Button(onClick = if (singing) microphoneAction else model::startQuestion) {
            Text(stringResource(R.string.next_question))
        }
        Phase.ANSWERING -> {
            val answers = if (state.mode == QuestionMode.DEGREE) (0..6).toList() else (0..12).toList()
            answers.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { answer ->
                        OutlinedButton(onClick = { model.answer(answer) }, modifier = Modifier.weight(1f)) {
                            Text(if (state.mode == QuestionMode.DEGREE) "${answer + 1}" else intervalNames[answer])
                        }
                    }
                }
            }
            OutlinedButton(onClick = model::replay) { Text(stringResource(R.string.replay)) }
        }
        Phase.SAVING -> Unit
        else -> OutlinedButton(onClick = model::interrupt) { Text(stringResource(R.string.stop)) }
    }
    if (state.answered > 0) Text(stringResource(R.string.round_result, state.correct, state.answered))
}

@Composable
private fun PitchReadout(state: UiState) {
    val frame = state.frame
    val frequency = frame?.frequency
    if (frequency == null || frame.confidence < 0.85) {
        Text(stringResource(R.string.no_pitch), style = MaterialTheme.typography.titleMedium)
    } else {
        val midi = Music.nearest(frequency, state.settings.a4.toDouble())
        val cents = Music.error(Music.midi(frequency, state.settings.a4.toDouble()), midi, false)
        Text(stringResource(R.string.pitch_readout, Music.name(midi), frequency, cents),
            style = MaterialTheme.typography.titleLarge)
    }
    val description = stringResource(R.string.chart_description)
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        for (line in 0..4) {
            val y = size.height * line / 4
            drawLine(Color.LightGray, Offset(0f, y), Offset(size.width, y))
        }
        val newest = state.curve.lastOrNull()?.timeMs ?: return@Canvas
        var previous: Offset? = null
        for (entry in state.curve) {
            val frequencyValue = entry.frequency
            if (frequencyValue == null || entry.confidence < 0.85) { previous = null; continue }
            val midi = Music.midi(frequencyValue, state.settings.a4.toDouble())
            val point = Offset(
                (1 - (newest - entry.timeMs) / 10_000f) * size.width,
                (1 - ((midi - 36) / 48).toFloat().coerceIn(0f, 1f)) * size.height,
            )
            previous?.let { drawLine(color, it, point, strokeWidth = 3.dp.toPx()) }
            previous = point
        }
    }
}

@Composable
private fun ListeningPage(state: UiState, model: EarViewModel, microphoneAction: () -> Unit) {
    Text(stringResource(R.string.listen_intro))
    PitchReadout(state)
    Button(onClick = if (state.phase == Phase.LISTENING) model::stopListening else microphoneAction,
        enabled = state.loaded) {
        Text(stringResource(if (state.phase == Phase.LISTENING) R.string.stop else R.string.start_listening))
    }
    Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.titleMedium)
    if (state.notes.isEmpty()) Text(stringResource(R.string.no_notes))
    state.notes.asReversed().forEach {
        Text(stringResource(R.string.note_event, Music.name(it.midi), it.startMs / 1000.0, it.durationMs / 1000.0))
    }
}

@Composable
private fun HistoryPage(state: UiState) {
    val history = state.history
    Text(stringResource(R.string.history_summary, history.total, history.correct))
    if (history.attempts.isEmpty()) Text(stringResource(R.string.empty_history))
    val format = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
    history.attempts.forEach { attempt ->
        val mode = stringResource(when (attempt.mode) {
            "sing" -> R.string.sing_tab
            "degree" -> R.string.degree_mode
            else -> R.string.interval_mode
        })
        val result = stringResource(when {
            attempt.timeout -> R.string.history_timeout
            attempt.correct -> R.string.correct
            else -> R.string.history_wrong
        })
        Text(stringResource(R.string.history_row, format.format(Instant.ofEpochMilli(attempt.timeMs)), mode, result))
        attempt.cents?.let { Text(stringResource(R.string.history_cents, it)) }
        HorizontalDivider()
    }
}

@Composable
private fun SettingsPage(state: UiState, model: EarViewModel) {
    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
    var a4 by remember { mutableFloatStateOf(state.settings.a4.toFloat()) }
    LaunchedEffect(state.settings.a4) { a4 = state.settings.a4.toFloat() }
    Text(stringResource(R.string.a4_label, a4.toInt()))
    Slider(value = a4, onValueChange = { a4 = it }, valueRange = 415f..466f, steps = 50,
        onValueChangeFinished = { model.settings(state.settings.copy(a4 = a4.toInt())) })
    Text(stringResource(R.string.tolerance_label, state.settings.tolerance))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(20, 35, 50).forEach { tolerance ->
            FilterChip(selected = state.settings.tolerance == tolerance,
                onClick = { model.settings(state.settings.copy(tolerance = tolerance)) },
                label = { Text("±$tolerance") })
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.octave_label), Modifier.weight(1f))
        Switch(checked = state.settings.ignoreOctave,
            onCheckedChange = { model.settings(state.settings.copy(ignoreOctave = it)) })
    }
    Text(stringResource(R.string.range_hint))
    Text(stringResource(R.string.privacy))
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.clear_data)) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text(stringResource(R.string.clear_confirm)) },
        confirmButton = {
            TextButton(onClick = { confirm = false; model.clearHistory() }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
