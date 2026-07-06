package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import org.jetbrains.compose.resources.painterResource

/**
 * Uniform action-row icon button used across every tab's primary action row
 * (Go Live, Add to Schedule, and their row-mates). Mirrors the LowerThird tab's look:
 * a 34dp FilledIconButton with rounded corners and a hover tooltip.
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
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(buttonSize),
            shape = RoundedCornerShape(8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor
            )
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = tooltipText, modifier = Modifier.size(iconSize))
            } else if (painter != null) {
                Icon(painter, contentDescription = tooltipText, modifier = Modifier.size(iconSize))
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
        modifier = if (dimmed) modifier.alpha(0.5f) else modifier
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
