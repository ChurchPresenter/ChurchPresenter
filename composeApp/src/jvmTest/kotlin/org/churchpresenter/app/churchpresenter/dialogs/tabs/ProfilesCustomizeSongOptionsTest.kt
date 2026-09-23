@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongNumberOffset
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Song pane's option rows -- the controls that decide *what* a slide holds rather than how the
 * text looks: how much of the song, what the title slide shows, and where the number sits.
 *
 * Each of these is drawn for one element or one state and not the others, which is the part worth
 * pinning: "number before title" is only a question where the two share a position, and the number's
 * free offset only exists for the number. Drawn outside those cases the control stores a field the
 * presenter never reads, which looks like a setting that does nothing.
 */
class ProfilesCustomizeSongOptionsTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
        song: SongSettings = SongSettings(),
    ) = profileDocument(
        mode = mode,
        profile = OutputProfile(songMode = Constants.SONG_LANG_PRIMARY),
        song = song,
    )

    // ── How much of the song a slide holds ──────────────────────────────────────────────────────

    @Test
    fun `the chunk control switches the lyrics to one line a slide`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onAllNodesWithText("1 Line")[0].performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.SONG_DISPLAY_MODE_LINE, get().song().fullscreenDisplayMode)
        }
    }

    @Test
    fun `a band chunks on its own`() {
        // One verse fills a full screen and overruns a band, so the two shapes are asked separately
        // -- a band set to a line at a time must not drag the projector with it.
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onAllNodesWithText("1 Line")[0].performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.SONG_DISPLAY_MODE_LINE, get().song().lowerThirdDisplayMode)
            assertEquals(
                Constants.SONG_DISPLAY_MODE_VERSE,
                get().song().fullscreenDisplayMode,
                "the full screen's own chunk must be untouched",
            )
        }
    }

    // ── The title slide ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `an element can be taken off the title slide`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            openElement(CustomizeElement.SONG_TITLE_SLIDE)
            val before = get().song().titleSlideShowTitle
            onNodeWithTag("song_show_on_title_slide").performScrollTo().performClick()
            waitForIdle()

            assertEquals(!before, get().song().titleSlideShowTitle)
        }
    }

    @Test
    fun `only the number is asked whether it comes before the title`() {
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)

            // The title slide opens on the title, which has nothing to sit before.
            onAllNodesWithTag("song_titleSlideNumberBeforeTitle").assertCountEquals(0)
        }
    }

    // ── Where the number sits ───────────────────────────────────────────────────────────────────

    @Test
    fun `the number's order against the title is asked only where they share a position`() {
        // Both above the lyrics and both centred, with the number out of its corner: their order is
        // then a real question, because nothing else in the layout answers it. A cornered number is
        // drawn over the slide rather than in the title's row, which is why the corner has to go.
        val shared = SongSettings(
            songNumberCorner = Constants.NONE,
            titlePosition = Constants.ABOVE_VERSE,
            songNumberPosition = Constants.ABOVE_VERSE,
            titleHorizontalAlignment = Constants.CENTER,
            songNumberHorizontalAlignment = Constants.CENTER,
        )
        profilesTab(output(song = shared)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            val before = get().song().songNumberBeforeTitle
            onNodeWithTag("song_songNumberBeforeTitle").performScrollTo().performClick()
            waitForIdle()

            assertEquals(!before, get().song().songNumberBeforeTitle)
        }
    }

    @Test
    fun `the lyrics are never asked about the number's order`() {
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)

            onAllNodesWithTag("song_songNumberBeforeTitle").assertCountEquals(0)
        }
    }

    /**
     * The number's free nudge, as a percentage of the screen in each axis.
     *
     * It sits on top of whichever corner the appearance row put the number in, so a church can walk
     * it clear of a logo or a lower-third band without giving up the corner. The two axes are one
     * stored record, so the risk is one field's writer overwriting the other's.
     */
    @Test
    fun `the two offset axes are stored apart`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            assertTrue(
                onAllNodesWithTag("song_number_offset_x").fetchSemanticsNodes().isNotEmpty(),
                "the number is the one element with a free offset",
            )
            assertTrue(onAllNodesWithTag("song_number_offset_y").fetchSemanticsNodes().isNotEmpty())

            val stored = get().song().numberOffset(lowerThird = false)
            assertEquals(SongNumberOffset(), stored, "it starts unnudged")
        }
    }

    @Test
    fun `no other element carries an offset`() {
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)

            onAllNodesWithTag("song_number_offset_x").assertCountEquals(0)
            assertFalse(
                onAllNodesWithText("Offset Y %").fetchSemanticsNodes().isNotEmpty(),
                "the offset belongs to the number alone",
            )
        }
    }
}
