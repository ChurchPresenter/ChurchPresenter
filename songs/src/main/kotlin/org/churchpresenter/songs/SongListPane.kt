@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.songs

import org.churchpresenter.strings.generated.resources.song_alias_column
import org.churchpresenter.strings.generated.resources.song_order
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.churchpresenter.settings.SongColumnId
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.churchpresenter.sharedui.composables.SearchFieldFocus
import org.churchpresenter.sharedui.composables.rememberSearchFieldValue
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.strings.generated.resources.filter
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.strings.generated.resources.song_favorites
import org.churchpresenter.strings.generated.resources.song_favorites_clear
import org.churchpresenter.strings.generated.resources.song_play_count
import org.churchpresenter.strings.generated.resources.song_element_number
import org.churchpresenter.strings.generated.resources.search
import org.churchpresenter.strings.generated.resources.search_clear
import org.churchpresenter.strings.generated.resources.search_songs
import org.churchpresenter.strings.generated.resources.song_book
import org.churchpresenter.strings.generated.resources.title
import org.churchpresenter.strings.generated.resources.tune
import org.churchpresenter.strings.generated.resources.author
import org.churchpresenter.strings.generated.resources.composer
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.sharedui.composables.searchBarCard
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.field.Dropdown
import org.churchpresenter.hosanna.composables.field.SearchField
import org.churchpresenter.hosanna.composables.list.ListRow
import org.churchpresenter.hosanna.composables.list.RowAction
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.MenuEntry
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

internal const val SONG_LIST_REBUILD_CLICK_WINDOW_MS = 800
internal const val SONG_LIST_REBUILD_CLICK_COUNT = 3
internal const val SONG_LIST_SCROLL_SETTLE_MS = 100L
internal val SONG_LIST_CELL_GAP = 6.dp
internal val SONG_LIST_ACTION_CELL = 24.dp
internal const val SONG_LIST_LANGUAGE_TAG_ALPHA = 0.6f

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

/** The search box, the pickers and the search button share this height, a labelled dropdown's. */
internal val SongsSearchRowHeight = 44.dp

private val SongsPickerWidth = 140.dp

/**
 * The song list down the left of the Songs tab: search and filters, the resizable and reorderable
 * table, and the favourites panel beneath it.
 *
 * Column layout lives in [columns]; everything else arrives as a value or a callback.
 */
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
    /** Where the current search found a song, for the line under its row; null draws no line. */
    searchMatchFor: (SongItem) -> SongSearchMatch?,
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
    searchFocus: SearchFieldFocus = remember { SearchFieldFocus() },
) {
    val density = LocalDensity.current
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val pane = remember(
        columns, dialogs, live, filteredSongs, selectedSongIndex, searchQuery, isLoading, isPresenting, songbooks,
        songbookOptions, selectedSongbook, allSongBooksText, containsText, filterType, filterTypes, filterTypeMap,
        filterTypeDisplayMap, currentSortColumn, currentSortAscending, actionCols, availableCols, visibleCols,
        favorites, favoritesExpanded, favPanelHeightPx, tabFocusRequester, searchFocus, favoriteSongs, playCountFor,
        searchMatchFor,
        onSearchQueryChange, onSearchFocusChanged, onFilterTypeChange, onSongbookChange, onSortChange, onSelectSong,
        onSelectSongByDetails, onSelectSection, onToggleFavorite, onClearFavorites, onReloadSongs, onSaveColumnWidths,
        onSaveColumnOrder, onSaveHiddenColumns, onSaveFavPanelHeight, onFavoritesExpandedChange, onFavPanelHeightChange,
        onAddToSchedule, onPresenting, sendToPresenter, density
    ) {
        SongListScope(
            columns = columns,
            dialogs = dialogs,
            live = live,
            filteredSongs = filteredSongs,
            selectedSongIndex = selectedSongIndex,
            searchQuery = searchQuery,
            isLoading = isLoading,
            isPresenting = isPresenting,
            songbooks = songbooks,
            songbookOptions = songbookOptions,
            selectedSongbook = selectedSongbook,
            allSongBooksText = allSongBooksText,
            containsText = containsText,
            filterType = filterType,
            filterTypes = filterTypes,
            filterTypeMap = filterTypeMap,
            filterTypeDisplayMap = filterTypeDisplayMap,
            currentSortColumn = currentSortColumn,
            currentSortAscending = currentSortAscending,
            actionCols = actionCols,
            availableCols = availableCols,
            visibleCols = visibleCols,
            favorites = favorites,
            favoritesExpanded = favoritesExpanded,
            favPanelHeightPx = favPanelHeightPx,
            tabFocusRequester = tabFocusRequester,
            searchFocus = searchFocus,
            favoriteSongs = favoriteSongs,
            playCountFor = playCountFor,
            searchMatchFor = searchMatchFor,
            onSearchQueryChange = onSearchQueryChange,
            onSearchFocusChanged = onSearchFocusChanged,
            onFilterTypeChange = onFilterTypeChange,
            onSongbookChange = onSongbookChange,
            onSortChange = onSortChange,
            onSelectSong = onSelectSong,
            onSelectSongByDetails = onSelectSongByDetails,
            onSelectSection = onSelectSection,
            onToggleFavorite = onToggleFavorite,
            onClearFavorites = onClearFavorites,
            onReloadSongs = onReloadSongs,
            onSaveColumnWidths = onSaveColumnWidths,
            onSaveColumnOrder = onSaveColumnOrder,
            onSaveHiddenColumns = onSaveHiddenColumns,
            onSaveFavPanelHeight = onSaveFavPanelHeight,
            onFavoritesExpandedChange = onFavoritesExpandedChange,
            onFavPanelHeightChange = onFavPanelHeightChange,
            onAddToSchedule = onAddToSchedule,
            onPresenting = onPresenting,
            sendToPresenter = sendToPresenter,
            density = density,
        )
    }
    pane.SongListColumn(Modifier.weight(1f).fillMaxHeight())
}

