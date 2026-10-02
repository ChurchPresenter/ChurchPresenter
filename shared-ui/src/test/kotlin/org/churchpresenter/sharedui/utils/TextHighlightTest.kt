package org.churchpresenter.sharedui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search-result highlight span finder, extracted from BibleTab's `buildAnnotatedString` loop.
 * A wrong span slices the wrong characters (or throws), so the invariants the View relies on are:
 * case-insensitive matching, every occurrence found left-to-right and non-overlapping, and a blank
 * query producing no spans (an empty query would otherwise make `indexOf` loop forever).
 */
class TextHighlightTest {

    @Test
    fun `a single occurrence yields one span at its position`() =
        assertEquals(listOf(6 to 11), highlightRanges("John: grace abounds", "grace"))

    @Test
    fun `every occurrence is found, left to right and non-overlapping`() =
        assertEquals(listOf(0 to 3, 4 to 7), highlightRanges("aba aba", "aba"))

    @Test
    fun `matching is case-insensitive but spans index the original text`() =
        assertEquals(listOf(0 to 5, 6 to 11, 12 to 17), highlightRanges("Grace GRACE grace", "grace"))

    @Test
    fun `adjacent repeats do not overlap`() =
        assertEquals(listOf(0 to 2, 2 to 4), highlightRanges("aaaa", "aa"))

    @Test
    fun `a blank query yields no spans`() {
        assertTrue(highlightRanges("anything at all", "").isEmpty())
        assertTrue(highlightRanges("anything at all", "   ").isEmpty(), "a whitespace-only query is blank once trimmed")
    }

    @Test
    fun `a query that does not occur yields no spans`() =
        assertTrue(highlightRanges("Genesis 1:1", "beginning").isEmpty())

    @Test
    fun `the query is trimmed before matching`() =
        assertEquals(listOf(6 to 11), highlightRanges("John: grace abounds", "  grace  "))

    @Test
    fun `every returned span is a valid slice of the text`() {
        val text = "grace upon grace upon grace"
        for ((start, end) in highlightRanges(text, "grace")) {
            assertTrue(start in 0..end && end <= text.length, "span $start..$end must be sliceable")
            assertEquals("grace", text.substring(start, end).lowercase())
        }
    }
}
