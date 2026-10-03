package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.sharedui.utils.isHeaderLine
import org.churchpresenter.sharedui.utils.isSlideBreak
import org.churchpresenter.sharedui.utils.songBackgroundDirectiveOf

private val WHITESPACE_RUN = Regex("\\s+")

internal fun SongsViewModel.applyFilters() {
    var filtered = allSongItemsState.value

    // Filter by songbook - only apply if a real songbook is selected (not "All Song Books")
    // Uses prefix matching so selecting "Kids" also shows "Kids/AM" and "Kids/PM"
    if (selectedSongbookState.value.isNotEmpty() && songbooksState.value.contains(selectedSongbookState.value)) {
        val selected = selectedSongbookState.value
        filtered = if (selected == "/") {
            filtered.filter { it.songbook.isBlank() }
        } else {
            filtered.filter { it.songbook == selected || it.songbook.startsWith("$selected/") }
        }
    }

    // Filter by search query.
    //
    // Matched trimmed, while the box keeps what was typed: a query pasted from a service plan or
    // an email routinely carries a leading or trailing space, and matching it raw made the search
    // come back empty with nothing on screen to explain why — the worst possible moment being
    // mid-service. Only the ends are trimmed; whitespace inside a title is still significant, so
    // "Be Thou  My Vision" and "Be Thou My Vision" remain different queries.
    val query = searchQueryState.value.trim()
    if (isNumberQuery(query)) {
        // Digits alone are a song number: matching them against titles and lyrics as well
        // would bury song 48 under every hymn with "48" somewhere in its words.
        filtered = when (filterTypeState.value) {
            Constants.CONTAINS -> filtered.filter { it.number.contains(query) }
            Constants.STARTS_WITH -> filtered.filter { it.number.startsWith(query) }
            Constants.EXACT_MATCH -> filtered.filter { it.number.trim() == query }
            else -> filtered
        }
    } else if (query.isNotEmpty()) {
        filtered = when (filterTypeState.value) {
            Constants.CONTAINS -> filtered.filter { song ->
                song.searchTitles().any { "${song.number}. $it".contains(query, ignoreCase = true) } ||
                    searchLyricsOf(song).contains(query, ignoreCase = true)
            }
            Constants.STARTS_WITH -> filtered.filter { song ->
                song.number.startsWith(query, ignoreCase = true) ||
                    song.searchTitles().any { it.startsWith(query, ignoreCase = true) }
            }
            Constants.EXACT_MATCH -> filtered.filter { song ->
                song.number.trim().equals(query, ignoreCase = true) ||
                    song.searchTitles().any { it.trim().equals(query, ignoreCase = true) }
            }
            else -> filtered
        }
    }

    filteredSongsListState.value = filtered

    // Adjust selected index if needed
    if (selectedSongIndexState.value >= filtered.size && filtered.isNotEmpty()) {
        selectedSongIndexState.value = 0
    }

    refreshFilteredSongItems()

    // Re-select song by sourceFile after reload (preserves selection across edits)
    val pendingFile = pendingSelectSourceFile
    if (pendingFile != null) {
        val items = filteredSongItemsState.value
        val idx = items.indexOfFirst { it.sourceFile == pendingFile }
        if (idx >= 0) {
            selectedSongIndexState.value = idx
            pendingSelectSourceFile = null
            // The re-select moved the song after [refreshFilteredSongItems] clamped, so the
            // section index has to be checked against this song too.
            clampSectionSelection()
        }
    }
}

/**
 * Every name this song can be found by: the primary title and each translation's.
 *
 * A bilingual song is one song with several names, and the list only ever shows the primary --
 * so an operator who knows a song by the name half the room sings could not find it by typing
 * that name. All three filter types match against every one of them; the number is still the
 * number, which no translation has its own of.
 *
 * Blanks are dropped rather than matched: a language that carries lyrics but no title of its
 * own would otherwise make an empty query-shaped match on the [Constants.EXACT_MATCH] path.
 */
private fun SongItem.searchTitles(): List<String> =
    (listOf(title) + extraTranslations().map { it.title }).filter { it.isNotBlank() }

