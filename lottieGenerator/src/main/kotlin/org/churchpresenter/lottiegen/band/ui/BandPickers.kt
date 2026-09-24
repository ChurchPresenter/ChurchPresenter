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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.first
import org.churchpresenter.lottiegen.ui.Tokens
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

/*
 * The choosers of the band generator: the captioned field a menu drops from, the menu itself,
 * and the grids of chips a short list is picked from in place.
 */

private const val CAPTION_TRACKING = 0.1f
private const val MENU_OFFSET_PX = 46

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
            // The app's sunken dropdown field, as the main generator's [LottieDropdown]: the accent
            // rim while its menu is open.
            .sunken(
                CARD_SHAPE,
                elevationPalette(),
                rim = if (open) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
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
 * How a menu answers the keyboard: ↑ and ↓ move [selected] through [count] rows and apply the
 * row at once through [onSelect], so the preview follows the arrows; [rowHeight] is what the
 * list scrolls by to keep that row in view.
 */
internal class MenuKeys(val selected: Int, val count: Int, val rowHeight: Dp, val onSelect: (Int) -> Unit)

/**
 * The floating list under a picker, as wide as the field it drops from: dismissed by a click
 * anywhere else, Escape or Enter, and past [maxHeight] it scrolls, with a bar to say so. With
 * [keys] the arrows walk the rows.
 */
@Composable
internal fun PopupMenu(
    onDismiss: () -> Unit,
    width: Dp,
    maxHeight: Dp = MENU_MAX_HEIGHT,
    keys: MenuKeys? = null,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // The chosen row is kept on screen as the arrows move it; the first show lands on it too.
    if (keys != null) {
        LaunchedEffect(keys.selected) {
            val rowPx = with(density) { keys.rowHeight.roundToPx() }
            val top = keys.selected * rowPx
            // Not before the list is laid out: on its first frame the viewport is still zero, and
            // the arithmetic below would scroll the first row out of sight to "fit" it.
            val viewport = snapshotFlow { scroll.viewportSize }.first { it > 0 }
            when {
                top < scroll.value -> scroll.animateScrollTo(top)
                top + rowPx > scroll.value + viewport -> scroll.animateScrollTo(top + rowPx - viewport)
            }
        }
    }
    Popup(
        offset = IntOffset(0, MENU_OFFSET_PX),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onKeyEvent = { event -> keys?.let { handleMenuKey(event, it, onDismiss) } ?: false },
    ) {
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
            // Sized to the list, never the other way round: a bar that filled the height would
            // pull the box up to its cap, and a four-line menu would open as a 300dp one.
            Box(Modifier.matchParentSize()) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

/** The arrows step the choice, Enter and Escape close; anything else is left alone. */
private fun handleMenuKey(event: KeyEvent, keys: MenuKeys, onDismiss: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionDown -> {
            if (keys.selected < keys.count - 1) keys.onSelect(keys.selected + 1)
            true
        }
        Key.DirectionUp -> {
            if (keys.selected > 0) keys.onSelect(keys.selected - 1)
            true
        }
        Key.Enter, Key.NumPadEnter, Key.Escape -> {
            onDismiss()
            true
        }
        else -> false
    }
}

private val MENU_MAX_HEIGHT = 300.dp
private val MENU_SCROLLBAR_GUTTER = 8.dp
internal val MENU_ROW_HEIGHT = 32.dp

/** One line of a [PopupMenu]; the chosen one sits on an accent tint and carries a check. */
@Composable
internal fun MenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .height(MENU_ROW_HEIGHT)
            .background(menuRowBackground(selected))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Tokens.Accent else Tokens.OutlineText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SelectedMark(selected)
    }
}

/** The fill under a menu's chosen row: the accent, thinned to a tint the text still reads on. */
@Composable
internal fun menuRowBackground(selected: Boolean): Color =
    if (selected) Tokens.Accent.copy(alpha = SELECTED_ROW_ALPHA) else Color.Transparent

/** The check at the end of a chosen row; the same width left empty otherwise, so labels line up. */
@Composable
internal fun SelectedMark(selected: Boolean) {
    Box(Modifier.size(14.dp)) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(14.dp))
        }
    }
}

private const val SELECTED_ROW_ALPHA = 0.22f
