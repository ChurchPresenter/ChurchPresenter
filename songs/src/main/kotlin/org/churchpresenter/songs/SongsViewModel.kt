package org.churchpresenter.songs

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.songs.CachedSong
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.util.IdentityHashMap

class SongsViewModel(
    internal var appSettings: AppSettings,
    internal val onSongsLoaded: ((List<SongItem>) -> Unit)? = null,
    // Test seams (production defaults reproduce the shipping behaviour):
    //  - [dispatcher] backs the view-model scope. It is Dispatchers.Main in the app so state updates
    //    land on the UI thread; tests pass Dispatchers.Default so many view models loading at once
    //    don't all queue behind each other on the single Swing event thread and time out.
    //  - [ioDispatcher] runs the file reads. Tests pass an immediate dispatcher so a load completes
    //    synchronously instead of queueing on a shared pool — with Dispatchers.IO hardcoded here the
    //    tests had no way to control the part that does the work, and had to poll a wall clock for
    //    it, which times out on a loaded CI runner (issue #56).
    //  - [enableFolderWatcher] starts the directory watcher that reloads on file changes. Tests turn
    //    it off so a blocking watch loop and its self-triggered reloads don't race the assertions.
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val enableFolderWatcher: Boolean = true,
    // Where the Instance Link follower's catalog and lyric fetches are logged; the app's
    // InstanceLinkLogger.
    internal val remoteSyncLog: (event: String, fields: Map<String, Any?>) -> Unit = { _, _ -> },
) {
    internal val songsDataState = mutableStateOf(Songs())

    val songsData: State<Songs> = songsDataState

    internal val isLoadingState = mutableStateOf(false)

    val isLoading: State<Boolean> = isLoadingState

    internal val viewModelScope = CoroutineScope(dispatcher + SupervisorJob())

    internal var loadSongsJob: kotlinx.coroutines.Job? = null

    internal val songFolderWatcher = SongFolderWatcher(viewModelScope) { loadSongs() }

    internal val allSongItemsState = mutableStateOf<List<SongItem>>(emptyList())

    internal val songbooksState = mutableStateOf<List<String>>(emptyList())

    val songbooks: State<List<String>> = songbooksState

    internal val searchQueryState = mutableStateOf("")

    val searchQuery: State<String> = searchQueryState

    internal val selectedSongbookState = mutableStateOf("")

    val selectedSongbook: State<String> = selectedSongbookState

    internal val filterTypeState = mutableStateOf(Constants.CONTAINS)

    val filterType: State<String> = filterTypeState

    internal val selectedSongIndexState = mutableStateOf(0)

    val selectedSongIndex: State<Int> = selectedSongIndexState

    internal val selectedSectionIndexState = mutableStateOf(-1)

    val selectedSectionIndex: State<Int> = selectedSectionIndexState

    internal val selectedLineIndexState = mutableStateOf(-1)

    val selectedLineIndex: State<Int> = selectedLineIndexState

    internal val filteredSongsListState = mutableStateOf<List<SongItem>>(emptyList())

    // After edit/reload, re-select the song by its sourceFile path
    internal var pendingSelectSourceFile: String? = null

    // Favorites — persisted via AppSettings.songFavorites
    internal val favoritesState = mutableStateOf<Set<String>>(appSettings.songFavorites.toSet())

    val favorites: State<Set<String>> = favoritesState

    fun toggleFavorite(songId: String) {
        val current = favoritesState.value.toMutableSet()
        if (songId in current) current.remove(songId) else current.add(songId)
        favoritesState.value = current
    }

    fun clearFavorites() {
        favoritesState.value = emptySet()
    }

    fun getFavoriteSongs(): List<SongItem> {
        val favIds = favoritesState.value
        return allSongItemsState.value.filter { it.songId in favIds }
    }

    // Statistics — used for play-count sorting
    internal var playCounts: SongPlayCounts? = null

    fun setPlayCounts(counts: SongPlayCounts?) {
        playCounts = counts
        if (sortColumnState.value == Constants.SORT_PLAY_COUNT) refreshFilteredSongItems()
    }

    // Sort state — managed by ViewModel so it survives recomposition
    internal val sortColumnState = mutableStateOf("")

    val sortColumn: State<String> = sortColumnState

    internal val sortAscendingState = mutableStateOf(true)

    val sortAscending: State<Boolean> = sortAscendingState

    // Sorted + filtered song items — ready for the UI to display directly
    internal val filteredSongItemsState = mutableStateOf<List<SongItem>>(emptyList())

    val filteredSongItems: State<List<SongItem>> = filteredSongItemsState

    init {
        loadSongs()
    }

    fun updateSettings(newSettings: AppSettings) {
        appSettings = newSettings
        favoritesState.value = newSettings.songFavorites.toSet()
        loadSongs()
    }

    // ── Instance Link — remote song catalog ──────────────────────────────────
    // While active, the song list comes from the primary's catalog (metadata only — number/title/
    // tune/author) instead of local disk; full lyrics are fetched lazily per-song on selection
    // (see fetchRemoteDetailIfNeeded) rather than upfront, since a large library could mean
    // thousands of individual requests. Editing is disabled — see updateSong/createSong/deleteSong.
    internal var remoteModeActive = false

    internal var remoteFetchLyrics: (suspend (number: String, songbook: String) -> List<String>?)? = null

    // Bumped whenever a lazily-fetched remote song's lyrics arrive for the song still selected at
    // that time — the tab observes this to re-push to the presenter (look-ahead, current section)
    // once data actually exists, since the fetch in fetchRemoteDetailIfNeeded races the initial
    // selection and can't push synchronously.
    internal val remoteLyricsUpdatedState = mutableStateOf(0)

    val remoteLyricsUpdated: State<Int> = remoteLyricsUpdatedState

    /**
     * Called from the owning tab whenever Instance Link connects/disconnects or the catalog updates.
     *
     * [catalog] is the primary's songs, metadata only; [fetchLyrics] fetches one song's raw lyrics,
     * headers and lines in the format the local parser produces.
     */
    fun setInstanceLinkSource(
        active: Boolean,
        catalog: List<SongItem>?,
        fetchLyrics: (suspend (number: String, songbook: String) -> List<String>?)?
    ) {
        if (!active) {
            if (remoteModeActive) {
                remoteModeActive = false
                remoteFetchLyrics = null
                loadSongs()
            }
            return
        }
        remoteModeActive = true
        remoteFetchLyrics = fetchLyrics
        loadSongsJob?.cancel()
        songFolderWatcher.dispose()
        val items = catalog.orEmpty()
        applySongList(items)
        remoteSyncLog("songs_sync_result", mapOf("catalogPresent" to (catalog != null), "songCount" to items.size))
    }

    fun loadSongs() {
        if (remoteModeActive) return
        loadSongsJob?.cancel()
        loadSongsJob = viewModelScope.launch {
            isLoadingState.value = true
            val storageDir = appSettings.songSettings.storageDirectory
            try {
                // Phase 1: Try loading from cache for instant display
                if (storageDir.isNotEmpty()) {
                    val cached = withContext(ioDispatcher) {
                        SongFileParser.loadSongCache(storageDir)
                    }
                    if (cached != null && cached.isNotEmpty()) {
                        applySongList(cached)
                        isLoadingState.value = false
                    }
                }

                // Phase 2: Load from disk incrementally (only re-parse changed files)
                val result = withContext(ioDispatcher) {
                    val s = Songs()
                    var cachedSongsList: List<CachedSong> = emptyList()
                    if (storageDir.isNotEmpty()) {
                        val dir = File(storageDir)
                        if (dir.exists() && dir.isDirectory) {
                            val parser = SongFileParser()
                            val cacheMap = SongFileParser.loadCachedSongMap(storageDir)
                            cachedSongsList = parser.loadSongsFromDirectory(dir.absolutePath, cacheMap)
                            s.addSongs(cachedSongsList.map { it.song })
                        }
                    }
                    if (s.getSongCount() == 0) {
                        try {
                            s.loadFromSps(Constants.FALLBACK_SONG_RESOURCE)
                        } catch (_: Exception) {
                        }
                    }
                    Pair(s, cachedSongsList)
                }

                val songs = result.first
                val cachedSongsList = result.second
                val freshSongs = songs.getSongs()

                // Only update UI if data actually changed
                val currentSongs = songsDataState.value.getSongs()
                if (freshSongs != currentSongs) {
                    applySongList(freshSongs, songs)
                }

                // Save cache for next launch
                if (storageDir.isNotEmpty() && cachedSongsList.isNotEmpty()) {
                    withContext(ioDispatcher) {
                        SongFileParser.saveSongCache(storageDir, cachedSongsList)
                    }
                }

                // Start watching the song directory for changes
                if (enableFolderWatcher && storageDir.isNotEmpty()) {
                    songFolderWatcher.watchDirectory(File(storageDir))
                }
            } finally {
                isLoadingState.value = false
            }
        }
    }

    internal val lyricSearchText = IdentityHashMap<SongItem, String>()

    internal var lyricSearchTextFor: List<SongItem>? = null

    internal val lyricSections = IdentityHashMap<SongItem, MutableMap<Int, List<SearchableSection>>>()

    internal var lyricSectionsFor: List<SongItem>? = null

    fun dispose() {
        songFolderWatcher.dispose()
        viewModelScope.cancel()
    }
}
