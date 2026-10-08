package io.github.gongpx20069.relativeear

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.FixedTraining
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.NoteQuestion
import io.github.gongpx20069.relativeear.core.TrainingSetup

class MainActivity : ComponentActivity() {
    private val model: EarViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { EarTheme { EarApp(model) } }
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
    AppShell(state, model::select, model::reload) {
        when (state.screen) {
            Screen.SING, Screen.EAR -> TrainingPage(state, model::configure,
                if (state.screen == Screen.SING) ::microphoneAction else model::startQuestion,
                model::replay, model::playReference, model::previewNote, model::answer, model::interrupt)
            Screen.LISTEN -> ListeningPage(state, ::microphoneAction, model::stopListening,
                model::replayDetected, model::stopReplay, model::previewNote)
            Screen.HISTORY -> HistoryPage(state, model::openPractice, model::closePractice)
            Screen.SETTINGS -> SettingsPage(state, model)
        }
    }
}

@Composable
private fun AppShell(state: UiState, onSelect: (Screen) -> Unit, onReload: () -> Unit, content: @Composable () -> Unit) {
    val labels = listOf(R.string.ear_tab, R.string.sing_tab, R.string.listen_tab, R.string.report_tab, R.string.settings_tab)
    val titles = listOf(R.string.ear_title, R.string.sing_title, R.string.listen_title, R.string.history_title, R.string.settings_title)
    Scaffold(bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Screen.entries.forEachIndexed { index, screen ->
                NavigationBarItem(selected = state.screen == screen,
                    enabled = state.phase != Phase.SAVING,
                    onClick = { if (state.screen != screen) onSelect(screen) },
                    icon = { NavigationArtwork(screen) }, label = { Text(stringResource(labels[index])) })
            }
        }
    }) { padding ->
        val scroll = key(state.screen, state.selectedPractice?.session, state.selectedPractice?.mode) {
            rememberScrollState()
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(Pine, RoundedCornerShape(4.dp)))
                Text("RELATIVE EAR", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
            }
            Text(stringResource(titles[state.screen.ordinal]), style = MaterialTheme.typography.headlineLarge)
            state.message?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!state.loaded) OutlinedButton(onClick = onReload) { Text(stringResource(R.string.reload)) }
            content()
            Text(stringResource(R.string.test_status), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun NoteLabel(note: Int, notation: AnswerNotation, large: Boolean = false) {
    val names = stringArrayResource(R.array.fixed_solfege_names)
    val degree = checkNotNull(FixedTraining.degree(note))
    val primary = if (notation == AnswerNotation.SOLFEGE) names[degree] else Music.name(note)
    val secondary = if (notation == AnswerNotation.SOLFEGE) Music.name(note) else names[degree]
    Text(primary, fontSize = if (large) 28.sp else 21.sp, fontWeight = FontWeight.SemiBold)
    Text(secondary, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun TrainingPage(
    state: UiState, onConfigure: (TrainingSetup) -> Unit, onStart: () -> Unit, onReplay: () -> Unit,
    onReference: (Boolean) -> Unit, onPreview: (Int) -> Unit, onAnswer: (Int) -> Unit, onStop: () -> Unit,
) {
    val singing = state.screen == Screen.SING
    TrainingConfiguration(state, onConfigure)
    val shownNote = state.demoNote ?: state.question?.target?.takeIf {
        singing || state.phase in listOf(Phase.FEEDBACK, Phase.COMPLETE)
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Pine)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.fixed_anchor), color = Mint, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.rhythm_badge, state.training.bpm), color = Mint,
                    style = MaterialTheme.typography.labelMedium)
            }
            NoteStaff(shownNote)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (shownNote != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides Color.White,
                    ) { NoteLabel(shownNote, state.training.notation, large = true) }
                } else Text(stringResource(if (state.phase == Phase.IDLE) R.string.training_hero else R.string.hear_the_note),
                    color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(when (state.phase) {
                    Phase.PLAYING -> R.string.playing
                    Phase.DEMONSTRATING -> R.string.demonstrating
                    Phase.LISTENING -> R.string.sing_now
                    Phase.ANSWERING -> R.string.answer_now
                    Phase.SAVING -> R.string.saving
                    Phase.COMPLETE -> R.string.round_complete
                    Phase.FEEDBACK -> R.string.feedback_hint
                    else -> if (singing) R.string.fixed_sing_hint else R.string.fixed_ear_hint
                }), Modifier.padding(top = 6.dp), color = Mint, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(10) { index ->
                    Box(Modifier.weight(1f).height(4.dp).background(
                        if (index < state.answered) WarmGold else Mint.copy(alpha = 0.2f), RoundedCornerShape(2.dp)))
                }
            }
            Text(stringResource(R.string.training_progress, state.answered, state.correct), color = Mint,
                style = MaterialTheme.typography.labelMedium)
        }
    }
    if (singing && state.phase == Phase.LISTENING) PitchReadout(state)
    when (state.phase) {
        Phase.IDLE, Phase.COMPLETE, Phase.FEEDBACK -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.canHearAnswer || state.configurable) {
                    OutlinedButton(onClick = { onReference(state.canHearAnswer) },
                        enabled = state.loaded, modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                        Text(stringResource(if (state.canHearAnswer) R.string.play_answer else R.string.play_reference))
                    }
                }
                Button(onClick = onStart, enabled = state.loaded, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp)) {
                    Text(stringResource(when (state.phase) {
                        Phase.FEEDBACK -> R.string.next_question
                        Phase.COMPLETE -> R.string.new_round
                        else -> if (singing) R.string.start_singing else R.string.start_round
                    }))
                }
            }
        }
        Phase.ANSWERING -> {
            SectionCard(stringResource(R.string.choose_note)) {
                state.training.notes.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { note ->
                            OutlinedButton(onClick = { onAnswer(note) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 16.dp, horizontal = 4.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { NoteLabel(note, state.training.notation) }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                TextButton(onClick = onReplay) { Text(stringResource(R.string.replay)) }
            }
        }
        Phase.SAVING -> Unit
        else -> OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop)) }
    }
    if (state.phase in listOf(Phase.IDLE, Phase.COMPLETE, Phase.DEMONSTRATING)) {
        PianoKeyboard(state.canPreview, state.demoNote, onPreview)
    }
}

