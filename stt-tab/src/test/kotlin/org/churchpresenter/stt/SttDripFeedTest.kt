package org.churchpresenter.stt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The character arithmetic behind the letter-by-letter caption reveal.
 *
 * What matters to the room: the reveal advances at the configured pace, never re-types words that
 * have already been read, and never falls so far behind live speech that the caption stops being
 * the sentence being spoken. Every rule here is one of those three, and every one is exercised
 * against the caption string the presenter actually draws — [captionText] is the same joining
 * `buildDisplayText` renders, so a cursor position always means the same thing on both sides.
 */
class SttDripFeedTest {

    private fun segment(text: String, id: Int = 1) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private val blessed = listOf(segment("Blessed are", id = 1), segment("the peacemakers", id = 2))

    // ── captionText ─────────────────────────────────────────────────────────────

    @Test
    fun `segments are joined into the caption the presenter draws`() {
        assertEquals("Blessed are the peacemakers", captionText(blessed))
    }

    @Test
    fun `ragged whitespace from the transcriber is collapsed`() {
        val ragged = listOf(segment("  Blessed   are \n"), segment(" the  peacemakers ", id = 2))
        assertEquals(
            "Blessed are the peacemakers",
            captionText(ragged),
            "the cursor counts characters of the drawn caption, so both sides must normalise alike",
        )
    }

    @Test
    fun `blank segments take up no room in the caption`() {
        val withBlank = listOf(segment("Blessed are"), segment("   ", id = 2), segment("the peacemakers", id = 3))
        assertEquals("Blessed are the peacemakers", captionText(withBlank))
    }

    // ── applyRevealBudget ───────────────────────────────────────────────────────

    @Test
    fun `a cursor mid-word truncates that segment and drops the rest`() {
        val shown = applyRevealBudget(blessed, revealed = 5)
        assertEquals("Bless", captionText(shown))
    }

    @Test
    fun `a cursor exactly on a segment boundary stops before the joining space`() {
        val shown = applyRevealBudget(blessed, revealed = 11)
        assertEquals("Blessed are", captionText(shown))
        assertEquals(1, shown.size, "the next segment has not been reached yet")
    }

    @Test
    fun `a cursor past the boundary spends one character on the joining space`() {
        val shown = applyRevealBudget(blessed, revealed = 13)
        assertEquals(
            "Blessed are t",
            captionText(shown),
            "13 revealed characters must be 13 characters of caption, space included",
        )
    }

    @Test
    fun `a cursor at the end returns the segments untouched`() {
        assertSame(
            blessed,
            applyRevealBudget(blessed, revealed = captionText(blessed).length + 50),
            "once caught up the reveal must not allocate a new list every frame",
        )
    }

    @Test
    fun `a cursor at zero shows nothing`() {
        assertEquals("", captionText(applyRevealBudget(blessed, revealed = 0)))
    }

    @Test
    fun `every cursor position renders exactly that many characters`() {
        val full = captionText(blessed)
        for (cursor in 0..full.length) {
            assertEquals(
                // trimEnd: a cursor landing exactly on a joining space draws the words either side
                // of it identically whether or not the space itself is emitted.
                full.take(cursor).trimEnd(),
                captionText(applyRevealBudget(blessed, cursor)),
                "cursor $cursor must draw the first $cursor characters of the caption",
            )
        }
    }

    // ── reanchorCursor ──────────────────────────────────────────────────────────

    @Test
    fun `a newly arrived segment lets the reveal carry straight on`() {
        assertEquals(
            5,
            reanchorCursor("Blessed are", prevRevealed = 5, newFull = "Blessed are the peacemakers"),
            "an append must not restart the reveal — that is the bug that made the speed setting inert",
        )
    }

    @Test
    fun `text dropped off the front of the window shifts the cursor with it`() {
        // Shown was "Blessed are the peac"; the window has since dropped "Blessed are ", so
        // "the peac" — 8 characters — is what stays on screen.
        assertEquals(
            8,
            reanchorCursor("Blessed are the peacemakers", prevRevealed = 20, newFull = "the peacemakers"),
            "the same words stay revealed after the rolling window trims older segments",
        )
    }

    @Test
    fun `a window that scrolled past everything shown reveals the rest properly`() {
        assertEquals(
            0,
            reanchorCursor("Blessed are the peacemakers", prevRevealed = 5, newFull = "peacemakers and more"),
            "none of this has been read yet, so it must be typed out rather than dumped on screen",
        )
    }

    @Test
    fun `a caption rewritten past recognition jumps to the end rather than re-typing`() {
        assertEquals(
            "Rejoice and be glad".length,
            reanchorCursor("Blessed are the peacemakers", prevRevealed = 20, newFull = "Rejoice and be glad"),
            "showing everything at once beats making the room read the same words twice",
        )
    }

    @Test
    fun `an empty caption starts the next reveal from the beginning`() {
        assertEquals(0, reanchorCursor("", prevRevealed = 0, newFull = "Blessed are"))
    }

    // ── revealStep ──────────────────────────────────────────────────────────────

    @Test
    fun `a reveal that is keeping up draws one character per tick`() {
        assertEquals(1, revealStep(revealed = 20, target = 27), "the configured speed is the speed")
    }

    @Test
    fun `a reveal left far behind live speech draws faster to catch up`() {
        assertTrue(
            revealStep(revealed = 0, target = 2_000) > 1,
            "a caption that keeps drifting behind the speaker stops being the caption for what is said",
        )
    }

    @Test
    fun `a caught-up reveal never steps backwards`() {
        assertEquals(1, revealStep(revealed = 40, target = 10), "a shrinking caption must not produce a negative step")
    }
}
