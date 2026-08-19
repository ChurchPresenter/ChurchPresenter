package songlibrary.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.models.songs.SongField
import core.models.songs.SongItem
import core.models.songs.SortColumn
import java.io.File
import org.churchpresenter.app.churchpresenter.ui.theme.semantic
import org.jetbrains.compose.resources.stringResource
import songlibrary.OPTIONAL_COLUMNS
import songlibrary.SongLibraryState
import songlibrary.generated.resources.Res
import songlibrary.generated.resources.all_song_books
import songlibrary.generated.resources.batch_edit_menu
import songlibrary.generated.resources.clear
import songlibrary.generated.resources.column_author
import songlibrary.generated.resources.column_ccli
import songlibrary.generated.resources.column_composer
import songlibrary.generated.resources.column_number
import songlibrary.generated.resources.column_secondary_title
import songlibrary.generated.resources.column_song_book
import songlibrary.generated.resources.column_title
import songlibrary.generated.resources.column_tune
import songlibrary.generated.resources.columns
import songlibrary.generated.resources.columns_always
import songlibrary.generated.resources.columns_show_all
import songlibrary.generated.resources.delete
import songlibrary.generated.resources.delete_song
import songlibrary.generated.resources.done
import songlibrary.generated.resources.edit_song
import songlibrary.generated.resources.empty_title
import songlibrary.generated.resources.footer_songs
import songlibrary.generated.resources.library_empty
import songlibrary.generated.resources.loading
import songlibrary.generated.resources.new_song
import songlibrary.generated.resources.new_song_book_menu
import songlibrary.generated.resources.no_song_book
import songlibrary.generated.resources.reset_filters
import songlibrary.generated.resources.revert
import songlibrary.generated.resources.save_changes
import songlibrary.generated.resources.save_failed
import songlibrary.generated.resources.search_placeholder
import songlibrary.generated.resources.selected_count
import songlibrary.generated.resources.subhead_counts
import songlibrary.generated.resources.subhead_filtered
import songlibrary.generated.resources.unsaved_changes
import songlibrary.generated.resources.window_title

/**
 * One song asked to be edited, and everything an editor needs to do it.
 *
 * [allSongs] and [songbooks] are what the library holds right now, so a host that checks for a
 * clashing number or offers a list of books does not have to scan the folder a second time.
 */
