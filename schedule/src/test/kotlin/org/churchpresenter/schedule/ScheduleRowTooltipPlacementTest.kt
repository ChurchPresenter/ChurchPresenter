@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a row button's tooltip opens (#1042).
 *
 * The hover overlay sits at the row's right-hand end, so its tooltips open to the button's left.
 * The legacy line starts with Remove at the panel's left edge, where a tooltip to the left has no
 * room: it was pushed back over the button and took the click meant for it. So that line's
 * tooltips open below.
 */
class ScheduleRowTooltipPlacementTest {

    /** Hovers [button], lets the tooltip's delay pass, and returns the button's and tooltip's bounds. */
    private fun ComposeUiTest.hoverForTooltip(label: String, index: Int): Pair<Rect, Rect> {
        val button = buttonAt(label, index)
        button.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()
        val tooltip = onAllNodesWithText(label).fetchSemanticsNodes()
        assertEquals(1, tooltip.size, "one tooltip names the hovered button")
        return button.fetchSemanticsNode().boundsInWindow to tooltip.single().boundsInWindow
    }

    @Test
    fun `the legacy line's Remove tooltip opens below the button, clear of it`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { _, _ ->
            val (button, tooltip) = hoverForTooltip(ScheduleLabel.REMOVE, 0)
            assertTrue(tooltip.top >= button.bottom, "tooltip $tooltip starts under button $button")
        }

    @Test
    fun `the legacy line's Remove still removes while its tooltip shows`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { vm, _ ->
            val before = vm.scheduleItems.map { it.id }
            hoverForTooltip(ScheduleLabel.REMOVE, 0)
            buttonAt(ScheduleLabel.REMOVE, 0).performClick()
            waitForIdle()

            assertEquals(before.drop(1), vm.scheduleItems.map { it.id }, "the first card is gone")
        }

    @Test
    fun `the hover overlay's tooltips still open to the left`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            val (button, tooltip) = hoverForTooltip(ScheduleLabel.GO_LIVE, 0)
            assertTrue(tooltip.right <= button.left, "tooltip $tooltip ends left of button $button")
        }

    private companion object {
        /** Past `TooltipArea`'s default 500 ms delay; the clock is the test's, so it costs nothing. */
        const val TOOLTIP_WAIT_MS = 1_000L
    }
}
