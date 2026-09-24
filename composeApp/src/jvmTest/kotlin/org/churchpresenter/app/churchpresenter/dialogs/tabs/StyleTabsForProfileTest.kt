package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which Style tabs and element chips a profile offers: everything it can draw, less whatever it has
 * switched off under Content -- there is nothing on screen to style for content that never shows.
 */
class StyleTabsForProfileTest {

    private val allOn = OutputProfile(songLookAhead = true)

    @Test
    fun `a full screen showing everything offers every category`() {
        assertEquals(
            listOf(
                CustomizePane.BIBLE, CustomizePane.SONGS, CustomizePane.BACKGROUND, CustomizePane.CAPTIONS,
                CustomizePane.SUBTITLES, CustomizePane.QA, CustomizePane.DICTIONARY,
            ),
            stylePanesFor(allOn),
        )
    }

    @Test
    fun `a stage monitor offers its zones, Q&A and the dictionary`() {
        val stage = allOn.copy(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
        assertEquals(
            listOf(CustomizePane.STAGE_MONITOR, CustomizePane.QA, CustomizePane.DICTIONARY),
            stylePanesFor(stage),
        )
    }

    @Test
    fun `content switched off takes its tab away`() {
        val narrowed = allOn.copy(
            bibleMode = Constants.SONG_LANG_OFF,
            songMode = Constants.SONG_LANG_OFF,
            showSTT = false,
            showSubtitles = false,
            showQA = false,
            showDictionary = false,
        )
        assertEquals(listOf(CustomizePane.BACKGROUND), stylePanesFor(narrowed))
    }

    @Test
    fun `the stage monitor keeps its own tab whatever it shows`() {
        val bare = OutputProfile(
            displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR,
            showQA = false,
            showDictionary = false,
        )
        assertEquals(listOf(CustomizePane.STAGE_MONITOR), stylePanesFor(bare))
    }

    @Test
    fun `the look-ahead chip follows the look-ahead switch`() {
        assertTrue(CustomizeElement.SONG_LOOK_AHEAD in styleElementsFor(CustomizePane.SONGS, allOn))
        assertFalse(CustomizeElement.SONG_LOOK_AHEAD in styleElementsFor(CustomizePane.SONGS, OutputProfile()))
    }

    @Test
    fun `a background chip needs both its surface and its content`() {
        val noBibleBackground = allOn.copy(showBibleBackground = false)
        val noBible = allOn.copy(bibleMode = Constants.SONG_LANG_OFF)
        assertFalse(CustomizeElement.BACKGROUND_BIBLE in styleElementsFor(CustomizePane.BACKGROUND, noBibleBackground))
        assertFalse(CustomizeElement.BACKGROUND_BIBLE in styleElementsFor(CustomizePane.BACKGROUND, noBible))
        assertTrue(CustomizeElement.BACKGROUND_SONG in styleElementsFor(CustomizePane.BACKGROUND, noBible))
    }

    @Test
    fun `the default surface is the band's on a lower third and the screen's otherwise`() {
        val band = allOn.copy(
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            showFullscreenBackground = false,
        )
        val screen = allOn.copy(showLowerThirdBackground = false)
        assertTrue(CustomizeElement.BACKGROUND_DEFAULT in styleElementsFor(CustomizePane.BACKGROUND, band))
        assertTrue(CustomizeElement.BACKGROUND_DEFAULT in styleElementsFor(CustomizePane.BACKGROUND, screen))
    }

    @Test
    fun `with no background surface left the Background tab goes too`() {
        val none = allOn.copy(
            showFullscreenBackground = false,
            showBibleBackground = false,
            showSongsBackground = false,
        )
        assertFalse(CustomizePane.BACKGROUND in stylePanesFor(none))
    }

    @Test
    fun `the whole-form categories get no element chips`() {
        val formTabs = listOf(
            CustomizePane.CAPTIONS,
            CustomizePane.SUBTITLES,
            CustomizePane.QA,
            CustomizePane.DICTIONARY,
        )
        for (pane in formTabs) {
            assertTrue(pane.isWholeForm)
            assertEquals(emptyList(), customizeElements(pane))
        }
        assertFalse(CustomizePane.BIBLE.isWholeForm)
    }

    @Test
    fun `a vertical band shows, and stays, as a lower third`() {
        val vertical = Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL
        assertEquals(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, shownDisplayMode(vertical))
        assertEquals(vertical, pickedDisplayMode(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, vertical))
        assertEquals(
            Constants.DISPLAY_MODE_FULLSCREEN,
            pickedDisplayMode(Constants.DISPLAY_MODE_FULLSCREEN, vertical),
            "but picking another mode leaves the band",
        )
    }
}
