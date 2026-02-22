package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.all_song_books
import churchpresenter.composeapp.generated.resources.contains
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.filter_type_colon
import churchpresenter.composeapp.generated.resources.go_live
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onAddToSchedule: ((songNumber: Int, title: String, songbook: String) -> Unit)? = null,
    selectedSongItem: ScheduleItem.SongItem? = null,
    onSongItemSelected: (LyricSection) -> Unit,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    theme: ThemeMode = ThemeMode.SYSTEM
) {
    val viewModel = remember { SongsViewModel(appSettings) }

    // React to schedule item selection — select song then notify presenter
    LaunchedEffect(selectedSongItem) {
        selectedSongItem?.let {
            val found = viewModel.selectSongByDetails(it.songNumber, it.title, it.songbook)
            if (found) {
                viewModel.getSelectedLyricSection()?.let { section -> onSongItemSelected(section) }
            }
        }
    }
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

    // String resources
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

    Row(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> { viewModel.navigatePreviousSong(); true }
                        Key.DirectionRight -> { viewModel.navigateNextSong(); true }
                        Key.DirectionUp -> {
                            if (!viewModel.navigatePreviousSection()) viewModel.navigatePreviousSong()
                            true
                        }
                        Key.DirectionDown -> {
                            val navigated = viewModel.navigateNextSection()
                            if (navigated) {
                                viewModel.getSelectedLyricSection()?.let { onSongItemSelected(it) }
                            } else {
                                viewModel.navigateNextSong()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        // Left panel — Search and song list
        Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
            // Search controls
            Column(modifier = Modifier.padding(8.dp)) {
                DropdownSelector(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    label = "",
                    items = songbookOptions,
                    selected = selectedSongbook.ifEmpty { allSongBooksText },
                    onSelectedChange = { viewModel.updateSelectedSongbook(it) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.filter_type_colon),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                    DropdownSelector(
                        modifier = Modifier.weight(1f),
                        label = "",
                        items = filterTypes,
                        selected = filterTypeDisplayMap[filterType] ?: containsText,
                        onSelectedChange = { displayText ->
                            val internalKey = filterTypeMap[displayText] ?: Constants.CONTAINS
                            viewModel.updateFilterType(internalKey)
                        }
                    )
                    Button(
                        onClick = { /* Search action */ },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(stringResource(Res.string.search), style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    label = {
                        Text(stringResource(Res.string.search_songs), style = MaterialTheme.typography.labelMedium)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors().copy(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }

            // Column header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.number) + viewModel.getSortIndicator(Constants.SORT_NUMBER),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(70.dp).clickable { viewModel.updateSort(Constants.SORT_NUMBER) }
                )
                Text(
                    text = stringResource(Res.string.title) + viewModel.getSortIndicator(Constants.SORT_TITLE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f).clickable { viewModel.updateSort(Constants.SORT_TITLE) }
                )
                Text(
                    text = stringResource(Res.string.song_book) + viewModel.getSortIndicator(Constants.SORT_SONGBOOK),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(100.dp).clickable { viewModel.updateSort(Constants.SORT_SONGBOOK) }
                )
                Text(
                    text = stringResource(Res.string.tune) + viewModel.getSortIndicator(Constants.SORT_TUNE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(60.dp).clickable { viewModel.updateSort(Constants.SORT_TUNE) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

                LaunchedEffect(selectedSongIndex, filteredSongs.size) {
                    if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                        kotlinx.coroutines.delay(100)
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
                                .clickable { viewModel.selectSong(index) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val textColor = if (index == selectedSongIndex)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                            Text(song.number, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp), color = textColor)
                            Text(song.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = textColor)
                            Text(song.songbook, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = textColor)
                            Text(song.tune, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(60.dp), maxLines = 1, color = textColor)
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

        // Right panel — Lyrics display
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            // Header row with action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                    Button(
                        modifier = Modifier.wrapContentSize().padding(end = 4.dp),
                        onClick = {
                            songToEdit = filteredSongs[selectedSongIndex]
                            showEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(stringResource(Res.string.edit_song), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiary, maxLines = 1)
                    }
                }

                Button(
                    modifier = Modifier.wrapContentSize(),
                    onClick = { onPresenting(Presenting.LYRICS) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(Res.string.go_live), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, maxLines = 2)
                }

                if (onAddToSchedule != null && selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                    Button(
                        modifier = Modifier.wrapContentSize().padding(start = 4.dp),
                        onClick = {
                            val item = filteredSongs.getOrNull(selectedSongIndex)
                            if (item != null) {
                                onAddToSchedule(item.number.toIntOrNull() ?: 0, item.title, item.songbook)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(Res.string.add_to_schedule), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondary, maxLines = 2)
                    }
                }
            }

            // Lyrics content
            Box {
                val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()

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
                                    .clickable {
                                        viewModel.selectSection(
                                            if (selectedSectionIndex == sectionIndex) -1 else sectionIndex
                                        )
                                        onSongItemSelected(section)
                                    }
                                    .padding(8.dp)
                            ) {
                                section.lines.forEachIndexed { lineIndex, line ->
                                    val isHeader = lineIndex == 0 && (
                                        line.startsWith(Constants.VERSE_RUS) || line.startsWith(Constants.CHORUS_RUS) ||
                                        line.startsWith(Constants.VERSE) || line.startsWith(Constants.CHORUS)
                                    )
                                    val textColor = if (sectionIndex == selectedSectionIndex)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                    if (isHeader) {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
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
}

