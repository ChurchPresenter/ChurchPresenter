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

    init {
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
            val chapterCount = bible.getChapterCount(_selectedBookIndex.value)
            val count = if (chapterCount > 0) chapterCount else 1
            return (1..count).map { it.toString() }
        }
        return emptyList()
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

    fun performSearch() {
        // TODO: Implement search logic
    }
}
