@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test

/**
 * Pins the shape of the tab and validates the ordinals the behaviour tests use.
 *
 * The tab repeats the same handful of shared controls six times over and publishes no tags, so the
 * switches and style buttons are reached by position. That only stays honest while the layout holds,
 * which is what this class asserts: a control added, removed or reordered fails here first.
 */
class DictionarySettingsTabStructureTest {

    // ── Sections ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab renders every section exactly once`() = dictionaryTab { _ ->
        for (section in Section.all) {
            onAllNodesWithText(section).assertCountEquals(1)
        }
    }

    // ── Switches ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab offers one switch per toggleable setting`() = dictionaryTab { _ ->
        switches().assertCountEquals(Switches.COUNT)
        onAllNodesWithText("Show").assertCountEquals(4)
        onAllNodesWithText("Fade In").assertCountEquals(1)
        onAllNodesWithText("Fade Out").assertCountEquals(1)
    }

    /**
     * Every switch starts on, so a shared `assertIsOn` proves nothing about which is which. The
     * ordinals are validated instead by turning each one off from a fixture and seeing that exactly
     * that switch — and no other — reads off.
     */
    @Test
    fun `each switch ordinal addresses the setting it is named for`() {
        val cases = mapOf(
            Switches.SHOW_WORD to dictionarySettings { copy(showWord = false) },
            Switches.SHOW_DEFINITION to dictionarySettings { copy(showDefinition = false) },
            Switches.SHOW_REFERENCE to dictionarySettings { copy(showReference = false) },
            Switches.SHOW_KJV to dictionarySettings { copy(showKjvUsage = false) },
            Switches.FADE_IN to dictionarySettings { copy(fadeIn = false) },
            Switches.FADE_OUT to dictionarySettings { copy(fadeOut = false) },
        )
        for ((ordinal, settings) in cases) {
            dictionaryTab(initial = settings) { _ ->
                for (other in 0 until Switches.COUNT) {
                    if (other == ordinal) switch(other).assertIsOff() else switch(other).assertIsOn()
                }
            }
        }
    }

    // ── Colour, number and font fields ──────────────────────────────────────────────────────────

    @Test
    fun `the tab offers one colour field per colour setting`() = dictionaryTab { _ ->
        // Word, Definition, Card Background, Reference, KJV — the two shadow colours are hidden
        // until their shadow is switched on.
        colorFields().assertCountEquals(5)
    }

    @Test
    fun `switching both shadows on adds their colour fields`() {
        dictionaryTab(initial = dictionarySettings { copy(wordShadow = true, referenceShadow = true) }) { _ ->
            colorFields().assertCountEquals(7)
        }
    }

    @Test
    fun `the tab offers one number field per numeric setting`() = dictionaryTab { _ ->
        // Word, Definition, Reference and KJV font sizes; the four shadow fields are hidden.
        numberFields().assertCountEquals(4)
    }

    @Test
    fun `switching both shadows on adds their size and intensity fields`() {
        dictionaryTab(initial = dictionarySettings { copy(wordShadow = true, referenceShadow = true) }) { _ ->
            numberFields().assertCountEquals(8)
            onAllNodesWithText("SIZE (%)").assertCountEquals(2)
            onAllNodesWithText("INTENSITY (%)").assertCountEquals(2)
        }
    }

    @Test
    fun `only the word and reference sections offer a font dropdown`() = dictionaryTab { _ ->
        fontFields().assertCountEquals(2)
        onAllNodesWithText("FONT TYPE").assertCountEquals(2)
        onAllNodesWithText("FONT SIZE").assertCountEquals(4)
    }

    // ── Style buttons ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `both style groups offer all four buttons`() = dictionaryTab { _ ->
        for (label in listOf("B", "I", "U", "S")) {
            onAllNodesWithText(label).assertCountEquals(DictStyleGroup.COUNT)
        }
    }

    // ── Sliders ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab offers a captioned slider for opacity and for duration`() = dictionaryTab { get ->
        onNodeWithText("Opacity").assertExists("the card background opacity must be captioned")
        onNodeWithText("Transition Duration").assertExists("as must the transition duration")
        assertOpacityReads(get().dictionarySettings.cardBackgroundOpacity)
        assertDurationReads(get().dictionarySettings.transitionDuration)
    }

    // ── The stepper arrows ──────────────────────────────────────────────────────────────────────

    /**
     * Every stepper field publishes increment/decrement arrows, and each is laid out at a size a
     * click can reach.
     *
     * These arrows used to collapse to zero pixels wide on every tab in the app — a defect in the
     * shared `NumberSettingsTextField`, which this test pinned as present-but-unusable while it
     * stood. Now that it is fixed, the same test guards the fix.
     */
    @Test
    fun `the stepper arrows are laid out where they can be clicked`() {
        dictionaryTab(initial = dictionarySettings { copy(wordShadow = true, referenceShadow = true) }) { _ ->
            assertStepperArrowsUsable(expected = 8)
        }
    }
}
