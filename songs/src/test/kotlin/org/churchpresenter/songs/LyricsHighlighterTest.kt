package org.churchpresenter.songs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The lyrics box's colouring, and that it does not redo work the text has not changed. */
class LyricsHighlighterTest {

    private val inks = LyricsInks(verse = Color.Blue, chorus = Color.Red, chord = Color.Green, divider = Color.Gray)
    private val highlighter = LyricsHighlighter(inks)

    @Test
    fun `the text reads the same, with headers and chords coloured`() {
        val text = "[Chorus]\n[G]Amazing [not a chord] grace\n---"

        val out = highlighter.filter(AnnotatedString(text)).text

        assertEquals(text, out.text)
        assertEquals(Color.Red, out.spanStyles.first { it.start == 0 }.item.color)
        val chord = out.spanStyles.single { out.text.substring(it.start, it.end) == "[G]" }
        assertEquals(Color.Green, chord.item.color)
        assertTrue(out.spanStyles.none { out.text.substring(it.start, it.end) == "[not a chord]" })
    }

    @Test
    fun `a verse header takes the verse ink`() {
        val line = highlighter.highlight("[Verse 2]")

        assertEquals(Color.Blue, line.spanStyles.single().item.color)
    }

    @Test
    fun `an unchanged text is answered from the last result`() {
        val first = highlighter.filter(AnnotatedString("[C]la\nla"))

        assertSame(first, highlighter.filter(AnnotatedString("[C]la\nla")))
    }

    @Test
    fun `a changed text is coloured afresh`() {
        highlighter.filter(AnnotatedString("[C]la"))

        val out = highlighter.filter(AnnotatedString("[C]la\n[D]lo")).text

        assertEquals("[C]la\n[D]lo", out.text)
        assertEquals(2, out.spanStyles.count { it.item.color == Color.Green })
    }
}
