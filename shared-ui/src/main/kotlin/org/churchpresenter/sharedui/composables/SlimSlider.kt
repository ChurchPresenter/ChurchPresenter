package org.churchpresenter.sharedui.composables

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.components.COMPACT_SLIDER_THUMB
import org.churchpresenter.theme.components.COMPACT_SLIDER_TRACK
import org.churchpresenter.theme.components.SLIDER_THUMB
import org.churchpresenter.theme.components.SLIDER_TRACK
import org.churchpresenter.theme.components.drawSlider
import org.churchpresenter.theme.components.sliderLook

private const val DISABLED_ALPHA = 0.5f
private const val HANDLE_ACTIVE_SCALE = 1.15f

/**
 * A slim slider in the app's slider look ([drawSlider]): a flat 6dp track, the accent for the
 * selected portion, and a white handle ringed in the accent that grows a little on hover or drag --
 * [compact] draws the 4dp track and smaller handle a volume control uses. Tap or drag anywhere on
 * the track to set the value.
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
    compact: Boolean = false,
) {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val span = (end - start).takeIf { it != 0f } ?: 1f
    val fraction = ((value - start) / span).coerceIn(0f, 1f)
    // Where the fill starts: zero, when the range runs either side of it -- a curve at 0% is an
    // empty bar, not a half-full one -- and the range's start otherwise.
    val originFraction = ((0f.coerceIn(start, end) - start) / span).coerceIn(0f, 1f)

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = enabled && (hovered || dragging)
    val handleScale by animateFloatAsState(if (active) HANDLE_ACTIVE_SCALE else 1f, label = "slimHandleScale")

    // Hoisted for the Canvas below, which cannot read the theme itself.
    val look = sliderLook()

    // `pointerInput` below keeps the block it was given until one of its keys changes, so the block
    // holds whichever lambda was passed on the composition that created it. A caller whose lambda
    // closes over data — the canvas panel's `source.copy(...)`, say — would then have a drag write
    // back the data as it stood when the slider first appeared: edit a text source, drag any slider,
    // and the old text comes back. Reading both callbacks through `rememberUpdatedState` keeps the
    // retained block pointed at the current ones.
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    fun seekToX(x: Float, width: Int) {
        if (!enabled || width <= 0) return
        val f = (x / width).coerceIn(0f, 1f)
        currentOnValueChange(start + f * span)
    }

    Row(
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
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
                        currentOnValueChangeFinished?.invoke()
                    }
                }
                .pointerInput(enabled, start, end) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = true; seekToX(offset.x, size.width) },
                        onDragEnd = { dragging = false; currentOnValueChangeFinished?.invoke() },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ -> seekToX(change.position.x, size.width) }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSlider(
                    look,
                    fraction = fraction,
                    originFraction = originFraction,
                    track = if (compact) COMPACT_SLIDER_TRACK else SLIDER_TRACK,
                    thumb = if (compact) COMPACT_SLIDER_THUMB else SLIDER_THUMB,
                    thumbScale = handleScale,
                )
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
