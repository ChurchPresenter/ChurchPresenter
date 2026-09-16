@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * What the strip beneath the preview offers, per chip.
 *
 * The settings that belong to the slide rather than to one thing drawn on it moved here from a chip
 * of their own -- whose other three sections were the margins, the fades and the band height, all of
 * which the strip already carried, so it showed the same settings twice under two headings. The
 * strip is drawn under every chip, so the rows that cannot change *this* picture have to go.
 *
 * Existence rather than displayedness: the strip scrolls, so which of its rows are above the fold
 * is a property of the dialog's height rather than of what the chip offers. The strip's own row
 * captions are drawn uppercased, which is why they are matched that way.
 */
class ProjectionCustomizeStripRowsTest {

    private fun screen() = AppSettings(
        songSettings = SongSettings(titleSlideEnabled = true, showEndOfSongIndicator = true),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
        ),
    )

    /** The chip is gone; everything it drew is either on the strip or was a duplicate of it. */
    @Test
    fun `the songs pane no longer offers a slide chip`() {
        assertFalse(
            customizeElements(CustomizePane.SONGS).any { it.name == "SONG_SLIDE" },
            "the Slide chip's contents live on the strip now",
        )
    }

    @Test
    fun `a lyric slide is offered the lyric-slide rows`() {
        projectionTab(screen()) {
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithText("Word Wrap").assertExists()
            onNodeWithText("Repeat chorus after each verse").assertExists()
            onNodeWithText("MARKER").assertExists()
        }
    }

    /**
     * The title slide is a heading and its credits: it wraps no lyrics, repeats no chorus, carries
     * no end-of-song marker, and takes its vertical alignment from its own control in the pane.
     */
    @Test
    fun `the title slide is not offered rows that cannot move it`() {
        projectionTab(screen()) {
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onNodeWithText("Word Wrap").assertDoesNotExist()
            onNodeWithText("Repeat chorus after each verse").assertDoesNotExist()
            onNodeWithText("MARKER").assertDoesNotExist()
        }
    }

    /** What is left under that chip is what still redraws it. */
    @Test
    fun `the title slide keeps the rows that do move it`() {
        projectionTab(screen()) {
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onNodeWithText("MARGINS").assertExists()
            onNodeWithText("MOTION").assertExists()
        }
    }
}
