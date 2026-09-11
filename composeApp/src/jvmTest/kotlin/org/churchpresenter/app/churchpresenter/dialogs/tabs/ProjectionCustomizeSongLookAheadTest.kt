@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Song pane's look-ahead elements on both stored profiles: the face buttons, the next section's
 * quartet, the font pickers and the typography row.
 */
class ProjectionCustomizeSongLookAheadTest {

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        songSettings = SongSettings(
            lookAheadFontType = SENTINEL_FONT,
            lowerThirdLookAheadFontType = SENTINEL_FONT,
            lookAheadNextFontType = SENTINEL_FONT,
            lowerThirdLookAheadNextFontType = SENTINEL_FONT,
            lyricsFontType = SENTINEL_FONT,
            lyricsLowerThirdFontType = SENTINEL_FONT,
            lookAheadFontSize = 51,
            lowerThirdLookAheadFontSize = 52,
            lookAheadNextFontSize = 57,
            lowerThirdLookAheadNextFontSize = 58,
            lyricsLetterSpacing = 3,
            lyricsLowerThirdLetterSpacing = 4,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun AppSettings.stored(): SongSettings =
        assertNotNull(projectionSettings.screenAssignments[0].songOverride, "the output must have its own Songs")

    // ── The look-ahead line's face buttons ──────────────────────────────────────────────────────

    @Test
    fun `the look-ahead style quartet writes the full screen's own flags`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            for (glyph in listOf("B", "I", "U", SHADOW_GLYPH)) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().stored()
            assertTrue(
                stored.lookAheadBold && stored.lookAheadItalic &&
                    stored.lookAheadUnderline && stored.lookAheadShadow,
                "all four faces must be on",
            )
        }
    }

    @Test
    fun `styling the full screen's look-ahead leaves the band's alone`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            styleButton(group = 0, label = "B").performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertTrue(stored.lookAheadBold)
            assertFalse(stored.lowerThirdLookAheadBold, "the band's look-ahead must be untouched")
        }
    }

    @Test
    fun `the same quartet writes the band's look-ahead instead`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            for (glyph in listOf("B", "U", SHADOW_GLYPH)) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().stored()
            assertTrue(
                stored.lowerThirdLookAheadBold && stored.lowerThirdLookAheadUnderline &&
                    stored.lowerThirdLookAheadShadow,
            )
            assertFalse(stored.lookAheadBold, "the full screen's look-ahead must be untouched")
        }
    }

    @Test
    fun `the band's look-ahead alignment is its own`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            horizontalAlignButton(group = 0, which = HAlign.LEFT).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(Constants.LEFT, stored.lowerThirdLookAheadHorizontalAlignment)
            assertEquals(
                Constants.CENTER,
                stored.lookAheadHorizontalAlignment,
                "the full screen's look-ahead keeps its own alignment",
            )
        }
    }

    @Test
    fun `the band's look-ahead auto-fit is its own`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            toggleCheckbox("Auto")

            val stored = get().stored()
            assertFalse(stored.lowerThirdLookAheadFontSizeAutoFit, "auto-fit was on and must have gone off")
            assertTrue(stored.lookAheadFontSizeAutoFit, "the full screen's auto-fit must be untouched")
        }
    }

    // ── The next section ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the next section's quartet writes the full screen's own flags`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            // Not Italic: the next section ships italic, so clicking it would test the same button
            // in the other direction. The Italic test below does that deliberately.
            for (glyph in listOf("B", "U", SHADOW_GLYPH)) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().stored()
            assertTrue(
                stored.lookAheadNextBold && stored.lookAheadNextUnderline && stored.lookAheadNextShadow,
            )
            assertFalse(stored.lowerThirdLookAheadNextBold, "the band's next section must be untouched")
        }
    }

    @Test
    fun `the next section ships italic and the button turns it off`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            styleButton(group = 0, label = "I").performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertFalse(stored.lookAheadNextItalic, "Italic was on and must have gone off")
            assertTrue(stored.lowerThirdLookAheadNextItalic, "the band's next section is still italic")
        }
    }

    @Test
    fun `the next section's quartet writes the band's own flags`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            for (glyph in listOf("U", SHADOW_GLYPH)) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().stored()
            assertTrue(stored.lowerThirdLookAheadNextUnderline && stored.lowerThirdLookAheadNextShadow)
            assertFalse(stored.lookAheadNextUnderline, "the full screen's next section must be untouched")
        }
    }

    @Test
    fun `the next section has no alignment of its own`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            assertEquals(
                0,
                horizontalAlignButtons().fetchSemanticsNodes().size,
                "the next section follows the look-ahead line it sits under",
            )
        }
    }

    // ── The font pickers ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the look-ahead font picker writes the full screen's family`() {
        val family = uniquelyNamedFont()
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.lookAheadFontType)
            assertEquals(SENTINEL_FONT, stored.lowerThirdLookAheadFontType, "the band keeps its own face")
        }
    }

    @Test
    fun `the look-ahead font picker writes the band's family`() {
        val family = uniquelyNamedFont()
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.lowerThirdLookAheadFontType)
            assertEquals(SENTINEL_FONT, stored.lookAheadFontType, "the full screen keeps its own face")
        }
    }

    @Test
    fun `the next section's font picker writes the full screen's family`() {
        val family = uniquelyNamedFont()
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.lookAheadNextFontType)
            assertEquals(SENTINEL_FONT, stored.lowerThirdLookAheadNextFontType)
        }
    }

    @Test
    fun `the next section's font picker writes the band's family`() {
        val family = uniquelyNamedFont()
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.lowerThirdLookAheadNextFontType)
            assertEquals(SENTINEL_FONT, stored.lookAheadNextFontType)
        }
    }

    @Test
    fun `the lyrics font picker writes the full screen's family`() {
        val family = uniquelyNamedFont()
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.lyricsFontType)
            assertEquals(SENTINEL_FONT, stored.lyricsLowerThirdFontType)
        }
    }

    @Test
    fun `the lyrics font picker writes the band's family`() {
        val family = uniquelyNamedFont()
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.lyricsLowerThirdFontType)
            assertEquals(SENTINEL_FONT, stored.lyricsFontType)
        }
    }

    // ── The typography row under the lyrics ─────────────────────────────────────────────────────

    @Test
    fun `the case picker writes the full screen's lyrics`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            chooseSegment("aa")

            val stored = get().stored()
            assertEquals(Constants.TEXT_TRANSFORM_LOWERCASE, stored.lyricsTransform)
            assertEquals(
                Constants.TEXT_TRANSFORM_NONE,
                stored.lyricsLowerThirdTransform,
                "the band's own case must be untouched",
            )
        }
    }

    @Test
    fun `the case picker writes the band's lyrics`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            chooseSegment("Aa")

            val stored = get().stored()
            assertEquals(Constants.TEXT_TRANSFORM_CAPITALIZE, stored.lyricsLowerThirdTransform)
            assertEquals(
                Constants.TEXT_TRANSFORM_NONE,
                stored.lyricsTransform,
                "the full screen's own case must be untouched",
            )
        }
    }

    @Test
    fun `the typography row is offered on both shapes`() {
        for (mode in listOf(Constants.DISPLAY_MODE_FULLSCREEN, band)) {
            projectionTab(output(mode)) { _ ->
                openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
                onNodeWithText("Aa").assertExists("the case picker belongs to every shape of output")
            }
        }
    }

    @Test
    fun `the lyrics element also carries the vertical alignment the look-ahead has not`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            onNodeWithContentDescription("Align Top").assertExists()
        }
    }
}
