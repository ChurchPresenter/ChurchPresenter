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
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.text_style_bold
import churchpresenter.composeapp.generated.resources.text_style_border
import churchpresenter.composeapp.generated.resources.text_style_italic
import churchpresenter.composeapp.generated.resources.text_style_line_background
import churchpresenter.composeapp.generated.resources.text_style_shadow
import churchpresenter.composeapp.generated.resources.text_style_strikethrough
import churchpresenter.composeapp.generated.resources.text_style_underline
import churchpresenter.composeapp.generated.resources.tooltip_bold
import churchpresenter.composeapp.generated.resources.tooltip_border
import churchpresenter.composeapp.generated.resources.tooltip_italic
import churchpresenter.composeapp.generated.resources.tooltip_line_background
import churchpresenter.composeapp.generated.resources.tooltip_shadow
import churchpresenter.composeapp.generated.resources.tooltip_strikethrough
import churchpresenter.composeapp.generated.resources.tooltip_underline
import org.churchpresenter.core.models.text.TextBackdrop
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable

/**
 * A row of toggle buttons for text style: Bold, Italic, Underline, Shadow.
 * Each button toggles its style independently.
 *
 * Strikethrough is offered only when [onStrikethroughChange] is given, and the shadow button only
 * when [showShadow] is left on. Both default to the original four-button row, so a caller that
 * wants neither is unchanged; the Bible settings tab passes a strikethrough handler and turns the
 * shadow button off, having a labelled shadow row of its own.
 *
 * The last two buttons -- the border box and the line background -- appear together when
 * [backdrop] and [onBackdropChange] are given. Neither is a plain toggle: each turns its half on
 * and opens a dialog holding that half's colour and measurements, so a panel gains the feature
 * without gaining a single row. Turning one off again is the switch at the top of its dialog.
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
    modifier: Modifier = Modifier,
    buttonSize: Dp = 28.dp,
    strikethrough: Boolean = false,
    onStrikethroughChange: ((Boolean) -> Unit)? = null,
    showShadow: Boolean = true,
    backdrop: TextBackdrop? = null,
    onBackdropChange: ((TextBackdrop) -> Unit)? = null,
) {
    var showBorderDialog by remember { mutableStateOf(false) }
    var showLineBackgroundDialog by remember { mutableStateOf(false) }
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
            buttonSize = buttonSize,
            onClick = { onBoldChange(!bold) }
        )
        TextStyleToggleButton(
            label = stringResource(Res.string.text_style_italic),
            tooltip = stringResource(Res.string.tooltip_italic),
            isActive = italic,
            fontStyle = FontStyle.Italic,
            buttonSize = buttonSize,
            onClick = { onItalicChange(!italic) }
        )
        TextStyleToggleButton(
            label = stringResource(Res.string.text_style_underline),
            tooltip = stringResource(Res.string.tooltip_underline),
            isActive = underline,
            textDecoration = TextDecoration.Underline,
            buttonSize = buttonSize,
            onClick = { onUnderlineChange(!underline) }
        )
        if (onStrikethroughChange != null) {
            TextStyleToggleButton(
                label = stringResource(Res.string.text_style_strikethrough),
                tooltip = stringResource(Res.string.tooltip_strikethrough),
                isActive = strikethrough,
                textDecoration = TextDecoration.LineThrough,
                buttonSize = buttonSize,
                onClick = { onStrikethroughChange(!strikethrough) }
            )
        }
        if (showShadow) {
            TextStyleToggleButton(
                label = stringResource(Res.string.text_style_shadow),
                tooltip = stringResource(Res.string.tooltip_shadow),
                isActive = shadow,
                buttonSize = buttonSize,
                onClick = { onShadowChange(!shadow) }
            )
        }
        if (backdrop != null && onBackdropChange != null) {
            TextStyleToggleButton(
                label = stringResource(Res.string.text_style_border),
                tooltip = stringResource(Res.string.tooltip_border),
                isActive = backdrop.border,
                buttonSize = buttonSize,
                boxedLabel = true,
                onClick = {
                    // Switching it on is what the click means; the dialog is where it is shaped.
                    if (!backdrop.border) onBackdropChange(backdrop.copy(border = true))
                    showBorderDialog = true
                }
            )
            TextStyleToggleButton(
                label = stringResource(Res.string.text_style_line_background),
                tooltip = stringResource(Res.string.tooltip_line_background),
                isActive = backdrop.lineBackground,
                buttonSize = buttonSize,
                filledLabel = true,
                onClick = {
                    if (!backdrop.lineBackground) onBackdropChange(backdrop.copy(lineBackground = true))
                    showLineBackgroundDialog = true
                }
            )
        }
    }
    if (backdrop != null && onBackdropChange != null) {
        if (showBorderDialog) {
            TextBorderDialog(
                backdrop = backdrop,
                onChange = onBackdropChange,
                onDismiss = { showBorderDialog = false },
            )
        }
        if (showLineBackgroundDialog) {
            LineBackgroundDialog(
                backdrop = backdrop,
                onChange = onBackdropChange,
                onDismiss = { showLineBackgroundDialog = false },
            )
        }
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
    buttonSize: Dp = 28.dp,
    /** Draws the outline the button stands for around its own letter, rather than naming it. */
    boxedLabel: Boolean = false,
    /** Draws the band the button stands for behind its own letter. */
    filledLabel: Boolean = false,
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
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        Surface(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = if (isActive) activeBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() },
            color = if (isActive) activeBackground else inactiveBackground,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val content = if (isActive) activeContent else inactiveContent
                val container = if (isActive) activeBackground else inactiveBackground
                Text(
                    text = label,
                    fontSize = (buttonSize.value * 0.36f).sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    // The banded button knocks its letter out of a solid block, the way a
                    // highlighter icon does — a translucent wash behind it read as "disabled"
                    // rather than as a background.
                    color = if (filledLabel) container else content,
                    maxLines = 1,
                    modifier = when {
                        boxedLabel -> Modifier
                            .border(1.dp, content, RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp)
                        filledLabel -> Modifier
                            .background(content, RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp)
                        else -> Modifier
                    },
                )
            }
        }
    }
}

