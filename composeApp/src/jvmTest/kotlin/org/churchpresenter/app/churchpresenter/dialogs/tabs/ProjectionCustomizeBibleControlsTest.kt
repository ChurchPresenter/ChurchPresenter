@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Driving the Bible pane's controls, and reading back what each one stored.
 *
 * `ProjectionCustomizeElementsTest` proves every element's pane draws; this proves the controls in
 * them write, and write to the profile the output actually uses. Every field in the pane is an
 * `if (lowerThird)` over a pair of stored values, so each control is driven on both shapes: a pane
 * that wrote the full-screen half of the pair on a lower third would look right and change nothing.
 *
 * Fixture values are deliberately distinct, because a number field is addressed by the number it is
 * displaying and a colour field by the hex it shows.
 */
class ProjectionCustomizeBibleControlsTest {

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(
            marginTop = 11,
            marginBottom = 22,
            marginLeft = 33,
            marginRight = 44,
            multiTranslationSpacing = 17,
            transitionDuration = 555f,
            lowerThirdHeightPercent = 29,
            translations = listOf(
                BibleTranslationSettings(
                    fileName = "kjv.spb",
                    textFontSize = 61,
                    lowerThirdTextFontSize = 62,
                    textColor = "#AABBCC",
                    lowerThirdTextColor = "#DDEEFF",
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

    private fun AppSettings.storedTranslation(): BibleTranslationSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")
            .translationList()[0]

    private fun AppSettings.storedBible(): BibleSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")

    // ── The verse text ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `size, colour and the style quartet write the full-screen verse`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(61, 72)
            recolor("#AABBCC", "#112233")
            for (glyph in listOf("B", "I", "U", SHADOW_GLYPH)) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().storedTranslation()
            assertEquals(72, stored.textFontSize)
            assertEquals("#112233", stored.textColor)
            assertTrue(stored.textBold && stored.textItalic && stored.textUnderline && stored.textShadow)
            assertEquals(62, stored.lowerThirdTextFontSize, "the band's own size must be untouched")
            assertEquals("#DDEEFF", stored.lowerThirdTextColor, "and so must its colour")
        }
    }

    @Test
    fun `size, colour and the style quartet write the band's verse instead`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(62, 26)
            recolor("#DDEEFF", "#334455")
            for (glyph in listOf("B", "I", "U", SHADOW_GLYPH)) {
                styleButton(group = 0, label = glyph).performScrollTo().performClick()
                waitForIdle()
            }

            val stored = get().storedTranslation()
            assertEquals(26, stored.lowerThirdTextFontSize)
            assertEquals("#334455", stored.lowerThirdTextColor)
            assertTrue(
                stored.lowerThirdTextBold && stored.lowerThirdTextItalic &&
                    stored.lowerThirdTextUnderline && stored.lowerThirdTextShadow,
            )
            assertEquals(61, stored.textFontSize, "the full screen's own size must be untouched")
            assertFalse(stored.textBold, "and its own styling with it")
        }
    }

    @Test
    fun `the alignments write the verse's own and the stack's shared one`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            horizontalAlignButton(group = 0, which = HAlign.RIGHT).performScrollTo().performClick()
            waitForIdle()
            onNodeWithContentDescription("Align Top").performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.RIGHT, get().storedTranslation().textHorizontalAlignment)
            assertEquals(
                Constants.TOP,
                get().storedBible().verticalAlignment,
                "the vertical alignment is the stack's, not one translation's",
            )
        }
    }

    @Test
    fun `the case picker writes the translation's transform`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            chooseSegment("AA")

            assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, get().storedTranslation().textTransform)
        }
    }

    // ── The reference ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the reference element writes its own size, colour, position and abbreviation`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_REFERENCE)
            retypeNumberField(37, 42)
            recolor("#445566", "#667788")
            positionButton(group = 0, above = true).performScrollTo().performClick()
            waitForIdle()
            toggleCheckbox("Abbreviation")

            val stored = get().storedTranslation()
            assertEquals(42, stored.referenceFontSize)
            assertEquals("#667788", stored.referenceColor)
            assertEquals(REFERENCE_ABOVE, stored.referencePosition)
            assertEquals(
                !BibleTranslationSettings().showAbbreviation,
                stored.showAbbreviation,
                "the abbreviation box must have flipped",
            )
            assertEquals(38, stored.lowerThirdReferenceFontSize, "the band's reference must be untouched")
        }
    }

    @Test
    fun `the reference element writes the band's own size and position`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_REFERENCE)
            retypeNumberField(38, 24)
            positionButton(group = 0, above = true).performScrollTo().performClick()
            waitForIdle()

            val stored = get().storedTranslation()
            assertEquals(24, stored.lowerThirdReferenceFontSize)
            assertEquals(REFERENCE_ABOVE, stored.lowerThirdReferencePosition)
            assertEquals(37, stored.referenceFontSize, "the full screen's reference must be untouched")
        }
    }

    // ── The strip under the preview ─────────────────────────────────────────────────────────────

    @Test
    fun `the strip writes all four margins`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 12)
            retypeNumberField(22, 23)
            retypeNumberField(33, 34)
            retypeNumberField(44, 45)

            val stored = get().storedBible()
            assertEquals(
                listOf(12, 23, 34, 45),
                listOf(stored.marginTop, stored.marginBottom, stored.marginLeft, stored.marginRight),
            )
        }
    }

    @Test
    fun `the strip writes the fades, their duration and the divider`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Fade In", scroll = false)
            toggleCheckbox("Fade Out", scroll = false)
            toggleCheckbox("Crossfade", scroll = false)
            retypeNumberField(555, 620)
            toggleCheckbox("Show divider between translations", scroll = false)
            retypeNumberField(17, 19)

            val stored = get().storedBible()
            assertFalse(stored.fadeIn, "Fade In was on and must have gone off")
            assertFalse(stored.fadeOut)
            assertTrue(stored.crossfade, "Crossfade was off and must have come on")
            assertEquals(620f, stored.transitionDuration)
            assertTrue(stored.multiTranslationDivider)
            assertEquals(19, stored.multiTranslationSpacing)
        }
    }

    @Test
    fun `a band's strip writes its height`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(29, 40)

            assertEquals(40, get().storedBible().lowerThirdHeightPercent)
        }
    }
}
