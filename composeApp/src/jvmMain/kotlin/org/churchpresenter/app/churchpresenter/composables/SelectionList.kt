package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// Original version that passes the item
@Composable
fun SelectionList(
    modifier: Modifier = Modifier,
    list: List<String>,
    selectedIndex: Int = 0,
    onItemSelected: (String) -> Unit
) {
    SelectionListWithIndex(
        modifier = modifier,
        list = list,
        selectedIndex = selectedIndex,
        onItemSelected = { _, item ->
            onItemSelected(item)
        }
    )
}

// New version that passes both index and item
@Composable
fun SelectionListWithIndex(
    modifier: Modifier = Modifier,
    list: List<String>,
    selectedIndex: Int = 0,
    selectedIndices: Set<Int>? = null,
    singleLine: Boolean = false,
    onItemSelected: (Int, String) -> Unit,
    onItemDoubleClicked: ((Int, String) -> Unit)? = null,
    /** Called when the item is clicked with Ctrl (Windows/Linux) or Cmd (macOS) held. */
    onItemCtrlClicked: ((Int, String) -> Unit)? = null,
    /** Called when the item is clicked with Shift held. */
    onItemShiftClicked: ((Int, String) -> Unit)? = null,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    )

    // Scroll only when the selected item is not visible
    LaunchedEffect(selectedIndex, list.size) {
        if (selectedIndex >= 0 && selectedIndex < list.size) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isVisible = visibleItems.any { it.index == selectedIndex }
            if (!isVisible) {
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .fillMaxHeight()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp)
        ) {
            itemsIndexed(
                items = list,
                key = { index, item -> "$index-$item" }
            ) { index, item ->
                val isSelected = if (selectedIndices != null) selectedIndices.contains(index) else index == selectedIndex
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface
                        )
                        .pointerInput(index, list.size) {
                            var lastClickTime = 0L
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Release &&
                                        index >= 0 && index < list.size
                                    ) {
                                        val now = System.currentTimeMillis()
                                        val isDouble = now - lastClickTime < 300L
                                        lastClickTime = now
                                        val mods = event.keyboardModifiers
                                        when {
                                            isDouble && onItemDoubleClicked != null -> {
                                                onItemSelected(index, item)
                                                onItemDoubleClicked.invoke(index, item)
                                            }
                                            (mods.isCtrlPressed || mods.isMetaPressed) && onItemCtrlClicked != null ->
                                                onItemCtrlClicked.invoke(index, item)
                                            mods.isShiftPressed && onItemShiftClicked != null ->
                                                onItemShiftClicked.invoke(index, item)
                                            else -> onItemSelected(index, item)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState)
        )
    }
}