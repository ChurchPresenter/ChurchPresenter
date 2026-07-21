package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [formatAspectRatio] labels projector/output resolutions in the projection settings UI. */
class FormatAspectRatioTest {

    @Test
    fun `reduces common resolutions to their familiar ratio`() {
        assertEquals("16:9", formatAspectRatio(1920, 1080))
        assertEquals("16:9", formatAspectRatio(3840, 2160))
        assertEquals("4:3", formatAspectRatio(1024, 768))
        assertEquals("64:27", formatAspectRatio(2560, 1080)) // ultrawide, still within the <=64 cap
    }

    /**
     * Documents CURRENT behaviour, which is arguably wrong: the function promises a "common name"
     * but reduces by the full GCD, so every 16:10 display -- a very common projector and laptop
     * ratio -- is labelled "8:5", a form essentially nobody uses. 16:9 only survives because
     * 1920:1080 happens to reduce to exactly 16:9.
     *
     * Left as-is deliberately rather than changed on assumption; fixing it means adding a
     * lookup of conventional names ahead of the GCD reduction. If that lands, update this test.
     */
    @Test
    fun `16 to 10 displays are labelled 8 to 5 -- known cosmetic wart`() {
        assertEquals("8:5", formatAspectRatio(1920, 1200))
        assertEquals("8:5", formatAspectRatio(1280, 800))
        assertEquals("8:5", formatAspectRatio(2560, 1600))
    }

    @Test
    fun `falls back to a decimal ratio when the reduced form is not recognisable`() {
        // 1366x768 reduces to 683:384 -- technically correct, useless as a label.
        assertEquals("1.78:1", formatAspectRatio(1366, 768))
    }

    @Test
    fun `handles portrait orientation`() {
        assertEquals("9:16", formatAspectRatio(1080, 1920))
    }
}

/**
 * [isHeaderLine] / [isChorusHeader] drive section splitting for every song file the app parses,
 * so their edge behaviour decides whether a lyric line is shown as lyrics or eaten as a header.
 */
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
    }

    @Test
    fun `every chorus header is also a header line`() {
        for (line in listOf("{Chorus}", " {Refrain} ", "{}")) {
            assertTrue(isChorusHeader(line))
            assertTrue(isHeaderLine(line), "isChorusHeader implies isHeaderLine, broken by \"$line\"")
        }
    }
}
