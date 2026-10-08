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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@Composable
internal fun PianoKeyboard(enabled: Boolean, activeNote: Int?, onNote: (Int) -> Unit) {
    val names = stringArrayResource(R.array.fixed_solfege_names)
    SectionCard(stringResource(R.string.piano_title)) {
        Text(stringResource(R.string.piano_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val keyboardWidth = maxOf(maxWidth, 404.dp)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.width(keyboardWidth).background(Pine, RoundedCornerShape(12.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FixedTraining.notes.forEachIndexed { index, note ->
                        val description = stringResource(R.string.piano_key, names[index], Music.name(note))
                        val active = note == activeNote
                        Column(Modifier.weight(1f).height(136.dp)
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
}
