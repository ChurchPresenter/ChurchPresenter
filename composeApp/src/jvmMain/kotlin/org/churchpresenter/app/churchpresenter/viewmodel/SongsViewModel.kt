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
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
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
    private val _allSongs = mutableStateOf<List<String>>(emptyList())
    val allSongs: State<List<String>> = _allSongs

    private val _songbooks = mutableStateOf<List<String>>(emptyList())
    val songbooks: State<List<String>> = _songbooks


    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedSongbook = mutableStateOf("")
    val selectedSongbook: State<String> = _selectedSongbook


    private val _filterType = mutableStateOf(Constants.CONTAINS)
    val filterType: State<String> = _filterType

    private val _selectedSongIndex = mutableStateOf(2)
    val selectedSongIndex: State<Int> = _selectedSongIndex

    private val _selectedSectionIndex = mutableStateOf(-1)
    val selectedSectionIndex: State<Int> = _selectedSectionIndex

    private val _filteredSongs = mutableStateOf<List<String>>(emptyList())
    val filteredSongs: State<List<String>> = _filteredSongs

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
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val songs = withContext(Dispatchers.IO) {
                    val s = Songs()
                    if (appSettings.songSettings.storageDirectory.isNotEmpty()) {
                        val dir = File(appSettings.songSettings.storageDirectory)
                        if (dir.exists() && dir.isDirectory) {
                            val spsFiles: Array<File> = dir.listFiles { file ->
                                file.extension.lowercase() == Constants.EXTENSION_SPS
                            } ?: emptyArray()

                            spsFiles.sortedBy { it.name }.forEach { file ->
                                try {
                                    s.loadFromSpsAppend(file.absolutePath)
                                } catch (_: Exception) {
                                }
                            }
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

                // Update state on Main thread
                _songsData.value = songs

                val uniqueSongbooks = songs.getSongs()
                    .map { it.songbook }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                _songbooks.value = uniqueSongbooks

                _allSongs.value = songs.getSongs().map { "${it.number}. ${it.title}" }
                _filteredSongs.value = _allSongs.value
                refreshFilteredSongItems()

                // Notify listener (e.g. companion server) with full song list
                onSongsLoaded?.invoke(songs.getSongs())
            } finally {
                _isLoading.value = false
            }
        }
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
        _selectedSectionIndex.value = -1
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
    }

    fun getSelectedSong(): LyricSection? {
        val items = _filteredSongItems.value
        val idx = _selectedSongIndex.value
        if (items.isEmpty() || idx < 0 || idx >= items.size) return null
        val song = items[idx]
        return LyricSection(
            title = song.title,
            songNumber = song.number.toIntOrNull() ?: 0,
            lines = song.lyrics,
            type = Constants.SECTION_TYPE_SONG
        )
    }

    fun getLyricSections(): List<LyricSection> {
        val items = _filteredSongItems.value
        val idx = _selectedSongIndex.value
        if (items.isEmpty() || idx < 0 || idx >= items.size) return emptyList()
        val song = items[idx]

        // Split lyrics into sections (verses and choruses)
        val sections = mutableListOf<LyricSection>()
        val currentSection = mutableListOf<String>()
        var sectionType = Constants.SECTION_TYPE_VERSE
        var sectionNumber = 0

        song.lyrics.forEach { line ->
            when {
                line.contains(Constants.VERSE_RUS, ignoreCase = true) || line.contains(
                    Constants.VERSE,
                    ignoreCase = true
                ) -> {
                    if (currentSection.isNotEmpty()) {
                        sections.add(
                            LyricSection(
                                title = song.title,
                                songNumber = song.number.toIntOrNull() ?: 0,
                                lines = currentSection.toList(),
                                type = sectionType
                            )
                        )
                        currentSection.clear()
                    }
                    sectionType = Constants.SECTION_TYPE_VERSE
                    sectionNumber++
                    currentSection.add(line)
                }

                line.contains(Constants.CHORUS_RUS, ignoreCase = true) || line.contains(
                    Constants.CHORUS,
                    ignoreCase = true
                ) -> {
                    if (currentSection.isNotEmpty()) {
                        sections.add(
                            LyricSection(
                                title = song.title,
                                songNumber = song.number.toIntOrNull() ?: 0,
                                lines = currentSection.toList(),
                                type = sectionType
                            )
                        )
                        currentSection.clear()
                    }
                    sectionType = Constants.SECTION_TYPE_CHORUS
                    currentSection.add(line)
                }

                else -> {
                    currentSection.add(line)
                }
            }
        }

        // Add the last section
        if (currentSection.isNotEmpty()) {
            sections.add(
                LyricSection(
                    title = song.title,
                    songNumber = song.number.toIntOrNull() ?: 0,
                    lines = currentSection.toList(),
                    type = sectionType
                )
            )
        }

        // Mark the very last section so the presenter can show end-of-song indicator
        return sections.mapIndexed { index, section ->
            if (index == sections.lastIndex) section.copy(isLastSection = true) else section
        }
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
        if (_selectedSongIndex.value < _filteredSongs.value.size - 1) {
            _selectedSongIndex.value++
            _selectedSectionIndex.value = -1
            return true
        }
        return false
    }

    fun navigatePreviousSection(): Boolean {
        if (_selectedSectionIndex.value > 0) {
            _selectedSectionIndex.value--
            return true
        }
        return false
    }

    fun navigateNextSection(): Boolean {
        val sections = getLyricSections()
        if (_selectedSectionIndex.value < sections.size - 1) {
            _selectedSectionIndex.value++
            return true
        }
        return false
    }

    private fun applyFilters() {
        var filtered = _allSongs.value

        // Filter by songbook - only apply if a real songbook is selected (not "All Song Books")
        if (_selectedSongbook.value.isNotEmpty() && _songbooks.value.contains(_selectedSongbook.value)) {
            filtered = filtered.filter { songText ->
                val songNumber = songText.substringBefore(".").trim()
                val song = _songsData.value.getSongs().find { it.number == songNumber }
                song?.songbook == _selectedSongbook.value
            }
        }

        // Filter by search query
        if (_searchQuery.value.isNotEmpty()) {
            filtered = when (_filterType.value) {
                Constants.CONTAINS -> filtered.filter { it.contains(_searchQuery.value, ignoreCase = true) }
                Constants.STARTS_WITH -> filtered.filter {
                    it.substringAfter(". ").startsWith(_searchQuery.value, ignoreCase = true)
                }

                Constants.EXACT_MATCH -> filtered.filter { it.endsWith(_searchQuery.value, ignoreCase = true) }
                else -> filtered
            }
        }

        _filteredSongs.value = filtered

        // Adjust selected index if needed
        if (_selectedSongIndex.value >= filtered.size && filtered.isNotEmpty()) {
            _selectedSongIndex.value = 0
        }

        refreshFilteredSongItems()
    }

    private fun refreshFilteredSongItems() {
        val songsMap = _songsData.value.getSongs().associateBy { "${it.number}. ${it.title}" }
        var items = _filteredSongs.value.mapNotNull { songsMap[it] }

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

    fun updateSong(
        oldSong: SongItem,
        newSong: SongItem
    ): Boolean {
        try {
            // Update in memory
            _songsData.value.updateSong(oldSong, newSong)

            // Save to file (pass BOTH old and new song)
            val saved = _songsData.value.saveSongToFile(oldSong, newSong, appSettings.songSettings.storageDirectory)

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
        viewModelScope.cancel()
    }
}
