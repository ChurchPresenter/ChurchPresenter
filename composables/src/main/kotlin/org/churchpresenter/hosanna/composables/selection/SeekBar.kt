package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.SeekBarDefaults
import org.churchpresenter.hosanna.composables.defaults.SliderDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.SliderBubble
import org.churchpresenter.hosanna.composables.foundation.SliderColors
import org.churchpresenter.hosanna.composables.foundation.SliderThumb
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.sliderColors
import org.churchpresenter.hosanna.composables.model.applySeekKey
import org.churchpresenter.hosanna.composables.model.clockText
import org.churchpresenter.hosanna.composables.model.seekFraction
import org.churchpresenter.hosanna.composables.model.seekTimeAt
import org.churchpresenter.hosanna.composables.model.sliderKey
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.hosanna.composables.foundation.collectIsFocusVisibleAsState

/**
 * A media seek bar with the elapsed and total time either side. Hovering shows the time under the pointer.
 *
 * @param position the playback position in seconds.
 * @param duration the length in seconds.
 * @param onSeek called with the position picked.
 * @param contentDescription the bar's screen-reader name.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param buffered how far the media has loaded, in seconds.
 * @param showTimes shows the times either side.
 * @param onSeekFinished called when a drag or press ends.
 * @param enabled whether it can be moved.
 */
@Composable
fun SeekBar(
    position: Double,
    duration: Double,
    onSeek: (Double) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    buffered: Double = 0.0,
    showTimes: Boolean = true,
    onSeekFinished: () -> Unit = {},
    enabled: Boolean = true,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusVisibleAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val colors = ThemeColors.current.sliderColors(state)
    val timeStyle = HosannaTheme.typography.captionStrong.tabular().copy(color = colors.value)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SeekBarDefaults.TimeGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTimes) {
            BasicText(
                clockText(position),
                Modifier.widthIn(min = SeekBarDefaults.TimeWidth),
                style = timeStyle.copy(textAlign = TextAlign.End),
            )
        }
        SeekTrack(
            position = position,
            duration = duration,
            buffered = buffered,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            contentDescription = contentDescription,
            colors = colors,
            state = state,
            interactionSource = interactionSource,
            enabled = enabled,
            modifier = Modifier.weight(1f).widthIn(min = SliderDefaults.MinWidth),
        )
        if (showTimes) {
            BasicText(clockText(duration), Modifier.widthIn(min = SeekBarDefaults.TimeWidth), style = timeStyle)
        }
    }
}

@Composable
private fun SeekTrack(
    position: Double,
    duration: Double,
    buffered: Double,
    onSeek: (Double) -> Unit,
    onSeekFinished: () -> Unit,
    contentDescription: String,
    colors: SliderColors,
    state: ButtonState,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    modifier: Modifier,
) {
    val current by rememberUpdatedState(position)
    val seek by rememberUpdatedState(onSeek)
    val finished by rememberUpdatedState(onSeekFinished)
    var pointerX by remember { mutableStateOf<Float?>(null) }
    val fraction = seekFraction(position, duration)
    BoxWithConstraints(
        modifier = modifier
            .height(SliderDefaults.HitHeight)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = clockText(position)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    position.toFloat(),
                    0f..duration.coerceAtLeast(0.0).toFloat(),
                )
                if (enabled) {
                    setProgress { target ->
                        seek(target.toDouble().coerceIn(0.0, duration.coerceAtLeast(0.0)))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .hoverable(interactionSource, enabled)
            .focusable(enabled, interactionSource)
            .onKeyEvent { event ->
                val key = sliderKey(event.key, centered = false)
                if (!enabled || key == null || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                seek(applySeekKey(key, event.isShiftPressed, current, duration))
                true
            }
            .pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pointerX = when (event.type) {
                            PointerEventType.Exit -> null
                            else -> event.changes.first().position.x
                        }
                    }
                }
            }
            .pointerInput(enabled, duration) {
                if (!enabled) return@pointerInput
                val radius = SeekBarDefaults.Thumb.toPx() / 2
                fun at(x: Float) = seekTimeAt((x - radius) / (size.width - 2 * radius), duration)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    seek(at(down.position.x))
                    drag(down.id) { moved ->
                        seek(at(moved.position.x))
                        moved.consume()
                    }
                    interactionSource.tryEmit(PressInteraction.Release(press))
                    finished()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = SeekBarDefaults.Thumb
        val thumbX = thumb / 2 + (maxWidth - thumb) * fraction
        SeekCanvas(fraction, seekFraction(buffered, duration), colors)
        val active = state == ButtonState.Hovered || state == ButtonState.Pressed || state == ButtonState.Focused
        if (active) SliderThumb(thumbX, thumb, colors, state, enabled)
        val density = LocalDensity.current
        val hoverAt = pointerX
        when {
            state == ButtonState.Pressed -> SliderBubble(thumbX, thumb / 2, clockText(position), colors)
            state == ButtonState.Hovered && hoverAt != null -> {
                val x = with(density) { hoverAt.toDp() }.coerceIn(thumb / 2, maxWidth - thumb / 2)
                val time = seekTimeAt((x - thumb / 2) / (maxWidth - thumb), duration)
                SliderBubble(x, thumb / 2, clockText(time), colors)
            }
        }
    }
}

@Composable
private fun SeekCanvas(played: Float, buffered: Float, colors: SliderColors) {
    Canvas(Modifier.fillMaxWidth().height(SliderDefaults.HitHeight)) {
        val radius = SeekBarDefaults.Thumb.toPx() / 2
        val track = SeekBarDefaults.Track.toPx()
        val usable = size.width - 2 * radius
        val top = Offset(radius, size.height / 2 - track / 2)
        val corner = CornerRadius(track / 2)
        drawRoundRect(colors.track, top, Size(usable, track), corner)
        drawRoundRect(colors.buffered, top, Size(usable * maxOf(buffered, played), track), corner)
        drawRoundRect(colors.fill, top, Size(usable * played, track), corner)
    }
}
