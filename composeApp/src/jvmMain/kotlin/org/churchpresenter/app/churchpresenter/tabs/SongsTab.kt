package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.all_song_books
import churchpresenter.composeapp.generated.resources.all_song_categories
import churchpresenter.composeapp.generated.resources.contains
import churchpresenter.composeapp.generated.resources.exact_match
import churchpresenter.composeapp.generated.resources.filter_colon
import churchpresenter.composeapp.generated.resources.filter_type_colon
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.no_lyrics_available
import churchpresenter.composeapp.generated.resources.no_song_selected
import churchpresenter.composeapp.generated.resources.number
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.search_songs
import churchpresenter.composeapp.generated.resources.song_book
import churchpresenter.composeapp.generated.resources.starts_with
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.tune
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Constants.CHORUS
import org.churchpresenter.app.churchpresenter.utils.Constants.CHORUS_RUS
import org.churchpresenter.app.churchpresenter.utils.Constants.VERSE
import org.churchpresenter.app.churchpresenter.utils.Constants.VERSE_RUS
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
    viewModel: SongsViewModel,
    scheduleViewModel: org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel? = null,
    onSongItemSelected: (LyricSection) -> Unit,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE }
) {
    // Get state from ViewModel
    val songbooks by viewModel.songbooks
    val searchQuery by viewModel.searchQuery
    val selectedSongbook by viewModel.selectedSongbook
    val filterType by viewModel.filterType
    val selectedSongIndex by viewModel.selectedSongIndex
    val selectedSectionIndex by viewModel.selectedSectionIndex

    // String resources
    val allSongBooksText = stringResource(Res.string.all_song_books)
    val allSongCategoriesText = stringResource(Res.string.all_song_categories)

    // Local category state (not yet in ViewModel)
    var selectedCategory by rememberSaveable { mutableStateOf(allSongCategoriesText) }

    // Prepend "All" option to songbooks
    val songbookOptions = remember(songbooks) { listOf(allSongBooksText) + songbooks }

    // Local state for sorting
    var sortColumn by rememberSaveable { mutableStateOf("") }
    var sortAscending by rememberSaveable { mutableStateOf(true) }

    // TODO: Categories should come from database or configuration
    // val categories = listOf(allSongCategoriesText, "Прославление", "Поклонение", "Евангелизация")

    // String resources for filter types
    val containsText = stringResource(Res.string.contains)
    val startsWithText = stringResource(Res.string.starts_with)
    val exactMatchText = stringResource(Res.string.exact_match)

    val filterTypes = listOf(containsText, startsWithText, exactMatchText)

    // Map filter type display text to internal key
    val filterTypeMap = mapOf(
        containsText to Constants.CONTAINS,
        startsWithText to Constants.STARTS_WITH,
        exactMatchText to Constants.EXACT_MATCH
    )

    // Map internal key to display text
    val filterTypeDisplayMap = mapOf(
        Constants.CONTAINS to containsText,
        Constants.STARTS_WITH to startsWithText,
        Constants.EXACT_MATCH to exactMatchText
    )

    // Get filtered songs from ViewModel (not recalculating locally to ensure consistency)
    val filteredSongsFromViewModel by viewModel.filteredSongs

    // Convert from List<String> to List<Song> for display
    val allSongs = viewModel.songsData.value.getSongs()
    val filteredSongs = remember(filteredSongsFromViewModel, sortColumn, sortAscending) {
        // Map the string list back to Song objects
        val songsMap = allSongs.associateBy { "${it.number}. ${it.title}" }
        var filtered = filteredSongsFromViewModel.mapNotNull { songText ->
            songsMap[songText]
        }

        // Apply sorting (only sorting is done in UI, filtering is in ViewModel)
        if (sortColumn.isNotEmpty()) {
            filtered = when (sortColumn) {
                Constants.SORT_NUMBER -> if (sortAscending) {
                    filtered.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
                } else {
                    filtered.sortedByDescending { it.number.toIntOrNull() ?: Int.MIN_VALUE }
                }

                Constants.SORT_TITLE -> if (sortAscending) {
                    filtered.sortedBy { it.title.lowercase() }
                } else {
                    filtered.sortedByDescending { it.title.lowercase() }
                }

                Constants.SORT_SONGBOOK -> if (sortAscending) {
                    filtered.sortedBy { it.songbook.lowercase() }
                } else {
                    filtered.sortedByDescending { it.songbook.lowercase() }
                }

                Constants.SORT_TUNE -> if (sortAscending) {
                    filtered.sortedBy { it.tune.lowercase() }
                } else {
                    filtered.sortedByDescending { it.tune.lowercase() }
                }

                else -> filtered
            }
        }

        filtered
    }

    // Helper function to handle column sorting
    val onColumnClick: (String) -> Unit = { column ->
        if (sortColumn == column) {
            sortAscending = !sortAscending
        } else {
            sortColumn = column
            sortAscending = true
        }
    }

    // Helper function to get sort indicator
    val getSortIndicator: (String) -> String = { column ->
        if (sortColumn == column) {
            if (sortAscending) " ↑" else " ↓"
        } else ""
    }


    Row(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            // Previous song
                            viewModel.navigatePreviousSong()
                            true
                        }

                        Key.DirectionRight -> {
                            // Next song
                            viewModel.navigateNextSong()
                            true
                        }

                        Key.DirectionUp -> {
                            // Navigate up: Try previous section first, if at top, go to previous song
                            val sectionNavigated = viewModel.navigatePreviousSection()
                            if (!sectionNavigated) {
                                // If we can't go to previous section, go to previous song
                                viewModel.navigatePreviousSong()
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            // Navigate down: Try next section first, if at bottom, go to next song
                            val navigated = viewModel.navigateNextSection()
                            if (navigated) {
                                viewModel.getSelectedLyricSection()?.let { section ->
                                    onSongItemSelected(section)
                                }
                            } else {
                                // If we can't go to next section, go to next song
                                viewModel.navigateNextSong()
                            }
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
    ) {
        // Left panel - Search and song list
        Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
            // Search controls
            Column(modifier = Modifier.padding(8.dp)) {
                // Songbooks dropdown
                DropdownSelector(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    label = "",
                    items = songbookOptions,
                    selected = selectedSongbook.ifEmpty { allSongBooksText },
                    onSelectedChange = { viewModel.updateSelectedSongbook(it) }
                )

//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text(
//                        stringResource(Res.string.filter_colon),
//                        style = MaterialTheme.typography.labelMedium,
//                        color = MaterialTheme.colorScheme.primary,
//                        modifier = Modifier.padding(vertical = 4.dp)
//                    )
//                    DropdownSelector(
//                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
//                        label = "",
//                        items = categories,
//                        selected = selectedCategory,
//                        onSelectedChange = { selectedCategory = it }
//                    )
//                }

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
                        Text(
                            stringResource(Res.string.search),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    label = {
                        Text(
                            stringResource(Res.string.search_songs),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors().copy(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }

            // Song list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.number) + getSortIndicator(Constants.SORT_NUMBER),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(70.dp).clickable { onColumnClick(Constants.SORT_NUMBER) }
                )
                Text(
                    text = stringResource(Res.string.title) + getSortIndicator(Constants.SORT_TITLE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f).clickable { onColumnClick(Constants.SORT_TITLE) }
                )
                Text(
                    text = stringResource(Res.string.song_book) + getSortIndicator(Constants.SORT_SONGBOOK),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(100.dp).clickable { onColumnClick(Constants.SORT_SONGBOOK) }
                )
                Text(
                    text = stringResource(Res.string.tune) + getSortIndicator(Constants.SORT_TUNE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(60.dp).clickable { onColumnClick(Constants.SORT_TUNE) }
                )
            }
            Box(
                modifier = Modifier.weight(1f)
            ) {
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

                // Auto-scroll to selected song when selection changes
                LaunchedEffect(selectedSongIndex, filteredSongs.size) {
                    println("DEBUG SongsTab: LaunchedEffect triggered - selectedSongIndex=$selectedSongIndex, filteredSongs.size=${filteredSongs.size}")
                    if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                        println("DEBUG SongsTab: Scrolling to item at index $selectedSongIndex")
                        // Small delay to ensure the list is fully composed
                        kotlinx.coroutines.delay(100)
                        lazyListState.animateScrollToItem(selectedSongIndex)
                        println("DEBUG SongsTab: Scroll animation complete")
                    } else {
                        println("DEBUG SongsTab: Skipping scroll - index out of bounds")
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
                    // ...existing itemsIndexed content...
                    itemsIndexed(filteredSongs) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index == selectedSongIndex && index < filteredSongs.size)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    viewModel.selectSong(index)
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = song.number,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(70.dp),
                                color = if (index == selectedSongIndex)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == selectedSongIndex)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = song.songbook,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(100.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == selectedSongIndex)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = song.tune,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(60.dp),
                                maxLines = 1,
                                color = if (index == selectedSongIndex)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = lazyListState)
                )
            }
        }

        // Right panel - Lyrics display
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    text = if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size)
                        filteredSongs[selectedSongIndex].title
                    else stringResource(Res.string.no_song_selected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    modifier = Modifier.wrapContentSize(),
                    onClick = { onPresenting(Presenting.LYRICS) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.go_live),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 2
                    )
                }

                // Add to Schedule button
                if (scheduleViewModel != null && selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                    Button(
                        modifier = Modifier.wrapContentSize().padding(start = 4.dp),
                        onClick = {
                            val song = filteredSongs[selectedSongIndex]
                            scheduleViewModel.addSong(
                                songNumber = song.number.toIntOrNull() ?: 0,
                                title = song.title,
                                songbook = song.songbook
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.add_to_schedule),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondary,
                            maxLines = 2
                        )
                    }
                }
            }

            // Lyrics content
            Box {
                val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()

                // Auto-scroll to selected section when selection changes
                LaunchedEffect(selectedSectionIndex) {
                    if (selectedSectionIndex >= 0) {
                        lyricsListState.animateScrollToItem(selectedSectionIndex)
                    }
                }

                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    // ...existing lyrics content...
                    if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size && filteredSongs[selectedSongIndex].lyrics.isNotEmpty()) {
                        val lyrics = filteredSongs[selectedSongIndex].lyrics
                        val title = filteredSongs[selectedSongIndex].title
                        val songNumber = filteredSongs[selectedSongIndex].number.toInt()
                        val sections = mutableListOf<LyricSection>()
                        var currentSection = mutableListOf<String>()
                        var currentSectionType = ""

                        // Group lyrics into sections
                        lyrics.forEachIndexed { index, line ->
                            if (line.startsWith(VERSE_RUS) || line.startsWith(CHORUS_RUS)) {
                                // Save previous section if it exists
                                if (currentSection.isNotEmpty()) {
                                    sections.add(
                                        LyricSection(
                                            title = title,
                                            type = currentSectionType,
                                            lines = currentSection.toList(),
                                            songNumber = songNumber,
                                        )
                                    )
                                }
                                // Start new section
                                currentSection = mutableListOf(line)
                                currentSectionType = if (line.startsWith(CHORUS_RUS)) VERSE else CHORUS
                            } else {
                                currentSection.add(line)
                            }
                        }
                        // Add the last section
                        if (currentSection.isNotEmpty()) {
                            sections.add(
                                LyricSection(
                                    title = title,
                                    type = currentSectionType,
                                    lines = currentSection.toList(),
                                    songNumber = songNumber,
                                )
                            )
                        }

                        // Display sections
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
                                        onSongItemSelected.invoke(section)
                                    }
                                    .padding(8.dp)
                            ) {
                                section.lines.forEachIndexed { lineIndex, line ->
                                    if (lineIndex == 0 && (line.startsWith(VERSE_RUS) || line.startsWith(CHORUS_RUS))) {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sectionIndex == selectedSectionIndex)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (sectionIndex == selectedSectionIndex)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onSurface,
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
}