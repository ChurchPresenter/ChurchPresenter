package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Cursor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import churchpresenter.composeapp.generated.resources.songs_indexing
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_favorites
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import androidx.compose.material.icons.filled.Tv
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.delete_saved_string
import churchpresenter.composeapp.generated.resources.filter
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.remove_from_favorites
import churchpresenter.composeapp.generated.resources.song_favorites
import churchpresenter.composeapp.generated.resources.song_favorites_clear
import churchpresenter.composeapp.generated.resources.song_play_count
import churchpresenter.composeapp.generated.resources.song_columns
import churchpresenter.composeapp.generated.resources.number
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.search_clear
import churchpresenter.composeapp.generated.resources.search_songs
import churchpresenter.composeapp.generated.resources.song_book
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.tune
import churchpresenter.composeapp.generated.resources.author
import churchpresenter.composeapp.generated.resources.composer
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.finalPassClickable
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.draggedColumnIndex
import org.churchpresenter.app.churchpresenter.utils.moveColumn
import org.churchpresenter.app.churchpresenter.utils.songColumnSortKey
import org.churchpresenter.app.churchpresenter.ui.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.RowScope

private const val REBUILD_CLICK_WINDOW_MS = 800
private const val REBUILD_CLICK_COUNT = 3
private const val SCROLL_SETTLE_MS = 100L

/**
 * Shared minimum height for the two bars across the top of the Songs tab — the search row on the
 * left and the action row on the right.
 *
 * They hold different-sized content (a 42.dp search field against 34.dp action buttons), so left to
 * their natural heights the two bars ended 8.dp apart and the divider under them stepped at the
 * pane boundary. Pinning both to one height lines them up without resizing either control: the
 * field keeps Bible's 42.dp and the buttons keep the 34.dp `ActionIconButton` every tab uses.
 *
 * 60.dp = the taller bar's natural height, 42.dp of field plus its 10.dp/8.dp inset.
 */
internal val SongsTopBarMinHeight = 60.dp

