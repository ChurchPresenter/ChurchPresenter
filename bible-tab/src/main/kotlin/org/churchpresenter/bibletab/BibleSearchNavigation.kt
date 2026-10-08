package org.churchpresenter.bibletab

// Where the search box's arrow keys step to, and the way back from a search to what is live.

/**
 * Up/down with the caret in the search box: through the text-search results, or one step at the
 * level the reference was typed to -- the book, the chapter or the verse -- rewriting the query.
 */
internal fun BibleTabScope.stepSearch(viewModel: BibleViewModel, forward: Boolean): Boolean {
    if (isSearchMode && searchResults.isNotEmpty()) {
        val step = if (forward) 1 else -1
        ui.highlightedResult = (ui.highlightedResult + step).coerceIn(0, searchResults.lastIndex)
        return true
    }
    if (searchMode == BibleSearchMode.TEXT) return false
    val ref = viewModel.parseReference(searchQuery.trim()) ?: return false
    val next = steppedReference(viewModel, ref, forward) ?: return true
    searchQueryChanged(viewModel, referenceText(next))
    return true
}

/** [ref] one step on at the level it names, or null at the edge of the book, chapter or Bible. */
internal fun BibleTabScope.steppedReference(
    viewModel: BibleViewModel,
    ref: SmartReference,
    forward: Boolean,
): SmartReference? {
    val delta = if (forward) 1 else -1
    val bible = viewModel.primaryBible.value ?: return null
    val verseStart = ref.verseStart
    val chapter = ref.chapter
    return when {
        verseStart != null -> {
            val verse = verseStart + delta
            val last = lastVerseOf(ref)
            if (verse < 1 || (last != null && verse > last)) null else ref.copy(verseStart = verse, verseEnd = null)
        }
        chapter != null -> {
            val next = chapter + delta
            if (next < 1 || next > bible.getChapterCount(ref.bookIndex)) null else ref.copy(chapter = next)
        }
        else -> {
            val bookCount = minOf(books.size, BibleViewModel.CANONICAL_BOOK_COUNT)
            val next = ref.bookIndex + delta
            if (next !in 0 until bookCount) null else ref.copy(bookIndex = next)
        }
    }
}

/** The last verse of [ref]'s chapter when that chapter is the one loaded, else null (unknown). */
private fun BibleTabScope.lastVerseOf(ref: SmartReference): Int? {
    if (ref.bookIndex != selectedBookIndex || (ref.chapter ?: 1) != selectedChapter) return null
    return verses.lastOrNull()?.substringBefore(". ")?.toIntOrNull()
}

/** How a reference reads in the search box: "John", "John 3" or "John 3:16". */
internal fun BibleTabScope.referenceText(ref: SmartReference): String {
    val book = books.getOrNull(ref.bookIndex).orEmpty()
    val chapter = ref.chapter ?: return book
    val verse = ref.verseStart ?: return "$book $chapter"
    return "$book $chapter:$verse"
}

/**
 * Back to what is live from a search: release the hold the search set -- re-sending what is on
 * screen first, so releasing it shows nothing new -- and select the live verse again. Returning is
 * not navigating away, so it must not set the chapter-change hold either.
 */
internal fun BibleTabScope.returnToLive(viewModel: BibleViewModel) {
    val shown = displayedVerses.firstOrNull() ?: return
    if (ui.heldForSearch) {
        onVerseSelected(displayedVerses)
        bibleOutput?.setBibleHold(false)
        ui.heldForSearch = false
    }
    val bookIndex = books.indexOf(shown.bookName)
    if (bookIndex < 0) return
    val (start, end) = verseSpan(shown.verseRange, shown.verseNumber)
    if (bookIndex != selectedBookIndex || shown.chapter != selectedChapter) {
        viewModel._sequentialChapterAdvance = true
    }
    viewModel.navigateToReference(SmartReference(bookIndex, shown.chapter, start, end))
}

/**
 * Holds the output before a browse moves a live selection -- a search, or a schedule verse opened
 * with a click -- so it stays off the screen until Go Live. Going back to live releases only this
 * hold, never one the operator set. Split browse never sends a browse selection, so needs none.
 */
internal fun BibleTabScope.holdOutputForBrowsing() {
    if (!currentIsPresenting || splitBrowseMode) return
    val output = bibleOutput ?: return
    if (!output.bibleHold.value) {
        output.setBibleHold(true)
        ui.heldForSearch = true
    }
}
