package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
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
    val allSongsCategoriesText = stringResource(Res.string.all_song_categories)
    // Filter songs based on search criteria
    val filteredSongs = remember(allSongs, searchQuery, selectedSongbook, selectedCategory, filterType) {
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
        if (selectedCategory != allSongsCategoriesText) {
            // For now, return all filtered songs since categories aren't clearly defined
        }

        filtered
    }

    // Ensure selectedSongIndex is within bounds
    LaunchedEffect(filteredSongs.size) {
        if (selectedSongIndex >= filteredSongs.size) {
            selectedSongIndex = if (filteredSongs.isNotEmpty()) 0 else -1
        }
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
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(Res.string.filter_type_colon), fontSize = 12.sp)
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
                        Text(stringResource(Res.string.search), fontSize = 12.sp)
                    }
                }

                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(Res.string.search_songs), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors().copy(
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White,
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
                    stringResource(Res.string.number),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(60.dp),
                    fontSize = 12.sp
                )
                Text(
                    stringResource(Res.string.title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp
                )
                Text(
                    stringResource(Res.string.song_book),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(100.dp),
                    fontSize = 12.sp
                )
                Text(
                    stringResource(Res.string.tune),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(60.dp),
                    fontSize = 12.sp
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .padding(horizontal = 8.dp)
            ) {
                itemsIndexed(filteredSongs) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == selectedSongIndex && index < filteredSongs.size)
                                    Color.Blue.copy(alpha = 0.3f)
                                else Color.White
                            )
                            .clickable {
                                selectedSongIndex = index
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = song.number,
                            modifier = Modifier.width(60.dp),
                            fontSize = 12.sp,
                            color = if (index == selectedSongIndex) Color.White else Color.Black
                        )
                        Text(
                            text = song.title,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = if (index == selectedSongIndex) Color.White else Color.Black
                        )
                        Text(
                            text = song.songbook,
                            modifier = Modifier.width(100.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = if (index == selectedSongIndex) Color.White else Color.Black
                        )
                        Text(
                            text = song.tune,
                            modifier = Modifier.width(60.dp),
                            fontSize = 12.sp,
                            color = if (index == selectedSongIndex) Color.White else Color.Black
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = Color.Gray.copy(alpha = 0.3f))
                }
            }
        }

        // Right panel - Lyrics display
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(Color.Gray.copy(alpha = 0.1f))
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    modifier = Modifier.wrapContentSize(),
                    onClick = { /* Go Live action */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text(
                        text = stringResource(Res.string.go_live),
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }

            // Lyrics content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size && filteredSongs[selectedSongIndex].lyrics.isNotEmpty()) {
                    items(filteredSongs[selectedSongIndex].lyrics.size) { index ->
                        val lyricLine = filteredSongs[selectedSongIndex].lyrics[index]
                        if (lyricLine.startsWith("Куплет") || lyricLine.startsWith("Припев")) {
                            Text(
                                text = lyricLine,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Text(
                                text = lyricLine,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(Res.string.no_lyrics_available),
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}