package org.churchpresenter.hosanna.composables.field

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
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.HosannaColors
import org.churchpresenter.hosanna.HosannaStateAlpha

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

internal object FieldDefaults {
    val LabeledHeight: Dp = 44.dp
    val CompactHeight: Dp = 36.dp
    val MultilineMinHeight: Dp = 72.dp
    val MultilineMaxHeight: Dp = 200.dp
    val HorizontalPadding: Dp = 12.dp
    val MultilineVerticalPadding: Dp = 10.dp
    val LabelGap: Dp = 2.dp
    val Icon: Dp = 16.dp
    val IconGap: Dp = 8.dp
    val ClearCircle: Dp = 20.dp
    val ClearGlyph: Dp = 10.dp
    val Border: Dp = 1.dp
    val SupportingGap: Dp = 6.dp
    val SupportingIcon: Dp = 13.dp
    val SupportingIconGap: Dp = 4.dp
    val Dash: Dp = 4.dp
    val DashGap: Dp = 3.dp
    const val TRANSITION_MILLIS: Int = 120
}

internal fun fieldState(enabled: Boolean, readOnly: Boolean, hovered: Boolean, focused: Boolean): FieldState = when {
    !enabled -> FieldState.Disabled
    readOnly -> FieldState.ReadOnly
    focused -> FieldState.Focused
    hovered -> FieldState.Hovered
    else -> FieldState.Rest
}

internal fun HosannaColors.fieldColors(state: FieldState, error: Boolean): FieldColors {
    val accent = if (error) this.error else primary
    val border = if (error) this.error else outline
    val label = if (error) this.error else onSurfaceVariant
    return when (state) {
        FieldState.Rest -> FieldColors(surfaceVariant, border, null, label, onSurface, onSurfaceFaint, accent)
        FieldState.Hovered -> FieldColors(
            onSurface.copy(alpha = HosannaStateAlpha.HOVER).compositeOver(surfaceVariant),
            if (error) border else onSurface.copy(alpha = HosannaStateAlpha.HOVER).compositeOver(outline),
            null,
            label,
            onSurface,
            onSurfaceFaint,
            accent,
        )
        FieldState.Focused -> FieldColors(
            surfaceContainerLowest,
            accent,
            accent.copy(alpha = HosannaStateAlpha.FOCUS_RING),
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
            val ink = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
            val edge = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER)
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
