package io.github.gongpx20069.relativeear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gongpx20069.relativeear.core.FixedTraining
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.Music

@Composable
internal fun ListeningPage(
    state: UiState, onStart: () -> Unit, onStop: () -> Unit, onReplay: () -> Unit,
    onStopReplay: () -> Unit, onPiano: (Int) -> Unit,
) {
    Text(stringResource(R.string.listen_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
    PitchReadout(state, includeCurve = false)
    Button(onClick = if (state.phase == Phase.LISTENING) onStop else onStart,
        enabled = state.loaded && state.phase in listOf(Phase.IDLE, Phase.LISTENING),
        modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.phase == Phase.LISTENING) R.string.stop else R.string.start_listening))
    }
    SectionCard(stringResource(R.string.melody_title)) {
        Text(stringResource(R.string.melody_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MelodyTimeline(state)
        val clip = state.listeningClip
        if (state.listeningMs > MelodyClip.WINDOW_MS) {
            Text(stringResource(R.string.melody_recent_window), style = MaterialTheme.typography.bodySmall)
        }
        if (state.phase == Phase.REPLAYING) {
            val names = stringArrayResource(R.array.fixed_solfege_names)
            val note = state.demoNote
            val degree = note?.let(FixedTraining::degree)
            val label = if (note == null) stringResource(R.string.melody_rest)
                else if (degree != null) "${names[degree]} (${Music.name(note)})" else Music.name(note)
            Text(stringResource(R.string.melody_playing, label), color = Pine)
        }
        Text(stringResource(R.string.melody_progress, state.replayPositionMs / 1000.0,
            (clip?.durationMs ?: 0) / 1000.0), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = if (state.phase == Phase.REPLAYING) onStopReplay else onReplay,
            enabled = state.canReplay || state.phase == Phase.REPLAYING, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.phase == Phase.REPLAYING) R.string.melody_stop else R.string.melody_replay))
        }
        if (state.phase != Phase.LISTENING && clip?.notes.isNullOrEmpty()) {
            Text(stringResource(R.string.melody_empty), style = MaterialTheme.typography.bodySmall)
        }
    }
    val liveNote = state.frame?.takeIf { it.confidence >= 0.85 }?.frequency?.let {
        Music.nearest(it, state.settings.a4.toDouble())
    }
    PianoKeyboard(state.canPreview, state.demoNote ?: liveNote, onPiano)
    SectionCard(stringResource(R.string.notes_title)) {
        if (state.notes.isEmpty()) Text(stringResource(R.string.no_notes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.notes.takeLast(30).asReversed().forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Music.name(it.midi), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.note_timing, it.startMs / 1000.0, it.durationMs / 1000.0))
            }
        }
    }
}

@Composable
private fun MelodyTimeline(state: UiState) {
    val clip = state.listeningClip
    val duration = clip?.durationMs ?: 1
    val origin = (state.listeningMs - MelodyClip.WINDOW_MS).coerceAtLeast(0)
    val progress = (state.replayPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    val description = stringResource(R.string.melody_board)
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("melody-timeline").semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
    }) {
        val inset = 4.dp.toPx()
        val width = size.width - inset * 2
        fun x(time: Long) = inset + width * (time.toFloat() / duration).coerceIn(0f, 1f)
        fun y(midi: Double) = size.height * (1 - ((midi - 34) / 52).toFloat().coerceIn(0f, 1f))
        for (line in 0..6) {
            val py = size.height * line / 6
            drawLine(Color(0xFFE0E6DE), Offset(0f, py), Offset(size.width, py))
            val px = inset + width * line / 6
            drawLine(Color(0xFFE0E6DE), Offset(px, 0f), Offset(px, size.height))
        }
        var previous: Offset? = null
        if (state.phase == Phase.LISTENING) state.curve.forEach { frame ->
            val frequency = frame.frequency
            if (frequency == null || frame.confidence < 0.85) previous = null
            else {
                val point = Offset(x(frame.timeMs - origin), y(Music.midi(frequency, state.settings.a4.toDouble())))
                previous?.let { drawLine(Pine.copy(alpha = 0.45f), it, point, 2.dp.toPx()) }
                previous = point
            }
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 11.sp.toPx()
        }
        clip?.notes?.forEach {
            val left = x(it.startMs)
            val right = x(it.startMs + it.durationMs)
            val top = (y(it.midi.toDouble()) - 9.dp.toPx()).coerceIn(0f, size.height - 18.dp.toPx())
            drawRoundRect(Mint, Offset(left, top), Size(maxOf(1f, right - left), 18.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            val label = Music.name(it.midi)
            if (right - left > paint.measureText(label) + 4.dp.toPx()) {
                drawContext.canvas.nativeCanvas.drawText(label, left + 2.dp.toPx(), top + 13.dp.toPx(), paint)
            }
        }
        drawLine(Color(0xFFD94242), Offset(inset + progress * width, 0f),
            Offset(inset + progress * width, size.height), 2.dp.toPx())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.melody_seconds, origin / 1000.0), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.melody_seconds, state.listeningMs / 1000.0), style = MaterialTheme.typography.labelSmall)
    }
}