internal fun SongsViewModel.searchLyricsOf(song: SongItem): String {
    if (lyricSearchTextFor !== allSongItemsState.value) {
        lyricSearchText.clear()
        lyricSearchTextFor = allSongItemsState.value
    }
    return lyricSearchText.getOrPut(song) {
        song.translationList().asSequence()
            .flatMap { it.lyrics.asSequence() }
            .filterNot { isHeaderLine(it) || isSlideBreak(it) || songBackgroundDirectiveOf(it) != null }
            .map { ChordTransposer.stripChords(it) }
            .joinToString(" ")
            .replace(WHITESPACE_RUN, " ")
    }
}

/**
 * Where the current search found [song] -- a title, a named section, which language -- for the
 * results list to show under the row; null when the box is empty, holds only digits (a song
 * number, which the number column already shows), or [song] is not a match.
 *
 * Reads [searchQuery], so a composable calling it follows the query as it is typed.
 */
fun SongsViewModel.searchMatchFor(song: SongItem): SongSearchMatch? {
    val query = searchQueryState.value.trim()
    if (query.isEmpty() || isNumberQuery(query)) return null
    if (lyricSectionsFor !== allSongItemsState.value) {
        lyricSections.clear()
        lyricSectionsFor = allSongItemsState.value
    }
    val cached = lyricSections.getOrPut(song) { mutableMapOf() }
    return findSongMatch(song, query) { index, lyrics -> cached.getOrPut(index) { searchableSections(lyrics) } }
}

internal fun SongsViewModel.isNumberQuery(query: String): Boolean = query.isNotEmpty() && query.all(Char::isDigit)

/** [items] in the order [column] asks for; the sort itself, without the selection bookkeeping. */
internal fun SongsViewModel.sortedBy(column: String, items: List<SongItem>): List<SongItem> = when (column) {
    Constants.SORT_NUMBER -> if (sortAscendingState.value)
        items.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
    else
        items.sortedByDescending { it.number.toIntOrNull() ?: Int.MIN_VALUE }
    Constants.SORT_TITLE -> if (sortAscendingState.value)
        items.sortedBy { it.title.lowercase() }
    else
        items.sortedByDescending { it.title.lowercase() }
    Constants.SORT_SONGBOOK -> if (sortAscendingState.value)
        items.sortedBy { it.songbook.lowercase() }
    else
        items.sortedByDescending { it.songbook.lowercase() }
    Constants.SORT_TUNE -> if (sortAscendingState.value)
        items.sortedBy { it.tune.lowercase() }
    else
        items.sortedByDescending { it.tune.lowercase() }
    Constants.SORT_PLAY_COUNT -> {
        val sm = playCounts
        if (sm != null) {
            val counts = items.associate { it.songId to sm.getSongPlayCount(it.songId) }
            if (sortAscendingState.value) items.sortedBy { counts[it.songId] ?: 0 }
            else items.sortedByDescending { counts[it.songId] ?: 0 }
        } else items
    }
    Constants.SORT_FAVORITES -> {
        val favIds = favoritesState.value
        if (sortAscendingState.value)
            items.sortedBy { if (it.songId in favIds) 0 else 1 }
        else
            items.sortedByDescending { if (it.songId in favIds) 0 else 1 }
    }
    Constants.SORT_AUTHOR -> if (sortAscendingState.value)
        items.sortedBy { it.author.lowercase() }
    else
        items.sortedByDescending { it.author.lowercase() }
    Constants.SORT_COMPOSER -> if (sortAscendingState.value)
        items.sortedBy { it.composer.lowercase() }
    else
        items.sortedByDescending { it.composer.lowercase() }
    else -> items
}

internal fun SongsViewModel.refreshFilteredSongItems() {
    val unsorted = filteredSongsListState.value
    val items = if (sortColumnState.value.isEmpty()) unsorted else sortedBy(sortColumnState.value, unsorted)

    filteredSongItemsState.value = items
    // A re-sort leaves selectedSongIndexState where it was, so a different song — with a different
    // number of sections — now sits under it.
    clampSectionSelection()
}

fun SongsViewModel.updateSort(column: String) {
    if (sortColumnState.value == column) {
        sortAscendingState.value = !sortAscendingState.value
    } else {
        sortColumnState.value = column
        sortAscendingState.value = true
    }
    refreshFilteredSongItems()
}

fun SongsViewModel.getSortIndicator(column: String): String {
    return if (sortColumnState.value == column) {
        if (sortAscendingState.value) " ↑" else " ↓"
    } else ""
}
