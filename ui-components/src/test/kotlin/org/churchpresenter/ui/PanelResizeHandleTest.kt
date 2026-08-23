@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The draggable strip between a side panel and the content beside it.
 *
 * There are two of these — the schedule panel on the left and the preview on the right — and until
 * this suite neither had a test: both were inline in `MainDesktop`, which only runs under a real
 * display and is excluded from the coverage gate. They are also the app's most-used drag handles,
 * and the place a drag-handle regression has already happened: a `pointerInput` keyed on the width
 * the gesture itself rewrites is torn down and relaunched at the end of every drag, so **the first
 * drag works and the rest do not**. A single-drag test cannot see that, so the case below drags
 * three times and is the reason this suite exists.
 *
 * The gesture is asserted through the callbacks rather than a rendered width: the caller owns the
 * width (it clamps and persists it), so what this component is responsible for is reporting the
 * drag at all, once per gesture, and only while expanded.
 */
class PanelResizeHandleTest {

    private companion object {
        const val HANDLE = "panel_resize_handle"
        const val TOGGLE = "collapse"
    }

    private class Reports {
        val drags = mutableListOf<Float>()
        var ends = 0
        var toggles = 0
        fun travelled(): Float = drags.sum()
    }

    /**
     * Composes the handle the way `MainDesktop` uses it: a width the drag rewrites, and which the
     * end of the gesture persists.
     *
     * That state is the whole point of the harness. Held in a `mutableStateOf` that the callbacks
     * write, every drag forces a recomposition — which is the only condition under which a
     * `pointerInput` key that includes the width can churn and kill the gesture. Without it the
     * repeat-drag test below passes even against a handle keyed on something that changes every
     * composition, and it was written that way first.
     */
    private fun ComposeUiTest.handle(collapsed: Boolean = false): Reports {
        val reports = Reports()
        setContent {
            MaterialTheme {
                var widthPx by remember { mutableStateOf(120f) }
                var savedPx by remember { mutableStateOf(120f) }
                Box(modifier = Modifier.size(200.dp)) {
                    PanelResizeHandle(
                        collapsed = collapsed,
                        onResize = { amount ->
                            reports.drags += amount
                            widthPx = resizedPanelWidth(widthPx, amount, invert = false, minPx = 50f, maxPx = 300f)
                        },
                        onResizeEnd = { reports.ends++; savedPx = widthPx },
                        onToggleCollapsed = { reports.toggles++ },
                        icon = ColorPainter(Color.Gray),
                        contentDescription = TOGGLE,
                        modifier = Modifier.testTag(HANDLE),
                    )
                }
            }
        }
        return reports
    }

    /** One drag across the handle, [dx] pixels, in eight steps. */
    private fun ComposeUiTest.drag(dx: Float) {
        val bounds = onNodeWithTag(HANDLE).fetchSemanticsNode().boundsInRoot
        val start = Offset(bounds.center.x, bounds.top + 30f)
        onNodeWithTag(HANDLE).performMouseInput {
            moveTo(start)
            press()
            repeat(8) { step -> moveTo(Offset(start.x + dx * (step + 1) / 8f, start.y)) }
            release()
        }
        waitForIdle()
    }

    @Test
    fun `dragging the handle reports the distance travelled`() = runComposeUiTest {
        val reports = handle()

        drag(dx = 40f)

        assertTrue(reports.drags.isNotEmpty(), "the drag must be reported at all")
        assertTrue(
            abs(reports.travelled() - 40f) <= 8f,
            "the reported amounts must add up to the distance dragged, was ${reports.travelled()}",
        )
        assertEquals(1, reports.ends, "and the end of the gesture fires once, where the width is saved")
    }

    @Test
    fun `dragging left reports negative amounts`() = runComposeUiTest {
        // Sign is load-bearing: the right-hand panel inverts it, so a handle that reported
        // magnitude only would widen the preview when it should narrow it.
        val reports = handle()

        drag(dx = -40f)

        assertTrue(reports.travelled() < 0f, "leftward travel must be negative, was ${reports.travelled()}")
    }

    @Test
    fun `the handle keeps working on the second and third drag`() = runComposeUiTest {
        // The regression this suite exists for. Each drag is asserted on its own, because the
        // failure is not "no drags" but "only the first one".
        val reports = handle()

        drag(dx = 30f)
        val first = reports.drags.size
        assertTrue(first > 0, "first drag reported nothing")

        drag(dx = 30f)
        val second = reports.drags.size - first
        assertTrue(second > 0, "second drag reported nothing -- the handle stopped after one gesture")

        drag(dx = -30f)
        val third = reports.drags.size - first - second
        assertTrue(third > 0, "third drag reported nothing")

        assertEquals(3, reports.ends, "each gesture must end exactly once")
    }

    @Test
    fun `a collapsed handle does not resize`() = runComposeUiTest {
        // Collapsed, the panel has no width to drag; the strip is only a place to click the
        // expand button, and a drag on it must not report anything.
        val reports = handle(collapsed = true)

        drag(dx = 40f)

        assertTrue(reports.drags.isEmpty(), "a collapsed panel must not resize, got ${reports.drags}")
        assertEquals(0, reports.ends)
    }

    @Test
    fun `the collapse button is reachable whether the panel is open or shut`() {
        listOf(false to "expanded", true to "collapsed").forEach { (collapsed, name) ->
            runComposeUiTest {
                // Collapsed is the state that matters: if the button were inside the
                // `if (!collapsed)` block with the grip dots, a collapsed panel could never be
                // reopened.
                val reports = handle(collapsed = collapsed)

                onNodeWithContentDescription(TOGGLE).performClick()

                assertEquals(1, reports.toggles, "$name: the toggle must fire")
            }
        }
    }

    // ── The width arithmetic the callers apply ──────────────────────────────────

    @Test
    fun `a left-hand panel grows as the handle moves right`() {
        assertEquals(140f, resizedPanelWidth(100f, dragAmount = 40f, invert = false, minPx = 50f, maxPx = 300f))
        assertEquals(60f, resizedPanelWidth(100f, dragAmount = -40f, invert = false, minPx = 50f, maxPx = 300f))
    }

    @Test
    fun `a right-hand panel grows as the handle moves left`() {
        assertEquals(140f, resizedPanelWidth(100f, dragAmount = -40f, invert = true, minPx = 50f, maxPx = 300f))
        assertEquals(60f, resizedPanelWidth(100f, dragAmount = 40f, invert = true, minPx = 50f, maxPx = 300f))
    }

    @Test
    fun `the width stays between its minimum and the window's cap`() {
        assertEquals(50f, resizedPanelWidth(100f, dragAmount = -500f, invert = false, minPx = 50f, maxPx = 300f))
        assertEquals(300f, resizedPanelWidth(100f, dragAmount = 500f, invert = false, minPx = 50f, maxPx = 300f))
    }

    @Test
    fun `a window too narrow for the minimum clamps to the cap instead of throwing`() {
        // `coerceIn` throws on a reversed range, and the cap really can fall below the minimum:
        // it is derived from the window width, which the user can drag smaller than the panel's
        // own floor.
        assertEquals(40f, resizedPanelWidth(100f, dragAmount = 500f, invert = false, minPx = 150f, maxPx = 40f))
        assertEquals(40f, resizedPanelWidth(100f, dragAmount = -500f, invert = false, minPx = 150f, maxPx = 40f))
    }
}
