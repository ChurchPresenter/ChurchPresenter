package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised

private const val DIMMED_ALPHA = 0.5f

/**
 * Uniform action-row icon button used across every tab's primary action row
 * (Go Live, Add to Schedule, and their row-mates). Mirrors the LowerThird tab's look:
 * a 34dp raised key in its own color, with rounded corners and a hover tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIconButton(
    onClick: () -> Unit,
    tooltipText: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor: Color = MaterialTheme.colorScheme.outlineVariant,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 16.dp,
    modifier: Modifier = Modifier,
    tooltipContent: (@Composable () -> Unit)? = null
) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                if (tooltipContent != null) {
                    tooltipContent()
                } else {
                    Text(
                        text = tooltipText,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        val palette = elevationPalette()
        val shape = RoundedCornerShape(8.dp)
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val pressed by interaction.collectIsPressedAsState()
        val ink = if (enabled) contentColor else disabledContentColor
        Box(
            modifier = modifier
                .size(buttonSize)
                .then(
                    if (enabled) {
                        Modifier.raised(shape, palette.tinted(containerColor, contentColor), palette, pressed, hovered)
                    } else {
                        Modifier.clip(shape).background(disabledContainerColor)
                    }
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = tooltipText, tint = ink, modifier = Modifier.size(iconSize))
            } else if (painter != null) {
                Icon(painter, contentDescription = tooltipText, tint = ink, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** Uniform "Go Live" button — matches LowerThird's action row. */
@Composable
fun GoLiveButton(
    onClick: () -> Unit,
    tooltipText: String,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    ActionIconButton(
        onClick = onClick,
        tooltipText = tooltipText,
        icon = Icons.Default.Tv,
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = if (dimmed) modifier.alpha(DIMMED_ALPHA) else modifier
    )
}

/**
 * **Save preset** — sits to the left of [AddToScheduleButton] on the tabs whose item can be saved
 * for later: pictures, presentation, media, announcements and canvas. What it saves is the same
 * item Add to Schedule would add, kept in `presets.json` for the Calendar Manager to pick up.
 */
@Composable
fun SavePresetButton(
    onClick: () -> Unit,
    tooltipText: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ActionIconButton(
        onClick = onClick,
        tooltipText = tooltipText,
        icon = Icons.Filled.BookmarkAdd,
        enabled = enabled,
        // Filled like its neighbours: surfaceVariant read as the disabled state next to them.
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        modifier = modifier
    )
}

/** Uniform "Add to Schedule" button — matches LowerThird's action row. */
@Composable
fun AddToScheduleButton(
    onClick: () -> Unit,
    tooltipText: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ActionIconButton(
        onClick = onClick,
        tooltipText = tooltipText,
        painter = painterResource(Res.drawable.ic_playlist_add),
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        modifier = modifier
    )
}
