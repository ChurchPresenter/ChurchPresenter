package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import org.churchpresenter.theme.AppShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.SongField
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SortColumn
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.songlibrary.SongLibraryState
import org.churchpresenter.songlibrary.generated.resources.Res
import org.churchpresenter.songlibrary.generated.resources.column_author
import org.churchpresenter.songlibrary.generated.resources.column_ccli
import org.churchpresenter.songlibrary.generated.resources.column_composer
import org.churchpresenter.songlibrary.generated.resources.column_duration
import org.churchpresenter.songlibrary.generated.resources.column_number
import org.churchpresenter.songlibrary.generated.resources.column_fourth_title
import org.churchpresenter.songlibrary.generated.resources.column_secondary_title
import org.churchpresenter.songlibrary.generated.resources.column_third_title
import org.churchpresenter.songlibrary.generated.resources.column_song_book
import org.churchpresenter.songlibrary.generated.resources.column_title
import org.churchpresenter.songlibrary.generated.resources.column_tune
import org.churchpresenter.songlibrary.generated.resources.delete_song
import org.churchpresenter.songlibrary.generated.resources.edit_song
import org.churchpresenter.songlibrary.generated.resources.empty_title
import org.churchpresenter.songlibrary.generated.resources.library_empty
import org.churchpresenter.songlibrary.generated.resources.reset_filters
import org.churchpresenter.hosanna.composables.model.ButtonIntent

// The grid itself: the header row, one row per song, and what stands in when there are none.

@Composable
internal fun SongTable(
    state: SongLibraryState,
    modifier: Modifier = Modifier,
    onCompareRow: (SongItem) -> Unit,
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
    val (frozen, scrolling) = splitFrozen(state.visibleColumns)
    val frozenWidth = TICK_WIDTH + PROBLEM_WIDTH + columnsWidth(frozen)

    // The scrollbars sit outside the lines they move, pinned to the edges of the table. The
    // horizontal one spans only the scrolling middle: the frozen edges never move.
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TableHeader(state, frozen, scrolling, scroll)
            when {
                state.isLoading -> SkeletonTable(state, frozen, scrolling, scroll)
                rows.isEmpty() -> EmptyState(state)
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(rows, key = { it.sourceFile }) { song ->
                        SongRow(
                            song = song,
                            state = state,
                            songbooks = songbooks,
                            columns = frozen to scrolling,
                            scroll = scroll,
                            onCompare = { onCompareRow(song) },
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
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = frozenWidth, end = ACTIONS_WIDTH),
        )
    }
}

/** The width the two scrollbars keep clear of each other in the table's bottom-right corner. */
private val SCROLLBAR_THICKNESS = 12.dp

@Composable
private fun TableHeader(
    state: SongLibraryState,
    frozen: List<SongField>,
    scrolling: List<SongField>,
    scroll: ScrollState,
) {
    val scheme = MaterialTheme.colorScheme
    val visible = state.rows.map { it.sourceFile }
    val all = visible.isNotEmpty() && visible.all { it in state.selected }
    val some = !all && visible.any { it in state.selected }

    Column(Modifier.fillMaxWidth()) {
        TableLine(
            scroll,
            Modifier.height(LibraryMetrics.headerHeight).background(scheme.surfaceContainer),
            frozen = {
                Box(Modifier.width(TICK_WIDTH), contentAlignment = Alignment.Center) {
                    LibraryCheckbox(checked = all, indeterminate = some, onToggle = { state.toggleAll() })
                }
                Spacer(Modifier.width(PROBLEM_WIDTH))
                frozen.forEach { field ->
                    HeadCell(state, label = columnLabel(field), sort = field.sortColumn(), width = field.width())
                }
            },
            scrolling = {
                scrolling.forEach { field ->
                    HeadCell(state, label = columnLabel(field), sort = field.sortColumn(), width = field.width())
                }
                if (state.showDuration) {
                    HeadCell(
                        state,
                        label = stringResource(Res.string.column_duration),
                        sort = SortColumn.DURATION,
                        width = DURATION_WIDTH,
                    )
                }
                if (state.showOrder) OrderHeadCell()
            },
            actions = { Spacer(Modifier.width(ACTIONS_WIDTH)) },
        )
        Hairline()
    }
}

