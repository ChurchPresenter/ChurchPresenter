package org.churchpresenter.app.churchpresenter.presenter

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a Bible band's text layers say at a given frame: the ticker's one scrolling line per slot, and
 * how much of a verse a typewriter has typed -- growing through `text_in`, whole through the hold,
 * shrinking back through `text_out`.
 *
 * The template is a real one parsed from JSON, its five segments a fifth of 90 frames each, so every
 * boundary below can be read straight off the marker list.
 */
class BibleLottieBandTextTest {

    private fun template(motion: String) = requireNotNull(
        parseBibleLottieTemplate(
            """{"fr":30,"op":90,"w":1920,"h":1080,"cp":{"textAnimation":"$motion"},"markers":[
                {"cm":"bg_in","tm":0,"dr":18},{"cm":"text_in","tm":18,"dr":18},{"cm":"hold","tm":36,"dr":18},
                {"cm":"text_out","tm":54,"dr":18},{"cm":"bg_out","tm":72,"dr":18}]}""",
        ),
    )

    private val verse = "In the beginning God created"

    // ── Ticker ──────────────────────────────────────────────────────────────────

    @Test
    fun `a ticker carries each slot's reference at the head of its own line and empties the reference layer`() {
        val texts = tickerTexts(
            mapOf(
                "Text1" to verse, "Reference1" to "Genesis 1:1",
                "Text2" to "The LORD is my shepherd", "Reference2" to "Psalm 23:1",
                "Logo" to "Church",
            ),
        )

        assertEquals("Genesis 1:1${TICKER_GAP}$verse", texts["Text1"])
        assertEquals(
            "Psalm 23:1${TICKER_GAP}The LORD is my shepherd",
            texts["Text2"],
            "a second slot ticks the same way",
        )
        assertEquals("", texts["Reference1"], "nothing sits still beside the motion")
        assertEquals("", texts["Reference2"])
        assertEquals("Church", texts["Logo"], "a layer that is neither is left as it is")
    }

    @Test
    fun `a ticker line with no reference, or no text, is just the text`() {
        assertEquals(verse, tickerLine(null, verse))
        assertEquals(verse, tickerLine("  ", verse))
        assertEquals("", tickerLine("Genesis 1:1", ""))
        assertEquals(mapOf("Text1" to verse), tickerTexts(mapOf("Text1" to verse)), "a slot with no reference layer")
    }

    // ── Typewriter ──────────────────────────────────────────────────────────────

    @Test
    fun `a typewriter shows nothing before text_in, grows through it, and holds the whole verse`() {
        val t = template("TYPEWRITER")

        assertEquals("", revealedText(t, verse, 10f), "the band is still coming in")
        assertEquals(verse.take(verse.length / 2), revealedText(t, verse, 27f), "half way through text_in")
        assertEquals(verse, revealedText(t, verse, 36f))
        assertEquals(verse, revealedText(t, verse, 45f), "the hold")
    }

    @Test
    fun `a typewriter un-types through text_out and is empty once the band is going`() {
        val t = template("TYPEWRITER")

        assertEquals(verse.take(verse.length / 2), revealedText(t, verse, 63f), "half way back out")
        assertEquals("", revealedText(t, verse, 80f))
    }

    @Test
    fun `a word typewriter reveals whole words, never half of one`() {
        val t = template("TYPEWRITER_WORDS")

        assertEquals("In the beginning", revealedText(t, verse, 27f), "three of five words half way in")
        assertEquals(verse, revealedText(t, verse, 45f))
        assertEquals("In the beginning", revealedText(t, verse, 63f), "and three half way out")
    }

    @Test
    fun `keyframed and ticker bands show the whole verse and let the file do the moving`() {
        assertEquals(verse, revealedText(template("NONE"), verse, 10f))
        assertEquals(verse, revealedText(template("TICKER"), verse, 10f))
    }
}
