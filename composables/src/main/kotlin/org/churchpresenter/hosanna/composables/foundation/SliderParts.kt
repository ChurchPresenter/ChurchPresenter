package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.SliderDefaults
import org.churchpresenter.hosanna.hosannaElevation
import org.churchpresenter.hosanna.tabular

@Composable
internal fun SliderThumb(
    x: Dp,
    size: Dp,
    colors: SliderColors,
    state: ButtonState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val halo = if (state == ButtonState.Pressed) SliderDefaults.DragHalo else SliderDefaults.HoverHalo
    Box(
        Modifier
            .offset(x = x - size / 2 - halo)
            .size(size + halo * 2)
            .background(colors.halo, CircleShape),
    )
    Box(
        modifier
            .offset(x = x - size / 2)
            .size(size)
            .then(colors.ring?.let { Modifier.focusRing(it, null) } ?: Modifier)
            .then(if (enabled) Modifier.thumbShadow() else Modifier)
            .background(colors.thumb, CircleShape)
            .border(if (enabled) SliderDefaults.ThumbRing else SliderDefaults.ThumbEdge, colors.thumbRing, CircleShape),
    )
}

@Composable
private fun Modifier.thumbShadow(): Modifier =
    hosannaElevation(HosannaLevel.Level1, CircleShape, HosannaTheme.elevation)

@Composable
internal fun SliderBubble(x: Dp, above: Dp, text: String, colors: SliderColors) {
    BasicText(
        text,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE))
                layout(0, 0) {
                    val bottom = above + SliderDefaults.BubbleGap
                    placeable.place(x.roundToPx() - placeable.width / 2, -bottom.roundToPx() - placeable.height)
                }
            }
            .background(colors.bubble, RoundedCornerShape(SliderDefaults.BubbleRadius))
            .padding(horizontal = SliderDefaults.BubblePaddingX, vertical = SliderDefaults.BubblePaddingY),
        style = HosannaTheme.typography.captionStrong.tabular().copy(color = colors.bubbleInk),
    )
}
