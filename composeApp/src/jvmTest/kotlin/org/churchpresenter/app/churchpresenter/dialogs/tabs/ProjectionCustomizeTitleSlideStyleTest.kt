@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The style controls of one title-slide element: face, colour, the button row, alignment and the
 * typography sliders.
 *
 * `ProjectionCustomizeTitleSlideTest` covers the slide-wide switches, the element picker and the
 * size. These are the controls below them, which write the same profile the lyric slides draw the
 * title with — so each test also checks the band's copy of that profile did not move, a full-screen
 * output having no business writing it.
 */
class ProjectionCustomizeTitleSlideStyleTest {

    /** Distinct values throughout, so each control can be found by what it is showing. */
    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        songSettings = SongSettings(
            titleSlideEnabled = true,
            titleFontSize = 44,
            titleColor = "#AA3311",
            titleLetterSpacing = 3,
            titleWordSpacing = 7,
            titleLowerThirdFontSize = 21,
            titleLowerThirdColor = "#225588",
        ),
        projectionSettings = ProjectionSettings(screenAssignments = listOf(ScreenAssignment(displayMode = mode))),
    )

    /**
     * What this output actually draws songs with.
     *
     * An override stopped being a whole `SongSettings` snapshot and became a sparse tree of what the
     * screen changed, so it is resolved against the document rather than read as settings.
     */
    private fun AppSettings.stored(): SongSettings {
        val assignment = projectionSettings.screenAssignments[0]
        assertNotNull(assignment.songOverride, "the output must have its own Songs")
        return resolvedFor(assignment).songSettings
    }

    /** The profile the pane edits: the title, on a full screen. The picker opens on it. */
    private fun AppSettings.storedTitle(): SongElementStyle =
        stored().elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN)

    private fun AppSettings.storedBandTitle(): SongElementStyle =
        stored().elementStyle(SongStyleElement.TITLE, SongStyleTarget.LOWER_THIRD)

    @Test
    fun `the colour writes the title profile`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            recolor("#AA3311", "#112233")

            assertEquals("#112233", get().storedTitle().color)
            assertEquals("#225588", get().storedBandTitle().color, "the band's own title is untouched")
        }
    }

    @Test
    fun `the face writes the title profile`() {
        val picked = uniquelyNamedFont()
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            pickFont("Arial", picked)

            assertEquals(picked, get().storedTitle().fontType)
        }
    }

    @Test
    fun `italic and underline write the title profile`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            for (glyph in listOf("I", "U")) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val style = get().storedTitle()
            assertTrue(style.italic && style.underline)
            assertFalse(style.bold, "the button beside them must not have been pressed")
            assertFalse(get().storedBandTitle().italic, "the band's own title is untouched")
        }
    }

    @Test
    fun `strikethrough and shadow are two controls, not one`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            // The only "S" beside B/I/U is the line through the letters. Shadow is the checkbox on
            // the row below, which is where its colour, size and opacity fold out from.
            styleButton(group = 0, label = SHADOW_GLYPH).performScrollTo().performClick()
            waitForIdle()
            assertTrue(get().storedTitle().strikethrough, "the S is the strikethrough")
            assertFalse(get().storedTitle().shadow, "and it is not the shadow")

            shadowCheckbox(group = 0).performScrollTo().performClick()
            waitForIdle()
            assertTrue(get().storedTitle().shadow, "the checkbox is the shadow")
            assertTrue(get().storedTitle().strikethrough, "which leaves the S on")
        }
    }

    @Test
    fun `the outline button strokes the title's glyphs`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onNodeWithContentDescription("Outline").performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().storedTitle().outline.enabled)
            assertFalse(get().storedBandTitle().outline.enabled, "the band's own title is untouched")
        }
    }

    @Test
    fun `the text-backing button writes the title's backdrop`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            onNodeWithContentDescription("Text backing").performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().storedTitle().backdrop.lineBackground, "the chip's fallback look is a fill")
        }
    }

    @Test
    fun `the alignment writes the title profile`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            horizontalAlignButton(group = 0, which = HAlign.RIGHT).performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.RIGHT, get().storedTitle().horizontalAlignment)
            assertEquals(
                Constants.CENTER,
                get().storedBandTitle().horizontalAlignment,
                "the band's own title is untouched",
            )
        }
    }

    @Test
    fun `the case picker writes the title's transform`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            chooseSegment("UPPERCASE")

            assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, get().storedTitle().transform)
        }
    }

    @Test
    fun `the spacing sliders write the title profile`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE_SLIDE)
            tapSliderTrack("Letter spacing", "3px", fraction = 0.5f)
            assertEquals(40, get().storedTitle().letterSpacing, "halfway along a -20..100 track")
            assertEquals(7, get().storedTitle().wordSpacing, "the slider below it must not move")

            tapSliderTrack("Word spacing", "7px", fraction = 0.5f)
            assertEquals(40, get().storedTitle().wordSpacing)
            assertEquals(40, get().storedTitle().letterSpacing, "nor the one above it, afterwards")
        }
    }
}
