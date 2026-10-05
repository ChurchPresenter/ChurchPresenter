package org.churchpresenter.app.churchpresenter

import org.churchpresenter.sharedui.models.Presenting

/** Whether media is what is on the output, which is what connected phones are told. */
internal fun isMediaLive(slideContent: Presenting): Boolean = slideContent == Presenting.MEDIA

/**
 * The song position to announce, or none when songs are not what is live.
 *
 * A follower given a position while something else is live would jump to a section of a song nobody
 * is singing, so the mode is checked before the index is read rather than after.
 */
internal fun livePositionOrNull(source: Presenting, forMode: Presenting, index: Int): Int? =
    if (source == forMode) index else null

/** A field worth sending, or nothing — an empty string and "not set" mean the same thing on the wire. */
internal fun nullIfEmpty(value: String): String? = value.ifEmpty { null }

/**
 * The canonical verse reference of the selected verse, or none.
 *
 * Only once the verse names a book the loaded bible actually knows: the code is resolved through
 * that bible, so a verse from a translation that is no longer loaded — or a
 * partially-filled verse mid-selection — has no code rather than a wrong one.
 */
internal fun <T> liveVerseCode(
    bookName: String,
    chapter: Int,
    verseNumber: Int,
    bookIdByName: (String) -> Int?,
    codeReference: (bookId: Int, chapter: Int, verse: Int) -> T?,
): T? {
    if (bookName.isEmpty()) return null
    val bookId = bookIdByName(bookName) ?: return null
    return codeReference(bookId, chapter, verseNumber)
}

/** Whether media is what the presentation-live flag should report. */
internal fun isPresentationLive(slideContent: Presenting): Boolean =
    slideContent == Presenting.PRESENTATION

/**
 * The line to select for a section chosen remotely, or none.
 *
 * A remote caller says -1 when it means "the whole section" rather than a line within it, so any
 * negative index is normalised to that rather than passed through as a position.
 */
internal fun remoteSongLineIndex(requestedLineIndex: Int): Int =
    if (requestedLineIndex >= 0) requestedLineIndex else -1

/** Whether taking a song section live also has to switch the output over to lyrics. */
internal fun shouldSwitchToLyrics(slideContent: Presenting): Boolean =
    slideContent != Presenting.LYRICS

/** Whether a section change is worth telling connected phones about — only while songs are live. */
internal fun shouldBroadcastSongSection(slideContent: Presenting): Boolean =
    slideContent == Presenting.LYRICS

/** Whether the output going empty is worth announcing. */
internal fun shouldBroadcastDisplayCleared(slideContent: Presenting): Boolean =
    slideContent == Presenting.NONE

/**
 * Whether a clear signal is a fresh one rather than the value replayed on subscribe.
 *
 * The flow replays its current value to every new subscriber — including on re-subscribe when the
 * role changes — so acting on equality would clear the output every time the link was reconfigured.
 */
internal fun isFreshClearSignal(signal: Int, lastSeen: Int): Boolean = signal != lastSeen
