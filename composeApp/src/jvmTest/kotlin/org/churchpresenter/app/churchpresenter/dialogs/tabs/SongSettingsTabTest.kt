@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives every control in the tab's left-hand column — the title slide, song number, title,
 * transition and text-margin sections — and asserts both halves of what each one is for: the value
 * it writes into [SongSettings] (which is what gets serialised to `settings.json`) and the change it
 * makes on screen.
 *
 * Fields are located by a value the fixture makes unique to them rather than by position; see
 * `SongSettingsTabTestSupport.kt` for why, and for the ordinal maps used by the button groups that
 * publish no text of their own.
 */
class SongSettingsTabTest {

    private fun settingsWith(change: SongSettings.() -> SongSettings): AppSettings =
        AppSettings().let { it.copy(songSettings = it.songSettings.change()) }

    // ── Song title slide ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the title-slide checkbox switches the title slide on`() = songTab { get ->
        onNodeWithTag("song_titleSlideEnabled").performScrollTo().assertIsOff().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleSlideEnabled, "the checkbox must enable the title slide")
        onNodeWithTag("song_titleSlideEnabled").assertIsOn()
    }

    @Test
    fun `the title-slide checkbox switches the title slide off`() =
        songTab(initial = settingsWith { copy(titleSlideEnabled = true) }) { get ->
            onNodeWithTag("song_titleSlideEnabled").performScrollTo().assertIsOn().performClick()
            waitForIdle()
            assertEquals(false, get().songSettings.titleSlideEnabled, "the checkbox must disable the title slide")
            onNodeWithTag("song_titleSlideEnabled").assertIsOff()
        }

    @Test
    fun `the title-slide number checkbox is disabled while the title slide is off`() = songTab { get ->
        // Reports itself disabled rather than simply having no click action. The old code disabled
        // this by passing a null callback, which removed the action outright; LabeledCheckbox marks
        // the row disabled instead, which is what a screen reader needs in order to say so.
        onNodeWithTag("song_titleSlideShowSongNumber")
            .performScrollTo()
            .assertIsNotEnabled()
        assertEquals(
            true,
            get().songSettings.titleSlideShowSongNumber,
            "disabling the control must not change the saved preference",
        )
    }

    @Test
    fun `the title-slide number checkbox controls whether the number is included`() =
        songTab(initial = settingsWith { copy(titleSlideEnabled = true) }) { get ->
            onNodeWithTag("song_titleSlideShowSongNumber")
                .performScrollTo()
                .assertIsOn()
                .performClick()
            waitForIdle()
            assertEquals(
                false,
                get().songSettings.titleSlideShowSongNumber,
                "the checkbox must disable the number on the title slide",
            )
            onNodeWithTag("song_titleSlideShowSongNumber").assertIsOff()
        }

    // ── Song number ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the song-number fullscreen font size field stores a new size`() =
        songTab(initial = settingsWith { copy(songNumberFontSize = 111) }) { get ->
            retypeNumberField(showing = 111, to = 123)
            assertEquals(123, get().songSettings.songNumberFontSize, "retyping the field must store the size")
            assertNumberFieldShows(123, "the song-number fullscreen font size")
        }

    @Test
    fun `the song-number fullscreen font size field keeps the stored size when given an out-of-range value`() =
        songTab(initial = settingsWith { copy(songNumberFontSize = 111) }) { get ->
            retypeNumberField(showing = 111, to = 999)
            assertEquals(111, get().songSettings.songNumberFontSize, "999 is outside 8..150 and must not be stored")
            assertNumberFieldShows(999, "the rejected entry still")
        }

    @Test
    fun `the song-number lower-third font size field stores a new size`() =
        songTab(initial = settingsWith { copy(songNumberLowerThirdFontSize = 112) }) { get ->
            retypeNumberField(showing = 112, to = 44)
            assertEquals(44, get().songSettings.songNumberLowerThirdFontSize, "the lower-third size must be stored")
            assertNumberFieldShows(44, "the song-number lower-third font size")
        }

    @Test
    fun `the show-number fullscreen dropdown stores Every Page`() = songTab { get ->
        chooseShowOption(ShowDropdown.NUMBER_FULLSCREEN, "Every Page")
        assertEquals(Constants.EVERY_PAGE, get().songSettings.showNumber, "picking Every Page must be stored")
    }

    @Test
    fun `the show-number fullscreen dropdown stores None`() = songTab { get ->
        chooseShowOption(ShowDropdown.NUMBER_FULLSCREEN, "None")
        assertEquals(Constants.NONE, get().songSettings.showNumber, "picking None must be stored")
    }

    @Test
    fun `the show-number lower-third dropdown stores Every Page`() = songTab { get ->
        chooseShowOption(ShowDropdown.NUMBER_LOWER_THIRD, "Every Page")
        assertEquals(
            Constants.EVERY_PAGE,
            get().songSettings.showNumberLowerThird,
            "the lower-third choice is separate"
        )
        assertEquals(Constants.FIRST_PAGE, get().songSettings.showNumber, "the fullscreen choice must be untouched")
        // Only the untouched dropdown is asserted on screen: a dropdown that was just clicked
        // echoes the choice from its own state, so its display proves nothing. See the round-trip
        // test below for how a picked value's display is actually verified.
        showDropdowns()[ShowDropdown.NUMBER_FULLSCREEN].assertTextContains("First Page")
    }

    @Test
    fun `the song-number fullscreen position buttons move the number above and below the verse`() = songTab { get ->
        positionButton(PositionGroup.SONG_NUMBER_FULLSCREEN, above = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.ABOVE_VERSE, get().songSettings.songNumberPosition, "Above must store AboveVerse")

        positionButton(PositionGroup.SONG_NUMBER_FULLSCREEN, above = false).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.BELOW_VERSE, get().songSettings.songNumberPosition, "Below must store BelowVerse")
    }

    @Test
    fun `the song-number lower-third position buttons move the number above and below the verse`() = songTab { get ->
        positionButton(PositionGroup.SONG_NUMBER_LOWER_THIRD, above = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.ABOVE_VERSE, get().songSettings.songNumberLowerThirdPosition, "Above must be stored")
        assertEquals(
            Constants.BELOW_VERSE,
            get().songSettings.songNumberPosition,
            "the fullscreen position must be untouched",
        )
    }

    @Test
    fun `the song-number fullscreen alignment buttons store left centre and right`() = songTab { get ->
        val group = HAlignGroup.SONG_NUMBER_FULLSCREEN
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.RIGHT),
            staysUnselected = horizontalAlignButton(group, HAlign.CENTER),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.songNumberHorizontalAlignment, "left must be stored")

        horizontalAlignButton(group, HAlign.CENTER).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.CENTER, get().songSettings.songNumberHorizontalAlignment, "centre must be stored")

        horizontalAlignButton(group, HAlign.RIGHT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.RIGHT, get().songSettings.songNumberHorizontalAlignment, "right must be stored")
    }

    @Test
    fun `the song-number lower-third alignment buttons store left centre and right`() = songTab { get ->
        val group = HAlignGroup.SONG_NUMBER_LOWER_THIRD
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.RIGHT),
            staysUnselected = horizontalAlignButton(group, HAlign.CENTER),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.songNumberLowerThirdHorizontalAlignment, "left must be stored")
        assertEquals(
            Constants.RIGHT,
            get().songSettings.songNumberHorizontalAlignment,
            "the fullscreen alignment must be untouched",
        )

        horizontalAlignButton(group, HAlign.CENTER).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.CENTER,
            get().songSettings.songNumberLowerThirdHorizontalAlignment,
            "centre must be stored"
        )
    }

    @Test
    fun `the number-before-title checkbox stays hidden while number and title are laid out differently`() =
        songTab { _ ->
            // Out of the box the number sits below the verse and right-aligned, the title middle and centred.
            onNodeWithTag("song_songNumberBeforeTitle").assertDoesNotExist()
        }

    @Test
    fun `aligning the song number with the title reveals the number-before-title checkbox`() = songTab { get ->
        // The number already sits below the verse; move the title there too, then match the alignment.
        positionButton(PositionGroup.TITLE_FULLSCREEN, above = false).performScrollTo().performClick()
        waitForIdle()
        horizontalAlignButton(HAlignGroup.SONG_NUMBER_FULLSCREEN, HAlign.CENTER).performScrollTo().performClick()
        waitForIdle()

        assertEquals(
            Constants.BELOW_VERSE,
            get().songSettings.titlePosition,
            "the title must have moved below the verse"
        )
        onNodeWithTag("song_songNumberBeforeTitle")
            .assertExists("matching the number's layout to the title's must reveal the ordering checkbox")
        onNodeWithText("Number before title").assertExists("the checkbox must be captioned")
    }

    /**
     * The ordering checkbox is offered when *either* output lines the number up with the title, so
     * matching only the lower-third pair is enough on its own.
     */
    @Test
    fun `matching only the lower-third layout also reveals the number-before-title checkbox`() = songTab { get ->
        onNodeWithTag("song_songNumberBeforeTitle").assertDoesNotExist()

        positionButton(PositionGroup.TITLE_LOWER_THIRD, above = false).performScrollTo().performClick()
        waitForIdle()
        horizontalAlignButton(HAlignGroup.SONG_NUMBER_LOWER_THIRD, HAlign.CENTER).performScrollTo().performClick()
        waitForIdle()

        assertEquals(
            Constants.BELOW_VERSE,
            get().songSettings.titleLowerThirdPosition,
            "the lower-third title must have moved below the verse",
        )
        assertEquals(
            Constants.MIDDLE,
            get().songSettings.titlePosition,
            "the fullscreen pair must still be mismatched",
        )
        onNodeWithTag("song_songNumberBeforeTitle")
            .assertExists("a matching lower-third layout alone must reveal the ordering checkbox")
    }

    @Test
    fun `the number-before-title checkbox clears its flag`() {
        val aligned = settingsWith {
            copy(songNumberPosition = titlePosition, songNumberHorizontalAlignment = titleHorizontalAlignment)
        }
        songTab(initial = aligned) { get ->
            assertEquals(true, get().songSettings.songNumberBeforeTitle, "the flag starts on")
            onNodeWithTag("song_songNumberBeforeTitle").performScrollTo().assertIsOn().performClick()
            waitForIdle()
            assertEquals(false, get().songSettings.songNumberBeforeTitle, "clicking it must clear the flag")
            onNodeWithTag("song_songNumberBeforeTitle").assertIsOff()
        }
    }

    // ── Title ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the show-title fullscreen dropdown stores Every Page`() = songTab { get ->
        chooseShowOption(ShowDropdown.TITLE_FULLSCREEN, "Every Page")
        assertEquals(Constants.EVERY_PAGE, get().songSettings.titleDisplay, "picking Every Page must be stored")
    }

    @Test
    fun `the show-title lower-third dropdown stores None`() = songTab { get ->
        chooseShowOption(ShowDropdown.TITLE_LOWER_THIRD, "None")
        assertEquals(Constants.NONE, get().songSettings.titleLowerThirdDisplay, "picking None must be stored")
        assertEquals(Constants.FIRST_PAGE, get().songSettings.titleDisplay, "the fullscreen choice must be untouched")
        showDropdowns()[ShowDropdown.TITLE_FULLSCREEN].assertTextContains("First Page")
    }

    /**
     * Each dropdown maps its three display strings back to a stored constant with its own `when`, so
     * every option has to be picked in every dropdown for all four mappings to have been run.
     */
    @Test
    fun `every option can be picked in every show dropdown`() {
        val options = listOf(
            "None" to Constants.NONE,
            "First Page" to Constants.FIRST_PAGE,
            "Every Page" to Constants.EVERY_PAGE
        )
        val readers = listOf<(SongSettings) -> String>(
            { it.showNumber },
            { it.showNumberLowerThird },
            { it.titleDisplay },
            { it.titleLowerThirdDisplay },
        )
        songTab { get ->
            for (dropdown in 0 until ShowDropdown.COUNT) {
                for ((label, stored) in options) {
                    chooseShowOption(dropdown, label)
                    assertEquals(
                        stored,
                        readers[dropdown](get().songSettings),
                        "picking $label in dropdown $dropdown must store $stored",
                    )
                }
            }
        }
    }

    @Test
    fun `stored None and Every Page choices are shown as they were saved`() {
        val stored = settingsWith {
            copy(
                showNumber = Constants.EVERY_PAGE,
                showNumberLowerThird = Constants.NONE,
                titleDisplay = Constants.NONE,
                titleLowerThirdDisplay = Constants.EVERY_PAGE,
            )
        }
        songTab(initial = stored) { _ ->
            showDropdowns()[ShowDropdown.NUMBER_FULLSCREEN].assertTextContains("Every Page")
            showDropdowns()[ShowDropdown.NUMBER_LOWER_THIRD].assertTextContains("None")
            showDropdowns()[ShowDropdown.TITLE_FULLSCREEN].assertTextContains("None")
            showDropdowns()[ShowDropdown.TITLE_LOWER_THIRD].assertTextContains("Every Page")
        }
    }

    /**
     * Settings files written by older builds — or hand-edited ones — can hold a value none of the
     * three options match. Every dropdown falls back to First Page rather than rendering blank.
     */
    @Test
    fun `an unrecognised stored choice falls back to First Page`() {
        val legacy = settingsWith {
            copy(
                showNumber = "Legacy",
                showNumberLowerThird = "Legacy",
                titleDisplay = "Legacy",
                titleLowerThirdDisplay = "Legacy",
            )
        }
        songTab(initial = legacy) { get ->
            for (dropdown in 0 until ShowDropdown.COUNT) {
                showDropdowns()[dropdown].assertTextContains("First Page")
            }
            assertEquals("Legacy", get().songSettings.showNumber, "the stored value itself is left alone")
        }
    }

    /**
     * `DropdownSettingsField` echoes the option you click into its own state, so its display right
     * after a pick would look right even if the choice were never stored. This closes that loop the
     * only way that means anything: pick in one composition, then render a fresh tab from the
     * settings that came out and assert the field shows the choice there.
     */
    @Test
    fun `a picked option is what a fresh render of the saved settings shows`() {
        var saved = AppSettings()
        songTab { get ->
            chooseShowOption(ShowDropdown.NUMBER_FULLSCREEN, "Every Page")
            chooseShowOption(ShowDropdown.TITLE_LOWER_THIRD, "None")
            saved = get()
        }
        songTab(initial = saved) { _ ->
            showDropdowns()[ShowDropdown.NUMBER_FULLSCREEN].assertTextContains("Every Page")
            showDropdowns()[ShowDropdown.TITLE_LOWER_THIRD].assertTextContains("None")
            showDropdowns()[ShowDropdown.NUMBER_LOWER_THIRD].assertTextContains("First Page")
            showDropdowns()[ShowDropdown.TITLE_FULLSCREEN].assertTextContains("First Page")
        }
    }

    @Test
    fun `the title fullscreen font size field stores a new size`() =
        songTab(initial = settingsWith { copy(titleFontSize = 113) }) { get ->
            retypeNumberField(showing = 113, to = 64)
            assertEquals(64, get().songSettings.titleFontSize, "the title size must be stored")
            assertNumberFieldShows(64, "the title fullscreen font size")
        }

    @Test
    fun `the title lower-third font size field stores a new size`() =
        songTab(initial = settingsWith { copy(titleLowerThirdFontSize = 114) }) { get ->
            retypeNumberField(showing = 114, to = 33)
            assertEquals(33, get().songSettings.titleLowerThirdFontSize, "the lower-third title size must be stored")
            assertNumberFieldShows(33, "the title lower-third font size")
        }

    @Test
    fun `the title fullscreen font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(titleFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.titleFontType, "committing the filtered family must store it")
            assertEquals("Arial", get().songSettings.titleLowerThirdFontType, "the lower-third family is separate")
        }

    @Test
    fun `the title lower-third font dropdown stores the picked family`() =
        songTab(initial = settingsWith { copy(titleLowerThirdFontType = SENTINEL_FONT) }) { get ->
            val font = uniquelyNamedFont()
            pickFont(showing = SENTINEL_FONT, to = font)
            assertEquals(font, get().songSettings.titleLowerThirdFontType, "the picked family must be stored")
        }

    @Test
    fun `the title font dropdown reports no results for an unknown family`() = songTab { get ->
        pickFontFilterOnly(showing = "Arial", filter = SENTINEL_FONT)
        onAllNodesWithText("No results found", substring = true).assertCountEquals(1)
        assertEquals("Arial", get().songSettings.titleFontType, "typing a filter alone must not change the family")
    }

    @Test
    fun `the title fullscreen colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(titleColor = "#101010") }) { get ->
            recolor(fromHex = "#101010", toHex = "#20A0C0")
            assertTrue(
                get().songSettings.titleColor.equals("#20A0C0", ignoreCase = true),
                "the confirmed hex must become the title colour, was ${get().songSettings.titleColor}",
            )
            onNodeWithText("#20A0C0").assertExists("the field must show the new colour")
        }

    @Test
    fun `cancelling the colour dialog leaves the title colour alone`() =
        songTab(initial = settingsWith { copy(titleColor = "#101010") }) { get ->
            openColorField("#101010")
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            assertEquals("#101010", get().songSettings.titleColor, "Cancel must not change the colour")
            onNodeWithText("Cancel").assertDoesNotExist()
        }

    @Test
    fun `the title lower-third colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(titleLowerThirdColor = "#102030") }) { get ->
            recolor(fromHex = "#102030", toHex = "#40B050")
            assertTrue(
                get().songSettings.titleLowerThirdColor.equals("#40B050", ignoreCase = true),
                "the lower-third title colour must be stored",
            )
        }

    @Test
    fun `the title fullscreen style buttons toggle bold italic and underline`() = songTab { get ->
        val group = StyleGroup.TITLE_FULLSCREEN
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleBold, "B must set bold")

        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleItalic, "I must set italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleUnderline, "U must set underline")

        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.titleBold, "B must clear bold again")
    }

    @Test
    fun `the title fullscreen shadow button reveals the shadow detail row`() = songTab { get ->
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)
        styleButton(StyleGroup.TITLE_FULLSCREEN, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleShadow, "S must set the shadow flag")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(1)
    }

    @Test
    fun `the title fullscreen shadow colour field stores the picked colour`() =
        songTab(initial = settingsWith { copy(titleShadow = true, titleShadowColor = "#010203") }) { get ->
            recolor(fromHex = "#010203", toHex = "#607080")
            assertTrue(
                get().songSettings.titleShadowColor.equals("#607080", ignoreCase = true),
                "the shadow colour must be stored",
            )
        }

    @Test
    fun `the title fullscreen shadow size field stores a new size`() =
        songTab(initial = settingsWith { copy(titleShadow = true, titleShadowSize = 123) }) { get ->
            retypeNumberField(showing = 123, to = 250)
            assertEquals(250, get().songSettings.titleShadowSize, "the shadow size must be stored")
            assertNumberFieldShows(250, "the shadow size")
        }

    @Test
    fun `the title fullscreen shadow intensity field stores a new intensity`() =
        songTab(initial = settingsWith { copy(titleShadow = true, titleShadowOpacity = 77) }) { get ->
            retypeNumberField(showing = 77, to = 42)
            assertEquals(42, get().songSettings.titleShadowOpacity, "the shadow intensity must be stored")
            assertNumberFieldShows(42, "the shadow intensity")
        }

    @Test
    fun `the title lower-third style buttons toggle bold italic and underline`() = songTab { get ->
        val group = StyleGroup.TITLE_LOWER_THIRD
        styleButton(group, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleLowerThirdBold, "B must set lower-third bold")
        assertEquals(false, get().songSettings.titleBold, "the fullscreen title must be untouched")

        styleButton(group, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleLowerThirdItalic, "I must set lower-third italic")

        styleButton(group, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleLowerThirdUnderline, "U must set lower-third underline")
    }

    @Test
    fun `the title lower-third shadow button reveals its own shadow detail row`() = songTab { get ->
        styleButton(StyleGroup.TITLE_LOWER_THIRD, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.titleLowerThirdShadow, "S must set the lower-third shadow flag")
        assertEquals(false, get().songSettings.titleShadow, "the fullscreen shadow must stay off")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
    }

    @Test
    fun `the title lower-third shadow detail fields store their values`() =
        songTab(
            initial = settingsWith {
                copy(titleLowerThirdShadow = true, titleLowerThirdShadowSize = 124, titleLowerThirdShadowOpacity = 78)
            },
        ) { get ->
            retypeNumberField(showing = 124, to = 300)
            assertEquals(
                300,
                get().songSettings.titleLowerThirdShadowSize,
                "the lower-third shadow size must be stored"
            )
            retypeNumberField(showing = 78, to = 55)
            assertEquals(55, get().songSettings.titleLowerThirdShadowOpacity, "the intensity must be stored")
        }

    @Test
    fun `the title lower-third shadow colour field stores the picked colour`() =
        songTab(
            initial = settingsWith { copy(titleLowerThirdShadow = true, titleLowerThirdShadowColor = "#020304") },
        ) { get ->
            recolor(fromHex = "#020304", toHex = "#506070")
            assertTrue(
                get().songSettings.titleLowerThirdShadowColor.equals("#506070", ignoreCase = true),
                "the lower-third title shadow colour must be stored",
            )
        }

    @Test
    fun `the title fullscreen position buttons move the title above and below the verse`() = songTab { get ->
        positionButton(PositionGroup.TITLE_FULLSCREEN, above = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.ABOVE_VERSE, get().songSettings.titlePosition, "Above must store AboveVerse")

        positionButton(PositionGroup.TITLE_FULLSCREEN, above = false).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.BELOW_VERSE, get().songSettings.titlePosition, "Below must store BelowVerse")
    }

    @Test
    fun `the title lower-third position buttons move the title above the verse`() = songTab { get ->
        positionButton(PositionGroup.TITLE_LOWER_THIRD, above = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.ABOVE_VERSE, get().songSettings.titleLowerThirdPosition, "Above must be stored")
        assertEquals(Constants.MIDDLE, get().songSettings.titlePosition, "the fullscreen position must be untouched")
    }

    @Test
    fun `the title fullscreen alignment buttons store left centre and right`() = songTab { get ->
        val group = HAlignGroup.TITLE_FULLSCREEN
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.CENTER),
            staysUnselected = horizontalAlignButton(group, HAlign.RIGHT),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.titleHorizontalAlignment, "left must be stored")

        horizontalAlignButton(group, HAlign.RIGHT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.RIGHT, get().songSettings.titleHorizontalAlignment, "right must be stored")

        horizontalAlignButton(group, HAlign.CENTER).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.CENTER, get().songSettings.titleHorizontalAlignment, "centre must be stored")
    }

    @Test
    fun `the title lower-third alignment buttons store left and right`() = songTab { get ->
        val group = HAlignGroup.TITLE_LOWER_THIRD
        selectAndAssertGroupRepaint(
            click = horizontalAlignButton(group, HAlign.LEFT),
            losesSelection = horizontalAlignButton(group, HAlign.CENTER),
            staysUnselected = horizontalAlignButton(group, HAlign.RIGHT),
            what = "the alignment group",
        )
        waitForIdle()
        assertEquals(Constants.LEFT, get().songSettings.titleLowerThirdHorizontalAlignment, "left must be stored")
        assertEquals(
            Constants.CENTER,
            get().songSettings.titleHorizontalAlignment,
            "the fullscreen alignment must be untouched",
        )

        horizontalAlignButton(group, HAlign.RIGHT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Constants.RIGHT, get().songSettings.titleLowerThirdHorizontalAlignment, "right must be stored")
    }

    // ── Transition ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the transition duration label shows the stored duration`() =
        songTab(initial = settingsWith { copy(transitionDuration = 1250f) }) { _ ->
            onNodeWithText("1250ms").assertExists("the slider must label itself with the stored duration")
        }

    /**
     * `SlimSlider` draws its track on a bare `Canvas` and publishes no semantics for it, so there is
     * no node to click. The two labels around it do have bounds, and the track is exactly the gap
     * between them, so the click goes in at a computed coordinate on the root. The assertion is on
     * the invariants the handler guarantees — inside the range and snapped to 50ms — rather than on
     * an exact value, which would be asserting pixel arithmetic.
     */
    @Test
    fun `clicking the transition duration slider stores a snapped duration`() = songTab { get ->
        onNodeWithText("Transition Duration:").performScrollTo()
        waitForIdle()
        val label = onNodeWithText("Transition Duration:").fetchSemanticsNode().boundsInRoot
        val trailing = onNodeWithText("500ms").fetchSemanticsNode().boundsInRoot
        val trackStart = label.right
        val trackEnd = trailing.left - 10f // Arrangement.spacedBy(10.dp), density 1 under test
        onRoot().performMouseInput { click(Offset(trackStart + (trackEnd - trackStart) * 0.4f, trailing.center.y)) }
        waitForIdle()

        val stored = get().songSettings.transitionDuration
        assertTrue(stored != 500f, "clicking further along the track must move the duration off its default")
        assertTrue(stored in 100f..2000f, "the duration must stay inside the slider's range, was $stored")
        assertTrue(stored % 50f == 0f, "the duration must be snapped to 50ms, was $stored")
        onNodeWithText("${stored.toInt()}ms").assertExists("the label must follow the slider")
    }

    @Test
    fun `the fade-in checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_fadeIn").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.fadeIn, "the checkbox must clear fade-in")
        onNodeWithTag("song_fadeIn").assertIsOff()
    }

    @Test
    fun `the fade-out checkbox clears the flag`() = songTab { get ->
        onNodeWithTag("song_fadeOut").performScrollTo().assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, get().songSettings.fadeOut, "the checkbox must clear fade-out")
        assertEquals(true, get().songSettings.fadeIn, "fade-in must be untouched")
        onNodeWithTag("song_fadeOut").assertIsOff()
    }

    @Test
    fun `the crossfade checkbox sets the flag`() = songTab { get ->
        onNodeWithTag("song_crossfade").performScrollTo().assertIsOff().performClick()
        waitForIdle()
        assertEquals(true, get().songSettings.crossfade, "the checkbox must set crossfade")
        onNodeWithTag("song_crossfade").assertIsOn()
    }

    @Test
    fun `the bilingual layout row switches to top and bottom`() = songTab { get ->
        onNodeWithText("Left / Right").performScrollTo().assertIsSelected()
        onNodeWithText("Top / Bottom").performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            get().songSettings.bilingualLayout,
            "picking Top / Bottom must be stored",
        )
        onNodeWithText("Top / Bottom").assertIsSelected()
        onNodeWithText("Left / Right").assertIsNotSelected()
    }

    @Test
    fun `the bilingual layout row switches back to left and right`() =
        songTab(initial = settingsWith { copy(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM) }) { get ->
            onNodeWithText("Left / Right").performScrollTo().assertIsNotSelected().performClick()
            waitForIdle()
            assertEquals(
                Constants.BILINGUAL_SIDE_BY_SIDE,
                get().songSettings.bilingualLayout,
                "picking Left / Right must be stored",
            )
            onNodeWithText("Left / Right").assertIsSelected()
        }

    @Test
    fun `the end-of-song spacing field stores a new spacing`() = songTab { get ->
        retypeNumberField(showing = 2, to = 7)
        assertEquals(7, get().songSettings.endOfSongIndicatorSpacing, "the spacing must be stored")
        assertNumberFieldShows(7, "the end-of-song spacing")
    }

    @Test
    fun `the end-of-song spacing field keeps the stored value when given one outside its range`() = songTab { get ->
        retypeNumberField(showing = 2, to = 25)
        assertEquals(2, get().songSettings.endOfSongIndicatorSpacing, "25 is outside 0..10 and must not be stored")
    }

    // ── Text margins ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the top margin field stores a new margin`() =
        songTab(initial = settingsWith { copy(marginTop = 131) }) { get ->
            retypeNumberField(showing = 131, to = 200)
            assertEquals(200, get().songSettings.marginTop, "the top margin must be stored")
            assertNumberFieldShows(200, "the top margin")
        }

    @Test
    fun `the left margin field stores a new margin`() =
        songTab(initial = settingsWith { copy(marginLeft = 132) }) { get ->
            retypeNumberField(showing = 132, to = 210)
            assertEquals(210, get().songSettings.marginLeft, "the left margin must be stored")
            assertEquals(96, get().songSettings.marginRight, "the right margin must be untouched")
        }

    @Test
    fun `the right margin field stores a new margin`() =
        songTab(initial = settingsWith { copy(marginRight = 133) }) { get ->
            retypeNumberField(showing = 133, to = 220)
            assertEquals(220, get().songSettings.marginRight, "the right margin must be stored")
            assertEquals(96, get().songSettings.marginLeft, "the left margin must be untouched")
        }

    @Test
    fun `the bottom margin field stores a new margin`() =
        songTab(initial = settingsWith { copy(marginBottom = 134) }) { get ->
            retypeNumberField(showing = 134, to = 230)
            assertEquals(230, get().songSettings.marginBottom, "the bottom margin must be stored")
            assertNumberFieldShows(230, "the bottom margin")
        }

    @Test
    fun `a margin outside the allowed range is not stored`() =
        songTab(initial = settingsWith { copy(marginTop = 131) }) { get ->
            retypeNumberField(showing = 131, to = 900)
            assertEquals(131, get().songSettings.marginTop, "900 is outside 0..500 and must not be stored")
        }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────────

    /**
     * The tab's whole job is to produce an [AppSettings] the app can persist, so this takes what the
     * controls actually wrote and round-trips it through the same serialiser `SettingsManager` uses.
     */
    @Test
    fun `the values the controls write survive a settings json round trip`() = songTab { get ->
        onNodeWithTag("song_titleSlideEnabled").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("song_titleSlideShowSongNumber").performClick()
        waitForIdle()
        onNodeWithTag("song_crossfade").performScrollTo().performClick()
        waitForIdle()
        chooseShowOption(ShowDropdown.NUMBER_FULLSCREEN, "Every Page")
        retypeNumberField(showing = 2, to = 5)

        onNodeWithTag("song_titleSlideEnabled").assertIsOn()
        onNodeWithTag("song_crossfade").assertIsOn()

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<AppSettings>(json.encodeToString(get()))

        assertEquals(true, restored.songSettings.titleSlideEnabled, "the title-slide flag must survive")
        assertEquals(
            false,
            restored.songSettings.titleSlideShowSongNumber,
            "the title-slide number preference must survive",
        )
        assertEquals(true, restored.songSettings.crossfade, "the crossfade flag must survive")
        assertEquals(Constants.EVERY_PAGE, restored.songSettings.showNumber, "the show-number choice must survive")
        assertEquals(5, restored.songSettings.endOfSongIndicatorSpacing, "the spacing must survive")
    }
}
