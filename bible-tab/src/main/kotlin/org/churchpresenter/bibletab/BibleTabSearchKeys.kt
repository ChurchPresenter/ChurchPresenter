package org.churchpresenter.bibletab

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.ShortcutAction

/*
 * The keyboard while the caret is in the Bible search box, and the key between search and what is
 * live.
 *
 * The search box is where the keyboard browses: typing and the arrow keys move the selection, and
 * nothing they do reaches the output until Go Live. While a verse is live in the ordinary browser,
 * the first search that moves the selection puts the output on hold (the same hold that navigating
 * to another chapter sets); Go Live releases it, and so does going back to live, which also restores
 * the selection. Split browse never sends a browse selection, so it needs no hold.
 */

/** A key pressed with the caret in the search box. False for one the field should have. */
internal fun BibleTabScope.handleSearchKey(viewModel: BibleViewModel, event: KeyEvent): Boolean = when {
    shortcuts.matches(ShortcutAction.GO_LIVE, event) -> {
        goLiveFromSearch(viewModel)
        true
    }
    event.key == Key.DirectionUp -> stepSearch(viewModel, forward = false)
    event.key == Key.DirectionDown -> stepSearch(viewModel, forward = true)
    else -> false
}

/** Into the search box with the query selected; or out of it, back to what is live (or the list). */
internal fun BibleTabScope.switchSearchLive(viewModel: BibleViewModel) {
    if (!searchFieldFocused) {
        searchFocus.focusAndSelectAll()
        return
    }
    if (currentIsPresenting && !splitBrowseMode) returnToLive(viewModel)
    focusRequester.requestFocus()
}

/** The search text changed: hold the output first when it is about to move a live selection. */
internal fun BibleTabScope.searchQueryChanged(viewModel: BibleViewModel, query: String) {
    holdForSearch(viewModel, query)
    viewModel.onSmartQueryChanged(query)
}

/** The selection is exactly what is on screen, so Go Live would change nothing. */
internal fun BibleTabScope.selectionIsLive(viewModel: BibleViewModel): Boolean {
    if (!liveAndShowing()) return false
    val selected = viewModel.getSelectedVerses()
    return selected.isNotEmpty() && selected.map { it.key() } == displayedVerses.map { it.key() }
}

private fun SelectedVerse.key() = Triple(bookName, chapter, verseNumber)

/** Something from this tab is on screen and not held behind a staged selection. */
private fun BibleTabScope.liveAndShowing(): Boolean =
    currentIsPresenting && bibleOutput?.bibleHold?.value != true && displayedVerses.isNotEmpty()

/** [ref] is the single verse on screen -- a chapter typed alone stands for its first verse. */
private fun BibleTabScope.referenceIsLive(ref: SmartReference): Boolean {
    if (!liveAndShowing() || ref.verseEnd != null) return false
    val shown = displayedVerses.singleOrNull() ?: return false
    return shown.key() == Triple(books.getOrNull(ref.bookIndex), ref.chapter ?: 1, ref.verseStart ?: 1)
}

private fun BibleTabScope.holdForSearch(viewModel: BibleViewModel, query: String) {
    if (searchMode == BibleSearchMode.TEXT || viewModel.parseReference(query.trim()) == null) return
    holdOutputForBrowsing()
}

/**
 * Go Live from the search box: the highlighted text-search result (the first when none is), or the
 * typed reference -- loaded first, so a fast typist never puts the previous verse up. A query that
 * is not a reference runs the text search instead, and the next Go Live takes a result.
 */
private fun BibleTabScope.goLiveFromSearch(viewModel: BibleViewModel) {
    if (isSearchMode && searchResults.isNotEmpty()) {
        val result = searchResults.getOrElse(ui.highlightedResult) { searchResults.first() }
        val bookIndex = books.indexOf(result.book)
        if (bookIndex < 0) return
        viewModel.clearSearch()
        val ref = SmartReference(bookIndex, result.chapter.toIntOrNull(), result.verse.toIntOrNull(), null)
        goLiveWith(viewModel, ref)
        return
    }
    val query = searchQuery.trim()
    if (query.isEmpty()) return
    val ref = if (searchMode == BibleSearchMode.TEXT) null else viewModel.parseReference(query)
    if (ref == null) viewModel.submitSmartQuery() else goLiveWith(viewModel, ref)
}

private fun BibleTabScope.goLiveWith(viewModel: BibleViewModel, ref: SmartReference) {
    if (!referenceIsLive(ref)) viewModel.navigateToReference(ref, goLive = true, goLiveSource = "manual")
    focusRequester.requestFocus()
}
