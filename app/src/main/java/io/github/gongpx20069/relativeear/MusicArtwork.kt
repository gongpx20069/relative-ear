package io.github.gongpx20069.relativeear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.gongpx20069.relativeear.core.FixedTraining
import io.github.gongpx20069.relativeear.core.Music

@Composable
fun NavigationArtwork(screen: Screen) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(25.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x, y), Offset(x2, y2), 1.8f, StrokeCap.Round)
            when (screen) {
                Screen.EAR -> {
                    val path = Path().apply {
                        moveTo(8f, 8f); cubicTo(8f, 1f, 20f, 2f, 20f, 9f)
                        cubicTo(20f, 14f, 14f, 13f, 14f, 18f)
                        cubicTo(14f, 23f, 8f, 23f, 7f, 18f)
                        moveTo(11f, 9f); cubicTo(11f, 5f, 17f, 6f, 17f, 9f)
                        cubicTo(17f, 12f, 12f, 11f, 12f, 14f)
                    }
                    drawPath(path, color, style = Stroke(1.8f, cap = StrokeCap.Round))
                    line(3f, 8f, 2f, 11f); line(2f, 11f, 3f, 14f)
                }
                Screen.SING -> {
                    drawRoundRect(color, Offset(9f, 2f), Size(6f, 13f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f), style = Stroke(1.8f))
                    drawArc(color, 0f, 180f, false, Offset(5f, 6f), Size(14f, 13f), style = Stroke(1.8f))
                    line(12f, 19f, 12f, 22f); line(8f, 22f, 16f, 22f)
                }
                Screen.LISTEN -> listOf(5f, 2f, 7f, 4f, 6f).forEachIndexed { i, extent ->
                    line(4f + i * 4, 12f - extent, 4f + i * 4, 12f + extent)
                }
                Screen.HISTORY -> {
                    line(4f, 21f, 21f, 21f); line(4f, 21f, 4f, 4f)
                    line(8f, 17f, 8f, 12f); line(13f, 17f, 13f, 7f); line(18f, 17f, 18f, 3f)
                }
                Screen.SETTINGS -> {
                    for (i in 0..2) {
                        val y = 5f + i * 7
                        val x = if (i == 1) 15f else 8f
                        line(3f, y, x - 3, y); line(x + 3, y, 21f, y)
                        drawCircle(color, 2.8f, Offset(x, y), style = Stroke(1.8f))
                    }
                }
            }
        }
    }
}

@Composable
fun NoteStaff(note: Int?, modifier: Modifier = Modifier) {
    val description = if (note == null) stringResource(R.string.staff_hidden)
        else stringResource(R.string.staff_note, Music.name(note))
    Canvas(modifier.fillMaxWidth().height(126.dp).semantics { contentDescription = description }) {
        val spacing = 17.dp.toPx()
        val bottom = size.height * 0.76f
        if (note != null) {
            for (line in 0..4) {
                val y = bottom - line * spacing
                drawLine(Mint.copy(alpha = 0.28f), Offset(size.width * 0.12f, y),
                    Offset(size.width * 0.88f, y), 1.dp.toPx())
            }
        } else drawCircle(Mint.copy(alpha = 0.07f), 52.dp.toPx(), Offset(size.width / 2, size.height / 2))
        // An unpitched icon replaces the staff until the answer has been submitted.
        val degree = note?.let(FixedTraining::degree) ?: 6
        val center = Offset(size.width / 2, bottom - (degree - 2) * spacing / 2)
        val radiusX = 10.dp.toPx()
        val radiusY = 6.5.dp.toPx()
        if (degree == 0) {
            drawLine(Mint, Offset(center.x - radiusX * 1.8f, center.y),
                Offset(center.x + radiusX * 1.8f, center.y), 1.dp.toPx())
        }
        rotate(-18f, center) {
            drawOval(WarmGold, center - Offset(radiusX, radiusY), Size(radiusX * 2, radiusY * 2))
        }
        val stem = center.x + radiusX * 0.85f
        val top = center.y - spacing * 2.7f
        drawLine(WarmGold, Offset(stem, center.y), Offset(stem, top), 2.2.dp.toPx())
        val flag = Path().apply {
            moveTo(stem, top)
            cubicTo(stem + spacing * 0.25f, top + spacing * 0.9f,
                stem + spacing * 1.6f, top + spacing * 0.6f,
                stem + spacing * 0.9f, top + spacing * 1.9f)
        }
        drawPath(flag, WarmGold, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}
