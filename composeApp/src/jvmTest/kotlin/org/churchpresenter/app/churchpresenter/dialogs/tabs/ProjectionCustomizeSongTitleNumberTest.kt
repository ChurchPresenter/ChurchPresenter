@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The Song pane's title and number: when each appears, how big the title is, and which corner the
 * number sits in — each on both stored profiles.
 */
class ProjectionCustomizeSongTitleNumberTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        songSettings = SongSettings(
            titleDisplay = Constants.FIRST_PAGE,
            titleLowerThirdDisplay = Constants.NONE,
            titleFontSize = 47,
            titleLowerThirdFontSize = 48,
            showNumber = Constants.FIRST_PAGE,
            showNumberLowerThird = Constants.NONE,
            songNumberCorner = Constants.TOP_LEFT,
            songNumberLowerThirdCorner = Constants.BOTTOM_RIGHT,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.stored(): SongSettings =
        assertNotNull(projectionSettings.screenAssignments[0].songOverride, "the output must have its own Songs")

    private fun ComposeUiTest.openTitle(override: Boolean = true) =
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE, override = override)

    private fun ComposeUiTest.openNumber(override: Boolean = true) =
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER, override = override)

    // ── What each element offers ──────────────────────────────────────────────

    @Test
    fun `the title offers when it shows and how big it is`() {
        projectionTab(output()) { _ ->
            openTitle(override = false)
            // Not "None": the Display column on the tab behind the dialog carries it too, and the
            // finder spans every root.
            onNodeWithText("First Page").assertExists()
            onNodeWithText("Every Page").assertExists()
            // The size field is found by the number it is showing; its caption is drawn inside it.
            assertNumberFieldShows(47, "the title's size field")
        }
    }

    @Test
    fun `the number offers when it shows and which corner`() {
        projectionTab(output()) { _ ->
            openNumber(override = false)
            onNodeWithText("Corner").assertExists()
            onNodeWithText("Top Left").assertExists()
        }
    }

    @Test
    fun `the number sets no size of its own`() {
        projectionTab(output()) { _ ->
            openTitle(override = false)
            assertNumberFieldShows(47, "the title's size field")

            openElement(CustomizeElement.SONG_NUMBER)
            assertEquals(
                0,
                onAllNodes(hasSetTextAction() and hasText("47")).fetchSemanticsNodes(false).size,
                "the number element draws no size field of its own",
            )
        }
    }

    @Test
    fun `neither carries a color or a style row`() {
        for (element in listOf(CustomizeElement.SONG_TITLE, CustomizeElement.SONG_NUMBER)) {
            projectionTab(output()) { _ ->
                openCustomizePane(CustomizePane.SONGS, element, override = false)
                onNodeWithText("B").assertDoesNotExist()
            }
        }
    }

    // ── When the title shows ──────────────────────────────────────────────────

    @Test
    fun `showing the title on every page writes the full screen's own`() {
        projectionTab(output()) { get ->
            openTitle()
            chooseSegment("Every Page")

            val stored = get().stored()
            assertEquals(Constants.EVERY_PAGE, stored.titleDisplay)
            assertEquals(Constants.NONE, stored.titleLowerThirdDisplay, "the band's own must be untouched")
        }
    }

    @Test
    fun `moving the title back to the first page writes the full screen's own`() {
        val everyPage = output().let {
            it.copy(songSettings = it.songSettings.copy(titleDisplay = Constants.EVERY_PAGE))
        }
        projectionTab(everyPage) { get ->
            openTitle()
            chooseSegment("First Page")

            assertEquals(Constants.FIRST_PAGE, get().stored().titleDisplay)
        }
    }

    @Test
    fun `showing the title writes the band's own instead`() {
        projectionTab(output(band)) { get ->
            openTitle()
            chooseSegment("First Page")

            val stored = get().stored()
            assertEquals(Constants.FIRST_PAGE, stored.titleLowerThirdDisplay)
            assertEquals(Constants.FIRST_PAGE, stored.titleDisplay, "the full screen's own was already First Page")
        }
    }

    @Test
    fun `the title's show setting round-trips through the choices it can be given`() {
        for (choice in listOf("Every Page" to Constants.EVERY_PAGE, "First Page" to Constants.FIRST_PAGE)) {
            projectionTab(output()) { get ->
                openTitle()
                chooseSegment(choice.first)
                assertEquals(choice.second, get().stored().titleDisplay, choice.first)
            }
        }
    }

    // ── How big the title is ──────────────────────────────────────────────────

    @Test
    fun `the title's size writes the full screen's own`() {
        projectionTab(output()) { get ->
            openTitle()
            retypeNumberField(47, 39)

            val stored = get().stored()
            assertEquals(39, stored.titleFontSize)
            assertEquals(48, stored.titleLowerThirdFontSize)
        }
    }

    @Test
    fun `the title's size writes the band's own instead`() {
        projectionTab(output(band)) { get ->
            openTitle()
            retypeNumberField(48, 21)

            val stored = get().stored()
            assertEquals(21, stored.titleLowerThirdFontSize)
            assertEquals(47, stored.titleFontSize)
        }
    }

    @Test
    fun `a title size outside the range is not stored`() {
        projectionTab(output()) { get ->
            openTitle()
            retypeNumberField(47, 400)

            assertEquals(47, get().stored().titleFontSize)
        }
    }

    @Test
    fun `the title's size leaves the lyrics alone`() {
        projectionTab(output()) { get ->
            openTitle()
            retypeNumberField(47, 39)

            assertEquals(SongSettings().lyricsFontSize, get().stored().lyricsFontSize)
        }
    }

    // ── When the number shows ─────────────────────────────────────────────────

    @Test
    fun `showing the number writes the full screen's own`() {
        projectionTab(output()) { get ->
            openNumber()
            chooseSegment("Every Page")

            val stored = get().stored()
            assertEquals(Constants.EVERY_PAGE, stored.showNumber)
            assertEquals(Constants.NONE, stored.showNumberLowerThird, "the band's own must be untouched")
        }
    }

    @Test
    fun `showing the number writes the band's own instead`() {
        projectionTab(output(band)) { get ->
            openNumber()
            chooseSegment("Every Page")

            val stored = get().stored()
            assertEquals(Constants.EVERY_PAGE, stored.showNumberLowerThird)
            assertEquals(Constants.FIRST_PAGE, stored.showNumber)
        }
    }

    @Test
    fun `moving the number back to the first page writes the full screen's own`() {
        val everyPage = output().let {
            it.copy(songSettings = it.songSettings.copy(showNumber = Constants.EVERY_PAGE))
        }
        projectionTab(everyPage) { get ->
            openNumber()
            chooseSegment("First Page")

            assertEquals(Constants.FIRST_PAGE, get().stored().showNumber)
        }
    }

    // ── Which corner ──────────────────────────────────────────────────────────

    @Test
    fun `the corner dropdown shows the profile in force`() {
        projectionTab(output()) { _ ->
            openNumber(override = false)
            onNodeWithText("Top Left").assertExists()
        }
    }

    @Test
    fun `the band's corner dropdown shows the band's own`() {
        projectionTab(output(band)) { _ ->
            openNumber(override = false)
            onNodeWithText("Bottom Right").assertExists()
        }
    }

    @Test
    fun `the title and the number are independent`() {
        projectionTab(output()) { get ->
            openTitle()
            chooseSegment("Every Page")
            openElement(CustomizeElement.SONG_NUMBER)
            chooseSegment("Every Page")

            val stored = get().stored()
            assertEquals(Constants.EVERY_PAGE, stored.titleDisplay)
            assertEquals(Constants.EVERY_PAGE, stored.showNumber)
            assertEquals(Constants.NONE, stored.titleLowerThirdDisplay, "neither band profile moved")
            assertEquals(Constants.NONE, stored.showNumberLowerThird)
        }
    }

    @Test
    fun `chipping away and back keeps what the title stored`() {
        projectionTab(output()) { get ->
            openTitle()
            retypeNumberField(47, 39)
            openElement(CustomizeElement.SONG_LYRICS)
            openElement(CustomizeElement.SONG_TITLE)

            assertEquals(39, get().stored().titleFontSize)
        }
    }

    @Test
    fun `the number's own settings survive the title being edited`() {
        projectionTab(output()) { get ->
            openNumber()
            chooseSegment("Every Page")
            openElement(CustomizeElement.SONG_TITLE)
            retypeNumberField(47, 39)

            val stored = get().stored()
            assertEquals(Constants.EVERY_PAGE, stored.showNumber)
            assertEquals(Constants.TOP_LEFT, stored.songNumberCorner)
        }
    }
}
