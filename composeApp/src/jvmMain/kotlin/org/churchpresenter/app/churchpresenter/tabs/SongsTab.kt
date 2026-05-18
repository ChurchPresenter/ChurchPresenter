package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondary
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import churchpresenter.composeapp.generated.resources.songs_indexing
import churchpresenter.composeapp.generated.resources.songs_no_db_title
import churchpresenter.composeapp.generated.resources.songs_no_db_hint
import churchpresenter.composeapp.generated.resources.songs_no_db_step
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_favorites
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.all_song_books
import churchpresenter.composeapp.generated.resources.contains
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.back_to_live
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.line_navigation_hint
import churchpresenter.composeapp.generated.resources.new_song
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.no_lyrics_available
import churchpresenter.composeapp.generated.resources.remove_from_favorites
import churchpresenter.composeapp.generated.resources.song_favorites
import churchpresenter.composeapp.generated.resources.song_favorites_clear
import churchpresenter.composeapp.generated.resources.song_play_count
import churchpresenter.composeapp.generated.resources.song_columns
import churchpresenter.composeapp.generated.resources.number
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.search_songs
import churchpresenter.composeapp.generated.resources.song_book
import churchpresenter.composeapp.generated.resources.song_title_slide
import churchpresenter.composeapp.generated.resources.starts_with
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.tune
import churchpresenter.composeapp.generated.resources.author
import churchpresenter.composeapp.generated.resources.composer
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.dialogs.EditSongDialog
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
    viewModel: SongsViewModel,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((songNumber: Int, title: String, songbook: String, songId: String) -> Unit)? = null,
    selectedSongItem: ScheduleItem.SongItem? = null,
    selectedSongItemVersion: Int = 0,
    onSongItemSelected: (LyricSection) -> Unit,
    onAllSectionsChanged: (List<LyricSection>) -> Unit = {},
    onSectionIndexChanged: (Int) -> Unit = {},
    onLineIndexChanged: (Int) -> Unit = {},
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    isPresenting: Boolean = false,
    theme: ThemeMode = ThemeMode.SYSTEM,
    statisticsManager: StatisticsManager? = null
) {
    LaunchedEffect(statisticsManager) { viewModel.setStatisticsManager(statisticsManager) }

    // Reload songs whenever the storage directory changes (e.g. after settings are saved)
    val isFirstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(appSettings.songSettings.storageDirectory) {
        if (isFirstComposition.value) {
            isFirstComposition.value = false
        } else {
            viewModel.updateSettings(appSettings)
        }
    }

    // React to schedule item selection.
    // If data is still loading when the item arrives, wait for loading to finish
    // then retry — no fixed delay, no polling, no race condition.
    // Observe ViewModel state
    val songbooks by viewModel.songbooks
    val searchQuery by viewModel.searchQuery
    val selectedSongbook by viewModel.selectedSongbook
    val filterType by viewModel.filterType
    val selectedSongIndex by viewModel.selectedSongIndex
    val selectedSectionIndex by viewModel.selectedSectionIndex
    val filteredSongs by viewModel.filteredSongItems
    val isLoading by viewModel.isLoading
    val currentSortColumn by viewModel.sortColumn
    val currentSortAscending by viewModel.sortAscending

    // Edit Song Dialog state (pure UI state — fine to keep here)
    var showEditDialog by remember { mutableStateOf(false) }
    var songToEdit by remember { mutableStateOf<SongItem?>(null) }
    var showNewSongDialog by remember { mutableStateOf(false) }

    // Favorites panel state
    var favoritesExpanded by remember { mutableStateOf(true) }
    val favorites by viewModel.favorites

    // Track which song/section/line is currently live on the presenter
    var liveSongIndex by remember { mutableStateOf(-1) }
    var liveSectionIndex by remember { mutableStateOf(0) }
    var liveLineIndex by remember { mutableStateOf(0) }

    // Track whether the title slide entry is currently selected in the lyrics panel
    var isTitleSlideSelected by remember { mutableStateOf(false) }

    // Reset title-slide selection whenever the active song changes
    LaunchedEffect(selectedSongIndex) { isTitleSlideSelected = false }

    // Helper: push current viewModel selection to presenter and track as live
    fun sendToPresenter() {
        onAllSectionsChanged(viewModel.getLyricSections())
        onSectionIndexChanged(viewModel.selectedSectionIndex.value)
        onLineIndexChanged(viewModel.selectedLineIndex.value)
        viewModel.getSelectedLyricSection()?.let { onSongItemSelected(it) }
        // Record song display for statistics — only when a different song is presented
        val idx = viewModel.selectedSongIndex.value
        if (idx != liveSongIndex) {
            val items = viewModel.filteredSongItems.value
            if (idx in items.indices) {
                val song = items[idx]
                statisticsManager?.recordSongDisplay(
                    songNumber = song.number.toIntOrNull() ?: 0,
                    title = song.title,
                    songbook = song.songbook,
                    author = song.author
                )
            }
        }
        liveSongIndex = viewModel.selectedSongIndex.value
        liveSectionIndex = viewModel.selectedSectionIndex.value
        liveLineIndex = viewModel.selectedLineIndex.value
    }

    // React to schedule item selection
    // Uses selectedSongItemVersion as a key so clicking the same song twice always re-fires
    LaunchedEffect(selectedSongItem, selectedSongItemVersion) {
        selectedSongItem?.let { item ->
            // Wait until data is ready if currently loading
            if (viewModel.isLoading.value) {
                snapshotFlow { viewModel.isLoading.value }
                    .first { !it }
            }
            val found = viewModel.selectSongByDetails(item.songNumber, item.title, item.songbook, item.songId)
            if (found) {
                sendToPresenter()
            }
        }
    }

    // String resources
    val newSongStr = stringResource(Res.string.new_song)
    val backToLiveStr = stringResource(Res.string.back_to_live)
    val lineNavHintStr = stringResource(Res.string.line_navigation_hint)
    val allSongBooksText = stringResource(Res.string.all_song_books)

    // Prepend "All" option to songbooks
    val songbookOptions = remember(songbooks) { listOf(allSongBooksText) + songbooks }

    // String resources for filter types
    val containsText = stringResource(Res.string.contains)
    val startsWithText = stringResource(Res.string.starts_with)
    val exactMatchText = stringResource(Res.string.exact_match)

    val filterTypes = listOf(containsText, startsWithText, exactMatchText)

    val filterTypeMap = mapOf(
        containsText to Constants.CONTAINS,
        startsWithText to Constants.STARTS_WITH,
        exactMatchText to Constants.EXACT_MATCH
    )
    val filterTypeDisplayMap = mapOf(
        Constants.CONTAINS to containsText,
        Constants.STARTS_WITH to startsWithText,
        Constants.EXACT_MATCH to exactMatchText
    )

    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

    val tabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { tabFocusRequester.requestFocus() }

    // Column widths driven by settings; local state for smooth dragging
    var colWNumber by remember(appSettings.songSettings.colWidthNumber) {
        mutableStateOf(with(density) { appSettings.songSettings.colWidthNumber.dp.toPx() })
    }
    var colWTitle by remember(appSettings.songSettings.colWidthTitle) {
        mutableStateOf(with(density) { appSettings.songSettings.colWidthTitle.dp.toPx() })
    }
    var colWSongbook by remember(appSettings.songSettings.colWidthSongbook) {
        mutableStateOf(with(density) { appSettings.songSettings.colWidthSongbook.dp.toPx() })
    }
    var colWTune by remember(appSettings.songSettings.colWidthTune) {
        mutableStateOf(with(density) { appSettings.songSettings.colWidthTune.dp.toPx() })
    }
    var colWPlayCount by remember(appSettings.songSettings.colWidthPlayCount) {
        mutableStateOf(with(density) { appSettings.songSettings.colWidthPlayCount.dp.toPx() })
    }
    var colWAuthor by remember(appSettings.songSettings.colWidthAuthor) {
        mutableStateOf(with(density) { appSettings.songSettings.colWidthAuthor.dp.toPx() })
    }
    var colWComposer by remember(appSettings.songSettings.colWidthComposer) {
        mutableStateOf(with(density) { appSettings.songSettings.colWidthComposer.dp.toPx() })
    }

    // Favorites panel height in px
    var favPanelHeightPx by remember(appSettings.songFavoritesPanelHeightDp) {
        mutableStateOf(with(density) { appSettings.songFavoritesPanelHeightDp.dp.toPx() })
    }

    // Column order — "songbook" excluded when only one songbook loaded;
    // "add_to_schedule" excluded when the callback is absent
    val actionCols = setOf("favorites", "add_to_schedule")
    val availableCols = buildList {
        add("number"); add("title")
        if (songbooks.size > 1) add("songbook")
        add("tune")
        add("play_count")
        add("author")
        add("composer")
        if (onAddToSchedule != null) add("add_to_schedule")
        add("favorites")
    }
    var colOrder by remember(appSettings.songColOrder, songbooks.size) {
        val saved = appSettings.songColOrder
        val order = if (saved.isEmpty()) availableCols
        else {
            val filtered = saved.filter { it in availableCols }
            val missing = availableCols.filter { it !in filtered }
            filtered + missing
        }
        mutableStateOf(order)
    }
    // Drag-to-reorder state
    var draggingColId by remember { mutableStateOf<String?>(null) }
    var dragAccumX by remember { mutableStateOf(0f) }

    // Column visibility
    var hiddenCols by remember(appSettings.songHiddenCols) {
        mutableStateOf(appSettings.songHiddenCols)
    }
    var showColMenu by remember { mutableStateOf(false) }
    var colMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var showColumnsMenu by remember { mutableStateOf(false) }
    // Plain val — recomputed on every recomposition so it always reflects the current
    // colOrder and hiddenCols state objects (remember(key){} creates new MutableState on
    // each settings save, which would silently break a derivedStateOf subscription).
    val visibleCols = colOrder.filter { it !in hiddenCols }

    // Panel split — lyrics panel width in px; 0 means "not yet set, use half of row"
    var lyricsPanelPx by remember(appSettings.songSettings.lyricsPanelWidthDp) {
        val saved = appSettings.songSettings.lyricsPanelWidthDp
        mutableStateOf(if (saved > 0) with(density) { saved.dp.toPx() } else 0f)
    }
    var rowTotalWidth by remember { mutableStateOf(0f) }

    fun saveColWidths() {
        onSettingsChangeState.value { s ->
            s.copy(
                songSettings = s.songSettings.copy(
                    colWidthNumber      = with(density) { colWNumber.toDp().value.toInt() },
                    colWidthTitle       = with(density) { colWTitle.toDp().value.toInt() },
                    colWidthSongbook    = with(density) { colWSongbook.toDp().value.toInt() },
                    colWidthTune        = with(density) { colWTune.toDp().value.toInt() },
                    colWidthPlayCount   = with(density) { colWPlayCount.toDp().value.toInt() },
                    colWidthAuthor      = with(density) { colWAuthor.toDp().value.toInt() },
                    colWidthComposer    = with(density) { colWComposer.toDp().value.toInt() },
                    lyricsPanelWidthDp  = with(density) { lyricsPanelPx.toDp().value.toInt() }
                ),
                songColOrder = colOrder,
                songHiddenCols = hiddenCols
            )
        }
    }

    fun colWidth(id: String) = when (id) {
        "number"     -> colWNumber
        "title"      -> colWTitle
        "songbook"   -> colWSongbook
        "tune"       -> colWTune
        "play_count" -> colWPlayCount
        "author"     -> colWAuthor
        "composer"   -> colWComposer
        else         -> with(density) { 30.dp.toPx() } // action columns: 6dp spacer + 24dp icon button
    }

    fun setColWidth(id: String, px: Float) {
        when (id) {
            "number"     -> colWNumber    = px.coerceIn(with(density) { 30.dp.toPx() },  with(density) { 200.dp.toPx() })
            "title"      -> colWTitle     = px.coerceIn(with(density) { 60.dp.toPx() },  with(density) { 600.dp.toPx() })
            "songbook"   -> colWSongbook  = px.coerceIn(with(density) { 40.dp.toPx() },  with(density) { 300.dp.toPx() })
            "tune"       -> colWTune      = px.coerceIn(with(density) { 40.dp.toPx() },  with(density) { 300.dp.toPx() })
            "play_count" -> colWPlayCount = px.coerceIn(with(density) { 30.dp.toPx() },  with(density) { 150.dp.toPx() })
            "author"     -> colWAuthor    = px.coerceIn(with(density) { 40.dp.toPx() },  with(density) { 400.dp.toPx() })
            "composer"   -> colWComposer  = px.coerceIn(with(density) { 40.dp.toPx() },  with(density) { 400.dp.toPx() })
        }
    }

    fun sortKey(id: String) = when (id) {
        "number"     -> Constants.SORT_NUMBER
        "title"      -> Constants.SORT_TITLE
        "songbook"   -> Constants.SORT_SONGBOOK
        "tune"       -> Constants.SORT_TUNE
        "play_count" -> Constants.SORT_PLAY_COUNT
        "favorites"  -> Constants.SORT_FAVORITES
        "author"     -> Constants.SORT_AUTHOR
        "composer"   -> Constants.SORT_COMPOSER
        else         -> ""
    }

    // NOTE: operates on visibleCols (set after state vars), returns index within visibleCols
    fun computeNewIdx(draggedId: String, accumX: Float, visibleCols: List<String>): Int {
        val currentIdx = visibleCols.indexOf(draggedId)
        if (currentIdx < 0) return 0
        val handleW = with(density) { 6.dp.toPx() }
        var remaining = accumX
        var newIdx = currentIdx
        if (remaining > 0f) {
            var i = currentIdx + 1
            while (i < visibleCols.size) {
                val w = colWidth(visibleCols[i]) + handleW
                if (remaining >= w / 2f) { newIdx = i; remaining -= w } else break
                i++
            }
        } else {
            var i = currentIdx - 1
            while (i >= 0) {
                val w = colWidth(visibleCols[i]) + handleW
                if (-remaining >= w / 2f) { newIdx = i; remaining += w } else break
                i--
            }
        }
        return newIdx
    }

    @Composable
    fun DragHandle(colId: String, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
        val currentOnDrag by rememberUpdatedState(onDrag)
        val currentOnDragEnd by rememberUpdatedState(onDragEnd)
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(colId) {
                    detectHorizontalDragGestures(onDragEnd = { currentOnDragEnd() }) { _, amount ->
                        currentOnDrag(amount)
                    }
                }
        )
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                rowTotalWidth = size.width.toFloat()
                if (lyricsPanelPx == 0f) {
                    lyricsPanelPx = rowTotalWidth / 2f
                }
            }
            .focusRequester(tabFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isLineMode = appSettings.songSettings.fullscreenDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                        appSettings.songSettings.lowerThirdDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                        appSettings.songSettings.lookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                        appSettings.songSettings.lowerThirdLookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (isLineMode) {
                                viewModel.navigatePreviousLine()
                                sendToPresenter()
                            } else if (!isPresenting) {
                                viewModel.navigatePreviousSong()
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (isLineMode) {
                                viewModel.navigateNextLine()
                                sendToPresenter()
                            } else if (!isPresenting) {
                                viewModel.navigateNextSong()
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (!viewModel.navigatePreviousSection() && !isPresenting) viewModel.navigatePreviousSong()
                            sendToPresenter()
                            true
                        }
                        Key.DirectionDown -> {
                            if (!viewModel.navigateNextSection() && !isPresenting) viewModel.navigateNextSong()
                            sendToPresenter()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Left panel — Search and song list (fills remaining space)
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
                modifier = Modifier.fillMaxWidth().padding(all = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f).widthIn(min = 120.dp),
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = {
                        Text(stringResource(Res.string.search_songs), style = MaterialTheme.typography.bodyMedium)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors().copy(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )

                if (songbooks.size > 1) {
                    DropdownSelector(
                        modifier = Modifier.width(160.dp),
                        label = "",
                        items = songbookOptions,
                        selected = selectedSongbook.ifEmpty { allSongBooksText },
                        onSelectedChange = { viewModel.updateSelectedSongbook(it) }
                    )
                }

                DropdownSelector(
                    modifier = Modifier.width(160.dp),
                    label = "",
                    items = filterTypes,
                    selected = filterTypeDisplayMap[filterType] ?: containsText,
                    onSelectedChange = { displayText ->
                        val internalKey = filterTypeMap[displayText] ?: Constants.CONTAINS
                        viewModel.updateFilterType(internalKey)
                    }
                )

                // Hidden rebuild: 3 rapid clicks on the search button force-reloads songs from disk
                var rebuildClickCount by remember { mutableStateOf(0) }
                var rebuildClickTime by remember { mutableStateOf(0L) }
                IconButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - rebuildClickTime > 800) rebuildClickCount = 0
                        rebuildClickCount++
                        rebuildClickTime = now
                        if (rebuildClickCount >= 3) {
                            rebuildClickCount = 0
                            viewModel.loadSongs()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(20.dp))
                    }
                }

            }

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
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press &&
                                    event.button?.isSecondary == true
                                ) {
                                    val pos = event.changes.firstOrNull()?.position
                                    if (pos != null) colMenuOffset = with(density) { DpOffset(pos.x.toDp(), pos.y.toDp()) }
                                    showColMenu = true
                                }
                            }
                        }
                    }
            ) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            // Column visibility button — fixed at start, does not scroll
            Box {
                TooltipIconButton(
                    painter = rememberVectorPainter(Icons.Default.Tune),
                    text = stringResource(Res.string.song_columns),
                    onClick = { showColumnsMenu = true },
                    buttonSize = 36.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )
                DropdownMenu(
                    expanded = showColumnsMenu,
                    onDismissRequest = { showColumnsMenu = false }
                ) {
                    availableCols.forEach { colId ->
                        val isVisible = colId !in hiddenCols
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
                                    hiddenCols = if (isVisible) hiddenCols + colId else hiddenCols - colId
                                    onSettingsChangeState.value { s -> s.copy(songHiddenCols = hiddenCols) }
                                }
                            },
                            enabled = !(isProtected && isVisible)
                        )
                    }
                }
            }
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
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleCols.forEach { colId ->
                    val isBeingDragged = colId == draggingColId
                    val sk = sortKey(colId)
                    val isSortable = sk.isNotEmpty() && colId != "add_to_schedule"
                    val isSorted = isSortable && currentSortColumn == sk
                    val reorderDragMod = Modifier.pointerInput(colId) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val vc = colOrder.filter { it !in hiddenCols }
                                val newVisIdx = computeNewIdx(colId, dragAccumX, vc)
                                val targetId = vc.getOrNull(newVisIdx)
                                if (targetId != null) {
                                    val fromIdx = colOrder.indexOf(colId)
                                    val toIdx = colOrder.indexOf(targetId)
                                    if (toIdx != fromIdx) {
                                        val mutable = colOrder.toMutableList()
                                        mutable.removeAt(fromIdx)
                                        mutable.add(toIdx.coerceIn(0, mutable.size), colId)
                                        colOrder = mutable
                                        onSettingsChangeState.value { s -> s.copy(songColOrder = colOrder) }
                                    }
                                }
                                draggingColId = null
                                dragAccumX = 0f
                            },
                            onDragCancel = { draggingColId = null; dragAccumX = 0f }
                        ) { _, amount ->
                            if (draggingColId != colId) { draggingColId = colId; dragAccumX = 0f }
                            dragAccumX += amount
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
                                .then(if (isSortable) Modifier.clickable { viewModel.updateSort(sk) } else Modifier)
                                .then(reorderDragMod),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
                                .then(if (isSortable) Modifier.clickable { viewModel.updateSort(sk) } else Modifier)
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
                            onDragEnd = ::saveColWidths
                        )
                    }
                }
            }
            } // end inner scrollable header Box
            } // end header Row

            // Right-click dropdown — toggle column visibility
            DropdownMenu(
                expanded = showColMenu,
                onDismissRequest = { showColMenu = false },
                offset = colMenuOffset
            ) {
                availableCols.forEach { colId ->
                    val isVisible = colId !in hiddenCols
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
                                hiddenCols = if (isVisible) hiddenCols + colId else hiddenCols - colId
                                onSettingsChangeState.value { s -> s.copy(songHiddenCols = hiddenCols) }
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
                        delay(100)
                        lazyListState.animateScrollToItem(selectedSongIndex)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScrollState)
                ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .width(contentMinWidthDp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(filteredSongs) { index, song ->
                        var showContextMenu by remember { mutableStateOf(false) }
                        var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
                        Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index == selectedSongIndex) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
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
                            val textColor = if (index == selectedSongIndex)
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
                                            val count = statisticsManager?.getSongPlayCount(song.songbook, song.number.toIntOrNull() ?: 0) ?: 0
                                            if (count > 0) count.toString() else ""
                                        }
                                        "author"     -> song.author
                                        "composer"   -> song.composer
                                        else         -> ""
                                    }
                                    Text(
                                        cellText,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = if (colId == "play_count") TextAlign.End else TextAlign.Start,
                                        modifier = Modifier
                                            .width(with(density) { colWidth(colId).toDp() })
                                            .initialPassClickable {
                                                viewModel.selectSong(index)
                                                if (isPresenting && liveSongIndex >= 0) {
                                                    viewModel.selectSection(-1)
                                                }
                                                tabFocusRequester.requestFocus()
                                            }
                                            .padding(horizontal = 8.dp),
                                        maxLines = if (colId == "number") Int.MAX_VALUE else 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = textColor
                                    )
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
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        "favorites" -> {
                                            val isFav = song.songId in favorites
                                            IconButton(
                                                onClick = {
                                                    viewModel.toggleFavorite(song.songId)
                                                    onSettingsChangeState.value { s ->
                                                        s.copy(songFavorites = viewModel.favorites.value.toList())
                                                    }
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
                                                    tint = if (isFav) Color(0xFFFFC107)
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
                                        tint = if (isFav) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    viewModel.toggleFavorite(song.songId)
                                    onSettingsChangeState.value { s ->
                                        s.copy(songFavorites = viewModel.favorites.value.toList())
                                    }
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
                                    songToEdit = song
                                    showEditDialog = true
                                    tabFocusRequester.requestFocus()
                                    showContextMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.go_live)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_cast),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    viewModel.selectSong(index)
                                    sendToPresenter()
                                    onPresenting(Presenting.LYRICS)
                                    tabFocusRequester.requestFocus()
                                    showContextMenu = false
                                }
                            )
                        }
                        } // Box
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
                } // end horizontalScroll Box
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = lazyListState)
                )
                }
            }
            HorizontalScrollbar(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                adapter = rememberScrollbarAdapter(hScrollState)
            )
            } // end Column (song list + horizontal scrollbar)

            // ── Favorites panel ───────────────────────────────────────
            val favoriteSongs = viewModel.getFavoriteSongs()
            if (favoriteSongs.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { favoritesExpanded = !favoritesExpanded }
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
                            viewModel.clearFavorites()
                            onSettingsChangeState.value { s -> s.copy(songFavorites = emptyList()) }
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
                                .pointerHoverIcon(PointerIcon.Hand)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            onSettingsChangeState.value { s ->
                                                s.copy(songFavoritesPanelHeightDp = with(density) { favPanelHeightPx.toDp().value.toInt() })
                                            }
                                        }
                                    ) { _, amount ->
                                        favPanelHeightPx = (favPanelHeightPx - amount)
                                            .coerceIn(
                                                with(density) { 60.dp.toPx() },
                                                with(density) { 400.dp.toPx() }
                                            )
                                    }
                                }
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
                                            .initialPassClickable {
                                                viewModel.selectSongByDetails(
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
                                            modifier = Modifier.weight(1f)
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

        // Vertical drag handle — resize lyrics panel
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { saveColWidths() }
                    ) { _, amount ->
                        lyricsPanelPx = (lyricsPanelPx - amount)
                            .coerceIn(
                                with(density) { 150.dp.toPx() },
                                with(density) { 800.dp.toPx() }
                            )
                    }
                }
        )

        // Right panel — Lyrics display (fixed width, resizable via drag handle)
        Column(
            modifier = Modifier
                .width(with(density) { lyricsPanelPx.toDp() })
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            // Header row with action buttons — switches to icon-only when width is tight
            val editSongStr    = stringResource(Res.string.edit_song)
            val goLiveStr      = stringResource(Res.string.go_live)
            val addScheduleStr = stringResource(Res.string.add_to_schedule)

            val hasSongSelected = selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size && selectedSectionIndex >= 0
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                    TooltipArea(
                        tooltip = {
                            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                Text(editSongStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        IconButton(
                            onClick = { songToEdit = filteredSongs[selectedSongIndex]; showEditDialog = true; tabFocusRequester.requestFocus() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = editSongStr, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // New Song button
                TooltipArea(
                    tooltip = {
                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                            Text(newSongStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = { showNewSongDialog = true; tabFocusRequester.requestFocus() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = newSongStr, modifier = Modifier.size(20.dp))
                    }
                }

                if (onAddToSchedule != null && selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                    TooltipArea(
                        tooltip = {
                            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                Text(addScheduleStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        IconButton(
                            onClick = {
                                filteredSongs.getOrNull(selectedSongIndex)?.let { item ->
                                    onAddToSchedule(item.number.toIntOrNull() ?: 0, item.title, item.songbook, item.songId)
                                }
                                tabFocusRequester.requestFocus()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = addScheduleStr, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                TooltipArea(
                    tooltip = {
                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                            Text(goLiveStr, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = { sendToPresenter(); onPresenting(Presenting.LYRICS); tabFocusRequester.requestFocus() },
                        enabled = hasSongSelected,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_cast), contentDescription = goLiveStr, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // "Back to Live" button — shown when browsing a different song than what's live
            if (isPresenting && liveSongIndex >= 0 && selectedSongIndex != liveSongIndex) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.selectSong(liveSongIndex)
                        viewModel.selectSection(liveSectionIndex)
                        viewModel.setLineIndex(liveLineIndex)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(backToLiveStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onError, maxLines = 1)
                }
            }

            // Arrow key navigation hint — only in line mode
            val isLineModeHint = appSettings.songSettings.fullscreenDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                    appSettings.songSettings.lowerThirdDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                    appSettings.songSettings.lookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                    appSettings.songSettings.lowerThirdLookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE
            if (isLineModeHint) {
                Text(
                    text = lineNavHintStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Lyrics content
            val noSongsLoaded = filteredSongs.isEmpty() && searchQuery.isBlank()
            if (noSongsLoaded) {
                // ── Empty state: no song database configured ──────────────
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 320.dp),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🎵",
                                style = MaterialTheme.typography.displaySmall
                            )
                            Text(
                                text = stringResource(Res.string.songs_no_db_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = stringResource(Res.string.songs_no_db_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(Res.string.songs_no_db_step),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
            // ── Normal lyrics view ────────────────────────────────────
            Box {
                val lyricsListState = rememberLazyListState()
                val titleSlideEnabled = appSettings.songSettings.titleSlideEnabled
                val currentSong = filteredSongs.getOrNull(selectedSongIndex)

                LaunchedEffect(selectedSectionIndex, isTitleSlideSelected) {
                    if (isTitleSlideSelected) {
                        lyricsListState.animateScrollToItem(0)
                    } else if (selectedSectionIndex >= 0) {
                        val offset = if (titleSlideEnabled && currentSong != null) 1 else 0
                        lyricsListState.animateScrollToItem(selectedSectionIndex + offset)
                    }
                }

                // Get lyric sections from ViewModel — no parsing in UI
                val sections = if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                    viewModel.getLyricSections()
                } else emptyList()

                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    // ── Title slide entry ────────────────────────────────────
                    if (titleSlideEnabled && currentSong != null && sections.isNotEmpty()) {
                        item {
                            val titleLine = listOf(currentSong.number, currentSong.title)
                                .filter { it.isNotBlank() }.joinToString(" – ")
                            val creditLine = listOf(currentSong.author, currentSong.composer)
                                .filter { it.isNotBlank() }.joinToString(" / ")

                            fun buildTitleSection() = LyricSection(
                                type = "title_slide",
                                title = currentSong.title,
                                secondaryTitle = currentSong.secondaryTitle,
                                songNumber = currentSong.number.toIntOrNull() ?: 0,
                                lines = buildList {
                                    add(titleLine)
                                    if (creditLine.isNotBlank()) add(creditLine)
                                }
                            )

                            fun sendTitleSlide() {
                                val ts = buildTitleSection()
                                val allSections = listOf(ts) + viewModel.getLyricSections()
                                onAllSectionsChanged(allSections)
                                onSectionIndexChanged(0)
                                onLineIndexChanged(0)
                                onSongItemSelected(ts)
                                isTitleSlideSelected = true
                                liveSongIndex = selectedSongIndex
                                liveSectionIndex = -1
                                liveLineIndex = 0
                            }

                            val contentColor = if (isTitleSlideSelected)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isTitleSlideSelected)
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .initialPassCombinedClickable(
                                        onClick = { sendTitleSlide() },
                                        onDoubleClick = { sendTitleSlide(); onPresenting(Presenting.LYRICS) }
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.song_title_slide),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    text = titleLine,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                if (creditLine.isNotBlank()) {
                                    Text(
                                        text = creditLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }

                    // ── Regular lyric sections ───────────────────────────────
                    if (sections.isNotEmpty()) {
                        itemsIndexed(sections) { sectionIndex, section ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (!isTitleSlideSelected && sectionIndex == selectedSectionIndex)
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .initialPassCombinedClickable(
                                        onClick = {
                                            viewModel.selectSection(sectionIndex)
                                            isTitleSlideSelected = false
                                            sendToPresenter()
                                        },
                                        onDoubleClick = {
                                            viewModel.selectSection(sectionIndex)
                                            isTitleSlideSelected = false
                                            sendToPresenter()
                                            onPresenting(Presenting.LYRICS)
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                val textColor = if (!isTitleSlideSelected && sectionIndex == selectedSectionIndex)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface

                                val isPerLineMode = appSettings.songSettings.fullscreenDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                                    appSettings.songSettings.lowerThirdDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                                    appSettings.songSettings.lookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
                                    appSettings.songSettings.lowerThirdLookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE
                                val activeLineIndex = if (isPerLineMode && sectionIndex == selectedSectionIndex)
                                    viewModel.selectedLineIndex.value else -1

                                // Render section header if present
                                section.header?.let { header ->
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                // Lyrics panel always shows both — language filtering only applies to presenter
                                val langDisplay = Constants.SONG_LANG_BOTH
                                val showPrimary = langDisplay != Constants.SONG_LANG_SECONDARY
                                val showSecondary = langDisplay != Constants.SONG_LANG_PRIMARY && section.secondaryLines.isNotEmpty()

                                val lineClickHandler: ((Int) -> Unit)? = if (isPerLineMode) { lineIdx ->
                                    viewModel.selectSection(sectionIndex)
                                    viewModel.setLineIndex(lineIdx)
                                    isTitleSlideSelected = false
                                    sendToPresenter()
                                } else null

                                if (showPrimary && showSecondary) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            LyricLines(section.lines, textColor, activeLineIndex, lineClickHandler)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            LyricLines(section.secondaryLines, textColor, activeLineIndex, lineClickHandler)
                                        }
                                    }
                                } else if (showSecondary) {
                                    LyricLines(section.secondaryLines, textColor, activeLineIndex, lineClickHandler)
                                } else {
                                    LyricLines(section.lines, textColor, activeLineIndex, lineClickHandler)
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = stringResource(Res.string.no_lyrics_available),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = lyricsListState)
                )
            }
            } // end else (songs loaded)
        }
    }

    // Edit Song Dialog — pure UI dialog state is fine here
    EditSongDialog(
        isVisible = showEditDialog,
        song = songToEdit,
        songbooks = viewModel.songbooks.value,
        existingSongs = viewModel.songsData.value.getSongs(),
        theme = theme,
        onDismiss = { showEditDialog = false },
        onSave = { updatedSong ->
            songToEdit?.let { oldSong ->
                val success = viewModel.updateSong(oldSong, updatedSong)
                if (success) {
                    songToEdit = null
                    showEditDialog = false
                }
            }
        }
    )

    // New Song Dialog
    val newSongTemplate = remember {
        val templateLyrics = listOf("[Verse 1]", "", "", "[Chorus]", "", "", "[Verse 2]", "", "", "[Verse 3]", "", "")
        SongItem(
            number = "",
            title = "",
            songbook = "",
            lyrics = templateLyrics,
            secondaryLyrics = templateLyrics
        )
    }
    EditSongDialog(
        isVisible = showNewSongDialog,
        song = newSongTemplate,
        songbooks = viewModel.songbooks.value,
        existingSongs = viewModel.songsData.value.getSongs(),
        isNewSong = true,
        theme = theme,
        onDismiss = { showNewSongDialog = false },
        onSave = { newSong ->
            val success = viewModel.createSong(newSong)
            if (success) {
                showNewSongDialog = false
            }
        }
    )
}

@Composable
private fun LyricLines(lines: List<String>, textColor: Color, activeLineIndex: Int = -1, onLineClick: ((Int) -> Unit)? = null) {
    lines.forEachIndexed { lineIndex, line ->
        val isActiveLine = activeLineIndex >= 0 && lineIndex == activeLineIndex
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
            color = if (isActiveLine) MaterialTheme.colorScheme.primary else textColor,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .then(if (onLineClick != null) Modifier.initialPassClickable { onLineClick(lineIndex) } else Modifier)
        )
    }
}
