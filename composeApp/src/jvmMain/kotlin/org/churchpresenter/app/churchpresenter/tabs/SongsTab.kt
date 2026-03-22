package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import churchpresenter.composeapp.generated.resources.Res
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
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.no_lyrics_available
import churchpresenter.composeapp.generated.resources.number
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.search_songs
import churchpresenter.composeapp.generated.resources.song_book
import churchpresenter.composeapp.generated.resources.starts_with
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.tune
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
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
    onSongsLoaded: ((List<SongItem>) -> Unit)? = null,
    statisticsManager: StatisticsManager? = null
) {
    val onSongsLoadedState by rememberUpdatedState(onSongsLoaded)
    val viewModel = remember { SongsViewModel(appSettings, onSongsLoaded = { songs -> onSongsLoadedState?.invoke(songs) }) }

    // Reload songs whenever the storage directory changes (e.g. after settings are saved)
    LaunchedEffect(appSettings.songSettings.storageDirectory) {
        viewModel.updateSettings(appSettings)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.dispose() }
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

    // Edit Song Dialog state (pure UI state — fine to keep here)
    var showEditDialog by remember { mutableStateOf(false) }
    var songToEdit by remember { mutableStateOf<SongItem?>(null) }
    var showNewSongDialog by remember { mutableStateOf(false) }

    // Track which song/section/line is currently live on the presenter
    var liveSongIndex by remember { mutableStateOf(-1) }
    var liveSectionIndex by remember { mutableStateOf(0) }
    var liveLineIndex by remember { mutableStateOf(0) }

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
                    songbook = song.songbook
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

    // Panel split — lyrics panel width in px; 0 means "not yet set, use half of row"
    var lyricsPanelPx by remember(appSettings.songSettings.lyricsPanelWidthDp) {
        val saved = appSettings.songSettings.lyricsPanelWidthDp
        mutableStateOf(if (saved > 0) with(density) { saved.dp.toPx() } else 0f)
    }
    var rowTotalWidth by remember { mutableStateOf(0f) }

    fun saveColWidths() {
        onSettingsChangeState.value { s ->
            s.copy(songSettings = s.songSettings.copy(
                colWidthNumber      = with(density) { colWNumber.toDp().value.toInt() },
                colWidthTitle       = with(density) { colWTitle.toDp().value.toInt() },
                colWidthSongbook    = with(density) { colWSongbook.toDp().value.toInt() },
                colWidthTune        = with(density) { colWTune.toDp().value.toInt() },
                lyricsPanelWidthDp  = with(density) { lyricsPanelPx.toDp().value.toInt() }
            ))
        }
    }

    @Composable
    fun DragHandle(onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = onDragEnd) { _, amount ->
                        onDrag(amount)
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
            .onKeyEvent { keyEvent ->
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
            .focusable()
    ) {
        // Left panel — Search and song list (fills remaining space)
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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

                DropdownSelector(
                    modifier = Modifier.width(160.dp),
                    label = "",
                    items = songbookOptions,
                    selected = selectedSongbook.ifEmpty { allSongBooksText },
                    onSelectedChange = { viewModel.updateSelectedSongbook(it) }
                )

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

                IconButton(
                    onClick = { /* Search action */ },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(20.dp))
                }
            }

            // Column header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Color.Gray.copy(alpha = 0.2f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.number) + viewModel.getSortIndicator(Constants.SORT_NUMBER),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .width(with(density) { colWNumber.toDp() })
                        .padding(horizontal = 8.dp)
                        .clickable { viewModel.updateSort(Constants.SORT_NUMBER) }
                )
                DragHandle(
                    onDrag = { colWNumber = (colWNumber + it).coerceIn(with(density) { 30.dp.toPx() }, with(density) { 200.dp.toPx() }) },
                    onDragEnd = ::saveColWidths
                )
                Text(
                    text = stringResource(Res.string.title) + viewModel.getSortIndicator(Constants.SORT_TITLE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .width(with(density) { colWTitle.toDp() })
                        .padding(horizontal = 8.dp)
                        .clickable { viewModel.updateSort(Constants.SORT_TITLE) }
                )
                DragHandle(
                    onDrag = { colWTitle = (colWTitle + it).coerceIn(with(density) { 60.dp.toPx() }, with(density) { 600.dp.toPx() }) },
                    onDragEnd = ::saveColWidths
                )
                Text(
                    text = stringResource(Res.string.song_book) + viewModel.getSortIndicator(Constants.SORT_SONGBOOK),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .width(with(density) { colWSongbook.toDp() })
                        .padding(horizontal = 8.dp)
                        .clickable { viewModel.updateSort(Constants.SORT_SONGBOOK) }
                )
                DragHandle(
                    onDrag = { colWSongbook = (colWSongbook + it).coerceIn(with(density) { 40.dp.toPx() }, with(density) { 300.dp.toPx() }) },
                    onDragEnd = ::saveColWidths
                )
                Text(
                    text = stringResource(Res.string.tune) + viewModel.getSortIndicator(Constants.SORT_TUNE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .width(with(density) { colWTune.toDp() })
                        .padding(horizontal = 8.dp)
                        .clickable { viewModel.updateSort(Constants.SORT_TUNE) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                val lazyListState = rememberLazyListState()

                LaunchedEffect(selectedSongIndex, filteredSongs.size) {
                    if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                        delay(100)
                        lazyListState.animateScrollToItem(selectedSongIndex)
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(filteredSongs) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index == selectedSongIndex) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    viewModel.selectSong(index)
                                    if (isPresenting && liveSongIndex >= 0) {
                                        viewModel.selectSection(-1)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val textColor = if (index == selectedSongIndex)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                            Text(
                                song.number,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(with(density) { colWNumber.toDp() }).padding(horizontal = 8.dp),
                                color = textColor
                            )
                            Box(modifier = Modifier.width(6.dp)) // spacer matching drag handle
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(with(density) { colWTitle.toDp() }).padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = textColor
                            )
                            Box(modifier = Modifier.width(6.dp))
                            Text(
                                song.songbook,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(with(density) { colWSongbook.toDp() }).padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = textColor
                            )
                            Box(modifier = Modifier.width(6.dp))
                            Text(
                                song.tune,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(with(density) { colWTune.toDp() }).padding(horizontal = 8.dp),
                                maxLines = 1,
                                color = textColor
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = lazyListState)
                )
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

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                val useIcons = maxWidth < 1100.dp
                val hasSongSelected = selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size && selectedSectionIndex >= 0
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (useIcons) {
                        TooltipArea(
                            tooltip = {
                                Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(goLiveStr, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = { sendToPresenter(); onPresenting(Presenting.LYRICS) },
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
                    } else {
                        Button(
                            onClick = { sendToPresenter(); onPresenting(Presenting.LYRICS) },
                            enabled = hasSongSelected,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(goLiveStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                        }
                    }

                    if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                        if (useIcons) {
                            TooltipArea(
                                tooltip = {
                                    Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(editSongStr, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.CursorPoint()
                            ) {
                                IconButton(
                                    onClick = { songToEdit = filteredSongs[selectedSongIndex]; showEditDialog = true },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_edit), contentDescription = editSongStr, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            Button(
                                onClick = { songToEdit = filteredSongs[selectedSongIndex]; showEditDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text(editSongStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiary, maxLines = 1)
                            }
                        }
                    }

                    // New Song button
                    if (useIcons) {
                        TooltipArea(
                            tooltip = {
                                Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(newSongStr, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = { showNewSongDialog = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = newSongStr, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        Button(
                            onClick = { showNewSongDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text(newSongStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiary, maxLines = 1)
                        }
                    }

                    if (onAddToSchedule != null && selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                        if (useIcons) {
                            TooltipArea(
                                tooltip = {
                                    Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(addScheduleStr, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.CursorPoint()
                            ) {
                                IconButton(
                                    onClick = {
                                        filteredSongs.getOrNull(selectedSongIndex)?.let { item ->
                                            onAddToSchedule(item.number.toIntOrNull() ?: 0, item.title, item.songbook, item.songId)
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = addScheduleStr, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    filteredSongs.getOrNull(selectedSongIndex)?.let { item ->
                                        onAddToSchedule(item.number.toIntOrNull() ?: 0, item.title, item.songbook, item.songId)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text(addScheduleStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondary, maxLines = 1)
                            }
                        }
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
            Box {
                val lyricsListState = rememberLazyListState()

                LaunchedEffect(selectedSectionIndex) {
                    if (selectedSectionIndex >= 0) {
                        lyricsListState.animateScrollToItem(selectedSectionIndex)
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
                    if (sections.isNotEmpty()) {
                        itemsIndexed(sections) { sectionIndex, section ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (sectionIndex == selectedSectionIndex)
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.selectSection(sectionIndex)
                                            sendToPresenter()
                                        },
                                        onDoubleClick = {
                                            viewModel.selectSection(sectionIndex)
                                            sendToPresenter()
                                            onPresenting(Presenting.LYRICS)
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                val textColor = if (sectionIndex == selectedSectionIndex)
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
                .then(if (onLineClick != null) Modifier.clickable { onLineClick(lineIndex) } else Modifier)
        )
    }
}
