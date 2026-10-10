package org.churchpresenter.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CanvasTabPanelDragTest {

    private fun ComposeUiTest.selectToolLeft(): Float = onNodeWithText("◆").fetchSemanticsNode().boundsInRoot.left

    private fun ComposeUiTest.dragAcross(fromX: Float, byX: Float) {
        onRoot().performMouseInput {
            moveTo(Offset(fromX, 300f))
            press()
            moveTo(Offset(fromX + byX / 2f, 300f))
            moveTo(Offset(fromX + byX, 300f))
            release()
        }
        waitForIdle()
    }

    @Test
    fun `dragging the left separator widens the scene list and saves the width`() =
        canvasTab(seed = { addScene("Main") }, width = 1200.dp) { _, reports ->
            val before = selectToolLeft()

            dragAcross(fromX = 204f, byX = 60f)

            assertTrue(selectToolLeft() - before > 40f, "the canvas moved right with the separator")
            assertEquals(1, reports.settingsChanges, "the new width is saved once, when the drag ends")
        }

    @Test
    fun `the scene list cannot be dragged narrower than its minimum`() =
        canvasTab(seed = { addScene("Main") }, width = 1200.dp) { _, reports ->
            val before = selectToolLeft()

            dragAcross(fromX = 204f, byX = -180f)

            val after = selectToolLeft()
            assertTrue(before - after in 60f..90f, "it stops at 120dp, from $before to $after")
            assertEquals(1, reports.settingsChanges)
        }

    @Test
    fun `dragging the right separator saves the properties panel width`() =
        canvasTab(seed = { addScene("Main") }, width = 1200.dp) { _, reports ->
            val tabWidth = onRoot().fetchSemanticsNode().size.width.toFloat()
            dragAcross(fromX = tabWidth - 204f, byX = -60f)

            assertEquals(1, reports.settingsChanges)
        }
}
