package org.churchpresenter.bibletab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `"N. text"` verse-line convention, extracted out of BibleTab/BibleViewModel where it was
 * re-parsed inline at a dozen sites. These are the invariants every one of those sites relied on;
 * a change here would silently mis-number verses, mislabel copied references, or mis-log a passage
 * span for CCLI reporting.
 */
class VerseLineTest {

    // ── verseNumberOf ─────────────────────────────────────────────────────────

    @Test fun `verse number is the leading integer before the dot-space`() =
        assertEquals(16, verseNumberOf("16. For God so loved the world"))

    @Test fun `verse number is null when the line has no leading number`() =
        assertNull(verseNumberOf("For God so loved the world"))

    @Test fun `verse number tolerates a number-only remainder`() =
        assertEquals(3, verseNumberOf("3. "))

    @Test fun `verse number is null for a blank line`() =
        assertNull(verseNumberOf(""))

    // ── verseTextOf ───────────────────────────────────────────────────────────

    @Test fun `verse text is what follows the dot-space`() =
        assertEquals("For God so loved the world", verseTextOf("16. For God so loved the world"))

    @Test fun `verse text falls back to the whole line when there is no dot-space`() =
        assertEquals("just text", verseTextOf("just text"))

    @Test fun `verse text honours an explicit empty fallback`() =
        assertEquals("", verseTextOf("just text", fallback = ""))

    // ── indexOfFirstLiveVerse ─────────────────────────────────────────────────

    private val chapter = listOf("1. alpha", "2. beta", "3. gamma", "4. delta")

    @Test fun `first live verse is the earliest line whose number is live`() =
        assertEquals(1, indexOfFirstLiveVerse(chapter, setOf(2, 4)))

    @Test fun `first live verse is minus one when none are live`() =
        assertEquals(-1, indexOfFirstLiveVerse(chapter, setOf(9)))

    @Test fun `first live verse ignores lines that carry no number`() =
        assertEquals(1, indexOfFirstLiveVerse(listOf("intro", "2. beta"), setOf(2)))

    // ── nextLiveVerseNumber ───────────────────────────────────────────────────

    @Test fun `next verse steps down to the following line`() =
        assertEquals(3, nextLiveVerseNumber(chapter, refVerse = 2, moveUp = false))

    @Test fun `next verse steps up to the preceding line`() =
        assertEquals(1, nextLiveVerseNumber(chapter, refVerse = 2, moveUp = true))

    @Test fun `stepping up from the first line stays on it`() =
        assertEquals(1, nextLiveVerseNumber(chapter, refVerse = 1, moveUp = true))

    @Test fun `stepping down from the last line stays on it`() =
        assertEquals(4, nextLiveVerseNumber(chapter, refVerse = 4, moveUp = false))

    @Test fun `an unknown reference starts from the top`() =
        assertEquals(2, nextLiveVerseNumber(chapter, refVerse = 99, moveUp = false))

    @Test fun `no next verse in an empty chapter`() =
        assertNull(nextLiveVerseNumber(emptyList(), refVerse = 1, moveUp = false))

    // ── filteredSelectionIndices ──────────────────────────────────────────────

    // The full chapter, and a filter that hides verse 2 ("beta").
    private val allVerses = listOf("1. alpha", "2. beta", "3. gamma", "4. delta")
    private val filtered = listOf("1. alpha", "3. gamma", "4. delta")

    @Test fun `selections map to their positions in the filtered list`() =
        assertEquals(setOf(0, 2), filteredSelectionIndices(listOf(0, 3), allVerses, filtered))

    @Test fun `a selection hidden by the filter is dropped`() =
        // real index 1 ("2. beta") isn't in the filtered list, so only index 0 survives.
        assertEquals(setOf(0), filteredSelectionIndices(listOf(0, 1), allVerses, filtered))

    @Test fun `no selections yields null, not an empty set`() =
        assertNull(filteredSelectionIndices(emptyList(), allVerses, filtered))

    @Test fun `selections all hidden by the filter yield null`() =
        assertNull(filteredSelectionIndices(listOf(1), allVerses, filtered))

    @Test fun `an out-of-range selection index is ignored`() =
        assertEquals(setOf(0), filteredSelectionIndices(listOf(0, 99), allVerses, filtered))

    // ── formatVerseReference ──────────────────────────────────────────────────

    @Test fun `reference includes the verse number when present`() =
        assertEquals("John 3:16", formatVerseReference("16. For God…", "John", 3))

    @Test fun `reference omits the colon and number when the line has none`() =
        assertEquals("John 3", formatVerseReference("no number here", "John", 3))

    // ── verseSpan ─────────────────────────────────────────────────────────────

    @Test fun `span of a hyphen range is its endpoints`() =
        assertEquals(1 to 3, verseSpan("1-3", fallbackVerse = 1))

    @Test fun `span of a comma list is its min and max`() =
        assertEquals(2 to 5, verseSpan("2,4,5", fallbackVerse = 2))

    @Test fun `span of a single-number range has a null end`() =
        assertEquals(7 to null, verseSpan("7", fallbackVerse = 1))

    @Test fun `a blank range falls back to the single fallback verse`() =
        assertEquals(16 to null, verseSpan("", fallbackVerse = 16))

    @Test fun `an unparseable range falls back to the single fallback verse`() =
        assertEquals(9 to null, verseSpan("abc", fallbackVerse = 9))
}
