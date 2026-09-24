package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised

private val ICON_KEY_RADIUS = 8.dp
private val ICON_KEY_INSET = 2.dp


/**
 * Reusable IconButton with tooltip that appears on hover, hidden when partially off-screen.
 *
 * Flat until hovered, when it rises into a raised key; it presses in on click.
 *
 * [iconTint] defaults to [colors]' content colour — so an icon is drawn in the theme's content
 * colour, and dimmed by the *disabled* one when `enabled` is false.
 * Every drawable in `composeResources/drawable` bakes `android:fillColor="#FF000000"`, so an
 * untinted `Image` painted a hard black glyph regardless of theme or state: invisible against the
 * dark theme, and a disabled button that looked exactly as live as an enabled one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TooltipIconButton(
    painter: Painter,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 20.dp,
    buttonSize: Dp = 36.dp,
    iconTint: Color? = null,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors()
) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        val palette = elevationPalette()
        val shape = RoundedCornerShape(ICON_KEY_RADIUS)
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val pressed by interaction.collectIsPressedAsState()
        val ink = if (enabled) colors.contentColor else colors.disabledContentColor
        Box(
            modifier = modifier
                .size(buttonSize)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(ICON_KEY_INSET)
                .then(
                    if (enabled && (hovered || pressed)) {
                        Modifier.raised(shape, palette.key, palette, pressed = pressed, hovered = hovered, lift = 2.dp)
                    } else {
                        Modifier.clip(shape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painter,
                contentDescription = text,
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconTint ?: ink)
            )
        }
    }
}

