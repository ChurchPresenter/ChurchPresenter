package org.churchpresenter.converter.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RtfTextTest {

    private fun rtf(body: String, header: String = "\\ansi\\ansicpg1252") =
        "{\\rtf1$header{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}{\\colortbl;\\red0\\green0\\blue0;}$body}"

    @Test
    fun `plain text is passed through untouched`() {
        assertEquals("Verse 1\nline one", RtfText.toPlainText("Verse 1\nline one"))
    }

    @Test
    fun `control words and the font and colour tables are not text`() {
        val text = RtfText.toPlainText(rtf("\\pard\\f0\\fs24 Amazing grace"))
        assertEquals("Amazing grace", text)
        assertFalse(text.contains("Arial"), text)
        assertFalse(text.contains("red0"), text)
    }

    @Test
    fun `par ends a line and line breaks within one`() {
        assertEquals("one\ntwo\nthree", RtfText.toPlainText(rtf("one\\par two\\line three")))
    }

    @Test
    fun `escaped braces and backslashes are literal`() {
        assertEquals("{a} \\ b", RtfText.toPlainText(rtf("\\{a\\} \\\\ b")))
    }

    @Test
    fun `a byte escape is decoded in the document code page`() {
        // 0xE0 is 'а' in cp1251 and 'à' in cp1252 — the same byte, two different letters.
        assertEquals("а", RtfText.toPlainText(rtf("\\'e0", header = "\\ansi\\ansicpg1251")))
        assertEquals("à", RtfText.toPlainText(rtf("\\'e0")))
    }

    @Test
    fun `a font declared Cyrillic overrides the document code page`() {
        // What a Russian song library actually looks like: cp1252 document, cp1251 font.
        val source = "{\\rtf1\\ansi\\ansicpg1252{\\fonttbl{\\f0\\fnil\\fcharset204 Arial;}}" +
            "\\f0\\'cf\\'e5\\'f1\\'ed\\'ff}"
        assertEquals("Песня", RtfText.toPlainText(source))
    }

    @Test
    fun `a unicode escape is used and its fallback character skipped`() {
        assertEquals("Ω", RtfText.toPlainText(rtf("\\u937?")))
        assertEquals("ΩΩ", RtfText.toPlainText(rtf("\\u937?\\u937?")))
    }

    @Test
    fun `uc sets how many fallback characters a unicode escape replaces`() {
        assertEquals("Ωafter", RtfText.toPlainText(rtf("\\uc3\\u937?xyafter")))
    }

    @Test
    fun `a unicode escape above the signed range wraps round`() {
        // RTF writes \u as signed 16-bit, so U+FB01 goes out as -1279.
        assertEquals("ﬁ", RtfText.toPlainText(rtf("\\u-1279?")))
    }

    @Test
    fun `an ignorable destination contributes nothing`() {
        assertEquals("kept", RtfText.toPlainText(rtf("{\\*\\expandedcolortbl;;}kept")))
    }

    @Test
    fun `a hard-wrapped paragraph is not broken at the wrap`() {
        // RTF writers wrap long paragraphs with a bare newline; treating it as a break would
        // split lyrics mid-sentence.
        assertEquals("one two", RtfText.toPlainText(rtf("one \ntwo")))
    }

    @Test
    fun `a newline on a run boundary does break the line`() {
        assertEquals("small\nLYRIC", RtfText.toPlainText(rtf("\\f0\\fs24 small\n\\f0\\fs120 LYRIC")))
    }

    @Test
    fun `an empty document yields nothing rather than throwing`() {
        assertEquals("", RtfText.toPlainText(rtf("")))
        assertTrue(RtfText.toPlainText("").isEmpty())
    }

    @Test
    fun `a truncated document does not throw`() {
        RtfText.toPlainText("{\\rtf1\\ansi{\\fonttbl{\\f0 Arial")
        RtfText.toPlainText("{\\rtf1\\ansi\\'")
    }
}
