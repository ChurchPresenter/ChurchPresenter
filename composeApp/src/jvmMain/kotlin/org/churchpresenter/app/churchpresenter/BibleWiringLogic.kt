package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.SelectBibleVerseRequest

/*
 * The Bible decisions the main screen makes while the Bible tab may not be composed: which Bibles the
 * Lookup Engine indexes, when it runs, and when a detected or scheduled verse goes live. Pure.
 */

/**
 * The bible files the lookup engine indexes, as a stable re-index key.
 *
 * Sorted because it is a *set*: the engine restarts — and re-indexes — whenever this changes, so
 * swapping primary↔secondary must produce the same key and leave a running engine alone, while
 * genuinely switching to another translation must produce a different one. Sorting is what makes
 * "same bibles, different order" compare equal.
 *
 * File names only, which is why the call site keys the effect on the storage directory separately:
 * moving to another folder holding the same names does not change this.
 */
internal fun engineBibleFiles(bibleSettings: BibleSettings): List<String> =
    bibleSettings.translationList().map { it.fileName }.sorted()

/**
 * Whether the Bible Lookup Engine should be running.
 *
 * All three have to hold: the engine reads its audio from the speech-to-text feed, so it is useless
 * without one; it is opt-in; and with no bibles to index there is nothing to match against. The
 * else-branch at the call site stops the engine and expires any references already detected, so
 * getting this wrong either leaves stale verses staged or silently stops auto-follow mid-service.
 */
internal fun shouldRunBibleEngine(
    sttConnected: Boolean,
    engineEnabled: Boolean,
    engineBibles: List<String>,
): Boolean = sttConnected && engineEnabled && engineBibles.isNotEmpty()

/**
 * Whether the main window, rather than `BibleTab`, must resolve a clicked schedule verse.
 *
 * Same window as [shouldMainHandleAutoFollow] and the same reasoning, minus the live-content
 * condition: an item the operator clicked is a request to show that verse whatever is on screen
 * now. [bibleTabIndex] is `-1` when the tab is hidden — and a hidden Bible tab is precisely the
 * case where `selectTab` declines to switch, so nothing else would ever resolve the item.
 */
internal fun shouldMainResolveScheduleVerse(
    activeTabIndex: Int,
    bibleTabIndex: Int,
): Boolean = activeTabIndex != bibleTabIndex

/**
 * Whether the main window, rather than the Bible tab, should apply an auto-follow detection.
 *
 * `BibleTab` sits inside `AnimatedContent` and leaves the composition when the operator switches
 * away, taking its own auto-follow handler — and the history, statistics and training-log writes
 * that go with it — with it. This is the stand-in for exactly that window, so both conditions are
 * about not doing the wrong thing while it is gone:
 *
 * - While the Bible tab *is* the active one it owns the detection; handling it here as well would
 *   put the verse live twice and log it twice.
 * - Only when Bible is already the live content. Auto-follow keeps a passage in step with the
 *   speaker; it must never take the screen away from a song or a slide on its own.
 *
 * [bibleTabIndex] is `-1` when the Bible tab is hidden altogether, which can never equal a real
 * [activeTabIndex] — so with the tab hidden the main window always handles it, there being no
 * `BibleTab` in the composition to defer to.
 */
internal fun shouldMainHandleAutoFollow(
    activeTabIndex: Int,
    bibleTabIndex: Int,
    presentingMode: Presenting,
): Boolean = activeTabIndex != bibleTabIndex && presentingMode == Presenting.BIBLE

