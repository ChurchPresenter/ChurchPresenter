package org.churchpresenter.bibletab

import org.churchpresenter.bibletab.BibleViewModel.ModuleRef
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.Bible
import org.churchpresenter.bibletab.BibleViewModel.Companion.CANONICAL_BOOK_COUNT

internal fun Bible.getCanonicalBooks(): List<String> = getBooks().take(BibleViewModel.CANONICAL_BOOK_COUNT)

fun BibleViewModel.selectVerseByBookId(bookId: Int, chapter: Int, verseNumber: Int) {
    val bible = _primaryBible.value ?: return
    val displayIndex = bible.getDisplayIndexForBookId(bookId)
    if (displayIndex < 0) return
    val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
    val clamped = displayIndex.coerceIn(0, bookCount - 1)

    searchJob?.cancel()
    _isSearchMode.value = false
    _searchResults.value = emptyList()
    _selectedBookIndex.value = clamped
    _selectedChapter.value = chapter
    _selectedVerseIndex.value = 0
    _selectedVerseIndices.clear()
    _multiVerseEnabled.value = false

    loadChapterJob?.cancel()
    loadChapterJob = viewModelScope.launch {
        if (!_isFullyLoadedFlow.value) _isFullyLoadedFlow.first { it }
        val bId = bible.getBookId(clamped)
        val chapterVerses = withContext(ioDispatcher) { bible.getChapter(bId, chapter).verses }
        _verses.value = chapterVerses
        val verseIdx = chapterVerses.indexOfFirst { it.startsWith("$verseNumber. ") }
        _selectedVerseIndex.value = if (verseIdx >= 0) verseIdx else 0
        _verseSelectionToken.value++
        refreshFilteredLists()
    }
}

fun BibleViewModel.selectVerseByCanonicalRef(
    bookId: Int,
    chapter: Int,
    verse: Int,
    goLiveSource: String? = null,
): Boolean {
    val bible = _primaryBible.value ?: return false

    val details = bible.getVerseDetailsByCode(bookId, chapter, verse) ?: return false

    return selectVerseByDetails(
        bookName = details.bookName,
        chapter = details.displayChapter,
        verseNumber = details.displayVerse,
        goLiveSource = goLiveSource,
        bookId = bookId,
    )
}

fun BibleViewModel.moduleRefFor(bookId: Int, chapter: Int, verse: Int): ModuleRef? {
    val bible = _primaryBible.value ?: return null
    val details = bible.getVerseDetailsByCode(bookId, chapter, verse) ?: return null
    return ModuleRef(
        abbreviation = bible.getBookAbbreviation(bookId) ?: details.bookName,
        chapter = details.displayChapter,
        verse = details.displayVerse,
        text = details.verseText,
    )
}

internal fun BibleViewModel.refreshFilteredLists() {
    _filteredBooks.value = getFilteredBooks()
    _filteredChapters.value = getFilteredChapters()
    _filteredVerses.value = getFilteredVerses()
}

fun BibleViewModel.addCurrentVerseToSchedule(
    onAdd: (
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseText: String,
        verseRange: String,
        bookId: Int
    ) -> Unit
): Boolean {
    if (_verses.value.isEmpty()) return false
    val idx = _selectedVerseIndex.value
    if (idx < 0 || idx >= _verses.value.size) return false
    val selectedVerses = getSelectedVerses()
    if (selectedVerses.isEmpty()) return false
    val verse = selectedVerses[0]
    onAdd(verse.bookName, verse.chapter, verse.verseNumber, verse.verseText, verse.verseRange, verse.bookId)

    if (_multiVerseEnabled.value) {
        clearMultiVerseSelection()
    }
    return true
}

fun BibleViewModel.addCanonicalRefToSchedule(
    bookId: Int,
    chapter: Int,
    verse: Int,
    onAdd: (
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseText: String,
        verseRange: String,
        bookId: Int
    ) -> Unit,
): Boolean {
    val bible = _primaryBible.value ?: return false
    val details = bible.getVerseDetailsByCode(bookId, chapter, verse) ?: return false
    onAdd(details.bookName, details.displayChapter, details.displayVerse, details.verseText, "", bookId)
    return true
}
