package org.churchpresenter.converter.ui

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.components.drawSlider
import org.churchpresenter.theme.components.sliderLook

/**
 * A slim slider in the app's slider look (`drawSlider` in `:theme`): a flat 6dp track, the accent
 * for the selected portion, and a white handle ringed in the accent. Tap or drag anywhere on the
 * track to set the value.
 *
 * The current value is shown as a numeric label at the trailing end — pass [trailingLabel] formatted
 * for the unit (e.g. "80%").
 *
 * This mirrors `composables/SlimSlider.kt` in the main app, deliberately duplicated rather than
 * shared: the converter is its own Gradle build with no dependency on the app, so it cannot import
 * from it. Keep the two in step when either changes — the point of the copy is that a slider in the
 * converter looks like a slider everywhere else.
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
    val handleScale by animateFloatAsState(if (active) HANDLE_ACTIVE_SCALE else 1f, label = "slimHandleScale")

    // Hoisted for the Canvas below, which cannot read the theme itself.
    val look = sliderLook()

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
                drawSlider(look, fraction = fraction, thumbScale = handleScale)
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

private const val HANDLE_ACTIVE_SCALE = 1.15f