@Composable
private fun TrainingConfiguration(state: UiState, onConfigure: (TrainingSetup) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var rangeError by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase in listOf(Phase.PLAYING, Phase.ANSWERING, Phase.LISTENING, Phase.DEMONSTRATING)) expanded = false
    }
    val setup = state.training
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.training_setup), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.setup_summary, setup.notes.joinToString(" / ") { Music.name(it) },
                        stringResource(if (setup.notation == AnswerNotation.SOLFEGE) R.string.solfege_option else R.string.note_name_option)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { expanded = !expanded }, enabled = state.configurable) {
                    Text(stringResource(if (expanded) R.string.collapse else R.string.edit_setup))
                }
            }
            if (expanded) {
                Text(stringResource(R.string.range_title), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FixedTraining.beginner to R.string.three_notes, FixedTraining.fiveNotes to R.string.five_notes,
                        FixedTraining.notes to R.string.eight_notes).forEach { (notes, label) ->
                        FilterChip(selected = setup.notes == notes, enabled = state.configurable,
                            onClick = { rangeError = false; onConfigure(setup.copy(notes = notes)) },
                            label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(R.string.custom_range_hint), style = MaterialTheme.typography.bodySmall)
                FixedTraining.notes.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { note ->
                            val selected = note in setup.notes
                            FilterChip(selected = selected, enabled = state.configurable, onClick = {
                                if (selected && setup.notes.size == 1) rangeError = true
                                else {
                                    rangeError = false
                                    onConfigure(setup.copy(notes = if (selected) setup.notes - note else (setup.notes + note).sorted()))
                                }
                            }, label = { Text(Music.name(note)) })
                        }
                    }
                }
                if (rangeError) Text(stringResource(R.string.range_empty_error), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.answer_style), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnswerNotation.entries.forEach { notation ->
                        FilterChip(selected = setup.notation == notation, enabled = state.configurable,
                            onClick = { onConfigure(setup.copy(notation = notation)) },
                            label = { Text(stringResource(if (notation == AnswerNotation.SOLFEGE)
                                R.string.solfege_option else R.string.note_name_option)) })
                    }
                }
                Text(stringResource(R.string.tempo_title), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FixedTraining.tempos.forEach { bpm ->
                        FilterChip(selected = setup.bpm == bpm, enabled = state.configurable,
                            onClick = { onConfigure(setup.copy(bpm = bpm)) }, label = { Text("$bpm") })
                    }
                }
                Text(stringResource(R.string.eighth_hint, setup.timing.slotMs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun PitchReadout(state: UiState, includeCurve: Boolean = true) {
    SectionCard(stringResource(R.string.live_pitch)) {
        val frame = state.frame
        val frequency = frame?.frequency
        if (frequency == null || frame.confidence < 0.85) {
            Text(stringResource(R.string.no_pitch), style = MaterialTheme.typography.titleMedium)
        } else {
            val midi = Music.nearest(frequency, state.settings.a4.toDouble())
            val question = state.question
            val cents = Music.error(Music.midi(frequency, state.settings.a4.toDouble()),
                if (state.screen == Screen.SING && question != null) question.target else midi,
                state.screen == Screen.SING && state.settings.ignoreOctave)
            Text(Music.name(midi), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.pitch_readout, Music.name(midi), frequency, cents),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val gaugeDescription = stringResource(R.string.tuning_description, cents)
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(26.dp).semantics { contentDescription = gaugeDescription }) {
                drawLine(Color(0xFFE0E6DE), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4.dp.toPx())
                drawLine(primary, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
                drawCircle(primary, 6.dp.toPx(), Offset(((cents.coerceIn(-50.0, 50.0) + 50) / 100 * size.width).toFloat(),
                    size.height / 2))
            }
        }
        if (!includeCurve) return@SectionCard
        val description = stringResource(R.string.chart_description)
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description }) {
            for (line in 0..4) {
                val y = size.height * line / 4
                drawLine(Color(0xFFE0E6DE), Offset(0f, y), Offset(size.width, y))
            }
            val newest = state.curve.lastOrNull()?.timeMs ?: return@Canvas
            var previous: Offset? = null
            for (entry in state.curve) {
                val value = entry.frequency
                if (value == null || entry.confidence < 0.85) { previous = null; continue }
                val midi = Music.midi(value, state.settings.a4.toDouble())
                val point = Offset((1 - (newest - entry.timeMs) / 10_000f) * size.width,
                    (1 - ((midi - 36) / 48).toFloat().coerceIn(0f, 1f)) * size.height)
                previous?.let { drawLine(color, it, point, strokeWidth = 2.dp.toPx()) }
                previous = point
            }
        }
    }
}

