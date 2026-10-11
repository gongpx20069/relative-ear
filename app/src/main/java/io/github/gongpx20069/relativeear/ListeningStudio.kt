package io.github.gongpx20069.relativeear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gongpx20069.relativeear.core.AnswerNotation
import io.github.gongpx20069.relativeear.core.DetectionTake
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.TimelineWindow
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun detectionPitchLabel(midi: Int, notation: AnswerNotation): String {
    val names = stringArrayResource(R.array.chromatic_solfege_names)
    return if (notation == AnswerNotation.NOTE_NAME) Music.name(midi)
        else "${names[Math.floorMod(midi, 12)]} · ${Music.name(midi)}"
}

@Composable
internal fun ListeningPage(
    state: UiState, onStart: () -> Unit, onStop: () -> Unit, onReplay: () -> Unit,
    onStopReplay: () -> Unit, onSave: () -> Unit = {}, onMode: (DetectionMode) -> Unit = {},
    onNotation: (AnswerNotation) -> Unit = {}, onExpand: () -> Unit = {},
) {
    Text(stringResource(R.string.listen_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
    PitchReadout(state, includeCurve = false)
    DetectionCanvas(state.listeningTake, state.listeningClip, state, state.listeningId,
        onMode, onNotation, onExpand)
    ListeningActions(state, onStart, onStop, onReplay, onStopReplay, onSave)
    DetectionNoteDetails(state.listeningClip, state.detectionNotation)
}

@Composable
internal fun ListeningActions(state: UiState, onStart: () -> Unit, onStop: () -> Unit,
    onReplay: () -> Unit, onStopReplay: () -> Unit, onSave: () -> Unit) {
    var replace by rememberSaveable { mutableStateOf(false) }
    Button(onClick = {
        if (state.phase == Phase.LISTENING) onStop()
        else if (state.hasUnsavedDetection) replace = true else onStart()
    }, enabled = state.loaded && state.phase in listOf(Phase.IDLE, Phase.LISTENING),
        modifier = Modifier.fillMaxWidth()) {
        Text(if (state.phase == Phase.LISTENING) stringResource(R.string.detection_stop_elapsed, state.listeningMs / 1000.0)
            else stringResource(R.string.start_listening))
    }
    Text(stringResource(R.string.detection_limit_hint), style = MaterialTheme.typography.bodySmall)
    ReplayControls(state, onReplay, onStopReplay)
    OutlinedButton(onClick = onSave, enabled = state.canSaveDetection, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.listeningSaved) R.string.detection_saved else R.string.detection_save))
    }
    if (replace) AlertDialog(onDismissRequest = { replace = false },
        title = { Text(stringResource(R.string.detection_new)) },
        text = { Text(stringResource(R.string.detection_replace_warning)) },
        confirmButton = { TextButton(onClick = { replace = false; onStart() }) {
            Text(stringResource(R.string.detection_discard_start))
        } },
        dismissButton = { TextButton(onClick = { replace = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun ReplayControls(state: UiState, onReplay: () -> Unit, onStopReplay: () -> Unit) {
    val clip = state.activeClip
    Text(stringResource(R.string.melody_progress, state.replayPositionMs / 1000.0,
        (clip?.durationMs ?: 0) / 1000.0), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = if (state.phase == Phase.REPLAYING) onStopReplay else onReplay,
        enabled = state.canReplay || state.phase == Phase.REPLAYING, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.phase == Phase.REPLAYING) R.string.melody_stop else R.string.melody_replay))
    }
    val playing = state.demoNote
    if (state.phase == Phase.REPLAYING) {
        Text(stringResource(R.string.melody_playing,
            if (playing == null) stringResource(R.string.melody_rest) else detectionPitchLabel(playing, state.detectionNotation)))
    }
    Text(stringResource(R.string.detection_synthetic_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun DetectionFullscreen(state: UiState, onMode: (DetectionMode) -> Unit,
    onNotation: (AnswerNotation) -> Unit, onExit: () -> Unit, onStart: () -> Unit, onStop: () -> Unit,
    onReplay: () -> Unit, onStopReplay: () -> Unit, onSave: () -> Unit) {
    val history = state.screen == Screen.HISTORY
    Column(Modifier.fillMaxSize().testTag("detection-fullscreen").verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetectionCanvas(if (history) state.savedDetection?.take else state.listeningTake,
            state.activeClip, state, if (history) state.selectedDetection?.id else state.listeningId,
            onMode, onNotation, onExit, expanded = true)
        state.message?.let { Text(it.localized(), color = MaterialTheme.colorScheme.error) }
        if (history) ReplayControls(state, onReplay, onStopReplay)
        else ListeningActions(state, onStart, onStop, onReplay, onStopReplay, onSave)
    }
}

@Composable
internal fun DetectionCanvas(take: DetectionTake?, clip: MelodyClip?, state: UiState, identity: String?,
    onMode: (DetectionMode) -> Unit, onNotation: (AnswerNotation) -> Unit,
    onExpand: () -> Unit, expanded: Boolean = false) {
    var reviewStart by rememberSaveable(identity) { mutableFloatStateOf(0f) }
    val listening = state.screen == Screen.LISTEN && state.phase == Phase.LISTENING
    val replaying = state.phase == Phase.REPLAYING
    val duration = clip?.durationMs ?: state.listeningMs
    LaunchedEffect(listening) { if (!listening) reviewStart = 0f }
    val window = when {
        listening -> TimelineWindow.live(duration)
        replaying -> TimelineWindow.following(state.replayPositionMs, duration)
        else -> TimelineWindow.browse(reviewStart.toLong(), duration)
    }
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (expanded) TextButton(onClick = onExpand, modifier = Modifier.testTag("detection-expand")) {
                Text(stringResource(R.string.piano_collapse))
            }
            DetectionMode.entries.forEach { mode ->
                FilterChip(selected = state.detectionMode == mode, onClick = { onMode(mode) },
                    modifier = Modifier.testTag("detection-mode-${mode.name}"),
                    label = { Text(stringResource(if (mode == DetectionMode.NOTES) R.string.detection_notes_mode else R.string.detection_curve_mode)) })
            }
            if (!expanded) TextButton(onClick = onExpand, modifier = Modifier.testTag("detection-expand")) {
                Text(stringResource(R.string.piano_expand))
            }
            if (expanded) DetectionNotationChips(state, onNotation)
        }
        if (!expanded) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetectionNotationChips(state, onNotation)
        }
        if (!expanded) Text(stringResource(R.string.detection_window_hint), style = MaterialTheme.typography.bodySmall)
        PitchTimeline(take, clip, state.detectionMode, state.detectionNotation, window,
            state.replayPositionMs, replaying, listening, expanded,
            onPan = { delta -> reviewStart = (reviewStart + delta).coerceIn(0f, (duration - TimelineWindow.WIDTH_MS).coerceAtLeast(0).toFloat()) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.melody_seconds, window.startMs.coerceAtLeast(0) / 1000.0),
                style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.melody_seconds, minOf(window.endMs, duration).coerceAtLeast(0) / 1000.0),
                style = MaterialTheme.typography.labelSmall)
        }
        if (!listening && duration > TimelineWindow.WIDTH_MS) {
            Text(stringResource(R.string.detection_seek), style = MaterialTheme.typography.labelMedium)
            Slider(value = window.startMs.toFloat(), onValueChange = { reviewStart = it }, enabled = !replaying,
                valueRange = 0f..(duration - TimelineWindow.WIDTH_MS).toFloat(),
                modifier = Modifier.testTag("detection-review-slider"))
        }
        if (!expanded && clip?.notes.isNullOrEmpty()) Text(stringResource(R.string.melody_empty),
            style = MaterialTheme.typography.bodySmall)
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    else SectionCard(stringResource(R.string.melody_title)) { content() }
}

