package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.churchpresenter.hosanna.HosannaStateAlpha

@Immutable
internal data class SliderColors(
    val track: Color,
    val fill: Color,
    val buffered: Color,
    val thumb: Color,
    val thumbRing: Color,
    val halo: Color,
    val ring: Color?,
    val tick: Color,
    val tickOn: Color,
    val bubble: Color,
    val bubbleInk: Color,
    val value: Color,
    val endLabel: Color,
)

internal fun ThemeColors.sliderColors(state: ButtonState): SliderColors {
    if (state == ButtonState.Disabled) {
        val ink = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        return SliderColors(
            track = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER),
            fill = ink,
            buffered = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER),
            thumb = surfaceContainerHigh,
            thumbRing = ink,
            halo = Color.Transparent,
            ring = null,
            tick = ink,
            tickOn = surfaceContainerLowest,
            bubble = inverseSurface,
            bubbleInk = inverseOnSurface,
            value = ink,
            endLabel = ink,
        )
    }
    return SliderColors(
        track = outlineVariant,
        fill = primary,
        buffered = primary.copy(alpha = HosannaStateAlpha.SECONDARY_FILL),
        thumb = onPrimary,
        thumbRing = primary,
        halo = when (state) {
            ButtonState.Hovered -> primary.copy(alpha = HosannaStateAlpha.HOVER)
            ButtonState.Pressed -> primary.copy(alpha = HosannaStateAlpha.DRAGGED)
            else -> Color.Transparent
        },
        ring = if (state == ButtonState.Focused) primary.copy(alpha = HosannaStateAlpha.FOCUS_RING) else null,
        tick = faint,
        tickOn = onPrimary,
        bubble = inverseSurface,
        bubbleInk = inverseOnSurface,
        value = onSurfaceVariant,
        endLabel = faint,
    )
}
