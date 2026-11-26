package org.churchpresenter.hosanna.composables.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.defaults.ListDefaults
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.listRowColors
import org.churchpresenter.hosanna.composables.model.NavigationItem
import org.churchpresenter.hosanna.composables.model.jumpTarget
import org.churchpresenter.hosanna.composables.model.listKey
import org.churchpresenter.hosanna.composables.model.moveIndex

/**
 * A list to pick one item from, such as Bible books or chapters. ↑ ↓, Home, End and the page keys move the
 * pick, and typing jumps to the first match.
 *
 * @param items the lines.
 * @param selectedKey the key of the picked line, or null.
 * @param onSelect called with the key of the line picked.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the list's focus.
 * @param centered centers the text, for narrow lists of numbers.
 */
@Composable
fun NavigationList(
    items: List<NavigationItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    centered: Boolean = false,
) {
    val listFocused by interactionSource.collectIsFocusedAsState()
    val state = rememberLazyListState()
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }
    val current by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) {
            delay(ListDefaults.TYPE_RESET_MILLIS)
            typed = ""
        }
    }
    LaunchedEffect(selectedIndex) {
        val shown = state.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex && it.offset >= 0 }
        if (selectedIndex >= 0 && !shown) state.scrollToItem(selectedIndex)
    }
    LazyColumn(
        state = state,
        modifier = modifier
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val key = listKey(event.key)
                val target = when {
                    key != null -> moveIndex(current, key, items.size, ListDefaults.PAGE)
                    event.isCtrlPressed || event.isMetaPressed || event.isAltPressed -> null
                    event.utf16CodePoint > ' '.code -> {
                        typed += event.utf16CodePoint.toChar()
                        jumpTarget(items.map { it.label }, typed, maxOf(current, 0))
                    }
                    else -> null
                }
                target?.takeIf { it >= 0 }?.let { select(items[it].key) }
                key != null || target != null
            },
        verticalArrangement = Arrangement.spacedBy(ListDefaults.NavigationGap),
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            NavigationRow(item, index == selectedIndex, listFocused && index == selectedIndex, centered) {
                select(item.key)
            }
        }
    }
}

@Composable
private fun NavigationRow(
    item: NavigationItem,
    selected: Boolean,
    focused: Boolean,
    centered: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val state = buttonState(true, false, hovered, pressed, focused)
    val colors = AppTheme.colors.listRowColors(selected, live = false, marker = true, state = state)
    val typography = AppTheme.typography
    RowFrame(colors, ListDefaults.NavigationRow, dragging = false, modifier = Modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ListDefaults.NavigationRow)
                .semantics { this.selected = selected }
                .clickable(source, null, role = Role.Tab, onClick = onClick)
                .padding(
                    start = if (selected && !centered) ListDefaults.SelectedPadding else ListDefaults.RowPadding,
                    end = ListDefaults.RowPadding,
                ),
            contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
        ) {
            BasicText(
                item.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = (if (selected) typography.bodyStrong else typography.body).copy(
                    color = colors.content,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                ),
            )
        }
    }
}
