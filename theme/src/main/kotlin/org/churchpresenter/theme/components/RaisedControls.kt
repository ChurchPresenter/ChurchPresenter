package org.churchpresenter.theme.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ElevationPalette
import org.churchpresenter.theme.RaisedFill
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.flatDisabled
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken

/**
 * `Button` in the elevated look: a raised key in its container color, top-lit, with a drop shadow
 * tinted to match. It lifts on hover and presses in on click; disabled it lies flat and dimmed.
 * Takes the same parameters as Material's `Button`, so a call site only swaps the name.
 */
@Composable
fun RaisedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(BUTTON_RADIUS),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = elevationPalette()
    RaisedButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        fill = palette.tinted(colors.containerColor, colors.contentColor),
        ink = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * `OutlinedButton` in the elevated look: a neutral raised key rather than an outline. A caller that
 * fills it (a selected state) gets a raised key in that color instead, and a caller that passes a
 * [border] keeps it drawn over the key.
 */
@Composable
fun KeyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(BUTTON_RADIUS),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    /** A palette fill to use as it is -- [ElevationPalette.danger], say -- in place of [colors]. */
    fill: RaisedFill? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = elevationPalette()
    val filled = colors.containerColor.alpha > 0f
    RaisedButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        fill = fill ?: if (filled) palette.tinted(colors.containerColor, colors.contentColor) else palette.key,
        ink = when {
            !enabled -> colors.disabledContentColor
            fill != null -> fill.ink
            else -> colors.contentColor
        },
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * `TextButton` in the elevated look: no surface at rest, a faint outline under the pointer and an
 * inner shade while pressed -- the quiet action beside a raised one, like Cancel beside OK.
 */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(BUTTON_RADIUS),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (enabled) colors.contentColor else colors.disabledContentColor
    val filled = colors.containerColor.alpha > 0f
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .then(if (filled) Modifier.background(colors.containerColor) else Modifier)
            .then(
                when {
                    !enabled -> Modifier
                    pressed -> Modifier.sunken(
                        shape,
                        palette,
                        fill = palette.wellBottom.copy(alpha = GHOST_PRESS_ALPHA),
                    )
                    hovered -> Modifier.border(1.dp, palette.wellBorder, shape)
                    else -> Modifier
                }
            )
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(ButtonDefaults.MinWidth, ButtonDefaults.MinHeight)
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun RaisedButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    fill: RaisedFill,
    ink: Color,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (enabled) {
                    Modifier.raised(shape, fill, palette, pressed, hovered)
                } else {
                    Modifier.flatDisabled(shape, palette)
                }
            )
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(ButtonDefaults.MinWidth, ButtonDefaults.MinHeight)
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/**
 * `Switch` in the elevated look: a sunken track that fills with the accent when on, and a raised
 * knob. Same footprint and parameters as Material's `Switch`.
 */
@Composable
fun RaisedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val knobOffset by animateDpAsState(if (checked) SWITCH_TRAVEL else 0.dp)
    val trackShape = CircleShape
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(toggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SWITCH_WIDTH, SWITCH_HEIGHT)
                .then(
                    if (checked) {
                        Modifier
                            .clip(trackShape)
                            .background(Brush.verticalGradient(listOf(palette.accent.bottom, palette.accent.top)))
                            .border(1.dp, palette.wellBorder, trackShape)
                    } else {
                        Modifier.sunken(trackShape, palette)
                    }
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = SWITCH_INSET + knobOffset)
                    .size(SWITCH_KNOB)
                    .raised(CircleShape, knobFill(palette, checked), palette, lift = 2.dp)
            )
        }
    }
}

/** On dark the knob takes the accent's ink when on and a mid grey when off; on light it is white. */
@Composable
private fun knobFill(palette: ElevationPalette, checked: Boolean): RaisedFill {
    if (!palette.isDark) return palette.key
    val base = if (checked) palette.accent.ink else MaterialTheme.colorScheme.onSurfaceVariant
    return palette.tinted(base, base)
}

private const val DISABLED_ALPHA = 0.45f
private const val GHOST_PRESS_ALPHA = 0.6f
private val BUTTON_RADIUS = 10.dp
private val SWITCH_WIDTH = 44.dp
private val SWITCH_HEIGHT = 24.dp
private val SWITCH_INSET = 3.dp
private val SWITCH_KNOB = 18.dp
private val SWITCH_TRAVEL = 20.dp
