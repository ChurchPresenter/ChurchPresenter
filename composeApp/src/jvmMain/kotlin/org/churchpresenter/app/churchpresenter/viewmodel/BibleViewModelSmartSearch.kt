package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.BibleSearch

private const val SCORE_EXACT = 100
private const val SCORE_PREFIX = 80
private const val SCORE_CONTAINS = 60

/**
 * The unified search box: turning what was typed into a book, a reference or a text query, and
 * acting on it.
 */

internal fun BibleViewModel.canonicalBookIdToIndex(canonicalId: Int): Int? =
    _primaryBible.value?.getDisplayIndexForBookId(canonicalId)?.takeIf { it in _books.value.indices }

internal fun BibleViewModel.scoreNameMatch(name: String, norm: String, normNoSpace: String): Int {
    val nameNoSpace = name.replace(" ", "")
    return when {
        name == norm || nameNoSpace == normNoSpace -> SCORE_EXACT
        name.startsWith(norm) || nameNoSpace.startsWith(normNoSpace) -> SCORE_PREFIX
        name.contains(norm) || nameNoSpace.contains(normNoSpace) -> SCORE_CONTAINS
        else -> 0
    }
}

internal fun BibleViewModel.rankedBookMatches(token: String): List<Pair<Int, Int>> {
    val norm = token.trim().lowercase().replace(Regex("\\s+"), " ")
    if (norm.isEmpty()) return emptyList()
    val normNoSpace = norm.replace(" ", "")
    val books = _books.value
    if (books.isEmpty()) return emptyList()

    val scored = linkedMapOf<Int, Int>()
    fun consider(index: Int?, score: Int) {
        if (index == null || index < 0 || score <= 0) return
        val prev = scored[index]
        if (prev == null || score > prev) scored[index] = score
    }

    books.forEachIndexed { i, name ->
        consider(i, scoreNameMatch(name.lowercase(), norm, normNoSpace))
    }

    BibleViewModel.STANDARD_ENGLISH_BOOKS.forEachIndexed { i, english ->
        consider(canonicalBookIdToIndex(i + 1), scoreNameMatch(english, norm, normNoSpace))
    }

    return scored.entries
        .sortedWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }
                .thenBy { books.getOrNull(it.key)?.length ?: Int.MAX_VALUE }
                .thenBy { it.key }
        )
        .map { it.key to it.value }
}

internal fun BibleViewModel.resolveBook(token: String): Int = rankedBookMatches(token).firstOrNull()?.first ?: -1

internal fun BibleViewModel.resolveBookForLiveNav(token: String): Int {
    val norm = token.trim().lowercase()
    if (norm.isEmpty()) return -1
    val exact = _books.value.indexOfFirst { it.lowercase() == norm }
    if (exact >= 0) return exact
    val ranked = rankedBookMatches(token)
    val top = ranked.firstOrNull()
    val topCount = ranked.count { it.second == top?.second }
    return if (top != null && topCount == 1) top.first else -1
}

internal fun BibleViewModel.parseReference(input: String): SmartReference? {
    val refRegex = Regex("^(.*?)\\s*(\\d+)(?:\\s*[:. ]\\s*(\\d+)(?:\\s*-\\s*(\\d+))?)?\\s*$")
    val match = refRegex.find(input)
    if (match != null) {
        val bookToken = match.groupValues[1].trim()
        val chapter = match.groupValues[2].toIntOrNull()
        val verseStart = match.groupValues[3].toIntOrNull()
        val verseEnd = match.groupValues[4].toIntOrNull()
        val bookIndex = if (bookToken.isEmpty()) {
            _selectedBookIndex.value
        } else {
            resolveBook(bookToken).takeIf { it >= 0 } ?: return null
        }
        return SmartReference(bookIndex, chapter, verseStart, verseEnd)
    }

    val bookIndex = resolveBookForLiveNav(input)
    return if (bookIndex >= 0) SmartReference(bookIndex, null, null, null) else null
}

internal fun BibleViewModel.isReferenceQuery(query: String): Boolean = parseReference(query.trim()) != null

