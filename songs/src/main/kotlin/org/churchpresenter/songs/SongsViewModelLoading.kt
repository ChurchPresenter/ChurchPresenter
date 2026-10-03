package org.churchpresenter.songs

import kotlinx.coroutines.launch
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

internal fun SongsViewModel.applySongList(songItems: List<SongItem>, songsObj: Songs? = null) {
    val songs = songsObj ?: Songs().also { it.addSongs(songItems) }
    songsDataState.value = songs

    // Collect songbooks including parent paths (e.g. "Kids/AM" also adds "Kids")
    // Use "/" for songs in the root directory
    val allPaths = mutableSetOf<String>()
    for (item in songItems) {
        val sb = item.songbook
        if (sb.isBlank()) {
            allPaths.add("/")
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

    onSongsLoaded?.invoke(songItems)
}
