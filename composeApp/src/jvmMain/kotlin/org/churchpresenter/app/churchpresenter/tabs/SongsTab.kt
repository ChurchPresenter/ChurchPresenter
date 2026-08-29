package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.composables.focusRescuePressHook
import org.churchpresenter.app.churchpresenter.composables.rememberFocusLostRescue
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import java.awt.Window as AwtWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.all_song_books
import churchpresenter.composeapp.generated.resources.contains
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.back_to_live
import churchpresenter.composeapp.generated.resources.line_navigation_hint
import churchpresenter.composeapp.generated.resources.new_song
import churchpresenter.composeapp.generated.resources.delete_saved_string
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.number
import churchpresenter.composeapp.generated.resources.starts_with
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.author
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.dialogs.EditSongDialog
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.pairLabel
import org.churchpresenter.app.churchpresenter.utils.availableSongColumns
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.isDualLanguagePresentation
import org.churchpresenter.app.churchpresenter.utils.isSplitScreenSong
import org.churchpresenter.app.churchpresenter.utils.isChordChartPresentation
import org.churchpresenter.app.churchpresenter.utils.isSongLineMode
import org.churchpresenter.app.churchpresenter.viewmodel.resolveEditedSongPush
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import org.jetbrains.compose.resources.stringResource

/** The toolbar button that adds the *selected* song, as opposed to any other "Add to Schedule". */
internal const val SONGS_ADD_SELECTED_TAG = "songs_addSelectedToSchedule"

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
    /** The hosting AWT window — used by the focus-lost rescue to heal AWT focus (see
     *  composables/FocusLostRescue.kt). */
    hostWindow: AwtWindow? = null,
    viewModel: SongsViewModel,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((songNumber: Int, title: String, songbook: String, songId: String) -> Unit)? = null,
    /** Instance Link Controller mode — non-null only when connected and controlling. Go-live with a
     *  *new* song (approval-gated the first time on the primary, instant afterwards). */
    onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
    /** Instance Link Controller mode — section navigation *within the same already-live song*
     *  (always instant on the primary, no approval gate). [lineIndex] carries "one line at a time"
     *  display-mode navigation (-1 = section-level only). */
    onInstanceLinkSendSongSection: ((number: String, section: Int, lineIndex: Int) -> Unit)? = null,
    selectedSongItem: ScheduleItem.SongItem? = null,
    selectedSongItemVersion: Int = 0,
    onSongItemSelected: (LyricSection) -> Unit,
    onAllSectionsChanged: (List<LyricSection>) -> Unit = {},
    onSectionIndexChanged: (Int) -> Unit = {},
    onLineIndexChanged: (Int) -> Unit = {},
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    isPresenting: Boolean = false,
    theme: ThemeMode = ThemeMode.SYSTEM,
    statisticsManager: StatisticsManager? = null,
    dialogDismissSignal: Int = 0,
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
    val remoteLyricsUpdated by viewModel.remoteLyricsUpdated
    val filteredSongs by viewModel.filteredSongItems
    val isLoading by viewModel.isLoading
    val currentSortColumn by viewModel.sortColumn
    val currentSortAscending by viewModel.sortAscending

    // Edit Song Dialog state (pure UI state — fine to keep here)
    val dialogs = rememberSongDialogRequests()

    // Favorites panel state
    var favoritesExpanded by remember { mutableStateOf(true) }
    val favorites by viewModel.favorites

    // Track which song/section/line is currently live on the presenter.
    // live.songId is the song's stable songId (not a list index) so it survives the
    // filtered list being rebuilt by search — see AGENT.md's "Song Edit While Live" note.
    val live = rememberSongLiveState()

    // Reset title-slide selection whenever the active song changes
    LaunchedEffect(selectedSongIndex) { live.titleSlideSelected = false }

    // Helper: push current viewModel selection to presenter and track as live.
    // goLive=true marks this call as an explicit "go live" action so statistics are
    // recorded even though the isPresenting flag has not yet propagated.
    fun sendToPresenter(goLive: Boolean = false) {
        onAllSectionsChanged(viewModel.getLyricSections())
        onSectionIndexChanged(viewModel.selectedSectionIndex.value)
        onLineIndexChanged(viewModel.selectedLineIndex.value)
        val idx = viewModel.selectedSongIndex.value
        val items = viewModel.filteredSongItems.value
        val tuning = items.getOrNull(idx)?.let { appSettings.tuningFor(it.songId) } ?: SongTuning()
        viewModel.getSelectedLyricSection()?.let {
            onSongItemSelected(it.copy(bpm = tuning.bpm, capo = tuning.capo))
        }
        // Record song display for statistics — only when the song is actually live
        // (or being sent live), and only when a different song is presented.
        val isDifferentSong = items.getOrNull(idx)?.songId?.let { it != live.songId } ?: false
        if ((goLive || isPresenting) && isDifferentSong) {
            if (idx in items.indices) {
                val song = items[idx]
                statisticsManager?.recordSongDisplay(
                    songId = song.songId,
                    songNumber = song.number.toIntOrNull() ?: 0,
                    title = song.title,
                    songbook = song.songbook,
                    author = song.author
                )
                // Bilingual worship actually happening, rather than merely being configured. Sits
                // under the same "different song went live" guard so a service counts songs, not
                // section changes.
                val outputs = appSettings.projectionSettings.screenAssignments
                if (isDualLanguagePresentation(song, outputs)) {
                    UsageEvents.record(UsageEvent.SONG_DUAL_LANGUAGE)
                }
                if (isSplitScreenSong(outputs)) UsageEvents.record(UsageEvent.SONG_SPLIT_SCREEN)
                if (isChordChartPresentation(song, outputs)) {
                    UsageEvents.record(UsageEvent.SONG_CHORD_CHART)
                }
            }
        }
        // Instance Link Controller mode: a genuine go-live with a *different* song needs the primary
        // to actually load it first (sendProject, approval-gated the first time); a go-live that's
        // just a section/line change on the *same* already-live song can use the lighter, always-
        // instant sendSongSection instead — reusing the isDifferentSong condition already computed above.
        if (goLive && idx in items.indices) {
            val song = items[idx]
            if (isDifferentSong) {
                onInstanceLinkSendProject?.invoke(
                    ScheduleItem.SongItem(
                        id = java.util.UUID.randomUUID().toString(),
                        songNumber = song.number.toIntOrNull() ?: 0,
                        title = song.title,
                        songbook = song.songbook,
                        songId = song.songId
                    )
                )
            } else {
                onInstanceLinkSendSongSection?.invoke(song.number, viewModel.selectedSectionIndex.value, viewModel.selectedLineIndex.value)
            }
        }
        live.songId = items.getOrNull(idx)?.songId
        live.sectionIndex = viewModel.selectedSectionIndex.value
        live.lineIndex = viewModel.selectedLineIndex.value
    }

    // Re-pushes freshly-edited content to the presenter when the just-saved song is the one
    // currently live. Sourced directly from `editedSong` (the dialog's just-saved SongItem)
    // rather than viewModel.getSelectedLyricSection() — the catalog reload triggered by
    // updateSong() is async, so the viewModel's own selection state isn't guaranteed fresh yet.
    // [tuning] is passed in rather than read back from settings: the editor saves tempo, capo and
    // song together, and the settings write has not reached `appSettings` yet in that same frame.
    fun sendEditedSongToPresenter(editedSong: SongItem, tuning: SongTuning) {
        val sections = viewModel.getLyricSections(editedSong)
        val push = resolveEditedSongPush(sections, live.sectionIndex, live.lineIndex, editedSong, tuning)
        onAllSectionsChanged(sections)
        onSectionIndexChanged(push.sectionIndex)
        onLineIndexChanged(push.lineIndex)
        onSongItemSelected(push.section)
        live.sectionIndex = push.sectionIndex
        live.lineIndex = push.lineIndex
    }

    val tabFocusRequester = remember { FocusRequester() }
    // True while the caret is in the song search field — the tab's key handler stands down for it.
    var searchFieldFocused by remember { mutableStateOf(false) }
    // Focus-lost rescue: arrow-key song/section/line navigation only works while the tab
    // holds keyboard focus AND the window is focused — full machinery in
    // composables/FocusLostRescue.kt (shared with Presentation/Bible).
    val focusRescue = rememberFocusLostRescue(hostWindow, tabFocusRequester)
    val shortcuts = LocalShortcuts.current

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
                tabFocusRequester.requestFocus()
            }
        }
    }

    // Remote (Instance Link) songs fetch their lyrics lazily after selection — sendToPresenter()
    // above may have already run against empty lyrics before the fetch resolved, so re-push once
    // it lands (viewModel only bumps this when it's still for the currently selected song).
    LaunchedEffect(remoteLyricsUpdated) {
        if (remoteLyricsUpdated > 0) {
            sendToPresenter()
        }
    }

    // String resources
    val newSongStr = stringResource(Res.string.new_song)
    val backToLiveStr = stringResource(Res.string.back_to_live)
    // Built from the live bindings rather than naming the arrow keys, and empty when the user has
    // unbound both pairs so the render site can drop the hint entirely.
    val lineKeys = shortcuts.pairLabel(ShortcutAction.SONGS_PREVIOUS, ShortcutAction.SONGS_NEXT)
    val verseKeys = shortcuts.pairLabel(ShortcutAction.SONGS_PREVIOUS_SECTION, ShortcutAction.SONGS_NEXT_SECTION)
    val lineNavHintStr = if (lineKeys.isEmpty() && verseKeys.isEmpty()) {
        ""
    } else {
        stringResource(Res.string.line_navigation_hint, lineKeys, verseKeys)
    }
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

    LaunchedEffect(dialogDismissSignal) { tabFocusRequester.requestFocus() }

    val columns = rememberSongTableColumns(
        settings = appSettings,
        density = density,
        availableColumns = availableSongColumns(songbooks.size, hasAddToSchedule = onAddToSchedule != null),
    )

    // Favorites panel height in px
    var favPanelHeightPx by remember(appSettings.songFavoritesPanelHeightDp) {
        mutableStateOf(with(density) { appSettings.songFavoritesPanelHeightDp.dp.toPx() })
    }

    val actionCols = setOf("favorites", "add_to_schedule")
    val availableCols = availableSongColumns(songbooks.size, hasAddToSchedule = onAddToSchedule != null)
    val visibleCols = columns.visible


    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    // Panel split — lyrics panel width in px; 0 means "not yet set, use half of row"
    var lyricsPanelPx by remember(currentLayout.lyricsPanelWidthDp, isMaximized) {
        val saved = currentLayout.lyricsPanelWidthDp
        mutableStateOf(if (saved > 0) with(density) { saved.dp.toPx() } else 0f)
    }
    var rowTotalWidth by remember { mutableStateOf(0f) }

    fun saveColWidths() {
        val dp = columns.widthsInDp()
        onSettingsChangeState.value { s ->
            s.copy(
                songSettings = s.songSettings.copy(
                    colWidthNumber      = dp["number"] ?: s.songSettings.colWidthNumber,
                    colWidthTitle       = dp["title"] ?: s.songSettings.colWidthTitle,
                    colWidthSongbook    = dp["songbook"] ?: s.songSettings.colWidthSongbook,
                    colWidthTune        = dp["tune"] ?: s.songSettings.colWidthTune,
                    colWidthPlayCount   = dp["play_count"] ?: s.songSettings.colWidthPlayCount,
                    colWidthAuthor      = dp["author"] ?: s.songSettings.colWidthAuthor,
                    colWidthComposer    = dp["composer"] ?: s.songSettings.colWidthComposer,
                ),
                songColOrder = columns.order,
                songHiddenCols = columns.hidden,
            )
        }
    }

    fun saveLyricsPanelWidth() {
        val widthDp = with(density) { lyricsPanelPx.toDp().value.toInt() }
        onSettingsChangeState.value { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(lyricsPanelWidthDp = widthDp))
            else s.copy(windowedLayout = s.windowedLayout.copy(lyricsPanelWidthDp = widthDp))
        }
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
            .onFocusChanged { focusRescue.onFocusChanged(it.hasFocus) }
            .focusRescuePressHook(focusRescue)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                // The handler sits on the tab root, so it sees every key before the search field
                // does. While the caret is in that field the keys belong to the text — left/right
                // move it, and nothing here may swallow them. Same rule as BibleTab.
                if (keyEvent.type == KeyEventType.KeyDown && !searchFieldFocused) {
                    val isLineMode = isSongLineMode(appSettings.songSettings)
                    when {
                        shortcuts.matches(ShortcutAction.SONGS_PREVIOUS, keyEvent) -> {
                            if (isLineMode) {
                                viewModel.navigatePreviousLine()
                                sendToPresenter(goLive = isPresenting)
                            } else if (!isPresenting) {
                                viewModel.navigatePreviousSong()
                            }
                            true
                        }
                        shortcuts.matches(ShortcutAction.SONGS_NEXT, keyEvent) -> {
                            if (isLineMode) {
                                viewModel.navigateNextLine()
                                sendToPresenter(goLive = isPresenting)
                            } else if (!isPresenting) {
                                viewModel.navigateNextSong()
                            }
                            true
                        }
                        shortcuts.matches(ShortcutAction.SONGS_PREVIOUS_SECTION, keyEvent) -> {
                            if (!viewModel.navigatePreviousSection() && !isPresenting) viewModel.navigatePreviousSong()
                            sendToPresenter(goLive = isPresenting)
                            true
                        }
                        shortcuts.matches(ShortcutAction.SONGS_NEXT_SECTION, keyEvent) -> {
                            if (!viewModel.navigateNextSection() && !isPresenting) viewModel.navigateNextSong()
                            sendToPresenter(goLive = isPresenting)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Left panel — Search and song list (fills remaining space)
        SongListPane(
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
            favoriteSongs = { viewModel.getFavoriteSongs() },
            playCountFor = { id -> statisticsManager?.getSongPlayCount(id) },
            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            onSearchFocusChanged = { searchFieldFocused = it },
            onFilterTypeChange = { viewModel.updateFilterType(it) },
            onSongbookChange = { viewModel.updateSelectedSongbook(it) },
            onSortChange = { viewModel.updateSort(it) },
            onSelectSong = { viewModel.selectSong(it) },
            onSelectSongByDetails = { number, title, songbook, songId ->
                viewModel.selectSongByDetails(number, title, songbook, songId)
            },
            onSelectSection = { viewModel.selectSection(it) },
            onToggleFavorite = { songId ->
                viewModel.toggleFavorite(songId)
                onSettingsChangeState.value { s -> s.copy(songFavorites = viewModel.favorites.value.toList()) }
            },
            onClearFavorites = {
                viewModel.clearFavorites()
                onSettingsChangeState.value { s -> s.copy(songFavorites = emptyList()) }
            },
            onReloadSongs = { viewModel.loadSongs() },
            onSaveColumnWidths = ::saveColWidths,
            onSaveColumnOrder = { onSettingsChangeState.value { s -> s.copy(songColOrder = columns.order) } },
            onSaveHiddenColumns = { onSettingsChangeState.value { s -> s.copy(songHiddenCols = columns.hidden) } },
            onSaveFavPanelHeight = {
                onSettingsChangeState.value { s ->
                    s.copy(songFavoritesPanelHeightDp = with(density) { favPanelHeightPx.toDp().value.toInt() })
                }
            },
            onFavoritesExpandedChange = { favoritesExpanded = it },
            onFavPanelHeightChange = { favPanelHeightPx = it },
            onAddToSchedule = onAddToSchedule,
            onPresenting = onPresenting,
            sendToPresenter = ::sendToPresenter,
        )

        // Vertical drag handle — resize lyrics panel
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        lyricsPanelPx = (lyricsPanelPx - delta)
                            .coerceIn(
                                with(density) { 150.dp.toPx() },
                                with(density) { 800.dp.toPx() }
                            )
                    },
                    onDragStopped = { saveLyricsPanelWidth() }
                )
        )

        SongLyricsPanel(
            lyricsPanelPx = lyricsPanelPx,
            appSettings = appSettings,
            filteredSongs = filteredSongs,
            selectedSongIndex = selectedSongIndex,
            selectedSectionIndex = selectedSectionIndex,
            selectedLineIndex = viewModel.selectedLineIndex.value,
            searchQuery = searchQuery,
            isPresenting = isPresenting,
            live = live,
            dialogs = dialogs,
            backToLiveStr = backToLiveStr,
            lineNavHintStr = lineNavHintStr,
            newSongStr = newSongStr,
            focusRescue = focusRescue,
            tabFocusRequester = tabFocusRequester,
            lyricSections = { viewModel.getLyricSections() },
            onSectionSelected = { viewModel.selectSection(it) },
            onLineSelected = { viewModel.setLineIndex(it) },
            onBackToLiveSong = { live.songId?.let { viewModel.selectSongById(it) } },
            onSectionIndexChanged = onSectionIndexChanged,
            onLineIndexChanged = onLineIndexChanged,
            onAllSectionsChanged = onAllSectionsChanged,
            onSongItemSelected = onSongItemSelected,
            onAddToSchedule = onAddToSchedule,
            onPresenting = onPresenting,
            sendToPresenter = ::sendToPresenter,
        )
    }

    // The metronome tempo is only ever read by the stage monitor, so the field that sets it is
    // offered only when there is one configured.
    val hasStageMonitorScreen = appSettings.projectionSettings.screenAssignments.any {
        it.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    }

    // Edit Song Dialog — pure UI dialog state is fine here
    EditSongDialog(
        isVisible = dialogs.editing != null,
        song = dialogs.editing,
        songbooks = viewModel.songbooks.value,
        existingSongs = viewModel.songsData.value.getSongs(),
        theme = theme,
        tuning = dialogs.editing?.let { appSettings.tuningFor(it.songId) } ?: SongTuning(),
        showTuningFields = hasStageMonitorScreen,
        chordsVisible = appSettings.songSettings.editorShowChords,
        onChordsVisibleChange = { visible ->
            onSettingsChangeState.value { s -> s.copy(songSettings = s.songSettings.copy(editorShowChords = visible)) }
        },
        onApplyBackgroundToSongbook = { songbook, background, lowerThirdBackground ->
            viewModel.applyBackgroundToSongbook(songbook, background, lowerThirdBackground)
        },
        onDismiss = { dialogs.closeEditor() },
        onSave = { updatedSong, tuning ->
            dialogs.editing?.let { oldSong ->
                val wasLive = isPresenting && live.songId == oldSong.songId
                val success = viewModel.updateSong(oldSong, updatedSong)
                if (success) {
                    onSettingsChangeState.value { s -> s.withTuning(updatedSong.songId, tuning) }
                    dialogs.closeEditor()
                    dialogs.closeEditor()
                    if (wasLive) sendEditedSongToPresenter(updatedSong, tuning)
                }
            }
        }
    )

    // Delete Song Confirmation Dialog
    if (dialogs.deleting != null) {
        val s = dialogs.deleting
        if (s != null) {
            AlertDialog(
                onDismissRequest = { dialogs.closeDelete(); dialogs.closeDelete() },
                title = { Text(stringResource(Res.string.confirm_delete)) },
                text = {
                    Column {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        if (s.sourceFile.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                s.sourceFile,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                        viewModel.deleteSong(s)
                        dialogs.closeDelete()
                    }) {
                        Text(stringResource(Res.string.delete_saved_string), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(shape = RoundedCornerShape(6.dp), onClick = { dialogs.closeDelete(); dialogs.closeDelete() }) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            )
        }
    }

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
        isVisible = dialogs.creatingNew,
        song = newSongTemplate,
        songbooks = viewModel.songbooks.value,
        existingSongs = viewModel.songsData.value.getSongs(),
        isNewSong = true,
        theme = theme,
        showTuningFields = hasStageMonitorScreen,
        chordsVisible = appSettings.songSettings.editorShowChords,
        onChordsVisibleChange = { visible ->
            onSettingsChangeState.value { s -> s.copy(songSettings = s.songSettings.copy(editorShowChords = visible)) }
        },
        onDismiss = { dialogs.closeNew() },
        onSave = { newSong, tuning ->
            val success = viewModel.createSong(newSong)
            if (success) {
                if (tuning != SongTuning()) {
                    onSettingsChangeState.value { s -> s.withTuning(newSong.songId, tuning) }
                }
                dialogs.closeNew()
            }
        }
    )
}

@Composable
internal fun LyricLines(
    lines: List<String>,
    textColor: Color,
    activeLineIndex: Int = -1,
    onLineClick: ((Int) -> Unit)? = null,
    onLineDoubleClick: ((Int) -> Unit)? = null,
) {
    lines.forEachIndexed { lineIndex, line ->
        val isActiveLine = activeLineIndex >= 0 && lineIndex == activeLineIndex
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
            color = if (isActiveLine) MaterialTheme.colorScheme.primary else textColor,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .then(
                    if (onLineClick != null) Modifier.initialPassCombinedClickable(
                        onClick = { onLineClick(lineIndex) },
                        onDoubleClick = { onLineDoubleClick?.invoke(lineIndex) }
                    ) else Modifier
                )
        )
    }
}
