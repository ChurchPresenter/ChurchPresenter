package songlibrary.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.models.songs.SongField
import core.models.songs.SongItem
import core.models.songs.SortColumn
import org.jetbrains.compose.resources.stringResource
import songlibrary.SongLibraryState
import songlibrary.generated.resources.Res
import songlibrary.generated.resources.column_author
import songlibrary.generated.resources.column_ccli
import songlibrary.generated.resources.column_composer
import songlibrary.generated.resources.column_number
import songlibrary.generated.resources.column_secondary_title
import songlibrary.generated.resources.column_song_book
import songlibrary.generated.resources.column_title
import songlibrary.generated.resources.column_tune
import songlibrary.generated.resources.delete_song
import songlibrary.generated.resources.edit_song
import songlibrary.generated.resources.empty_title
import songlibrary.generated.resources.library_empty
import songlibrary.generated.resources.reset_filters

// The grid itself: the header row, one row per song, and what stands in when there are none.

@Composable
internal fun SongTable(
    state: SongLibraryState,
    modifier: Modifier = Modifier,
    onEditRow: ((SongItem) -> Unit)?,
    onDeleteRow: (SongItem) -> Unit,
    onNewBook: () -> Unit,
) {
    val scroll = rememberScrollState()
    val listState = rememberLazyListState()
    val rows = state.rows
    // Read here and passed down: as `state.songbooks` inside the row it was a scan and sort of the
    // whole library per visible row per frame.
    val songbooks = state.songbooks
    val columnWidth = state.visibleColumns.fold(0.dp) { total, field -> total + field.width() + 1.dp }
    val width = TICK_WIDTH + columnWidth + ACTIONS_WIDTH

    // The scrollbars sit OUTSIDE the horizontally scrolled column, so they stay pinned to the edges
    // of the table rather than sliding away with the columns they are there to move.
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().horizontalScroll(scroll)) {
            TableHeader(state, width)
            when {
                state.isLoading -> SkeletonTable(state, width)
                rows.isEmpty() -> EmptyState(state, width)
                else -> LazyColumn(Modifier.width(width).fillMaxHeight(), state = listState) {
                    items(rows, key = { it.sourceFile }) { song ->
                        SongRow(
                            song = song,
                            state = state,
                            songbooks = songbooks,
                            width = width,
                            onEdit = onEditRow?.let { { it(song) } },
                            onDelete = { onDeleteRow(song) },
                            onNewBook = onNewBook,
                        )
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom = SCROLLBAR_THICKNESS),
        )
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = SCROLLBAR_THICKNESS),
        )
    }
}

/** The width the two scrollbars keep clear of each other in the table's bottom-right corner. */
private val SCROLLBAR_THICKNESS = 12.dp

