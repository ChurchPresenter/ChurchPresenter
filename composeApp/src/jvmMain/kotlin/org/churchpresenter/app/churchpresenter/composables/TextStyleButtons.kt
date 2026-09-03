package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.arrow_down
import churchpresenter.composeapp.generated.resources.backdrop_title
import churchpresenter.composeapp.generated.resources.text_style_backdrop
import churchpresenter.composeapp.generated.resources.text_style_bold
import churchpresenter.composeapp.generated.resources.text_style_italic
import churchpresenter.composeapp.generated.resources.text_style_shadow
import churchpresenter.composeapp.generated.resources.text_style_strikethrough
import churchpresenter.composeapp.generated.resources.text_style_underline
import churchpresenter.composeapp.generated.resources.tooltip_backdrop_options
import churchpresenter.composeapp.generated.resources.tooltip_bold
import churchpresenter.composeapp.generated.resources.tooltip_italic
import churchpresenter.composeapp.generated.resources.tooltip_shadow
import churchpresenter.composeapp.generated.resources.tooltip_strikethrough
import churchpresenter.composeapp.generated.resources.tooltip_underline
import org.churchpresenter.core.models.text.TextBackdrop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A row of toggle buttons for text style: Bold, Italic, Underline, Shadow.
 * Each button toggles its style independently.
 *
 * Strikethrough is offered only when [onStrikethroughChange] is given, and the shadow button only
 * when [showShadow] is left on. Both default to the original four-button row, so a caller that
 * wants neither is unchanged; the Bible settings tab passes a strikethrough handler and turns the
 * shadow button off, having a labelled shadow row of its own.
 *
 * Passing [backdrop] and [onBackdropChange] adds the text-backing control at the end: one split
 * button, not two toggles. Its left half turns the last look on and off the way Bold does, and its
 * caret opens [TextBackdropDialog], where the fill behind the lines and the box around the block
 * are picked together.
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
            TextBackdropButton(
                backdrop = backdrop,
                onBackdropChange = onBackdropChange,
                buttonSize = buttonSize,
            )
        }
    }
}

/**
 * The text-backing control: a chip of the current look, and a caret onto the dialog behind it.
 *
 * Clicking the chip flips the last style off and on without opening anything, so it behaves like
 * the four buttons beside it; the caret is for changing the look rather than for having one. That
 * split is why the whole feature fits where two toggles used to, and why the button reports its
 * state — the chip is drawn with the settings in force, so a maroon plate looks maroon here.
 *
 * The last style is remembered rather than stored: it only has to survive the panel being open, and
 * a backdrop that has never been on falls back to a plain fill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextBackdropButton(
    backdrop: TextBackdrop,
    onBackdropChange: (TextBackdrop) -> Unit,
    buttonSize: Dp,
) {
    var showDialog by remember { mutableStateOf(false) }
    val mode = backdrop.mode
    var lastMode by remember {
        mutableStateOf(if (mode == TextBackdropMode.OFF) TextBackdropMode.FILL else mode)
    }
    LaunchedEffect(mode) { if (mode != TextBackdropMode.OFF) lastMode = mode }

    val isActive = mode != TextBackdropMode.OFF
    val title = stringResource(Res.string.backdrop_title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackdropSegment(
            tooltip = title,
            isActive = isActive,
            shape = segmentShape(index = 0, count = 2),
            modifier = Modifier.width(buttonSize + CHIP_SEGMENT_EXTRA).height(buttonSize),
            onClick = {
                onBackdropChange(backdrop.withMode(if (isActive) TextBackdropMode.OFF else lastMode))
            },
        ) { content ->
            TextBackdropChip(
                backdrop = backdrop,
                emptyOutline = content.copy(alpha = OUTLINE_ALPHA),
                emptyInk = content,
                modifier = Modifier.width(CHIP_WIDTH).height(CHIP_HEIGHT),
                label = stringResource(Res.string.text_style_backdrop),
                fontSize = (buttonSize.value * CHIP_LABEL_SCALE).sp,
            )
        }
        BackdropSegment(
            tooltip = stringResource(Res.string.tooltip_backdrop_options),
            isActive = isActive,
            shape = segmentShape(index = 1, count = 2),
            modifier = Modifier.width(CARET_SEGMENT_WIDTH).height(buttonSize),
            onClick = { showDialog = true },
        ) { content ->
            Icon(
                painter = painterResource(Res.drawable.arrow_down),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(CARET_SIZE),
            )
        }
    }
    if (showDialog) {
        TextBackdropDialog(
            backdrop = backdrop,
            onChange = onBackdropChange,
            onDismiss = { showDialog = false },
        )
    }
}

/** One half of the split button, coloured exactly as the toggles beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackdropSegment(
    tooltip: String,
    isActive: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (contentColor: Color) -> Unit,
) {
    val activeBackground = MaterialTheme.colorScheme.primary
    val contentColor =
        if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    TooltipArea(
        tooltip = { BackdropTooltip(tooltip) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        Surface(
            modifier = modifier
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = if (isActive) {
                        activeBackground
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
                    },
                    shape = shape,
                )
                .clickable { onClick() },
            color = if (isActive) activeBackground else MaterialTheme.colorScheme.surfaceVariant,
            shape = shape,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content(contentColor)
            }
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
    onClick: () -> Unit
) {
    val activeBackground = MaterialTheme.colorScheme.primary
    val inactiveBackground = MaterialTheme.colorScheme.surfaceVariant
    val activeContent = MaterialTheme.colorScheme.onPrimary
    val inactiveContent = MaterialTheme.colorScheme.onSurfaceVariant

    TooltipArea(
        tooltip = { BackdropTooltip(tooltip) },
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
                    color = if (isActive) {
                        activeBackground
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
                    },
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
                Text(
                    text = label,
                    fontSize = (buttonSize.value * 0.36f).sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    color = if (isActive) activeContent else inactiveContent,
                    maxLines = 1,
                )
            }
        }
    }
}

/** How much wider than a plain toggle the chip half is, so the swatch is not cramped. */
private val CHIP_SEGMENT_EXTRA = 6.dp
private val CHIP_WIDTH = 22.dp
private val CHIP_HEIGHT = 15.dp
private val CARET_SEGMENT_WIDTH = 16.dp
private val CARET_SIZE = 10.dp
private const val CHIP_LABEL_SCALE = 0.32f
