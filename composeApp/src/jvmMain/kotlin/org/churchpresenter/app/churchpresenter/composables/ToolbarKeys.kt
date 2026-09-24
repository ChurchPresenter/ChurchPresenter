package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.churchpresenter.theme.raisedHover
import org.churchpresenter.theme.flatDisabled

private const val DISABLED_INK_ALPHA = 0.35f
private val KEY_INSET = 2.dp
private val KEY_RADIUS = 8.dp
private val OPEN_DOT = 4.dp
private val OPEN_DOT_INSET = 3.dp

/** How a [ToolbarKey] sits in its toolbar. */
enum class ToolbarKeyStyle {
    /** Flat, with a faint tint under the pointer -- an icon inside a raised strip. */
    FLAT,

    /** Always a raised neutral key -- a stepper's − and +. */
    RAISED,

    /**
     * A key that opens a panel: flat until hovered, and raised with a small accent dot under the
     * icon while its panel is open, so the open one can be told apart at a glance.
     */
    PANEL_TOGGLE,
}

/**
 * One toolbar icon with a hover tooltip, drawn in [style]. [open] only matters to
 * [ToolbarKeyStyle.PANEL_TOGGLE]. Disabled, the icon dims and the key lies flat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarKey(
    painter: Painter,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ToolbarKeyStyle = ToolbarKeyStyle.FLAT,
    open: Boolean = false,
    enabled: Boolean = true,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        val palette = elevationPalette()
        val shape = RoundedCornerShape(KEY_RADIUS)
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val pressed by interaction.collectIsPressedAsState()
        val raisedToggle = style == ToolbarKeyStyle.PANEL_TOGGLE && (open || hovered || pressed)
        val surface = when {
            !enabled && style == ToolbarKeyStyle.RAISED -> Modifier.flatDisabled(shape, palette)
            !enabled -> Modifier.clip(shape)
            style == ToolbarKeyStyle.RAISED ->
                Modifier.raised(shape, palette.key, palette, pressed, hovered, lift = 2.dp)
            raisedToggle -> Modifier.raisedHover(shape, palette.key, palette, pressed = pressed, lift = 2.dp)
            // Flat at rest; under the pointer it rises into its own key, so one icon lifts, not the row.
            hovered -> Modifier.raised(shape, palette.key, palette, pressed = pressed, hovered = true, lift = 2.dp)
            else -> Modifier.clip(shape)
        }
        Box(
            modifier = modifier
                .size(buttonSize)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(KEY_INSET)
                .then(surface),
            contentAlignment = Alignment.Center,
        ) {
            // The icon is the key's only laid-out child, so it is centred on its own. The open dot is
            // an overlay pinned to the bottom edge: stacked under the icon in a Column it was always
            // measured, transparent or not, and pushed every panel toggle's icon ~3dp above centre.
            Image(
                painter = painter,
                contentDescription = text,
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(if (enabled) tint else tint.copy(alpha = DISABLED_INK_ALPHA)),
            )
            if (style == ToolbarKeyStyle.PANEL_TOGGLE && open) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = OPEN_DOT_INSET)
                        .size(OPEN_DOT)
                        .clip(CircleShape)
                        .background(palette.accent.bottom)
                )
            }
        }
    }
}
