package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.*
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.utils.Constants.CONTAINS
import org.churchpresenter.app.churchpresenter.utils.Constants.CURRENT_BOOK
import org.churchpresenter.app.churchpresenter.utils.Constants.ENTIRE_BIBLE
import java.io.File

class BibleViewModel(
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
        _selectedScope.value = ENTIRE_BIBLE
        _selectedMode.value = CONTAINS

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
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        } else {
            null
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
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        } else {
            null
        }

        // Load books from primary Bible only
        _primaryBible.value?.let { bible ->
            _books.value = bible.getBooks()
            if (bible.getBookCount() > 0) {
                loadChapter(_selectedBookIndex.value, _selectedChapter.value)
            }
        } ?: run {
            // No primary Bible loaded - clear books and verses
            _books.value = emptyList()
            _verses.value = emptyList()
        }
    }

    fun loadChapter(bookIndex: Int, chapter: Int) {
        println("DEBUG BibleViewModel: loadChapter called with bookIndex=$bookIndex, chapter=$chapter")
        _primaryBible.value?.let { bible ->
            val bookCount = bible.getBookCount()
            println("DEBUG BibleViewModel: bookCount=$bookCount")
            if (bookCount > 0) {
                val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
                _selectedBookIndex.value = clampedIndex
                _selectedChapter.value = chapter

                val bookId = clampedIndex + 1
                println("DEBUG BibleViewModel: Getting chapter for bookId=$bookId, chapter=$chapter")
                val chapterVerses = bible.getChapter(bookId, chapter)
                println("DEBUG BibleViewModel: Got ${chapterVerses.size} verses")
                _verses.value = chapterVerses
                _selectedVerseIndex.value = 0
            }
        } ?: println("DEBUG BibleViewModel: _primaryBible.value is null")
    }

    fun selectBook(bookIndex: Int) {
        println("DEBUG BibleViewModel: selectBook called with bookIndex=$bookIndex")
        _selectedBookIndex.value = bookIndex
        _selectedChapter.value = 1
        _selectedVerseIndex.value = 0
        println("DEBUG BibleViewModel: Before loadChapter - _verses.value.size=${_verses.value.size}")
        loadChapter(bookIndex, 1)
        println("DEBUG BibleViewModel: After loadChapter - _verses.value.size=${_verses.value.size}")
    }

    fun selectChapter(chapter: Int) {
        println("DEBUG BibleViewModel: selectChapter called with chapter=$chapter")
        _selectedChapter.value = chapter
        _selectedVerseIndex.value = 0
        println("DEBUG BibleViewModel: Before loadChapter - _verses.value.size=${_verses.value.size}")
        loadChapter(_selectedBookIndex.value, chapter)
        println("DEBUG BibleViewModel: After loadChapter - _verses.value.size=${_verses.value.size}")
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
            // getChapterCount expects 0-based book index
            val chapterCount = bible.getChapterCount(_selectedBookIndex.value)
            val count = if (chapterCount > 0) chapterCount else 1
            return (1..count).map { it.toString() }
        }
        return emptyList()
    }

    // Map of common English book name searches to their Russian equivalents
    private val englishToRussianBookMap = mapOf(
        "genesis" to "бытие",
        "exodus" to "исход",
        "leviticus" to "левит",
        "numbers" to "числа",
        "deuteronomy" to "второзаконие",
        "joshua" to "иисус навин",
        "judges" to "судей",
        "ruth" to "руфь",
        "samuel" to "царств",
        "kings" to "царств",
        "chronicles" to "паралипоменон",
        "ezra" to "ездра",
        "nehemiah" to "неемия",
        "esther" to "есфирь",
        "job" to "иов",
        "psalm" to "псалтирь",
        "proverbs" to "притчи",
        "ecclesiastes" to "екклесиаст",
        "song" to "песн",
        "isaiah" to "исаия",
        "jeremiah" to "иеремия",
        "lamentations" to "плач",
        "ezekiel" to "иезекииль",
        "daniel" to "даниил",
        "hosea" to "осия",
        "joel" to "иоиль",
        "amos" to "амос",
        "obadiah" to "авдий",
        "jonah" to "иона",
        "micah" to "михей",
        "nahum" to "наум",
        "habakkuk" to "аввакум",
        "zephaniah" to "софония",
        "haggai" to "аггей",
        "zechariah" to "захария",
        "malachi" to "малахия",
        "matthew" to "матфея",
        "mark" to "марка",
        "luke" to "луки",
        "john" to "иоанн",
        "acts" to "деяния",
        "romans" to "римлянам",
        "corinthians" to "коринфянам",
        "galatians" to "галатам",
        "ephesians" to "ефесянам",
        "philippians" to "филиппийцам",
        "colossians" to "колоссянам",
        "thessalonians" to "фессалоникийцам",
        "timothy" to "тимофею",
        "titus" to "титу",
        "philemon" to "филимону",
        "hebrews" to "евреям",
        "james" to "иакова",
        "peter" to "петра",
        "jude" to "иуды",
        "revelation" to "откровение"
    )

    fun getFilteredBooks(): List<String> {
        val query = _bookSearchQuery.value

        if (query.isEmpty()) {
            return _books.value
        }

        // First try direct match
        val directMatch = _books.value.filter { it.contains(query, ignoreCase = true) }

        // If no direct match and query is Latin characters, try English-to-Russian mapping
        val filtered = if (directMatch.isEmpty() && query.all { it.isLetter() && it.code < 128 }) {

            // Find ALL English book names that match the query
            val matchedEntries = englishToRussianBookMap.entries.filter {
                val keyContainsQuery = it.key.contains(query, ignoreCase = true)
                val queryContainsKey = query.contains(it.key, ignoreCase = true)
                keyContainsQuery || queryContainsKey
            }

            if (matchedEntries.isNotEmpty()) {

                // Search for books using ALL the Russian equivalents
                val allRussianEquivalents = matchedEntries.map { it.value }
                val result = _books.value.filter { bookName ->
                    val matches = allRussianEquivalents.any { russianName ->
                        bookName.contains(russianName, ignoreCase = true)
                    }
                    matches
                }
                result
            } else {
                directMatch
            }
        } else {
            directMatch
        }

        return filtered
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
            // getChapterCount expects 0-based book index
            val maxChapter = bible.getChapterCount(_selectedBookIndex.value)
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
                    _selectedScope.value.contains(CURRENT_BOOK, ignoreCase = true) -> {
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
