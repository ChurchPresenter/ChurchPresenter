@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Dictionary pane's four text blocks and the card behind them: what each element offers, what
 * each control stores, and that no element writes another's field.
 */
class ProjectionCustomizeDictionaryElementsTest {

    private fun output() = AppSettings(
        dictionarySettings = DictionarySettings(
            wordFontSize = 61,
            wordColor = "#AABBCC",
            referenceFontSize = 37,
            referenceColor = "#445566",
            definitionFontSize = 47,
            definitionColor = "#556677",
            kjvUsageFontSize = 51,
            kjvUsageColor = "#667788",
            cardBackgroundColor = "#889900",
            cardBackgroundOpacity = 0.42f,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
        ),
    )

    private fun AppSettings.stored(): DictionarySettings = assertNotNull(
        projectionSettings.screenAssignments[0].dictionaryOverride,
        "the output must have its own Dictionary",
    )

    private fun ComposeUiTest.open(element: CustomizeElement, override: Boolean = true) =
        openCustomizePane(CustomizePane.DICTIONARY, element, override = override)

    // ── What each element offers ──────────────────────────────────────────────

    @Test
    fun `the word offers a Show switch`() {
        projectionTab(output()) { _ ->
            open(CustomizeElement.DICTIONARY_WORD, override = false)
            onNodeWithText("Show").assertExists()
        }
    }

    @Test
    fun `the definition offers a Show switch`() {
        projectionTab(output()) { _ ->
            open(CustomizeElement.DICTIONARY_DEFINITION, override = false)
            onNodeWithText("Show").assertExists()
        }
    }

    @Test
    fun `the KJV usage offers a Show switch`() {
        projectionTab(output()) { _ ->
            open(CustomizeElement.DICTIONARY_KJV, override = false)
            onNodeWithText("Show").assertExists()
        }
    }

    @Test
    fun `the card offers no Show switch, because it is what everything is drawn on`() {
        projectionTab(output()) { _ ->
            open(CustomizeElement.DICTIONARY_CARD, override = false)
            onNodeWithText("Show").assertDoesNotExist()
        }
    }

    @Test
    fun `the definition and the KJV usage set no face of their own`() {
        for (element in listOf(CustomizeElement.DICTIONARY_DEFINITION, CustomizeElement.DICTIONARY_KJV)) {
            projectionTab(output()) { _ ->
                open(element, override = false)
                onNodeWithText("B").assertDoesNotExist()
            }
        }
    }

    // ── The Show switches ─────────────────────────────────────────────────────

