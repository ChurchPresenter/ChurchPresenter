package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.*
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.models.LyricSection
import java.io.File

class SongsViewModel(
    private val appSettings: AppSettings
) {
    private val _songsData = mutableStateOf(Songs())
    val songsData: State<Songs> = _songsData

    private val _allSongs = mutableStateOf<List<String>>(emptyList())
    val allSongs: State<List<String>> = _allSongs

    private val _songbooks = mutableStateOf<List<String>>(emptyList())
    val songbooks: State<List<String>> = _songbooks


    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedSongbook = mutableStateOf("")
    val selectedSongbook: State<String> = _selectedSongbook


    private val _filterType = mutableStateOf("Contains")
    val filterType: State<String> = _filterType

    private val _selectedSongIndex = mutableStateOf(2)
    val selectedSongIndex: State<Int> = _selectedSongIndex

    private val _selectedSectionIndex = mutableStateOf(-1)
    val selectedSectionIndex: State<Int> = _selectedSectionIndex

    private val _filteredSongs = mutableStateOf<List<String>>(emptyList())
    val filteredSongs: State<List<String>> = _filteredSongs

    init {
        loadSongs()
    }

    fun loadSongs() {
        val songs = Songs()

        if (appSettings.songSettings.storageDirectory.isNotEmpty()) {
            val dir = File(appSettings.songSettings.storageDirectory)
            if (dir.exists() && dir.isDirectory) {
                // Get all .sps files from the directory
                val spsFiles: Array<File> = dir.listFiles { file ->
                    file.extension.lowercase() == "sps"
                } ?: emptyArray()

                // Load each .sps file (sorted alphabetically) - using append to combine all databases
                spsFiles.sortedBy { it.name }.forEach { file ->
                    try {
                        println("Loading song database: ${file.name}")
                        songs.loadFromSpsAppend(file.absolutePath)
                        println("Total songs loaded so far: ${songs.getSongCount()}")
                    } catch (e: Exception) {
                        println("Error loading ${file.name}: ${e.message}")
                    }
                }
            }
        }

        // If no files were loaded, try to load the bundled resource as fallback
        if (songs.getSongCount() == 0) {
            try {
                println("No song files found in directory, loading bundled resource")
                songs.loadFromSps("pv3300.sps")
            } catch (e: Exception) {
                println("Error loading bundled resource: ${e.message}")
            }
        }

        println("Total songs in database: ${songs.getSongCount()}")
        _songsData.value = songs

        // Extract unique songbook names
        val uniqueSongbooks = songs.getSongs()
            .map { it.songbook }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        _songbooks.value = uniqueSongbooks

        // Initialize all songs
        _allSongs.value = songs.getSongs().map { "${it.number}. ${it.title}" }
        _filteredSongs.value = _allSongs.value
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

    fun selectSection(index: Int) {
        _selectedSectionIndex.value = index
    }

    fun getSelectedSong(): LyricSection? {
        if (_filteredSongs.value.isEmpty() || _selectedSongIndex.value >= _filteredSongs.value.size) {
            return null
        }

        val songText = _filteredSongs.value[_selectedSongIndex.value]
        val songNumber = songText.substringBefore(".").trim()

        val song = _songsData.value.getSongs().find { it.number == songNumber } ?: return null

        return LyricSection(
            title = song.title,
            songNumber = song.number.toIntOrNull() ?: 0,
            lines = song.lyrics,
            type = "song"
        )
    }

    fun getLyricSections(): List<LyricSection> {
        if (_filteredSongs.value.isEmpty() || _selectedSongIndex.value >= _filteredSongs.value.size) {
            return emptyList()
        }

        val songText = _filteredSongs.value[_selectedSongIndex.value]
        val songNumber = songText.substringBefore(".").trim()

        val song = _songsData.value.getSongs().find { it.number == songNumber } ?: return emptyList()

        // Split lyrics into sections (verses and choruses)
        val sections = mutableListOf<LyricSection>()
        val currentSection = mutableListOf<String>()
        var sectionType = "verse"
        var sectionNumber = 0

        song.lyrics.forEach { line ->
            when {
                line.contains("Куплет", ignoreCase = true) || line.contains("verse", ignoreCase = true) -> {
                    if (currentSection.isNotEmpty()) {
                        sections.add(LyricSection(
                            title = song.title,
                            songNumber = song.number.toIntOrNull() ?: 0,
                            lines = currentSection.toList(),
                            type = sectionType
                        ))
                        currentSection.clear()
                    }
                    sectionType = "verse"
                    sectionNumber++
                    currentSection.add(line)
                }
                line.contains("Припев", ignoreCase = true) || line.contains("chorus", ignoreCase = true) -> {
                    if (currentSection.isNotEmpty()) {
                        sections.add(LyricSection(
                            title = song.title,
                            songNumber = song.number.toIntOrNull() ?: 0,
                            lines = currentSection.toList(),
                            type = sectionType
                        ))
                        currentSection.clear()
                    }
                    sectionType = "chorus"
                    currentSection.add(line)
                }
                else -> {
                    currentSection.add(line)
                }
            }
        }

        // Add the last section
        if (currentSection.isNotEmpty()) {
            sections.add(LyricSection(
                title = song.title,
                songNumber = song.number.toIntOrNull() ?: 0,
                lines = currentSection.toList(),
                type = sectionType
            ))
        }

        return sections
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
        val sections = getLyricSections()
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

        // Filter by songbook
        if (_selectedSongbook.value.isNotEmpty() && _selectedSongbook.value != "All Song Books") {
            filtered = filtered.filter { songText ->
                val songNumber = songText.substringBefore(".").trim()
                val song = _songsData.value.getSongs().find { it.number == songNumber }
                song?.songbook == _selectedSongbook.value
            }
        }


        // Filter by search query
        if (_searchQuery.value.isNotEmpty()) {
            filtered = when (_filterType.value) {
                "Contains" -> filtered.filter { it.contains(_searchQuery.value, ignoreCase = true) }
                "Starts with" -> filtered.filter { it.substringAfter(". ").startsWith(_searchQuery.value, ignoreCase = true) }
                "Ends with" -> filtered.filter { it.endsWith(_searchQuery.value, ignoreCase = true) }
                else -> filtered
            }
        }

        _filteredSongs.value = filtered

        // Adjust selected index if needed
        if (_selectedSongIndex.value >= filtered.size && filtered.isNotEmpty()) {
            _selectedSongIndex.value = 0
        }
    }
}
