@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The strip rows that belong to the slide as a whole -- word wrap, the repeated chorus, where the
 * lyrics sit, the end-of-song marker and the content region -- driven on the Profiles tab and read
 * back from what the profile actually renders with.
 *
 * Ported from `ProjectionCustomizeSlideStripTest`, which drove the same rows through the Projection
 * tab's Customize dialog and read them off a `ScreenAssignment`. The rows did not change; where
 * they live and what owns them did.
 *
 * The starting values are deliberately odd numbers so a field can be found by what it shows without
 * colliding with a margin or a font size.
 */
class ProfilesCustomizeSlideStripTest {

    private val region = ContentRegion(xOffsetPercent = -13, yOffsetPercent = 19, widthPercent = 83)

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(
        mode = mode,
        song = SongSettings(
            showEndOfSongIndicator = true,
            endOfSongIndicatorSpacing = 13,
            layoutExtras = SongLayoutExtras(contentRegion = region),
        ),
        bible = BibleSettings(contentRegion = region),
    )

    @Test
    fun `word wrap and the repeated chorus are written for this profile`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            toggleCheckbox("Word Wrap", scroll = false)
            toggleCheckbox("Repeat chorus after each verse", scroll = false)

            assertTrue(get().song().wordWrap, "word wrap was off and must have come on")
            assertFalse(get().song().autoRepeatChorus, "the repeated chorus was on and must have gone off")
        }
    }

    @Test
    fun `the lyric slides can be moved to the bottom of the screen`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            // The margins row has a "Bottom" field too; the alignment's is the one that is a button.
            onAllNodes(hasText("Bottom") and hasClickAction() and !hasSetTextAction())
                .onFirst()
                .performScrollTo()
                .performClick()
            waitForIdle()

            assertEquals(Constants.BOTTOM, get().song().lyricsAlignment)
        }
    }

    @Test
    fun `the end-of-song marker's spacing is written, and hides with the marker`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(13, 17)
            assertEquals(17, get().song().endOfSongIndicatorSpacing)

            toggleCheckbox("Show", scroll = false)

            assertFalse(get().song().showEndOfSongIndicator)
            // A spacing for a marker that is off changes nothing, so the field goes with it.
            onNode(hasSetTextAction() and hasText("17")).assertDoesNotExist()
        }
    }

    @Test
    fun `a full-screen song profile writes its own content region`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(83, 70)
            retypeNumberField(-13, -40)
            retypeNumberField(19, 25)

            assertEquals(
                ContentRegion(xOffsetPercent = -40, yOffsetPercent = 25, widthPercent = 70),
                get().song().layoutExtras.contentRegion,
            )
        }
    }

    @Test
    fun `a full-screen Bible profile writes its own content region`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            retypeNumberField(83, 60)
            retypeNumberField(-13, 5)
            retypeNumberField(19, -30)

            assertEquals(
                ContentRegion(xOffsetPercent = 5, yOffsetPercent = -30, widthPercent = 60),
                get().bible().contentRegion,
            )
        }
    }

    @Test
    fun `a band is offered no content region, its own width already being the band`() {
        profilesTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithText("CONTENT REGION").assertDoesNotExist()
        }
    }
}