@Composable
private fun SongListScope.SongListColumn(modifier: Modifier) {
    Column(modifier = modifier) {
        // Pre-compute column labels (stringResource is @Composable, can't be called in forEach)
        val colHeaderLabels = mapOf(
            SongColumnId.NUMBER     to stringResource(Res.string.song_element_number),
            SongColumnId.ALIAS      to stringResource(Res.string.song_alias_column),
            SongColumnId.TITLE      to stringResource(Res.string.title),
            SongColumnId.SONGBOOK   to stringResource(Res.string.song_book),
            SongColumnId.TUNE       to stringResource(Res.string.tune),
            SongColumnId.PLAY_COUNT to stringResource(Res.string.song_play_count),
            SongColumnId.AUTHOR     to stringResource(Res.string.author),
            SongColumnId.COMPOSER   to stringResource(Res.string.composer),
            SongColumnId.ORDER      to stringResource(Res.string.song_order),
        )
        val allColLabels = colHeaderLabels + mapOf(
            SongColumnId.FAVORITES       to stringResource(Res.string.song_favorites),
            SongColumnId.ADD_TO_SCHEDULE to stringResource(Res.string.add_to_schedule)
        )

        SongSearchBar()

        // Shared horizontal scroll state for header + song list
        val hScrollState = rememberScrollState()

        val contentMinWidthDp = with(density) {
            val colsW = visibleCols.fold(0f) { acc, id -> acc + colWidth(id) }
            // DragHandles (6dp each) only appear after data columns, not after action columns
            val handlesW = visibleCols.count { it !in actionCols } * 6.dp.toPx()
            (colsW + handlesW).toDp() + 16.dp
        }

        // The header and the list share one card.
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 4.dp, bottom = 4.dp).bibleListCard()) {
            SongTableHeader(hScrollState, contentMinWidthDp, allColLabels)

            // Song list + horizontal scrollbar
            SongListBody(hScrollState, contentMinWidthDp, Modifier.weight(1f))
        } // end card

        SongFavoritesPanel(this@SongListColumn)
    }
}

