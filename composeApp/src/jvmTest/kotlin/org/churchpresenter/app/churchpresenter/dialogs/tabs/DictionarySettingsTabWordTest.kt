@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives every control in the Word (Original) section — the largest of the six, and the only one
 * with the full set: a Show switch, a colour, bold/italic/shadow, a shadow detail row, a font
 * family and a font size.
 *
 * Each test asserts both halves: the value written into [DictionarySettings] (which is what reaches
 * `settings.json`) and what the tab then shows. Where the section shares a control type with another
 * section — colour and font size especially — the test also checks the neighbour was left alone,
 * because every one of these callbacks is a copy of the same `s.copy(dictionarySettings = ...)`
 * shape and a mis-typed field name would otherwise go unnoticed.
 */
class DictionarySettingsTabWordTest {

    // ── Show ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the show switch turns the word off and back on`() = dictionaryTab { get ->
        assertTrue(get().dictionarySettings.showWord, "the word shows out of the box")
        switch(Switches.SHOW_WORD).assertIsOn()

        switch(Switches.SHOW_WORD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.showWord, "switching off must be stored")
        switch(Switches.SHOW_WORD).assertIsOff()
        assertTrue(get().dictionarySettings.showDefinition, "the definition switch must be untouched")

        switch(Switches.SHOW_WORD).performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.showWord, "switching back on must be stored too")
        switch(Switches.SHOW_WORD).assertIsOn()
    }

    // ── Colour ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the word colour field stores the confirmed hex`() {
        dictionaryTab(initial = dictionarySettings { copy(wordColor = "#101112") }) { get ->
            recolor(fromHex = "#101112", toHex = "#20A0C0")
            assertTrue(
                get().dictionarySettings.wordColor.equals("#20A0C0", ignoreCase = true),
                "the confirmed hex must become the word colour, was ${get().dictionarySettings.wordColor}",
            )
            assertColorFieldShows("#20A0C0", "the word colour field")
            assertEquals("#DDDDDD", get().dictionarySettings.definitionColor, "the definition colour must be untouched")
            assertEquals("#FFFFFF", get().dictionarySettings.referenceColor, "the reference colour must be untouched")
        }
    }

    // ── Bold / Italic / Underline / Shadow ──────────────────────────────────────────────────────

    @Test
    fun `the bold button stores the word as bold`() = dictionaryTab { get ->
        assertEquals(false, get().dictionarySettings.wordBold, "not bold out of the box")
        styleButton(DictStyleGroup.WORD, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.wordBold, "clicking B must be stored")

        styleButton(DictStyleGroup.WORD, "B").performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.wordBold, "clicking B again must clear it")
    }

    @Test
    fun `the italic button stores the word as italic`() = dictionaryTab { get ->
        styleButton(DictStyleGroup.WORD, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.wordItalic, "clicking I must be stored")
        assertEquals(false, get().dictionarySettings.wordBold, "and must not also set bold")
    }

    /**
     * The word section has no underline setting — the tab wires `underline = false` with an empty
     * `onUnderlineChange` — but `TextStyleButtons` still renders a U button, so it is on screen and
     * clickable and does nothing. Pinned so that wiring it up later fails here and gets a real test.
     */
    @Test
    fun `the underline button is rendered but wired to nothing`() = dictionaryTab { get ->
        // First prove the group is live, so "U changed nothing" is a statement about U rather than
        // about the tab: S sits in the same TextStyleButtons and does write.
        styleButton(DictStyleGroup.WORD, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.wordShadow, "the group must be wired up for this to mean anything")

        val before = get().dictionarySettings
        styleButton(DictStyleGroup.WORD, "U").performClick()
        waitForIdle()
        assertEquals(before, get().dictionarySettings, "the word section stores no underline flag")
    }

    @Test
    fun `the shadow button stores the flag and reveals the shadow row`() = dictionaryTab { get ->
        assertEquals(false, get().dictionarySettings.wordShadow, "no shadow out of the box")
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)

        styleButton(DictStyleGroup.WORD, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.wordShadow, "clicking S must be stored")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(1)
        assertEquals(false, get().dictionarySettings.referenceShadow, "the reference shadow must be untouched")

        styleButton(DictStyleGroup.WORD, "S").performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.wordShadow, "clicking S again must clear it")
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)
    }

    // ── Shadow detail row ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the word shadow colour stores the confirmed hex`() {
        dictionaryTab(initial = dictionarySettings { copy(wordShadow = true, wordShadowColor = "#123456") }) { get ->
            recolor(fromHex = "#123456", toHex = "#654321")
            assertTrue(
                get().dictionarySettings.wordShadowColor.equals("#654321", ignoreCase = true),
                "the confirmed hex must become the word shadow colour",
            )
            assertColorFieldShows("#654321", "the word shadow colour field")
        }
    }

    @Test
    fun `the word shadow size stores a new percentage`() {
        dictionaryTab(initial = dictionarySettings { copy(wordShadow = true, wordShadowSize = 111) }) { get ->
            retypeNumberField(showing = 111, to = 140)
            assertEquals(140, get().dictionarySettings.wordShadowSize, "the typed size must be stored")
            assertEquals(90, get().dictionarySettings.wordShadowOpacity, "the intensity must be untouched")
        }
    }

    @Test
    fun `the word shadow intensity stores a new percentage`() {
        dictionaryTab(initial = dictionarySettings { copy(wordShadow = true, wordShadowOpacity = 77) }) { get ->
            retypeNumberField(showing = 77, to = 40)
            assertEquals(40, get().dictionarySettings.wordShadowOpacity, "the typed intensity must be stored")
            assertEquals(100, get().dictionarySettings.wordShadowSize, "the size must be untouched")
        }
    }

    // ── Font ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the word font dropdown stores the picked family`() {
        val target = uniquelyNamedFont()
        dictionaryTab(initial = dictionarySettings { copy(wordFontType = SENTINEL_FONT) }) { get ->
            pickFont(showing = SENTINEL_FONT, to = target)
            assertEquals(target, get().dictionarySettings.wordFontType, "the picked family must be stored")
            assertEquals("Arial", get().dictionarySettings.referenceFontType, "the reference font must be untouched")
        }
    }

    /**
     * The dropdown's arrow — a 14dp `Icon` with a bare `clickable`, no role and no content
     * description, so it is reachable only by position. It is the affordance a mouse user actually
     * uses; [pickFont] takes the other route, typing a filter and committing on the IME action.
     *
     * Both what it opens and what picking from it stores are asserted, because the menu item's
     * `onClick` is a separate path from the keyboard commit and writes the setting itself.
     */
    @Test
    fun `the font dropdown arrow opens the menu and a picked font is stored`() {
        val installed = mixedCaseInstalledFont()
        // The field is parked on the lowercased spelling: the filter is case-insensitive, so the
        // menu offers the properly-cased family, which is a value the field does not already show.
        val settings = dictionarySettings {
            copy(wordFontType = installed.lowercase(), referenceFontType = SENTINEL_FONT)
        }
        dictionaryTab(initial = settings) { get ->
            onAllNodesWithText(installed).assertCountEquals(0)

            fontDropdownArrow(group = 0).performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText(installed).assertCountEquals(1) // the menu is open and lists the family

            onAllNodesWithText(installed)[0].performClick()
            waitForIdle()
            assertEquals(installed, get().dictionarySettings.wordFontType, "picking from the menu must be stored")
            assertFontFieldShows(installed, "the word font dropdown")
            assertEquals(
                SENTINEL_FONT,
                get().dictionarySettings.referenceFontType,
                "the reference font must be untouched",
            )
        }
    }

    /**
     * Clicking the arrow a second time does **not** close the menu — it stays open.
     *
     * The arrow's handler is a toggle (`if (expanded) expanded = false else { requestFocus(); expanded * = true }`), but it never sees `expanded == true`: the click pulls focus off the editor first,
     * and the editor's `onFocusChanged` has already set `expanded = false` by the time the handler
     * runs, so it takes the opening branch again. The close branch is therefore unreachable from the
     * arrow.
     *
     * Pinned as the behaviour that actually ships rather than the behaviour the code reads like. It
     * is in the shared `FontSettingsDropdown`, so it affects every font dropdown in the app, not just
     * this tab; fixing it will fail this test, which is the point.
     */
    @Test
    fun `clicking the arrow again leaves the menu open`() {
        val installed = mixedCaseInstalledFont()
        val parked = installed.lowercase()
        val settings = dictionarySettings {
            copy(wordFontType = parked, referenceFontType = SENTINEL_FONT)
        }
        dictionaryTab(initial = settings) { get ->
            fontDropdownArrow(group = 0).performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText(installed).assertCountEquals(1)

            fontDropdownArrow(group = 0).performClick()
            waitForIdle()
            onAllNodesWithText(installed).assertCountEquals(1) // still listed: the menu did not close
            assertEquals(parked, get().dictionarySettings.wordFontType, "and nothing was stored either way")
        }
    }

    /**
     * Typing a filter no family matches leaves the menu with only its "no results" item, which is
     * disabled — the branch that keeps the dropdown from committing nonsense.
     */
    @Test
    fun `a filter matching no family offers a disabled no-results item`() {
        dictionaryTab { get ->
            pickFontFilterOnly(showing = "Arial", filter = SENTINEL_FONT)
            onNodeWithText("No results found for \"$SENTINEL_FONT\"")
                .assertExists("the menu must say why it is empty")
                .assertIsNotEnabled()
            assertEquals("Arial", get().dictionarySettings.wordFontType, "an unmatched filter stores nothing")
        }
    }

    @Test
    fun `the word font size stores a new value`() = dictionaryTab { get ->
        assertEquals(70, get().dictionarySettings.wordFontSize, "70 out of the box")
        retypeNumberField(showing = 70, to = 96)
        assertEquals(96, get().dictionarySettings.wordFontSize, "the typed size must be stored")
        assertEquals(32, get().dictionarySettings.definitionFontSize, "the definition size must be untouched")
    }

    /**
     * The field accepts 8..200. `NumberSettingsTextField` displays whatever is typed either way and
     * only withholds the callback when the value is out of range, so the stored value is the only
     * thing that says which happened — and a fresh render is what an operator would actually see.
     */
    @Test
    fun `a word font size outside the range is not stored`() = dictionaryTab { get ->
        retypeNumberField(showing = 70, to = 400)
        assertEquals(70, get().dictionarySettings.wordFontSize, "400 is above the 8..200 range")

        // Proves the field was live throughout: a value inside the range does land. Without this the
        // test would pass just as well against a field wired to nothing.
        retypeNumberField(showing = 400, to = 200)
        assertEquals(200, get().dictionarySettings.wordFontSize, "200 is the top of the range and is accepted")
    }

    @Test
    fun `a typed word font size is what a fresh render of the saved settings shows`() {
        var saved = 0
        dictionaryTab { get ->
            retypeNumberField(showing = 70, to = 120)
            saved = get().dictionarySettings.wordFontSize
        }
        assertEquals(120, saved, "the value must have been stored to be re-rendered")
        dictionaryTab(initial = dictionarySettings { copy(wordFontSize = saved) }) { _ ->
            assertNumberFieldShows(120, "the word font size on a fresh render")
        }
    }
}
