package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.bible.SelectedVerse
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Showing a long verse as two halves instead of one shrunken block.
 *
 * `BiblePresenter` never cuts scripture off -- it shrinks until the whole of it fits (issue #97) --
 * so the longest verses in the Bible arrive on screen unreadably small, which is worst over a busy
 * background image. With `BibleSettings.splitLongVerses` on, a verse past
 * `BibleSettings.longVerseWordCount` words is shown as two pages instead, and the existing
 * next/previous-verse keys step through them before moving on to the next verse.
 *
 * The break is taken at the word boundary nearest the middle **by character count**, not by word
 * count: halves of equal word count are not halves of equal length, and the point of the setting is
 * that neither page is the one that still has to shrink.
 *
 * Which page is showing is tied to one publication of the selection -- see [currentVersePage] --
 * so anything that selects something else starts from a first half on its own.
 */

/**
 * The tunable range of `BibleSettings.longVerseWordCount`, in words.
 *
 * The floor is not arbitrary: 25 is where Tamil splits about as often as English does at the
 * default of 45 (4.2% of TAM_BSI against 5.5% of the KJV), so a Tamil church sits mid-scale rather
 * than pinned at the end of the track and still splitting five times less. Below that the top of
 * the range would be the footgun -- 20 splits 60.6% of the KJV. The ceiling is where there is
 * nothing left to tune: at 60 the KJV is down to 0.48% and its longest verse is 90 words.
 */
internal const val LONG_VERSE_WORDS_MIN = 25
internal const val LONG_VERSE_WORDS_MAX = 60

/** The slider's increment. Five words is finer than the measured difference between two settings. */
internal const val LONG_VERSE_WORDS_STEP = 5

/**
 * The stop below [LONG_VERSE_WORDS_MIN] at which splitting is off altogether.
 *
 * The setting is one slider rather than a checkbox and a slider, so "never split" has to be a
 * position on the track. It writes `BibleSettings.splitLongVerses = false` and leaves the word count
 * alone, which is what lets the operator turn splitting off and back on without losing the number
 * they had tuned.
 */
internal const val LONG_VERSE_WORDS_OFF = LONG_VERSE_WORDS_MIN - LONG_VERSE_WORDS_STEP

/**
 * Where the threshold slider's handle sits for a stored setting.
 *
 * Splitting turned off is a *position* on this slider rather than a checkbox beside it, so "off" has
 * to be expressible as a number; it is [LONG_VERSE_WORDS_OFF], one step below the usable range.
 */
internal fun longVerseSliderPosition(splitting: Boolean, wordCount: Int): Int =
    if (splitting) wordCount.coerceIn(LONG_VERSE_WORDS_MIN, LONG_VERSE_WORDS_MAX) else LONG_VERSE_WORDS_OFF

/**
 * What dropping the handle at [raw] should store: whether splitting is on, and the word count.
 *
 * At the Off stop the count returned is [currentWordCount] unchanged, so turning splitting off and
 * back on returns the operator to the threshold they had tuned rather than to the default.
 */
internal fun longVerseSliderStop(raw: Float, currentWordCount: Int): Pair<Boolean, Int> {
    val snapped = (raw / LONG_VERSE_WORDS_STEP).roundToInt() * LONG_VERSE_WORDS_STEP
    val splitting = snapped >= LONG_VERSE_WORDS_MIN
    return splitting to if (splitting) snapped.coerceAtMost(LONG_VERSE_WORDS_MAX) else currentWordCount
}

private val WHITESPACE = Regex("\\s+")

/**
 * Whether [text] is long enough to be worth showing as two pages.
 *
 * [wordThreshold] is the operator's setting, clamped -- a hand-edited settings file holding 0 would
 * otherwise split every verse in the Bible, including "Jesus wept".
 */
internal fun isLongVerse(text: String, wordThreshold: Int): Boolean =
    text.split(WHITESPACE).count { it.isNotBlank() } >
        wordThreshold.coerceIn(LONG_VERSE_WORDS_MIN, LONG_VERSE_WORDS_MAX)

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

/** The word count a verse must pass to be split -- likewise read where it is needed. */
internal val BibleViewModel.longVerseWordCount: Int
    get() = appSettings.bibleSettings.longVerseWordCount

/** Whether [text] is shown as two pages, so a caller has a half to step to. */
internal fun BibleViewModel.pagesInTwo(text: String): Boolean =
    splitLongVersesEnabled && isLongVerse(text, longVerseWordCount)

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
internal fun List<SelectedVerse>.versePage(
    page: Int,
    enabled: Boolean,
    wordThreshold: Int,
): List<SelectedVerse> {
    if (!enabled) return this
    val primary = firstOrNull() ?: return this
    if (!isLongVerse(primary.verseText, wordThreshold)) return this
    return map { verse ->
        val (first, second) = splitAtWordMidpoint(verse.verseText)
        verse.copy(verseText = if (page == VERSE_PAGE_SECOND) second else first)
    }
}
