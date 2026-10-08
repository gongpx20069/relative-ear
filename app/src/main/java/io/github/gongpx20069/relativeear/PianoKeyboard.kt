package io.github.gongpx20069.relativeear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gongpx20069.relativeear.core.FixedTraining
import io.github.gongpx20069.relativeear.core.Music
import io.github.gongpx20069.relativeear.core.ToneVoice

@Composable
internal fun PianoKeyboard(enabled: Boolean, activeNote: Int?, onNote: (Int) -> Unit) {
    SectionCard(stringResource(R.string.piano_title)) {
        Text(stringResource(R.string.piano_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PianoKeys(enabled, activeNote, onNote, Modifier.fillMaxWidth().height(142.dp))
    }
}

@Composable
internal fun PianoPage(state: UiState, onNote: (Int) -> Unit, onVoice: (ToneVoice) -> Unit, onExpand: () -> Unit) {
    if (state.pianoExpanded) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .displayCutoutPadding().padding(12.dp).testTag("piano-fullscreen"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.piano_fullscreen_label), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onExpand) { Text(stringResource(R.string.piano_collapse)) }
            }
            PianoVoices(state, onVoice)
            state.message?.let { Text(it.localized(), style = MaterialTheme.typography.bodySmall) }
            PianoKeys(state.canPreview, state.demoNote, onNote, Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Text(stringResource(R.string.piano_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        PianoVoices(state, onVoice)
        PianoKeyboard(state.canPreview, state.demoNote, onNote)
        OutlinedButton(onClick = onExpand, enabled = state.loaded, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.piano_expand))
        }
    }
}

@Composable
private fun PianoVoices(state: UiState, onVoice: (ToneVoice) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.piano_voice))
        for ((voice, label) in listOf(ToneVoice.PIANO to R.string.voice_piano,
            ToneVoice.FLUTE to R.string.voice_flute, ToneVoice.PURE to R.string.voice_pure)) {
            FilterChip(selected = state.pianoVoice == voice, onClick = { onVoice(voice) },
                enabled = state.canPreview && !state.pianoVoiceSaving,
                label = { Text(stringResource(label)) }, modifier = Modifier.testTag("voice-${voice.name}"))
        }
    }
}

@Composable
private fun PianoKeys(enabled: Boolean, activeNote: Int?, onNote: (Int) -> Unit, modifier: Modifier) {
    val names = stringArrayResource(R.array.fixed_solfege_names)
    BoxWithConstraints(modifier) {
        val keyboardWidth = maxOf(maxWidth, 404.dp)
        val keyHeight = (maxHeight - 6.dp).coerceAtLeast(64.dp)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(keyboardWidth).background(Pine, RoundedCornerShape(12.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FixedTraining.notes.forEachIndexed { index, note ->
                    val description = stringResource(R.string.piano_key, names[index], Music.name(note))
                    val active = note == activeNote
                    Column(Modifier.weight(1f).height(keyHeight)
                        .background(if (active) WarmGold else Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, if (active) WarmGold else Color(0xFFCED6CE), RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled, role = Role.Button) { onNote(note) }
                        .testTag("piano-$note").semantics { contentDescription = description; selected = active }
                        .padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(names[index], color = Pine, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(Music.name(note), color = Pine, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