data class SongEditorRequest(
    val song: SongItem,
    val songbooks: List<String>,
    val allSongs: List<SongItem>,
    val onSave: (SongItem) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The Song Library Manager: every song in the library folder in one grid, editable in place.
 *
 * The library is a folder of `.song` files and a person's only view of it is otherwise one song at
 * a time, so anything that spans songs — renumbering a book, filling in a missing composer, moving
 * a set into a new song book — means opening each of them. This is that work in one screen: type in
 * a cell, tick a row, and nothing touches the disk until Save.
 */
@Composable
fun SongLibraryApp(
    libraryFolder: File,
    onClose: (() -> Unit)? = null,
    /**
     * The editor a row's Edit button opens, supplied by whoever hosts this window.
     *
     * Inside ChurchPresenter that is the app's own Edit Song dialog, the same one the Songs tab
     * opens — one editor for a song, wherever it is opened from. Standalone there is no app to ask,
     * so the plain one below stands in.
     */
    songEditor: (@Composable (editing: SongEditorRequest) -> Unit)? = null,
) {
    val state = remember(libraryFolder) { SongLibraryState(libraryFolder) }
    LaunchedEffect(libraryFolder) { state.reloadAsync() }

    var newBookOpen by remember { mutableStateOf(false) }
    var batchOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<SongItem>>(emptyList()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LibraryHeader(state, onNewBook = { newBookOpen = true })
        if (state.selected.isNotEmpty()) {
            BulkBar(
                state = state,
                onBatchEdit = { batchOpen = true },
                onDelete = { pendingDelete = state.selectedSongs() },
            )
        }
        SongTable(
            state = state,
            modifier = Modifier.weight(1f),
            onEditRow = { state.editing = it.sourceFile },
            onDeleteRow = { pendingDelete = listOf(it) },
            onNewBook = { newBookOpen = true },
        )
        LibraryFooter(state, onClose)
    }

    if (newBookOpen) {
        NewSongBookDialog(
            existing = state.songbooks,
            selectedCount = state.selected.size,
            onDismiss = { newBookOpen = false },
            onCreate = { name, assign ->
                state.createSongbook(name, assign)
                newBookOpen = false
            },
        )
    }

    if (batchOpen) {
        BatchEditDialog(
            count = state.selected.size,
            songbooks = state.songbooks,
            onDismiss = { batchOpen = false },
            onApply = { fields ->
                state.editAll(fields)
                batchOpen = false
            },
        )
    }

    if (pendingDelete.isNotEmpty()) {
        DeleteConfirmDialog(
            songs = pendingDelete,
            onDismiss = { pendingDelete = emptyList() },
            onConfirm = {
                state.delete(pendingDelete)
                pendingDelete = emptyList()
            },
        )
    }

    state.editing?.let { sourceFile ->
        state.songOf(sourceFile)?.let { song ->
            val request = SongEditorRequest(
                song = song,
                songbooks = state.songbooks,
                allSongs = state.songs,
                onSave = {
                    state.replace(it)
                    state.editing = null
                },
                onDismiss = { state.editing = null },
            )
            if (songEditor != null) songEditor(request) else SongEditorDialog(request)
        }
    }
}

@Composable
private fun LibraryHeader(state: SongLibraryState, onNewBook: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.window_title), style = LibraryType.title, color = scheme.onSurface)
                Text(subhead(state), style = LibraryType.small, color = scheme.onSurfaceVariant)
            }

            SearchField(state.view.query) { state.view = state.view.copy(query = it) }
            SongBookFilter(state, onNewBook)
            ColumnsMenu(state)
            val newSongTitle = stringResource(Res.string.new_song)
            PrimaryButton(
                label = newSongTitle,
                onClick = { state.newSong(newSongTitle) },
                icon = { Icon(Icons.Default.Add, null, Modifier.size(13.dp), tint = scheme.onPrimary) },
            )
        }
        Hairline()
    }
}

@Composable
private fun subhead(state: SongLibraryState): String =
    if (state.isLoading) stringResource(Res.string.loading)
    else if (state.view.isFiltered) stringResource(Res.string.subhead_filtered, state.rows.size, state.songs.size)
    else stringResource(Res.string.subhead_counts, state.songs.size, state.songbooks.size)

@Composable
private fun BulkBar(state: SongLibraryState, onBatchEdit: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().background(scheme.primary.copy(alpha = ACCENT_SURFACE_ALPHA)).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.selected_count, state.selected.size),
                style = LibraryType.bodyStrong,
                color = scheme.primary,
            )
            Box(Modifier.width(1.dp).height(16.dp).background(scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA)))
            PrimaryButton(
                label = stringResource(Res.string.batch_edit_menu),
                onClick = onBatchEdit,
                icon = { Icon(Icons.Default.Edit, null, Modifier.size(12.dp), tint = scheme.onPrimary) },
            )
            Spacer(Modifier.weight(1f))
            QuietButton(stringResource(Res.string.delete), onClick = onDelete, danger = true)
            Text(
                stringResource(Res.string.clear),
                style = LibraryType.button,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { state.clearSelection() }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA)))
    }
}

@Composable
private fun SongTable(
    state: SongLibraryState,
    modifier: Modifier = Modifier,
    onEditRow: (SongItem) -> Unit,
    onDeleteRow: (SongItem) -> Unit,
    onNewBook: () -> Unit,
) {
    val scroll = rememberScrollState()
    val listState = rememberLazyListState()
    val rows = state.rows
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
                            width = width,
                            onEdit = { onEditRow(song) },
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

/**
 * What the table shows while the folder is still being read.
 *
 * The rows the real table will have, in the columns it will have them in, with a highlight sweeping
 * across — so the window arrives already the right shape and the grid fills in, rather than the
 * layout jumping when the load lands. A spinner in the middle of an empty table says only that
 * something is happening; this says what is coming.
 */
@Composable
private fun SkeletonTable(state: SongLibraryState, width: Dp) {
    val sweep = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_SWEEP_MS, easing = LinearEasing)),
        label = "sweep",
    )
    Column(Modifier.width(width)) {
        repeat(SKELETON_ROWS) { row -> SkeletonRow(state, width, sweep, row) }
    }
}

