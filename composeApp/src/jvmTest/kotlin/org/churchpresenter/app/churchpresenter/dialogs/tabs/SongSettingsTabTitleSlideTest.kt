@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Song tab's title-slide view: the third position of the switch above the preview, which
 * points the one set of controls at the slide a song opens with rather than at its lyric slides.
 */
class SongSettingsTabTitleSlideTest {

    private fun withTitleSlide(song: SongSettings = SongSettings()) =
        AppSettings(songSettings = song.copy(titleSlideEnabled = true))

    private fun ComposeUiTest.openTitleSlide() {
        onNodeWithText("Title Slide").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pick(element: SongStyleElement) {
        onNodeWithText(element.tabLabel).performClick()
        waitForIdle()
    }

    // ── The switch ────────────────────────────────────────────────────────────

    @Test
    fun `the title slide position is offered only while the slide is on`() = songTab { _ ->
        onAllNodesWithText("Title Slide").assertCountEquals(0)

        onNodeWithTag("song_titleSlideEnabled").performClick()
        waitForIdle()

        onNodeWithText("Title Slide").assertExists()
    }

    @Test
    fun `picking the title slide swaps the element tabs for the slide's own`() = songTab(withTitleSlide()) { _ ->
        openTitleSlide()

        TITLE_SLIDE_ELEMENTS.forEach { onNodeWithText(it.tabLabel).assertExists(it.name) }
        listOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION).forEach {
            onAllNodesWithText(it.tabLabel).assertCountEquals(0)
        }
    }

    @Test
    fun `the scope note names the title slide and the output's size`() = songTab(withTitleSlide()) { _ ->
        openTitleSlide()
        onAllNodesWithText("Title slide · ", substring = true).onFirst().assertExists()

        onNodeWithText("Lower Third").performClick()
        waitForIdle()
        openTitleSlide()
        onAllNodesWithText("Title slide · ", substring = true).onFirst().assertExists()
        onAllNodesWithText("Lower third · ", substring = true).assertCountEquals(0)
    }

    @Test
    fun `leaving for an output while a credit is selected falls back to the lyrics`() =
        songTab(withTitleSlide()) { get ->
            openTitleSlide()
            pick(SongStyleElement.AUTHOR)

            onNodeWithText("Full Screen").performClick()
            waitForIdle()

            onNodeWithText(SongStyleElement.LYRICS.tabLabel).assertExists()
            onAllNodesWithText(SongStyleElement.AUTHOR.tabLabel).assertCountEquals(0)
            // And the lyric-slide controls are back: the chunk row belongs to that view alone.
            onNodeWithText("1 Verse").assertExists()
            assertTrue(get().songSettings.titleSlideEnabled, "nothing stored changed")
        }

    @Test
    fun `the look-ahead preview switch has no place on the title slide`() = songTab(withTitleSlide()) { _ ->
        onNodeWithText("Look ahead").assertExists()
        openTitleSlide()
        onAllNodesWithText("Look ahead").assertCountEquals(0)
    }

    // ── What the view offers ──────────────────────────────────────────────────

    @Test
    fun `show on title slide writes the selected element's own switch`() = songTab(withTitleSlide()) { get ->
        openTitleSlide()
        pick(SongStyleElement.COMPOSER)
        onNodeWithTag("song_show_on_title_slide").assertIsOn()

        onNodeWithTag("song_show_on_title_slide").performClick()
        waitForIdle()

        assertFalse(get().songSettings.titleSlideShowComposer)
        assertTrue(get().songSettings.titleSlideShowAuthor, "the author beside it is untouched")
        onNodeWithTag("song_show_on_title_slide").assertIsOff()
    }

    @Test
    fun `the number's row switch is the number tab's alone, and follows its visibility`() =
        songTab(withTitleSlide()) { get ->
            openTitleSlide()
            pick(SongStyleElement.TITLE)
            onAllNodesWithText("Show song number before title").assertCountEquals(0)

            pick(SongStyleElement.NUMBER)
            onNodeWithTag("song_titleSlideNumberBeforeTitle").assertIsOn()
            onNodeWithTag("song_titleSlideNumberBeforeTitle").performClick()
            waitForIdle()
            assertFalse(get().songSettings.titleSlideNumberBeforeTitle)

            onNodeWithTag("song_show_on_title_slide").performClick()
            waitForIdle()
            onNodeWithTag("song_titleSlideNumberBeforeTitle").assertIsNotEnabled()
        }

    @Test
    fun `the language switch is the title tab's alone`() = songTab(withTitleSlide()) { get ->
        openTitleSlide()
        pick(SongStyleElement.AUTHOR)
        onAllNodesWithText("Lang").assertCountEquals(0)

        pick(SongStyleElement.TITLE)
        onNodeWithText("Lang").assertExists()
        onNodeWithText("Secondary").performClick()
        waitForIdle()

        assertEquals(Constants.SONG_LANG_SECONDARY, get().songLanguageFor(SongStyleTarget.FULL_SCREEN))
    }

    @Test
    fun `the first-page switch and the position are lyric-slide questions, so the view hides them`() =
        songTab(withTitleSlide()) { _ ->
            openTitleSlide()
            pick(SongStyleElement.TITLE)
            onAllNodesWithText("Position").assertCountEquals(0)
            onAllNodesWithText("First page").assertCountEquals(0)
            onAllNodesWithText("Chunk").assertCountEquals(0)
        }

    @Test
    fun `a style button writes the credit's own profile on the selected output`() =
        songTab(withTitleSlide()) { get ->
            openTitleSlide()
            pick(SongStyleElement.CCLI)
            onNodeWithText("B").performClick()
            waitForIdle()
            assertTrue(get().songSettings.titleSlideCcli.bold)
            assertFalse(get().songSettings.titleSlideCcliLowerThird.bold, "the band's own profile is untouched")
            assertFalse(get().songSettings.titleBold, "and so is the title's")

            onNodeWithText("Lower Third").performClick()
            waitForIdle()
            openTitleSlide()
            pick(SongStyleElement.CCLI)
            onNodeWithText("B").performClick()
            waitForIdle()
            assertTrue(get().songSettings.titleSlideCcliLowerThird.bold)
        }

    @Test
    fun `the number and the title keep the profiles the lyric slides draw with`() =
        songTab(withTitleSlide()) { get ->
            openTitleSlide()
            pick(SongStyleElement.TITLE)
            onNodeWithText("I").performClick()
            waitForIdle()
            assertTrue(get().songSettings.titleItalic, "one title profile, shared with the lyric slides")
        }

    // ── The rail ──────────────────────────────────────────────────────────────

    @Test
    fun `the vertical alignment row writes the title slide's own field`() = songTab(withTitleSlide()) { get ->
        onAllNodesWithContentDescription("Align Top").onFirst().performClick()
        waitForIdle()

        assertEquals(Constants.TOP, get().songSettings.titleSlideVerticalAlignment)
        assertEquals(SongSettings().lyricsAlignment, get().songSettings.lyricsAlignment, "not the lyrics'")
    }
}
