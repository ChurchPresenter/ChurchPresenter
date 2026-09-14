package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.lottiegen.ui.Tokens

/*
 * The choosers of the band generator: the captioned field a menu drops from, the menu itself,
 * and the grids of chips a short list is picked from in place.
 */

private const val CAPTION_TRACKING = 0.1f
private const val MENU_OFFSET_PX = 46

/** A captioned field showing a picked value, with the menu that picks it dropping from below. */
@Composable
internal fun <T> PickerField(
    caption: String,
    value: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        CaptionedButton(caption, value, open, onClick = { open = !open })
        if (open) {
            PopupMenu(onDismiss = { open = false }, width = anchorWidth) {
                options.forEach { option ->
                    MenuRow(labelOf(option), option == selected) {
                        onPick(option)
                        open = false
                    }
                }
            }
        }
    }
}

/** The caption-over-value button a picker opens from. */
@Composable
internal fun CaptionedButton(
    caption: String,
    value: String,
    open: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CARD_SHAPE)
            .background(Tokens.FieldBg)
            .border(1.dp, if (open) Tokens.FieldBorderHover else Tokens.FieldBorder, CARD_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                caption.uppercase(), fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (8.5f * CAPTION_TRACKING).sp, color = Tokens.HintText, maxLines = 1,
            )
            Text(
                value, fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                color = Tokens.PrimaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        Icon(
            Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Tokens.Caret,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * The floating list under a picker, as wide as the field it drops from: dismissed by a click
 * anywhere else, and past [maxHeight] it scrolls, with a bar to say so.
 */
@Composable
internal fun PopupMenu(
    onDismiss: () -> Unit,
    width: Dp,
    maxHeight: Dp = MENU_MAX_HEIGHT,
    content: @Composable () -> Unit,
) {
    Popup(
        offset = IntOffset(0, MENU_OFFSET_PX),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val scroll = rememberScrollState()
        Box(
            modifier = Modifier
                .width(width)
                .heightIn(max = maxHeight)
                .clip(MENU_SHAPE)
                .background(Tokens.CardBg)
                .border(1.dp, Tokens.CardBorderOpen, MENU_SHAPE)
                .padding(4.dp),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = MENU_SCROLLBAR_GUTTER)) { content() }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

private val MENU_MAX_HEIGHT = 300.dp
private val MENU_SCROLLBAR_GUTTER = 8.dp

/** One line of a [PopupMenu]. */
@Composable
internal fun MenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color = if (selected) Tokens.Accent else Tokens.OutlineText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Tokens.HeadBgOpen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    )
}

/** One cell of a choice grid: filled with the accent when it is the choice, outlined otherwise. */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(29.dp)
            .clip(FIELD_SHAPE)
            .background(if (selected) Tokens.Accent else Tokens.FieldBg)
            .border(1.dp, if (selected) Tokens.Accent else Tokens.FieldBorder, FIELD_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Tokens.OnAccent else Tokens.LabelText, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A grid of [ChoiceChip]s, [columns] to a row. */
@Composable
internal fun <T> ChoiceGrid(options: List<T>, selected: T, columns: Int, labelOf: (T) -> String, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { option ->
                    ChoiceChip(labelOf(option), option == selected, { onPick(option) }, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

