@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

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
 * The Dictionary pane's reference element: its Show switch, its font picker and its shadow rows.
 * `ProjectionCustomizeSurfaceControlsTest` covers the sizes and colors of every element.
 */
class ProjectionCustomizeDictionaryExtrasTest {

    private fun output() = AppSettings(
        dictionarySettings = DictionarySettings(
            wordFontType = SENTINEL_FONT,
            referenceFontType = SENTINEL_FONT,
            referenceFontSize = 37,
            referenceColor = "#445566",
            referenceShadowColor = "#778899",
            referenceShadowSize = 41,
            referenceShadowOpacity = 63,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
        ),
    )

    private fun AppSettings.stored(): DictionarySettings = assertNotNull(
        projectionSettings.screenAssignments[0].dictionaryOverride,
        "the output must have its own Dictionary",
    )

    // ── The Show switch ───────────────────────────────────────────────────────

    @Test
    fun `the reference can be switched off`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Show")

            assertFalse(get().stored().showReference, "it ships on and must have gone off")
        }
    }

    @Test
    fun `switching the reference off leaves the other elements showing`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Show")

            val stored = get().stored()
            assertTrue(stored.showDefinition, "the definition is not the reference")
            assertTrue(stored.showWord)
        }
    }

    // ── The font picker ───────────────────────────────────────────────────────

    @Test
    fun `the reference font picker writes the reference's face`() {
        val family = uniquelyNamedFont()
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.referenceFontType)
            assertEquals(SENTINEL_FONT, stored.wordFontType, "the word's own face must be untouched")
        }
    }

    @Test
    fun `the word font picker writes the word's face`() {
        val family = uniquelyNamedFont()
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_WORD)
            pickFont(SENTINEL_FONT, family)

            val stored = get().stored()
            assertEquals(family, stored.wordFontType)
            assertEquals(SENTINEL_FONT, stored.referenceFontType, "the reference's own face must be untouched")
        }
    }

    @Test
    fun `only the word and the reference carry a font picker`() {
        for (element in listOf(CustomizeElement.DICTIONARY_DEFINITION, CustomizeElement.DICTIONARY_KJV)) {
            projectionTab(output()) { _ ->
                openCustomizePane(CustomizePane.DICTIONARY, element, override = false)
                assertEquals(
                    0,
                    fontFields().fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                    "$element sets no face of its own",
                )
            }
        }
    }

    // ── The shadow rows ───────────────────────────────────────────────────────

    @Test
    fun `the reference's shadow rows are folded away until the box is ticked`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE, override = false)
            onNodeWithText("SIZE (%)").assertDoesNotExist()
            onNodeWithText("INTENSITY (%)").assertDoesNotExist()
        }
    }

    @Test
    fun `ticking the shadow box unfolds its three rows`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Shadow")

            assertTrue(get().stored().referenceShadow)
            onNodeWithText("SIZE (%)").assertExists()
            onNodeWithText("INTENSITY (%)").assertExists()
            onNodeWithText("#778899").assertExists("and the shadow's own color field")
        }
    }

    @Test
    fun `the shadow size writes the reference's own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Shadow")
            retypeNumberField(41, 200)

            assertEquals(200, get().stored().referenceShadowSize)
        }
    }

    @Test
    fun `the shadow intensity writes the reference's own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Shadow")
            retypeNumberField(63, 25)

            assertEquals(25, get().stored().referenceShadowOpacity)
        }
    }

    @Test
    fun `the shadow color writes the reference's own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Shadow")
            recolor("#778899", "#112233")

            val stored = get().stored()
            assertEquals("#112233", stored.referenceShadowColor)
            assertEquals("#445566", stored.referenceColor, "the text's own color must not move with it")
        }
    }

    @Test
    fun `an intensity past 100 is not stored`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Shadow")
            retypeNumberField(63, 140)

            assertEquals(63, get().stored().referenceShadowOpacity)
        }
    }

    @Test
    fun `unticking the shadow folds the rows away again`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            toggleCheckbox("Shadow")
            onNodeWithText("SIZE (%)").assertExists()

            toggleCheckbox("Shadow")
            assertFalse(get().stored().referenceShadow)
            onNodeWithText("SIZE (%)").assertDoesNotExist()
        }
    }
}
