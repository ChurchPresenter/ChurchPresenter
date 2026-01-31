package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
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
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.jetbrains.compose.resources.stringResource

@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
    onSongItemSelected: (LyricSection) -> Unit,
    presenting: (Presenting) -> Unit = { Presenting.NONE }
) {
    // Create Songs instance and load the bundled pv3300.sps resource
    val songsData = remember {
        Songs().apply {
            loadFromSps("pv3300.sps")
        }
    }

    val allSongBooksText = stringResource(Res.string.all_song_books)
    val allSongCategoriesText = stringResource(Res.string.all_song_categories)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedSongbook by rememberSaveable { mutableStateOf(allSongBooksText) }
    var selectedCategory by rememberSaveable { mutableStateOf(allSongCategoriesText) }
    var filterType by rememberSaveable { mutableStateOf("Contains") }
    var selectedSongIndex by rememberSaveable { mutableStateOf(2) } // Default to third song

    // State for selected verse/chorus section
    var selectedSectionIndex by rememberSaveable { mutableStateOf(-1) }

    // Sorting state
    var sortColumn by rememberSaveable { mutableStateOf("") }
    var sortAscending by rememberSaveable { mutableStateOf(true) }

    val songbooks = listOf(allSongBooksText, "Песнь Возрождения", "Гимны Веры", "Песни Радости")
    val categories = listOf(allSongCategoriesText, "Прославление", "Поклонение", "Евангелизация")
    val filterTypes = listOf(
        stringResource(Res.string.contains),
        stringResource(Res.string.starts_with),
        stringResource(Res.string.exact_match)
    )

    // Get all songs from data source
    val allSongs = songsData.getSongs()

    val allSongsText = stringResource(Res.string.all_song_books)
    // Filter songs based on search criteria
    val filteredSongs =
        remember(allSongs, searchQuery, selectedSongbook, selectedCategory, filterType, sortColumn, sortAscending) {
            var filtered = allSongs

            // Apply search filter
            if (searchQuery.isNotBlank()) {
                filtered = songsData.findSongs(searchQuery, filterType)
            }

            // Apply songbook filter
            if (selectedSongbook != allSongsText) {
                filtered = filtered.filter { it.songbook.contains(selectedSongbook, ignoreCase = true) }
            }

            // Apply category filter (placeholder - categories not clearly defined in SPS format)
            // TODO: Implement category filtering when category data is available
            // For now, category filtering is not implemented as categories aren't defined in SPS format
            // Category filtering would check: if (selectedCategory != allSongCategoriesText)

            // Apply sorting
            if (sortColumn.isNotEmpty()) {
                filtered = when (sortColumn) {
                    "number" -> if (sortAscending) {
                        filtered.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
                    } else {
                        filtered.sortedByDescending { it.number.toIntOrNull() ?: Int.MIN_VALUE }
                    }

                    "title" -> if (sortAscending) {
                        filtered.sortedBy { it.title.lowercase() }
                    } else {
                        filtered.sortedByDescending { it.title.lowercase() }
                    }

                    "songbook" -> if (sortAscending) {
                        filtered.sortedBy { it.songbook.lowercase() }
                    } else {
                        filtered.sortedByDescending { it.songbook.lowercase() }
                    }

                    "tune" -> if (sortAscending) {
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

    // Ensure selectedSongIndex is within bounds
    LaunchedEffect(filteredSongs.size) {
        if (selectedSongIndex >= filteredSongs.size) {
            selectedSongIndex = if (filteredSongs.isNotEmpty()) 0 else -1
        }
        selectedSectionIndex = -1 // Reset section selection when songs change
    }

    // Reset section selection when song changes
    LaunchedEffect(selectedSongIndex) {
        selectedSectionIndex = -1
    }


    Row(modifier = modifier.fillMaxSize()) {
        // Left panel - Search and song list
        Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
            // Search controls
            Column(modifier = Modifier.padding(8.dp)) {
                // Songbooks dropdown
                DropdownSelector(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    label = "",
                    items = songbooks,
                    selected = selectedSongbook,
                    onSelectedChange = { selectedSongbook = it }
                )

                // Categories dropdown
                DropdownSelector(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    label = "",
                    items = categories,
                    selected = selectedCategory,
                    onSelectedChange = { selectedCategory = it }
                )

                // Filter section
                Text(
                    stringResource(Res.string.filter_colon),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.filter_type_colon),
                        style = MaterialTheme.typography.labelMedium
                    )
                    DropdownSelector(
                        modifier = Modifier.weight(1f),
                        label = "",
                        items = filterTypes,
                        selected = filterType,
                        onSelectedChange = { filterType = it }
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
                    onValueChange = { searchQuery = it },
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
                    text = stringResource(Res.string.number) + getSortIndicator("number"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(70.dp).clickable { onColumnClick("number") }
                )
                Text(
                    text = stringResource(Res.string.title) + getSortIndicator("title"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f).clickable { onColumnClick("title") }
                )
                Text(
                    text = stringResource(Res.string.song_book) + getSortIndicator("songbook"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(100.dp).clickable { onColumnClick("songbook") }
                )
                Text(
                    text = stringResource(Res.string.tune) + getSortIndicator("tune"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(60.dp).clickable { onColumnClick("tune") }
                )
            }
            Box(
                modifier = Modifier.weight(1f)
            ) {
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
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
                                    selectedSongIndex = index
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
                    onClick = { presenting.invoke(Presenting.LYRICS) },
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
            }

            // Lyrics content
            Box {
                val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()
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
                        val sections = mutableListOf<LyricSection>()
                        var currentSection = mutableListOf<String>()
                        var currentSectionType = ""

                        // Group lyrics into sections
                        for (line in lyrics) {
                            if (line.startsWith("Куплет") || line.startsWith("Припев")) {
                                // Save previous section if it exists
                                if (currentSection.isNotEmpty()) {
                                    sections.add(LyricSection(currentSectionType, currentSection.toList()))
                                }
                                // Start new section
                                currentSection = mutableListOf(line)
                                currentSectionType = if (line.startsWith("Куплет")) "verse" else "chorus"
                            } else {
                                currentSection.add(line)
                            }
                        }
                        // Add the last section
                        if (currentSection.isNotEmpty()) {
                            sections.add(LyricSection(currentSectionType, currentSection.toList()))
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
                                        selectedSectionIndex =
                                            if (selectedSectionIndex == sectionIndex) -1 else sectionIndex
                                        onSongItemSelected.invoke(section)
                                    }
                                    .padding(8.dp)
                            ) {
                                section.lines.forEachIndexed { lineIndex, line ->
                                    if (lineIndex == 0 && (line.startsWith("Куплет") || line.startsWith("Припев"))) {
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