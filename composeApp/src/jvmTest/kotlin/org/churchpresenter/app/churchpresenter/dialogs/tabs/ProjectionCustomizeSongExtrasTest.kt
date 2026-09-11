@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The number's corner dropdown and the text-backing button — the two Song-pane controls that go
 * somewhere else before they store anything. Both driven on each stored profile.
 */
class ProjectionCustomizeSongExtrasTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        songSettings = SongSettings(
            songNumberCorner = Constants.TOP_LEFT,
            songNumberLowerThirdCorner = Constants.BOTTOM_RIGHT,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.stored(): SongSettings =
        assertNotNull(projectionSettings.screenAssignments[0].songOverride, "the output must have its own Songs")

    /** Opens the corner dropdown showing [showing] and picks [option] from its menu. */
    private fun androidx.compose.ui.test.ComposeUiTest.chooseCorner(showing: String, option: String) {
        onNodeWithText(showing).performScrollTo().performClick()
        waitForIdle()
        onNode(hasTextExactly(option) and hasClickAction()).performClick()
        waitForIdle()
    }

    private val backdropChip = "Text backing"
    private val backdropCaret = "Text backing options"

    // ── The number's corner ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the corner dropdown shows the full screen's stored corner`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER, override = false)
            onNodeWithText("Top Left").assertExists()
        }
    }

    @Test
    fun `the corner dropdown shows the band's stored corner instead`() {
        projectionTab(output(band)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER, override = false)
            onNodeWithText("Bottom Right").assertExists()
        }
    }

    @Test
    fun `picking a corner writes the full screen's own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            chooseCorner("Top Left", "Bottom Left")

            val stored = get().stored()
            assertEquals(Constants.BOTTOM_LEFT, stored.songNumberCorner)
            assertEquals(
                Constants.BOTTOM_RIGHT,
                stored.songNumberLowerThirdCorner,
                "the band's corner must be untouched",
            )
        }
    }

    @Test
    fun `picking a corner writes the band's own`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            chooseCorner("Bottom Right", "Top Right")

            val stored = get().stored()
            assertEquals(Constants.TOP_RIGHT, stored.songNumberLowerThirdCorner)
            assertEquals(
                Constants.TOP_LEFT,
                stored.songNumberCorner,
                "the full screen's corner must be untouched",
            )
        }
    }

    @Test
    fun `the corner can be turned off altogether`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            chooseCorner("Top Left", "Off")

            assertEquals(Constants.NONE, get().stored().songNumberCorner)
        }
    }

    // ── The lyrics' text backing ────────────────────────────────────────────────────────────────

    @Test
    fun `the lyrics carry a text-backing button`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            onNodeWithContentDescription(backdropChip).assertExists()
            onNodeWithContentDescription(backdropCaret).assertExists()
        }
    }

    @Test
    fun `the backing chip writes the full screen's lyrics`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithContentDescription(backdropChip).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertTrue(stored.lyricsBackdrop.lineBackground, "the chip turns the fallback fill on")
            assertEquals(
                TextBackdrop(),
                stored.lyricsLowerThirdBackdrop,
                "the band's own backing must be untouched",
            )
        }
    }

    @Test
    fun `the backing chip writes the band's lyrics instead`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithContentDescription(backdropChip).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertTrue(stored.lyricsLowerThirdBackdrop.lineBackground)
            assertFalse(stored.lyricsBackdrop.lineBackground, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `the caret opens the backing dialog over the pane`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithContentDescription(backdropCaret).performScrollTo().performClick()
            waitForIdle()
            // The lyrics start with no backing, so the dialog opens on Off — which shows its Style
            // row and its hint, and no presets at all.
            onNodeWithText("STYLE").assertExists()
            onNodeWithText("Both").assertExists()
            onNodeWithText("PRESETS").assertDoesNotExist()
        }
    }

    @Test
    fun `a look chosen in that dialog reaches the full screen's lyrics`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithContentDescription(backdropCaret).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Border").performClick()
            waitForIdle()

            assertTrue(get().stored().lyricsBackdrop.border, "the dialog must write through the pane")
        }
    }

    @Test
    fun `a look chosen in that dialog reaches the band's lyrics`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithContentDescription(backdropCaret).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Border").performClick()
            waitForIdle()

            val stored = get().stored()
            assertTrue(stored.lyricsLowerThirdBackdrop.border)
            assertFalse(stored.lyricsBackdrop.border, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `the look-ahead line carries a backing of its own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            onNodeWithContentDescription(backdropChip).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertTrue(stored.lookAheadBackdrop.lineBackground)
            assertFalse(stored.lyricsBackdrop.lineBackground, "the lyrics' own backing must be untouched")
        }
    }

    @Test
    fun `the next section carries a backing of its own`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            onNodeWithContentDescription(backdropChip).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertTrue(stored.lowerThirdLookAheadNextBackdrop.lineBackground)
            assertFalse(stored.lookAheadNextBackdrop.lineBackground, "the full screen's own must be untouched")
        }
    }
}
