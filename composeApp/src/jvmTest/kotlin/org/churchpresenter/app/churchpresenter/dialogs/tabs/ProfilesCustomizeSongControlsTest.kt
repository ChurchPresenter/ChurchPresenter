@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Driving the Song pane's controls, and reading back what each one stored.
 *
 * The Song category carries five elements over two stored profiles, so the same control set writes
 * ten different fields depending on which chip is up and what shape the profile is. Each of these
 * drives one chip and asserts both halves of the pair it touched -- the one that should have moved,
 * and the one that should not.
 *
 * Ported from `ProjectionCustomizeSongControlsTest`; the song language now lives on the profile
 * rather than on a `ScreenAssignment`.
 */
class ProfilesCustomizeSongControlsTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
        songMode: String = Constants.SONG_LANG_PRIMARY,
    ) = profileDocument(
        mode = mode,
        // The look-ahead on, or its chip is not offered at all.
        profile = OutputProfile(songMode = songMode, songLookAhead = true),
        song = SongSettings(
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
    )

    // ── The lyrics ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `size, auto-fit, colour and the style quartet write the full-screen lyrics`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(61, 72)
            toggleCheckbox("Auto")
            recolor("#AABBCC", "#112233")
            for (glyph in listOf("B", "I", "U")) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }
            shadowCheckbox(group = 0).performScrollTo().performClick()
            waitForIdle()

            val stored = get().song()
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
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(62, 26)
            recolor("#DDEEFF", "#334455")
            styleButton(group = 0, label = "B").performScrollTo().performClick()
            waitForIdle()

            val stored = get().song()
            assertEquals(26, stored.lyricsLowerThirdFontSize)
            assertEquals("#334455", stored.lyricsLowerThirdColor)
            assertTrue(stored.lyricsLowerThirdBold)
            assertEquals(61, stored.lyricsFontSize, "the full screen's own size must be untouched")
            assertFalse(stored.lyricsBold, "and its own styling with it")
        }
    }

    @Test
    fun `the alignments and the case picker write the lyrics`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            horizontalAlignButton(group = 0, which = HAlign.LEFT).performScrollTo().performClick()
            waitForIdle()
            onNodeWithContentDescription("Align Top").performScrollTo().performClick()
            waitForIdle()
            chooseSegment("UPPERCASE")

            val stored = get().song()
            assertEquals(Constants.LEFT, stored.lyricsHorizontalAlignment)
            assertEquals(Constants.TOP, stored.lyricsAlignment, "the vertical alignment is one value, not two")
            assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, stored.lyricsTransform)
        }
    }

    // ── Title, number and the two look-aheads ───────────────────────────────────────────────────

    @Test
    fun `the title element writes when it shows and how big`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            chooseSegment("Every Page")
            retypeNumberField(47, 39)

            val stored = get().song()
            assertEquals(Constants.EVERY_PAGE, stored.titleDisplay)
            assertEquals(39, stored.titleFontSize)
            assertEquals(48, stored.titleLowerThirdFontSize, "the band's title size must be untouched")
        }
    }

    @Test
    fun `the title element writes the band's own pair`() {
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            chooseSegment("Every Page")
            retypeNumberField(48, 21)

            val stored = get().song()
            assertEquals(Constants.EVERY_PAGE, stored.titleLowerThirdDisplay)
            assertEquals(21, stored.titleLowerThirdFontSize)
            assertEquals(47, stored.titleFontSize, "the full screen's title size must be untouched")
        }
    }

    @Test
    fun `the number element writes when the number shows`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            chooseSegment("Every Page")

            assertEquals(Constants.EVERY_PAGE, get().song().showNumber)
        }
    }

    @Test
    fun `the look-ahead element writes its size, colour, styling and alignment`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            retypeNumberField(51, 46)
            toggleCheckbox("Auto")
            recolor("#445566", "#667788")
            styleButton(group = 0, label = "I").performScrollTo().performClick()
            waitForIdle()
            horizontalAlignButton(group = 0, which = HAlign.RIGHT).performScrollTo().performClick()
            waitForIdle()

            val stored = get().song()
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
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            retypeNumberField(57, 43)
            recolor("#667788", "#889900")
            shadowCheckbox(group = 0).performScrollTo().performClick()
            waitForIdle()

            val stored = get().song()
            assertEquals(43, stored.lookAheadNextFontSize)
            assertEquals("#889900", stored.lookAheadNextColor)
            assertTrue(stored.lookAheadNextShadow)
            assertEquals(58, stored.lowerThirdLookAheadNextFontSize, "the band's next section must be untouched")
        }
    }

    @Test
    fun `the look-ahead elements write the band's own profile`() {
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LOOK_AHEAD)
            retypeNumberField(52, 25)
            recolor("#556677", "#998877")
            openElement(CustomizeElement.SONG_NEXT_SECTION)
            retypeNumberField(58, 23)

            val stored = get().song()
            assertEquals(25, stored.lowerThirdLookAheadFontSize)
            assertEquals("#998877", stored.lowerThirdLookAheadColor)
            assertEquals(23, stored.lowerThirdLookAheadNextFontSize)
            assertEquals(51, stored.lookAheadFontSize, "the full screen's look-ahead must be untouched")
        }
    }

    // ── The strip under the preview ─────────────────────────────────────────────────────────────

    @Test
    fun `the strip writes the margins, the fades and their duration`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(11, 12)
            retypeNumberField(22, 23)
            retypeNumberField(33, 34)
            retypeNumberField(44, 45)
            toggleCheckbox("Fade In", scroll = false)
            toggleCheckbox("Crossfade", scroll = false)
            retypeNumberField(555, 620)

            val stored = get().song()
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
    fun `a band's strip writes its height`() {
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(29, 40)

            assertEquals(40, get().song().lowerThirdHeightPercent)
        }
    }

    /**
     * The arrangement control moved off the strip and into the pane, under the Lyrics chip, beside
     * the vertical alignment it belongs with. It is drawn only where the profile shows both
     * languages -- a profile narrowed to one has nothing to arrange.
     */
    @Test
    fun `a bilingual profile is offered the arrangement in the Lyrics pane`() {
        profilesTab(output(band, Constants.SONG_LANG_BOTH)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            chooseSegment("Top / Bottom")

            assertEquals(Constants.BILINGUAL_TOP_BOTTOM, get().song().bilingualLayout)
        }
    }

    @Test
    fun `a single-language profile is offered no arrangement at all`() {
        profilesTab(output(band, Constants.SONG_LANG_PRIMARY)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onAllNodesWithText("Top / Bottom").assertCountEquals(0)
        }
    }
}
