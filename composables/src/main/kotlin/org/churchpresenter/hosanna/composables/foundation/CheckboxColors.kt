package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.state.ToggleableState
import org.churchpresenter.theme.ThemeColors
import org.churchpresenter.theme.StateAlpha

@Immutable
internal data class CheckboxColors(
    val fill: Color,
    val edge: Color,
    val mark: Color,
    val halo: Color,
    val ring: Color?,
    val label: Color,
    val description: Color,
)

internal fun ThemeColors.checkboxColors(value: ToggleableState, state: ButtonState, error: Boolean): CheckboxColors {
    val on = value != ToggleableState.Off
    if (state == ButtonState.Disabled) {
        val ink = onSurface.copy(alpha = StateAlpha.DISABLED_CONTENT)
        return CheckboxColors(
            fill = if (on) ink else surfaceContainerLowest,
            edge = if (on) Color.Transparent else onSurface.copy(alpha = StateAlpha.DISABLED_CONTAINER),
            mark = surfaceContainerLowest,
            halo = Color.Transparent,
            ring = null,
            label = ink,
            description = ink,
        )
    }
    val layer = when (state) {
        ButtonState.Hovered -> StateAlpha.HOVER
        ButtonState.Pressed -> StateAlpha.PRESSED
        else -> 0f
    }
    val restEdge = if (error) this.error else onSurfaceFaint
    return CheckboxColors(
        fill = if (on) onPrimary.copy(alpha = layer).compositeOver(primary) else surfaceContainerLowest,
        edge = when {
            on -> Color.Transparent
            error -> this.error
            layer > 0f -> onSurfaceVariant
            else -> restEdge
        },
        mark = onPrimary,
        halo = if (layer > 0f) onSurface.copy(alpha = layer) else Color.Transparent,
        ring = if (state == ButtonState.Focused) primary.copy(alpha = StateAlpha.FOCUS_RING) else null,
        label = onSurface,
        description = onSurfaceVariant,
    )
}
