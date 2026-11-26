package org.churchpresenter.hosanna.composables.foundation

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Dp
import org.churchpresenter.theme.ThemeColors
import org.churchpresenter.theme.ElevationLevel
import org.churchpresenter.theme.StateAlpha
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.ButtonFoundationDefaults
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.theme.elevationShadow

internal enum class SegmentRole { Radio, Action, Toggle }

internal enum class ButtonState { Rest, Hovered, Pressed, Focused, Loading, Disabled }

@Immutable
internal data class ButtonColors(val container: Color, val content: Color, val ring: Color?)

internal const val FULL_TURN = 360f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

internal fun buttonState(enabled: Boolean, loading: Boolean, hovered: Boolean, pressed: Boolean, focused: Boolean) =
    when {
        !enabled -> ButtonState.Disabled
        loading -> ButtonState.Loading
        pressed -> ButtonState.Pressed
        focused -> ButtonState.Focused
        hovered -> ButtonState.Hovered
        else -> ButtonState.Rest
    }

internal fun ThemeColors.restColors(hierarchy: ButtonHierarchy, intent: ButtonIntent): Pair<Color, Color> =
    when (hierarchy) {
        ButtonHierarchy.Primary -> when (intent) {
            ButtonIntent.Default -> primary to onPrimary
            ButtonIntent.Critical -> error to onError
            ButtonIntent.Neutral -> secondary to onSecondary
            ButtonIntent.Success -> success to onSuccess
            ButtonIntent.Warning -> tertiary to onTertiary
        }
        ButtonHierarchy.Secondary -> when (intent) {
            ButtonIntent.Default -> primaryContainer to onPrimaryContainer
            ButtonIntent.Critical -> errorContainer to onErrorContainer
            ButtonIntent.Neutral -> secondaryContainer to onSecondaryContainer
            ButtonIntent.Success -> successContainer to onSuccessContainer
            ButtonIntent.Warning -> tertiaryContainer to onTertiaryContainer
        }
        ButtonHierarchy.Tertiary -> when (intent) {
            ButtonIntent.Default -> Color.Transparent to primary
            ButtonIntent.Critical -> Color.Transparent to error
            ButtonIntent.Neutral -> Color.Transparent to secondary
            ButtonIntent.Success -> Color.Transparent to success
            ButtonIntent.Warning -> Color.Transparent to tertiary
        }
    }

internal fun ThemeColors.buttonColors(
    hierarchy: ButtonHierarchy,
    intent: ButtonIntent,
    state: ButtonState,
): ButtonColors {
    val (container, content) = restColors(hierarchy, intent)
    fun layer(alpha: Float) = content.copy(alpha = alpha).compositeOver(container)
    return when (state) {
        ButtonState.Rest, ButtonState.Loading -> ButtonColors(container, content, null)
        ButtonState.Hovered -> ButtonColors(layer(StateAlpha.HOVER), content, null)
        ButtonState.Pressed -> ButtonColors(layer(StateAlpha.PRESSED), content, null)
        ButtonState.Focused -> ButtonColors(
            layer(StateAlpha.FOCUS),
            content,
            primary.copy(alpha = StateAlpha.FOCUS_RING),
        )
        ButtonState.Disabled -> ButtonColors(
            if (hierarchy == ButtonHierarchy.Tertiary) {
                Color.Transparent
            } else {
                onSurface.copy(alpha = StateAlpha.DISABLED_CONTAINER)
            },
            onSurface.copy(alpha = StateAlpha.DISABLED_CONTENT),
            null,
        )
    }
}

internal fun buttonLevel(hierarchy: ButtonHierarchy, state: ButtonState): ElevationLevel =
    if (hierarchy == ButtonHierarchy.Primary && state in RaisedStates) ElevationLevel.Level1 else ElevationLevel.Level0

private val RaisedStates = setOf(ButtonState.Rest, ButtonState.Hovered, ButtonState.Loading)

internal fun floatingLevel(state: ButtonState): ElevationLevel = when (state) {
    ButtonState.Rest, ButtonState.Focused, ButtonState.Loading -> ElevationLevel.Level3
    ButtonState.Hovered -> ElevationLevel.Level4
    ButtonState.Pressed -> ElevationLevel.Level1
    ButtonState.Disabled -> ElevationLevel.Level0
}

@Immutable
internal data class ButtonLook(
    val hierarchy: ButtonHierarchy,
    val intent: ButtonIntent,
    val height: Dp,
    val cornerRadius: Dp?,
    val floating: Boolean = false,
)

@Composable
internal fun ButtonFrame(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    look: ButtonLook,
    enabled: Boolean,
    loading: Boolean,
    checked: Boolean? = null,
    content: @Composable (Color) -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(enabled, loading, hovered, pressed, focused)
    val colors = AppTheme.colors.buttonColors(look.hierarchy, look.intent, state)
    val container by animateColorAsState(
        colors.container,
        tween(ButtonFoundationDefaults.TRANSITION_MILLIS, easing = EaseOut),
    )
    val shape = look.cornerRadius?.let { RoundedCornerShape(it) } ?: AppTheme.shapes.full
    val level = if (look.floating) floatingLevel(state) else buttonLevel(look.hierarchy, state)
    val interactive = enabled && !loading
    Box(
        modifier = modifier
            .elevationShadow(level, shape, AppTheme.elevation)
            .then(colors.ring?.let { Modifier.focusRing(it, look.cornerRadius) } ?: Modifier)
            .height(look.height)
            .background(container, shape)
            .then(
                if (checked == null) {
                    Modifier.clickable(interactionSource, null, interactive, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.toggleable(checked, interactionSource, null, interactive, Role.Checkbox) { onClick() }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(colors.content)
    }
}

internal fun Modifier.focusRing(color: Color, cornerRadius: Dp?): Modifier = drawBehind {
    val width = AppTheme.focusRingWidth.toPx()
    val half = width / 2
    val radius = (cornerRadius?.toPx() ?: (size.minDimension / 2)) + half
    drawRoundRect(
        color = color,
        topLeft = Offset(-half, -half),
        size = Size(size.width + width, size.height + width),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width),
    )
}

@Composable
internal fun ButtonIcon(icon: Painter, tint: Color, size: Dp) {
    Image(
        painter = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
internal fun Spinner(
    color: Color,
    diameter: Dp,
    stroke: Dp = ButtonFoundationDefaults.SpinnerStroke,
    millis: Int = ButtonFoundationDefaults.SPIN_MILLIS,
    track: Color = Color.Transparent,
) {
    val turn by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(
            tween(millis, easing = LinearEasing),
            RepeatMode.Restart,
        ),
    )
    Canvas(
        Modifier
            .size(diameter)
            .progressSemantics()
            .rotate(turn),
    ) {
        val width = stroke.toPx()
        val ring = Size(size.width - width, size.height - width)
        drawArc(track, 0f, FULL_TURN, false, Offset(width / 2, width / 2), ring, style = Stroke(width))
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = ButtonFoundationDefaults.SPINNER_SWEEP,
            useCenter = false,
            topLeft = Offset(width / 2, width / 2),
            size = ring,
            style = Stroke(width, cap = StrokeCap.Round),
        )
    }
}
