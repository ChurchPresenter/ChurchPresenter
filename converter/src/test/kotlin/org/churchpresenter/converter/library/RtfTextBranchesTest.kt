package org.churchpresenter.converter.library

import kotlin.test.Test
import kotlin.test.assertEquals

class RtfTextBranchesTest {

    private fun plain(rtf: String) = RtfText.toPlainText(rtf)

    @Test
    fun `a backslash at the very end is dropped`() {
        assertEquals("abc", plain("{\\rtf1 abc\\"))
    }

    @Test
    fun `an escaped carriage return is a line break`() {
        assertEquals("a\nb", plain("{\\rtf1 a\\\rb}"))
    }

    @Test
    fun `a negative unicode escape wraps into the upper half of the 16-bit range`() {
        assertEquals("Ｘ", plain("{\\rtf1\\uc0 \\u-200}"))
    }

    @Test
    fun `a unicode fallback written as a hex escape is skipped as one character`() {
        assertEquals("Я end", plain("{\\rtf1\\uc1 \\u1071\\'3f end}"))
    }

    @Test
    fun `a negative fallback count skips nothing`() {
        assertEquals("Яx", plain("{\\rtf1\\uc-1 \\u1071 x}"))
    }

    @Test
    fun `hex escapes inside an ignored destination are not text`() {
        assertEquals("ab", plain("{\\rtf1 a{\\*\\custom \\'41\\'42}b}"))
    }

    @Test
    fun `binary data is skipped`() {
        assertEquals("ab", plain("{\\rtf1 a\\bin3 xyzb}"))
    }

    @Test
    fun `the DOS and Mac code pages are decoded, and an unknown one falls back to Windows Latin`() {
        assertEquals("ü", plain("{\\rtf1\\ansicpg437 \\'81}"))
        assertEquals("ä", plain("{\\rtf1\\ansicpg10000 \\'8a}"))
        assertEquals("é", plain("{\\rtf1\\ansicpg99999 \\'e9}"))
    }

    @Test
    fun `a newline only starts a run when a font number follows it`() {
        assertEquals("ab", plain("{\\rtf1 a\n\\fs20 b}"))
        assertEquals("a\nb", plain("{\\rtf1 a\n\\f1 b}"))
        assertEquals("a", plain("{\\rtf1 a\n"))
    }

    @Test
    fun `a hex escape cut short at the end is ignored`() {
        assertEquals("a", plain("{\\rtf1 a\\'"))
    }

    @Test
    fun `a newline is a run break only before a numbered font`() {
        assertEquals("a\nb", plain("{\\rtf1 a\r\\f1 b}"))
        assertEquals("ab", plain("{\\rtf1 a\n\\fs24 b}"))
        assertEquals("ab", plain("{\\rtf1 a\n\\par0b}").replace("\n", ""))
        assertEquals("a", plain("{\\rtf1 a\n"))
        assertEquals("a", plain("{\\rtf1 a\n\\"))
        assertEquals("a", plain("{\\rtf1 a\n\\f"))
    }

    @Test
    fun `a control word cut off by the end of the document ends the text`() {
        assertEquals("a", plain("{\\rtf1 a\\li"))
        assertEquals("a", plain("{\\rtf1 a\\li-"))
        assertEquals("a", plain("{\\rtf1 a\\li-36"))
        assertEquals("ab", plain("{\\rtf1 a\\li-360 b}"))
    }

    @Test
    fun `a unicode fallback is cut short by the end of the document`() {
        assertEquals("Я", plain("{\\rtf1\\uc3 \\u1071 a"))
        assertEquals("Я", plain("{\\rtf1\\uc2 \\u1071\\"))
    }

    @Test
    fun `characters inside an ignored destination are not text`() {
        assertEquals("ab", plain("{\\rtf1 a{\\*\\generator x\\~y\\u1071z}b}"))
    }
}
