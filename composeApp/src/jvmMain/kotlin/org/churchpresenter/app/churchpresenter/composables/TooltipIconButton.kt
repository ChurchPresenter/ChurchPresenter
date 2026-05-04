package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.utils.AnalyticsReporter

/**
 * Reusable IconButton with tooltip that appears on hover
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
    TooltipArea(
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
        },
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(0.dp, 16.dp)
        )
    ) {
        IconButton(
            onClick = { AnalyticsReporter.logButtonClick(text); onClick() },
            enabled = enabled,
            modifier = modifier.size(buttonSize),
            colors = colors
        ) {
            Image(
                painter = painter,
                contentDescription = text,
                modifier = Modifier.size(iconSize),
                colorFilter = iconTint?.let { ColorFilter.tint(it) }
            )
        }
    }
}

