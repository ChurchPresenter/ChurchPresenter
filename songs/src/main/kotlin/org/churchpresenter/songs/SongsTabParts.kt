package org.churchpresenter.songs

import org.churchpresenter.sharedui.models.Presenting
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.churchpresenter.sharedui.composables.focusRescuePressHook
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.all_song_books
import org.churchpresenter.strings.generated.resources.contains
import org.churchpresenter.strings.generated.resources.exact_match
import org.churchpresenter.strings.generated.resources.back_to_live
import org.churchpresenter.strings.generated.resources.line_navigation_hint
import org.churchpresenter.strings.generated.resources.new_song
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.strings.generated.resources.confirm_delete
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.starts_with
import org.churchpresenter.strings.generated.resources.title
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.pairLabel
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.settings.languageLabel
import org.churchpresenter.settings.withLanguageNames
import org.churchpresenter.settings.moveSongLanguageAmong
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.DragHandle
import androidx.compose.foundation.layout.RowScope

/** The tab's effects: play counts, a storage-folder change, schedule selection, opening focus and remote lyrics. */
@Composable
internal fun SongsTabController.SongsTabEffects(
    playCounts: SongPlayCounts?,
    schedule: ScheduleSelection,
    dialogDismissSignal: Int,
) {
    val selectedSongItem = schedule.item
    val selectedSongItemVersion = schedule.version
    LaunchedEffect(playCounts) { viewModel.setPlayCounts(playCounts) }

    // Reload songs whenever the storage directory changes (e.g. after settings are saved)
    val isFirstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(appSettings.songSettings.storageDirectory) {
        if (isFirstComposition.value) {
            isFirstComposition.value = false
        } else {
            viewModel.updateSettings(appSettings)
        }
    }

    val selectedSongIndex by viewModel.selectedSongIndex
    val searchQuery by viewModel.searchQuery
    val remoteLyricsUpdated by viewModel.remoteLyricsUpdated

    // Reset title-slide selection whenever the active song changes
    LaunchedEffect(selectedSongIndex) { live.titleSlideSelected = false }

    // Whatever was held back is moot once the selection or the live song changes.
    LaunchedEffect(selectedSongIndex, live.songId) { browsePausedHint = false }

    val songDialogOpen = dialogs.editing != null || dialogs.creatingNew || dialogs.deleting != null
    // Read before the schedule effect below records this visit's version.
    val openedFromSchedule = remember {
        selectedSongItem != null && (selectedSongItem to selectedSongItemVersion) != viewModel.scheduleSeen
    }

    // A song handed over by the schedule or a remote. Acted on once per handover: the tab is rebuilt
    // on every visit and the app keeps the last schedule song, so without the version check a
    // visit would select that song again and push it over whatever is live.
    LaunchedEffect(selectedSongItem, selectedSongItemVersion) {
        val handover = selectedSongItem to selectedSongItemVersion
        val fresh = handover != viewModel.scheduleSeen
        viewModel.scheduleSeen = handover
        val item = selectedSongItem?.takeIf { fresh } ?: return@LaunchedEffect
        // Wait until data is ready if currently loading
        if (viewModel.isLoading.value) {
            snapshotFlow { viewModel.isLoading.value }.first { !it }
        }
        // A song the library does not have leaves the output as it is.
        if (!viewModel.selectSongByDetails(item.songNumber, item.title, item.songbook, item.songId)) {
            return@LaunchedEffect
        }
        live.titleSlideSelected = false
        when (schedule.action) {
            ScheduleSongAction.GO_LIVE -> {
                sendToPresenter(goLive = true, source = schedule.source)
                onPresenting(Presenting.LYRICS)
            }
            ScheduleSongAction.OPEN -> if (!isPresenting) sendToPresenter()
            ScheduleSongAction.PUSH -> sendToPresenter()
        }
        tabFocusRequester.requestFocus()
    }

    // Remote (Instance Link) songs fetch their lyrics lazily after selection — sendToPresenter()
    // above may have already run against empty lyrics before the fetch resolved, so re-push once
    // it lands (viewModel only bumps this when it's still for the currently selected song).
    LaunchedEffect(remoteLyricsUpdated) {
        if (remoteLyricsUpdated > 0) {
            sendToPresenter()
        }
    }

    // Opening the tab puts the caret in the search box (#798), unless the keyboard belongs to what is
    // live here -- or to the schedule song just opened -- so the step keys keep working. After that,
    // closing a dialog hands the keyboard back to the tab as before.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(dialogDismissSignal) {
        val opening = !opened
        opened = true
        val searchFirst = appSettings.keyboardShortcutSettings.focusSearchOnTabOpen &&
            !isPresenting && !openedFromSchedule && !songDialogOpen
        if (opening && searchFirst) focusSearch() else tabFocusRequester.requestFocus()
    }
}