/** One column heading: click to sort by it, with the arrow on the one the grid is ordered by. */
@Composable
private fun HeadCell(state: SongLibraryState, label: String, sort: SortColumn, width: Dp) {
    val scheme = MaterialTheme.colorScheme
    val sorted = state.view.sortBy == sort
    Row(
        Modifier.width(width)
            .height(LibraryMetrics.headerHeight)
            .clickable { state.sortBy(sort) }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
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
    ColumnDivider(LibraryMetrics.headerHeight)
}

@Composable
private fun SongRow(
    song: SongItem,
    state: SongLibraryState,
    songbooks: List<String>,
    columns: Pair<List<SongField>, List<SongField>>,
    scroll: ScrollState,
    onCompare: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onNewBook: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val checked = song.sourceFile in state.selected
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(Modifier.fillMaxWidth()) {
        TableLine(
            scroll,
            Modifier.heightIn(min = LibraryMetrics.rowHeight)
                .background(if (checked) scheme.primary.copy(alpha = ACCENT_SURFACE_ALPHA) else scheme.background)
                // Over the tick's tint too: the row under the pointer is the one its buttons act on,
                // ticked or not.
                .background(if (hovered) scheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent)
                .hoverable(hover)
                .testTag(SONG_ROW_TAG),
            frozen = {
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
                ProblemMark(state.translationProblems[song.sourceFile])
                SongCells(columns.first, song, state, songbooks, onNewBook)
            },
            scrolling = {
                SongCells(columns.second, song, state, songbooks, onNewBook)
                if (state.showDuration) {
                    // Measured, not typed: how long the song usually stays on screen. Read only --
                    // the number comes from the services it was sung in, and there is nothing to
                    // correct by hand.
                    DurationCell(seconds = state.durations[song.sourceFile])
                    ColumnDivider()
                }
                if (state.showOrder) {
                    OrderCell(song.sectionOrder)
                    ColumnDivider()
                }
            },
            actions = {
                Row(
                    Modifier.width(ACTIONS_WIDTH).padding(end = SCROLLBAR_THICKNESS),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompareAction(
                        languages = song.translationList().count { !it.isEmpty },
                        mismatches = state.translationProblems[song.sourceFile]?.mismatchedSections ?: 0,
                        onClick = onCompare,
                    )
                    if (onEdit != null) {
                        RowAction(
                            Icons.Default.Edit, stringResource(Res.string.edit_song), ButtonIntent.Default, onEdit,
                        )
                    }
                    RowAction(
                        Icons.Default.Delete, stringResource(Res.string.delete_song), ButtonIntent.Critical, onDelete,
                    )
                }
            },
        )
        Hairline()
    }
}

/** [fields]' cells of [song], each editable in place, with the hairline after each. */
@Composable
private fun SongCells(
    fields: List<SongField>,
    song: SongItem,
    state: SongLibraryState,
    songbooks: List<String>,
    onNewBook: () -> Unit,
) {
    fields.forEach { field ->
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
        ColumnDivider()
    }
}

@Composable
private fun EmptyState(state: SongLibraryState) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(AppShape(12.dp)).background(scheme.surfaceContainer),
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
    SongField.THIRD_TITLE -> stringResource(Res.string.column_third_title)
    SongField.FOURTH_TITLE -> stringResource(Res.string.column_fourth_title)
    SongField.SONGBOOK -> stringResource(Res.string.column_song_book)
    SongField.AUTHOR -> stringResource(Res.string.column_author)
    SongField.COMPOSER -> stringResource(Res.string.column_composer)
    SongField.TUNE -> stringResource(Res.string.column_tune)
    SongField.CCLI -> stringResource(Res.string.column_ccli)
}

internal fun SongField.sortColumn(): SortColumn = when (this) {
    SongField.NUMBER -> SortColumn.NUMBER
    SongField.TITLE -> SortColumn.TITLE
    SongField.SECONDARY_TITLE -> SortColumn.SECONDARY_TITLE
    SongField.THIRD_TITLE -> SortColumn.THIRD_TITLE
    SongField.FOURTH_TITLE -> SortColumn.FOURTH_TITLE
    SongField.SONGBOOK -> SortColumn.SONGBOOK
    SongField.AUTHOR -> SortColumn.AUTHOR
    SongField.COMPOSER -> SortColumn.COMPOSER
    SongField.TUNE -> SortColumn.TUNE
    SongField.CCLI -> SortColumn.CCLI
}

/** Wide enough for what the column holds: a title is a sentence, a number is four digits. */
internal fun SongField.width(): Dp = when (this) {
    SongField.NUMBER -> 84.dp
    SongField.TITLE, SongField.SECONDARY_TITLE, SongField.THIRD_TITLE, SongField.FOURTH_TITLE -> 280.dp
    SongField.SONGBOOK -> 190.dp
    SongField.AUTHOR, SongField.COMPOSER -> 180.dp
    SongField.TUNE -> 150.dp
    SongField.CCLI -> 110.dp
}

internal val TICK_WIDTH = 36.dp

/** The slot between the tick and the first column that holds a problem song's `!`. */
internal val PROBLEM_WIDTH = 18.dp

internal val ACTIONS_WIDTH = 96.dp

/** Each song row, for the tests that read how a row is drawn. */
internal const val SONG_ROW_TAG = "song-row"
