package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.BibleBookNames
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import java.io.File

class BibleViewModel(
    private var appSettings: AppSettings
) {
    private val _primaryBible = mutableStateOf<Bible?>(null)
    val primaryBible: State<Bible?> = _primaryBible

    private val _secondaryBible = mutableStateOf<Bible?>(null)
    val secondaryBible: State<Bible?> = _secondaryBible

    private val _books = mutableStateOf<List<String>>(emptyList())
    val books: State<List<String>> = _books

    private val _selectedBookIndex = mutableStateOf(0)
    val selectedBookIndex: State<Int> = _selectedBookIndex

    private val _selectedChapter = mutableStateOf(1)
    val selectedChapter: State<Int> = _selectedChapter

    private val _selectedVerseIndex = mutableStateOf(0)
    val selectedVerseIndex: State<Int> = _selectedVerseIndex

    private val _verses = mutableStateOf<List<String>>(emptyList())
    val verses: State<List<String>> = _verses

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedScopeIndex = mutableStateOf(0)  // 0 = Entire Bible, 1 = Current Book
    val selectedScopeIndex: State<Int> = _selectedScopeIndex

    private val _selectedModeIndex = mutableStateOf(0)  // 0 = Contains, 1 = Exact Match
    val selectedModeIndex: State<Int> = _selectedModeIndex

    private val _bookSearchQuery = mutableStateOf("")
    val bookSearchQuery: State<String> = _bookSearchQuery

    private val _chapterSearchQuery = mutableStateOf("")
    val chapterSearchQuery: State<String> = _chapterSearchQuery

    private val _verseSearchQuery = mutableStateOf("")
    val verseSearchQuery: State<String> = _verseSearchQuery

    // Filtered list states — updated whenever underlying data or queries change
    private val _filteredBooks = mutableStateOf<List<String>>(emptyList())
    val filteredBooks: State<List<String>> = _filteredBooks

    private val _filteredChapters = mutableStateOf<List<String>>(emptyList())
    val filteredChapters: State<List<String>> = _filteredChapters

    private val _filteredVerses = mutableStateOf<List<String>>(emptyList())
    val filteredVerses: State<List<String>> = _filteredVerses

    private val _searchResults = mutableStateOf<List<org.churchpresenter.app.churchpresenter.data.BibleSearch>>(emptyList())
    val searchResults: State<List<org.churchpresenter.app.churchpresenter.data.BibleSearch>> = _searchResults

    private val _isSearchMode = mutableStateOf(false)
    val isSearchMode: State<Boolean> = _isSearchMode

    // Dynamic book name mapping for cross-language search
    private val _bookNameMapping = mutableStateOf<Map<String, String>>(emptyMap())
    val bookNameMapping: State<Map<String, String>> = _bookNameMapping

    private val _englishBookNames = mutableStateOf<List<String>>(emptyList())

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    init {
        // Initialize default search scope and mode indices
        _selectedScopeIndex.value = 0  // Entire Bible
        _selectedModeIndex.value = 0   // Contains

        // Load localized book name mapping and English book names
        viewModelScope.launch {
            try {
                _bookNameMapping.value = BibleBookNames.getBookNameMapping()
                _englishBookNames.value = BibleBookNames.getEnglishBookNames()
            } catch (e: Exception) {
                e.printStackTrace()
                _bookNameMapping.value = emptyMap()
                _englishBookNames.value = emptyList()
            }
        }

        loadBibles()
    }

    fun updateSettings(newSettings: AppSettings) {
        appSettings = newSettings
        loadBibles()
    }

    fun loadBibles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Parse Bible files on IO thread so the UI stays responsive
                val primary = withContext(Dispatchers.IO) {
                    if (appSettings.bibleSettings.primaryBible.isNotEmpty() &&
                        appSettings.bibleSettings.storageDirectory.isNotEmpty()
                    ) {
                        val bibleFile = File(
                            appSettings.bibleSettings.storageDirectory,
                            appSettings.bibleSettings.primaryBible
                        )
                        if (bibleFile.exists()) {
                            try {
                                Bible().apply { loadFromSpb(bibleFile.absolutePath) }
                            } catch (e: Exception) {
                                e.printStackTrace(); null
                            }
                        } else null
                    } else null
                }

                val secondary = withContext(Dispatchers.IO) {
                    if (appSettings.bibleSettings.secondaryBible.isNotEmpty() &&
                        appSettings.bibleSettings.storageDirectory.isNotEmpty()
                    ) {
                        val bibleFile = File(
                            appSettings.bibleSettings.storageDirectory,
                            appSettings.bibleSettings.secondaryBible
                        )
                        if (bibleFile.exists()) {
                            try {
                                Bible().apply { loadFromSpb(bibleFile.absolutePath) }
                            } catch (e: Exception) {
                                e.printStackTrace(); null
                            }
                        } else null
                    } else null
                }

                // Update state back on the Main thread
                _primaryBible.value = primary
                _secondaryBible.value = secondary

                primary?.let { bible ->
                    _books.value = bible.getBooks()
                    refreshFilteredLists()
                    if (bible.getBookCount() > 0) {
                        loadChapter(_selectedBookIndex.value, _selectedChapter.value)
                    }
                } ?: run {
                    _books.value = emptyList()
                    _verses.value = emptyList()
                    refreshFilteredLists()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadChapter(bookIndex: Int, chapter: Int) {
        _primaryBible.value?.let { bible ->
            val bookCount = bible.getBookCount()
            if (bookCount > 0) {
                val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
                _selectedBookIndex.value = clampedIndex
                _selectedChapter.value = chapter

                val bookId = clampedIndex + 1
                val chapterVerses = bible.getChapter(bookId, chapter)
                _verses.value = chapterVerses
                _selectedVerseIndex.value = 0
                refreshFilteredLists()
            }
        }
    }

    fun selectBook(bookIndex: Int) {
        _selectedBookIndex.value = bookIndex
        _selectedChapter.value = 1
        _selectedVerseIndex.value = 0
        loadChapter(bookIndex, 1)
    }

    fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
        _selectedVerseIndex.value = 0
        loadChapter(_selectedBookIndex.value, chapter)
    }

    fun selectVerse(verseIndex: Int) {
        // Bounds check to prevent index out of bounds
        if (verseIndex >= 0 && verseIndex < _verses.value.size) {
            _selectedVerseIndex.value = verseIndex
        } else {
            // Reset to 0 if index is invalid
            _selectedVerseIndex.value = 0
        }
    }

    fun selectVerseByDetails(bookName: String, chapter: Int, verseNumber: Int): Boolean {
        // Find the book by name
        val books = _books.value
        val bookIndex = books.indexOfFirst { it.equals(bookName, ignoreCase = true) }

        if (bookIndex >= 0) {
            // Select the book
            selectBook(bookIndex)

            // Select the chapter
            selectChapter(chapter)

            // Find and select the verse
            val verses = _verses.value
            val verseIndex = verses.indexOfFirst {
                it.startsWith("$verseNumber.")
            }

            if (verseIndex >= 0) {
                selectVerse(verseIndex)
                return true
            }
        }
        return false
    }

    fun getChaptersForCurrentBook(): List<String> {
        _primaryBible.value?.let { bible ->
            // getChapterCount expects 0-based book index
            val bookIndex = _selectedBookIndex.value
            val chapterCount = bible.getChapterCount(bookIndex)
            val count = if (chapterCount > 0) chapterCount else 1
            return (1..count).map { it.toString() }
        }
        return emptyList()
    }

    fun getFilteredBooks(): List<String> {
        val query = _bookSearchQuery.value

        if (query.isEmpty()) {
            return _books.value
        }

        // STEP 1: Try direct match (case-insensitive) against actual book names
        val directMatch = _books.value.filter {
            it.contains(query, ignoreCase = true)
        }

        if (directMatch.isNotEmpty()) {
            return directMatch
        }

        // STEP 2: Try cross-language search using English book names
        val isLatin = query.all { it.isLetter() && it.code < 128 }

        if (!isLatin) {
            return emptyList()
        }

        // Create a list of standard English book order (hardcoded for reliability)
        // This must match the order of books in Bible files (66 books in standard order)
        val standardEnglishBooks = listOf(
            "genesis", "exodus", "leviticus", "numbers", "deuteronomy", "joshua", "judges", "ruth",
            "1 samuel", "2 samuel", "1 kings", "2 kings", "1 chronicles", "2 chronicles",
            "ezra", "nehemiah", "esther", "job", "psalms", "proverbs", "ecclesiastes", "song of solomon",
            "isaiah", "jeremiah", "lamentations", "ezekiel", "daniel", "hosea", "joel", "amos",
            "obadiah", "jonah", "micah", "nahum", "habakkuk", "zephaniah", "haggai", "zechariah", "malachi",
            "matthew", "mark", "luke", "john", "acts", "romans",
            "1 corinthians", "2 corinthians", "galatians", "ephesians", "philippians", "colossians",
            "1 thessalonians", "2 thessalonians", "1 timothy", "2 timothy", "titus", "philemon",
            "hebrews", "james", "1 peter", "2 peter", "1 john", "2 john", "3 john", "jude", "revelation"
        )

        // Find indices of matching English books
        val matchingIndices = standardEnglishBooks.mapIndexedNotNull { index, englishName ->
            if (englishName.contains(query, ignoreCase = true)) {
                index
            } else {
                null
            }
        }

        if (matchingIndices.isEmpty()) {
            return emptyList()
        }

        // Get books from the actual Bible by these indices
        // This works because Bible files are in standard book order
        val mappedResults = matchingIndices.mapNotNull { index ->
            _books.value.getOrNull(index)
        }

        return mappedResults
    }

    fun getFilteredChapters(): List<String> {
        val chapters = getChaptersForCurrentBook()
        val query = _chapterSearchQuery.value
        if (query.isEmpty()) {
            return chapters
        }
        return chapters.filter { it.contains(query, ignoreCase = true) }
    }

    fun getFilteredVerses(): List<String> {
        val query = _verseSearchQuery.value
        if (query.isEmpty()) {
            return _verses.value
        }
        return _verses.value.filter { it.contains(query, ignoreCase = true) }
    }

    fun getSelectedVerses(): List<SelectedVerse> {
        val verseList = mutableListOf<SelectedVerse>()

        // Safety checks: ensure we have verses and valid index
        if (_verses.value.isEmpty()) {
            return verseList
        }

        // Clamp the index to valid range
        val safeIndex = _selectedVerseIndex.value.coerceIn(0, _verses.value.size - 1)

        // Update index if it was clamped
        if (safeIndex != _selectedVerseIndex.value) {
            _selectedVerseIndex.value = safeIndex
        }

        val verse = _verses.value[safeIndex]
        val verseNumber = verse.substringBefore(". ").toIntOrNull() ?: 1
        val bookId = _selectedBookIndex.value + 1

        // Add primary Bible verse
        _primaryBible.value?.getVerseDetails(bookId, _selectedChapter.value, verseNumber)?.let { (bookName, verseText, _) ->
            val abbreviation = _primaryBible.value?.getBibleAbbreviation() ?: ""
            verseList.add(
                SelectedVerse(
                    bibleAbbreviation = abbreviation,
                    bookName = bookName,
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumber,
                    verseText = verseText
                )
            )
        }

        // Add secondary Bible verse if available
        _secondaryBible.value?.getVerseDetails(bookId, _selectedChapter.value, verseNumber)?.let { (bookName, verseText, _) ->
            val abbreviation = _secondaryBible.value?.getBibleAbbreviation() ?: ""
            verseList.add(
                SelectedVerse(
                    bibleAbbreviation = abbreviation,
                    bookName = bookName,
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumber,
                    verseText = verseText
                )
            )
        }

        return verseList
    }

    fun navigatePreviousVerse(): Boolean {
        if (_verses.value.isNotEmpty() && _selectedVerseIndex.value > 0) {
            _selectedVerseIndex.value--
            return true
        }
        return false
    }

    fun navigateNextVerse(): Boolean {
        if (_verses.value.isNotEmpty() && _selectedVerseIndex.value < _verses.value.size - 1) {
            _selectedVerseIndex.value++
            return true
        }
        return false
    }

    fun navigatePreviousChapter(): Boolean {
        if (_selectedChapter.value > 1) {
            selectChapter(_selectedChapter.value - 1)
            return true
        }
        return false
    }

    fun navigateNextChapter(): Boolean {
        _primaryBible.value?.let { bible ->
            // getChapterCount expects 0-based book index
            val maxChapter = bible.getChapterCount(_selectedBookIndex.value)
            if (_selectedChapter.value < maxChapter) {
                selectChapter(_selectedChapter.value + 1)
                return true
            }
        }
        return false
    }

    private fun refreshFilteredLists() {
        _filteredBooks.value = getFilteredBooks()
        _filteredChapters.value = getFilteredChapters()
        _filteredVerses.value = getFilteredVerses()
    }

    /**
     * Adds the currently selected Bible verse to the given schedule.
     * Returns true if the verse was successfully added, false otherwise.
     */
    fun addCurrentVerseToSchedule(scheduleViewModel: ScheduleViewModel): Boolean {
        if (_verses.value.isEmpty()) return false
        val idx = _selectedVerseIndex.value
        if (idx < 0 || idx >= _verses.value.size) return false
        val selectedVerses = getSelectedVerses()
        if (selectedVerses.isEmpty()) return false
        val verse = selectedVerses[0]
        scheduleViewModel.addBibleVerse(
            bookName = verse.bookName,
            chapter = verse.chapter,
            verseNumber = verse.verseNumber,
            verseText = verse.verseText
        )
        return true
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedScopeIndex(index: Int) {
        _selectedScopeIndex.value = index
    }

    fun updateSelectedModeIndex(index: Int) {
        _selectedModeIndex.value = index
    }

    fun updateBookSearchQuery(query: String) {
        _bookSearchQuery.value = query
        refreshFilteredLists()
    }

    fun updateChapterSearchQuery(query: String) {
        _chapterSearchQuery.value = query
        refreshFilteredLists()
    }

    fun updateVerseSearchQuery(query: String) {
        _verseSearchQuery.value = query
        refreshFilteredLists()
    }

    fun performSearch() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            // Clear search results
            _searchResults.value = emptyList()
            _isSearchMode.value = false
            return
        }

        _primaryBible.value?.let { bible ->
            try {
                // Determine search mode based on selectedModeIndex
                // 0 = Contains, 1 = Exact Match
                val isExactMatch = _selectedModeIndex.value == 1

                // Build regex pattern
                val pattern = if (isExactMatch) {
                    "\\b${Regex.escape(query)}\\b"
                } else {
                    Regex.escape(query)
                }
                val searchRegex = Regex(pattern, RegexOption.IGNORE_CASE)

                // Determine scope based on selectedScopeIndex
                // 0 = Entire Bible, 1 = Current Book
                val results = when (_selectedScopeIndex.value) {
                    1 -> {
                        // Search in current book only
                        val bookId = _selectedBookIndex.value + 1
                        bible.searchBible(allWords = false, searchExp = searchRegex, book = bookId)
                    }
                    else -> {
                        // Search entire Bible
                        bible.searchBible(allWords = false, searchExp = searchRegex)
                    }
                }

                _searchResults.value = results
                _isSearchMode.value = true

            } catch (e: Exception) {
                e.printStackTrace()
                _searchResults.value = emptyList()
                _isSearchMode.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchMode.value = false
    }

    fun selectSearchResult(result: org.churchpresenter.app.churchpresenter.data.BibleSearch) {
        // Find the book index
        val bookName = result.book
        val bookIndex = _books.value.indexOf(bookName)
        if (bookIndex >= 0) {
            val chapter = result.chapter.toIntOrNull() ?: 1
            val verse = result.verse.toIntOrNull() ?: 1

            // Select the book and chapter
            selectBook(bookIndex)
            selectChapter(chapter)

            // Find and select the verse
            val verseIndex = _verses.value.indexOfFirst { it.startsWith("$verse.") }
            if (verseIndex >= 0) {
                selectVerse(verseIndex)
            }
        }
    }
}
