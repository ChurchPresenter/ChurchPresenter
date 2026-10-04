@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleTabDeleteZoneDragTest {

    private fun ComposeUiTest.gripOf(cardIndex: Int): Offset {
        val card = onAllNodesWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNodes()[cardIndex].boundsInRoot
        return Offset(card.left + 13f, card.center.y)
    }

    private fun ComposeUiTest.cardHeight(): Float =
        onAllNodesWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNodes()[0].boundsInRoot.height

    private fun ComposeUiTest.rootHeight(): Float = onRoot().fetchSemanticsNode().boundsInRoot.height

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
    fun `dragging a row up by its grip moves it above its neighbour`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
            addSong(songNumber = 3, title = "Third Song", songbook = "Hymnal")
        }) { vm, _ ->
            dragRow(from = 2, dy = -(cardHeight() + 3f) * 1.5f)

            assertEquals(
                listOf("1 - First Song", "3 - Third Song", "2 - Second Song"),
                vm.scheduleItems.map { it.displayText },
            )
        }

    @Test
    fun `dropping a row on the delete zone removes it`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
        }) { vm, _ ->
            dragRow(from = 0, dy = rootHeight())

            assertEquals(listOf("2 - Second Song"), vm.scheduleItems.map { it.displayText })
        }

    @Test
    fun `deleting the selected row by dragging clears the selection`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
        }) { vm, _ ->
            vm.selectItem(vm.scheduleItems[0].id)
            waitForIdle()

            dragRow(from = 0, dy = rootHeight())

            assertEquals(1, vm.scheduleItems.size)
            assertNull(vm.selectedItemId)
        }

    @Test
    fun `deleting an unselected row by dragging keeps the other selection`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
        }) { vm, _ ->
            val keep = vm.scheduleItems[1].id
            vm.selectItem(keep)
            waitForIdle()

            dragRow(from = 0, dy = rootHeight())

            assertEquals(keep, vm.selectedItemId)
        }
}
