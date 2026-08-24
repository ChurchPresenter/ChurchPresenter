package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.songchords.isChorusHeader
import org.churchpresenter.songchords.isHeaderLine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongHeaderParsingTest {

    @Test
    fun `recognises both bracket styles as headers`() {
        assertTrue(isHeaderLine("[Verse 1]"))
        assertTrue(isHeaderLine("{Chorus}"))
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertTrue(isHeaderLine("   [Verse 1]   "))
        assertTrue(isChorusHeader("  {Chorus}  "))
    }

    @Test
    fun `only braces mark a chorus`() {
        assertTrue(isChorusHeader("{Chorus}"))
        assertFalse(isChorusHeader("[Chorus]")) // square brackets are verse/other, even when named "Chorus"
    }

    @Test
    fun `does not treat ordinary lyric lines as headers`() {
        for (lyric in listOf(
            "Amazing grace how sweet the sound",
            "[unclosed",
            "unopened]",
            "He said [something] mid-line and carried on",
            "",
        )) {
            assertFalse(isHeaderLine(lyric), "should not be a header: \"$lyric\"")
        }
    }

    @Test
    fun `mismatched bracket pairs are not headers`() {
        assertFalse(isHeaderLine("[Verse}"))
        assertFalse(isHeaderLine("{Verse]"))
        assertFalse(isChorusHeader("{Chorus"), "an unclosed brace is a lyric line, not a chorus")
        assertFalse(isChorusHeader("Chorus}"), "and so is a stray closing one")
    }

    @Test
    fun `every chorus header is also a header line`() {
        for (line in listOf("{Chorus}", " {Refrain} ", "{}")) {
            assertTrue(isChorusHeader(line))
            assertTrue(isHeaderLine(line), "isChorusHeader implies isHeaderLine, broken by \"$line\"")
        }
    }
}