/**
 * The verses to put on screen for a remote "select bible verse" request.
 *
 * Three situations. A client reading a Bible downloaded onto the phone sets
 * [SelectBibleVerseRequest.useClientText]: its text is shown as sent, under its own translation
 * name, because the operator chose that translation and the screen should show what the phone
 * shows. Otherwise [resolved] is what this machine's own bibles made of the reference; when they
 * made nothing — the phone names the book differently — the request's own
 * [SelectBibleVerseRequest.verseText] is still better than showing nothing at all. In both
 * fallbacks the style profile stays this instance's ([translationFileName]), since that is what
 * the look is keyed to.
 *
 * When it did resolve, the request's [SelectBibleVerseRequest.verseRange] is stamped onto every
 * verse: the local lookup knows the verses but not the span the client asked for, and the range is
 * what the reference line renders from, so dropping it turns "John 3:16-18" into "John 3:16".
 */
internal fun remoteSelectedVerses(
    resolved: List<SelectedVerse>,
    request: SelectBibleVerseRequest,
    translationFileName: String,
    bibleAbbreviation: String,
    bibleName: String,
): List<SelectedVerse> {
    val clientText = request.useClientText && request.verseText.isNotBlank()
    if (resolved.isNotEmpty() && !clientText) {
        return resolved.map { it.copy(verseRange = request.verseRange) }
    }
    return listOf(
        SelectedVerse(
            translationFileName = translationFileName,
            bibleAbbreviation = if (clientText) {
                request.bibleAbbreviation.ifBlank { bibleAbbreviation }
            } else {
                bibleAbbreviation
            },
            bibleName = if (clientText) request.bibleName.ifBlank { bibleName } else bibleName,
            bookName = request.bookName,
            chapter = request.chapter,
            verseNumber = request.verseNumber,
            verseText = request.verseText,
            verseRange = request.verseRange,
            // The canonical book id: from the local lookup when there was one, else as the client
            // sent it — so anything keyed on it keeps working whatever language the book is named in.
            bookId = resolved.firstOrNull()?.bookId ?: request.bookId,
        ),
    )
}

internal fun resolveBookIndex(bookNames: List<String>, requestedBookName: String): Int =
    bookNames.indexOfFirst { it.equals(requestedBookName, ignoreCase = true) }

/**
 * Every verse a remote's request put on screen: the numbers its range names ("16-18", "2,4,5"),
 * or just its verse. A backwards or absurd range falls back to the single verse rather than
 * recording hundreds of plays.
 */
internal fun remoteVerseNumbers(request: SelectBibleVerseRequest): List<Int> {
    val numbers = request.verseRange.split(",").flatMap { part ->
        val bounds = part.split("-").mapNotNull { it.trim().toIntOrNull() }
        when {
            bounds.size == 2 && bounds[1] >= bounds[0] && bounds[1] - bounds[0] < MAX_REMOTE_RANGE ->
                (bounds[0]..bounds[1]).toList()
            bounds.size == 1 -> bounds
            else -> emptyList()
        }
    }.distinct()
    return numbers.ifEmpty { listOf(request.verseNumber) }
}

/** No chapter has more verses than Psalm 119's 176. */
private const val MAX_REMOTE_RANGE = 200

internal fun parseVerseRangeEnd(verseRange: String, verseNumber: Int): Int? {
    val rangeNums = verseRange
        .takeIf { it.isNotBlank() }
        ?.split(",", "-")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() }
    return rangeNums?.max()?.takeIf { it > verseNumber }
}

/**
 * The book id for a resolved book index, or 0 when the book was not found or the bible cannot name
 * it. Zero is the "no book" value the verse payload carries.
 */
internal fun resolveBookIdOrZero(bookIndex: Int, bookIdAt: (Int) -> Int?): Int =
    if (bookIndex >= 0) bookIdAt(bookIndex) ?: 0 else 0

/**
 * Whether a bible announced by the primary invalidates the cached copy. Either signal counts —
 * primary or secondary — because a full replica re-downloads both.
 */
internal fun shouldInvalidateBibleCache(bibleUpdatedSignal: Int, secondaryBibleUpdatedSignal: Int): Boolean =
    bibleUpdatedSignal > 0 || secondaryBibleUpdatedSignal > 0