    @Test
    fun `switching the definition off writes only its own flag`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_DEFINITION)
            toggleCheckbox("Show")

            val stored = get().stored()
            assertFalse(stored.showDefinition)
            assertTrue(stored.showWord, "the word must still show")
            assertTrue(stored.showKjvUsage)
        }
    }

    @Test
    fun `switching the KJV usage off writes only its own flag`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_KJV)
            toggleCheckbox("Show")

            val stored = get().stored()
            assertFalse(stored.showKjvUsage)
            assertTrue(stored.showDefinition)
        }
    }

    @Test
    fun `switching the word off writes only its own flag`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_WORD)
            toggleCheckbox("Show")

            val stored = get().stored()
            assertFalse(stored.showWord)
            assertTrue(stored.showReference)
        }
    }

    @Test
    fun `a Show switch goes back on`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_KJV)
            toggleCheckbox("Show")
            assertFalse(get().stored().showKjvUsage)
            toggleCheckbox("Show")
            assertTrue(get().stored().showKjvUsage)
        }
    }

    // ── Sizes ─────────────────────────────────────────────────────────────────

    @Test
    fun `the word's size writes the word`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_WORD)
            retypeNumberField(61, 72)

            val stored = get().stored()
            assertEquals(72, stored.wordFontSize)
            assertEquals(47, stored.definitionFontSize, "no other block may move")
            assertEquals(51, stored.kjvUsageFontSize)
        }
    }

    @Test
    fun `the definition's size writes the definition`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_DEFINITION)
            retypeNumberField(47, 30)

            val stored = get().stored()
            assertEquals(30, stored.definitionFontSize)
            assertEquals(61, stored.wordFontSize)
        }
    }

    @Test
    fun `the KJV usage's size writes the KJV usage`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_KJV)
            retypeNumberField(51, 24)

            val stored = get().stored()
            assertEquals(24, stored.kjvUsageFontSize)
            assertEquals(37, stored.referenceFontSize)
        }
    }

    @Test
    fun `a size outside the range is not stored`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_DEFINITION)
            retypeNumberField(47, 400)

            assertEquals(47, get().stored().definitionFontSize)
        }
    }

    @Test
    fun `the smallest size the range allows is stored`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_DEFINITION)
            retypeNumberField(47, 8)

            assertEquals(8, get().stored().definitionFontSize)
        }
    }

    // ── Colors ────────────────────────────────────────────────────────────────

    @Test
    fun `the word's color writes the word`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_WORD)
            recolor("#AABBCC", "#112233")

            val stored = get().stored()
            assertEquals("#112233", stored.wordColor)
            assertEquals("#556677", stored.definitionColor, "no other block may move")
        }
    }

    @Test
    fun `the definition's color writes the definition`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_DEFINITION)
            recolor("#556677", "#223344")

            val stored = get().stored()
            assertEquals("#223344", stored.definitionColor)
            assertEquals("#AABBCC", stored.wordColor)
        }
    }

    @Test
    fun `the KJV usage's color writes the KJV usage`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_KJV)
            recolor("#667788", "#334455")

            val stored = get().stored()
            assertEquals("#334455", stored.kjvUsageColor)
            assertEquals("#445566", stored.referenceColor)
        }
    }

    @Test
    fun `the card's color writes the card`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_CARD)
            recolor("#889900", "#445566")

            val stored = get().stored()
            assertEquals("#445566", stored.cardBackgroundColor)
            assertEquals("#AABBCC", stored.wordColor, "the text on it must not move")
        }
    }

    // ── The card's opacity ────────────────────────────────────────────────────

    @Test
    fun `the card's opacity is shown as a percentage`() {
        projectionTab(output()) { _ ->
            open(CustomizeElement.DICTIONARY_CARD, override = false)
            onNodeWithText("42").assertExists("0.42 stored reads as 42 on screen")
        }
    }

    @Test
    fun `retyping the card's opacity stores it as a fraction`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_CARD)
            retypeNumberField(42, 80)

            assertEquals(0.8f, get().stored().cardBackgroundOpacity)
        }
    }

    @Test
    fun `a fully transparent card is a setting, not an absence`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_CARD)
            retypeNumberField(42, 0)

            assertEquals(0f, get().stored().cardBackgroundOpacity)
        }
    }

    @Test
    fun `an opacity past 100 is not stored`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_CARD)
            retypeNumberField(42, 140)

            assertEquals(0.42f, get().stored().cardBackgroundOpacity)
        }
    }

    // ── Independence across the whole pane ────────────────────────────────────

    @Test
    fun `each element's size is a field of its own`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_WORD)
            retypeNumberField(61, 72)
            openElement(CustomizeElement.DICTIONARY_DEFINITION)
            retypeNumberField(47, 30)
            openElement(CustomizeElement.DICTIONARY_KJV)
            retypeNumberField(51, 24)

            val stored = get().stored()
            assertEquals(listOf(72, 30, 24), listOf(stored.wordFontSize, stored.definitionFontSize, stored.kjvUsageFontSize))
            assertEquals(37, stored.referenceFontSize, "the reference was never opened and must be untouched")
        }
    }

    @Test
    fun `chipping between elements keeps what each already stored`() {
        projectionTab(output()) { get ->
            open(CustomizeElement.DICTIONARY_WORD)
            recolor("#AABBCC", "#112233")
            openElement(CustomizeElement.DICTIONARY_CARD)
            openElement(CustomizeElement.DICTIONARY_WORD)

            assertEquals("#112233", get().stored().wordColor)
        }
    }
}