/** The tab's row: the song list, the handle between, and the lyrics panel. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun SongsTabController.SongsTabPanes(modifier: Modifier) {
    val shortcuts = LocalShortcuts.current
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
            .onFocusChanged {
                focusRescue.onFocusChanged(it.hasFocus)
                tabRootFocused = it.isFocused
            }
            .focusRescuePressHook(focusRescue)
            .focusable()
            // The handler sits on the tab root, so it sees every key before the search field
            // does. While the caret is in that field the keys belong to the text — left/right
            // move it, and nothing here may swallow them. Same rule as BibleTab.
            .onPreviewKeyEvent { keyEvent -> handleKey(keyEvent, shortcuts) }
    ) {
        // Left panel — Search and song list (fills remaining space)
        SongListSide(this)

        // Vertical drag handle — resize lyrics panel
        DragHandle(onDragEnd = { saveLyricsPanelWidth() }) { delta ->
            lyricsPanelPx = (lyricsPanelPx - delta)
                .coerceIn(
                    with(density) { 150.dp.toPx() },
                    with(density) { 800.dp.toPx() }
                )
        }

        SongLyricsSide(this)
    }
}

@Composable
private fun SongsTabController.SongListSide(row: RowScope) = with(row) {
    val songbooks by viewModel.songbooks
    val searchQuery by viewModel.searchQuery
    val selectedSongbook by viewModel.selectedSongbook
    val filterType by viewModel.filterType
    val selectedSongIndex by viewModel.selectedSongIndex
    val filteredSongs by viewModel.filteredSongItems
    val isLoading by viewModel.isLoading
    val currentSortColumn by viewModel.sortColumn
    val currentSortAscending by viewModel.sortAscending
    val favorites by viewModel.favorites
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

    val actionCols = setOf(SongColumnId.FAVORITES, SongColumnId.ADD_TO_SCHEDULE)
    val availableCols = availableSongColumns(songbooks.size, hasAddToSchedule = onAddToSchedule != null)
    val visibleCols = columns.visible

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
        searchFocus = searchFocus,
        favoriteSongs = { viewModel.getFavoriteSongs() },
        playCountFor = { id -> playCounts?.getSongPlayCount(id) },
        searchMatchFor = viewModel::searchMatchFor,
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
            onSettingsChange { s -> s.copy(songFavorites = viewModel.favorites.value.toList()) }
        },
        onClearFavorites = {
            viewModel.clearFavorites()
            onSettingsChange { s -> s.copy(songFavorites = emptyList()) }
        },
        onReloadSongs = { viewModel.loadSongs() },
        onSaveColumnWidths = ::saveColWidths,
        onSaveColumnOrder = { onSettingsChange { s -> s.copy(songColOrder = columns.order) } },
        onSaveHiddenColumns = { onSettingsChange { s -> s.copy(songHiddenCols = columns.hidden) } },
        onSaveFavPanelHeight = {
            onSettingsChange { s ->
                s.copy(songFavoritesPanelHeightDp = with(density) { favPanelHeightPx.toDp().value.toInt() })
            }
        },
        onFavoritesExpandedChange = { favoritesExpanded = it },
        onFavPanelHeightChange = { favPanelHeightPx = it },
        onAddToSchedule = onAddToSchedule,
        onPresenting = onPresenting,
        sendToPresenter = ::sendToPresenter,
    )
}

@Composable
private fun SongsTabController.SongLyricsSide(row: RowScope) = with(row) {
    val searchQuery by viewModel.searchQuery
    val selectedSongIndex by viewModel.selectedSongIndex
    val selectedSectionIndex by viewModel.selectedSectionIndex
    val filteredSongs by viewModel.filteredSongItems
    val shortcuts = LocalShortcuts.current

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

    SongLyricsPanel(
        lyricsPanelPx = lyricsPanelPx,
        appSettings = appSettings,
        filteredSongs = filteredSongs,
        selectedSongIndex = selectedSongIndex,
        selectedSectionIndex = selectedSectionIndex,
        selectedLineIndex = viewModel.selectedLineIndex.value,
        searchQuery = searchQuery,
        searchFieldFocused = searchFieldFocused,
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
        onBackToLiveSong = ::backToLive,
        browsePausedHint = browsePausedHint,
        onSearchBannerClick = ::switchSearchLive,
        onAddToSchedule = onAddToSchedule,
        onPresenting = onPresenting,
        sendToPresenter = ::sendToPresenter,
        onMoveLanguage = { available, index, offset ->
            onSettingsChange { s -> s.moveSongLanguageAmong(available, index, offset) }
        },
    )
}


/** The song editor, for the song being edited and for a new one; the app draws it. */
@Composable
internal fun SongsTabController.SongEditorDialogs() {
    // What the song's languages are called install-wide -- named in the editor, read by the
    // profiles' song languages and the output language switch.
    val songLanguageNames = List(MAX_SONG_TRANSLATIONS) { appSettings.songSettings.languageLabel(it) }
    val onLanguageNamesChange: (List<String>) -> Unit = { names ->
        onSettingsChange { s -> s.copy(songSettings = s.songSettings.withLanguageNames(names)) }
    }
    val onChordsVisibleChange: (Boolean) -> Unit = { visible ->
        onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(editorShowChords = visible)) }
    }

    // Edit Song Dialog — pure UI dialog state is fine here
    songEditor(
        SongEditorRequest(
            isVisible = dialogs.editing != null,
            song = dialogs.editing,
            songbooks = viewModel.songbooks.value,
            existingSongs = viewModel.songsData.value.getSongs(),
            tuning = dialogs.editing?.let { appSettings.tuningFor(it.songId) } ?: SongTuning(),
            chordsVisible = appSettings.songSettings.editorShowChords,
            typicalSeconds = dialogs.editing?.let(typicalSongSeconds),
            onChordsVisibleChange = onChordsVisibleChange,
            onApplyBackgroundToSongbook = { songbook, background, lowerThirdBackground ->
                viewModel.applyBackgroundToSongbook(songbook, background, lowerThirdBackground)
            },
            languageNames = songLanguageNames,
            onLanguageNamesChange = onLanguageNamesChange,
            onDismiss = { dialogs.closeEditor() },
            onSave = { updatedSong, tuning ->
                dialogs.editing?.let { oldSong ->
                    val wasLive = isPresenting && live.songId == oldSong.songId
                    val success = viewModel.updateSong(oldSong, updatedSong)
                    if (success) {
                        UsageEvents.record(UsageEvent.SONG_EDITED)
                        onSettingsChange { s -> s.withTuning(updatedSong.songId, tuning) }
                        dialogs.closeEditor()
                        dialogs.closeEditor()
                        if (wasLive) sendEditedSongToPresenter(updatedSong, tuning)
                    }
                }
            },
        )
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
    songEditor(
        SongEditorRequest(
            isVisible = dialogs.creatingNew,
            song = newSongTemplate,
            songbooks = viewModel.songbooks.value,
            existingSongs = viewModel.songsData.value.getSongs(),
            isNewSong = true,
            chordsVisible = appSettings.songSettings.editorShowChords,
            typicalSeconds = dialogs.editing?.let(typicalSongSeconds),
            onChordsVisibleChange = onChordsVisibleChange,
            languageNames = songLanguageNames,
            onLanguageNamesChange = onLanguageNamesChange,
            onDismiss = { dialogs.closeNew() },
            onSave = { newSong, tuning ->
                val success = viewModel.createSong(newSong)
                if (success) {
                    if (tuning != SongTuning()) {
                        onSettingsChange { s -> s.withTuning(newSong.songId, tuning) }
                    }
                    dialogs.closeNew()
                }
            },
        )
    )
}

/** Asks before a song's file is deleted from the library folder. */
@Composable
internal fun SongsTabController.DeleteSongDialog() {
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
                    GhostButton(
                        shape = AppShape(6.dp),
                        onClick = {
                        viewModel.deleteSong(s)
                        dialogs.closeDelete()
                    }) {
                        Text(stringResource(Res.string.delete_saved_string), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    GhostButton(
                        shape = AppShape(6.dp),
                        onClick = { dialogs.closeDelete(); dialogs.closeDelete() }
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            )
        }
    }
}
