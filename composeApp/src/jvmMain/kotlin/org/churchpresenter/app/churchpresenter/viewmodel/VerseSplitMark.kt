package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.bible.SelectedVerse

/**
 * Telling the operator which half of a split verse is the one on screen. The split itself, and the
 * setting behind it, are in `VerseSplit.kt`.
 */

/**
 * Where the break falls in a split verse's line in the browser, and which side of it is on screen.
 *
 * The operator otherwise has no way to tell the two halves apart: the verse list highlights the same
 * row for both, so "which part is projected right now" is answerable only by reading the output.
 *
 * [breakOffset] is an index into the `"N. text"` line as the list draws it, not into the verse text,
 * so the number prefix is already accounted for.
 */
internal data class VerseSplitMark(
    val verseNumber: Int,
    val breakOffset: Int,
    val secondHalfLive: Boolean,
)

/** The chapter line [live] came from, when it is a single verse of the chapter being browsed. */
private fun BibleViewModel.liveVerseLine(live: SelectedVerse): String? {
    if (live.chapter != _selectedChapter.value || live.verseRange.isNotEmpty()) return null
    val bookId = _primaryBible.value?.getBookId(_selectedBookIndex.value) ?: return null
    if (_primaryBible.value?.getBookName(bookId) != live.bookName) return null
    return _verses.value.firstOrNull { verseNumberOf(it) == live.verseNumber }
}

/**
 * How the verse list should mark [live], or null when what is on screen is not half of a verse.
 *
 * Which half is read off the projected text itself rather than from the page state, so it cannot
 * disagree with the output -- including while Bible Hold has the projector on one half and the
 * operator has already browsed elsewhere.
 */
internal fun BibleViewModel.liveVerseSplitMark(live: SelectedVerse?): VerseSplitMark? {
    val line = live?.takeIf { splitLongVersesEnabled }?.let { liveVerseLine(it) } ?: return null
    val text = verseTextOf(line)
    if (!isLongVerse(text, longVerseWordCount)) return null
    val (first, second) = splitAtWordMidpoint(text)
    val secondHalfLive = when (live.verseText) {
        second -> true
        first -> false
        else -> return null
    }
    return VerseSplitMark(
        verseNumber = live.verseNumber,
        breakOffset = (line.length - text.length) + first.length,
        secondHalfLive = secondHalfLive,
    )
}
