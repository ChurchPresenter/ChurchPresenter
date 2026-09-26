package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The Songs tab's Display order: one order for a song's languages, which every profile showing more
 * than one of them follows (#650).
 */
class SongLanguageOrderTest {

    private fun settings(vararg profiles: OutputProfile, order: List<Int> = emptyList()) = AppSettings(
        songSettings = SongSettings(languageOrder = order),
        projectionSettings = ProjectionSettings(outputProfiles = profiles.toList()),
    )

    private fun AppSettings.profile(id: String) = projectionSettings.outputProfiles.first { it.id == id }

    // ── The order itself ────────────────────────────────────────────────────────────────────────

    @Test
    fun `an order never set reads as the slots in turn`() {
        assertEquals(listOf(0, 1, 2, 3), SongSettings().languageDisplayOrder())
    }

    @Test
    fun `a stored order comes first and the slots it leaves out follow`() {
        assertEquals(listOf(2, 0, 1, 3), SongSettings(languageOrder = listOf(2, 0)).languageDisplayOrder())
        assertEquals(
            listOf(1, 0, 2, 3),
            SongSettings(languageOrder = listOf(1, 1, 9, 0)).languageDisplayOrder(),
            "repeats and strays ignored",
        )
    }

    @Test
    fun `moving a language stores the new order`() {
        val after = settings().moveSongLanguage(index = 1, offset = -1)

        assertEquals(listOf(1, 0, 2, 3), after.songSettings.languageDisplayOrder())
    }

    @Test
    fun `a move off either end changes nothing`() {
        val before = settings()

        assertSame(before, before.moveSongLanguage(index = 0, offset = -1))
        assertSame(before, before.moveSongLanguage(index = 3, offset = 1))
        assertSame(before, before.moveSongLanguage(index = 7, offset = -1))
    }

    // ── The profiles follow ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a profile showing several languages shows them in the new order`() {
        val after = settings(OutputProfile(id = "screen", songTranslations = listOf(0, 1)))
            .moveSongLanguage(index = 1, offset = -1)

        assertEquals(listOf(1, 0), after.profile("screen").songTranslations)
    }

    @Test
    fun `a profile showing every language is given the whole order`() {
        val after = settings(OutputProfile(id = "screen", songMode = Constants.SONG_LANG_BOTH))
            .moveSongLanguage(index = 1, offset = -1)

        assertEquals(listOf(1, 0, 2, 3), after.profile("screen").songTranslations)
        assertEquals(listOf(1, 0), after.profile("screen").songLanguages(available = 2), "a two-language song")
    }

    @Test
    fun `a profile showing one language, or no songs, is left alone`() {
        val one = OutputProfile(id = "one", songTranslations = listOf(1))
        val primaryOnly = OutputProfile(id = "primary", songMode = Constants.SONG_LANG_PRIMARY)
        val off = OutputProfile(id = "off", songMode = Constants.SONG_LANG_OFF, songTranslations = listOf(0, 1))

        val after = settings(one, primaryOnly, off).moveSongLanguage(index = 1, offset = -1)

        assertEquals(one, after.profile("one"))
        assertEquals(primaryOnly, after.profile("primary"))
        assertEquals(off, after.profile("off"))
    }

    @Test
    fun `a move builds on the order already stored`() {
        val after = settings(OutputProfile(id = "screen", songTranslations = listOf(0, 1, 2)), order = listOf(2, 0, 1))
            .moveSongLanguage(index = 2, offset = -2)

        assertEquals(listOf(1, 2, 0, 3), after.songSettings.languageDisplayOrder())
        assertEquals(listOf(1, 2, 0), after.profile("screen").songTranslations)
    }

    // ── As the Songs tab moves them: among the selected song's languages ────────────────────────

    @Test
    fun `a move among a song's own languages steps past the slots it does not have`() {
        // Order 0,2,1,3 and a two-language song: the panel shows 0 then 1, and moving 1 up puts it
        // ahead of 0 in the full order -- slot 2 keeps its place after it.
        val after = settings(order = listOf(0, 2, 1, 3)).moveSongLanguageAmong(available = 2, index = 1, offset = -1)

        assertEquals(listOf(1, 0, 2, 3), after.songSettings.languageDisplayOrder())
    }

    @Test
    fun `a move past the ends of a song's languages changes nothing`() {
        val before = settings()

        assertSame(before, before.moveSongLanguageAmong(available = 2, index = 1, offset = 1))
        assertSame(before, before.moveSongLanguageAmong(available = 2, index = 0, offset = -1))
    }
}
