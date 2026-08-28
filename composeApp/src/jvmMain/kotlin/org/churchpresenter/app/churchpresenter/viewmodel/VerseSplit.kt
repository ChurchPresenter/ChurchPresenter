package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.bible.SelectedVerse
import kotlin.math.abs

/**
 * Showing a long verse as two halves instead of one shrunken block.
 *
 * `BiblePresenter` never cuts scripture off -- it shrinks until the whole of it fits (issue #97) --
 * so the longest verses in the Bible arrive on screen unreadably small, which is worst over a busy
 * background image. With `BibleSettings.splitLongVerses` on, a verse past [LONG_VERSE_WORD_COUNT]
 * words is shown as two pages instead, and the existing next/previous-verse keys step through them
 * before moving on to the next verse.
 *
 * The break is taken at the word boundary nearest the middle **by character count**, not by word
 * count: halves of equal word count are not halves of equal length, and the point of the setting is
 * that neither page is the one that still has to shrink.
 *
 * Which page is showing is tied to one publication of the selection -- see [currentVersePage] --
 * so anything that selects something else starts from a first half on its own.
 */

/** How many words a verse needs before it is worth splitting. Esther 8:9, the longest, has 92. */
private const val LONG_VERSE_WORD_COUNT = 45

private val WHITESPACE = Regex("\\s+")

/** Whether [text] is long enough to be worth showing as two pages. */
internal fun isLongVerse(text: String): Boolean =
    text.split(WHITESPACE).count { it.isNotBlank() } > LONG_VERSE_WORD_COUNT

/**
 * [text] broken at the word boundary closest to its middle.
 *
 * Always splits, however short the text: the decision of *whether* to split belongs to the primary
 * translation ([isLongVerse] on the first entry), so that a shorter parallel translation of the same
 * verse pages along with it rather than repeating itself on the second page.
 */
internal fun splitAtWordMidpoint(text: String): Pair<String, String> {
    val trimmed = text.trim()
    val midpoint = trimmed.length / 2
    var breakAt = -1
    trimmed.forEachIndexed { index, character ->
        if (character.isWhitespace() && (breakAt < 0 || abs(index - midpoint) < abs(breakAt - midpoint))) {
            breakAt = index
        }
    }
    if (breakAt <= 0) return trimmed to ""
    return trimmed.substring(0, breakAt).trimEnd() to trimmed.substring(breakAt).trimStart()
}

/** The first of the two pages a split verse is shown as. */
internal const val VERSE_PAGE_FIRST = 0

/** The second of the two pages a split verse is shown as. */
internal const val VERSE_PAGE_SECOND = 1

/**
 * The page showing right now, or the first half whenever the selection has moved on since it was set.
 *
 * **The page is valid for exactly one publication of the selection.** Every path that changes what is
 * selected -- a click, a search hit, a schedule item, a chapter load, auto-follow -- ends by bumping
 * [BibleViewModel._verseSelectionToken], so any of them invalidates the page without having to know
 * this exists. Keying it to the verse instead was wrong in a way worth recording: leaving a verse on
 * its second half and *coming back* to it matched the stored key again and put the operator on the
 * second half, which is what "sometimes jumps to the second part" was.
 */
internal fun BibleViewModel.currentVersePage(): Int =
    if (_versePageToken == _verseSelectionToken.value) _versePage.value else VERSE_PAGE_FIRST

/** Publishes the selection on [page]; the page holds until anything else republishes it. */
internal fun BibleViewModel.publishVersePage(page: Int) {
    _versePage.value = page
    _verseSelectionToken.value++
    _versePageToken = _verseSelectionToken.value
}

/** Whether verses are split at all -- the setting, read where it is needed rather than cached. */
internal val BibleViewModel.splitLongVersesEnabled: Boolean
    get() = appSettings.bibleSettings.splitLongVerses

/** Whether [text] is shown as two pages, so a caller has a half to step to. */
internal fun BibleViewModel.pagesInTwo(text: String): Boolean =
    splitLongVersesEnabled && isLongVerse(text)

/**
 * Steps the verse [text] to its other half, if there is one to step to.
 *
 * False for a short verse, and for the half already showing -- which is what lets both callers treat
 * it as "no half to step to, move to another verse instead".
 */
internal fun BibleViewModel.stepVersePage(text: String, forward: Boolean): Boolean {
    if (!pagesInTwo(text)) return false
    val target = if (forward) VERSE_PAGE_SECOND else VERSE_PAGE_FIRST
    if (currentVersePage() == target) return false
    publishVersePage(target)
    return true
}

/**
 * Publishes the page a verse being *arrived at* should open on: the second half when the operator is
 * moving backwards into a split verse, the first otherwise. Always publishes, so it doubles as the
 * invalidation of whatever page the verse being left was on.
 */
internal fun BibleViewModel.publishLandingPage(text: String, fromBehind: Boolean) {
    publishVersePage(if (fromBehind && pagesInTwo(text)) VERSE_PAGE_SECOND else VERSE_PAGE_FIRST)
}

/**
 * [this] as its [page], when the verse is long enough to split. A no-op otherwise, and a no-op for
 * every verse when the setting is off -- callers pass [enabled] rather than checking themselves.
 */
internal fun List<SelectedVerse>.versePage(page: Int, enabled: Boolean): List<SelectedVerse> {
    if (!enabled) return this
    val primary = firstOrNull() ?: return this
    if (!isLongVerse(primary.verseText)) return this
    return map { verse ->
        val (first, second) = splitAtWordMidpoint(verse.verseText)
        verse.copy(verseText = if (page == VERSE_PAGE_SECOND) second else first)
    }
}
