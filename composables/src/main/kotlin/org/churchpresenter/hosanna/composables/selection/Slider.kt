package org.churchpresenter.hosanna.composables.selection

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.SliderDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.SliderBubble
import org.churchpresenter.hosanna.composables.foundation.SliderColors
import org.churchpresenter.hosanna.composables.foundation.SliderThumb
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.sliderColors
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.model.applySliderKey
import org.churchpresenter.hosanna.composables.model.formatNumber
import org.churchpresenter.hosanna.composables.model.signedText
import org.churchpresenter.hosanna.composables.model.sliderFraction
import org.churchpresenter.hosanna.composables.model.sliderKey
import org.churchpresenter.hosanna.composables.model.sliderOrigin
import org.churchpresenter.hosanna.composables.model.sliderStepDots
import org.churchpresenter.hosanna.composables.model.sliderValueAt
import org.churchpresenter.hosanna.composables.model.snapToOrigin
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.hosanna.composables.foundation.collectIsFocusVisibleAsState

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * A slider for a value in a range, with optional steps, value text and a center origin. ← → step it, Shift
 * steps ten times, Home and End jump to the ends.
 *
 * @param value the value.
 * @param onValueChange called with the new value while dragging or stepping.
 * @param contentDescription the slider's screen-reader name.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param range the allowed values and the step it snaps to; ten steps or fewer show dots.
 * @param valueText the value shown at the end and in the drag bubble, such as "60%".
 * @param centered fills from the middle, snaps to it and resets to it on a double-click, 0 or Delete.
 * @param startLabel optional text before the slider, such as "−6".
 * @param endLabel optional text after the slider, such as "+6".
 * @param onValueChangeFinished called when a drag or press ends.
 * @param enabled whether it can be moved.
 */
@Composable
fun Slider(
    value: Double,
    onValueChange: (Double) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    range: NumberRange = SliderDefaults.Percent,
    valueText: String? = null,
    centered: Boolean = false,
    startLabel: String? = null,
    endLabel: String? = null,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusVisibleAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val colors = ThemeColors.current.sliderColors(state)
    val shown = valueText ?: if (centered) signedText(value, range.decimals) else formatNumber(value, range.decimals)
    val labelStyle = HosannaTheme.typography.caption.copy(color = colors.endLabel)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SliderDefaults.LabelGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        startLabel?.let { BasicText(it, style = labelStyle) }
        SliderTrack(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            contentDescription = contentDescription,
            range = range,
            centered = centered,
            bubbleText = shown,
            colors = colors,
            state = state,
            interactionSource = interactionSource,
            enabled = enabled,
            modifier = Modifier.weight(1f).widthIn(min = SliderDefaults.MinWidth),
        )
        endLabel?.let { BasicText(it, style = labelStyle) }
        valueText?.let {
            BasicText(
                it,
                modifier = Modifier
                    .padding(start = SliderDefaults.Gap - SliderDefaults.LabelGap)
                    .widthIn(min = if (centered) SliderDefaults.SignedValueWidth else SliderDefaults.ValueWidth),
                style = HosannaTheme.typography.captionStrong.tabular()
                    .copy(color = colors.value, textAlign = TextAlign.End),
            )
        }
    }
}

@Composable
private fun SliderTrack(
    value: Double,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: () -> Unit,
    contentDescription: String,
    range: NumberRange,
    centered: Boolean,
    bubbleText: String,
    colors: SliderColors,
    state: ButtonState,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    modifier: Modifier,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val dragging = state == ButtonState.Pressed
    val fraction by animateFloatAsState(
        sliderFraction(value, range),
        if (dragging) snap() else tween(SliderDefaults.FILL_MILLIS, easing = EaseOut),
    )
    BoxWithConstraints(
        modifier = modifier
            .height(SliderDefaults.HitHeight)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = bubbleText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    value.toFloat(),
                    range.min.toFloat()..range.max.toFloat(),
                    (sliderStepDots(range).size - 2).coerceAtLeast(0),
                )
                if (enabled) {
                    setProgress { target ->
                        change(sliderValueAt(sliderFraction(target.toDouble(), range), range))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .hoverable(interactionSource, enabled)
            .focusable(enabled, interactionSource)
            .onKeyEvent { event ->
                val key = sliderKey(event.key, centered)
                if (!enabled || key == null || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                change(applySliderKey(key, event.isShiftPressed, current, range))
                true
            }
            .pointerInput(enabled, range, centered) {
                if (!enabled) return@pointerInput
                val radius = SliderDefaults.Thumb.toPx() / 2
                fun at(x: Float): Double {
                    val next = sliderValueAt((x - radius) / (size.width - 2 * radius), range)
                    return if (centered) snapToOrigin(next, range) else next
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    change(at(down.position.x))
                    drag(down.id) { moved ->
                        change(at(moved.position.x))
                        moved.consume()
                    }
                    interactionSource.tryEmit(PressInteraction.Release(press))
                    finished()
                }
            }
            .then(
                if (centered && enabled) {
                    Modifier.pointerInput(range) {
                        detectTapGestures(onDoubleTap = { change(sliderOrigin(range)); finished() })
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbX = SliderDefaults.Thumb / 2 + (maxWidth - SliderDefaults.Thumb) * fraction
        TrackCanvas(fraction, if (centered) sliderFraction(sliderOrigin(range), range) else null, range, colors)
        SliderThumb(thumbX, SliderDefaults.Thumb, colors, state, enabled)
        if (dragging) SliderBubble(thumbX, SliderDefaults.Thumb / 2, bubbleText, colors)
    }
}

@Composable
private fun TrackCanvas(fraction: Float, origin: Float?, range: NumberRange, colors: SliderColors) {
    val dots = remember(range) { sliderStepDots(range) }
    Canvas(Modifier.fillMaxWidth().height(SliderDefaults.HitHeight)) {
        val radius = SliderDefaults.Thumb.toPx() / 2
        val track = SliderDefaults.Track.toPx()
        val usable = size.width - 2 * radius
        val y = size.height / 2
        val corner = CornerRadius(track / 2)
        fun xOf(f: Float) = radius + usable * f
        drawRoundRect(colors.track, Offset(radius, y - track / 2), Size(usable, track), corner)
        val from = xOf(origin ?: 0f)
        val to = xOf(fraction)
        val start = minOf(from, to)
        drawRoundRect(colors.fill, Offset(start, y - track / 2), Size(maxOf(from, to) - start, track), corner)
        dots.forEach { dot ->
            val filled = xOf(dot) in start..maxOf(from, to)
            drawCircle(if (filled) colors.tickOn else colors.tick, SliderDefaults.Dot.toPx() / 2, Offset(xOf(dot), y))
        }
        origin?.let {
            val mark = SliderDefaults.CenterMark.toPx()
            val high = SliderDefaults.CenterMarkHeight.toPx()
            drawRect(
                if (fraction != it) colors.fill else colors.tick,
                Offset(xOf(it) - mark / 2, y - high / 2),
                Size(mark, high),
            )
        }
    }
}
