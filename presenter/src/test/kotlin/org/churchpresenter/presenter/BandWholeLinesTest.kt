package org.churchpresenter.presenter

import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import org.churchpresenter.lottiegen.lottie.ShapedLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The band's own placement of text drawn as whole lines: the padding before each line, where that
 * puts it, and what the player is told so it does not place the line again.
 */
class BandWholeLinesTest {

    /** Every character ten wide at the base size, a space five. */
    private val shaped = ShapedLines(wordWidth = { it.length * 10f }, spaceWidth = 5f)
    private val box = LottieSlotBox(x = 100f, y = 0f, w = 200f, h = 100f)

    @Test
    fun `each line is padded and placed by its own width`() {
        val centred = placeLines(shaped, listOf("aaaa", "aa"), box, TextJustify.Center, scale = 1f)
        assertEquals(listOf(" ".repeat(16), " ".repeat(18)), centred.pads)
        assertEquals(listOf(180f, 190f), centred.lefts)

        val right = placeLines(shaped, listOf("aaaa"), box, TextJustify.Right, scale = 1f)
        assertEquals(listOf(260f), right.lefts)
        val left = placeLines(shaped, listOf("aaaa"), box, TextJustify.Left, scale = 1f)
        assertEquals(listOf(""), left.pads)
        assertEquals(listOf(100f), left.lefts)
    }

    @Test
    fun `a fitted size places the line in template pixels with the padding counted at the base size`() {
        // At half size the box is 400 wide at the base size: a 40-wide line leaves 180 each side.
        val placed = placeLines(shaped, listOf("aaaa"), box, TextJustify.Center, scale = 0.5f)
        assertEquals(listOf(" ".repeat(36)), placed.pads)
        assertEquals(listOf(190f), placed.lefts)
    }

    @Test
    fun `the player draws placed lines from the left in a box too wide to wrap again`() {
        val placed = PlacedLines(pads = listOf(""), lefts = listOf(100f))

        assertEquals(TextJustify.Left, playerJustify(TextJustify.Center, ticker = false, placed = placed))
        assertEquals(TextJustify.Left, playerJustify(TextJustify.Right, ticker = true, placed = null))
        assertEquals(TextJustify.Right, playerJustify(TextJustify.Right, ticker = false, placed = null))

        assertEquals(1_000_000f, playerBox(box, ticker = false, placed = placed, lineWidth = 50f).w)
        assertEquals(250f, playerBox(box, ticker = true, placed = null, lineWidth = 50f).w, "a ticker's whole line")
        assertSame(box, playerBox(box, ticker = false, placed = null, lineWidth = 50f))
    }

    @Test
    fun `the second voice measures a run shaped or letter by letter, at the fitted size`() {
        assertEquals(22.5f, voiceRunWidth(shaped, "aa bb", scale = 0.5f) { 99f }("aa bb"))

        val letters = voiceRunWidth(null, "ab", scale = 2f) { c -> if (c == 'a') 3f else 4f }
        assertEquals(14f, letters("ab"))
        assertEquals(0f, letters("z"), "a character the slot's text never had")
    }

    @Test
    fun `whole-line placement leaves no padding for a line that is not there`() {
        val placed = placeLines(shaped, emptyList(), box, TextJustify.Center, scale = 1f)
        assertEquals(emptyList(), placed.pads)
        assertNull(placed.lefts.firstOrNull())
    }
}
