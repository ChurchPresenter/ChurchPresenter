@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the schedule toolbar behaves as the panel narrows.
 *
 * The panel is resizable and lives beside the tabs, so it is routinely dragged narrow mid-service.
 * The toolbar's buttons used to sit in one fixed `Row` inside a pill: a `FlowRow` breaks between its
 * items and never inside one, so the pill simply overflowed and clipped its own tail — the buttons
 * past the edge were unreachable, with nothing to say they existed.
 *
 * What has to hold, in order of how it fails:
 *
 *  * **Nothing moves while it fits.** A toolbar that reflows early costs the operator the muscle
 *    memory of where each icon is.
 *  * **Then one icon at a time**, into the same pill rather than a second one — it stays one group.
 *  * **Undo and Redo never split.** They read as one control; Redo alone at the start of a line
 *    looks like a different thing.
 *
 * Rows are compared by `top`, not by exact pixels: the assertion is which line a button landed on,
 * which is what an operator sees, and it survives the font metrics differing across platforms.
 */
class ScheduleTabToolbarWrapTest {

    private val allButtons = listOf(
        ScheduleLabel.NEW, "Open Schedule", "Save Schedule", ScheduleLabel.CLEAR,
        ScheduleLabel.UNDO, ScheduleLabel.REDO, ScheduleLabel.ADD_LABEL,
    )

    // Undo/Redo are addressed by test tag rather than tooltip text — their tooltips name the live
    // keyboard binding, which renders as "Ctrl+Z" on Windows/Linux and "⌃Z" on macOS.
    private val taggedButtons = setOf(ScheduleLabel.UNDO, ScheduleLabel.REDO)

    private fun ComposeUiTest.rowOf(label: String): Float =
        (if (label in taggedButtons) onNodeWithTag(label) else onNodeWithContentDescription(label))
            .fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `a wide panel keeps the whole toolbar on one line`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            val rows = allButtons.map { rowOf(it) }.distinct()

            assertEquals(1, rows.size, "nothing may wrap while it fits: buttons landed on $rows")
        }

    @Test
    fun `a narrow panel moves one icon at a time onto a second line`() =
        scheduleTab(width = 200.dp, seed = { seedService() }) { _, _ ->
            val rows = allButtons.map { rowOf(it) }

            assertEquals(2, rows.distinct().size, "it must wrap rather than clip: $rows")
            assertEquals(
                1, rows.count { it == rows.max() },
                "only the button that no longer fits moves down, not a whole group: $rows",
            )
        }

    @Test
    fun `a panel narrow enough to wrap still settles on two lines`() =
        scheduleTab(width = 150.dp, seed = { seedService() }) { _, _ ->
            // Clear sits with New/Open/Save rather than at the tail, which is what makes this fit
            // in two: four file-level icons on the first line, four editing ones on the second.
            // Ordered the old way, 150dp needed three.
            assertEquals(2, allButtons.map { rowOf(it) }.distinct().size, "two lines, not three")
        }

    @Test
    fun `undo and redo stay on the same line however narrow it gets`() =
        scheduleTab(width = 130.dp, seed = { seedService() }) { _, _ ->
            // 130dp is past what two lines can hold, which is where the pair would otherwise be
            // split by the break.
            assertTrue(allButtons.map { rowOf(it) }.distinct().size >= 3, "fixture must actually be cramped")

            assertEquals(rowOf(ScheduleLabel.UNDO), rowOf(ScheduleLabel.REDO), "the pair must not be broken up")
        }
}
