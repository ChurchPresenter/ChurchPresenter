@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Song pane's remaining controls: the title slide's own section and element picker, where the
 * song number sits, the text backing, and the font picker.
 *
 * Ported from the `ProjectionCustomizeSong*` and `ProjectionCustomizeTitleSlide*` suites, which
 * reached the same controls through the Projection tab's Customize dialog.
 */
class ProfilesCustomizeSongExtrasTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
        song: SongSettings = SongSettings(
            titleSlideEnabled = true,
            lyricsFontType = SENTINEL_FONT,
            lyricsLowerThirdFontType = SENTINEL_FONT,
            lyricsFontSize = 61,
            lyricsLowerThirdFontSize = 62,
        ),
    ) = profileDocument(mode = mode, song = song, profile = OutputProfile())

    // ── The title slide ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the title slide chip draws its own section`() {
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onNodeWithText("Song Title Slide").assertExists()
            onNodeWithText("Enabled").assertExists()
        }
    }

    @Test
    fun `the title slide can be switched off`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            toggleCheckbox("Enabled")

            assertFalse(get().song().titleSlideEnabled, "it started on and must have gone off")
        }
    }

    @Test
    fun `the title slide keeps a selector of its own for its six parts`() {
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            // The chips above the pane have one seat for the whole title slide, so its parts get
            // their own row -- the lyric elements each have a chip already.
            onNodeWithText("Composer").assertExists()
        }
    }

    @Test
    fun `picking a part of the title slide styles that part`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onNodeWithText("Composer").performScrollTo().performClick()
            waitForIdle()
            styleButton(group = 0, label = "B").performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().song().titleSlideComposer.bold, "the composer line must have gone bold")
        }
    }

    // ── Where the number sits ───────────────────────────────────────────────────────────────────

    /** The corner is a closed dropdown, so its options exist only once it is opened. */
    private fun SkikoComposeUiTest.openCornerDropdown() {
        onNodeWithTag("song_number_corner").performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `the number element offers the four corners and Off`() {
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            openCornerDropdown()

            // The dropdown's own button shows the current corner, so the one already selected
            // appears twice -- once on the button, once in the open menu.
            for (corner in listOf("Top Left", "Top Right", "Bottom Left", "Bottom Right")) {
                assertTrue(
                    onAllNodesWithText(corner).fetchSemanticsNodes().isNotEmpty(),
                    "$corner must be on offer",
                )
            }
        }
    }

    @Test
    fun `putting the number in a corner stores that corner`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            openCornerDropdown()
            onNodeWithText("Top Right").performClick()
            waitForIdle()

            assertEquals(Constants.TOP_RIGHT, get().song().numberCorner(lowerThird = false))
        }
    }

    @Test
    fun `a band stores its own corner`() {
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            openCornerDropdown()
            onNodeWithText("Bottom Left").performClick()
            waitForIdle()

            assertEquals(Constants.BOTTOM_LEFT, get().song().numberCorner(lowerThird = true))
            assertEquals(
                SongSettings().numberCorner(lowerThird = false),
                get().song().numberCorner(lowerThird = false),
                "the full screen's corner must be untouched",
            )
        }
    }

    // ── Text backing and the font ───────────────────────────────────────────────────────────────

    @Test
    fun `the lyrics carry a text-backing button that writes the full screen`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithContentDescription("Text backing").performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().song().lyricsBackdrop.lineBackground)
            assertEquals(
                TextBackdrop(),
                get().song().lyricsLowerThirdBackdrop,
                "the band's own backing must be untouched",
            )
        }
    }

    @Test
    fun `the font picker writes the full screen's lyrics`() {
        val family = uniquelyNamedFont()
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            pickFont(SENTINEL_FONT, family)

            assertEquals(family, get().song().lyricsFontType)
            assertEquals(
                SENTINEL_FONT,
                get().song().lyricsLowerThirdFontType,
                "the band keeps its own face",
            )
        }
    }

    @Test
    fun `the font picker writes the band's lyrics instead`() {
        val family = uniquelyNamedFont()
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            pickFont(SENTINEL_FONT, family)

            assertEquals(family, get().song().lyricsLowerThirdFontType)
            assertEquals(SENTINEL_FONT, get().song().lyricsFontType, "the full screen keeps its own")
        }
    }
}
