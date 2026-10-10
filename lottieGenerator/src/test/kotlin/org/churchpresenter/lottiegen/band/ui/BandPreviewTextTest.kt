package org.churchpresenter.lottiegen.band.ui

import org.churchpresenter.lottiegen.band.BandTimeline
import org.churchpresenter.lottiegen.band.TextAnimation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BandPreviewTextTest {

    private val t = BandTimeline(
        bgInFrames = 10, textInFrames = 10, holdFrames = 10, textOutFrames = 10, bgOutFrames = 10,
    )

    @Test
    fun `a ticker line leads with the reference only when both halves are there`() {
        assertEquals("Ref    Verse", tickerLine("Ref", "Verse"))
        assertEquals("Verse", tickerLine(null, "Verse"))
        assertEquals("Verse", tickerLine("  ", "Verse"))
        assertEquals(" ", tickerLine("Ref", " "))
    }

    @Test
    fun `the typewriter types in, holds, types out and is gone after`() {
        val text = "abcd"
        assertEquals("", revealedText(TextAnimation.TYPEWRITER, t, text, 5f))
        assertEquals("ab", revealedText(TextAnimation.TYPEWRITER, t, text, 15f))
        assertEquals(text, revealedText(TextAnimation.TYPEWRITER, t, text, 25f))
        assertEquals("ab", revealedText(TextAnimation.TYPEWRITER, t, text, 35f))
        assertEquals("", revealedText(TextAnimation.TYPEWRITER, t, text, 45f))
    }

    @Test
    fun `the word typewriter reveals whole words`() {
        assertEquals("one two", revealedText(TextAnimation.TYPEWRITER_WORDS, t, "one two three four", 15f))
        assertEquals("one two three four", revealedText(TextAnimation.TYPEWRITER_WORDS, t, "one two three four", 25f))
    }

    @Test
    fun `text documents are read only from layers that carry a name and a font size`() {
        val json = """
            {"layers": [
              {"nm": "Text1", "t": {"d": {"k": [{"s": {"s": 42}}]}}},
              {"nm": "NoSize", "t": {"d": {"k": [{"s": {"f": "x"}}]}}},
              {"nm": "NoDoc", "t": {"d": {"k": []}}},
              {"t": {"d": {"k": [{"s": {"s": 1}}]}}},
              3
            ]}
        """.trimIndent()
        val docs = readTextDocuments(json)
        assertEquals(setOf("Text1"), docs.keys)
        assertEquals(42f, docs.getValue("Text1").fontSize)
        assertTrue(readTextDocuments("{}").isEmpty())
        assertTrue(readTextDocuments("not json").isEmpty())
    }
}
