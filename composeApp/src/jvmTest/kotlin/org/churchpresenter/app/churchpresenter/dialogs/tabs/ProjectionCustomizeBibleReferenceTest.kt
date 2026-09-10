@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * The Bible pane's reference element: where the reference sits relative to the verse, and whether
 * the translation's abbreviation rides along with it.
 */
class ProjectionCustomizeBibleReferenceTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(
                    fileName = "kjv.spb",
                    referenceFontSize = 37,
                    lowerThirdReferenceFontSize = 38,
                    referenceColor = "#445566",
                    lowerThirdReferenceColor = "#778899",
                ),
            ),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.stored(): BibleTranslationSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")
            .translationList()[0]

    private fun open(test: androidx.compose.ui.test.ComposeUiTest, override: Boolean = true) =
        test.openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_REFERENCE, override = override)

    // ── What the element offers ───────────────────────────────────────────────

    @Test
    fun `the reference offers a position and an abbreviation switch`() {
        projectionTab(output()) { _ ->
            open(this, override = false)
            onNodeWithText("Position").assertExists()
            onNodeWithText("Abbreviation").assertExists()
            onNodeWithText("Horizontal alignment:").assertExists()
        }
    }

    @Test
    fun `the verse text offers neither`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT, override = false)
            onNodeWithText("Position").assertDoesNotExist()
            onNodeWithText("Abbreviation").assertDoesNotExist()
        }
    }

    @Test
    fun `the reference has no style quartet of its own`() {
        projectionTab(output()) { _ ->
            open(this, override = false)
            onNodeWithText("B").assertDoesNotExist()
            onNodeWithText("I").assertDoesNotExist()
        }
    }

    // ── Position ──────────────────────────────────────────────────────────────

    @Test
    fun `putting the reference above writes the full screen's own`() {
        projectionTab(output()) { get ->
            open(this)
            positionButton(group = 0, above = true).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(REFERENCE_ABOVE, stored.referencePosition)
            assertEquals(
                REFERENCE_BELOW,
                stored.lowerThirdReferencePosition,
                "the band's own position must be untouched",
            )
        }
    }

    @Test
    fun `putting the reference below writes the full screen's own`() {
        val above = output().let {
            it.copy(
                bibleSettings = it.bibleSettings.withTranslations(
                    listOf(it.bibleSettings.translationList()[0].copy(referencePosition = REFERENCE_ABOVE)),
                ),
            )
        }
        projectionTab(above) { get ->
            open(this)
            positionButton(group = 0, above = false).performScrollTo().performClick()
            waitForIdle()

            assertEquals(REFERENCE_BELOW, get().stored().referencePosition)
        }
    }

    @Test
    fun `the position writes the band's own instead`() {
        projectionTab(output(band)) { get ->
            open(this)
            positionButton(group = 0, above = true).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(REFERENCE_ABOVE, stored.lowerThirdReferencePosition)
            assertEquals(REFERENCE_BELOW, stored.referencePosition, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `the position row offers exactly two choices`() {
        projectionTab(output()) { _ ->
            open(this, override = false)
            onNodeWithContentDescription("Above").assertExists()
            onNodeWithContentDescription("Below").assertExists()
        }
    }

    // ── The abbreviation switch ───────────────────────────────────────────────

    @Test
    fun `the abbreviation switch writes the translation's own flag`() {
        projectionTab(output()) { get ->
            open(this)
            toggleCheckbox("Abbreviation")

            assertTrue(get().stored().showAbbreviation, "it ships off and must have come on")
        }
    }

    @Test
    fun `the abbreviation is one value, not a pair`() {
        projectionTab(output(band)) { get ->
            open(this)
            toggleCheckbox("Abbreviation")

            assertTrue(
                get().stored().showAbbreviation,
                "a band writes the same flag the full screen does — the reference names one translation",
            )
        }
    }

    @Test
    fun `the abbreviation switch goes back off`() {
        projectionTab(output()) { get ->
            open(this)
            toggleCheckbox("Abbreviation")
            assertTrue(get().stored().showAbbreviation)
            toggleCheckbox("Abbreviation")
            assertFalse(get().stored().showAbbreviation)
        }
    }

    // ── Horizontal alignment ──────────────────────────────────────────────────

    @Test
    fun `the alignment writes the full screen's reference`() {
        projectionTab(output()) { get ->
            open(this)
            horizontalAlignButton(group = 0, which = HAlign.CENTER).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(Constants.CENTER, stored.referenceHorizontalAlignment)
            assertEquals(
                Constants.RIGHT,
                stored.lowerThirdReferenceHorizontalAlignment,
                "the band's own alignment must be untouched, and a reference starts right",
            )
        }
    }

    @Test
    fun `the alignment writes the band's reference instead`() {
        projectionTab(output(band)) { get ->
            open(this)
            horizontalAlignButton(group = 0, which = HAlign.LEFT).performScrollTo().performClick()
            waitForIdle()

            val stored = get().stored()
            assertEquals(Constants.LEFT, stored.lowerThirdReferenceHorizontalAlignment)
            assertEquals(Constants.RIGHT, stored.referenceHorizontalAlignment)
        }
    }

    @Test
    fun `the alignment leaves the verse text's own alone`() {
        projectionTab(output()) { get ->
            open(this)
            horizontalAlignButton(group = 0, which = HAlign.CENTER).performScrollTo().performClick()
            waitForIdle()

            assertEquals(
                BibleTranslationSettings().textHorizontalAlignment,
                get().stored().textHorizontalAlignment,
            )
        }
    }

    // ── Size and color, on both profiles ──────────────────────────────────────

    @Test
    fun `the size writes the full screen's reference`() {
        projectionTab(output()) { get ->
            open(this)
            retypeNumberField(37, 44)

            val stored = get().stored()
            assertEquals(44, stored.referenceFontSize)
            assertEquals(38, stored.lowerThirdReferenceFontSize)
        }
    }

    @Test
    fun `the size writes the band's reference`() {
        projectionTab(output(band)) { get ->
            open(this)
            retypeNumberField(38, 21)

            val stored = get().stored()
            assertEquals(21, stored.lowerThirdReferenceFontSize)
            assertEquals(37, stored.referenceFontSize)
        }
    }

    @Test
    fun `the color writes the full screen's reference`() {
        projectionTab(output()) { get ->
            open(this)
            recolor("#445566", "#112233")

            val stored = get().stored()
            assertEquals("#112233", stored.referenceColor)
            assertEquals("#778899", stored.lowerThirdReferenceColor)
        }
    }

    @Test
    fun `the color writes the band's reference`() {
        projectionTab(output(band)) { get ->
            open(this)
            recolor("#778899", "#334455")

            val stored = get().stored()
            assertEquals("#334455", stored.lowerThirdReferenceColor)
            assertEquals("#445566", stored.referenceColor)
        }
    }

    @Test
    fun `a size outside the range is not stored`() {
        projectionTab(output()) { get ->
            open(this)
            retypeNumberField(37, 400)

            assertEquals(37, get().stored().referenceFontSize)
        }
    }

    @Test
    fun `the reference's controls leave the verse text alone`() {
        projectionTab(output()) { get ->
            open(this)
            retypeNumberField(37, 44)

            val stored = get().stored()
            assertEquals(BibleTranslationSettings().textFontSize, stored.textFontSize)
            assertEquals(BibleTranslationSettings().textColor, stored.textColor)
        }
    }
}
