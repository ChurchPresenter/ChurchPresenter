package org.churchpresenter.bibletab

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.bibletab.BibleViewModel.Companion.CANONICAL_BOOK_COUNT

suspend fun BibleViewModel.getVersesForDisplay(bookName: String, chapter: Int, verseNum: Int): List<SelectedVerse> {
    val primaryBible = _primaryBible.value ?: return emptyList()
    val bookIndex = _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
    if (bookIndex < 0) return emptyList()
    val bookId = primaryBible.getBookId(bookIndex)
    return withContext(ioDispatcher) {
        val verseList = mutableListOf<SelectedVerse>()
        val chapterVerses = primaryBible.getChapter(bookId, chapter).verses
        val verseStr = chapterVerses.firstOrNull { v ->
            verseNumberOf(v) == verseNum
        }
        val primaryVerseText = verseStr?.let { verseTextOf(it, "") } ?: ""
        val primaryBookName = primaryBible.getBookName(bookId) ?: bookName
        if (primaryVerseText.isNotEmpty()) {
            verseList.add(
                SelectedVerse(
                    translationFileName = _loadedTranslations.value.firstOrNull()?.fileName.orEmpty(),
                    bibleAbbreviation = primaryBible.getBibleAbbreviation(),
                    bibleName = primaryBible.getBibleTitle(),
                    bookName = primaryBookName,
                    chapter = chapter,
                    verseNumber = verseNum,
                    verseText = primaryVerseText,

                    bookId = bookId,
                )
            )
        }
        val codeRef = primaryBible.getCodeReference(bookId, chapter, verseNum)
        val targetBook = codeRef?.first ?: bookId
        val targetChapter = codeRef?.second ?: chapter
        val targetVerse = codeRef?.third ?: verseNum
        _loadedTranslations.value.drop(1).forEach { loadedTranslation ->
            val bible = loadedTranslation.bible
            bible.getVerseDetailsByCode(targetBook, targetChapter, targetVerse)?.let { result ->
                verseList.add(
                    SelectedVerse(
                        translationFileName = loadedTranslation.fileName,
                        bibleAbbreviation = bible.getBibleAbbreviation(),
                        bibleName = bible.getBibleTitle(),
                        bookName = result.bookName,
                        chapter = result.displayChapter,
                        verseNumber = result.displayVerse,
                        verseText = result.verseText,
                        bookId = targetBook,
                    )
                )
            }
        }
        verseList.versePage(currentVersePage(), splitLongVersesEnabled, longVerseWordCount)
    }
}

internal fun BibleViewModel.parseVerseNumbers(rangeStr: String): List<Int> {
    val result = mutableListOf<Int>()
    rangeStr.split(",").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val bounds = trimmed.split("-")
            val from = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
            val to   = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
            for (index in from..to) result.add(index)
        } else {
            trimmed.toIntOrNull()?.let { result.add(it) }
        }
    }
    return result
}

fun BibleViewModel.selectVerseByDetails(
    bookName: String,
    chapter: Int,
    verseNumber: Int,
    verseRange: String = "",
    goLiveSource: String? = null,
    bookId: Int = 0
): Boolean {
    val bookIndex = resolveBookIndex(bookName, bookId)
    if (bookIndex < 0) return false

    beginVerseSelection(bookIndex, chapter)
    viewModelScope.launch {
        awaitFullyLoaded()
        applyVerseSelection(bookIndex, chapter, verseNumber, verseRange, goLiveSource)
    }
    return true
}

/**
 * Selects the same verse as [selectVerseByDetails] but waits for it, and hands back the verses
 * that are now selected — empty when this Bible does not have the reference.
 *
 * The difference from [selectVerseByDetails] is the **order**: the Bible is awaited *before*
 * the book is looked up, not after. At startup `loadBibles` publishes a books-only module first
 * and the full text second, so a reference resolved during that window either misses the book
 * list entirely or finds a book whose chapters hold no verses — and the caller is told `true`
 * either way. That is the blank output a schedule item produced on the first click of a cold
 * start.
 *
 * Returning the verses is the other half: the caller pushes them to the presenter itself rather
 * than leaving it to a token effect that may not be composed by the time the token moves.
 */
