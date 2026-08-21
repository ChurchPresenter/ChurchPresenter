package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.editor.EditorState
import org.churchpresenter.lottiegen.spec.AnimProperty
import org.churchpresenter.lottiegen.spec.ElementSpec
import org.churchpresenter.lottiegen.ui.Strings
import kotlin.math.abs

private val RowHeight = 22.dp
private val NameColumnWidth = 110.dp

// Named per-property marker colors (timeline legend).
private val ColorPosition = Color(0xFF4FC3F7)
private val ColorOpacity = Color(0xFF81C784)
private val ColorScale = Color(0xFFFFB74D)
private val ColorRotation = Color(0xFFBA68C8)
private val ColorRectSize = Color(0xFF4DD0E1)
private val ColorStrokeWidth = Color(0xFFF06292)
private val ColorTrim = Color(0xFFAED581)
private val ScrubColor = Color(0xFFE53935)
private val GridColor = Color(0x33FFFFFF)

private fun propertyColor(property: AnimProperty): Color = when (property) {
    AnimProperty.POSITION_OFFSET -> ColorPosition
    AnimProperty.OPACITY -> ColorOpacity
    AnimProperty.SCALE -> ColorScale
    AnimProperty.ROTATION -> ColorRotation
    AnimProperty.RECT_SIZE -> ColorRectSize
    AnimProperty.STROKE_WIDTH -> ColorStrokeWidth
    AnimProperty.TRIM -> ColorTrim
}

/**
 * Timeline strip: one row per element, keyframe diamonds at their in-phase
 * percentages, and a draggable scrub line. The axis covers the animate-in only —
 * the exit is its auto-generated mirror and is not editable here; the preview's
 * full-range seek slider still reaches hold/out.
 *
 * Scrub position maps to composition progress as `pct/100 × inFrames/totalFrames`.
 */
@Composable
fun TimelinePanel(
    state: EditorState,
    seek: Float,
    onSeekChange: (Float) -> Unit,
    onPlayingChange: (Boolean) -> Unit
) {
    val elements = state.spec.elements
    val inFraction = state.inFrames.toFloat() / state.totalFrames.toFloat()
    // Current scrub position in in-phase percent (parks at 100 during hold/out).
    val scrubPct = ((seek / inFraction) * 100f).coerceIn(0f, 100f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                Strings.editorTimeline,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                Strings.editorTimelineNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Element names
            Column(modifier = Modifier.width(NameColumnWidth)) {
                Box(modifier = Modifier.height(RowHeight)) // axis header spacer
                for (element in elements) {
                    Box(modifier = Modifier.height(RowHeight)) {
                        Text(
                            element.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (element.id == state.selectedElementId)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Track area
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(RowHeight),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (label in listOf("0%", "25%", "50%", "75%", "100%")) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight * elements.size)
                        .pointerInputScrub(elements, rowHeightPx, inFraction, state, onSeekChange, onPlayingChange)
                ) {
                    drawGrid(elements.size, rowHeightPx)
                    elements.forEachIndexed { rowIndex, element ->
                        drawElementRow(element, rowIndex, rowHeightPx)
                    }
                    // Scrub line
                    val x = size.width * scrubPct / 100f
                    drawLine(ScrubColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                }
            }
        }
    }
}

private fun Modifier.pointerInputScrub(
    elements: List<ElementSpec>,
    rowHeightPx: Float,
    inFraction: Float,
    state: EditorState,
    onSeekChange: (Float) -> Unit,
    onPlayingChange: (Boolean) -> Unit
): Modifier = this
    .pointerInput(elements) {
        detectTapGestures { offset ->
            val hit = hitTestKeyframe(elements, offset, size.width.toFloat(), rowHeightPx)
            if (hit != null) {
                state.selectElement(hit)
            } else {
                onPlayingChange(false)
                onSeekChange((offset.x / size.width).coerceIn(0f, 1f) * inFraction)
            }
        }
    }
    .pointerInput(elements) {
        detectDragGestures { change, _ ->
            onPlayingChange(false)
            onSeekChange((change.position.x / size.width).coerceIn(0f, 1f) * inFraction)
        }
    }

private fun hitTestKeyframe(
    elements: List<ElementSpec>,
    offset: Offset,
    width: Float,
    rowHeightPx: Float
): String? {
    val rowIndex = (offset.y / rowHeightPx).toInt()
    val element = elements.getOrNull(rowIndex) ?: return null
    val hitRadius = rowHeightPx * 0.5f
    for (track in element.tracks) {
        for (keyframe in track.keyframes) {
            val x = width * (keyframe.pct / 100.0).toFloat()
            if (abs(x - offset.x) <= hitRadius) return element.id
        }
    }
    return null
}

private fun DrawScope.drawGrid(rowCount: Int, rowHeightPx: Float) {
    for (quarter in 0..4) {
        val x = size.width * quarter / 4f
        drawLine(GridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
    for (row in 0..rowCount) {
        val y = row * rowHeightPx
        drawLine(GridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawElementRow(element: ElementSpec, rowIndex: Int, rowHeightPx: Float) {
    val centerY = rowIndex * rowHeightPx + rowHeightPx / 2
    val diamond = rowHeightPx * 0.28f
    for (track in element.tracks) {
        val color = propertyColor(track.property)
        for (keyframe in track.keyframes) {
            val x = size.width * (keyframe.pct / 100.0).toFloat()
            val path = Path().apply {
                moveTo(x, centerY - diamond)
                lineTo(x + diamond, centerY)
                lineTo(x, centerY + diamond)
                lineTo(x - diamond, centerY)
                close()
            }
            drawPath(path, color)
        }
    }
}
