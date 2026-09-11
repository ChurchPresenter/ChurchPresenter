@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Title Slide chip of the Customize dialog's Song pane: this output's own title slide, with
 * the controls the global tab's title-slide view has, and the Apply button beside Done.
 */
class ProjectionCustomizeTitleSlideTest {

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        songSettings = SongSettings(
            titleSlideEnabled = true,
            titleSlideAuthor = SongCreditStyle(fontSize = 34),
            titleSlideAuthorLowerThird = SongCreditStyle(fontSize = 20),
        ),
        projectionSettings = ProjectionSettings(screenAssignments = listOf(ScreenAssignment(displayMode = mode))),
    )

    private fun AppSettings.stored(): SongSettings =
        assertNotNull(projectionSettings.screenAssignments[0].songOverride, "the output must have its own Songs")

    @Test
    fun `the title slide is the first chip of the Song pane`() {
        assertEquals(CustomizeElement.SONG_TITLE_SLIDE, customizeElements(CustomizePane.SONGS).first())
    }

    @Test
    fun `the chip shows the title slide on the stage`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onNodeWithText("Title slide preview").assertExists()

            openElement(CustomizeElement.SONG_LYRICS)
            onAllNodesWithText("Title slide preview").assertCountEquals(0)
        }
    }

    @Test
    fun `the slide-wide switches write this output's override`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            toggleCheckbox("Show song number before title")
            onAllNodesWithContentDescription("Align Top").onFirst().performClick()
            waitForIdle()

            val stored = get().stored()
            assertFalse(stored.titleSlideNumberBeforeTitle)
            assertEquals(Constants.TOP, stored.titleSlideVerticalAlignment)
            assertTrue(get().songSettings.titleSlideNumberBeforeTitle, "the global settings are untouched")
        }
    }

    @Test
    fun `the band has no vertical alignment to offer`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onAllNodesWithContentDescription("Align Top").assertCountEquals(0)
        }
    }

    @Test
    fun `an element's show switch writes its own flag`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            chooseSegment("Composer")
            toggleCheckbox("Show on title slide")

            val stored = get().stored()
            assertFalse(stored.titleSlideShowComposer)
            assertTrue(stored.titleSlideShowAuthor)
        }
    }

    @Test
    fun `a credit's size writes the profile for this output's shape`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            chooseSegment("Author")
            retypeNumberField(34, 41)

            val stored = get().stored()
            assertEquals(41, stored.titleSlideAuthor.fontSize)
            assertEquals(20, stored.titleSlideAuthorLowerThird.fontSize, "the band's own profile is untouched")
        }
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            chooseSegment("Author")
            retypeNumberField(20, 23)

            val stored = get().stored()
            assertEquals(23, stored.titleSlideAuthorLowerThird.fontSize)
            assertEquals(34, stored.titleSlideAuthor.fontSize, "the screen's own profile is untouched")
        }
    }

    @Test
    fun `the title's style buttons write the title profile the lyric slides share`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            // The chip row has a "Title" of its own; the picker's is the second on screen.
            chooseSegment("Title", nth = 1)
            styleButton(group = 0, label = "B").performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().stored().titleBold)
        }
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    @Test
    fun `Apply is offered only where there is a draft to apply, and runs it`() {
        var applied = 0
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalApplySettings provides { applied++ }) {
                    OutputCustomizeDialog(
                        screenLabel = "Screen 1",
                        assignment = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
                        globalSettings = output(),
                        onApply = {},
                        onDismiss = {},
                    )
                }
            }
            onNodeWithTag(CUSTOMIZE_APPLY_TAG).performClick()
            waitForIdle()
            assertEquals(1, applied)
        }
        runComposeUiTest {
            setContent {
                OutputCustomizeDialog(
                    screenLabel = "Screen 1",
                    assignment = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
                    globalSettings = output(),
                    onApply = {},
                    onDismiss = {},
                )
            }
            onNodeWithTag(CUSTOMIZE_APPLY_TAG).assertDoesNotExist()
        }
    }
}
