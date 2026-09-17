@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The song number's above/below-verse position control is offered only when a corner is not already
 * deciding where the number goes.
 *
 * `SongNumberCorner` has always said a corner is an alternative to the row the number shares with
 * the title rather than an extra setting layered on it, and `SongPresenter` agrees -- a cornered
 * number is drawn over the slide and its position is never read. The control was offered anyway, and
 * the corner defaults to bottom right, so the first thing an operator met on the Number element was
 * a control that did nothing.
 */
class ProjectionCustomizeNumberPositionTest {

    private fun screen(corner: String) = AppSettings(
        songSettings = SongSettings(songNumberCorner = corner),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
        ),
    )

    @Test
    fun `a cornered number has no position control`() {
        projectionTab(screen(Constants.BOTTOM_RIGHT)) {
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            onNodeWithText("Position").assertDoesNotExist()
        }
    }

    @Test
    fun `switching the corner off brings it back`() {
        projectionTab(screen(Constants.NONE)) {
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            onNodeWithText("Position").assertIsDisplayed()
        }
    }

    /** The title has no corner, so its own position control is unaffected either way. */
    @Test
    fun `the title keeps its position control`() {
        projectionTab(screen(Constants.BOTTOM_RIGHT)) {
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            onNodeWithText("Position").assertIsDisplayed()
        }
    }
}