@Composable
private fun SkeletonRow(state: SongLibraryState, width: Dp, sweep: State<Float>, row: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.width(width)) {
        Row(
            Modifier.fillMaxWidth().height(LibraryMetrics.rowHeight).background(scheme.background),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Every bar is told where it sits across the table, so the highlight crosses the whole
            // row as one sweep rather than restarting inside each cell.
            var offset = 0.dp
            Box(Modifier.width(TICK_WIDTH), contentAlignment = Alignment.Center) {
                SkeletonBar(15.dp, sweep, offset + 10.dp, width)
            }
            offset += TICK_WIDTH
            state.visibleColumns.forEach { field ->
                val cell = field.width()
                Box(Modifier.width(cell).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                    SkeletonBar((cell - 18.dp) * barFraction(row, field.ordinal), sweep, offset + 9.dp, width)
                }
                Box(Modifier.width(1.dp).height(LibraryMetrics.rowHeight).background(scheme.onSurface.copy(alpha = HAIRLINE_ALPHA)))
                offset += cell + 1.dp
            }
            Spacer(Modifier.width(ACTIONS_WIDTH))
        }
        Hairline()
    }
}

/** One bar, filled with the moving gradient. [xOffset] is where it starts across [tableWidth]. */
@Composable
private fun SkeletonBar(barWidth: Dp, sweep: State<Float>, xOffset: Dp, tableWidth: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.width(barWidth)
            .height(SKELETON_BAR_HEIGHT)
            .clip(RoundedCornerShape(3.dp))
            // Read in the draw phase, not composition: `sweep.value` changes every frame, and read
            // up in the composable it would recompose eighty cells sixty times a second.
            .drawBehind {
                val total = tableWidth.toPx()
                val band = total * SKELETON_BAND
                val head = -band + sweep.value * (total + band * 2) - xOffset.toPx()
                drawRect(
                    Brush.linearGradient(
                        colorStops = arrayOf(0f to scheme.onSurface.copy(alpha = SKELETON_ALPHA), 0.5f to scheme.onSurface.copy(alpha = SKELETON_HIGHLIGHT_ALPHA), 1f to scheme.onSurface.copy(alpha = SKELETON_ALPHA)),
                        start = Offset(head, 0f),
                        end = Offset(head + band, 0f),
                    )
                )
            }
    )
}

/**
 * How much of its cell a bar fills, so the rows read as text of different lengths rather than a
 * block. Derived from the row and column rather than random, so it does not change under a redraw.
 */
private fun barFraction(row: Int, column: Int): Float =
    SKELETON_MIN_FILL + ((row * 7 + column * 13) % SKELETON_FILL_STEPS) * SKELETON_FILL_STEP

