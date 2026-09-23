package org.churchpresenter.lottiegen.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.flatDisabled
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken
import kotlin.math.roundToInt

/**
 * The house slider, in the app's elevated look: a sunken rounded track with the accent gradient
 * filling it up to the value, and a raised knob that swells slightly on hover -- the same slider as
 * the app's own panels. Replaces Material's Slider so the control panel and the preview transport
 * share one visual language.
 *
 * Both a press anywhere on the track and a drag move the value, matching the reference's
 * pointer-capture behaviour.
 */
@Composable
fun LottieSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    trackHeight: Dp = 5.dp,
    knobSize: Dp = 13.dp,
    /** The well's fill; unspecified is the theme's own sunken well. */
    trackColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    /** What the filled part of the track is painted with; the accent gradient by default. */
    fillBrush: Brush? = null,
) {
    val density = LocalDensity.current
    val palette = elevationPalette()
    val defaultFill = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, palette.accent.top))
    var widthPx by remember { mutableStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    // Knob grows on hover, mirroring `.sld:hover .knob { scale(1.18) }` in the reference.
    val knobScale by animateFloatAsState(if (hovered && enabled) 1.18f else 1f, label = "knobScale")

    fun emit(xPx: Float) {
        if (!enabled || widthPx <= 0) return
        val t = (xPx / widthPx).coerceIn(0f, 1f)
        val raw = valueRange.start + t * span
        val snapped = if (steps > 0) {
            val stepSize = span / (steps + 1)
            valueRange.start + (((raw - valueRange.start) / stepSize).roundToInt() * stepSize)
        } else raw
        onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { widthPx = it.width }
            .hoverable(interaction, enabled)
            .pointerInput(enabled, valueRange, steps, widthPx) {
                if (enabled) detectTapGestures { emit(it.x) }
            }
            .pointerInput(enabled, valueRange, steps, widthPx) {
                if (enabled) detectDragGestures(
                    onDragStart = { emit(it.x) },
                    onDrag = { change, _ -> change.consume(); emit(change.position.x) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .sunken(RoundedCornerShape(99.dp), palette, fill = trackColor)
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(99.dp))
                    .background(fillBrush ?: defaultFill)
            )
        }

        // Knob — centred on the fill edge, so it travels the full track width.
        val knobPx = with(density) { knobSize.toPx() }
        val knobX = with(density) { (fraction * widthPx - knobPx / 2f).toDp() }
        Box(
            modifier = Modifier
                .offset(x = knobX)
                .size(knobSize)
                .graphicsLayer { scaleX = knobScale; scaleY = knobScale }
                .then(
                    if (enabled) {
                        Modifier.raised(CircleShape, palette.key, palette, lift = 2.dp)
                    } else {
                        Modifier.flatDisabled(CircleShape, palette)
                    }
                )
        )
    }
}
