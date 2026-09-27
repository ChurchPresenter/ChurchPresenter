package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ElevationPalette
import org.churchpresenter.theme.elevationPalette
import kotlin.math.abs

private const val DISABLED_ALPHA = 0.5f
private const val HANDLE_REST_SCALE = 0.85f
private const val KNOB_SHADOW_ALPHA = 0.3f
private const val GROOVE_SHADE_LIGHT = 0.3f
private const val GROOVE_SHADE_DARK = 0.7f
private const val GROOVE_LIP_LIGHT = 0.9f
private const val GROOVE_LIP_DARK = 0.2f
private const val FILL_SHINE_ALPHA = 0.55f
private const val FILL_SHADE_ALPHA = 0.25f
private const val BEVEL_SHADE_DEPTH = 0.6f
private const val KNOB_RIM_SHADE = 0.35f
private val KNOB_RIM = 1.5.dp
private val TRACK_HEIGHT = 8.dp
private const val KNOB_EDGE_ALPHA = 0.2f
private val KNOB_RADIUS = 7.dp
private val HAIRLINE = 0.5.dp

/**
 * A slim slider in the elevated look: a sunken 6dp track with an accent gradient fill for the
 * selected portion, and a raised knob that grows a little on hover or drag. Tap or drag anywhere on
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
    val handleScale by animateFloatAsState(if (active) 1f else HANDLE_REST_SCALE, label = "slimHandleScale")

    // Hoisted for the Canvas below, which cannot read the theme itself.
    val palette = elevationPalette()

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
                drawElevatedSlider(originFraction, fraction, handleScale, palette)
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

/**
 * The sunken track, its accent fill between [originFraction] and [fraction], and the raised knob
 * at [fraction].
 */
private fun DrawScope.drawElevatedSlider(
    originFraction: Float,
    fraction: Float,
    handleScale: Float,
    palette: ElevationPalette,
) {
    val trackH = TRACK_HEIGHT.toPx()
    val cy = size.height / 2f
    val top = cy - trackH / 2f
    val radius = CornerRadius(trackH / 2f, trackH / 2f)
    val track = Rect(0f, top, size.width, top + trackH)
    // The groove: the well's color, shade falling from its top edge, light caught on its bottom lip.
    drawRoundRect(palette.wellBottom, topLeft = track.topLeft, size = track.size, cornerRadius = radius)
    clipPath(Path().apply { addRoundRect(RoundRect(track, radius)) }) {
        drawBevel(
            track,
            upper = Color.Black.copy(alpha = if (palette.isDark) GROOVE_SHADE_DARK else GROOVE_SHADE_LIGHT),
            lower = Color.White.copy(alpha = if (palette.isDark) GROOVE_LIP_DARK else GROOVE_LIP_LIGHT),
        )
    }
    if (fraction != originFraction) {
        val fillStart = size.width * minOf(originFraction, fraction)
        val playedW = size.width * abs(fraction - originFraction)
        val fill = Rect(fillStart, top, fillStart + playedW, top + trackH)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(palette.accent.bottom, palette.accent.top),
                startX = fillStart,
                endX = fillStart + playedW.coerceAtLeast(trackH)
            ),
            topLeft = fill.topLeft,
            size = fill.size,
            cornerRadius = radius
        )
        // The fill the other way round, lit on top and shaded underneath, so it stands in the groove.
        clipPath(Path().apply { addRoundRect(RoundRect(fill, radius)) }) {
            drawBevel(
                fill,
                upper = Color.White.copy(alpha = FILL_SHINE_ALPHA),
                lower = Color.Black.copy(alpha = FILL_SHADE_ALPHA),
            )
        }
    }
    // The knob: a drop shadow, a darker rim showing under it, then the top-lit face.
    val hx = (size.width * fraction).coerceIn(0f, size.width)
    val knobR = KNOB_RADIUS.toPx() * handleScale
    val rimDrop = KNOB_RIM.toPx()
    drawCircle(
        color = palette.dropShadow.copy(alpha = KNOB_SHADOW_ALPHA),
        radius = knobR + 1.dp.toPx(),
        center = Offset(hx, cy + rimDrop + 1.dp.toPx())
    )
    drawCircle(
        color = lerp(palette.key.bottom, Color.Black, KNOB_RIM_SHADE),
        radius = knobR,
        center = Offset(hx, cy + rimDrop)
    )
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(palette.key.top, palette.key.bottom),
            startY = cy - knobR,
            endY = cy + knobR
        ),
        radius = knobR,
        center = Offset(hx, cy)
    )
    drawCircle(
        color = palette.dropShadow.copy(alpha = KNOB_EDGE_ALPHA),
        radius = knobR,
        center = Offset(hx, cy),
        style = Stroke(HAIRLINE.toPx())
    )
}

/**
 * Shade in [upper] falling from the top of [bounds] over [BEVEL_SHADE_DEPTH] of its height, and a
 * 1dp [lower] line along its bottom.
 */
private fun DrawScope.drawBevel(bounds: Rect, upper: Color, lower: Color) {
    val line = 1.dp.toPx()
    drawRect(
        Brush.verticalGradient(
            listOf(upper, upper.copy(alpha = 0f)),
            startY = bounds.top,
            endY = bounds.top + bounds.height * BEVEL_SHADE_DEPTH,
        ),
        topLeft = bounds.topLeft,
        size = Size(bounds.width, bounds.height * BEVEL_SHADE_DEPTH),
    )
    drawRect(lower, topLeft = Offset(bounds.left, bounds.bottom - line), size = Size(bounds.width, line))
}
