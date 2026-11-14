package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The Songs tab's Display order: one order for a song's languages. Every profile names places in it,
 * so a move changes what each one shows without changing the profile (#650, #1029).
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

    // ── The profiles keep their places ──────────────────────────────────────────────────────────

    @Test
    fun `a move leaves every profile as it was`() {
        val profiles = arrayOf(
            OutputProfile(id = "two", songTranslations = listOf(0, 1)),
            OutputProfile(id = "all", songMode = Constants.SONG_LANG_BOTH),
            OutputProfile(id = "one", songTranslations = listOf(0)),
            OutputProfile(id = "primary", songMode = Constants.SONG_LANG_PRIMARY),
        )

        val after = settings(*profiles).moveSongLanguage(index = 1, offset = -1)

        assertEquals(profiles.toList(), after.projectionSettings.outputProfiles)
    }

    @Test
    fun `a profile showing the first place shows whichever language is first now`() {
        val after = settings(OutputProfile(id = "screen", songTranslations = listOf(0)))
            .moveSongLanguage(index = 1, offset = -1)

        assertEquals(listOf(1), after.profile("screen").songLanguages(2, after.songSettings.languageDisplayOrder()))
    }

    @Test
    fun `a profile showing several languages shows them in the new order`() {
        val after = settings(OutputProfile(id = "screen", songTranslations = listOf(0, 1)))
            .moveSongLanguage(index = 1, offset = -1)

        assertEquals(listOf(1, 0), after.profile("screen").songLanguages(2, after.songSettings.languageDisplayOrder()))
    }

    @Test
    fun `a move builds on the order already stored`() {
        val after = settings(order = listOf(2, 0, 1)).moveSongLanguage(index = 2, offset = -2)

        assertEquals(listOf(1, 2, 0, 3), after.songSettings.languageDisplayOrder())
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
