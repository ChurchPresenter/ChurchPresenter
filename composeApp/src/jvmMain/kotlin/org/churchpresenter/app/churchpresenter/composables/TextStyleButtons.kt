package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.text_style_bold
import churchpresenter.composeapp.generated.resources.text_style_italic
import churchpresenter.composeapp.generated.resources.text_style_shadow
import churchpresenter.composeapp.generated.resources.text_style_underline
import churchpresenter.composeapp.generated.resources.tooltip_bold
import churchpresenter.composeapp.generated.resources.tooltip_italic
import churchpresenter.composeapp.generated.resources.tooltip_shadow
import churchpresenter.composeapp.generated.resources.tooltip_underline
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable

/**
 * A row of toggle buttons for text style: Bold, Italic, Underline, Shadow.
 * Each button toggles its style independently.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextStyleButtons(
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    shadow: Boolean,
    onBoldChange: (Boolean) -> Unit,
    onItalicChange: (Boolean) -> Unit,
    onUnderlineChange: (Boolean) -> Unit,
    onShadowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextStyleToggleButton(
            label = stringResource(Res.string.text_style_bold),
            tooltip = stringResource(Res.string.tooltip_bold),
            isActive = bold,
            fontWeight = FontWeight.Bold,
            onClick = { onBoldChange(!bold) }
        )
        TextStyleToggleButton(
            label = stringResource(Res.string.text_style_italic),
            tooltip = stringResource(Res.string.tooltip_italic),
            isActive = italic,
            fontStyle = FontStyle.Italic,
            onClick = { onItalicChange(!italic) }
        )
        TextStyleToggleButton(
            label = stringResource(Res.string.text_style_underline),
            tooltip = stringResource(Res.string.tooltip_underline),
            isActive = underline,
            textDecoration = TextDecoration.Underline,
            onClick = { onUnderlineChange(!underline) }
        )
        TextStyleToggleButton(
            label = stringResource(Res.string.text_style_shadow),
            tooltip = stringResource(Res.string.tooltip_shadow),
            isActive = shadow,
            onClick = { onShadowChange(!shadow) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextStyleToggleButton(
    label: String,
    tooltip: String,
    isActive: Boolean,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    textDecoration: TextDecoration? = null,
    onClick: () -> Unit
) {
    val activeBackground = MaterialTheme.colorScheme.primary
    val inactiveBackground = MaterialTheme.colorScheme.surfaceVariant
    val activeContent = MaterialTheme.colorScheme.onPrimary
    val inactiveContent = MaterialTheme.colorScheme.onSurfaceVariant

    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = tooltip,
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
        Surface(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = 1.dp,
                    color = if (isActive) activeBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable { onClick() },
            color = if (isActive) activeBackground else inactiveBackground,
            shape = RoundedCornerShape(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    color = if (isActive) activeContent else inactiveContent,
                    maxLines = 1
                )
            }
        }
    }
}

