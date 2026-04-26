package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.SongFileParser
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.isChorusHeader
import org.churchpresenter.app.churchpresenter.utils.isHeaderLine
import java.io.File

class SongsViewModel(
    private var appSettings: AppSettings,
    private val onSongsLoaded: ((List<SongItem>) -> Unit)? = null
) {
    private val _songsData = mutableStateOf(Songs())
    val songsData: State<Songs> = _songsData

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadSongsJob: kotlinx.coroutines.Job? = null
    private val songFolderWatcher = SongFolderWatcher(viewModelScope) { loadSongs() }
    private val _allSongItems = mutableStateOf<List<SongItem>>(emptyList())

    private val _songbooks = mutableStateOf<List<String>>(emptyList())
    val songbooks: State<List<String>> = _songbooks


    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedSongbook = mutableStateOf("")
    val selectedSongbook: State<String> = _selectedSongbook


    private val _filterType = mutableStateOf(Constants.CONTAINS)
    val filterType: State<String> = _filterType

    private val _selectedSongIndex = mutableStateOf(0)
    val selectedSongIndex: State<Int> = _selectedSongIndex

    private val _selectedSectionIndex = mutableStateOf(-1)
    val selectedSectionIndex: State<Int> = _selectedSectionIndex

    private val _selectedLineIndex = mutableStateOf(-1)
    val selectedLineIndex: State<Int> = _selectedLineIndex

    private val _filteredSongsList = mutableStateOf<List<SongItem>>(emptyList())

    // Sort state — managed by ViewModel so it survives recomposition
    private val _sortColumn = mutableStateOf("")
    val sortColumn: State<String> = _sortColumn

    private val _sortAscending = mutableStateOf(true)
    val sortAscending: State<Boolean> = _sortAscending

    // Sorted + filtered song items — ready for the UI to display directly
    private val _filteredSongItems = mutableStateOf<List<SongItem>>(emptyList())
    val filteredSongItems: State<List<SongItem>> = _filteredSongItems

    init {
        loadSongs()
    }

    fun updateSettings(newSettings: AppSettings) {
        appSettings = newSettings
        loadSongs()
    }

    fun loadSongs() {
        loadSongsJob?.cancel()
        loadSongsJob = viewModelScope.launch {
            _isLoading.value = true
            val storageDir = appSettings.songSettings.storageDirectory
            try {
                // Phase 1: Try loading from cache for instant display
                if (storageDir.isNotEmpty()) {
                    val cached = withContext(Dispatchers.IO) {
                        SongFileParser.loadSongCache(storageDir)
                    }
                    if (cached != null && cached.isNotEmpty()) {
                        applySongList(cached)
                        _isLoading.value = false
                    }
                }

                // Phase 2: Load from disk and update if changed
                val songs = withContext(Dispatchers.IO) {
                    val s = Songs()
                    if (storageDir.isNotEmpty()) {
                        val dir = File(storageDir)
                        if (dir.exists() && dir.isDirectory) {
                            val parser = SongFileParser()
                            val songFileSongs = parser.loadSongsFromDirectory(dir.absolutePath)
                            s.addSongs(songFileSongs)
                        }
                    }
                    if (s.getSongCount() == 0) {
                        try {
                            s.loadFromSps(Constants.FALLBACK_SONG_RESOURCE)
                        } catch (_: Exception) {
                        }
                    }
                    s
                }

                val freshSongs = songs.getSongs()

                // Only update UI if data actually changed
                val currentSongs = _songsData.value?.getSongs() ?: emptyList()
                if (freshSongs != currentSongs) {
                    applySongList(freshSongs, songs)
                }

                // Save cache for next launch
                if (storageDir.isNotEmpty() && freshSongs.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        SongFileParser.saveSongCache(storageDir, freshSongs)
                    }
                }

                // Start watching the song directory for changes
                if (storageDir.isNotEmpty()) {
                    songFolderWatcher.watchDirectory(File(storageDir))
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun applySongList(songItems: List<SongItem>, songsObj: Songs? = null) {
        val songs = songsObj ?: Songs().also { it.addSongs(songItems) }
        _songsData.value = songs

        val uniqueSongbooks = songItems
            .map { it.songbook }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        _songbooks.value = uniqueSongbooks

        _allSongItems.value = songItems
        _filteredSongsList.value = songItems
        refreshFilteredSongItems()

        onSongsLoaded?.invoke(songItems)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun updateSelectedSongbook(songbook: String) {
        _selectedSongbook.value = songbook
        applyFilters()
    }

    fun updateFilterType(filterType: String) {
        _filterType.value = filterType
        applyFilters()
    }

    fun selectSong(index: Int) {
        _selectedSongIndex.value = index
        _selectedSectionIndex.value = 0
        _selectedLineIndex.value = 0
    }

    fun selectSongByDetails(songNumber: Int, title: String, songbook: String, songId: String = ""): Boolean {
        val allSongs = _songsData.value.getSongs()

        // 1. Primary: stable songId "songbook::number" — unambiguous across songbooks
        val songData = allSongs.find { songId.isNotBlank() && it.songId == songId }
        // 2. Fallback: songbook + number (old saved schedules without songId)
            ?: allSongs.find {
                it.songbook.equals(songbook, ignoreCase = true) && it.number == songNumber.toString()
            }
        // 3. Last resort: title only
            ?: allSongs.find { it.title.equals(title, ignoreCase = true) }

        if (songData == null) return false

        // Find index in _filteredSongItems (what the UI renders) by songId — never change filter
        var idx = _filteredSongItems.value.indexOfFirst { it.songId == songData.songId }

        if (idx < 0) {
            // Song is outside current filter — bypass temporarily, restore immediately
            val savedSongbook = _selectedSongbook.value
            val savedSearch   = _searchQuery.value
            _selectedSongbook.value = ""
            _searchQuery.value = ""
            applyFilters()
            idx = _filteredSongItems.value.indexOfFirst { it.songId == songData.songId }
            _selectedSongbook.value = savedSongbook
            _searchQuery.value = savedSearch
            applyFilters()
            if (idx < 0) return false
        }

        _selectedSongIndex.value = idx
        _selectedSectionIndex.value = 0
        return true
    }

    fun selectSection(index: Int) {
        _selectedSectionIndex.value = index
        _selectedLineIndex.value = 0
    }

    fun setLineIndex(index: Int) {
        _selectedLineIndex.value = index
    }

    fun getSelectedSong(): LyricSection? {
        val items = _filteredSongItems.value
        val idx = _selectedSongIndex.value
        if (items.isEmpty() || idx < 0 || idx >= items.size) return null
        val song = items[idx]
        return LyricSection(
            title = song.title,
            secondaryTitle = song.secondaryTitle,
            songNumber = song.number.toIntOrNull() ?: 0,
            lines = song.lyrics,
            secondaryLines = song.secondaryLyrics,
            type = Constants.SECTION_TYPE_SONG
        )
    }

    fun getLyricSections(): List<LyricSection> {
        val items = _filteredSongItems.value
        val idx = _selectedSongIndex.value
        if (items.isEmpty() || idx < 0 || idx >= items.size) return emptyList()
        val song = items[idx]

        // Split primary lyrics into sections
        val primarySections = splitLyricsIntoSections(song.lyrics, song.title, song.number)
        // Split secondary lyrics into sections (matched by order)
        val secondarySections = if (song.secondaryLyrics.isNotEmpty()) {
            splitLyricsIntoSections(song.secondaryLyrics, song.secondaryTitle, song.number)
        } else {
            emptyList()
        }

        // Merge primary and secondary sections by index
        val sections = primarySections.mapIndexed { index, section ->
            val secondary = secondarySections.getOrNull(index)
            section.copy(
                secondaryTitle = song.secondaryTitle,
                secondaryLines = secondary?.lines ?: emptyList()
            )
        }

        // Mark the very last section so the presenter can show end-of-song indicator
        return sections.mapIndexed { index, section ->
            if (index == sections.lastIndex) section.copy(isLastSection = true) else section
        }
    }

    private fun splitLyricsIntoSections(lyrics: List<String>, title: String, number: String): List<LyricSection> {
        // First pass: parse raw sections
        val rawSections = mutableListOf<LyricSection>()
        val currentLines = mutableListOf<String>()
        var currentHeader: String? = null
        var sectionType = Constants.SECTION_TYPE_VERSE

        fun flushSection() {
            if (currentLines.isNotEmpty() || currentHeader != null) {
                rawSections.add(
                    LyricSection(
                        header = currentHeader,
                        title = title,
                        songNumber = number.toIntOrNull() ?: 0,
                        lines = currentLines.toList(),
                        type = sectionType
                    )
                )
                currentLines.clear()
            }
        }

        lyrics.forEach { line ->
            if (isHeaderLine(line)) {
                flushSection()
                currentHeader = line
                sectionType = if (isChorusHeader(line)) Constants.SECTION_TYPE_CHORUS else Constants.SECTION_TYPE_VERSE
            } else if (line.isNotBlank()) {
                currentLines.add(line)
            }
        }

        flushSection()

        // Second pass: auto-repeat chorus after each verse
        val chorusSection = rawSections.firstOrNull { it.type == Constants.SECTION_TYPE_CHORUS }
        if (chorusSection == null) return rawSections

        val result = mutableListOf<LyricSection>()
        for (section in rawSections) {
            if (section.type == Constants.SECTION_TYPE_CHORUS) continue // skip original, will be inserted after verses
            result.add(section)
            if (section.type == Constants.SECTION_TYPE_VERSE) {
                result.add(chorusSection)
            }
        }

        return result
    }

    fun getSelectedLyricSection(): LyricSection? {
        val sections = getLyricSections()
        if (_selectedSectionIndex.value < 0 || _selectedSectionIndex.value >= sections.size) {
            return getSelectedSong()
        }
        return sections[_selectedSectionIndex.value]
    }

    fun navigatePreviousSong(): Boolean {
        if (_selectedSongIndex.value > 0) {
            _selectedSongIndex.value--
            _selectedSectionIndex.value = -1
            return true
        }
        return false
    }

    fun navigateNextSong(): Boolean {
        if (_selectedSongIndex.value < _filteredSongItems.value.size - 1) {
            _selectedSongIndex.value++
            _selectedSectionIndex.value = -1
            return true
        }
        return false
    }

    fun navigatePreviousSection(): Boolean {
        _selectedLineIndex.value = 0
        val sections = getLyricSections()
        var prevIdx = _selectedSectionIndex.value - 1
        while (prevIdx >= 0) {
            if (sections[prevIdx].lines.isNotEmpty()) {
                _selectedSectionIndex.value = prevIdx
                return true
            }
            prevIdx--
        }
        return false
    }

    fun navigateNextSection(): Boolean {
        _selectedLineIndex.value = 0
        val sections = getLyricSections()
        var nextIdx = _selectedSectionIndex.value + 1
        while (nextIdx < sections.size) {
            if (sections[nextIdx].lines.isNotEmpty()) {
                _selectedSectionIndex.value = nextIdx
                return true
            }
            nextIdx++
        }
        return false
    }

    fun navigateNextLine(): Boolean {
        val section = getSelectedLyricSection() ?: return false
        val displayLines = section.lines
        val currentLine = _selectedLineIndex.value
        if (currentLine < displayLines.size - 1) {
            _selectedLineIndex.value = currentLine + 1
            return true
        }
        // Move to next section with content lines (skip empty sections)
        val sections = getLyricSections()
        var nextIdx = _selectedSectionIndex.value + 1
        while (nextIdx < sections.size) {
            if (sections[nextIdx].lines.isNotEmpty()) {
                _selectedSectionIndex.value = nextIdx
                _selectedLineIndex.value = 0
                return true
            }
            nextIdx++
        }
        return false
    }

    fun navigatePreviousLine(): Boolean {
        val currentLine = _selectedLineIndex.value
        if (currentLine > 0) {
            _selectedLineIndex.value = currentLine - 1
            return true
        }
        // Move to previous section with content lines (skip empty sections)
        val sections = getLyricSections()
        var prevIdx = _selectedSectionIndex.value - 1
        while (prevIdx >= 0) {
            if (sections[prevIdx].lines.isNotEmpty()) {
                _selectedSectionIndex.value = prevIdx
                _selectedLineIndex.value = (sections[prevIdx].lines.size - 1).coerceAtLeast(0)
                return true
            }
            prevIdx--
        }
        return false
    }

    private fun applyFilters() {
        var filtered = _allSongItems.value

        // Filter by songbook - only apply if a real songbook is selected (not "All Song Books")
        if (_selectedSongbook.value.isNotEmpty() && _songbooks.value.contains(_selectedSongbook.value)) {
            filtered = filtered.filter { it.songbook == _selectedSongbook.value }
        }

        // Filter by search query
        if (_searchQuery.value.isNotEmpty()) {
            filtered = when (_filterType.value) {
                Constants.CONTAINS -> filtered.filter {
                    "${it.number}. ${it.title}".contains(_searchQuery.value, ignoreCase = true)
                }
                Constants.STARTS_WITH -> filtered.filter {
                    it.title.startsWith(_searchQuery.value, ignoreCase = true) ||
                    it.number.startsWith(_searchQuery.value, ignoreCase = true)
                }
                Constants.EXACT_MATCH -> filtered.filter {
                    it.title.equals(_searchQuery.value, ignoreCase = true) ||
                    it.number.equals(_searchQuery.value, ignoreCase = true)
                }
                else -> filtered
            }
        }

        _filteredSongsList.value = filtered

        // Adjust selected index if needed
        if (_selectedSongIndex.value >= filtered.size && filtered.isNotEmpty()) {
            _selectedSongIndex.value = 0
        }

        refreshFilteredSongItems()
    }

    private fun refreshFilteredSongItems() {
        var items = _filteredSongsList.value

        if (_sortColumn.value.isNotEmpty()) {
            items = when (_sortColumn.value) {
                Constants.SORT_NUMBER -> if (_sortAscending.value)
                    items.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
                else
                    items.sortedByDescending { it.number.toIntOrNull() ?: Int.MIN_VALUE }

                Constants.SORT_TITLE -> if (_sortAscending.value)
                    items.sortedBy { it.title.lowercase() }
                else
                    items.sortedByDescending { it.title.lowercase() }

                Constants.SORT_SONGBOOK -> if (_sortAscending.value)
                    items.sortedBy { it.songbook.lowercase() }
                else
                    items.sortedByDescending { it.songbook.lowercase() }

                Constants.SORT_TUNE -> if (_sortAscending.value)
                    items.sortedBy { it.tune.lowercase() }
                else
                    items.sortedByDescending { it.tune.lowercase() }

                else -> items
            }
        }

        _filteredSongItems.value = items
    }

    private fun buildSongFileName(number: String, title: String): String {
        return if (number.isNotBlank()) {
            "${number.padStart(4, '0')} - $title.song"
        } else {
            "$title.song"
        }
    }

    fun updateSong(
        oldSong: SongItem,
        newSong: SongItem
    ): Boolean {
        try {
            val storageDir = appSettings.songSettings.storageDirectory
            var songToSave = newSong

            // Handle .song file moves/renames when songbook, title, or number change
            if (oldSong.sourceFile.isNotEmpty() && storageDir.isNotEmpty()) {
                val oldFile = File(oldSong.sourceFile)
                if (oldFile.exists()) {
                    val songbookChanged = oldSong.songbook != newSong.songbook
                    val titleChanged = oldSong.title != newSong.title
                    val numberChanged = oldSong.number != newSong.number

                    if (songbookChanged || titleChanged || numberChanged) {
                        val targetDir = if (songbookChanged) {
                            File(storageDir, newSong.songbook)
                        } else {
                            oldFile.parentFile
                        }
                        if (!targetDir.exists()) targetDir.mkdirs()

                        val newFileName = if (titleChanged || numberChanged) {
                            buildSongFileName(newSong.number, newSong.title)
                        } else {
                            oldFile.name
                        }

                        val newFile = File(targetDir, newFileName)
                        oldFile.copyTo(newFile, overwrite = true)
                        if (oldFile.absolutePath != newFile.absolutePath) {
                            oldFile.delete()
                        }
                        // Clean up empty old directory
                        if (songbookChanged) {
                            val oldDir = oldFile.parentFile
                            if (oldDir != null && oldDir.isDirectory && oldDir.listFiles()?.isEmpty() == true) {
                                oldDir.delete()
                            }
                        }
                        songToSave = newSong.copy(sourceFile = newFile.absolutePath)
                    }
                }
            }

            // Update in memory
            _songsData.value.updateSong(oldSong, songToSave)

            // Save to file (pass BOTH old and new song)
            val saved = _songsData.value.saveSongToFile(oldSong, songToSave, storageDir)

            if (saved) {
                // Reload songs to reflect changes
                loadSongs()
                // Re-apply current filters to update the filtered list
                applyFilters()
                return true
            }

            return false
        } catch (_: Exception) {
            return false
        }
    }

    fun createSong(song: SongItem): Boolean {
        try {
            val storageDir = appSettings.songSettings.storageDirectory
            if (storageDir.isEmpty() || song.songbook.isBlank()) return false

            val targetDir = File(storageDir, song.songbook)
            if (!targetDir.exists()) targetDir.mkdirs()

            val fileName = if (song.number.isNotBlank()) {
                "${song.number.padStart(4, '0')} - ${song.title}.song"
            } else {
                "${song.title}.song"
            }
            val filePath = File(targetDir, fileName).absolutePath

            val parser = SongFileParser()
            parser.writeSongFile(song.copy(sourceFile = filePath), filePath)

            loadSongs()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun updateSort(column: String) {
        if (_sortColumn.value == column) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortColumn.value = column
            _sortAscending.value = true
        }
        refreshFilteredSongItems()
    }

    fun getSortIndicator(column: String): String {
        return if (_sortColumn.value == column) {
            if (_sortAscending.value) " ↑" else " ↓"
        } else ""
    }

    /**
     * Adds the currently selected song to the schedule.
     * Returns true if successfully added, false otherwise.
     */
    fun addCurrentSongToSchedule(scheduleViewModel: ScheduleViewModel): Boolean {
        val items = _filteredSongItems.value
        val idx = _selectedSongIndex.value
        if (idx < 0 || idx >= items.size) return false
        val song = items[idx]
        scheduleViewModel.addSong(
            songNumber = song.number.toIntOrNull() ?: 0,
            title = song.title,
            songbook = song.songbook
        )
        return true
    }

    fun dispose() {
        songFolderWatcher.dispose()
        viewModelScope.cancel()
    }
}
