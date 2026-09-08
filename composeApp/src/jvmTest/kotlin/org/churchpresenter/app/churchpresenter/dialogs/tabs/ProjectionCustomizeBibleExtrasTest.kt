@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Bible pane's face, alignment and text-backing controls, on both stored profiles.
 *
 * `ProjectionCustomizeBibleControlsTest` covers the sizes and colours; these are the rest of the
 * verse-text group. The vertical alignment is the odd one out and is asserted as such: it is one
 * value on `BibleSettings` rather than a pair on the translation, so a band and a full screen share
 * it.
 */
class ProjectionCustomizeBibleExtrasTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(
                    fileName = "kjv.spb",
                    textFontType = SENTINEL_FONT,
                    lowerThirdTextFontType = SENTINEL_FONT,
                    textFontSize = 61,
                    lowerThirdTextFontSize = 62,
                ),
            ),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.storedTranslation(): BibleTranslationSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")
            .translationList()[0]

    private fun AppSettings.storedBible(): BibleSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")

    private val backdropChip = "Text backing"
    private val backdropCaret = "Text backing options"

    // ── The face buttons ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the style quartet writes the full screen's verse text`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            for (glyph in listOf("B", "U")) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().storedTranslation()
            assertTrue(stored.textBold && stored.textUnderline)
            assertFalse(stored.lowerThirdTextBold, "the band's verse text must be untouched")
        }
    }

    @Test
    fun `the style quartet writes the band's verse text instead`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            styleButton(group = 0, label = "I").performScrollTo().performClick()
            waitForIdle()

            val stored = get().storedTranslation()
            assertTrue(stored.lowerThirdTextItalic)
            assertFalse(stored.textItalic, "the full screen's verse text must be untouched")
        }
    }

    // ── The alignments ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the horizontal alignment writes the full screen's verse text`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            horizontalAlignButton(group = 0, which = HAlign.CENTER).performScrollTo().performClick()
            waitForIdle()

            val stored = get().storedTranslation()
            assertEquals(Constants.CENTER, stored.textHorizontalAlignment)
            assertEquals(
                Constants.LEFT,
                stored.lowerThirdTextHorizontalAlignment,
                "the band's own alignment must be untouched, and verse text starts left",
            )
        }
    }

    @Test
    fun `the horizontal alignment writes the band's verse text instead`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            horizontalAlignButton(group = 0, which = HAlign.RIGHT).performScrollTo().performClick()
            waitForIdle()

            val stored = get().storedTranslation()
            assertEquals(Constants.RIGHT, stored.lowerThirdTextHorizontalAlignment)
            assertEquals(Constants.LEFT, stored.textHorizontalAlignment)
        }
    }

    @Test
    fun `the vertical alignment is one value, not a pair`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithContentDescription("Align Top").performScrollTo().performClick()
            waitForIdle()

            assertEquals(
                Constants.TOP,
                get().storedBible().verticalAlignment,
                "it lives on the Bible settings rather than on the translation",
            )
        }
    }

    // ── The text backing ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the verse text carries a text-backing button`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithContentDescription(backdropChip).assertExists()
            onNodeWithContentDescription(backdropCaret).assertExists()
        }
    }

    @Test
    fun `the backing chip writes the full screen's verse text`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithContentDescription(backdropChip).performScrollTo().performClick()
            waitForIdle()

            val stored = get().storedTranslation()
            assertTrue(stored.textBackdrop.lineBackground)
            assertEquals(TextBackdrop(), stored.lowerThirdTextBackdrop, "the band's own must be untouched")
        }
    }

    @Test
    fun `the backing chip writes the band's verse text instead`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithContentDescription(backdropChip).performScrollTo().performClick()
            waitForIdle()

            val stored = get().storedTranslation()
            assertTrue(stored.lowerThirdTextBackdrop.lineBackground)
            assertEquals(TextBackdrop(), stored.textBackdrop, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `a look chosen in the backing dialog reaches the verse text`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithContentDescription(backdropCaret).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Both").performClick()
            waitForIdle()

            val stored = get().storedTranslation().textBackdrop
            assertTrue(stored.lineBackground && stored.border, "the dialog must write through the pane")
        }
    }

    // ── The font picker ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the font picker writes the full screen's verse text`() {
        val family = uniquelyNamedFont()
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            pickFont(SENTINEL_FONT, family)

            val stored = get().storedTranslation()
            assertEquals(family, stored.textFontType)
            assertEquals(SENTINEL_FONT, stored.lowerThirdTextFontType, "the band keeps its own face")
        }
    }

    @Test
    fun `the font picker writes the band's verse text instead`() {
        val family = uniquelyNamedFont()
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            pickFont(SENTINEL_FONT, family)

            val stored = get().storedTranslation()
            assertEquals(family, stored.lowerThirdTextFontType)
            assertEquals(SENTINEL_FONT, stored.textFontType, "the full screen keeps its own face")
        }
    }
}
