@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives every control in the Lyrics, Fullscreen Display and Lower Third Display sections — the
 * tab's right-hand column — asserting both the value written into [SongSettings] and the on-screen
 * result.
 *
 * The two "Auto" push-buttons next to the lyric font sizes are the only controls on the tab that
 * need a live [PresenterManager]: they are rendered only when one is supplied, stay disabled until
 * lyrics are actually live on a matching output, and then measure the live verse to pick a size.
 * All three of those states are covered here.
 */
class SongSettingsTabLyricsTest {

    private fun settingsWith(change: SongSettings.() -> SongSettings): AppSettings =
        AppSettings().let { it.copy(songSettings = it.songSettings.change()) }

    // ── Lyrics ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the word-wrap checkbox sets the flag`() = songTab { get ->
        onNodeWithTag("song_wordWrap").performScrollTo().assertIsOff().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.wordWrap, "the checkbox must set word wrap")
        onNodeWithTag("song_wordWrap").assertIsOn()
    }

    @Test
    fun `the word-wrap checkbox clears the flag again`() =
        songTab(initial = settingsWith { copy(wordWrap = true) }) { get ->
            onNodeWithTag("song_wordWrap").performScrollTo().assertIsOn().performClick()
            waitForIdle()
            assertEquals(false, get().songSettings.wordWrap, "the checkbox must clear word wrap")
            onNodeWithTag("song_wordWrap").assertIsOff()
        }

    @Test
    fun `the lyrics vertical alignment buttons store top middle and bottom`() = songTab { get ->
        onNodeWithContentDescription("Align Top").performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.TOP, get().songSettings.lyricsAlignment, "top must be stored")

        onNodeWithContentDescription("Align Bottom").performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.BOTTOM, get().songSettings.lyricsAlignment, "bottom must be stored")

        onNodeWithContentDescription("Align Middle").performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.MIDDLE, get().songSettings.lyricsAlignment, "middle must be stored")
    }

    // ── Fullscreen display ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the fullscreen display mode row switches to one line`() = songTab { get ->
        segmentedButton("1 Verse", ModeRow.FULLSCREEN).performScrollTo().assertIsSelected()
        segmentedButton("1 Line", ModeRow.FULLSCREEN).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_DISPLAY_MODE_LINE,
            get().songSettings.fullscreenDisplayMode,
            "picking 1 Line must be stored",
        )
        segmentedButton("1 Line", ModeRow.FULLSCREEN).assertIsSelected()
        segmentedButton("1 Verse", ModeRow.FULLSCREEN).assertIsNotSelected()
    }

    @Test
    fun `the fullscreen display mode row switches back to one verse`() =
        songTab(initial = settingsWith { copy(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE) }) { get ->
            segmentedButton("1 Verse", ModeRow.FULLSCREEN).performScrollTo().assertIsNotSelected().performClick()
            waitForIdle()
            assertEquals(
                Constants.SONG_DISPLAY_MODE_VERSE,
                get().songSettings.fullscreenDisplayMode,
                "picking 1 Verse must be stored",
            )
            segmentedButton("1 Verse", ModeRow.FULLSCREEN).assertIsSelected()
        }

    @Test
    fun `the fullscreen language row switches to primary only`() = songTab { get ->
        segmentedButton("Both", ModeRow.FULLSCREEN).performScrollTo().assertIsSelected()
        segmentedButton("Primary", ModeRow.FULLSCREEN).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_LANG_PRIMARY,
            get().songSettings.fullscreenLanguageDisplay,
            "picking Primary must be stored",
        )
        segmentedButton("Primary", ModeRow.FULLSCREEN).assertIsSelected()
    }

    @Test
    fun `the fullscreen language row switches to secondary only`() = songTab { get ->
        segmentedButton("Secondary", ModeRow.FULLSCREEN).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_LANG_SECONDARY,
            get().songSettings.fullscreenLanguageDisplay,
            "picking Secondary must be stored",
        )
        assertEquals(
            Constants.SONG_LANG_BOTH,
            get().songSettings.lowerThirdLanguageDisplay,
            "the lower-third row must be untouched",
        )
        segmentedButton("Secondary", ModeRow.FULLSCREEN).assertIsSelected()
    }

    @Test
    fun `the lyrics fullscreen font size field stores a new size`() =
        songTab(initial = settingsWith { copy(lyricsFontSize = 115) }) { get ->
            retypeNumberField(showing = 115, to = 88)
            assertEquals(88, get().songSettings.lyricsFontSize, "the lyrics size must be stored")
            assertNumberFieldShows(88, "the lyrics fullscreen font size")
        }

    @Test
    fun `the lyrics fullscreen font size field keeps the stored size when given one outside its range`() =
        songTab(initial = settingsWith { copy(lyricsFontSize = 115) }) { get ->
            retypeNumberField(showing = 115, to = 4)
            assertEquals(115, get().songSettings.lyricsFontSize, "4 is below the 8..150 range and must not be stored")
        }

    @Test
    fun `the lyrics fullscreen auto-fit checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_lyricsFontSizeAutoFit").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.lyricsFontSizeAutoFit, "the checkbox must clear auto-fit")
        onNodeWithTag("song_lyricsFontSizeAutoFit").assertIsOff()
    }

    @Test
    fun `the lyrics fullscreen font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(lyricsFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.lyricsFontType, "the picked family must be stored")
        }

    @Test
    fun `the lyrics fullscreen alignment buttons store left centre and right`() = songTab { get ->
        val group = HAlignGroup.LYRICS_FULLSCREEN
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.CENTER),
            staysUnselected = horizontalAlignButton(group, HAlign.RIGHT),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.lyricsHorizontalAlignment, "left must be stored")

        horizontalAlignButton(group, HAlign.RIGHT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.RIGHT, get().songSettings.lyricsHorizontalAlignment, "right must be stored")

        horizontalAlignButton(group, HAlign.CENTER).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.CENTER, get().songSettings.lyricsHorizontalAlignment, "centre must be stored")
    }

    @Test
    fun `the lyrics fullscreen colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(lyricsColor = "#111213") }) { get ->
            recolor(fromHex = "#111213", toHex = "#30C0D0")
            assertTrue(
                get().songSettings.lyricsColor.equals("#30C0D0", ignoreCase = true),
                "the lyrics colour must be stored, was ${get().songSettings.lyricsColor}",
            )
            onNodeWithText("#30C0D0").assertExists("the field must show the new colour")
        }

    @Test
    fun `the lyrics fullscreen style buttons toggle bold italic and underline`() = songTab { get ->
        val group = StyleGroup.LYRICS_FULLSCREEN
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsBold, "B must set bold")

        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsItalic, "I must set italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsUnderline, "U must set underline")
    }

    @Test
    fun `the lyrics fullscreen shadow button reveals the shadow detail row`() = songTab { get ->
        styleButton(StyleGroup.LYRICS_FULLSCREEN, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsShadow, "S must set the shadow flag")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(1)
    }

    @Test
    fun `the lyrics fullscreen shadow detail fields store their values`() =
        songTab(
            initial = settingsWith {
                copy(
                    lyricsShadow = true,
                    lyricsShadowColor = "#040506",
                    lyricsShadowSize = 125,
                    lyricsShadowOpacity = 79,
                )
            },
        ) { get ->
            retypeNumberField(showing = 125, to = 180)
            assertEquals(180, get().songSettings.lyricsShadowSize, "the shadow size must be stored")
            retypeNumberField(showing = 79, to = 66)
            assertEquals(66, get().songSettings.lyricsShadowOpacity, "the shadow intensity must be stored")
            recolor(fromHex = "#040506", toHex = "#708090")
            assertTrue(
                get().songSettings.lyricsShadowColor.equals("#708090", ignoreCase = true),
                "the shadow colour must be stored",
            )
        }

    // ── Lower third display ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the lower-third display mode row switches to one verse`() = songTab { get ->
        segmentedButton("1 Line", ModeRow.LOWER_THIRD).performScrollTo().assertIsSelected()
        segmentedButton("1 Verse", ModeRow.LOWER_THIRD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_DISPLAY_MODE_VERSE,
            get().songSettings.lowerThirdDisplayMode,
            "picking 1 Verse must be stored",
        )
        assertEquals(
            Constants.SONG_DISPLAY_MODE_VERSE,
            get().songSettings.fullscreenDisplayMode,
            "the fullscreen row must be untouched",
        )
        segmentedButton("1 Verse", ModeRow.LOWER_THIRD).assertIsSelected()
    }

    @Test
    fun `the lower-third display mode row switches back to one line`() =
        songTab(initial = settingsWith { copy(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE) }) { get ->
            segmentedButton("1 Line", ModeRow.LOWER_THIRD).performScrollTo().assertIsNotSelected().performClick()
            waitForIdle()
            assertEquals(
                Constants.SONG_DISPLAY_MODE_LINE,
                get().songSettings.lowerThirdDisplayMode,
                "picking 1 Line must be stored",
            )
            segmentedButton("1 Line", ModeRow.LOWER_THIRD).assertIsSelected()
        }

    @Test
    fun `the lower-third language row switches to primary only`() = songTab { get ->
        segmentedButton("Primary", ModeRow.LOWER_THIRD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_LANG_PRIMARY,
            get().songSettings.lowerThirdLanguageDisplay,
            "picking Primary must be stored",
        )
        segmentedButton("Primary", ModeRow.LOWER_THIRD).assertIsSelected()
    }

    @Test
    fun `the lyrics lower-third font size field stores a new size`() =
        songTab(initial = settingsWith { copy(lyricsLowerThirdFontSize = 116) }) { get ->
            retypeNumberField(showing = 116, to = 36)
            assertEquals(36, get().songSettings.lyricsLowerThirdFontSize, "the lower-third size must be stored")
            assertNumberFieldShows(36, "the lyrics lower-third font size")
        }

    @Test
    fun `the lyrics lower-third auto-fit checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_lyricsLowerThirdFontSizeAutoFit").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.lyricsLowerThirdFontSizeAutoFit, "the checkbox must clear auto-fit")
        assertEquals(true, get().songSettings.lyricsFontSizeAutoFit, "the fullscreen flag must be untouched")
        onNodeWithTag("song_lyricsLowerThirdFontSizeAutoFit").assertIsOff()
    }

    @Test
    fun `the lyrics lower-third font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(lyricsLowerThirdFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.lyricsLowerThirdFontType, "the picked family must be stored")
        }

    @Test
    fun `the lyrics lower-third alignment buttons store left and right`() = songTab { get ->
        val group = HAlignGroup.LYRICS_LOWER_THIRD
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.CENTER),
            staysUnselected = horizontalAlignButton(group, HAlign.RIGHT),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.lyricsLowerThirdHorizontalAlignment, "left must be stored")
        assertEquals(
            Constants.CENTER,
            get().songSettings.lyricsHorizontalAlignment,
            "the fullscreen alignment must be untouched",
        )

        horizontalAlignButton(group, HAlign.RIGHT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.RIGHT, get().songSettings.lyricsLowerThirdHorizontalAlignment, "right must be stored")
    }

    @Test
    fun `the lyrics lower-third colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(lyricsLowerThirdColor = "#141516") }) { get ->
            recolor(fromHex = "#141516", toHex = "#50D060")
            assertTrue(
                get().songSettings.lyricsLowerThirdColor.equals("#50D060", ignoreCase = true),
                "the lower-third lyrics colour must be stored",
            )
        }

    @Test
    fun `the lyrics lower-third style buttons toggle bold italic and underline`() = songTab { get ->
        val group = StyleGroup.LYRICS_LOWER_THIRD
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsLowerThirdBold, "B must set lower-third bold")
        assertEquals(false, get().songSettings.lyricsBold, "the fullscreen lyrics must be untouched")

        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsLowerThirdItalic, "I must set lower-third italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsLowerThirdUnderline, "U must set lower-third underline")
    }

    @Test
    fun `the lyrics lower-third shadow button reveals its own shadow detail row`() = songTab { get ->
        styleButton(StyleGroup.LYRICS_LOWER_THIRD, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lyricsLowerThirdShadow, "S must set the lower-third shadow flag")
        assertEquals(false, get().songSettings.lyricsShadow, "the fullscreen shadow must stay off")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
    }

    @Test
    fun `the lyrics lower-third shadow detail fields store their values`() =
        songTab(
            initial = settingsWith {
                copy(
                    lyricsLowerThirdShadow = true,
                    lyricsLowerThirdShadowSize = 126,
                    lyricsLowerThirdShadowOpacity = 81,
                )
            },
        ) { get ->
            retypeNumberField(showing = 126, to = 140)
            assertEquals(140, get().songSettings.lyricsLowerThirdShadowSize, "the shadow size must be stored")
            retypeNumberField(showing = 81, to = 44)
            assertEquals(44, get().songSettings.lyricsLowerThirdShadowOpacity, "the shadow intensity must be stored")
        }

    @Test
    fun `the lyrics lower-third shadow colour field stores the picked colour`() =
        songTab(
            initial = settingsWith { copy(lyricsLowerThirdShadow = true, lyricsLowerThirdShadowColor = "#050607") },
        ) { get ->
            recolor(fromHex = "#050607", toHex = "#8090A0")
            assertTrue(
                get().songSettings.lyricsLowerThirdShadowColor.equals("#8090A0", ignoreCase = true),
                "the lower-third lyrics shadow colour must be stored",
            )
        }

    // ── The "Auto" measure buttons ──────────────────────────────────────────────────────────────

    private fun liveLyricsManager() = PresenterManager().apply {
        setLyricSection(LyricSection(title = "Amazing Grace", lines = listOf("Amazing grace how sweet the sound")))
        setPresentingMode(Presenting.LYRICS)
    }

    private fun outputs(vararg assignments: ScreenAssignment) = AppSettings().let {
        it.copy(projectionSettings = it.projectionSettings.copy(screenAssignments = assignments.toList()))
    }

    private val fullscreenOutput = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)
    private val lowerThirdOutput = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)

    @Test
    fun `the auto-fit buttons stay disabled while nothing is live`() =
        songTab(initial = outputs(fullscreenOutput, lowerThirdOutput), presenterManager = PresenterManager()) { _ ->
            autoFitButtons()[0].performScrollTo().assertIsNotEnabled()
            autoFitButtons()[1].performScrollTo().assertIsNotEnabled()
        }

    @Test
    fun `the fullscreen auto-fit button stays disabled without a fullscreen output`() =
        songTab(initial = outputs(lowerThirdOutput), presenterManager = liveLyricsManager()) { _ ->
            autoFitButtons()[0].performScrollTo().assertIsNotEnabled()
            autoFitButtons()[1].performScrollTo().assertIsEnabled()
        }

    @Test
    fun `the lower-third auto-fit button stays disabled without a lower-third output`() =
        songTab(initial = outputs(fullscreenOutput), presenterManager = liveLyricsManager()) { _ ->
            autoFitButtons()[0].performScrollTo().assertIsEnabled()
            autoFitButtons()[1].performScrollTo().assertIsNotEnabled()
        }

    @Test
    fun `the fullscreen auto-fit button measures the live verse and stores a size`() {
        val initial = outputs(fullscreenOutput).let {
            it.copy(songSettings = it.songSettings.copy(lyricsFontSize = 9))
        }
        songTab(initial = initial, presenterManager = liveLyricsManager()) { get ->
            autoFitButtons()[0].performScrollTo().performClick()
            waitForIdle()
            val fitted = get().songSettings.lyricsFontSize
            assertTrue(fitted > 9, "measuring a short line on a 1920x1080 output must grow the size, was $fitted")
            assertNumberFieldShows(fitted, "the fullscreen lyrics font size")
            assertEquals(28, get().songSettings.lyricsLowerThirdFontSize, "the lower-third size must be untouched")
        }
    }

    @Test
    fun `the lower-third auto-fit button measures the live verse and stores a size`() {
        val initial = outputs(lowerThirdOutput).let {
            it.copy(songSettings = it.songSettings.copy(lyricsLowerThirdFontSize = 9))
        }
        songTab(initial = initial, presenterManager = liveLyricsManager()) { get ->
            autoFitButtons()[1].performScrollTo().performClick()
            waitForIdle()
            val fitted = get().songSettings.lyricsLowerThirdFontSize
            assertTrue(fitted > 9, "measuring a short line in the lower-third band must grow the size, was $fitted")
            assertNumberFieldShows(fitted, "the lower-third lyrics font size")
            assertEquals(70, get().songSettings.lyricsFontSize, "the fullscreen size must be untouched")
        }
    }

    /**
     * With the title switched off there is no title band to subtract, so the whole output height is
     * available to the lyrics and the fitted size comes out at least as large as it does with one.
     */
    @Test
    fun `the fullscreen auto-fit button uses the whole height when no title is shown`() {
        val noTitle = outputs(fullscreenOutput).let {
            it.copy(songSettings = it.songSettings.copy(lyricsFontSize = 9, titleDisplay = Constants.NONE))
        }
        val withTitle = outputs(fullscreenOutput).let {
            it.copy(songSettings = it.songSettings.copy(lyricsFontSize = 9))
        }
        var fittedWithoutTitle = 0
        songTab(initial = noTitle, presenterManager = liveLyricsManager()) { get ->
            autoFitButtons()[0].performScrollTo().performClick()
            waitForIdle()
            fittedWithoutTitle = get().songSettings.lyricsFontSize
            assertTrue(fittedWithoutTitle > 9, "a title-less output must still be measured, was $fittedWithoutTitle")
            assertNumberFieldShows(fittedWithoutTitle, "the fullscreen lyrics font size")
        }
        songTab(initial = withTitle, presenterManager = liveLyricsManager()) { get ->
            autoFitButtons()[0].performScrollTo().performClick()
            waitForIdle()
            assertTrue(
                fittedWithoutTitle >= get().songSettings.lyricsFontSize,
                "dropping the title must not shrink the fitted size",
            )
        }
    }

    @Test
    fun `the lower-third auto-fit button uses the whole band when no title is shown`() {
        val noTitle = outputs(lowerThirdOutput).let {
            it.copy(songSettings = it.songSettings.copy(lyricsLowerThirdFontSize = 9, titleDisplay = Constants.NONE))
        }
        songTab(initial = noTitle, presenterManager = liveLyricsManager()) { get ->
            autoFitButtons()[1].performScrollTo().performClick()
            waitForIdle()
            val fitted = get().songSettings.lyricsLowerThirdFontSize
            assertTrue(fitted > 9, "a title-less lower third must still be measured, was $fitted")
            assertNumberFieldShows(fitted, "the lower-third lyrics font size")
        }
    }

    /**
     * The measurement builds its text style from the block's own bold/italic/underline settings, and
     * the title band it subtracts from its own. This runs the button with every one of those on, so
     * the styled side of each of those choices is exercised rather than only the plain default.
     */
    @Test
    fun `the fullscreen auto-fit button measures with the configured styles applied`() {
        val styled = outputs(fullscreenOutput).let {
            it.copy(
                songSettings = it.songSettings.copy(
                    lyricsFontSize = 9,
                    lyricsBold = true,
                    lyricsItalic = true,
                    lyricsUnderline = true,
                    titleBold = true,
                    titleItalic = true,
                ),
            )
        }
        songTab(initial = styled, presenterManager = liveLyricsManager()) { get ->
            autoFitButtons()[0].performScrollTo().performClick()
            waitForIdle()
            val fitted = get().songSettings.lyricsFontSize
            assertTrue(fitted > 9, "a bold italic underlined verse must still be measured, was $fitted")
            assertNumberFieldShows(fitted, "the fullscreen lyrics font size")
        }
    }

    @Test
    fun `the lower-third auto-fit button measures with the configured styles applied`() {
        val styled = outputs(lowerThirdOutput).let {
            it.copy(
                songSettings = it.songSettings.copy(
                    lyricsLowerThirdFontSize = 9,
                    lyricsLowerThirdBold = true,
                    lyricsLowerThirdItalic = true,
                    lyricsLowerThirdUnderline = true,
                    titleLowerThirdBold = true,
                    titleLowerThirdItalic = true,
                ),
            )
        }
        songTab(initial = styled, presenterManager = liveLyricsManager()) { get ->
            autoFitButtons()[1].performScrollTo().performClick()
            waitForIdle()
            val fitted = get().songSettings.lyricsLowerThirdFontSize
            assertTrue(fitted > 9, "a bold italic underlined verse must still be measured, was $fitted")
            assertNumberFieldShows(fitted, "the lower-third lyrics font size")
        }
    }

    /**
     * A section can be live with lyrics but no title of its own — a standalone chorus card, say. The
     * title band is then skipped even though the title is switched on for the output.
     */
    @Test
    fun `the auto-fit buttons skip the title band when the live section has no title`() {
        val untitled = PresenterManager().apply {
            setLyricSection(LyricSection(title = "", lines = listOf("Praise him all creatures here below")))
            setPresentingMode(Presenting.LYRICS)
        }
        val initial = outputs(fullscreenOutput, lowerThirdOutput).let {
            it.copy(
                songSettings = it.songSettings.copy(lyricsFontSize = 9, lyricsLowerThirdFontSize = 9),
            )
        }
        songTab(initial = initial, presenterManager = untitled) { get ->
            autoFitButtons()[0].performScrollTo().performClick()
            waitForIdle()
            autoFitButtons()[1].performScrollTo().performClick()
            waitForIdle()
            assertTrue(get().songSettings.lyricsFontSize > 9, "the untitled verse must be measured fullscreen")
            assertTrue(get().songSettings.lyricsLowerThirdFontSize > 9, "and in the lower third")
            assertNumberFieldShows(get().songSettings.lyricsFontSize, "the fullscreen lyrics font size")
            assertNumberFieldShows(get().songSettings.lyricsLowerThirdFontSize, "the lower-third lyrics font size")
        }
    }

    @Test
    fun `the auto-fit buttons leave the size alone when the live section is blank`() {
        val manager = PresenterManager().apply {
            setLyricSection(LyricSection(title = "Silence", lines = listOf("   ")))
            setPresentingMode(Presenting.LYRICS)
        }
        // A section whose only line is blank never satisfies the "lyrics are live" test, so the
        // button never becomes clickable — the guard inside its onClick is unreachable from the UI.
        songTab(initial = outputs(fullscreenOutput), presenterManager = manager) { get ->
            autoFitButtons()[0].performScrollTo().assertIsNotEnabled()
            assertEquals(70, get().songSettings.lyricsFontSize, "nothing must have been measured")
            assertNumberFieldShows(70, "the untouched fullscreen lyrics font size")
        }
    }
}
