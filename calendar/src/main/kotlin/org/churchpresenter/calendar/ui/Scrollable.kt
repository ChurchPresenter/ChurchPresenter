package org.churchpresenter.calendar.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The scrollbars this is a desktop window, not a phone.
 *
 * A `LazyColumn` on desktop shows no scrollbar of its own, so a list that overflows looks exactly
 * like a list that ends — there is nothing to say a thousand songs continue below the fold. Compose
 * Desktop supplies `VerticalScrollbar`; these wrap it so every scrolling surface in the window gets
 * one the same way, with the same gutter reserved so the bar never sits on top of the content.
 */
private val SCROLLBAR_GUTTER = 10.dp

/** A lazy list with a scrollbar down its right edge. */
@Composable
fun ScrollableList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(
            state = state,
            verticalArrangement = verticalArrangement,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
            content = content,
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

/** A lazy grid with a scrollbar down its right edge — the Bible's book, chapter and verse grids. */
@Composable
fun ScrollableGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyGridScope.() -> Unit,
) {
    Box(modifier) {
        LazyVerticalGrid(
            columns = columns,
            state = state,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
            content = content,
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

/** A plain scrolling column with a scrollbar — for bodies that are not lists. */
@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier) {
        Column(
            verticalArrangement = verticalArrangement,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state)
                .padding(contentPadding)
                .padding(end = SCROLLBAR_GUTTER),
            content = content,
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
