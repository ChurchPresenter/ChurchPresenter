package converter.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The RTF control words a real EasyWorship, MediaShout or ProPresenter document uses that a
 * minimal writer never emits.
 *
 * Everything here is about what must *not* reach the screen: a font or colour table read as lyrics
 * puts "Times New Roman;" on the slide, and a `\bin` run read as text puts a page of binary there.
 * The rest is code pages — a Cyrillic song is `\'hh` bytes, and decoding them in the wrong page is
 * the difference between lyrics and mojibake.
 */
class RtfTextEdgeCasesTest {

    private fun rtf(body: String) = RtfText.toPlainText("""{\rtf1\ansi$body}""")

    // ── What is not text ──────────────────────────────────────────────────────

    @Test
    fun `every metadata table is skipped, not read as lyrics`() {
        val skipped = listOf(
            """{\colortbl;\red0\green0\blue0;}""",
            """{\stylesheet{\s0 Normal;}}""",
            """{\info{\title Not a lyric}}""",
            """{\listtable{\list Not a lyric}}""",
            """{\generator Riched20 10.0;}""",
            """{\header Not a lyric}""",
            """{\footer Not a lyric}""",
        )
        skipped.forEach { table ->
            assertEquals("Sung line", rtf("""$table\pard Sung line"""), "leaked from $table")
        }
    }

    @Test
    fun `a destination the reader does not understand contributes nothing`() {
        assertEquals("Sung line", rtf("""{\*\bkmkstart Ignored}\pard Sung line"""))
    }

    @Test
    fun `a binary run is stepped over rather than printed`() {
        assertEquals("after", rtf("""\pard\bin5 12345after"""))
    }

    @Test
    fun `a negative or absent binary length does not move the cursor backwards`() {
        assertEquals("kept", rtf("""\pard\bin-4 kept"""))
        assertEquals("kept", rtf("""\pard\bin kept"""))
    }

    // ── Line and space controls ───────────────────────────────────────────────

    @Test
    fun `every line-ending control word ends the line`() {
        assertEquals("one\ntwo", rtf("""\pard one\par two"""))
        assertEquals("one\ntwo", rtf("""\pard one\line two"""))
        assertEquals("one\ntwo", rtf("""\pard one\sect two"""))
        assertEquals("one\ntwo", rtf("""\pard one\page two"""))
    }

    @Test
    fun `a tab inside a line is kept as one`() {
        assertTrue(rtf("""\pard one\tab two""").contains('\t'))
    }

    @Test
    fun `a non-breaking space is a space`() {
        assertEquals("one two", rtf("""\pard one\~two"""))
    }

    @Test
    fun `optional and non-breaking hyphens are not printed`() {
        assertEquals("praise", rtf("""\pard prai\-se"""))
        assertEquals("praise", rtf("""\pard prai\_se"""))
    }

    @Test
    fun `a backslash before a real newline ends the line`() {
        assertEquals("one\ntwo", rtf("\\pard one\\\ntwo"))
    }

    @Test
    fun `an unknown escape of a single character is dropped`() {
        assertEquals("ab", rtf("""\pard a\+b"""))
    }

    @Test
    fun `a trailing backslash at the very end is not read past`() {
        assertEquals("line", RtfText.toPlainText("""{\rtf1\ansi\pard line\"""))
    }

    // ── Code pages ────────────────────────────────────────────────────────────

    @Test
    fun `the caller's default code page decodes bytes seen before any ansicpg`() {
        assertEquals("Ю", RtfText.toPlainText("""{\rtf1\pard\'de}""", defaultCodePage = 1251))
    }

    @Test
    fun `a document code page applies to the bytes after it`() {
        assertEquals("Ю", rtf("""\ansicpg1251\pard\'de"""))
    }

    @Test
    fun `a Mac Roman document is decoded in its own page`() {
        assertEquals("©", rtf("""\ansicpg10000\pard\'a9"""))
    }

    @Test
    fun `a DOS code page document is decoded in its own page`() {
        assertEquals("Ç", rtf("""\ansicpg437\pard\'80"""))
    }

    @Test
    fun `a code page the JVM has never heard of falls back rather than throwing`() {
        assertEquals("A", rtf("""\ansicpg99999\pard\'41"""))
    }

    @Test
    fun `selecting a font outside the font table adopts that font's code page`() {
        val document = """{\rtf1\ansi{\fonttbl{\f0\fnil\fcharset0 Arial;}{\f1\fnil\fcharset204 Arial Cyr;}}""" +
            """\pard\f1\'cf\'f0\'e8\'ef\'e5\'e2}"""
        assertEquals("Припев", RtfText.toPlainText(document))
    }

    @Test
    fun `a charset for a font that was never opened is ignored`() {
        assertEquals("A", rtf("""\fcharset204\pard\'41"""))
    }

    @Test
    fun `a charset with no code page of its own leaves the document page alone`() {
        val document = """{\rtf1\ansi{\fonttbl{\f0\fnil\fcharset199 Odd;}}\pard\f0\'41}"""
        assertEquals("A", RtfText.toPlainText(document))
    }

    // ── Unicode escapes ───────────────────────────────────────────────────────

    @Test
    fun `a unicode escape skipping several fallback bytes steps over all of them`() {
        assertEquals("Ф!", rtf("""\pard\uc2\u1060\'3f\'3f!"""))
    }

    @Test
    fun `a negative skip count is treated as none`() {
        assertEquals("Ф?", rtf("""\pard\uc-1\u1060?"""))
    }

    @Test
    fun `a unicode escape inside an ignored destination is not printed`() {
        assertEquals("kept", rtf("""{\*\bkmkstart\u1060 ?}\pard kept"""))
    }

    @Test
    fun `a control word with no parameter where one is needed is passed over`() {
        assertEquals("kept", rtf("""\pard\u\uc\ansicpg kept"""))
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    @Test
    fun `a closing brace with no opening one does not lose the rest of the document`() {
        assertEquals("kept", RtfText.toPlainText("""{\rtf1\ansi\pard}}kept"""))
    }

    @Test
    fun `the font table only ignores the group it opened`() {
        val document = """{\rtf1\ansi{\fonttbl{\f0\fnil Arial;}}\pard Sung line}"""
        assertEquals("Sung line", RtfText.toPlainText(document))
    }

    // ── Not RTF at all ────────────────────────────────────────────────────────

    @Test
    fun `bare text stored where RTF was expected is kept as it is`() {
        assertEquals("Amazing grace", RtfText.toPlainText("Amazing grace"))
        assertEquals("", RtfText.toPlainText("   "))
    }

    @Test
    fun `a run boundary is only a newline followed by a font number`() {
        assertFalse(RtfText.toPlainText("""{\rtf1\ansi\pard one""" + "\n" + """\fs24 two}""").contains('\n'))
        assertTrue(RtfText.toPlainText("""{\rtf1\ansi\pard one""" + "\n" + """\f2 two}""").contains('\n'))
    }
}
