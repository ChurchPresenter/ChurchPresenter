@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The row of translation chips above the Bible pane's controls. It is the only pane with one — every
 * other category has a single set of settings — and it is drawn only once there is a stack worth
 * choosing from.
 */
class ProjectionCustomizeTranslationChipsTest {

    private fun translation(file: String, abbreviation: String = "", size: Int = 60) =
        BibleTranslationSettings(fileName = file, customAbbreviation = abbreviation, textFontSize = size)

    private fun output(vararg translations: BibleTranslationSettings) = AppSettings(
        bibleSettings = BibleSettings(translations = translations.toList()),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
        ),
    )

    private fun AppSettings.stored(index: Int): BibleTranslationSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")
            .translationList()[index]

    private val stack = arrayOf(
        translation("kjv.spb", "KJV", size = 61),
        translation("niv.spb", "NIV", size = 62),
        translation("esv.spb", "ESV", size = 63),
    )

    // ── When the row is drawn ─────────────────────────────────────────────────

    @Test
    fun `one translation draws no chips, because there is no choice`() {
        projectionTab(output(translation("kjv.spb", "KJV"))) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithTag(CUSTOMIZE_TRANSLATION_ROW_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `two translations draw the row`() {
        projectionTab(output(stack[0], stack[1])) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithTag(CUSTOMIZE_TRANSLATION_ROW_TAG).assertExists()
        }
    }

    @Test
    fun `no other category draws the row`() {
        for (pane in listOf(CustomizePane.SONGS, CustomizePane.DICTIONARY, CustomizePane.BACKGROUND)) {
            projectionTab(output(*stack)) { _ ->
                openCustomizePane(pane, override = false)
                onNodeWithTag(CUSTOMIZE_TRANSLATION_ROW_TAG).assertDoesNotExist()
            }
        }
    }

    // ── What the chips say ────────────────────────────────────────────────────

    @Test
    fun `each chip is numbered and named`() {
        projectionTab(output(*stack)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithText("1 · KJV").assertExists()
            onNodeWithText("2 · NIV").assertExists()
            onNodeWithText("3 · ESV").assertExists()
        }
    }

    @Test
    fun `a translation with no abbreviation of its own is still numbered and named`() {
        projectionTab(output(translation("kjv.spb"), translation("niv.spb"))) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithTag(CUSTOMIZE_TRANSLATION_ROW_TAG).assertExists()
            // The abbreviation falls back to one derived from the file name; what matters here is
            // that each chip still carries its position and some name rather than coming out blank.
            onNode(hasText("1 · ", substring = true)).assertExists()
            onNode(hasText("2 · ", substring = true)).assertExists()
        }
    }

    // ── Which translation the controls edit ───────────────────────────────────

    @Test
    fun `the first translation is edited until another chip is picked`() {
        projectionTab(output(*stack)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(61, 70)

            assertEquals(70, get().stored(0).textFontSize)
            assertEquals(62, get().stored(1).textFontSize, "the second must be untouched")
            assertEquals(63, get().stored(2).textFontSize)
        }
    }

    @Test
    fun `picking the second chip points the controls at it`() {
        projectionTab(output(*stack)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithText("2 · NIV").performClick()
            waitForIdle()
            retypeNumberField(62, 70)

            assertEquals(70, get().stored(1).textFontSize)
            assertEquals(61, get().stored(0).textFontSize, "the first must be untouched")
        }
    }

    @Test
    fun `picking the third chip points the controls at it`() {
        projectionTab(output(*stack)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithText("3 · ESV").performClick()
            waitForIdle()
            retypeNumberField(63, 70)

            assertEquals(70, get().stored(2).textFontSize)
            assertEquals(61, get().stored(0).textFontSize)
            assertEquals(62, get().stored(1).textFontSize)
        }
    }

    @Test
    fun `each translation keeps what was typed into it`() {
        projectionTab(output(*stack)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(61, 71)
            onNodeWithText("2 · NIV").performClick()
            waitForIdle()
            retypeNumberField(62, 72)
            onNodeWithText("3 · ESV").performClick()
            waitForIdle()
            retypeNumberField(63, 73)

            assertEquals(listOf(71, 72, 73), (0..2).map { get().stored(it).textFontSize })
        }
    }

    @Test
    fun `coming back to a chip shows what it stored`() {
        projectionTab(output(*stack)) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(61, 71)
            onNodeWithText("2 · NIV").performClick()
            waitForIdle()
            onNodeWithText("1 · KJV").performClick()
            waitForIdle()

            assertNumberFieldShows(71, "the first translation's size field")
        }
    }

    @Test
    fun `the chosen chip survives moving between elements`() {
        projectionTab(output(*stack)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithText("2 · NIV").performClick()
            waitForIdle()
            openElement(CustomizeElement.BIBLE_REFERENCE)
            openElement(CustomizeElement.BIBLE_TEXT)
            retypeNumberField(62, 70)

            assertEquals(70, get().stored(1).textFontSize)
        }
    }

    @Test
    fun `a two-translation stack offers exactly two chips`() {
        projectionTab(output(stack[0], stack[1])) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithText("1 · KJV").assertExists()
            onNodeWithText("2 · NIV").assertExists()
            onNodeWithText("3 · ESV").assertDoesNotExist()
        }
    }
}
