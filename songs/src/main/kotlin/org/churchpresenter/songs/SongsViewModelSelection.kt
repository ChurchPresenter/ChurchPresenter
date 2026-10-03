package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem

fun SongsViewModel.updateSearchQuery(query: String) {
    searchQueryState.value = query
    applyFilters()
}

fun SongsViewModel.updateSelectedSongbook(songbook: String) {
    selectedSongbookState.value = songbook
    applyFilters()
}

fun SongsViewModel.updateFilterType(filterType: String) {
    filterTypeState.value = filterType
    applyFilters()
}

fun SongsViewModel.selectSong(index: Int) {
    selectedSongIndexState.value = index
    selectedSectionIndexState.value = 0
    selectedLineIndexState.value = 0
    fetchRemoteDetailIfNeeded(index)
}

fun SongsViewModel.selectSongByDetails(songNumber: Int, title: String, songbook: String, songId: String = ""): Boolean {
    val allSongs = songsDataState.value.getSongs()

    // 1. Primary: stable songId "songbook::number". Unambiguous across songbooks, but not
    // within one: a real library repeats a number in a book, and the id is built from the
    // number -- so among the songs that share it, the one whose title matches wins, and
    // only then the first.
    val songData = allSongs.filter { songId.isNotBlank() && it.songId == songId }.preferringTitle(title)
    // 2. Fallback: songbook + number (old saved schedules without songId, and every mirrored
    // Instance Link schedule item — the wire protocol has no songId field at all, only a plain
    // Int songNumber). Compare numerically, not as raw strings: a catalog entry's number may be
    // zero-padded (e.g. "0042") while songNumber is always a plain Int (42) with no way to
    // recover the original padding, so a string comparison would silently never match.
        ?: allSongs.filter {
            it.songbook.equals(songbook, ignoreCase = true) &&
                (it.number.toIntOrNull()?.let { n -> n == songNumber } ?: (it.number == songNumber.toString()))
        }.preferringTitle(title)
    // 3. Last resort: title only
        ?: allSongs.find { it.title.equals(title, ignoreCase = true) }

    if (songData == null) return false

    // Find index in filteredSongItemsState (what the UI renders). By the song's file, not its
    // id: the id is what three same-numbered songs share, and matching on it here would
    // undo the choice just made above.
    var idx = filteredSongItemsState.value.indexOfFirst { it.isSameSongAs(songData) }

    if (idx < 0) {
        // Song is outside current filter — clear filters so the song stays visible at the correct index
        selectedSongbookState.value = ""
        searchQueryState.value = ""
        applyFilters()
        idx = filteredSongItemsState.value.indexOfFirst { it.isSameSongAs(songData) }
        if (idx < 0) return false
    }

    selectedSongIndexState.value = idx
    selectedSectionIndexState.value = 0
    fetchRemoteDetailIfNeeded(idx)
    return true
}

/** Selects a song by its stable songId alone, clearing filters if needed to reveal it. */
fun SongsViewModel.selectSongById(songId: String): Boolean = selectSongByDetails(0, "", "", songId)

/**
 * Of songs that share an id, the one titled [title]; failing that, the first. Null when there
 * are none. A blank [title] -- a caller that only has an id -- takes the first, as before.
 */
private fun List<SongItem>.preferringTitle(title: String): SongItem? =
    firstOrNull { title.isNotBlank() && it.title.equals(title.trim(), ignoreCase = true) } ?: firstOrNull()

/**
 * Whether this is the same library song as [other]. By file where both know theirs -- the one
 * thing three same-numbered songs cannot share -- and by id plus title otherwise.
 */
private fun SongItem.isSameSongAs(other: SongItem): Boolean =
    if (sourceFile.isNotBlank() && other.sourceFile.isNotBlank()) {
        sourceFile == other.sourceFile
    } else {
        songId == other.songId && title.equals(other.title, ignoreCase = true)
    }

fun SongsViewModel.selectSection(index: Int) {
    // Clamped, because the index does not always come from the rendered list: a phone pushes one
    // through MainDesktop's songDisplaySectionIndex collector, and Back-to-Live replays one
    // remembered from an earlier — possibly longer — version of the song.
    selectedSectionIndexState.value = index.coerceAtMost(getLyricSections().lastIndex)
    selectedLineIndexState.value = 0
}

/**
 * Keeps the section and line selection inside the song they now point at.
 *
 * There is no stored section list — [getLyricSections] recomputes from the selected song on every
 * call — so the list changes under the index whenever the song does: a filter or sort change puts
 * a different song at the same row, an edit or a folder-watcher reload can drop a verse, and an
 * Instance Link catalog arrives with no lyrics at all. An index left past the end presented the
 * wrong slide, and walking down from it crashed [navigatePreviousSection].
 *
 * The line index needs the same treatment for its own reason: a new song with as many sections
 * as the old one leaves the section index untouched, so only the line index is left pointing at
 * a line the section does not have — which shows a blank slide rather than the line the row is
 * highlighting.
 *
 * `-1` is a real value for both — the whole-song/title slide, and "no line chosen" — and is
 * preserved.
 */
internal fun SongsViewModel.clampSectionSelection() {
    val sections = getLyricSections()
    if (selectedSectionIndexState.value > sections.lastIndex) {
        selectedSectionIndexState.value = sections.lastIndex // -1 when the song has no sections
        selectedLineIndexState.value = 0
    }
    // Through [getSelectedLyricSection], because section -1 is the whole-song slide and its
    // lines are the song's — a line index into that one is as real as any other.
    val lineCount = getSelectedLyricSection()?.lines?.size ?: 0
    if (selectedLineIndexState.value >= lineCount) {
        selectedLineIndexState.value = (lineCount - 1).coerceAtLeast(0)
    }
}
