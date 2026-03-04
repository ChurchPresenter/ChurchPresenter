package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    singleLine: Boolean = false,
    onItemSelected: (Int, String) -> Unit,
    onItemDoubleClicked: ((Int, String) -> Unit)? = null
) {
    val listState = rememberLazyListState()

    // Scroll to selected item when selection changes
    LaunchedEffect(selectedIndex, list.size) {
        if (selectedIndex >= 0 && selectedIndex < list.size) {
            listState.animateScrollToItem(selectedIndex)
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
                key = { index, _ -> "$index-${list.hashCode()}" }
            ) { index, item ->
                val isSelected = index == selectedIndex
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
                        .combinedClickable(
                            onClick = {
                                if (index >= 0 && index < list.size) {
                                    onItemSelected(index, item)
                                }
                            },
                            onDoubleClick = {
                                if (index >= 0 && index < list.size) {
                                    onItemSelected(index, item)
                                    onItemDoubleClicked?.invoke(index, item)
                                }
                            }
                        )
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