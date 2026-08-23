@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.SENTINEL_FONT
import org.churchpresenter.ui.assertNumberFieldShows
import org.churchpresenter.ui.pickFont
import org.churchpresenter.ui.recolor
import org.churchpresenter.ui.retypeNumberField
import org.churchpresenter.ui.segmentedButton
import org.churchpresenter.ui.styleButton
import org.churchpresenter.ui.uniquelyNamedFont

/**
 * Drives every control in the four look-ahead sections — current and next section, for the
 * fullscreen and lower-third outputs — asserting the value written into [SongSettings] and the
 * on-screen result.
 *
 * These four sections style the confidence text the band reads ahead of the congregation, and every
 * one of them keeps its own copy of the same settings. Each test therefore also checks that the
 * sibling section it is most easily confused with was left alone.
 */
class SongSettingsTabLookAheadTest {

    private fun settingsWith(change: SongSettings.() -> SongSettings): AppSettings =
        AppSettings().let { it.copy(songSettings = it.songSettings.change()) }

    // ── Look ahead — fullscreen ─────────────────────────────────────────────────────────────────

    @Test
    fun `the look-ahead display mode row switches to one line`() = songTab { get ->
        segmentedButton("1 Verse", ModeRow.LOOK_AHEAD).performScrollTo().assertIsSelected()
        segmentedButton("1 Line", ModeRow.LOOK_AHEAD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_DISPLAY_MODE_LINE,
            get().songSettings.lookAheadDisplayMode,
            "picking 1 Line must be stored",
        )
        segmentedButton("1 Line", ModeRow.LOOK_AHEAD).assertIsSelected()
        segmentedButton("1 Verse", ModeRow.LOOK_AHEAD).assertIsNotSelected()
    }

    @Test
    fun `the look-ahead display mode row switches back to one verse`() =
        songTab(initial = settingsWith { copy(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE) }) { get ->
            segmentedButton("1 Verse", ModeRow.LOOK_AHEAD).performScrollTo().assertIsNotSelected().performClick()
            waitForIdle()
            assertEquals(
                Constants.SONG_DISPLAY_MODE_VERSE,
                get().songSettings.lookAheadDisplayMode,
                "picking 1 Verse must be stored",
            )
            segmentedButton("1 Verse", ModeRow.LOOK_AHEAD).assertIsSelected()
        }

    @Test
    fun `the look-ahead language row switches to both languages`() = songTab { get ->
        segmentedButton("Primary", ModeRow.LOOK_AHEAD).performScrollTo().assertIsSelected()
        segmentedButton("Both", ModeRow.LOOK_AHEAD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_LANG_BOTH,
            get().songSettings.lookAheadLanguageDisplay,
            "picking Both must be stored",
        )
        assertEquals(
            Constants.SONG_LANG_PRIMARY,
            get().songSettings.lowerThirdLookAheadLanguageDisplay,
            "the lower-third look-ahead row must be untouched",
        )
        segmentedButton("Both", ModeRow.LOOK_AHEAD).assertIsSelected()
    }

    @Test
    fun `the look-ahead language row switches to secondary only`() = songTab { get ->
        segmentedButton("Secondary", ModeRow.LOOK_AHEAD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_LANG_SECONDARY,
            get().songSettings.lookAheadLanguageDisplay,
            "picking Secondary must be stored",
        )
        segmentedButton("Secondary", ModeRow.LOOK_AHEAD).assertIsSelected()
        segmentedButton("Primary", ModeRow.LOOK_AHEAD).assertIsNotSelected()
    }