private const val SKELETON_ROWS = 10
private const val SKELETON_SWEEP_MS = 1400
private const val SKELETON_BAND = 0.35f
private const val SKELETON_MIN_FILL = 0.42f
private const val SKELETON_FILL_STEPS = 5
private const val SKELETON_FILL_STEP = 0.12f
private val SKELETON_BAR_HEIGHT = 9.dp

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
    width: Dp,
    onEdit: () -> Unit,
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
                            songbooks = state.songbooks,
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
                Box(Modifier.width(1.dp).height(LibraryMetrics.rowHeight).background(scheme.onSurface.copy(alpha = HAIRLINE_ALPHA)))
            }
            Row(
                Modifier.width(ACTIONS_WIDTH).padding(end = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowAction(Icons.Default.Edit, stringResource(Res.string.edit_song), scheme.primary, onEdit)
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
            Icon(Icons.Default.Search, null, tint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA), modifier = Modifier.size(19.dp))
        }
        Text(
            if (state.view.isFiltered) stringResource(Res.string.empty_title) else stringResource(Res.string.library_empty),
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
private fun LibraryFooter(state: SongLibraryState, onClose: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Hairline()
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(scheme.surfaceContainer).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.footer_songs, state.songs.size), style = LibraryType.small, color = scheme.onSurfaceVariant)
            if (state.isDirty) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.semantic.warning))
                    Text(
                        stringResource(Res.string.unsaved_changes, state.changedCount),
                        style = LibraryType.button,
                        color = MaterialTheme.semantic.warning,
                    )
                }
            }
            state.lastOutcome?.errors?.firstOrNull()?.let {
                Text(
                    stringResource(Res.string.save_failed, it),
                    style = LibraryType.small,
                    color = scheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            QuietButton(stringResource(Res.string.revert), onClick = { state.revert() }, enabled = state.isDirty)
            PrimaryButton(stringResource(Res.string.save_changes), onClick = { state.save() }, enabled = state.isDirty)
            if (onClose != null) {
                Box(Modifier.width(1.dp).height(20.dp).background(scheme.outlineVariant))
                QuietButton(stringResource(Res.string.done), onClick = onClose)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.width(236.dp)
            .height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(LibraryMetrics.radius))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(7.dp))
        PlainTextField(
            value = value,
            onValueChange = onChange,
            placeholder = stringResource(Res.string.search_placeholder),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SongBookFilter(state: SongLibraryState, onNewBook: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = when (val book = state.view.songbook) {
        null -> stringResource(Res.string.all_song_books)
        "" -> stringResource(Res.string.no_song_book)
        else -> book
    }
    LibraryDropdown(label = label, highlighted = state.view.songbook != null, menuWidth = 250.dp) { close ->
        MenuRow(stringResource(Res.string.all_song_books), selected = state.view.songbook == null, count = state.songs.size) {
            state.view = state.view.copy(songbook = null)
            close()
        }
        MenuRow(stringResource(Res.string.no_song_book), selected = state.view.songbook == "", count = state.counts[""] ?: 0) {
            state.view = state.view.copy(songbook = "")
            close()
        }
        MenuDivider()
        state.songbooks.forEach { book ->
            MenuRow(book, selected = state.view.songbook == book, count = state.counts[book] ?: 0) {
                state.view = state.view.copy(songbook = book)
                close()
            }
        }
        MenuDivider()
        MenuRow(
            label = stringResource(Res.string.new_song_book_menu),
            accent = true,
            leading = { Icon(Icons.Default.Add, null, tint = scheme.primary, modifier = Modifier.size(11.dp)) },
        ) {
            close()
            onNewBook()
        }
    }
}

@Composable
private fun ColumnsMenu(state: SongLibraryState) {
    val scheme = MaterialTheme.colorScheme
    LibraryDropdown(
        label = stringResource(Res.string.columns),
        highlighted = state.hiddenColumns.isNotEmpty(),
        menuWidth = 224.dp,
        leading = { Icon(Icons.Default.ViewColumn, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp)) },
    ) { _ ->
        MenuRow(stringResource(Res.string.columns_show_all), accent = true) { state.showAllColumns() }
        MenuDivider()
        // The title is always shown: a row identified only by its number is unreadable.
        MenuRow(
            label = columnLabel(SongField.TITLE),
            leading = { LibraryCheckbox(checked = true) },
            trailing = { Text(stringResource(Res.string.columns_always), style = LibraryType.columnHead, color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA)) },
            onClick = null,
        )
        OPTIONAL_COLUMNS.forEach { field ->
            MenuRow(
                label = columnLabel(field),
                leading = { LibraryCheckbox(checked = field !in state.hiddenColumns) },
            ) { state.toggleColumn(field) }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = HAIRLINE_ALPHA)))
}

@Composable
private fun columnLabel(field: SongField): String = when (field) {
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
private fun SongField.width(): Dp = when (this) {
    SongField.NUMBER -> 84.dp
    SongField.TITLE, SongField.SECONDARY_TITLE -> 280.dp
    SongField.SONGBOOK -> 190.dp
    SongField.AUTHOR, SongField.COMPOSER -> 180.dp
    SongField.TUNE -> 150.dp
    SongField.CCLI -> 110.dp
}

private val TICK_WIDTH = 36.dp
private val ACTIONS_WIDTH = 68.dp