suspend fun BibleViewModel.resolveVerseSelection(
    bookName: String,
    chapter: Int,
    verseNumber: Int,
    verseRange: String = "",
    goLiveSource: String? = null,
    bookId: Int = 0,
): List<SelectedVerse> {
    awaitFullyLoaded()
    val bookIndex = resolveBookIndex(bookName, bookId)
    if (bookIndex < 0) return emptyList()

    beginVerseSelection(bookIndex, chapter)
    applyVerseSelection(bookIndex, chapter, verseNumber, verseRange, goLiveSource)
    return getSelectedVerses()
}

/** Suspends until the full Bible text is loaded; returns at once when it already is. */
internal suspend fun BibleViewModel.awaitFullyLoaded() {
    if (!_isFullyLoadedFlow.value) {
        _isFullyLoadedFlow.first { it }
    }
}

/**
 * The display index of the book [bookId] (preferred) or [bookName] names, or -1.
 *
 * The id is tried first because it survives a translation change; the name is the fallback for
 * schedules written before ids were stored, and for callers that only have one.
 */
fun BibleViewModel.resolveBookIndex(bookName: String, bookId: Int): Int {
    val byId = if (bookId > 0) {
        _primaryBible.value?.getDisplayIndexForBookId(bookId)?.takeIf { it in _books.value.indices }
    } else {
        null
    }
    return byId ?: _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
}

/** Points the selection at a book and chapter, before the chapter's text has been read. */
internal fun BibleViewModel.beginVerseSelection(bookIndex: Int, chapter: Int) {
    _selectedBookIndex.value = bookIndex
    _selectedChapter.value = chapter
    _selectedVerseIndex.value = 0

    _selectedVerseIndices.clear()
    _multiVerseEnabled.value = false
}

/**
 * Turns a saved range ("16-18", "16,18,20") into a multi-verse selection within [chapterVerses].
 *
 * A range naming one verse leaves the single-verse selection alone: multi-verse mode changes how
 * the verse is drawn and how the reference reads, and one verse is not that.
 */
internal fun BibleViewModel.applyVerseRange(verseRange: String, chapterVerses: List<String>) {
    if (verseRange.isEmpty()) return
    val verseNumbers = parseVerseNumbers(verseRange)
    if (verseNumbers.size <= 1) return

    _selectedVerseIndices.clear()
    for (vNum in verseNumbers) {
        val vIdx = chapterVerses.indexOfFirst { it.startsWith("$vNum. ") }
        if (vIdx >= 0) _selectedVerseIndices.add(vIdx)
    }
    _multiVerseEnabled.value = _selectedVerseIndices.size > 1
}

/** Reads the chapter and settles the selection inside it. Assumes the Bible is fully loaded. */
internal suspend fun BibleViewModel.applyVerseSelection(
    bookIndex: Int,
    chapter: Int,
    verseNumber: Int,
    verseRange: String,
    goLiveSource: String?,
) {
    val bible = _primaryBible.value ?: return
    val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
    if (bookCount == 0) return

    val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
    val bookId = bible.getBookId(clampedIndex)

    val chapterResult = withContext(ioDispatcher) {
        bible.getChapter(bookId, chapter)
    }
    val chapterVerses = chapterResult.verses
    _verses.value = chapterVerses

    val verseIndex = chapterVerses.indexOfFirst { it.startsWith("$verseNumber. ") }
    _selectedVerseIndex.value = if (verseIndex >= 0) verseIndex else 0

    applyVerseRange(verseRange, chapterVerses)

    _verseSelectionToken.value++

    refreshFilteredLists()

    if (goLiveSource != null) {
        _autoFollowLiveSource.value = goLiveSource

        _autoFollowLiveMatchType.value = null
        _autoFollowLiveToken.value++
    }
}
