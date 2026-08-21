@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorStyleZone
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorZoneStyle
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Asserts the style and alignment buttons **look** different when selected.
 *
 * Every other test on this tab stops at the settings object, and for these particular controls that
 * is only half the claim. `TextStyleButtons`, `VerticalAlignmentButtons` and
 * `HorizontalAlignmentButtons` publish no `ToggleableState` and no `Selected` — a selected bold
 * button carries exactly the same semantics as an unselected one. Selection exists purely as paint,
 * so a regression that stopped drawing it would leave all sixty of those tests passing and the
 * operator unable to tell what is switched on.
 *
 * Each test compares the **same button in two fixtures** — the setting on, and the setting off — and
 * requires the pixels to differ. Comparing fixtures rather than before-and-after a click is what
 * keeps this honest: a clicked button also takes focus and press indication, so its own pixels
 * change even when the click re-selects what was already selected, which is the vacuous comparison
 * this deliberately avoids (the Song tab's `selectAndAssertGroupRepaint` records the same trap).
 *
 * Chained with the behaviour tests, which prove a click writes the setting, this closes the loop:
 * click → setting → paint.
 */
class StageMonitorSettingsTabRenderingTest {

    private val zone = StageMonitorStyleZone.A
    private val ordinal = ZoneOrdinal.of(zone)

    /** The pixels [locate] paints when the zone's style is [style]. */
    private fun pixelsWith(
        style: StageMonitorZoneStyle.() -> StageMonitorZoneStyle,
        locate: ComposeUiTest.() -> SemanticsNodeInteraction,
    ): IntArray {
        lateinit var pixels: IntArray
        stageMonitorTab(initial = zoneStyled(zone, style)) { _ ->
            pixels = locate().performScrollTo().renderedPixels()
        }
        return pixels
    }

    private fun assertPaintsDifferently(
        what: String,
        on: StageMonitorZoneStyle.() -> StageMonitorZoneStyle,
        off: StageMonitorZoneStyle.() -> StageMonitorZoneStyle,
        locate: ComposeUiTest.() -> SemanticsNodeInteraction,
    ) {
        assertFalse(
            pixelsWith(on, locate).contentEquals(pixelsWith(off, locate)),
            "$what must be painted differently when it is selected — it publishes no state to say so",
        )
    }

    // ── Style buttons ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the bold button is painted differently when the zone is bold`() = assertPaintsDifferently(
        what = "B",
        on = { copy(bold = true) },
        off = { copy(bold = false) },
        locate = { styleButton(ordinal, "B") },
    )

    @Test
    fun `the italic button is painted differently when the zone is italic`() = assertPaintsDifferently(
        what = "I",
        on = { copy(italic = true) },
        off = { copy(italic = false) },
        locate = { styleButton(ordinal, "I") },
    )

    @Test
    fun `the underline button is painted differently when the zone is underlined`() = assertPaintsDifferently(
        what = "U",
        on = { copy(underline = true) },
        off = { copy(underline = false) },
        locate = { styleButton(ordinal, "U") },
    )

    @Test
    fun `the shadow button is painted differently when the zone has a shadow`() = assertPaintsDifferently(
        what = "S",
        on = { copy(shadow = true) },
        off = { copy(shadow = false) },
        locate = { styleButton(ordinal, "S") },
    )

    // ── Alignment buttons ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the vertical alignment button is painted differently when it is the chosen one`() =
        assertPaintsDifferently(
            what = "Align Bottom",
            on = { copy(verticalAlignment = Constants.BOTTOM) },
            off = { copy(verticalAlignment = Constants.TOP) },
            locate = { verticalAlignButton(ordinal, VAlign.BOTTOM) },
        )

    @Test
    fun `the horizontal alignment button is painted differently when it is the chosen one`() =
        assertPaintsDifferently(
            what = "the right-align button",
            on = { copy(horizontalAlignment = Constants.RIGHT) },
            off = { copy(horizontalAlignment = Constants.LEFT) },
            locate = { horizontalAlignButton(ordinal, HAlign.RIGHT) },
        )

    // ── The whole loop, in one test ─────────────────────────────────────────────────────────────

    /**
     * Click → setting → paint, end to end, without the press-indication trap: the button clicked is
     * *not* the one whose pixels are compared. Selecting Middle must repaint the Top button, which
     * held the selection and now loses it, while nothing is clicked on Top itself.
     */
    @Test
    fun `choosing a different alignment repaints the button that held the selection`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(verticalAlignment = Constants.TOP) }) { get ->
            val topBefore = verticalAlignButton(ordinal, VAlign.TOP).performScrollTo().renderedPixels()

            verticalAlignButton(ordinal, VAlign.MIDDLE).performClick()
            waitForIdle()

            assertEquals(
                Constants.MIDDLE,
                get().styleOf(zone).verticalAlignment,
                "the click must reach the settings",
            )
            assertFalse(
                verticalAlignButton(ordinal, VAlign.TOP).renderedPixels().contentEquals(topBefore),
                "and the button that lost the selection must stop being painted as selected",
            )
        }
    }

    /**
     * The counterpart: a button in the same group that was unselected before and after must not
     * move at all. Without this the test above would also pass if the whole row simply repainted
     * on any interaction, which would say nothing about selection being drawn.
     */
    @Test
    fun `a button that stays unselected does not move when another is chosen`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(verticalAlignment = Constants.TOP) }) { _ ->
            val bottomBefore = verticalAlignButton(ordinal, VAlign.BOTTOM).performScrollTo().renderedPixels()

            verticalAlignButton(ordinal, VAlign.MIDDLE).performClick()
            waitForIdle()

            assertTrue(
                verticalAlignButton(ordinal, VAlign.BOTTOM).renderedPixels().contentEquals(bottomBefore),
                "a button unselected throughout must be painted identically",
            )
        }
    }

    /** Selection is drawn per zone: styling one zone must not repaint another's buttons. */
    @Test
    fun `selecting in one zone does not repaint another zone's buttons`() {
        stageMonitorTab { _ ->
            val otherOrdinal = ZoneOrdinal.of(StageMonitorStyleZone.E)
            val otherBefore = styleButton(otherOrdinal, "B").performScrollTo().renderedPixels()

            styleButton(ordinal, "B").performScrollTo().performClick()
            waitForIdle()

            assertTrue(
                styleButton(otherOrdinal, "B").performScrollTo().renderedPixels().contentEquals(otherBefore),
                "Bottom-Right's bold button must be untouched by a Top-Left edit",
            )
        }
    }
}