@Composable
private fun DetectionNotationChips(state: UiState, onNotation: (AnswerNotation) -> Unit) {
    AnswerNotation.entries.forEach { notation ->
        FilterChip(selected = state.detectionNotation == notation, onClick = { onNotation(notation) },
            modifier = Modifier.testTag("detection-label-${notation.name}"),
            label = { Text(stringResource(if (notation == AnswerNotation.SOLFEGE)
                R.string.solfege_option else R.string.note_name_option)) })
    }
}

@Composable
private fun PitchTimeline(take: DetectionTake?, clip: MelodyClip?, mode: DetectionMode,
    notation: AnswerNotation, window: TimelineWindow, positionMs: Long, replaying: Boolean,
    listening: Boolean, expanded: Boolean, onPan: (Float) -> Unit) {
    val names = stringArrayResource(R.array.chromatic_solfege_names)
    fun label(midi: Int) = if (notation == AnswerNotation.NOTE_NAME) Music.name(midi)
        else "${names[Math.floorMod(midi, 12)]} ${Music.name(midi)}"
    val points = take?.points.orEmpty()
    val notes = clip?.notes.orEmpty()
    val pitches = points.mapNotNull { it.midi } + notes.map { it.midi.toDouble() }
    val low = minOf(58, floor(pitches.minOrNull() ?: 60.0).toInt() - 2).coerceAtLeast(0)
    val high = maxOf(74, ceil(pitches.maxOrNull() ?: 72.0).toInt() + 2).coerceAtMost(127)
    val description = stringResource(R.string.detection_canvas_description,
        stringResource(if (mode == DetectionMode.NOTES) R.string.detection_notes_mode else R.string.detection_curve_mode),
        window.startMs.coerceAtLeast(0) / 1000.0, window.endMs.coerceAtLeast(0) / 1000.0)
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val currentOnPan by rememberUpdatedState(onPan)
    val chartHeight = if (expanded) (LocalConfiguration.current.screenHeightDp - 200).coerceAtLeast(110).dp else 250.dp
    Canvas(Modifier.fillMaxWidth().height(chartHeight).testTag("melody-timeline")
        .semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(
                if (clip == null) 0f else (positionMs.toFloat() / clip.durationMs).coerceIn(0f, 1f), 0f..1f)
        }.pointerInput(listening, replaying) {
            if (!listening && !replaying) detectHorizontalDragGestures { change, drag ->
                change.consume()
                currentOnPan(-drag / size.width * TimelineWindow.WIDTH_MS)
            }
        }) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor; textSize = 10.sp.toPx()
        }
        val left = maxOf(74.dp.toPx(), (low..high).maxOf { paint.measureText(label(it)) } + 8.dp.toPx())
            .coerceAtMost(size.width * 0.45f)
        val width = (size.width - left - 4.dp.toPx()).coerceAtLeast(1f)
        fun x(time: Long) = left + width * window.fraction(time)
        fun y(midi: Double) = 12.dp.toPx() + (size.height - 24.dp.toPx()) * (1 - (midi - low) / (high - low)).toFloat()
        var previousLabelY: Float? = null
        for (midi in low..high) {
            val pitchClass = midi % 12
            val natural = pitchClass in listOf(0, 2, 4, 5, 7, 9, 11)
            drawLine(if (natural) Color(0xFFD7E1D7) else Color(0xFFEBEFE9),
                Offset(left, y(midi.toDouble())), Offset(size.width, y(midi.toDouble())), 1.dp.toPx())
            val visibleLabel = natural && (high - low <= 18 || pitchClass in
                if (high - low <= 26) listOf(0, 4, 7) else listOf(0, 7))
            val labelY = y(midi.toDouble())
            if (visibleLabel && (previousLabelY == null || previousLabelY - labelY >= paint.fontSpacing + 2.dp.toPx())) {
                drawContext.canvas.nativeCanvas.drawText(label(midi), 0f, labelY + 3.dp.toPx(), paint)
                previousLabelY = labelY
            }
        }
        clipRect(left, 0f, size.width, size.height) {
            val firstSecond = Math.floorDiv(window.startMs, 1000L) * 1000
            for (time in firstSecond..window.endMs step 1000L) {
                drawLine(Color(0xFFE0E6DE), Offset(x(time), 0f), Offset(x(time), size.height))
            }
            notes.filter { it.startMs + it.durationMs >= window.startMs && it.startMs <= window.endMs }.forEach {
                val start = x(it.startMs)
                val end = x(it.startMs + it.durationMs)
                val height = minOf(18.dp.toPx(), (size.height - 24.dp.toPx()) / (high - low))
                val top = y(it.midi.toDouble()) - height / 2
                drawRoundRect(Mint.copy(alpha = if (mode == DetectionMode.CURVE) 0.4f else 1f),
                    Offset(start, top), Size((end - start).coerceAtLeast(1f), height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
            if (mode == DetectionMode.CURVE) {
                var previous: Pair<Long, Offset>? = null
                points.forEach { point ->
                    val midi = point.midi
                    if (midi == null) previous = null else {
                        val next = Offset(x(point.timeMs), y(midi))
                        previous?.let { (time, offset) ->
                            if (point.timeMs - time <= 160) drawLine(Pine, offset, next, 2.dp.toPx())
                        }
                        if (point.timeMs in window.startMs..window.endMs) drawCircle(Pine, 1.5.dp.toPx(), next)
                        previous = point.timeMs to next
                    }
                }
            }
            if (replaying || positionMs > 0) {
                drawLine(Color(0xFFD94242), Offset(x(positionMs), 0f),
                    Offset(x(positionMs), size.height), 2.dp.toPx())
            }
            if (listening) drawLine(Pine, Offset(x(window.endMs) - 1.dp.toPx(), 0f),
                Offset(x(window.endMs) - 1.dp.toPx(), size.height), 1.dp.toPx())
        }
    }
}

@Composable
internal fun DetectionNoteDetails(clip: MelodyClip?, notation: AnswerNotation) {
    var expanded by rememberSaveable(clip?.durationMs) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.detection_hide_details else R.string.detection_show_details,
            clip?.notes?.size ?: 0))
    }
    if (expanded) SectionCard(stringResource(R.string.detection_note_details)) {
        if (clip?.notes.isNullOrEmpty()) Text(stringResource(R.string.no_notes))
        clip?.notes?.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(detectionPitchLabel(it.midi, notation))
                Text(stringResource(R.string.note_timing, it.startMs / 1000.0, it.durationMs / 1000.0),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
