package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.churchpresenter.theme.HosannaStateAlpha

@Immutable
internal data class ListRowColors(
    val container: Color,
    val content: Color,
    val supporting: Color,
    val ring: Color?,
    val bar: Color?,
)

internal fun ThemeColors.listRowColors(
    selected: Boolean,
    live: Boolean,
    marker: Boolean,
    state: ButtonState,
): ListRowColors {
    if (state == ButtonState.Disabled) {
        val ink = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        return ListRowColors(Color.Transparent, ink, ink, null, null)
    }
    val base = if (selected) primaryContainer else Color.Transparent
    val content = if (selected) onPrimaryContainer else onSurface
    val layer = when (state) {
        ButtonState.Hovered -> HosannaStateAlpha.HOVER
        ButtonState.Pressed -> HosannaStateAlpha.PRESSED
        else -> 0f
    }
    return ListRowColors(
        container = if (layer > 0f) onSurface.copy(alpha = layer).compositeOver(base) else base,
        content = content,
        supporting = if (selected) onPrimaryContainer else onSurfaceVariant,
        ring = if (state == ButtonState.Focused) primary else null,
        bar = when {
            live -> this.live
            selected && marker -> primary
            else -> null
        },
    )
}
