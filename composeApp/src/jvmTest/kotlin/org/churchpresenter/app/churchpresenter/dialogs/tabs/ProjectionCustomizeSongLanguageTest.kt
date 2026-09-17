@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Songs pane's Lang control writes *this* screen's mode.
 *
 * It used to go through `withSongLanguage`, which writes `projectionSettings.screenAssignments` --
 * so the dialog, which stores an edit as the difference between two `SongSettings`, never carried it
 * out at all: its own preview obeyed immediately while the screen kept showing both languages. And
 * had it been carried out it would have been applied to every full-screen output rather than to the
 * one being customized.
 */
class ProjectionCustomizeSongLanguageTest {

    private fun twoScreens() = AppSettings(
        songSettings = SongSettings(titleSlideEnabled = true),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(
                    displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
                    songMode = Constants.SONG_LANG_BOTH,
                ),
                ScreenAssignment(
                    displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
                    songMode = Constants.SONG_LANG_BOTH,
                ),
            ),
        ),
    )

    private fun AppSettings.modes() = projectionSettings.screenAssignments.map { it.songMode }

    @Test
    fun `picking a language writes the screen being customized`() {
        projectionTab(twoScreens()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            chooseSegment("Primary")

            assertEquals(Constants.SONG_LANG_PRIMARY, get().modes()[0], "the screen whose dialog is open")
        }
    }

    @Test
    fun `it leaves every other screen alone`() {
        projectionTab(twoScreens()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            chooseSegment("Primary")

            assertEquals(Constants.SONG_LANG_BOTH, get().modes()[1], "the other screen is not being customized")
        }
    }

    /** Also on the title slide, where the control decides which of the two titles opens the song. */
    @Test
    fun `the title slide's copy writes the same screen`() {
        projectionTab(twoScreens()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            chooseSegment("Secondary")

            assertEquals(Constants.SONG_LANG_SECONDARY, get().modes()[0])
            assertEquals(Constants.SONG_LANG_BOTH, get().modes()[1])
        }
    }
}
