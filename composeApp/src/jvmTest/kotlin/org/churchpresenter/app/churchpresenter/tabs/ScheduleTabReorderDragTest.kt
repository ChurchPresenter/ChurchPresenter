@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dragging a schedule row by its grip to reorder it.
 *
 * This is the gesture behind the fatal `NoClassDefFoundError: DragItemGeometry` of 2026-07-31: the
 * `Move` branch builds a `DragItemGeometry` per visible row to hit-test the drop target, and it was
 * the first line to touch a class the running build's output was missing. The source was never
 * wrong — the class had been on `main` since 25 July — so the crash was a stale compile, not a
 * defect. This test is what makes that distinction cheap to re-check: it runs the same branch in
 * process, so a build that could not load the class fails here rather than under someone's mouse.
 *
 * It is driven through the **grip dots**, not the row body. The body's copy of the gesture takes
 * `requireShift = true` and a test cannot set `keyboardModifiers` on an injected pointer event —
 * the same blocker `PicturesTab`'s reorder is recorded as having. The grip's copy takes
 * `requireShift = false`, which is what makes this reachable at all.
 *
 * The gesture arms only after the pointer has travelled `DRAG_HANDLE_THRESHOLD` (4dp), and it arms
 * on the iteration *after* the move that crosses it, so the drag is injected as several small steps
 * rather than one jump: the first couple arm it, the rest move the cursor far enough to change the
 * drop target. `moveItem` is asserted on the view model, not on the screen, because the row order
 * is what a service actually runs on.
 */
class ScheduleTabReorderDragTest {

    /** The grip sits just inside the card's left edge: 3dp row padding, a 3dp accent bar, 5dp gap. */
    private fun ComposeUiTest.gripOf(cardIndex: Int): Offset {
        val card = onAllNodesWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNodes()[cardIndex].boundsInRoot
        return Offset(card.left + 13f, card.center.y)
    }

    private fun ComposeUiTest.cardHeight(): Float =
        onAllNodesWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNodes()[0].boundsInRoot.height

    /** Presses [from]'s grip and drags [dy] pixels in eight steps, ending with a release. */
    private fun ComposeUiTest.dragRow(from: Int, dy: Float) {
        val start = gripOf(from)
        onRoot().performMouseInput {
            moveTo(start)
            press()
            repeat(8) { step -> moveTo(Offset(start.x, start.y + dy * (step + 1) / 8f)) }
            release()
        }
        waitForIdle()
    }

    @Test
    fun `dragging a row down by its grip moves it past its neighbour`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
            addSong(songNumber = 3, title = "Third Song", songbook = "Hymnal")
        }) { vm, _ ->
            val step = cardHeight() + 3f

            dragRow(from = 0, dy = step * 1.5f)

            assertEquals(
                listOf("2 - Second Song", "1 - First Song", "3 - Third Song"),
                vm.scheduleItems.map { it.displayText },
                "the dragged row must land below the one it was dragged past",
            )
        }

    @Test
    fun `a grip press that goes nowhere leaves the order alone`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
        }) { vm, _ ->
            // Under the 4dp arming threshold: a click on the grip is not a drag, and must not
            // reorder anything.
            dragRow(from = 0, dy = 2f)

            assertEquals(
                listOf("1 - First Song", "2 - Second Song"),
                vm.scheduleItems.map { it.displayText },
                "a press that never armed must not move a row",
            )
        }

    @Test
    fun `the grip keeps working on the second and third drag`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
            addSong(songNumber = 3, title = "Third Song", songbook = "Hymnal")
        }) { vm, _ ->
            // The failure mode this repo has hit before on drag handles: a `pointerInput` keyed on
            // something the gesture itself rewrites tears its coroutine down at the end of every
            // drag, so the first one works and the rest do not (see the schedule splitter's own
            // comment in MainDesktop.kt). One drag can never see it; three can.
            val step = cardHeight() + 3f

            dragRow(from = 0, dy = step * 1.5f)
            assertEquals(
                listOf("2 - Second Song", "1 - First Song", "3 - Third Song"),
                vm.scheduleItems.map { it.displayText },
                "first drag",
            )

            dragRow(from = 0, dy = step * 1.5f)
            assertEquals(
                listOf("1 - First Song", "2 - Second Song", "3 - Third Song"),
                vm.scheduleItems.map { it.displayText },
                "second drag -- the one that used to stop working",
            )

            dragRow(from = 1, dy = step * 1.5f)
            assertEquals(
                listOf("1 - First Song", "3 - Third Song", "2 - Second Song"),
                vm.scheduleItems.map { it.displayText },
                "third drag, from a different row",
            )
        }
}
