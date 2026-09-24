package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import churchpresenter.composeapp.generated.resources.text_style_outline
import churchpresenter.composeapp.generated.resources.text_style_strikethrough
import churchpresenter.composeapp.generated.resources.text_style_underline
import churchpresenter.composeapp.generated.resources.tooltip_backdrop_options
import churchpresenter.composeapp.generated.resources.tooltip_bold
import churchpresenter.composeapp.generated.resources.tooltip_italic
import churchpresenter.composeapp.generated.resources.tooltip_outline
import churchpresenter.composeapp.generated.resources.tooltip_outline_options
import churchpresenter.composeapp.generated.resources.tooltip_shadow
import churchpresenter.composeapp.generated.resources.tooltip_strikethrough
import churchpresenter.composeapp.generated.resources.tooltip_underline
import androidx.compose.material3.Text
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.theme.components.TextStyleToggleButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.elevationPalette
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState

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
 *
 * [outline] and [onOutlineChange] add the glyph outline the same way, ahead of the backdrop: the
 * left half switches the stroke on and off, the caret opens [TextOutlineDialog] for its colour and
 * width. Only the surfaces that store an outline pass it, so it is absent rather than ineffective
 * everywhere else.
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
    outline: TextOutline? = null,
    onOutlineChange: ((TextOutline) -> Unit)? = null,
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
        if (outline != null && onOutlineChange != null) {
            TextOutlineButton(
                outline = outline,
                onOutlineChange = onOutlineChange,
                buttonSize = buttonSize,
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
 * The outline control: a toggle that switches the stroke on, and a caret onto its two settings.
 *
 * Shaped like the backdrop button beside it rather than like a plain toggle, because an outline is
 * never just on -- a black stroke on black lyrics is off as far as anyone watching is concerned --
 * so its colour has to be one click away from the button that turns it on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextOutlineButton(
    outline: TextOutline,
    onOutlineChange: (TextOutline) -> Unit,
    buttonSize: Dp,
) {
    var showDialog by remember { mutableStateOf(false) }
    val isActive = outline.enabled
    Row(verticalAlignment = Alignment.CenterVertically) {
        StyleSegment(
            tooltip = stringResource(Res.string.tooltip_outline),
            isActive = isActive,
            shape = segmentShape(index = 0, count = 2),
            modifier = Modifier.width(buttonSize).height(buttonSize),
            onClick = { onOutlineChange(outline.copy(enabled = !isActive)) },
        ) { content ->
            Text(
                text = stringResource(Res.string.text_style_outline),
                fontSize = (buttonSize.value * OUTLINE_LABEL_SCALE).sp,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
            )
        }
        StyleSegment(
            tooltip = stringResource(Res.string.tooltip_outline_options),
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
        TextOutlineDialog(
            outline = outline,
            onChange = onOutlineChange,
            onDismiss = { showDialog = false },
        )
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
        StyleSegment(
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
        StyleSegment(
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

/** One half of a split style button: a raised key, accent while on, like the toggles beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StyleSegment(
    tooltip: String,
    isActive: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (contentColor: Color) -> Unit,
) {
    val palette = elevationPalette()
    val fill = if (isActive) palette.accent else palette.key
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    TooltipArea(
        tooltip = { BackdropTooltip(tooltip) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        Box(
            modifier = modifier
                // The chip draws a letter and the caret draws an arrow; neither is a name, so the
                // tooltip is also the accessible one.
                .semantics { contentDescription = tooltip }
                .raised(shape, fill, palette, pressed = pressed, hovered = hovered, lift = 2.dp)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            content(fill.ink)
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

/** The letter on the outline toggle, at the size the plain toggles draw theirs. */
private const val OUTLINE_LABEL_SCALE = 0.36f
