package org.churchpresenter.songs

import kotlin.test.Test
import kotlin.test.assertEquals

class SpsSectionRoundTripTest {

    private val songs = Songs()

    /** Sections as SPS writes them: `@$` between sections, `@%` between lines. */
    private fun sections(sps: String): List<List<String>> =
        sps.split("@\$").map { it.split("@%") }

    @Test
    fun `an english song keeps its sections when written back`() {
        val sps = songs.formatLyricsForSps(
            listOf("[Verse 1]", "Amazing grace", "", "{Chorus}", "My chains are gone"),
        )
        assertEquals(
            listOf(
                listOf("Verse 1", "Amazing grace"),
                listOf("Chorus", "My chains are gone"),
            ),
            sections(sps),
        )
    }

    @Test
    fun `a polish song keeps its sections when written back`() {
        // Before the header rule was widened this wrote "[Zwrotka 1]" out as a lyric line,
        // brackets and all, folding the whole song into one section.
        val sps = songs.formatLyricsForSps(
            listOf("[Zwrotka 1]", "Cudowna Boża łaska ta", "", "{Refren}", "Me więzy spadły"),
        )
        assertEquals(
            listOf(
                listOf("Zwrotka 1", "Cudowna Boża łaska ta"),
                listOf("Refren", "Me więzy spadły"),
            ),
            sections(sps),
        )
    }

    @Test
    fun `a header this app has no word for is still a header`() {
        // Bracket-based, so a name from a language or tradition nobody listed survives too.
        val sps = songs.formatLyricsForSps(listOf("[Tacet]", "one", "", "[Vamp]", "two"))
        assertEquals(listOf(listOf("Tacet", "one"), listOf("Vamp", "two")), sections(sps))
    }

    @Test
    fun `a lyric line carrying a chord is not mistaken for a header`() {
        val sps = songs.formatLyricsForSps(listOf("[Verse 1]", "[G]Amazing grace", "how sweet"))
        assertEquals(listOf(listOf("Verse 1", "[G]Amazing grace", "how sweet")), sections(sps))
    }
}