/**
 * The song list down the left of the Songs tab: search and filters, the resizable and reorderable
 * table, and the favourites panel beneath it.
 *
 * Column layout lives in [columns]; everything else arrives as a value or a callback.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun RowScope.SongListPane(
    columns: SongTableColumns,
    dialogs: SongDialogRequests,
    live: SongLiveState,
    filteredSongs: List<SongItem>,
    selectedSongIndex: Int,
    searchQuery: String,
    isLoading: Boolean,
    isPresenting: Boolean,
    songbooks: List<String>,
    songbookOptions: List<String>,
    selectedSongbook: String,
    allSongBooksText: String,
    containsText: String,
    filterType: String,
    filterTypes: List<String>,
    filterTypeMap: Map<String, String>,
    filterTypeDisplayMap: Map<String, String>,
    currentSortColumn: String,
    currentSortAscending: Boolean,
    actionCols: Set<String>,
    availableCols: List<String>,
    visibleCols: List<String>,
    favorites: Set<String>,
    favoritesExpanded: Boolean,
    favPanelHeightPx: Float,
    tabFocusRequester: FocusRequester,
    favoriteSongs: () -> List<SongItem>,
    playCountFor: (String) -> Int?,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onFilterTypeChange: (String) -> Unit,
    onSongbookChange: (String) -> Unit,
    onSortChange: (String) -> Unit,
    onSelectSong: (Int) -> Unit,
    onSelectSongByDetails: (number: Int, title: String, songbook: String, songId: String) -> Unit,
    onSelectSection: (Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onClearFavorites: () -> Unit,
    onReloadSongs: () -> Unit,
    onSaveColumnWidths: () -> Unit,
    onSaveColumnOrder: () -> Unit,
    onSaveHiddenColumns: () -> Unit,
    onSaveFavPanelHeight: () -> Unit,
    onFavoritesExpandedChange: (Boolean) -> Unit,
    onFavPanelHeightChange: (Float) -> Unit,
    onAddToSchedule: ((Int, String, String, String) -> Unit)?,
    onPresenting: (Presenting) -> Unit,
    sendToPresenter: (goLive: Boolean) -> Unit,
) {
    val density = LocalDensity.current
fun colWidth(id: String) = columns.widthOf(id)

fun setColWidth(id: String, px: Float) = columns.setWidth(id, px)

fun sortKey(id: String) = songColumnSortKey(id)

// NOTE: operates on visibleCols (set after state vars), returns index within visibleCols
fun computeNewIdx(draggedId: String, accumX: Float, visibleCols: List<String>): Int =
    draggedColumnIndex(
        draggedId, accumX, visibleCols,
        columnWidthPx = ::colWidth,
        handleWidthPx = with(density) { 6.dp.toPx() },
    )

@Composable
fun DragHandle(colId: String, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .width(6.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(colId) {
                detectHorizontalDragGestures(onDragEnd = { currentOnDragEnd() }) { _, amount ->
                    currentOnDrag(amount)
                }
            }
    )
}

    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        // Pre-compute column labels (stringResource is @Composable, can't be called in forEach)
        val colHeaderLabels = mapOf(
            "number"     to stringResource(Res.string.number),
            "title"      to stringResource(Res.string.title),
            "songbook"   to stringResource(Res.string.song_book),
            "tune"       to stringResource(Res.string.tune),
            "play_count" to stringResource(Res.string.song_play_count),
            "author"     to stringResource(Res.string.author),
            "composer"   to stringResource(Res.string.composer)
        )
        val allColLabels = colHeaderLabels + mapOf(
            "favorites"       to stringResource(Res.string.song_favorites),
            "add_to_schedule" to stringResource(Res.string.add_to_schedule)
        )

        // Search controls — wraps to new line if not enough space
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            // Bible's search-row inset (BibleSearchRow.kt), so the two tabs' top bars sit on the
            // same margins instead of Songs starting 8.dp further left and 6.dp higher.
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = SongsTopBarMinHeight)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            // Styled search field matching the dropdown aesthetic
            Row(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 120.dp)
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 11.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { onSearchQueryChange(it) },
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { onSearchFocusChanged(it.isFocused) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.search_songs),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(30.dp)) {
                        Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.search_clear), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (songbooks.size > 1) {
                DropdownSelector(
                    label = stringResource(Res.string.song_book),
                    items = songbookOptions,
                    selected = selectedSongbook.ifEmpty { allSongBooksText },
                    onSelectedChange = { onSongbookChange(it) }
                )
            }

            DropdownSelector(
                label = stringResource(Res.string.filter),
                items = filterTypes,
                selected = filterTypeDisplayMap[filterType] ?: containsText,
                onSelectedChange = { displayText ->
                    val internalKey = filterTypeMap[displayText] ?: Constants.CONTAINS
                    onFilterTypeChange(internalKey)
                }
            )

            // Hidden rebuild: 3 rapid clicks on the search button force-reloads songs from disk
            var rebuildClickCount by remember { mutableStateOf(0) }
            var rebuildClickTime by remember { mutableStateOf(0L) }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        val now = System.currentTimeMillis()
                        if (now - rebuildClickTime > REBUILD_CLICK_WINDOW_MS) rebuildClickCount = 0
                        rebuildClickCount++
                        rebuildClickTime = now
                        if (rebuildClickCount >= REBUILD_CLICK_COUNT) {
                            rebuildClickCount = 0
                            onReloadSongs()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Shared horizontal scroll state for header + song list
        val hScrollState = rememberScrollState()

        val contentMinWidthDp = with(density) {
            val colsW = visibleCols.fold(0f) { acc, id -> acc + colWidth(id) }
            // DragHandles (6dp each) only appear after data columns, not after action columns
            val handlesW = visibleCols.count { it !in actionCols } * 6.dp.toPx()
            (colsW + handlesW).toDp() + 16.dp
        }

        // Column header row — scrolls horizontally with the song list
        // Wrapped in a Box so the right-click DropdownMenu can anchor here
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press &&
                                event.button?.isSecondary == true
                            ) {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null) columns.menuOffset = with(density) { DpOffset(pos.x.toDp(), pos.y.toDp()) }
                                columns.showMenu = true
                            }
                        }
                    }
                }
        ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(hScrollState)
        ) {
        Row(
            modifier = Modifier
                .width(contentMinWidthDp)
                .fillMaxHeight()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleCols.forEach { colId ->
                val isBeingDragged = colId == columns.draggingId
                val sk = sortKey(colId)
                val isSortable = sk.isNotEmpty() && colId != "add_to_schedule"
                val isSorted = isSortable && currentSortColumn == sk
                val reorderDragMod = Modifier.pointerInput(colId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val vc = columns.order.filter { it !in columns.hidden }
                            val newVisIdx = computeNewIdx(colId, columns.dragAccumX, vc)
                            val targetId = vc.getOrNull(newVisIdx)
                            if (targetId != null) {
                                val reordered = moveColumn(columns.order, colId, targetId)
                                if (reordered !== columns.order) {
                                    columns.order = reordered
                                    onSaveColumnOrder()
                                }
                            }
                            columns.draggingId = null
                            columns.dragAccumX = 0f
                        },
                        onDragCancel = { columns.draggingId = null; columns.dragAccumX = 0f }
                    ) { _, amount ->
                        if (columns.draggingId != colId) { columns.draggingId = colId; columns.dragAccumX = 0f }
                        columns.dragAccumX += amount
                    }
                }
                val cellColor = when {
                    isBeingDragged -> MaterialTheme.colorScheme.primary
                    isSorted -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val cellBg = if (isSorted)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else
                    Color.Transparent

                if (colId in actionCols) {
                    // Action column — icon header, no resize handle
                    Box(
                        modifier = Modifier
                            .width(with(density) { colWidth(colId).toDp() })
                            .fillMaxHeight()
                            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                            .background(cellBg, shape = MaterialTheme.shapes.extraSmall)
                            .then(if (isSortable) Modifier.clickable { onSortChange(sk) } else Modifier)
                            .then(reorderDragMod),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            // Deliberately unlabelled: this is a column header whose click
                            // SORTS, so naming it "Add to Schedule" would name it after an
                            // action it does not perform. Giving a sortable header its proper
                            // name is a semantics question for the whole table, not this icon.
                            Icon(
                                painter = painterResource(
                                    if (colId == "favorites") Res.drawable.ic_star else Res.drawable.ic_playlist_add
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = cellColor
                            )
                            if (isSorted) {
                                Icon(
                                    painter = painterResource(if (currentSortAscending) Res.drawable.ic_arrow_up else Res.drawable.ic_arrow_down),
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = cellColor
                                )
                            }
                        }
                    }
                } else {
                    // Data column — text label + resize handle
                    Box(
                        modifier = Modifier
                            .width(with(density) { colWidth(colId).toDp() })
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .background(cellBg, shape = MaterialTheme.shapes.extraSmall)
                            .then(if (isSortable) Modifier.clickable { onSortChange(sk) } else Modifier)
                            .then(reorderDragMod),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = colHeaderLabels[colId] ?: colId,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cellColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSorted) {
                                Icon(
                                    painter = painterResource(if (currentSortAscending) Res.drawable.ic_arrow_up else Res.drawable.ic_arrow_down),
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = cellColor
                                )
                            }
                        }
                    }
                    DragHandle(
                        colId = colId,
                        onDrag = { setColWidth(colId, colWidth(colId) + it) },
                        onDragEnd = onSaveColumnWidths
                    )
                }
            }
        }
        } // end inner scrollable header Box
        } // end header Row

        // Floating column filter button — right side
        Box(modifier = Modifier.align(Alignment.CenterEnd).background(MaterialTheme.colorScheme.surfaceVariant)) {
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Default.Tune),
                text = stringResource(Res.string.song_columns),
                onClick = { columns.showColumnsMenu = true },
                buttonSize = 36.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
            DropdownMenu(
                expanded = columns.showColumnsMenu,
                onDismissRequest = { columns.showColumnsMenu = false }
            ) {
                availableCols.forEach { colId ->
                    val isVisible = colId !in columns.hidden
                    val isProtected = colId == "title"
                    DropdownMenuItem(
                        text = { Text(allColLabels[colId] ?: colId) },
                        leadingIcon = {
                            Checkbox(
                                checked = isVisible,
                                onCheckedChange = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            if (!(isProtected && isVisible)) {
                                columns.hidden = if (isVisible) columns.hidden + colId else columns.hidden - colId
                                onSaveHiddenColumns()
                            }
                        },
                        enabled = !(isProtected && isVisible)
                    )
                }
            }
        }

        // Right-click dropdown — toggle column visibility
        DropdownMenu(
            expanded = columns.showMenu,
            onDismissRequest = { columns.showMenu = false },
            offset = columns.menuOffset
        ) {
            availableCols.forEach { colId ->
                val isVisible = colId !in columns.hidden
                val isProtected = colId == "title"
                DropdownMenuItem(
                    text = { Text(allColLabels[colId] ?: colId) },
                    leadingIcon = {
                        Checkbox(
                            checked = isVisible,
                            onCheckedChange = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        if (!(isProtected && isVisible)) {
                            columns.hidden = if (isVisible) columns.hidden + colId else columns.hidden - colId
                            onSaveHiddenColumns()
                        }
                    },
                    enabled = !(isProtected && isVisible)
                )
            }
        }
        } // end outer header Box (right-click + dropdown wrapper)

        // Song list + horizontal scrollbar
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && filteredSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.songs_indexing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val lazyListState = rememberLazyListState()

            LaunchedEffect(selectedSongIndex, filteredSongs.size) {
                if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                    delay(SCROLL_SETTLE_MS)
                    lazyListState.animateScrollToItem(selectedSongIndex)
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(hScrollState)
            ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .width(contentMinWidthDp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(end = 8.dp)
            ) {
                itemsIndexed(filteredSongs) { index, song ->
                    var showContextMenu by remember { mutableStateOf(false) }
                    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
                    val isRowSelected = index == selectedSongIndex
                    Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isRowSelected) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface
                            )
                            .finalPassClickable {
                                onSelectSong(index)
                                if (isPresenting && live.songId != null) {
                                    onSelectSection(-1)
                                }
                                tabFocusRequester.requestFocus()
                            }
                            .padding(vertical = 8.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        if (event.type == PointerEventType.Press &&
                                            event.button?.isSecondary == true
                                        ) {
                                            val pos = event.changes.first().position
                                            contextMenuOffset = with(density) {
                                                DpOffset(pos.x.toDp(), pos.y.toDp())
                                            }
                                            showContextMenu = true
                                        }
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val textColor = if (isRowSelected)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface
                        // All columns in visibleCols order — data cols use per-cell initialPassClickable,
                        // action cols are inline so reordering them is reflected in both header and rows
                        visibleCols.forEach { colId ->
                            if (colId !in actionCols) {
                                val cellText = when (colId) {
                                    "number"     -> song.number
                                    "title"      -> song.title
                                    "songbook"   -> song.songbook
                                    "tune"       -> song.tune
                                    "play_count" -> {
                                        val count = playCountFor(song.songId) ?: 0
                                        if (count > 0) count.toString() else ""
                                    }
                                    "author"     -> song.author
                                    "composer"   -> song.composer
                                    else         -> ""
                                }
                                TooltipArea(
                                    tooltip = {
                                        if (cellText.isNotEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.inverseSurface,
                                                shape = MaterialTheme.shapes.extraSmall,
                                                tonalElevation = 4.dp
                                            ) {
                                                Text(
                                                    cellText,
                                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    },
                                    tooltipPlacement = TooltipPlacement.ComponentRect(
                                        anchor = Alignment.BottomStart,
                                        offset = DpOffset(0.dp, 4.dp)
                                    )
                                ) {
                                    Text(
                                        cellText,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = if (colId == "play_count") TextAlign.End else TextAlign.Start,
                                        modifier = Modifier
                                            .width(with(density) { colWidth(colId).toDp() })
                                            .initialPassClickable {
                                                onSelectSong(index)
                                                if (isPresenting && live.songId != null) {
                                                    onSelectSection(-1)
                                                }
                                                tabFocusRequester.requestFocus()
                                            }
                                            .padding(horizontal = 8.dp),
                                        maxLines = if (colId == "number") Int.MAX_VALUE else 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = textColor
                                    )
                                }
                                Box(modifier = Modifier.width(6.dp))
                            } else {
                                Box(modifier = Modifier.width(6.dp))
                                when (colId) {
                                    "add_to_schedule" -> IconButton(
                                        onClick = { onAddToSchedule?.invoke(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_playlist_add),
                                            contentDescription = stringResource(Res.string.add_to_schedule),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    "favorites" -> {
                                        val isFav = song.songId in favorites
                                        IconButton(
                                            onClick = {
                                                onToggleFavorite(song.songId)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    if (isFav) Res.drawable.ic_star_filled else Res.drawable.ic_star
                                                ),
                                                contentDescription = if (isFav)
                                                    stringResource(Res.string.remove_from_favorites)
                                                else
                                                    stringResource(Res.string.add_to_favorites),
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isFav) MaterialTheme.semantic.favorite
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                        offset = contextMenuOffset
                    ) {
                        if (onAddToSchedule != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.add_to_schedule)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_playlist_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                onClick = {
                                    onAddToSchedule(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId)
                                    showContextMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                val isFav = song.songId in favorites
                                Text(stringResource(if (isFav) Res.string.remove_from_favorites else Res.string.add_to_favorites))
                            },
                            leadingIcon = {
                                val isFav = song.songId in favorites
                                Icon(
                                    painter = painterResource(if (isFav) Res.drawable.ic_star_filled else Res.drawable.ic_star),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isFav) MaterialTheme.semantic.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                onToggleFavorite(song.songId)
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.edit_song)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            },
                            onClick = {
                                dialogs.edit(song)
                                tabFocusRequester.requestFocus()
                                showContextMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.delete_saved_string), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_delete),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                dialogs.delete(song)
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.go_live)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                onSelectSong(index)
                                sendToPresenter(true)
                                onPresenting(Presenting.LYRICS)
                                tabFocusRequester.requestFocus()
                                showContextMenu = false
                            }
                        )
                    }
                    } // Box
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
            } // end horizontalScroll Box
            } // end song list Row (spacer + scroll box)
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = lazyListState)
            )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            HorizontalScrollbar(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                adapter = rememberScrollbarAdapter(hScrollState)
            )
        }
        } // end Column (song list + horizontal scrollbar)

        // ── Favorites panel ───────────────────────────────────────
        val favoriteSongs = favoriteSongs()
        if (favoriteSongs.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onFavoritesExpandedChange(!favoritesExpanded) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (favoritesExpanded) Res.drawable.ic_arrow_down else Res.drawable.ic_arrow_up
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.song_favorites),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                TooltipArea(
                    tooltip = {
                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                            Text(stringResource(Res.string.song_favorites_clear), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = {
                        onClearFavorites()
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete),
                            contentDescription = stringResource(Res.string.song_favorites_clear),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            AnimatedVisibility(visible = favoritesExpanded) {
                Column {
                    // Drag handle — drag up/down to resize panel height
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    onFavPanelHeightChange(
                                        (favPanelHeightPx - delta).coerceIn(
                                            with(density) { 60.dp.toPx() },
                                            with(density) { 400.dp.toPx() },
                                        )
                                    )
                                },
                                onDragStopped = {
                                    onSaveFavPanelHeight()
                                }
                            )
                    )
                    val favGridState = rememberLazyGridState()
                    Box(modifier = Modifier.fillMaxWidth().height(with(density) { favPanelHeightPx.toDp() })) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 180.dp),
                            state = favGridState,
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp)
                        ) {
                            itemsIndexed(favoriteSongs) { _, song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectSongByDetails(
                                                song.number.toIntOrNull() ?: 0,
                                                song.title,
                                                song.songbook,
                                                song.songId
                                            )
                                            tabFocusRequester.requestFocus()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (song.number.isNotBlank()) "${song.number}. ${song.title}" else song.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (onAddToSchedule != null) {
                                        IconButton(
                                            onClick = {
                                                onAddToSchedule(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId)
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_playlist_add),
                                                contentDescription = stringResource(Res.string.add_to_schedule),
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState = favGridState)
                        )
                    }
                }
            }
        }
    }
}
