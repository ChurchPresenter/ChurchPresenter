package org.churchpresenter.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.flatDisabled
import org.churchpresenter.theme.raised

private val ICON_BUTTON_SIZE = 40.dp
private val ICON_KEY_INSET = 3.dp
private val ICON_KEY_RADIUS = 8.dp

/**
 * `IconButton` in the elevated look: a flat icon at rest that rises into a raised key under the
 * pointer and presses in on click. A caller that fills it gets a raised key in that color instead.
 */
@Composable
fun KeyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ICON_KEY_RADIUS),
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val filled = colors.containerColor.alpha > 0f
    val surface = when {
        !enabled && filled -> Modifier.flatDisabled(shape, palette)
        !enabled -> Modifier.clip(shape)
        filled -> Modifier.raised(
            shape, palette.tinted(colors.containerColor, colors.contentColor), palette, pressed, hovered, lift = 2.dp
        )
        hovered || pressed -> Modifier.raised(shape, palette.key, palette, pressed = pressed, lift = 2.dp)
        else -> Modifier.clip(shape)
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ICON_BUTTON_SIZE)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(ICON_KEY_INSET)
            .then(surface),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (enabled) colors.contentColor else colors.disabledContentColor
        CompositionLocalProvider(LocalContentColor provides ink, content = content)
    }
}

/**
 * `FilledIconButton` / `OutlinedIconButton` in the elevated look: always a raised key -- in the
 * container color when there is one, the neutral key when it is transparent.
 */
@Composable
fun RaisedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ICON_KEY_RADIUS),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (colors.containerColor.alpha > 0f) {
        palette.tinted(colors.containerColor, colors.contentColor)
    } else {
        palette.key
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ICON_BUTTON_SIZE)
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
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (enabled) colors.contentColor else colors.disabledContentColor
        CompositionLocalProvider(LocalContentColor provides ink, content = content)
    }
}
