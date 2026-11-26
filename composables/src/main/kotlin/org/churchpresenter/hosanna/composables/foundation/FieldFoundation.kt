package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import org.churchpresenter.theme.ThemeColors
import org.churchpresenter.theme.StateAlpha
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults

internal enum class FieldState { Rest, Hovered, Focused, Disabled, ReadOnly }

@Immutable
internal data class FieldColors(
    val container: Color,
    val border: Color,
    val ring: Color?,
    val label: Color,
    val value: Color,
    val icon: Color,
    val caret: Color,
)

internal fun fieldState(enabled: Boolean, readOnly: Boolean, hovered: Boolean, focused: Boolean): FieldState = when {
    !enabled -> FieldState.Disabled
    readOnly -> FieldState.ReadOnly
    focused -> FieldState.Focused
    hovered -> FieldState.Hovered
    else -> FieldState.Rest
}

internal fun ThemeColors.fieldColors(state: FieldState, error: Boolean): FieldColors {
    val accent = if (error) this.error else primary
    val border = if (error) this.error else outline
    val label = if (error) this.error else onSurfaceVariant
    return when (state) {
        FieldState.Rest -> FieldColors(surfaceVariant, border, null, label, onSurface, onSurfaceFaint, accent)
        FieldState.Hovered -> FieldColors(
            onSurface.copy(alpha = StateAlpha.HOVER).compositeOver(surfaceVariant),
            if (error) border else onSurface.copy(alpha = StateAlpha.HOVER).compositeOver(outline),
            null,
            label,
            onSurface,
            onSurfaceFaint,
            accent,
        )
        FieldState.Focused -> FieldColors(
            surfaceContainerLowest,
            accent,
            accent.copy(alpha = StateAlpha.FOCUS_RING),
            accent,
            onSurface,
            onSurfaceFaint,
            accent,
        )
        FieldState.ReadOnly -> FieldColors(
            Color.Transparent,
            outline,
            null,
            onSurfaceVariant,
            onSurface,
            onSurfaceFaint,
            Color.Transparent,
        )
        FieldState.Disabled -> {
            val ink = onSurface.copy(alpha = StateAlpha.DISABLED_CONTENT)
            val edge = onSurface.copy(alpha = StateAlpha.DISABLED_CONTAINER)
            FieldColors(surfaceVariant, edge, null, ink, ink, ink, Color.Transparent)
        }
    }
}

internal fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val half = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx() - half),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(FieldDefaults.Dash.toPx(), FieldDefaults.DashGap.toPx()),
            ),
        ),
    )
}
