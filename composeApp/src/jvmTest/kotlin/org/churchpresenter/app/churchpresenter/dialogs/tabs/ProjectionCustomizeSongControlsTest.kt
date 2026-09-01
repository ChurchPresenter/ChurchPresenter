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
 * Driving the Song pane's controls, and reading back what each one stored.
 *
 * The Song category carries five elements over two stored profiles, so the same control set writes
 * ten different fields depending on which chip is up and what shape the output is. Each of these
 * drives one chip and asserts both halves of the pair it touched — the one that should have moved,
 * and the one that should not.
 */
class ProjectionCustomizeSongControlsTest {

    private fun output(
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
        songMode: String = Constants.SONG_LANG_PRIMARY,
    ) = AppSettings(
        songSettings = SongSettings(
            marginTop = 11,
            marginBottom = 22,
            marginLeft = 33,
            marginRight = 44,
            transitionDuration = 555f,
            lowerThirdHeightPercent = 29,
            lyricsFontSize = 61,
            lyricsLowerThirdFontSize = 62,
            lyricsColor = "#AABBCC",
            lyricsLowerThirdColor = "#DDEEFF",
            titleFontSize = 47,
            titleLowerThirdFontSize = 48,
            lookAheadFontSize = 51,
            lowerThirdLookAheadFontSize = 52,
            lookAheadColor = "#445566",
            lowerThirdLookAheadColor = "#556677",
            lookAheadNextFontSize = 57,
            lowerThirdLookAheadNextFontSize = 58,
            lookAheadNextColor = "#667788",
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode, songMode = songMode)),
        ),
    )

    private fun AppSettings.stored(): SongSettings =
        assertNotNull(projectionSettings.screenAssignments[0].songOverride, "the output must have its own Songs")

    // ── The lyrics ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `size, auto-fit, colour and the style quartet write the full-screen lyrics`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS)
            retypeNumberField(61, 72)
            toggleCheckbox("Auto")
            recolor("#AABBCC", "#112233")
            for (glyph in listOf("B", "I", "U", SHADOW_GLYPH)) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().stored()
            assertEquals(72, stored.lyricsFontSize)
            assertFalse(stored.lyricsFontSizeAutoFit, "auto-fit was on and must have gone off")
            assertEquals("#112233", stored.lyricsColor)
            assertTrue(stored.lyricsBold && stored.lyricsItalic && stored.lyricsUnderline && stored.lyricsShadow)
            assertEquals(62, stored.lyricsLowerThirdFontSize, "the band's own size must be untouched")
            assertEquals("#DDEEFF", stored.lyricsLowerThirdColor, "and its colour with it")
        }
    }

    @Test
    fun `the same controls write the band's lyrics instead`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.SONGS)
            retypeNumberField(62, 26)
            recolor("#DDEEFF", "#334455")
            styleButton(group = 0, label = "B").performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(26, stored.lyricsLowerThirdFontSize)
            assertEquals("#334455", stored.lyricsLowerThirdColor)
            assertTrue(stored.lyricsLowerThirdBold)
            assertEquals(61, stored.lyricsFontSize, "the full screen's own size must be untouched")
            assertFalse(stored.lyricsBold, "and its own styling with it")
        }
    }

    @Test
    fun `the alignments and the case picker write the lyrics`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS)
            horizontalAlignButton(group = 0, which = HAlign.LEFT).performScrollTo().performClick()
            waitForIdle()
            onNodeWithContentDescription("Align Top").performScrollTo().performClick()
            waitForIdle()
            chooseSegment("AA")

            val stored = get().stored()
            assertEquals(Constants.LEFT, stored.lyricsHorizontalAlignment)
            assertEquals(Constants.TOP, stored.lyricsAlignment, "the vertical alignment is one value, not two")
            assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, stored.lyricsTransform)
        }
    }

    // ── Title, number and the two look-aheads ───────────────────────────────────────────────────

    @Test
    fun `the title element writes when it shows and how big`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            chooseSegment("Every Page")
            retypeNumberField(47, 39)

            val stored = get().stored()
            assertEquals(Constants.EVERY_PAGE, stored.titleDisplay)
            assertEquals(39, stored.titleFontSize)
            assertEquals(48, stored.titleLowerThirdFontSize, "the band's title size must be untouched")
        }
    }

    @Test
    fun `the title element writes the band's own pair`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            chooseSegment("Every Page")
            retypeNumberField(48, 21)

            val stored = get().stored()
            assertEquals(Constants.EVERY_PAGE, stored.titleLowerThirdDisplay)
            assertEquals(21, stored.titleLowerThirdFontSize)
            assertEquals(47, stored.titleFontSize, "the full screen's title size must be untouched")
        }
    }

    @Test
    fun `the number element writes when the number shows`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            chooseSegment("Every Page")

            assertEquals(Constants.EVERY_PAGE, get().stored().showNumber)
        }
    }

    @Test
    fun `the look-ahead element writes its size, colour, styling and alignment`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            retypeNumberField(51, 46)
            toggleCheckbox("Auto")
            recolor("#445566", "#667788")
            styleButton(group = 0, label = "I").performScrollTo().performClick()
            waitForIdle()
            horizontalAlignButton(group = 0, which = HAlign.RIGHT).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(46, stored.lookAheadFontSize)
            assertFalse(stored.lookAheadFontSizeAutoFit)
            assertEquals("#667788", stored.lookAheadColor)
            assertTrue(stored.lookAheadItalic)
            assertEquals(Constants.RIGHT, stored.lookAheadHorizontalAlignment)
            assertEquals(52, stored.lowerThirdLookAheadFontSize, "the band's look-ahead must be untouched")
        }
    }

    @Test
    fun `the next-section element writes its own size, colour and styling`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            retypeNumberField(57, 43)
            recolor("#667788", "#889900")
            styleButton(group = 0, label = SHADOW_GLYPH).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(43, stored.lookAheadNextFontSize)
            assertEquals("#889900", stored.lookAheadNextColor)
            assertTrue(stored.lookAheadNextShadow)
            assertEquals(58, stored.lowerThirdLookAheadNextFontSize, "the band's next section must be untouched")
        }
    }

    @Test
    fun `the look-ahead elements write the band's own profile`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            retypeNumberField(52, 25)
            recolor("#556677", "#998877")
            openElement(CustomizeElement.SONG_NEXT_SECTION)
            retypeNumberField(58, 23)

            val stored = get().stored()
            assertEquals(25, stored.lowerThirdLookAheadFontSize)
            assertEquals("#998877", stored.lowerThirdLookAheadColor)
            assertEquals(23, stored.lowerThirdLookAheadNextFontSize)
            assertEquals(51, stored.lookAheadFontSize, "the full screen's look-ahead must be untouched")
        }
    }

    // ── The strip under the preview ─────────────────────────────────────────────────────────────

    @Test
    fun `the strip writes the margins, the fades and their duration`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS)
            retypeNumberField(11, 12)
            retypeNumberField(22, 23)
            retypeNumberField(33, 34)
            retypeNumberField(44, 45)
            toggleCheckbox("Fade In", scroll = false)
            toggleCheckbox("Crossfade", scroll = false)
            retypeNumberField(555, 620)

            val stored = get().stored()
            assertEquals(
                listOf(12, 23, 34, 45),
                listOf(stored.marginTop, stored.marginBottom, stored.marginLeft, stored.marginRight),
            )
            assertFalse(stored.fadeIn, "Fade In was on and must have gone off")
            assertTrue(stored.crossfade, "Crossfade was off and must have come on")
            assertEquals(620f, stored.transitionDuration)
        }
    }

    @Test
    fun `a bilingual band's strip writes its height and how the two languages sit`() {
        val bilingualBand = output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, Constants.SONG_LANG_BOTH)
        projectionTab(bilingualBand) { get ->
            openCustomizePane(CustomizePane.SONGS)
            retypeNumberField(29, 40)
            chooseSegment("Top / Bottom", scroll = false)

            val stored = get().stored()
            assertEquals(40, stored.lowerThirdHeightPercent)
            assertEquals(Constants.BILINGUAL_TOP_BOTTOM, stored.bilingualLayout)
        }
    }

    @Test
    fun `a single-language output is offered no bilingual layout`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, override = false)
            onNodeWithText("Top / Bottom").assertDoesNotExist()
        }
    }
}
