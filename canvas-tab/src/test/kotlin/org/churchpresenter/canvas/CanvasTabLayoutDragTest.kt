@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dragging the two panel splitters.
 *
 * The widths are remembered per window state, so the drag is only half of it: what the operator set
 * has to reach the settings when the drag ends, or the panel snaps back the next time the tab is
 * opened. The pure half of that — which of the two layouts a width is written into — is pinned by
 * [CanvasTabPanelResizeTest]; this is the drag that calls it.
 *
 * The splitters carry no label of their own, so they are found by geometry: each is the 6dp strip
 * immediately beside its panel's header.
 *
 * The widths land in the **maximized** layout here, not the windowed one. `LocalMainWindowState` has
 * nothing in it outside the real app window, and the tab reads a missing state as "not floating" —
 * which is right, because the fallback presenter window and the packaged app both run maximized.
 */
class CanvasTabLayoutDragTest {

    private companion object { const val STEPS = 12 }

    /**
     * The middle of a splitter strip.
     *
     * Both panels start at their remembered width, 200dp, and each strip is the 6dp immediately
     * beyond it — so the left one is at 203 from the left edge and the right one 203 from the right.
     * Measured rather than assumed: [leftSplitterX] is checked against the toolbar, which begins on
     * the far side of the strip.
     */
    private fun ComposeUiTest.leftSplitterX(): Float {
        val toolbarStarts = onNodeWithText("\u25C6").fetchSemanticsNode().boundsInRoot.left
        assertTrue(toolbarStarts > 206f, "the toolbar should sit past the splitter, was $toolbarStarts")
        return 203f
    }

    private fun ComposeUiTest.rightSplitterX(): Float =
        onRoot().fetchSemanticsNode().boundsInRoot.right - 203f

    private fun ComposeUiTest.dragHorizontally(x: Float, by: Float) {
        onRoot().performMouseInput {
            moveTo(Offset(x, 200f))
            press()
            // In steps, so the gesture's own slop is spent before the movement that counts.
            for (step in 1..STEPS) moveTo(Offset(x + by * step / STEPS, 200f))
            release()
        }
        waitForIdle()
    }

    @Test
    fun `widening the left panel is remembered`() {
        canvasTab(seed = { addScene("Scene") }) { _, _, reports ->
            val before = reports.settings.maximizedLayout.canvasLeftPanelWidthDp

            dragHorizontally(leftSplitterX(), by = 120f)

            assertTrue(reports.settingsChanges > 0, "the drag must be written back when it ends")
            assertTrue(
                reports.settings.maximizedLayout.canvasLeftPanelWidthDp > before,
                "the panel got wider, so the remembered width must too " +
                    "(${reports.settings.maximizedLayout.canvasLeftPanelWidthDp} vs $before)",
            )
        }
    }

    @Test
    fun `narrowing the left panel stops at its minimum rather than collapsing it`() {
        // A panel dragged to nothing cannot be dragged back — there is no handle left to grab.
        canvasTab(seed = { addScene("Scene") }) { _, _, reports ->
            dragHorizontally(leftSplitterX(), by = -400f)

            assertTrue(
                reports.settings.maximizedLayout.canvasLeftPanelWidthDp >= 120,
                "was ${reports.settings.maximizedLayout.canvasLeftPanelWidthDp}",
            )
        }
    }

    @Test
    fun `a drag that goes nowhere still leaves the width where it was`() {
        canvasTab(seed = { addScene("Scene") }) { _, _, reports ->
            val before = reports.settings.maximizedLayout.canvasLeftPanelWidthDp

            dragHorizontally(leftSplitterX(), by = 0f)

            assertEquals(before, reports.settings.maximizedLayout.canvasLeftPanelWidthDp)
        }
    }

    @Test
    fun `resizing the left panel does not disturb the right one`() {
        canvasTab(seed = { addScene("Scene") }) { _, _, reports ->
            val right = reports.settings.maximizedLayout.canvasRightPanelWidthDp

            dragHorizontally(leftSplitterX(), by = 120f)

            assertEquals(right, reports.settings.maximizedLayout.canvasRightPanelWidthDp)
        }
    }

    @Test
    fun `widening the right panel is remembered`() {
        canvasTab(seed = { addScene("Scene") }) { _, _, reports ->
            val before = reports.settings.maximizedLayout.canvasRightPanelWidthDp

            // Leftwards, because the right panel grows as its splitter moves towards the canvas.
            dragHorizontally(rightSplitterX(), by = -120f)

            assertTrue(
                reports.settings.maximizedLayout.canvasRightPanelWidthDp > before,
                "was ${reports.settings.maximizedLayout.canvasRightPanelWidthDp}, before $before",
            )
        }
    }

    @Test
    fun `narrowing the right panel stops at its minimum too`() {
        canvasTab(seed = { addScene("Scene") }) { _, _, reports ->
            dragHorizontally(rightSplitterX(), by = 400f)

            assertTrue(
                reports.settings.maximizedLayout.canvasRightPanelWidthDp >= 120,
                "was ${reports.settings.maximizedLayout.canvasRightPanelWidthDp}",
            )
        }
    }

    @Test
    fun `resizing the right panel does not disturb the left one`() {
        canvasTab(seed = { addScene("Scene") }) { _, _, reports ->
            val left = reports.settings.maximizedLayout.canvasLeftPanelWidthDp

            dragHorizontally(rightSplitterX(), by = -120f)

            assertEquals(left, reports.settings.maximizedLayout.canvasLeftPanelWidthDp)
        }
    }
}
