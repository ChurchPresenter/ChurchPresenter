package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A slim, modern slider matching the media seek bar: a 5px rounded track with a teal (theme
 * `primary`) gradient fill for the selected portion, and a white handle that fades and scales in
 * only on hover or drag, so the bar stays clean at rest. Tap or drag anywhere on the track to set
 * the value.
 *
 * The current value is shown as a numeric label at the trailing (right) end — pass [trailingLabel]
 * formatted for the unit (e.g. "80%", "45°", "1.5s"). Leave it null to omit (e.g. when a text field
 * follows the slider instead).
 */
@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingLabel: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val span = (end - start).takeIf { it != 0f } ?: 1f
    val fraction = ((value - start) / span).coerceIn(0f, 1f)

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = enabled && (hovered || dragging)
    val handleAlpha by animateFloatAsState(if (active) 1f else 0f, label = "slimHandleAlpha")
    val handleScale by animateFloatAsState(if (active) 1f else 0.35f, label = "slimHandleScale")

    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val playedStart = primary.copy(alpha = 0.65f)

    fun seekToX(x: Float, width: Int) {
        if (!enabled || width <= 0) return
        val f = (x / width).coerceIn(0f, 1f)
        onValueChange(start + f * span)
    }

    Row(
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .hoverable(interactionSource, enabled = enabled)
                .pointerInput(enabled, start, end) {
                    detectTapGestures { offset ->
                        seekToX(offset.x, size.width)
                        onValueChangeFinished?.invoke()
                    }
                }
                .pointerInput(enabled, start, end) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = true; seekToX(offset.x, size.width) },
                        onDragEnd = { dragging = false; onValueChangeFinished?.invoke() },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ -> seekToX(change.position.x, size.width) }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackH = 5.dp.toPx()
                val cy = size.height / 2f
                val top = cy - trackH / 2f
                val radius = CornerRadius(trackH / 2f, trackH / 2f)
                drawRoundRect(color = trackColor, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = radius)
                if (fraction > 0f) {
                    val playedW = size.width * fraction
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(playedStart, primary), startX = 0f, endX = playedW.coerceAtLeast(trackH)),
                        topLeft = Offset(0f, top),
                        size = Size(playedW, trackH),
                        cornerRadius = radius
                    )
                }
                if (handleAlpha > 0.01f) {
                    val hx = (size.width * fraction).coerceIn(0f, size.width)
                    drawCircle(color = Color.White.copy(alpha = handleAlpha), radius = 6.dp.toPx() * handleScale, center = Offset(hx, cy))
                }
            }
        }
        if (trailingLabel != null) {
            Text(
                text = trailingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 44.dp)
            )
        }
    }
}
