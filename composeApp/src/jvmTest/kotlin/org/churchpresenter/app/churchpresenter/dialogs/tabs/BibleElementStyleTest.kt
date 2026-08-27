package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The lens the settings tab edits through: one control set standing for four stored profiles.
 *
 * Every combination has to round-trip, because a control writes the whole [BibleElementStyle] back
 * and a field the writer forgot would be silently reset by any edit to its neighbour.
 */
class BibleElementStyleTest {

    private val combinations = listOf(
        BibleStyleElement.TEXT to BibleStyleTarget.FULL_SCREEN,
        BibleStyleElement.TEXT to BibleStyleTarget.LOWER_THIRD,
        BibleStyleElement.REFERENCE to BibleStyleTarget.FULL_SCREEN,
        BibleStyleElement.REFERENCE to BibleStyleTarget.LOWER_THIRD,
    )

    /** Every field set away from its default, so a writer that drops one is caught. */
    private fun distinct(seed: Int) = BibleElementStyle(
        color = "#11223$seed",
        fontType = "Georgia$seed",
        fontSize = 40 + seed,
        bold = true,
        italic = true,
        underline = true,
        strikethrough = true,
        shadow = true,
        shadowColor = "#99887$seed",
        shadowSize = 50 + seed,
        shadowOpacity = 60 + seed,
        horizontalAlignment = Constants.CENTER,
        position = Constants.POSITION_ABOVE,
        letterSpacing = 3 + seed,
        wordSpacing = 5 + seed,
        transform = Constants.TEXT_TRANSFORM_UPPERCASE,
    )

    @Test
    fun `every combination round-trips every field`() {
        combinations.forEachIndexed { index, (element, target) ->
            val style = distinct(index)
            val written = BibleTranslationSettings().withElementStyle(element, target, style)

            val expected = if (element == BibleStyleElement.TEXT) {
                // The verse has nowhere to store a position: only the reference moves.
                style.copy(position = Constants.POSITION_BELOW)
            } else {
                style
            }
            assertEquals(expected, written.elementStyle(element, target), "$element on $target")
        }
    }

    @Test
    fun `writing one combination leaves the other three alone`() {
        combinations.forEachIndexed { index, (element, target) ->
            val written = BibleTranslationSettings().withElementStyle(element, target, distinct(index))

            combinations.filterNot { it.first == element && it.second == target }.forEach { (other, otherTarget) ->
                assertEquals(
                    defaultElementStyle(other, otherTarget),
                    written.elementStyle(other, otherTarget),
                    "writing $element/$target must not touch $other/$otherTarget",
                )
            }
        }
    }

    @Test
    fun `the defaults are read from the stored defaults rather than restated`() {
        // The lower third carries smaller font sizes of its own, which is what makes this worth
        // asserting: a hand-written default would have drifted from the stored one.
        assertEquals(
            BibleTranslationSettings().lowerThirdTextFontSize,
            defaultElementStyle(BibleStyleElement.TEXT, BibleStyleTarget.LOWER_THIRD).fontSize,
        )
        assertNotEquals(
            defaultElementStyle(BibleStyleElement.TEXT, BibleStyleTarget.FULL_SCREEN).fontSize,
            defaultElementStyle(BibleStyleElement.TEXT, BibleStyleTarget.LOWER_THIRD).fontSize,
        )
    }

    @Test
    fun `the reference defaults to the right, the verse to the left`() {
        assertEquals(
            Constants.LEFT,
            defaultElementStyle(BibleStyleElement.TEXT, BibleStyleTarget.FULL_SCREEN).horizontalAlignment,
        )
        assertEquals(
            Constants.RIGHT,
            defaultElementStyle(BibleStyleElement.REFERENCE, BibleStyleTarget.FULL_SCREEN).horizontalAlignment,
        )
    }

    @Test
    fun `the preview follows the assigned display rather than assuming 16 by 9`() {
        val ultrawide = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(ScreenAssignment(targetBoundsW = 2560, targetBoundsH = 1080)),
            ),
        )

        assertEquals(PreviewOutputSize(2560, 1080), previewOutputSize(ultrawide))
    }

    @Test
    fun `a display that has not reported its bounds falls back to the stored basis`() {
        val unreported = AppSettings(
            projectionSettings = ProjectionSettings(screenAssignments = listOf(ScreenAssignment())),
        )

        assertEquals(PreviewOutputSize(STYLING_BASIS_WIDTH, STYLING_BASIS_HEIGHT), previewOutputSize(unreported))
    }
}