internal fun BibleViewModel.navigateToReference(
    ref: SmartReference,
    goLive: Boolean = false,
    goLiveSource: String = "auto",
    matchType: String? = null,
) {
    val bible = _primaryBible.value ?: return
    val bookCount = minOf(bible.getBookCount(), BibleViewModel.CANONICAL_BOOK_COUNT)
    if (bookCount == 0) return
    val idx = ref.bookIndex.coerceIn(0, bookCount - 1)
    val targetChapter = (ref.chapter ?: 1).coerceAtLeast(1)

    searchJob?.cancel()
    _isSearchMode.value = false
    _searchResults.value = emptyList()
    _selectedBookIndex.value = idx
    _selectedChapter.value = targetChapter
    _selectedVerseIndex.value = 0
    _selectedVerseIndices.clear()
    _multiVerseEnabled.value = false

    loadChapterJob?.cancel()
    loadChapterJob = viewModelScope.launch {
        if (!_isFullyLoadedFlow.value) _isFullyLoadedFlow.first { it }
        val bookId = bible.getBookId(idx)
        val chapterVerses = withContext(ioDispatcher) {
            bible.getChapter(bookId, targetChapter).verses
        }
        _verses.value = chapterVerses

        val verseStart = ref.verseStart
        if (verseStart != null) {
            val startIdx = chapterVerses.indexOfFirst { it.startsWith("$verseStart. ") }
            _selectedVerseIndex.value = if (startIdx >= 0) startIdx else 0
            val verseEnd = ref.verseEnd
            if (verseEnd != null && verseEnd > verseStart) {
                _selectedVerseIndices.clear()
                for (v in verseStart..verseEnd) {
                    val vIdx = chapterVerses.indexOfFirst { it.startsWith("$v. ") }
                    if (vIdx >= 0) _selectedVerseIndices.add(vIdx)
                }
                _multiVerseEnabled.value = _selectedVerseIndices.size > 1
            }
        }
        _verseSelectionToken.value++

        if (goLive) {
            _autoFollowLiveSource.value = goLiveSource
            _autoFollowLiveMatchType.value = matchType
            _autoFollowLiveToken.value++
        }
        refreshFilteredLists()
    }
}

internal fun BibleViewModel.onSmartQueryChanged(query: String) {
    _searchQuery.value = query
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
        searchJob?.cancel()
        _isSearchMode.value = false
        _searchResults.value = emptyList()
        return
    }
    when (_searchMode.value) {
        BibleSearchMode.TEXT -> scheduleLiveSearch()
        BibleSearchMode.REFERENCE -> {
            searchJob?.cancel()
            _isSearchMode.value = false
            _searchResults.value = emptyList()
            parseReference(trimmed)?.let { navigateToReference(it) }
        }
        BibleSearchMode.AUTO -> {
            val ref = parseReference(trimmed)
            if (ref != null) {
                searchJob?.cancel()
                navigateToReference(ref)
            } else {
                scheduleLiveSearch()
            }
        }
    }
}

internal fun BibleViewModel.submitSmartQuery() {
    val trimmed = _searchQuery.value.trim()
    if (trimmed.isEmpty()) {
        clearSearch()
        return
    }
    when (_searchMode.value) {
        BibleSearchMode.TEXT -> performSearch()
        BibleSearchMode.REFERENCE -> parseReference(trimmed)?.let { navigateToReference(it) }
        BibleSearchMode.AUTO -> {
            val ref = parseReference(trimmed)
            if (ref != null) navigateToReference(ref) else performSearch()
        }
    }
}

internal fun BibleViewModel.selectSearchResult(result: BibleSearch) {
    val bookIndex = _books.value.indexOf(result.book)
    if (bookIndex < 0) return
    val chapter = result.chapter.toIntOrNull() ?: 1
    val verse = result.verse.toIntOrNull() ?: 1

    val bible = _primaryBible.value ?: return
    val bookCount = minOf(bible.getBookCount(), BibleViewModel.CANONICAL_BOOK_COUNT)
    if (bookCount == 0) return

    val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
    val bookId = bible.getBookId(clampedIndex)

    _selectedBookIndex.value = clampedIndex
    _selectedChapter.value = chapter
    _selectedVerseIndex.value = 0

    viewModelScope.launch {
        val chapterResult = withContext(ioDispatcher) {
            bible.getChapter(bookId, chapter)
        }
        val chapterVerses = chapterResult.verses
        _verses.value = chapterVerses

        val verseIndex = chapterVerses.indexOfFirst { it.startsWith("$verse. ") }
        _selectedVerseIndex.value = if (verseIndex >= 0) verseIndex else 0
        _verseSelectionToken.value++

        refreshFilteredLists()
    }
}
