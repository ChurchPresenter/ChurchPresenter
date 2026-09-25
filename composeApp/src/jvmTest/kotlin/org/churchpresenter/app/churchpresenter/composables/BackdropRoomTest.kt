package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much room a backdrop needs outside the text it backs.
 *
 * `drawTextBackdrop` deliberately does not clamp its plate to the text's own box -- clamping would
 * eat the padding on every side the text reaches, leaving the outline sitting on the first letter.
 * Its comment names the condition that makes that safe: *only an ancestor that clips would cut it
 * off*. Both presenters clip, and their text fills the clipped box, so the condition did not hold:
 * a bordered backdrop on left-aligned text -- **the Bible's default** -- was shaved off at the left,
 * and a line wide enough to reach both edges lost its box entirely, leaving two horizontal rules.
 *
 * Found by photographing two translations side by side, which is the view that made it obvious.
 * The text is inset by this much now, so the plate draws into room that is already inside the clip.
 */
class BackdropRoomTest {

    @Test
    fun `no backdrop needs no room`() {
        assertEquals(0f, TextBackdrop().outsetSp)
    }

    @Test
    fun `a border needs its padding and its whole stroke`() {
        // `drawBlockBacking` pads by `borderPadding + stroke / 2` and then strokes outward to that
        // edge, so the full stroke width has to be clear, not half of it.
        val backdrop = TextBackdrop(border = true, borderPadding = 18, borderWidth = 6)
        assertEquals(24f, backdrop.outsetSp)
    }

    @Test
    fun `a line band needs whichever way it was grown`() {
        val wide = TextBackdrop(lineBackground = true, lineBackgroundWidth = 20, lineBackgroundHeight = 4)
        assertEquals(20f, wide.outsetSp)

        val tall = TextBackdrop(lineBackground = true, lineBackgroundWidth = 3, lineBackgroundHeight = 15)
        assertEquals(15f, tall.outsetSp)
    }

    @Test
    fun `a band slid off its line needs room for the slide as well`() {
        // The offset moves the band away from the text, so the far edge travels with it -- and it
        // can be negative, which moves it the other way and needs the same clearance.
        val down = TextBackdrop(lineBackground = true, lineBackgroundHeight = 4, lineBackgroundOffset = 12)
        assertEquals(16f, down.outsetSp)

        val up = TextBackdrop(lineBackground = true, lineBackgroundHeight = 4, lineBackgroundOffset = -12)
        assertEquals(16f, up.outsetSp, "a negative offset needs the same room as a positive one")
    }

    @Test
    fun `a bordered plate takes whichever of the two reaches further`() {
        val backdrop = TextBackdrop(
            border = true,
            borderPadding = 30,
            borderWidth = 4,
            lineBackground = true,
            lineBackgroundHeight = 6,
        )
        assertTrue(backdrop.outsetSp >= 34f, "the box reaches further than the band and must win")
    }

    @Test
    fun `the default border needs real room, which is what made this a default-configuration bug`() {
        // Nobody has to configure anything unusual to hit it: switching the border on at its stored
        // defaults already asks for room the text did not have.
        assertTrue(TextBackdrop(border = true).outsetSp > 0f)
    }
}
