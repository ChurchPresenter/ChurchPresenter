package org.churchpresenter.bibletab

import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.bibletab.BibleViewModel.Companion.CANONICAL_BOOK_COUNT
import org.churchpresenter.bibletab.BibleViewModel.Companion.CLICK_DEBOUNCE_MS
import org.churchpresenter.bibletab.BibleViewModel.Companion.STANDARD_ENGLISH_BOOKS

suspend fun BibleViewModel.getChapterVerses(bookName: String, chapter: Int): List<String> {
    val bible = _primaryBible.value ?: return emptyList()
    val bookIndex = _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
    if (bookIndex < 0) return emptyList()
    val bookId = bible.getBookId(bookIndex)
    return withContext(ioDispatcher) {
        bible.getChapter(bookId, chapter).verses
    }
}

fun BibleViewModel.loadChapter(bookIndex: Int, chapter: Int) {
    _primaryBible.value?.let { bible ->
        val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
        if (bookCount > 0) {
            val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
            _selectedBookIndex.value = clampedIndex
            _selectedChapter.value = chapter
            _selectedVerseIndex.value = 0
            loadChapterJob?.cancel()
            loadChapterJob = viewModelScope.launch {
                val bookId = bible.getBookId(clampedIndex)
                val chapterResult = withContext(ioDispatcher) {
                    bible.getChapter(bookId, chapter)
                }
                _verses.value = chapterResult.verses
                refreshFilteredLists()
                _verseSelectionToken.value++
            }
        }
    }
}

fun BibleViewModel.selectBook(bookIndex: Int) {
    val now = System.currentTimeMillis()
    if (now - lastBookSelectTime < CLICK_DEBOUNCE_MS) return
    lastBookSelectTime = now
    _selectedBookIndex.value = bookIndex
    _selectedChapter.value = 1
    _selectedVerseIndex.value = 0
    _selectedVerseIndices.clear()
    _multiVerseEnabled.value = false
    loadChapter(bookIndex, 1)
}

fun BibleViewModel.selectChapter(chapter: Int) {
    val now = System.currentTimeMillis()
    if (now - lastChapterSelectTime < CLICK_DEBOUNCE_MS) return
    lastChapterSelectTime = now
    _selectedChapter.value = chapter
    _selectedVerseIndex.value = 0
    _selectedVerseIndices.clear()
    _multiVerseEnabled.value = false
    loadChapter(_selectedBookIndex.value, chapter)
}

fun BibleViewModel.selectVerse(verseIndex: Int) {
    _selectedVerseIndices.clear()
    _multiVerseEnabled.value = false
    if (verseIndex >= 0 && verseIndex < _verses.value.size) {
        _selectedVerseIndex.value = verseIndex
        _verseSelectionToken.value++
    } else {
        _selectedVerseIndex.value = 0
    }
}

fun BibleViewModel.getChaptersForCurrentBook(): List<String> {
    _primaryBible.value?.let { bible ->

        val bookIndex = _selectedBookIndex.value
        val chapterCount = bible.getChapterCount(bookIndex)
        val count = if (chapterCount > 0) chapterCount else 1
        return (1..count).map { it.toString() }
    }
    return emptyList()
}

fun BibleViewModel.getFilteredBooks(): List<String> {
    val query = _bookSearchQuery.value
    if (query.isEmpty()) return _books.value

    return _books.value.filter { it.contains(query, ignoreCase = true) }
        .ifEmpty { booksNamedInEnglish(query) }
}

internal fun BibleViewModel.booksNamedInEnglish(query: String): List<String> {
    if (!query.all { it.isLetter() && it.code < ASCII_LIMIT }) return emptyList()
    val bible = _primaryBible.value ?: return emptyList()
    return STANDARD_ENGLISH_BOOKS
        .mapIndexedNotNull { index, englishName ->
            (index + 1).takeIf { englishName.contains(query, ignoreCase = true) }
        }
        .mapNotNull { bookId -> bible.getBookName(bookId) }
        .filter { it in _books.value }
}

fun BibleViewModel.getFilteredChapters(): List<String> {
    val chapters = getChaptersForCurrentBook()
    val query = _chapterSearchQuery.value
    if (query.isEmpty()) {
        return chapters
    }
    return chapters.filter { it.contains(query, ignoreCase = true) }
}

fun BibleViewModel.getFilteredVerses(): List<String> {
    val query = _verseSearchQuery.value
    if (query.isEmpty()) {
        return _verses.value
    }
    return _verses.value.filter { it.contains(query, ignoreCase = true) }
}
