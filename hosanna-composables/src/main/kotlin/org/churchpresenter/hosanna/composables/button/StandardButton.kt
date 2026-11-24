package org.churchpresenter.hosanna.composables.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.HosannaColors
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.hosannaElevation

enum class ButtonHierarchy { Primary, Secondary, Tertiary }

enum class ButtonIntent { Default, Critical, Neutral }

internal enum class ButtonState { Rest, Hovered, Pressed, Focused, Loading, Disabled }

@Immutable
internal data class ButtonColors(val container: Color, val content: Color, val ring: Color?)

internal object StandardButtonDefaults {
    val Height: Dp = 34.dp
    val MinWidth: Dp = 64.dp
    val HorizontalPadding: Dp = 14.dp
    val IconSidePadding: Dp = 12.dp
    val TertiaryHorizontalPadding: Dp = 10.dp
    val IconSize: Dp = 16.dp
    val IconGap: Dp = 7.dp
    val SpinnerStroke: Dp = 2.dp
    const val TRANSITION_MILLIS: Int = 120
    const val SPIN_MILLIS: Int = 800
    const val SPINNER_SWEEP: Float = 270f
}

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

@Composable
fun StandardButton(
    text: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    hierarchy: ButtonHierarchy = ButtonHierarchy.Primary,
    intent: ButtonIntent = ButtonIntent.Default,
    icon: Painter? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val tertiary = hierarchy == ButtonHierarchy.Tertiary
    val end = if (tertiary) {
        StandardButtonDefaults.TertiaryHorizontalPadding
    } else {
        StandardButtonDefaults.HorizontalPadding
    }
    val start = if (icon != null && !tertiary) StandardButtonDefaults.IconSidePadding else end
    ButtonFrame(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minWidth = StandardButtonDefaults.MinWidth),
        hierarchy = hierarchy,
        intent = intent,
        enabled = enabled,
        loading = loading,
    ) { content ->
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .padding(PaddingValues(start = start, end = end))
                    .alpha(if (loading && icon == null) 0f else 1f),
                horizontalArrangement = Arrangement.spacedBy(StandardButtonDefaults.IconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    if (loading) Spinner(content) else ButtonIcon(icon, content)
                }
                BasicText(
                    text = text,
                    style = HosannaTheme.typography.label.copy(color = content),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (loading && icon == null) Spinner(content)
        }
    }
}

@Composable
fun StandardButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    hierarchy: ButtonHierarchy = ButtonHierarchy.Primary,
    intent: ButtonIntent = ButtonIntent.Default,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    ButtonFrame(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(StandardButtonDefaults.Height)
            .semantics { this.contentDescription = contentDescription },
        hierarchy = hierarchy,
        intent = intent,
        enabled = enabled,
        loading = loading,
    ) { content ->
        if (loading) Spinner(content) else ButtonIcon(icon, content)
    }
}

internal fun buttonState(enabled: Boolean, loading: Boolean, hovered: Boolean, pressed: Boolean, focused: Boolean) =
    when {
        !enabled -> ButtonState.Disabled
        loading -> ButtonState.Loading
        pressed -> ButtonState.Pressed
        focused -> ButtonState.Focused
        hovered -> ButtonState.Hovered
        else -> ButtonState.Rest
    }

internal fun HosannaColors.restColors(hierarchy: ButtonHierarchy, intent: ButtonIntent): Pair<Color, Color> =
    when (hierarchy) {
        ButtonHierarchy.Primary -> when (intent) {
            ButtonIntent.Default -> primary to onPrimary
            ButtonIntent.Critical -> error to onError
            ButtonIntent.Neutral -> secondary to onSecondary
        }
        ButtonHierarchy.Secondary -> when (intent) {
            ButtonIntent.Default -> primaryContainer to onPrimaryContainer
            ButtonIntent.Critical -> errorContainer to onErrorContainer
            ButtonIntent.Neutral -> secondaryContainer to onSecondaryContainer
        }
        ButtonHierarchy.Tertiary -> when (intent) {
            ButtonIntent.Default -> Color.Transparent to primary
            ButtonIntent.Critical -> Color.Transparent to error
            ButtonIntent.Neutral -> Color.Transparent to secondary
        }
    }

internal fun HosannaColors.buttonColors(
    hierarchy: ButtonHierarchy,
    intent: ButtonIntent,
    state: ButtonState,
): ButtonColors {
    val (container, content) = restColors(hierarchy, intent)
    fun layer(alpha: Float) = content.copy(alpha = alpha).compositeOver(container)
    return when (state) {
        ButtonState.Rest, ButtonState.Loading -> ButtonColors(container, content, null)
        ButtonState.Hovered -> ButtonColors(layer(HosannaStateAlpha.HOVER), content, null)
        ButtonState.Pressed -> ButtonColors(layer(HosannaStateAlpha.PRESSED), content, null)
        ButtonState.Focused -> ButtonColors(
            layer(HosannaStateAlpha.FOCUS),
            content,
            primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
        )
        ButtonState.Disabled -> ButtonColors(
            if (hierarchy == ButtonHierarchy.Tertiary) {
                Color.Transparent
            } else {
                onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER)
            },
            onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT),
            null,
        )
    }
}

internal fun buttonLevel(hierarchy: ButtonHierarchy, state: ButtonState): HosannaLevel =
    if (hierarchy == ButtonHierarchy.Primary && state in RaisedStates) HosannaLevel.Level1 else HosannaLevel.Level0

private val RaisedStates = setOf(ButtonState.Rest, ButtonState.Hovered, ButtonState.Loading)

@Composable
private fun ButtonFrame(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    hierarchy: ButtonHierarchy,
    intent: ButtonIntent,
    enabled: Boolean,
    loading: Boolean,
    content: @Composable (Color) -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(enabled, loading, hovered, pressed, focused)
    val colors = HosannaTheme.colors.buttonColors(hierarchy, intent, state)
    val container by animateColorAsState(
        colors.container,
        tween(StandardButtonDefaults.TRANSITION_MILLIS, easing = EaseOut),
    )
    val shape = HosannaTheme.shapes.m
    Box(
        modifier = modifier
            .hosannaElevation(buttonLevel(hierarchy, state), shape, HosannaTheme.elevation)
            .then(colors.ring?.let { Modifier.focusRing(it) } ?: Modifier)
            .height(StandardButtonDefaults.Height)
            .background(container, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(colors.content)
    }
}

private fun Modifier.focusRing(color: Color): Modifier = drawBehind {
    val width = HosannaTheme.focusRingWidth.toPx()
    val half = width / 2
    val radius = HosannaRadius.M.toPx() + half
    drawRoundRect(
        color = color,
        topLeft = Offset(-half, -half),
        size = Size(size.width + width, size.height + width),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width),
    )
}

@Composable
private fun ButtonIcon(icon: Painter, tint: Color) {
    Image(
        painter = icon,
        contentDescription = null,
        modifier = Modifier.size(StandardButtonDefaults.IconSize),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun Spinner(color: Color) {
    val turn by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(StandardButtonDefaults.SPIN_MILLIS, easing = LinearEasing),
            RepeatMode.Restart,
        ),
    )
    Canvas(
        Modifier
            .size(StandardButtonDefaults.IconSize)
            .progressSemantics()
            .rotate(turn),
    ) {
        val stroke = StandardButtonDefaults.SpinnerStroke.toPx()
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = StandardButtonDefaults.SPINNER_SWEEP,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