@Composable
private fun TableHeader(state: SongLibraryState, width: Dp) {
    val scheme = MaterialTheme.colorScheme
    val visible = state.rows.map { it.sourceFile }
    val all = visible.isNotEmpty() && visible.all { it in state.selected }
    val some = !all && visible.any { it in state.selected }

    Column(Modifier.width(width)) {
        Row(
            Modifier.fillMaxWidth().height(LibraryMetrics.headerHeight).background(scheme.surfaceContainer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(TICK_WIDTH), contentAlignment = Alignment.Center) {
                LibraryCheckbox(checked = all, indeterminate = some, onToggle = { state.toggleAll() })
            }
            state.visibleColumns.forEach { field ->
                val sort = field.sortColumn()
                val sorted = state.view.sortBy == sort
                Row(
                    Modifier.width(field.width())
                        .fillMaxHeight()
                        .clickable { state.sortBy(sort) }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        columnLabel(field).uppercase(),
                        style = LibraryType.columnHead,
                        color = if (sorted) scheme.primary else scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (sorted) {
                        Icon(
                            if (state.view.ascending) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(scheme.onSurface.copy(alpha = HAIRLINE_ALPHA)))
            }
            Spacer(Modifier.width(ACTIONS_WIDTH))
        }
        Hairline()
    }
}

@Composable
private fun SongRow(
    song: SongItem,
    state: SongLibraryState,
    songbooks: List<String>,
    width: Dp,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onNewBook: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val checked = song.sourceFile in state.selected
    Column(Modifier.width(width)) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = LibraryMetrics.rowHeight)
                .background(if (checked) scheme.primary.copy(alpha = ACCENT_SURFACE_ALPHA) else scheme.background),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The stripe down the left is what makes a ticked row readable at a glance in a
            // hundred-row table, where the tick alone disappears.
            Box(
                Modifier.width(2.dp)
                    .height(LibraryMetrics.rowHeight)
                    .background(if (checked) scheme.primary else Color.Transparent)
            )
            Box(Modifier.width(TICK_WIDTH - 2.dp), contentAlignment = Alignment.Center) {
                LibraryCheckbox(checked = checked, onToggle = { state.toggle(song.sourceFile) })
            }
            state.visibleColumns.forEach { field ->
                Box(Modifier.width(field.width())) {
                    if (field == SongField.SONGBOOK) {
                        SongbookCell(
                            value = field.of(song),
                            songbooks = songbooks,
                            onPick = { state.edit(song.sourceFile, field, it) },
                            onNewBook = onNewBook,
                        )
                    } else {
                        EditableCell(
                            value = field.of(song),
                            strong = field == SongField.TITLE,
                            onCommit = { state.edit(song.sourceFile, field, it) },
                        )
                    }
                }
                Box(
                    Modifier.width(1.dp)
                        .height(LibraryMetrics.rowHeight)
                        .background(scheme.onSurface.copy(alpha = HAIRLINE_ALPHA))
                )
            }
            Row(
                Modifier.width(ACTIONS_WIDTH).padding(end = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onEdit != null) {
                    RowAction(Icons.Default.Edit, stringResource(Res.string.edit_song), scheme.primary, onEdit)
                }
                RowAction(Icons.Default.Delete, stringResource(Res.string.delete_song), scheme.error, onDelete)
            }
        }
        Hairline()
    }
}

@Composable
private fun RowAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun EmptyState(state: SongLibraryState, width: Dp) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(width).padding(vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            if (state.view.isFiltered) stringResource(Res.string.empty_title)
            else stringResource(Res.string.library_empty),
            style = LibraryType.bodyStrong,
            color = scheme.onSurfaceVariant,
        )
        if (state.view.isFiltered) {
            QuietButton(stringResource(Res.string.reset_filters), onClick = {
                state.view = state.view.copy(query = "", songbook = null)
            })
        }
    }
}

@Composable
internal fun columnLabel(field: SongField): String = when (field) {
    SongField.NUMBER -> stringResource(Res.string.column_number)
    SongField.TITLE -> stringResource(Res.string.column_title)
    SongField.SECONDARY_TITLE -> stringResource(Res.string.column_secondary_title)
    SongField.SONGBOOK -> stringResource(Res.string.column_song_book)
    SongField.AUTHOR -> stringResource(Res.string.column_author)
    SongField.COMPOSER -> stringResource(Res.string.column_composer)
    SongField.TUNE -> stringResource(Res.string.column_tune)
    SongField.CCLI -> stringResource(Res.string.column_ccli)
}

private fun SongField.sortColumn(): SortColumn = when (this) {
    SongField.NUMBER -> SortColumn.NUMBER
    SongField.TITLE -> SortColumn.TITLE
    SongField.SECONDARY_TITLE -> SortColumn.SECONDARY_TITLE
    SongField.SONGBOOK -> SortColumn.SONGBOOK
    SongField.AUTHOR -> SortColumn.AUTHOR
    SongField.COMPOSER -> SortColumn.COMPOSER
    SongField.TUNE -> SortColumn.TUNE
    SongField.CCLI -> SortColumn.CCLI
}

/** Wide enough for what the column holds: a title is a sentence, a number is four digits. */
internal fun SongField.width(): Dp = when (this) {
    SongField.NUMBER -> 84.dp
    SongField.TITLE, SongField.SECONDARY_TITLE -> 280.dp
    SongField.SONGBOOK -> 190.dp
    SongField.AUTHOR, SongField.COMPOSER -> 180.dp
    SongField.TUNE -> 150.dp
    SongField.CCLI -> 110.dp
}

internal val TICK_WIDTH = 36.dp

internal val ACTIONS_WIDTH = 68.dp
