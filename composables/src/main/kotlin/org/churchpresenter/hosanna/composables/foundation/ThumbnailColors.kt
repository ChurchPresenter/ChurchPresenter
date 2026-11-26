package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.ThemeColors
import org.churchpresenter.theme.StateAlpha
import org.churchpresenter.hosanna.composables.model.ThumbnailSize
import org.churchpresenter.hosanna.composables.model.ThumbnailStatus

@Immutable
internal data class ThumbnailColors(
    val edge: Color,
    val border: Color?,
    val ring: Color?,
    val scrim: Color,
    val badge: Color,
    val onBadge: Color,
    val placeholder: Color,
    val shine: Color,
    val placeholderInk: Color,
    val letterbox: Color,
    val caption: Color,
    val captionDim: Color,
)

internal fun ThemeColors.thumbnailColors(selected: Boolean, live: Boolean, state: ButtonState): ThumbnailColors {
    val lifted = state == ButtonState.Hovered || state == ButtonState.Pressed
    return ThumbnailColors(
        edge = if (lifted) onSurfaceFaint else onSurface.copy(alpha = StateAlpha.DISABLED_CONTAINER),
        border = when {
            live -> this.live
            selected -> primary
            else -> null
        },
        ring = if (state == ButtonState.Focused) primary.copy(alpha = StateAlpha.FOCUS_RING) else null,
        scrim = when (state) {
            ButtonState.Hovered -> screen.copy(alpha = StateAlpha.SCRIM)
            ButtonState.Pressed -> screen.copy(alpha = StateAlpha.OVERLAY)
            else -> Color.Transparent
        },
        badge = screen.copy(alpha = StateAlpha.OVERLAY),
        onBadge = onScreen,
        placeholder = outlineVariant,
        shine = surfaceContainerHigh,
        placeholderInk = onSurfaceVariant,
        letterbox = screen,
        caption = if (selected && !live) primary else onSurface,
        captionDim = onSurfaceVariant,
    )
}

@Immutable
internal class ThumbnailLook(
    val size: ThumbnailSize,
    val status: ThumbnailStatus,
    val hidden: Boolean,
    val colors: ThumbnailColors,
    val actions: Boolean,
    val hover: Boolean,
)