/** Search controls — wraps to a new line when there is not enough space. */
@Composable
private fun SongListScope.SongSearchBar() {
    // Search controls — wraps to new line if not enough space
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        // Bible's search-row inset (BibleSearchRow.kt), so the two tabs' top bars sit on the
        // same margins instead of Songs starting 8.dp further left and 6.dp higher.
        modifier = Modifier.fillMaxWidth()
            .searchBarCard(end = 0.dp)
            .heightIn(min = SongsTopBarMinHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        // Styled search field matching the dropdown aesthetic
        SongSearchField(Modifier.weight(1f).guideTarget(GuideTargets.SONG_SEARCH))

        if (songbooks.size > 1) {
            SongPicker(
                label = stringResource(Res.string.song_book),
                options = songbookOptions,
                selected = selectedSongbook.ifEmpty { allSongBooksText },
                onSelected = { onSongbookChange(it) },
            )
        }

        SongPicker(
            label = stringResource(Res.string.filter),
            options = filterTypes,
            selected = filterTypeDisplayMap[filterType] ?: containsText,
            onSelected = { displayText -> onFilterTypeChange(filterTypeMap[displayText] ?: Constants.CONTAINS) },
            modifier = Modifier.guideTarget(GuideTargets.SONG_SEARCH_FILTER),
        )

        // Hidden rebuild: 3 rapid clicks on the search button force-reloads songs from disk
        var rebuildClickCount by remember { mutableStateOf(0) }
        var rebuildClickTime by remember { mutableStateOf(0L) }
        val searchStr = stringResource(Res.string.search)
        Tooltip(searchStr) {
            IconButton(
                painterResource(IconRes.drawable.ic_search),
                searchStr,
                {
                    val now = System.currentTimeMillis()
                    if (now - rebuildClickTime > SONG_LIST_REBUILD_CLICK_WINDOW_MS) rebuildClickCount = 0
                    rebuildClickCount++
                    rebuildClickTime = now
                    if (rebuildClickCount >= SONG_LIST_REBUILD_CLICK_COUNT) {
                        rebuildClickCount = 0
                        onReloadSongs()
                    }
                },
                Modifier.size(SongsSearchRowHeight),
                size = IconButtonSize.Large,
                hierarchy = ButtonHierarchy.Primary,
                loading = isLoading,
            )
        }
    }
}

/** A songbook or filter picker: the options keyed by their own text, as the tab hands them back. */
@Composable
private fun SongPicker(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dropdown(
        entries = options.map { MenuEntry.Item(it, it) },
        selectedKey = selected.takeIf { it in options },
        onSelect = onSelected,
        modifier = modifier.widthIn(min = SongsPickerWidth),
        label = label,
    )
}

/** The song search box: Enter hands the keyboard to the list, and clearing it does too. */
@Composable
private fun SongListScope.SongSearchField(modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val report by rememberUpdatedState(onSearchFocusChanged)
    LaunchedEffect(focused) { report(focused) }
    var fieldValue by rememberSearchFieldValue(searchQuery, searchFocus)
    SearchField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != searchQuery) onSearchQueryChange(it.text)
        },
        placeholder = stringResource(Res.string.search_songs),
        clearContentDescription = stringResource(Res.string.search_clear),
        modifier = modifier.widthIn(min = 120.dp).height(SongsSearchRowHeight),
        interactionSource = source,
        // Go Live and the arrow keys are the tab's (SongsTabKeys). Enter still hands the keyboard to
        // the list when Go Live is bound to something else.
        onSearch = { tabFocusRequester.requestFocus() },
        // Back to the tab, not to the clear button -- a following Enter or Space would re-fire it.
        onClear = {
            onSearchQueryChange("")
            tabFocusRequester.requestFocus()
        },
        focusRequester = searchFocus.requester,
    )
}

/** The favourites panel under the list: a header, and a resizable grid when expanded. */
@Composable
private fun ColumnScope.SongFavoritesPanel(pane: SongListScope) {
    with(pane) {
        // ── Favorites panel ───────────────────────────────────────
        val favoriteSongs = favoriteSongs()
        if (favoriteSongs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SongFavoritesHeader()
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
                                SongFavoriteItem(song)
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

@Composable
private fun SongListScope.SongFavoritesHeader() {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onFavoritesExpandedChange(!favoritesExpanded) }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                if (favoritesExpanded) IconRes.drawable.ic_arrow_down else IconRes.drawable.ic_arrow_up
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
        val clearStr = stringResource(Res.string.song_favorites_clear)
        Tooltip(clearStr) {
            IconButton(
                painterResource(IconRes.drawable.ic_delete),
                clearStr,
                { onClearFavorites() },
                size = IconButtonSize.Small,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
            )
        }
    }
}

@Composable
private fun SongListScope.SongFavoriteItem(song: SongItem) {
    val addStr = stringResource(Res.string.add_to_schedule)
    ListRow(
        headline = if (song.number.isNotBlank()) "${song.number}. ${song.title}" else song.title,
        onClick = {
            onSelectSongByDetails(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId)
            tabFocusRequester.requestFocus()
        },
        actions = onAddToSchedule?.let { add ->
            {
                RowAction(
                    painterResource(IconRes.drawable.ic_playlist_add),
                    addStr,
                    { add(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId) },
                )
            }
        },
        keepActions = true,
    )
}
