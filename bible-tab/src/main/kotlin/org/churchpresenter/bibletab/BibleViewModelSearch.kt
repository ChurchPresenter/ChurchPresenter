package org.churchpresenter.bibletab

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.bibletab.BibleViewModel.Companion.LIVE_SEARCH_DEBOUNCE_MS

fun BibleViewModel.updateSearchQuery(query: String) {
    _searchQuery.value = query
}

fun BibleViewModel.updateSelectedScopeIndex(index: Int) {
    _selectedScopeIndex.value = index
}

fun BibleViewModel.updateSelectedModeIndex(index: Int) {
    _selectedModeIndex.value = index
}

fun BibleViewModel.updateBookSearchQuery(query: String) {
    _bookSearchQuery.value = query
    refreshFilteredLists()
}

fun BibleViewModel.updateChapterSearchQuery(query: String) {
    _chapterSearchQuery.value = query
    refreshFilteredLists()
}

fun BibleViewModel.updateVerseSearchQuery(query: String) {
    _verseSearchQuery.value = query
    refreshFilteredLists()
}

fun BibleViewModel.performSearch() = launchSearch(debounceMs = 0L)

internal fun BibleViewModel.scheduleLiveSearch() = launchSearch(debounceMs = LIVE_SEARCH_DEBOUNCE_MS)

internal fun BibleViewModel.launchSearch(debounceMs: Long) {
    val query = _searchQuery.value.trim()
    searchJob?.cancel()
    if (query.length < 2) {
        _searchResults.value = emptyList()
        _isSearchMode.value = false
        return
    }
    val bible = _primaryBible.value ?: return
    val isExactMatch = _selectedModeIndex.value == 1
    val scopeIndex = _selectedScopeIndex.value
    val bookIndex = _selectedBookIndex.value

    searchJob = viewModelScope.launch {
        if (debounceMs > 0) delay(debounceMs)
        val results = withContext(ioDispatcher) {
            try {
                val pattern = if (isExactMatch) "\\b${Regex.escape(query)}\\b" else Regex.escape(query)
                val searchRegex = Regex(pattern, RegexOption.IGNORE_CASE)
                if (scopeIndex == 1) {
                    bible.searchBible(allWords = false, searchExp = searchRegex, book = bible.getBookId(bookIndex))
                } else {
                    bible.searchBible(allWords = false, searchExp = searchRegex)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                // A query that does not make a valid regex (PatternSyntaxException).
                e.printStackTrace()
                emptyList()
            }
        }
        if (isActive) {
            _searchResults.value = results
            _isSearchMode.value = true
        }
    }
}

fun BibleViewModel.cycleSearchMode() {
    _searchMode.value = when (_searchMode.value) {
        BibleSearchMode.AUTO -> BibleSearchMode.REFERENCE
        BibleSearchMode.REFERENCE -> BibleSearchMode.TEXT
        BibleSearchMode.TEXT -> BibleSearchMode.AUTO
    }
    onSmartQueryChanged(_searchQuery.value)
}
