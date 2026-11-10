package org.churchpresenter.bibletab

/**
 * What the search box's text names: a book, chapter or verse, or nothing (a text search), under the
 * search mode and scope.
 */

/** The search scope that limits a text search to the selected book (`selectedScopeIndex`). */
internal const val SCOPE_CURRENT_BOOK = 1

private val CHAPTER_VERSE_REGEX = Regex("^(.*?)\\s*(\\d+)(?:\\s*[:. ]\\s*(\\d+)(?:\\s*-\\s*(\\d+))?)?\\s*$")

/** "John 3:16-18" split up: the book as typed (maybe empty), the chapter and the verse span. */
private data class ChapterVerse(val bookToken: String, val chapter: Int?, val verseStart: Int?, val verseEnd: Int?)

private fun matchChapterVerse(input: String): ChapterVerse? {
    val groups = CHAPTER_VERSE_REGEX.find(input)?.groupValues ?: return null
    return ChapterVerse(
        bookToken = groups[1].trim(),
        chapter = groups[2].toIntOrNull(),
        verseStart = groups[3].toIntOrNull(),
        verseEnd = groups[4].toIntOrNull(),
    )
}

internal fun BibleViewModel.parseReference(input: String): SmartReference? {
    val match = matchChapterVerse(input)
    if (match != null) {
        val bookIndex = if (match.bookToken.isEmpty()) {
            _selectedBookIndex.value
        } else {
            resolveBook(match.bookToken).takeIf { it >= 0 } ?: return null
        }
        return SmartReference(bookIndex, match.chapter, match.verseStart, match.verseEnd)
    }

    val bookIndex = resolveBookForLiveNav(input)
    return if (bookIndex >= 0) SmartReference(bookIndex, null, null, null) else null
}

/** Only numbers -- "3", "3:16", "3:16-18" -- read as a place in the selected book; anything else is null. */
internal fun BibleViewModel.parseBooklessReference(input: String): SmartReference? {
    val match = matchChapterVerse(input)?.takeIf { it.bookToken.isEmpty() } ?: return null
    return SmartReference(_selectedBookIndex.value, match.chapter, match.verseStart, match.verseEnd)
}

/**
 * The reference the search box's [query] names under the current mode and scope, or null when it is
 * a text search. Auto searching the Current Book takes only numbers as a reference, in that book:
 * a word such as "Jesus" would otherwise read as a book ("Je…" → Jeremiah) and leave it.
 */
internal fun BibleViewModel.referenceFor(query: String): SmartReference? = when (_searchMode.value) {
    BibleSearchMode.TEXT -> null
    BibleSearchMode.REFERENCE -> parseReference(query)
    BibleSearchMode.AUTO ->
        if (_selectedScopeIndex.value == SCOPE_CURRENT_BOOK) parseBooklessReference(query) else parseReference(query)
}
