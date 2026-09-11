package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which chord-chart row carries the words of a given lyric line.
 *
 * The two lists are not index-for-index: a section header, or a row of chords with nothing sung
 * under it, puts a row in the chart and no line on the slide. Pairing them by position among the
 * rows that *have* words is what keeps the chart under the right line.
 */
class SongChartRowTest {

    private fun section(lines: List<String> = emptyList(), chords: List<String> = emptyList()) =
        LyricSection(lines = lines, chordLines = chords)

    @Test
    fun `a section with no chords falls back to its plain words`() {
        val s = section(lines = listOf("Amazing grace", "how sweet the sound"))
        assertEquals("Amazing grace", chartRowFor(s, 0))
        assertEquals("how sweet the sound", chartRowFor(s, 1))
    }

    @Test
    fun `a plain section past its last line has no row`() {
        assertNull(chartRowFor(section(lines = listOf("only line")), 1))
    }

    @Test
    fun `a chorded section returns the row with the chord markers still in it`() {
        val s = section(
            lines = listOf("Amazing grace", "how sweet the sound"),
            chords = listOf("[G]Amazing grace", "how [C]sweet the sound"),
        )
        assertEquals("[G]Amazing grace", chartRowFor(s, 0))
        assertEquals("how [C]sweet the sound", chartRowFor(s, 1))
    }

    @Test
    fun `a section header in the chart does not consume a lyric line`() {
        val s = section(
            lines = listOf("Amazing grace", "how sweet the sound"),
            chords = listOf("[Verse 1]", "[G]Amazing grace", "how [C]sweet the sound"),
        )
        assertEquals("[G]Amazing grace", chartRowFor(s, 0))
        assertEquals("how [C]sweet the sound", chartRowFor(s, 1))
    }

    @Test
    fun `a chords-only row is skipped, as nothing is sung under it`() {
        val s = section(
            lines = listOf("Amazing grace"),
            chords = listOf("[G] [C] [D]", "[G]Amazing grace"),
        )
        assertEquals("[G]Amazing grace", chartRowFor(s, 0))
    }

    @Test
    fun `a blank chart row is skipped`() {
        val s = section(
            lines = listOf("Amazing grace", "how sweet the sound"),
            chords = listOf("[G]Amazing grace", "   ", "how [C]sweet the sound"),
        )
        assertEquals("how [C]sweet the sound", chartRowFor(s, 1))
    }

    @Test
    fun `a chorded section past its last singable row has no row`() {
        val s = section(
            lines = listOf("Amazing grace"),
            chords = listOf("[Verse 1]", "[G]Amazing grace"),
        )
        assertNull(chartRowFor(s, 1))
    }

    @Test
    fun `a negative index has no row rather than throwing`() {
        assertNull(chartRowFor(section(lines = listOf("a")), -1))
        assertNull(chartRowFor(section(lines = listOf("a"), chords = listOf("[G]a")), -1))
    }

    @Test
    fun `a section with neither words nor chords has no rows at all`() {
        assertNull(chartRowFor(section(), 0))
    }

    @Test
    fun `a chart of nothing but headers offers no rows`() {
        val s = section(lines = listOf("sung"), chords = listOf("[Verse 1]", "[Chorus]"))
        assertNull(chartRowFor(s, 0))
    }
}
