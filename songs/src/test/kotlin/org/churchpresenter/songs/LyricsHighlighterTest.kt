package org.churchpresenter.songs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The lyrics box's colouring, and that it does not redo work the text has not changed. */
class LyricsHighlighterTest {

    private val inks = LyricsInks(
        verse = Color.Blue,
        chorus = Color.Red,
        chord = Color.Green,
        divider = Color.Gray,
        voice = Color.Magenta,
    )
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

    @Test
    fun `a voice-2 block colours its lines until voice 1 or the next header`() {
        val text = "[voice: 2]\nsung by two\n[voice: 1]\nsung by one\n[voice: 2]\nagain\n[Chorus]\nall"

        val out = highlighter.filter(AnnotatedString(text)).text

        fun inkOf(words: String) = out.spanStyles.firstOrNull { out.text.substring(it.start, it.end) == words }?.item
        assertEquals(Color.Gray, inkOf("[voice: 2]")?.color, "a directive reads as one")
        assertEquals(Color.Magenta, inkOf("sung by two")?.color)
        assertEquals(null, inkOf("sung by one"))
        assertEquals(Color.Magenta, inkOf("again")?.color)
        assertEquals(null, inkOf("all"), "a header closes the block")
    }

    @Test
    fun `a 2 mark colours its whole line`() {
        val line = highlighter.highlight("  2>That saved")

        assertEquals("  2>That saved", line.text)
        val words = line.spanStyles.single { line.text.substring(it.start, it.end) == "That saved" }
        assertEquals(Color.Magenta, words.item.color)
    }

    @Test
    fun `marked words are coloured and the rest of the line is not`() {
        val line = highlighter.highlight("Was 2>(blind) but 2>(now I see")

        assertEquals("Was 2>(blind) but 2>(now I see", line.text)
        val voiced = line.spanStyles
            .filter { it.item.color == Color.Magenta }
            .map { line.text.substring(it.start, it.end) }
        assertTrue("blind" in voiced && "now I see" in voiced, "$voiced")
        assertTrue(")" in voiced, "a closing bracket is a mark")
        assertTrue(voiced.none { "but" in it })
    }

    @Test
    fun `text after the last marked words is left plain`() {
        val line = highlighter.highlight("2>(Now) I see")

        assertEquals(" I see", line.text.substring(line.spanStyles.maxOf { it.end }))
    }

    @Test
    fun `a long run of different lines starts the cache over and still colours right`() {
        val text = (0..2_001).joinToString("\n") { "line $it" }
        highlighter.filter(AnnotatedString(text))

        val out = highlighter.filter(AnnotatedString("[C]la")).text

        assertEquals(Color.Green, out.spanStyles.single().item.color)
    }
}
