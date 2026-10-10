package org.churchpresenter.bibletab

fun BibleViewModel.navigatePreviousVerse(): Boolean {
    if (stepSelectedVersePage(forward = false)) return true
    if (_verses.value.isNotEmpty() && _selectedVerseIndex.value > 0) {
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
        _selectedVerseIndex.value--
        // Backwards through a split verse means its second half first, not its first.
        publishLandingPage(
            selectedVersesUnsplit().firstOrNull()?.verseText.orEmpty(), fromBehind = true,
        )
        return true
    }
    return false
}

/** Moves to the other half of the live verse, when it is split and there is a half to move to. */
internal fun BibleViewModel.stepSelectedVersePage(forward: Boolean): Boolean =
    stepVersePage(selectedVersesUnsplit().firstOrNull()?.verseText ?: return false, forward)

fun BibleViewModel.navigateNextVerse(): Boolean {
    if (_verses.value.isEmpty()) return false
    if (stepSelectedVersePage(forward = true)) return true
    if (_selectedVerseIndex.value < _verses.value.size - 1) {
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
        _selectedVerseIndex.value++
        _verseSelectionToken.value++
        return true
    }

    val bible = _primaryBible.value ?: return false
    var nextBookIndex = _selectedBookIndex.value
    var nextChapter = _selectedChapter.value + 1
    if (nextChapter > bible.getChapterCount(nextBookIndex)) {
        nextBookIndex += 1
        nextChapter = 1
        if (nextBookIndex >= _books.value.size) return false
    }
    _sequentialChapterAdvance = true
    _selectedVerseIndices.clear()
    _multiVerseEnabled.value = false
    loadChapter(nextBookIndex, nextChapter)
    return true
}

fun BibleViewModel.navigatePreviousChapter(): Boolean {
    if (_selectedChapter.value > 1) {
        _sequentialChapterAdvance = true
        selectChapter(_selectedChapter.value - 1)
        return true
    }
    return false
}

fun BibleViewModel.navigateNextChapter(): Boolean {
    _primaryBible.value?.let { bible ->

        val maxChapter = bible.getChapterCount(_selectedBookIndex.value)
        if (_selectedChapter.value < maxChapter) {
            _sequentialChapterAdvance = true
            selectChapter(_selectedChapter.value + 1)
            return true
        }
    }
    return false
}

fun BibleViewModel.consumeSequentialChapterAdvance(): Boolean {
    val wasSequentialAdvance = _sequentialChapterAdvance
    _sequentialChapterAdvance = false
    return wasSequentialAdvance
}
