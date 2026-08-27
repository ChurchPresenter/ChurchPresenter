package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Language is stored per output as [ScreenAssignment.songMode], not in `SongSettings`.
 *
 * `SongPresenter` reads the song-level language fields only when given no `languageOverride`, and
 * every live call site passes that output's own mode -- so these accessors are what makes the
 * tab's language controls restrict anything at all.
 */
class SongOutputLanguageTest {

    private fun screen(mode: String, displayMode: String = Constants.DISPLAY_MODE_FULLSCREEN) =
        ScreenAssignment(displayMode = displayMode, songMode = mode)

    private fun settingsOf(vararg screens: ScreenAssignment) =
        AppSettings(projectionSettings = ProjectionSettings(screenAssignments = screens.toList()))

    private val lowerThird = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    @Test
    fun `a target reads the mode of its own outputs`() {
        val settings = settingsOf(
            screen(Constants.SONG_LANG_PRIMARY),
            screen(Constants.SONG_LANG_BOTH, lowerThird),
        )

        assertEquals(Constants.SONG_LANG_PRIMARY, settings.songLanguageFor(SongStyleTarget.FULL_SCREEN))
        assertEquals(Constants.SONG_LANG_BOTH, settings.songLanguageFor(SongStyleTarget.LOWER_THIRD))
    }

    @Test
    fun `writing a target leaves the other kind of output alone`() {
        val settings = settingsOf(
            screen(Constants.SONG_LANG_BOTH),
            screen(Constants.SONG_LANG_BOTH, lowerThird),
        ).withSongLanguage(SongStyleTarget.FULL_SCREEN, Constants.SONG_LANG_SECONDARY)

        assertEquals(Constants.SONG_LANG_SECONDARY, settings.projectionSettings.screenAssignments[0].songMode)
        assertEquals(Constants.SONG_LANG_BOTH, settings.projectionSettings.screenAssignments[1].songMode)
    }

    @Test
    fun `every output of that kind is written, not just the first`() {
        val settings = settingsOf(
            screen(Constants.SONG_LANG_BOTH),
            screen(Constants.SONG_LANG_BOTH),
        ).withSongLanguage(SongStyleTarget.FULL_SCREEN, Constants.SONG_LANG_PRIMARY)

        assertEquals(
            listOf(Constants.SONG_LANG_PRIMARY, Constants.SONG_LANG_PRIMARY),
            settings.projectionSettings.screenAssignments.map { it.songMode },
        )
    }

    @Test
    fun `an output switched off stays off`() {
        // "Off" means songs do not go to that screen at all, which is a different question from
        // which language -- turning it back on from here would put a song on a screen the operator
        // deliberately kept clear.
        val settings = settingsOf(
            screen(Constants.SONG_LANG_OFF),
            screen(Constants.SONG_LANG_BOTH),
        ).withSongLanguage(SongStyleTarget.FULL_SCREEN, Constants.SONG_LANG_PRIMARY)

        assertEquals(Constants.SONG_LANG_OFF, settings.projectionSettings.screenAssignments[0].songMode)
        assertEquals(Constants.SONG_LANG_PRIMARY, settings.projectionSettings.screenAssignments[1].songMode)
    }

    @Test
    fun `an output showing no songs does not speak for the group`() {
        val settings = settingsOf(
            screen(Constants.SONG_LANG_OFF),
            screen(Constants.SONG_LANG_SECONDARY),
        )

        assertEquals(
            Constants.SONG_LANG_SECONDARY,
            settings.songLanguageFor(SongStyleTarget.FULL_SCREEN),
            "the control never shows a value it does not offer",
        )
    }

    @Test
    fun `with no output of that kind the reading falls back rather than throwing`() {
        assertEquals(
            Constants.SONG_LANG_BOTH,
            settingsOf(screen(Constants.SONG_LANG_PRIMARY)).songLanguageFor(SongStyleTarget.LOWER_THIRD),
        )
    }

    // ── The rail's coarse switch ────────────────────────────────────────────────────────────────

    @Test
    fun `bilingual is true when any output shows a second language`() {
        assertEquals(true, settingsOf(screen(Constants.SONG_LANG_BOTH)).songIsBilingual)
        assertEquals(true, settingsOf(screen(Constants.SONG_LANG_SECONDARY)).songIsBilingual)
        assertEquals(false, settingsOf(screen(Constants.SONG_LANG_PRIMARY)).songIsBilingual)
        assertEquals(false, settingsOf(screen(Constants.SONG_LANG_OFF)).songIsBilingual)
    }

    @Test
    fun `single writes every output, whatever kind`() {
        val settings = settingsOf(
            screen(Constants.SONG_LANG_BOTH),
            screen(Constants.SONG_LANG_SECONDARY, lowerThird),
        ).withSongBilingual(false)

        assertEquals(
            listOf(Constants.SONG_LANG_PRIMARY, Constants.SONG_LANG_PRIMARY),
            settings.projectionSettings.screenAssignments.map { it.songMode },
        )
        assertEquals(false, settings.songIsBilingual)
    }

    @Test
    fun `bilingual restores both on every output`() {
        val settings = settingsOf(
            screen(Constants.SONG_LANG_PRIMARY),
            screen(Constants.SONG_LANG_PRIMARY, lowerThird),
        ).withSongBilingual(true)

        assertEquals(true, settings.songIsBilingual)
        assertEquals(
            listOf(Constants.SONG_LANG_BOTH, Constants.SONG_LANG_BOTH),
            settings.projectionSettings.screenAssignments.map { it.songMode },
        )
    }

    @Test
    fun `the two controls cannot disagree, because they read the same setting`() {
        val single = settingsOf(screen(Constants.SONG_LANG_BOTH)).withSongBilingual(false)
        assertEquals(Constants.SONG_LANG_PRIMARY, single.songLanguageFor(SongStyleTarget.FULL_SCREEN))

        val secondary = single.withSongLanguage(SongStyleTarget.FULL_SCREEN, Constants.SONG_LANG_SECONDARY)
        assertEquals(true, secondary.songIsBilingual, "a second language on any output reads as bilingual")
    }
}
