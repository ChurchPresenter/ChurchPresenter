package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.churchpresenter.hosanna.HosannaStateAlpha

@Immutable
internal data class SwitchColors(
    val track: Color,
    val edge: Color,
    val knob: Color,
    val icon: Color,
    val ring: Color?,
    val label: Color,
    val description: Color,
)

internal fun ThemeColors.switchColors(checked: Boolean, state: ButtonState, loading: Boolean = false): SwitchColors {
    if (state == ButtonState.Disabled) {
        val ink = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        return SwitchColors(
            track = if (checked) ink else surfaceContainerLowest,
            edge = if (checked) Color.Transparent else onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER),
            knob = if (checked) surfaceContainerLowest else ink,
            icon = ink,
            ring = null,
            label = ink,
            description = ink,
        )
    }
    val lifted = state == ButtonState.Hovered || state == ButtonState.Pressed
    val layer = if (state == ButtonState.Pressed) HosannaStateAlpha.PRESSED else HosannaStateAlpha.HOVER
    return SwitchColors(
        track = when {
            !checked -> surfaceContainerHighest
            lifted -> onPrimary.copy(alpha = layer).compositeOver(primary)
            else -> primary
        },
        edge = when {
            checked -> Color.Transparent
            lifted -> onSurfaceVariant
            else -> faint
        },
        knob = when {
            checked -> onPrimary
            lifted || loading -> onSurfaceVariant
            else -> faint
        },
        icon = if (checked) primary else surfaceContainerLowest,
        ring = if (state == ButtonState.Focused) primary.copy(alpha = HosannaStateAlpha.FOCUS_RING) else null,
        label = onSurface,
        description = onSurfaceVariant,
    )
}