    @Test
    fun `the look-ahead alignment buttons store left centre and right`() = songTab { get ->
        val group = HAlignGroup.LOOK_AHEAD
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.CENTER),
            staysUnselected = horizontalAlignButton(group, HAlign.RIGHT),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.lookAheadHorizontalAlignment, "left must be stored")

        horizontalAlignButton(group, HAlign.RIGHT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.RIGHT, get().songSettings.lookAheadHorizontalAlignment, "right must be stored")

        horizontalAlignButton(group, HAlign.CENTER).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.CENTER, get().songSettings.lookAheadHorizontalAlignment, "centre must be stored")
    }

    @Test
    fun `the look-ahead font size field stores a new size`() =
        songTab(initial = settingsWith { copy(lookAheadFontSize = 117) }) { get ->
            retypeNumberField(showing = 117, to = 52)
            assertEquals(52, get().songSettings.lookAheadFontSize, "the look-ahead size must be stored")
            assertNumberFieldShows(52, "the look-ahead font size")
        }

    @Test
    fun `the look-ahead auto-fit checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_lookAheadFontSizeAutoFit").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.lookAheadFontSizeAutoFit, "the checkbox must clear auto-fit")
        assertEquals(true, get().songSettings.lookAheadNextFontSizeAutoFit, "the next-section flag must be untouched")
        onNodeWithTag("song_lookAheadFontSizeAutoFit").assertIsOff()
    }

    @Test
    fun `the look-ahead font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(lookAheadFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.lookAheadFontType, "the picked family must be stored")
            assertEquals("Arial", get().songSettings.lookAheadNextFontType, "the next-section family is separate")
        }

    @Test
    fun `the look-ahead colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(lookAheadColor = "#171819") }) { get ->
            recolor(fromHex = "#171819", toHex = "#A0B0C0")
            assertTrue(
                get().songSettings.lookAheadColor.equals("#A0B0C0", ignoreCase = true),
                "the look-ahead colour must be stored, was ${get().songSettings.lookAheadColor}",
            )
            onNodeWithText("#A0B0C0").assertExists("the field must show the new colour")
        }

    @Test
    fun `the look-ahead style buttons toggle bold italic and underline`() = songTab { get ->
        val group = StyleGroup.LOOK_AHEAD
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lookAheadBold, "B must set bold")

        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lookAheadItalic, "I must set italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lookAheadUnderline, "U must set underline")
    }

    @Test
    fun `the look-ahead shadow button reveals the shadow detail row`() = songTab { get ->
        styleButton(StyleGroup.LOOK_AHEAD, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lookAheadShadow, "S must set the shadow flag")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
    }

    @Test
    fun `the look-ahead shadow detail fields store their values`() =
        songTab(
            initial = settingsWith {
                copy(
                    lookAheadShadow = true,
                    lookAheadShadowColor = "#070809",
                    lookAheadShadowSize = 127,
                    lookAheadShadowOpacity = 82,
                )
            },
        ) { get ->
            retypeNumberField(showing = 127, to = 160)
            assertEquals(160, get().songSettings.lookAheadShadowSize, "the shadow size must be stored")
            retypeNumberField(showing = 82, to = 33)
            assertEquals(33, get().songSettings.lookAheadShadowOpacity, "the shadow intensity must be stored")
            recolor(fromHex = "#070809", toHex = "#C0C0C0")
            assertTrue(
                get().songSettings.lookAheadShadowColor.equals("#C0C0C0", ignoreCase = true),
                "the shadow colour must be stored",
            )
        }

    // ── Look ahead next section — fullscreen ────────────────────────────────────────────────────

    @Test
    fun `the look-ahead next font size field stores a new size`() =
        songTab(initial = settingsWith { copy(lookAheadNextFontSize = 118) }) { get ->
            retypeNumberField(showing = 118, to = 40)
            assertEquals(40, get().songSettings.lookAheadNextFontSize, "the next-section size must be stored")
            assertEquals(70, get().songSettings.lookAheadFontSize, "the current-section size must be untouched")
            assertNumberFieldShows(40, "the look-ahead next font size")
        }

    @Test
    fun `the look-ahead next auto-fit checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_lookAheadNextFontSizeAutoFit").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.lookAheadNextFontSizeAutoFit, "the checkbox must clear auto-fit")
        onNodeWithTag("song_lookAheadNextFontSizeAutoFit").assertIsOff()
    }

    @Test
    fun `the look-ahead next font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(lookAheadNextFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.lookAheadNextFontType, "the picked family must be stored")
        }

    @Test
    fun `the look-ahead next colour field starts grey and stores the picked colour`() = songTab { get ->
        // The next-section preview is deliberately dimmed out of the box; two fields share that grey.
        assertEquals("#888888", get().songSettings.lookAheadNextColor, "the next section starts dimmed")
        onAllNodesWithText("#888888").assertCountEquals(2)
        recolor(fromHex = "#888888", toHex = "#B0C0D0")
        assertTrue(
            get().songSettings.lookAheadNextColor.equals("#B0C0D0", ignoreCase = true),
            "the next-section colour must be stored, was ${get().songSettings.lookAheadNextColor}",
        )
        assertEquals(
            "#888888",
            get().songSettings.lowerThirdLookAheadNextColor,
            "the lower-third next section must keep its own grey",
        )
    }

    @Test
    fun `the look-ahead next style buttons toggle bold and clear the default italic`() = songTab { get ->
        val group = StyleGroup.LOOK_AHEAD_NEXT
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lookAheadNextBold, "B must set bold")

        // The next-section preview is italic by default, so this button clears it.
        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.lookAheadNextItalic, "I must clear the default italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lookAheadNextUnderline, "U must set underline")
    }

    @Test
    fun `the look-ahead next shadow button reveals the shadow detail row`() = songTab { get ->
        styleButton(StyleGroup.LOOK_AHEAD_NEXT, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lookAheadNextShadow, "S must set the shadow flag")
        assertEquals(false, get().songSettings.lookAheadShadow, "the current-section shadow must stay off")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
    }

    @Test
    fun `the look-ahead next shadow detail fields store their values`() =
        songTab(
            initial = settingsWith {
                copy(lookAheadNextShadow = true, lookAheadNextShadowSize = 128, lookAheadNextShadowOpacity = 83)
            },
        ) { get ->
            retypeNumberField(showing = 128, to = 210)
            assertEquals(210, get().songSettings.lookAheadNextShadowSize, "the shadow size must be stored")
            retypeNumberField(showing = 83, to = 27)
            assertEquals(27, get().songSettings.lookAheadNextShadowOpacity, "the shadow intensity must be stored")
        }

    @Test
    fun `the look-ahead next shadow colour field stores the picked colour`() =
        songTab(
            initial = settingsWith { copy(lookAheadNextShadow = true, lookAheadNextShadowColor = "#0A0B0C") },
        ) { get ->
            recolor(fromHex = "#0A0B0C", toHex = "#90A0B0")
            assertTrue(
                get().songSettings.lookAheadNextShadowColor.equals("#90A0B0", ignoreCase = true),
                "the next-section shadow colour must be stored",
            )
        }

    // ── Look ahead — lower third ────────────────────────────────────────────────────────────────

    @Test
    fun `the lower-third look-ahead display mode row switches to one verse`() = songTab { get ->
        segmentedButton("1 Line", ModeRow.LT_LOOK_AHEAD).performScrollTo().assertIsSelected()
        segmentedButton("1 Verse", ModeRow.LT_LOOK_AHEAD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_DISPLAY_MODE_VERSE,
            get().songSettings.lowerThirdLookAheadDisplayMode,
            "picking 1 Verse must be stored",
        )
        assertEquals(
            Constants.SONG_DISPLAY_MODE_VERSE,
            get().songSettings.lookAheadDisplayMode,
            "the fullscreen look-ahead row must be untouched",
        )
        segmentedButton("1 Verse", ModeRow.LT_LOOK_AHEAD).assertIsSelected()
    }

    @Test
    fun `the lower-third look-ahead display mode row switches back to one line`() =
        songTab(
            initial = settingsWith { copy(lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE) },
        ) { get ->
            segmentedButton("1 Line", ModeRow.LT_LOOK_AHEAD).performScrollTo().assertIsNotSelected().performClick()
            waitForIdle()
            assertEquals(
                Constants.SONG_DISPLAY_MODE_LINE,
                get().songSettings.lowerThirdLookAheadDisplayMode,
                "picking 1 Line must be stored",
            )
            segmentedButton("1 Line", ModeRow.LT_LOOK_AHEAD).assertIsSelected()
        }

    @Test
    fun `the lower-third look-ahead language row switches to secondary only`() = songTab { get ->
        segmentedButton("Secondary", ModeRow.LT_LOOK_AHEAD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.SONG_LANG_SECONDARY,
            get().songSettings.lowerThirdLookAheadLanguageDisplay,
            "picking Secondary must be stored",
        )
        assertEquals(
            Constants.SONG_LANG_PRIMARY,
            get().songSettings.lookAheadLanguageDisplay,
            "the fullscreen look-ahead row must be untouched",
        )
        segmentedButton("Secondary", ModeRow.LT_LOOK_AHEAD).assertIsSelected()
    }

    @Test
    fun `the lower-third look-ahead alignment buttons store left and right`() = songTab { get ->
        val group = HAlignGroup.LT_LOOK_AHEAD
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.CENTER),
            staysUnselected = horizontalAlignButton(group, HAlign.RIGHT),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.lowerThirdLookAheadHorizontalAlignment, "left must be stored")
        assertEquals(
            Constants.CENTER,
            get().songSettings.lookAheadHorizontalAlignment,
            "the fullscreen look-ahead alignment must be untouched",
        )

        horizontalAlignButton(group, HAlign.RIGHT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.RIGHT, get().songSettings.lowerThirdLookAheadHorizontalAlignment, "right must be stored")
    }

    @Test
    fun `the lower-third look-ahead font size field stores a new size`() =
        songTab(initial = settingsWith { copy(lowerThirdLookAheadFontSize = 119) }) { get ->
            retypeNumberField(showing = 119, to = 24)
            assertEquals(24, get().songSettings.lowerThirdLookAheadFontSize, "the lower-third size must be stored")
            assertNumberFieldShows(24, "the lower-third look-ahead font size")
        }

    @Test
    fun `the lower-third look-ahead auto-fit checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_lowerThirdLookAheadFontSizeAutoFit").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.lowerThirdLookAheadFontSizeAutoFit, "the checkbox must clear auto-fit")
        assertEquals(true, get().songSettings.lookAheadFontSizeAutoFit, "the fullscreen flag must be untouched")
        onNodeWithTag("song_lowerThirdLookAheadFontSizeAutoFit").assertIsOff()
    }

    @Test
    fun `the lower-third look-ahead font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(lowerThirdLookAheadFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.lowerThirdLookAheadFontType, "the picked family must be stored")
        }

    @Test
    fun `the lower-third look-ahead colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(lowerThirdLookAheadColor = "#1A1B1C") }) { get ->
            recolor(fromHex = "#1A1B1C", toHex = "#D0E0F0")
            assertTrue(
                get().songSettings.lowerThirdLookAheadColor.equals("#D0E0F0", ignoreCase = true),
                "the lower-third look-ahead colour must be stored",
            )
        }

    @Test
    fun `the lower-third look-ahead style buttons toggle bold italic and underline`() = songTab { get ->
        val group = StyleGroup.LT_LOOK_AHEAD
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lowerThirdLookAheadBold, "B must set bold")
        assertEquals(false, get().songSettings.lookAheadBold, "the fullscreen look-ahead must be untouched")

        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lowerThirdLookAheadItalic, "I must set italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lowerThirdLookAheadUnderline, "U must set underline")
    }

    @Test
    fun `the lower-third look-ahead shadow button reveals the shadow detail row`() = songTab { get ->
        styleButton(StyleGroup.LT_LOOK_AHEAD, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lowerThirdLookAheadShadow, "S must set the shadow flag")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
    }

    @Test
    fun `the lower-third look-ahead shadow detail fields store their values`() =
        songTab(
            initial = settingsWith {
                copy(
                    lowerThirdLookAheadShadow = true,
                    lowerThirdLookAheadShadowSize = 129,
                    lowerThirdLookAheadShadowOpacity = 84,
                )
            },
        ) { get ->
            retypeNumberField(showing = 129, to = 190)
            assertEquals(190, get().songSettings.lowerThirdLookAheadShadowSize, "the shadow size must be stored")
            retypeNumberField(showing = 84, to = 21)
            assertEquals(21, get().songSettings.lowerThirdLookAheadShadowOpacity, "the intensity must be stored")
        }

    @Test
    fun `the lower-third look-ahead shadow colour field stores the picked colour`() =
        songTab(
            initial = settingsWith {
                copy(lowerThirdLookAheadShadow = true, lowerThirdLookAheadShadowColor = "#0D0E0F")
            },
        ) { get ->
            recolor(fromHex = "#0D0E0F", toHex = "#A0B0D0")
            assertTrue(
                get().songSettings.lowerThirdLookAheadShadowColor.equals("#A0B0D0", ignoreCase = true),
                "the lower-third look-ahead shadow colour must be stored",
            )
        }

    // ── Look ahead next section — lower third ───────────────────────────────────────────────────

    @Test
    fun `the lower-third look-ahead next font size field stores a new size`() =
        songTab(initial = settingsWith { copy(lowerThirdLookAheadNextFontSize = 120) }) { get ->
            retypeNumberField(showing = 120, to = 18)
            assertEquals(18, get().songSettings.lowerThirdLookAheadNextFontSize, "the size must be stored")
            assertEquals(28, get().songSettings.lowerThirdLookAheadFontSize, "the current-section size is separate")
            assertNumberFieldShows(18, "the lower-third look-ahead next font size")
        }

    @Test
    fun `the lower-third look-ahead next auto-fit checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_lowerThirdLookAheadNextFontSizeAutoFit").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(
            false,
            get().songSettings.lowerThirdLookAheadNextFontSizeAutoFit,
            "the checkbox must clear auto-fit",
        )
        onNodeWithTag("song_lowerThirdLookAheadNextFontSizeAutoFit").assertIsOff()
    }

    @Test
    fun `the lower-third look-ahead next font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(lowerThirdLookAheadNextFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.lowerThirdLookAheadNextFontType, "the picked family must be stored")
        }

    @Test
    fun `the lower-third look-ahead next colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(lowerThirdLookAheadNextColor = "#1D1E1F") }) { get ->
            recolor(fromHex = "#1D1E1F", toHex = "#E0F0A0")
            assertTrue(
                get().songSettings.lowerThirdLookAheadNextColor.equals("#E0F0A0", ignoreCase = true),
                "the lower-third next-section colour must be stored",
            )
        }

    @Test
    fun `the lower-third look-ahead next style buttons toggle bold and clear the default italic`() = songTab { get ->
        val group = StyleGroup.LT_LOOK_AHEAD_NEXT
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lowerThirdLookAheadNextBold, "B must set bold")

        // Like its fullscreen twin, the lower-third next-section preview is italic out of the box.
        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.lowerThirdLookAheadNextItalic, "I must clear the default italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lowerThirdLookAheadNextUnderline, "U must set underline")
    }

    @Test
    fun `the lower-third look-ahead next shadow button reveals the shadow detail row`() = songTab { get ->
        styleButton(StyleGroup.LT_LOOK_AHEAD_NEXT, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.lowerThirdLookAheadNextShadow, "S must set the shadow flag")
        assertEquals(false, get().songSettings.lowerThirdLookAheadShadow, "the current-section shadow must stay off")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
    }

    @Test
    fun `the lower-third look-ahead next shadow detail fields store their values`() =
        songTab(
            initial = settingsWith {
                copy(
                    lowerThirdLookAheadNextShadow = true,
                    lowerThirdLookAheadNextShadowSize = 130,
                    lowerThirdLookAheadNextShadowOpacity = 85,
                )
            },
        ) { get ->
            retypeNumberField(showing = 130, to = 220)
            assertEquals(220, get().songSettings.lowerThirdLookAheadNextShadowSize, "the shadow size must be stored")
            retypeNumberField(showing = 85, to = 19)
            assertEquals(19, get().songSettings.lowerThirdLookAheadNextShadowOpacity, "the intensity must be stored")
        }

    @Test
    fun `the lower-third look-ahead next shadow colour field stores the picked colour`() =
        songTab(
            initial = settingsWith {
                copy(lowerThirdLookAheadNextShadow = true, lowerThirdLookAheadNextShadowColor = "#0E0F10")
            },
        ) { get ->
            recolor(fromHex = "#0E0F10", toHex = "#B0C0E0")
            assertTrue(
                get().songSettings.lowerThirdLookAheadNextShadowColor.equals("#B0C0E0", ignoreCase = true),
                "the lower-third next-section shadow colour must be stored",
            )
        }
}
