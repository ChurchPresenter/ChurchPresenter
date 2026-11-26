package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.ThemeColors
import org.churchpresenter.theme.StateAlpha

@Immutable
internal data class TabColors(
    val content: Color,
    val layer: Color,
    val indicator: Color,
    val ring: Color?,
    val badge: Color,
    val onBadge: Color,
)

internal fun ThemeColors.tabColors(selected: Boolean, state: ButtonState): TabColors = TabColors(
    content = when {
        selected || state == ButtonState.Hovered || state == ButtonState.Pressed -> onSurface
        else -> onSurfaceVariant
    },
    layer = when (state) {
        ButtonState.Hovered -> onSurface.copy(alpha = StateAlpha.HOVER)
        ButtonState.Pressed -> onSurface.copy(alpha = StateAlpha.PRESSED)
        else -> Color.Transparent
    },
    indicator = if (selected) primary else Color.Transparent,
    ring = if (state == ButtonState.Focused) primary.copy(alpha = StateAlpha.FOCUS_RING) else null,
    badge = primary,
    onBadge = onPrimary,
)
