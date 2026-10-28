package org.churchpresenter.stt.presenter

import org.churchpresenter.core.models.stt.STTSegment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The drip feed's cursor followed by segment id: wherever its segment now is in the caption, the
 * cursor keeps its place in it, and nothing above it is re-typed or revealed early.
 */
class SttDripCursorTest {

    private fun segment(text: String, id: Int) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    // "Blessed are" (11) + " " + "the peacemakers" (15) = 27 characters
    private val blessed = listOf(segment("Blessed are", id = 1), segment("the peacemakers", id = 2))

    // ── reanchorCursor ──────────────────────────────────────────────────────────

    @Test
    fun `an appended segment leaves the cursor where it was`() {
        val more = blessed + segment("for they shall", id = 3)

        assertEquals(17, reanchorCursor(blessed, prevRevealed = 17, new = more))
    }

    @Test
    fun `the oldest segment leaving the front moves the cursor back with its own`() {
        // 5 characters into "the peacemakers"
        val trimmed = listOf(segment("the peacemakers", id = 2), segment("for they shall", id = 3))

        assertEquals(5, reanchorCursor(blessed, prevRevealed = 17, new = trimmed))
    }

    @Test
    fun `the cursor's own segment leaving the front reveals what is left from its start`() {
        val trimmed = listOf(segment("for they shall", id = 3))

        assertEquals(0, reanchorCursor(blessed, prevRevealed = 17, new = trimmed))
    }

    @Test
    fun `a correction above the cursor is swapped in and the cursor keeps its place in its own segment`() {
        val corrected = listOf(segment("Blessed indeed are", id = 1), segment("the peacemakers", id = 2))

        // 5 characters into "the peacemakers", now after 18 + 1
        assertEquals(24, reanchorCursor(blessed, prevRevealed = 17, new = corrected))
    }

    @Test
    fun `the cursor's own segment corrected keeps what it still begins with`() {
        val corrected = listOf(segment("Blessed are", id = 1), segment("the merciful", id = 2))

        // "the peacemakers" was typed to "the peace"; "the merciful" still begins "the "
        assertEquals(16, reanchorCursor(blessed, prevRevealed = 21, new = corrected))
    }

    @Test
    fun `a cursor on the space between two segments is at the start of the second`() {
        val trimmed = listOf(segment("the peacemakers", id = 2))

        assertEquals(0, reanchorCursor(blessed, prevRevealed = 12, new = trimmed))
    }

    @Test
    fun `a cursor at the end stays at the end of what was shown when more arrives`() {
        val more = blessed + segment("for they shall", id = 3)

        assertEquals(27, reanchorCursor(blessed, prevRevealed = 27, new = more))
    }

    @Test
    fun `the first segment past where the cursor was is revealed from its start`() {
        // The cursor's segment is gone but an older one is still there: everything past it is unread
        val moved = listOf(segment("Blessed are", id = 1), segment("for they shall", id = 3))

        assertEquals(11, reanchorCursor(blessed, prevRevealed = 17, new = moved))
    }

    @Test
    fun `nothing revealed stays nothing revealed`() {
        assertEquals(0, reanchorCursor(blessed, prevRevealed = 0, new = blessed + segment("more", id = 3)))
    }

    @Test
    fun `without unique ids the text is compared instead`() {
        val same = listOf(segment("Blessed are", id = 0), segment("the peacemakers", id = 0))

        assertEquals(17, reanchorCursor(same, prevRevealed = 17, new = same + segment("more", id = 0)))
    }

    // ── throughCursor ───────────────────────────────────────────────────────────

    @Test
    fun `the segments through the one being typed are kept whole`() {
        val three = blessed + segment("for they shall", id = 3)

        assertEquals(listOf(1, 2), throughCursor(three, revealed = 17).map { it.id })
        assertEquals("the peacemakers", throughCursor(three, revealed = 17).last().text)
    }

    @Test
    fun `a segment the cursor has not reached is left out`() {
        // 11 is the end of "Blessed are"; the space after it is not yet typed
        assertEquals(listOf(1), throughCursor(blessed, revealed = 11).map { it.id })
    }

    @Test
    fun `a segment without words takes no room`() {
        val gap = listOf(segment("Blessed are", id = 1), segment("  ", id = 2), segment("the peacemakers", id = 3))

        assertEquals(listOf(1, 2, 3), throughCursor(gap, revealed = 17).map { it.id })
    }

    @Test
    fun `nothing revealed keeps nothing`() {
        assertEquals(emptyList(), throughCursor(blessed, revealed = 0))
    }
}
