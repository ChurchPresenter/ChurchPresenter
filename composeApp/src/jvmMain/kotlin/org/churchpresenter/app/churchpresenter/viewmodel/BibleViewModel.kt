package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.*
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import java.io.File

class BibleViewModel(
    private val fallbackBible: Bible,
    private val appSettings: AppSettings
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

    private val _selectedScope = mutableStateOf("")
    val selectedScope: State<String> = _selectedScope

    private val _selectedMode = mutableStateOf("")
    val selectedMode: State<String> = _selectedMode

    private val _bookSearchQuery = mutableStateOf("")
    val bookSearchQuery: State<String> = _bookSearchQuery

    private val _chapterSearchQuery = mutableStateOf("")
    val chapterSearchQuery: State<String> = _chapterSearchQuery

    private val _verseSearchQuery = mutableStateOf("")
    val verseSearchQuery: State<String> = _verseSearchQuery

    private val _searchResults = mutableStateOf<List<org.churchpresenter.app.churchpresenter.data.BibleSearch>>(emptyList())
    val searchResults: State<List<org.churchpresenter.app.churchpresenter.data.BibleSearch>> = _searchResults

    private val _isSearchMode = mutableStateOf(false)
    val isSearchMode: State<Boolean> = _isSearchMode

    init {
        // Initialize default search scope and mode
        _selectedScope.value = "Entire Bible"
        _selectedMode.value = "Contains"

        loadBibles()
    }

    fun loadBibles() {
        // Load primary Bible from settings
        _primaryBible.value = if (appSettings.bibleSettings.primaryBible.isNotEmpty() &&
            appSettings.bibleSettings.storageDirectory.isNotEmpty()) {
            val bibleFile = File(appSettings.bibleSettings.storageDirectory, appSettings.bibleSettings.primaryBible)
            if (bibleFile.exists()) {
                try {
                    Bible().apply {
                        loadFromSpb(bibleFile.absolutePath)
                    }
                } catch (e: Exception) {
                    println("Error loading primary Bible from settings: ${e.message}")
                    e.printStackTrace()
                    fallbackBible
                }
            } else {
                println("Primary Bible file not found: ${bibleFile.absolutePath}, using fallback")
                fallbackBible
            }
        } else {
            println("No primary Bible configured in settings, using fallback")
            fallbackBible
        }

        // Load secondary Bible from settings
        _secondaryBible.value = if (appSettings.bibleSettings.secondaryBible.isNotEmpty() &&
            appSettings.bibleSettings.storageDirectory.isNotEmpty()) {
            val bibleFile = File(appSettings.bibleSettings.storageDirectory, appSettings.bibleSettings.secondaryBible)
            if (bibleFile.exists()) {
                try {
                    Bible().apply {
                        loadFromSpb(bibleFile.absolutePath)
                    }
                } catch (e: Exception) {
                    println("Error loading secondary Bible from settings: ${e.message}")
                    e.printStackTrace()
                    null
                }
            } else {
                println("Secondary Bible file not found: ${bibleFile.absolutePath}")
                null
            }
        } else {
            null
        }

        // Load books from primary Bible
        _primaryBible.value?.let { bible ->
            _books.value = bible.getBooks()
            if (bible.getBookCount() > 0) {
                loadChapter(_selectedBookIndex.value, _selectedChapter.value)
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
                _verses.value = bible.getChapter(bookId, chapter)
                _selectedVerseIndex.value = 0
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

    fun getChaptersForCurrentBook(): List<String> {
        _primaryBible.value?.let { bible ->
            val bookId = _selectedBookIndex.value + 1  // Convert 0-based index to 1-based book ID
            val chapterCount = bible.getChapterCount(bookId)
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
        return _books.value.filter { it.contains(query, ignoreCase = true) }
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
            verseList.add(
                SelectedVerse(
                    bookName = bookName,
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumber,
                    verseText = verseText
                )
            )
        }

        // Add secondary Bible verse if available
        _secondaryBible.value?.getVerseDetails(bookId, _selectedChapter.value, verseNumber)?.let { (bookName, verseText, _) ->
            verseList.add(
                SelectedVerse(
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
            val bookId = _selectedBookIndex.value + 1  // Convert 0-based index to 1-based book ID
            val maxChapter = bible.getChapterCount(bookId)
            if (_selectedChapter.value < maxChapter) {
                selectChapter(_selectedChapter.value + 1)
                return true
            }
        }
        return false
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedScope(scope: String) {
        _selectedScope.value = scope
    }

    fun updateSelectedMode(mode: String) {
        _selectedMode.value = mode
    }

    fun updateBookSearchQuery(query: String) {
        _bookSearchQuery.value = query
    }

    fun updateChapterSearchQuery(query: String) {
        _chapterSearchQuery.value = query
    }

    fun updateVerseSearchQuery(query: String) {
        _verseSearchQuery.value = query
    }

    fun performSearch() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            // Clear search results
            _searchResults.value = emptyList()
            _isSearchMode.value = false
            return
        }

        println("DEBUG: Performing search with query='$query', scope='${_selectedScope.value}', mode='${_selectedMode.value}'")

        _primaryBible.value?.let { bible ->
            try {
                // Determine search mode based on selectedMode
                val isExactMatch = _selectedMode.value.contains("Exact", ignoreCase = true)

                // Build regex pattern
                val pattern = if (isExactMatch) {
                    "\\b${Regex.escape(query)}\\b"
                } else {
                    Regex.escape(query)
                }
                val searchRegex = Regex(pattern, RegexOption.IGNORE_CASE)

                // Determine scope
                val results = when {
                    _selectedScope.value.contains("Current Book", ignoreCase = true) -> {
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

                println("Search completed: found ${results.size} results for '$query'")
            } catch (e: Exception) {
                println("Error performing search: ${e.message}")
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
