@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Song tab's layout and title-slide sections: the bilingual layout row, which is only drawn
 * with two languages on screen, and the title slide's second checkbox, which is only live with the
 * first one ticked.
 */
class SongSettingsTabLayoutTest {

    /** Bilingual, which is the only state in which the layout row is drawn. */
    private fun bilingual(layout: String = Constants.BILINGUAL_TOP_BOTTOM) = AppSettings(
        songSettings = SongSettings(bilingualLayout = layout),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(
                    displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
                    songMode = Constants.SONG_LANG_BOTH,
                ),
            ),
        ),
    )

    // ── The bilingual layout row ──────────────────────────────────────────────

    @Test
    fun `two languages bring the layout row with them`() = songTab(bilingual()) { _ ->
        onNodeWithText("Left / Right").assertExists()
        onNodeWithText("Top / Bottom").assertExists()
    }

    @Test
    fun `picking a layout writes it`() = songTab(bilingual()) { get ->
        onNodeWithText("Left / Right").performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, get().songSettings.bilingualLayout)
    }

    @Test
    fun `picking the other layout writes that one`() =
        songTab(bilingual(Constants.BILINGUAL_SIDE_BY_SIDE)) { get ->
            onNodeWithText("Top / Bottom").performScrollTo().performClick()
            waitForIdle()
            assertEquals(Constants.BILINGUAL_TOP_BOTTOM, get().songSettings.bilingualLayout)
        }

    @Test
    fun `the layout row writes nothing else`() = songTab(bilingual()) { get ->
        val before = get().songSettings
        onNodeWithText("Left / Right").performScrollTo().performClick()
        waitForIdle()
        assertEquals(before.copy(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE), get().songSettings)
    }

    @Test
    fun `dropping to one language takes the layout row away`() = songTab(bilingual()) { _ ->
        onNodeWithText("1 · Single").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Left / Right").assertDoesNotExist()
    }

    @Test
    fun `the language switch is captioned`() = songTab(bilingual()) { _ ->
        onNodeWithText("Song languages").assertExists()
        onNodeWithText("2 · Bilingual").assertExists()
    }

    // ── The title slide's number, from the editing card's title-slide view ────

    private fun ComposeUiTest.openTitleSlideNumber() {
        onNodeWithTag("song_titleSlideEnabled").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Title Slide").performClick()
        waitForIdle()
        onNodeWithText("Number").performClick()
        waitForIdle()
    }

    @Test
    fun `turning the title slide on makes the number checkbox live`() = songTab { get ->
        openTitleSlideNumber()
        assertTrue(get().songSettings.titleSlideEnabled)
        assertTrue(get().songSettings.titleSlideShowSongNumber, "the number ships on")

        onNodeWithTag("song_show_on_title_slide").performClick()
        waitForIdle()
        assertFalse(get().songSettings.titleSlideShowSongNumber, "it was on and must have gone off")
    }

    @Test
    fun `the number checkbox turns back on`() = songTab { get ->
        openTitleSlideNumber()
        onNodeWithTag("song_show_on_title_slide").performClick()
        waitForIdle()
        onNodeWithTag("song_show_on_title_slide").performClick()
        waitForIdle()
        assertTrue(get().songSettings.titleSlideShowSongNumber)
    }

    @Test
    fun `the title-slide checkbox reports its state`() = songTab { _ ->
        onNodeWithTag("song_titleSlideEnabled").performScrollTo().assertIsOff()

        onNodeWithTag("song_titleSlideEnabled").performClick()
        waitForIdle()
        onNodeWithTag("song_titleSlideEnabled").assertIsOn()
    }

    @Test
    fun `the section names itself and its rows`() = songTab { _ ->
        onNodeWithText("Enabled").assertExists()
        onNodeWithTag("song_titleSlideVerticalAlignment").assertExists()
    }

    // ── The layout section's own checkboxes ───────────────────────────────────

    @Test
    fun `the chorus repeat checkbox writes only its own flag`() = songTab { get ->
        val before = get().songSettings
        onNode(isToggleable() and hasText("Repeat chorus after each verse")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            before.copy(autoRepeatChorus = !before.autoRepeatChorus),
            get().songSettings,
        )
    }

    @Test
    fun `word wrap writes only its own flag`() = songTab { get ->
        val before = get().songSettings
        onNode(isToggleable() and hasText("Word Wrap")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before.copy(wordWrap = !before.wordWrap), get().songSettings)
    }
}
