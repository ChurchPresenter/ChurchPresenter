package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap
import org.churchpresenter.core.models.songs.SongItem

internal fun SongsViewModel.fetchRemoteDetailIfNeeded(index: Int) {
    val fetchLyrics = remoteFetchLyrics ?: return
    val items = filteredSongItemsState.value
    if (index < 0 || index >= items.size) return
    val song = items[index]
    if (song.lyrics.isNotEmpty()) return
    viewModelScope.launch {
        val lyrics = fetchLyrics(song.number, song.songbook)
        remoteSyncLog(
            "song_detail_fetch_result",
            mapOf("number" to song.number, "songbook" to song.songbook, "success" to (lyrics != null)),
        )
        if (lyrics == null) return@launch
        val updated = song.copy(lyrics = lyrics)
        fun List<SongItem>.replaced() = map { if (it.songId == song.songId) updated else it }
        filteredSongItemsState.value = filteredSongItemsState.value.replaced()
        allSongItemsState.value = allSongItemsState.value.replaced()
        songsDataState.value = Songs().also { it.addSongs(allSongItemsState.value) }
        // The fetched lyrics are the first sections this song has had; an index carried over from
        // whatever was selected before must not survive past their end.
        clampSectionSelection()
        // Only nudge the presenter if this song is still the one selected — the operator may
        // have already moved on to a different song by the time this fetch resolves.
        val currentIdx = selectedSongIndexState.value
        val current = filteredSongItemsState.value.getOrNull(currentIdx)
        if (current?.songId == song.songId) {
            remoteLyricsUpdatedState.value++
        }
    }
}

/** How the song book list names the songs at the top of the songs folder, which have no song book. */
internal const val ROOT_SONGBOOK = "/"

internal fun SongsViewModel.applySongList(songItems: List<SongItem>, songsObj: Songs? = null) {
    val songs = songsObj ?: Songs().also { it.addSongs(songItems) }
    songsDataState.value = songs

    // Collect songbooks including parent paths (e.g. "Kids/AM" also adds "Kids")
    // Use [ROOT_SONGBOOK] for songs in the root directory
    val allPaths = mutableSetOf<String>()
    for (item in songItems) {
        val sb = item.songbook
        if (sb.isBlank()) {
            allPaths.add(ROOT_SONGBOOK)
            continue
        }
        allPaths.add(sb)
        // Add all parent segments
        var idx = sb.indexOf('/')
        while (idx > 0) {
            allPaths.add(sb.substring(0, idx))
            idx = sb.indexOf('/', idx + 1)
        }
    }
    songbooksState.value = allPaths.sorted()

    allSongItemsState.value = songItems
    applyFilters()
    warmLyricSearch(songItems)

    onSongsLoaded?.invoke(songItems)
}

/**
 * Builds the lyric search's text for every song in [songItems] off the event thread, so the first
 * search after a library loads does not strip the chords of the whole library while the operator
 * types. Dropped if the library changed again in the meantime.
 */
internal fun SongsViewModel.warmLyricSearch(songItems: List<SongItem>) {
    viewModelScope.launch {
        val built = withContext(Dispatchers.Default) {
            songItems.associateWithTo(IdentityHashMap()) { lyricSearchTextOf(it) }
        }
        if (allSongItemsState.value !== songItems) return@launch
        if (lyricSearchTextFor !== songItems) {
            lyricSearchText.clear()
            lyricSearchTextFor = songItems
        }
        lyricSearchText.putAll(built)
    }
}
