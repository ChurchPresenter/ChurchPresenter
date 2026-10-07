package org.churchpresenter.songs

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the Background panel offers and what the presenter is willing to draw — the two decisions
 * either side of the picker, tested away from the composables that host them.
 */
class SongBackgroundChoiceTest {
    // ── What the Colors grid offers ─────────────────────────────────────────────

    // ── The line the preview sits behind ────────────────────────────────────────

    @Test
    fun `the sample line is the first line the audience would actually read`() {
        val lyrics = """
            [Verse 1]
            [G]Amazing grace how sweet the sound
            That saved a wretch like me
        """.trimIndent()

        assertEquals("Amazing grace how sweet the sound", firstLyricLine(lyrics))
    }

    @Test
    fun `section markers of both shapes are skipped`() {
        assertEquals("a chorus line", firstLyricLine("{Chorus}\na chorus line"))
        assertEquals("a verse line", firstLyricLine("[Verse 1]\na verse line"))
    }

    @Test
    fun `a song with nothing but markers has no sample line`() {
        assertEquals("", firstLyricLine("[Verse 1]\n\n[Chorus]\n"))
        assertEquals("", firstLyricLine(""))
    }
}
