package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.SliderDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.SliderBubble
import org.churchpresenter.hosanna.composables.foundation.SliderColors
import org.churchpresenter.hosanna.composables.foundation.SliderThumb
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.sliderColors
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.model.RangeThumb
import org.churchpresenter.hosanna.composables.model.applySliderKey
import org.churchpresenter.hosanna.composables.model.formatNumber
import org.churchpresenter.hosanna.composables.model.moveRangeThumb
import org.churchpresenter.hosanna.composables.model.nearestRangeThumb
import org.churchpresenter.hosanna.composables.model.sliderFraction
import org.churchpresenter.hosanna.composables.model.sliderKey
import org.churchpresenter.hosanna.composables.model.sliderValueAt
import org.churchpresenter.hosanna.tabular

@Composable
fun RangeSlider(
    start: Double,
    end: Double,
    onRangeChange: (Double, Double) -> Unit,
    startDescription: String,
    endDescription: String,
    modifier: Modifier = Modifier,
    startInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    endInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    range: NumberRange = SliderDefaults.Percent,
    valueText: String? = null,
    onRangeChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
) {
    val startState = thumbState(startInteractionSource, enabled)
    val endState = thumbState(endInteractionSource, enabled)
    val trackColors = HosannaTheme.colors.sliderColors(if (enabled) ButtonState.Rest else ButtonState.Disabled)
    val current by rememberUpdatedState(start to end)
    val change by rememberUpdatedState(onRangeChange)
    val finished by rememberUpdatedState(onRangeChangeFinished)
    val sources = mapOf(RangeThumb.Start to startInteractionSource, RangeThumb.End to endInteractionSource)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = SliderDefaults.MinWidth)
                .height(SliderDefaults.HitHeight)
                .pointerInput(enabled, range) {
                    if (!enabled) return@pointerInput
                    val radius = SliderDefaults.Thumb.toPx() / 2
                    fun at(x: Float) = sliderValueAt((x - radius) / (size.width - 2 * radius), range)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val (s, e) = current
                        val thumb = nearestRangeThumb(at(down.position.x), s, e)
                        val source = sources.getValue(thumb)
                        val press = PressInteraction.Press(down.position)
                        source.tryEmit(press)
                        fun move(x: Float) {
                            val (from, to) = current
                            val (a, b) = moveRangeThumb(thumb, at(x), from, to)
                            change(a, b)
                        }
                        move(down.position.x)
                        drag(down.id) { moved ->
                            move(moved.position.x)
                            moved.consume()
                        }
                        source.tryEmit(PressInteraction.Release(press))
                        finished()
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val startF = sliderFraction(start, range)
            val endF = sliderFraction(end, range)
            val thumb = SliderDefaults.Thumb
            fun xOf(f: Float): Dp = thumb / 2 + (maxWidth - thumb) * f
            RangeCanvas(startF, endF, trackColors)
            listOf(
                Triple(RangeThumb.Start, startState, start),
                Triple(RangeThumb.End, endState, end),
            ).forEach { (which, state, value) ->
                val colors = HosannaTheme.colors.sliderColors(state)
                val x = xOf(if (which == RangeThumb.Start) startF else endF)
                SliderThumb(
                    x, thumb, colors, state, enabled,
                    Modifier.rangeThumb(
                        value = value,
                        description = if (which == RangeThumb.Start) startDescription else endDescription,
                        range = range,
                        source = sources.getValue(which),
                        onKey = if (enabled) {
                            { next ->
                                val (s, e) = current
                                val (a, b) = moveRangeThumb(which, next, s, e)
                                change(a, b)
                            }
                        } else {
                            null
                        },
                    ),
                )
                if (state == ButtonState.Pressed) {
                    SliderBubble(x, thumb / 2, formatNumber(value, range.decimals), colors)
                }
            }
        }
        valueText?.let {
            BasicText(
                it,
                modifier = Modifier.padding(start = SliderDefaults.Gap).widthIn(min = SliderDefaults.ValueWidth),
                style = HosannaTheme.typography.captionStrong.tabular()
                    .copy(color = trackColors.value, textAlign = TextAlign.End),
            )
        }
    }
}

@Composable
private fun thumbState(source: MutableInteractionSource, enabled: Boolean): ButtonState {
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    return buttonState(enabled, false, hovered, pressed, focused)
}

private fun Modifier.rangeThumb(
    value: Double,
    description: String,
    range: NumberRange,
    source: MutableInteractionSource,
    onKey: ((Double) -> Unit)?,
): Modifier = semantics {
    contentDescription = description
    stateDescription = formatNumber(value, range.decimals)
    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.min.toFloat()..range.max.toFloat())
    if (onKey != null) {
        setProgress { target ->
            onKey(sliderValueAt(sliderFraction(target.toDouble(), range), range))
            true
        }
    } else {
        disabled()
    }
}
    .hoverable(source, onKey != null)
    .focusable(onKey != null, source)
    .onKeyEvent { event ->
        val key = sliderKey(event.key, centered = false)
        if (onKey == null || key == null || event.type != KeyEventType.KeyDown) return@onKeyEvent false
        onKey(applySliderKey(key, event.isShiftPressed, value, range))
        true
    }

@Composable
private fun RangeCanvas(start: Float, end: Float, colors: SliderColors) {
    Canvas(Modifier.fillMaxWidth().height(SliderDefaults.HitHeight)) {
        val radius = SliderDefaults.Thumb.toPx() / 2
        val track = SliderDefaults.Track.toPx()
        val usable = size.width - 2 * radius
        val y = size.height / 2 - track / 2
        val corner = CornerRadius(track / 2)
        drawRoundRect(colors.track, Offset(radius, y), Size(usable, track), corner)
        drawRoundRect(colors.fill, Offset(radius + usable * start, y), Size(usable * (end - start), track), corner)
    }
}
