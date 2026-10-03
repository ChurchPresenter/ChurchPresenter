package org.churchpresenter.bibletab

import kotlinx.coroutines.cancel

fun BibleViewModel.ctrlClickVerse(verseIndex: Int) {
    if (verseIndex < 0 || verseIndex >= _verses.value.size) return
    if (_selectedVerseIndices.contains(verseIndex)) {
        _selectedVerseIndices.remove(verseIndex)
        if (_selectedVerseIndices.isEmpty()) {

            _selectedVerseIndex.value = verseIndex
        }
    } else {

        if (_selectedVerseIndices.isEmpty()) {
            val anchor = _selectedVerseIndex.value
            if (anchor >= 0 && anchor < _verses.value.size && anchor != verseIndex) {
                _selectedVerseIndices.add(anchor)
            }
        }
        _selectedVerseIndices.add(verseIndex)
        _selectedVerseIndex.value = verseIndex
    }
    _multiVerseEnabled.value = _selectedVerseIndices.isNotEmpty()
    _verseSelectionToken.value++
}

fun BibleViewModel.shiftClickVerse(targetIndex: Int) {
    if (targetIndex < 0 || targetIndex >= _verses.value.size) return
    val anchor = _selectedVerseIndex.value.coerceIn(0, _verses.value.size - 1)
    val from = minOf(anchor, targetIndex)
    val to   = maxOf(anchor, targetIndex)
    _selectedVerseIndices.clear()
    for (index in from..to) _selectedVerseIndices.add(index)
    _multiVerseEnabled.value = _selectedVerseIndices.size > 1
    _verseSelectionToken.value++
}

fun BibleViewModel.getSelectedVerseNumbers(): List<Int> {
    return _selectedVerseIndices.sorted().mapNotNull { idx ->
        _verses.value.getOrNull(idx)?.let { verseNumberOf(it) }
    }
}

fun BibleViewModel.clearSearch() {
    searchJob?.cancel()
    _searchQuery.value = ""
    _searchResults.value = emptyList()
    _isSearchMode.value = false
}
