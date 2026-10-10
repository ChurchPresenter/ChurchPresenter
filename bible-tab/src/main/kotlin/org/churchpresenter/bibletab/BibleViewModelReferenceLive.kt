package org.churchpresenter.bibletab

/**
 * Puts the reference [text] — "John 3:16-18", "Иоанна 3:16", "jn 3 16" — on screen as the search box
 * and Go Live do: the book read against this Bible's own names and the standard English ones, then
 * shown the tab's way, with every Bible setting. False when [text] names no book this Bible has.
 */
fun BibleViewModel.goLiveWithReference(text: String): Boolean {
    val ref = parseReference(text.trim())?.takeIf { it.chapter != null } ?: return false
    navigateToReference(ref, goLive = true, goLiveSource = "helper")
    return true
}

/** The standard English name of the book [bookId] (1-66), as the search box also reads it; null past 66. */
fun standardEnglishBookName(bookId: Int): String? = BibleViewModel.STANDARD_ENGLISH_BOOKS.getOrNull(bookId - 1)