@Composable
private fun SettingsPage(state: UiState, model: EarViewModel) {
    val context = LocalContext.current
    UpdateSettings(state.update, model::checkForUpdates, model::dismissUpdatePrompt) { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            true
        } catch (error: ActivityNotFoundException) {
            Log.w("RelativeEar", "No application can open update URL", error)
            model.updateOpenFailed()
            false
        } catch (error: SecurityException) {
            Log.w("RelativeEar", "Opening update URL denied", error)
            model.updateOpenFailed()
            false
        }
    }
    SectionCard(stringResource(R.string.tuning_title)) {
        var a4 by remember { mutableFloatStateOf(state.settings.a4.toFloat()) }
        LaunchedEffect(state.settings.a4) { a4 = state.settings.a4.toFloat() }
        Text(stringResource(R.string.a4_label, a4.toInt()), style = MaterialTheme.typography.titleLarge)
        Slider(value = a4, onValueChange = { a4 = it }, valueRange = 415f..466f, steps = 50,
            enabled = state.loaded,
            onValueChangeFinished = { model.settings(state.settings.copy(a4 = a4.toInt())) })
        Text(stringResource(R.string.standard_tuning_hint), style = MaterialTheme.typography.bodySmall)
    }
    SectionCard(stringResource(R.string.singing_settings)) {
        Text(stringResource(R.string.tolerance_label, state.settings.tolerance))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 35, 50).forEach { tolerance ->
                FilterChip(selected = state.settings.tolerance == tolerance, enabled = state.loaded,
                    onClick = { model.settings(state.settings.copy(tolerance = tolerance)) },
                    label = { Text("±$tolerance") })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.octave_label), Modifier.weight(1f))
            Switch(checked = state.settings.ignoreOctave, enabled = state.loaded,
                onCheckedChange = { model.settings(state.settings.copy(ignoreOctave = it)) })
        }
        Text(stringResource(R.string.fixed_octave_hint), style = MaterialTheme.typography.bodySmall)
    }
    SectionCard(stringResource(R.string.privacy_title)) {
        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.range_hint), style = MaterialTheme.typography.bodySmall)
        var confirm by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { confirm = true }, enabled = state.loaded) { Text(stringResource(R.string.clear_data)) }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.clear_confirm)) },
            confirmButton = { TextButton(onClick = { confirm = false; model.clearHistory() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Preview(name = "Beginner studio", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun StudioPreview() = TrainingPreview(UiState(loaded = true))

@Preview(name = "Eight-note answer grid", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun AnswerPreview() = TrainingPreview(UiState(loaded = true, phase = Phase.ANSWERING,
    training = TrainingSetup(FixedTraining.notes, AnswerNotation.NOTE_NAME), question = NoteQuestion(72), count = 1))

@Composable
private fun TrainingPreview(state: UiState) {
    EarTheme {
        AppShell(state, {}, {}) {
            TrainingPage(state, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
