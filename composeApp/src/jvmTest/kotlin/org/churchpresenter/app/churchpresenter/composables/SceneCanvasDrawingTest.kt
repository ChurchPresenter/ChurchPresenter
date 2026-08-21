@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drawing a shape onto the canvas by dragging, which is how every shape in a scene is created.
 *
 * The gesture turns a drag in screen pixels into a `ShapeSource` in normalised 0..1 canvas
 * coordinates, and the arithmetic differs per tool: a rectangle or ellipse takes the drag's bounding
 * box, a line or arrow additionally stores its two endpoints *relative to* that box, and freehand
 * stores every sampled point the same way. A shape whose transform or points are wrong lands in the
 * wrong place on the audience screen, or collapses to nothing.
 *
 * Everything here is asserted as an invariant rather than an exact pixel: the drag positions are
 * fixed but the canvas measures to whatever the layout gives it, and the normalisation divides by
 * that. What is pinned is the shape's identity, the ordering and containment of its bounds, and the
 * relationships between its points — all of which hold at any canvas size.
 *
 * A backwards drag (right-to-left, bottom-to-top) is covered deliberately: the code takes `minOf`
 * and `abs` throughout precisely so a shape dragged in any direction still has a positive extent,
 * and that is easy to regress.
 */
class SceneCanvasDrawingTest {

    private val canvasTag = "scene-canvas"

    /** How far a drag is nudged to get past touch slop before its real move — see [dragOnCanvas]. */
    private val ARM_PX = 8f

    /** Slack for assertions comparing two drags: the arming nudge on each, over the 400dp canvas. */
    private val ARM_TOLERANCE = 2f * ARM_PX / 400f

    /** A 16:9 scene with nothing on it, so the only pointer target is the drawing surface. */
    private fun emptyScene() = Scene(
        id = "scene-1",
        name = "Scene",
        canvasWidth = 1920,
        canvasHeight = 1080,
        sources = emptyList(),
    )

    /**
     * Composes the canvas with [tool] active and runs [block], returning whatever shapes the drag
     * reported. The width is fixed so the canvas is laid out; its height follows the aspect ratio.
     */
    private fun draw(
        tool: String,
        strokeColor: String = "#FFFFFF",
        fillColor: String = "#00000000",
        strokeWidth: Float = 3f,
        block: ComposeUiTest.() -> Unit,
    ): List<SceneSource.ShapeSource> {
        val drawn = mutableListOf<SceneSource.ShapeSource>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.width(400.dp)) {
                        SceneCanvas(
                            modifier = Modifier.testTag(canvasTag),
                            scene = emptyScene(),
                            selectedSourceId = null,
                            onSourceSelected = { },
                            onTransformChanged = { _, _ -> },
                            activeTool = tool,
                            drawingStrokeColor = strokeColor,
                            drawingFillColor = fillColor,
                            drawingStrokeWidth = strokeWidth,
                            onShapeDrawn = { drawn += it },
                        )
                    }
                }
            }
            block()
        }
        return drawn
    }

    /**
     * Drags from [from] to [to].
     *
     * The small step away from [from] before the real move is load-bearing. `detectDragGestures`
     * reports `onDragStart` at the position where touch slop was first exceeded, **not** at the
     * press — so whatever point the drag is armed at becomes the shape's recorded origin. Arming a
     * few pixels from [from] keeps that origin next to the press; moving straight to a far-away
     * point instead would record the drag as starting there.
     */
    private fun ComposeUiTest.dragOnCanvas(from: Offset, to: Offset) {
        val arm = Offset(
            from.x + ARM_PX * (if (to.x >= from.x) 1f else -1f),
            from.y + ARM_PX * (if (to.y >= from.y) 1f else -1f),
        )
        onNodeWithTag(canvasTag).performMouseInput {
            // press() takes a button, not a position: the pointer is moved first, then pressed.
            moveTo(from)
            press()
            moveTo(arm)
            moveTo(to)
            release()
        }
        waitForIdle()
    }

    private fun assertNormalised(shape: SceneSource.ShapeSource) {
        val t = shape.transform
        assertTrue(t.x >= 0f && t.x <= 1f, "x must be normalised, was ${t.x}")
        assertTrue(t.y >= 0f && t.y <= 1f, "y must be normalised, was ${t.y}")
        assertTrue(t.width > 0f, "a drawn shape must have a positive width, was ${t.width}")
        assertTrue(t.height > 0f, "a drawn shape must have a positive height, was ${t.height}")
    }

    // ── Rectangle and ellipse: the bounding-box branch ──────────────────────────

    @Test
    fun `dragging with the rectangle tool reports one rectangle covering the drag`() {
        val drawn = draw("rectangle") {
            dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f))
        }

        val shape = drawn.singleOrNull()
        assertNotNull(shape, "one drag must produce exactly one shape, got ${drawn.size}")
        assertEquals("rectangle", shape.shapeType)
        assertNormalised(shape)
        // The bounding-box branch keeps no explicit points; the transform is the whole description.
        assertTrue(shape.points.isEmpty(), "a rectangle needs no path points")
    }

    @Test
    fun `the ellipse tool reports its own type rather than a rectangle`() {
        val drawn = draw("ellipse") {
            dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f))
        }

        assertEquals("ellipse", drawn.single().shapeType, "the active tool must name the shape")
    }

    @Test
    fun `a shape is named after the tool that drew it, capitalised`() {
        val drawn = draw("rectangle") {
            dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f))
        }

        // The name is what the operator sees in the source list, so it must be legible.
        assertEquals("Rectangle", drawn.single().name)
    }

    @Test
    fun `the drawing colours and stroke width are carried onto the shape`() {
        val drawn = draw("rectangle", strokeColor = "#FF0000", fillColor = "#8000FF00", strokeWidth = 7.5f) {
            dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f))
        }

        val shape = drawn.single()
        assertEquals("#FF0000", shape.strokeColor)
        assertEquals("#8000FF00", shape.fillColor)
        assertEquals(7.5f, shape.strokeWidth)
    }

    @Test
    fun `dragging backwards still produces a positive-sized shape`() {
        val forwards = draw("rectangle") { dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f)) }.single()
        val backwards = draw("rectangle") { dragOnCanvas(Offset(160f, 120f), Offset(40f, 30f)) }.single()

        assertNormalised(backwards)
        // Both drags describe the same rectangle, so they must agree on it.
        assertTrue(
            abs(forwards.transform.x - backwards.transform.x) <= ARM_TOLERANCE,
            "same left edge: ${forwards.transform.x} vs ${backwards.transform.x}",
        )
        assertTrue(
            abs(forwards.transform.y - backwards.transform.y) <= ARM_TOLERANCE,
            "same top edge: ${forwards.transform.y} vs ${backwards.transform.y}",
        )
        assertTrue(
            abs(forwards.transform.width - backwards.transform.width) <= ARM_TOLERANCE,
            "same width: ${forwards.transform.width} vs ${backwards.transform.width}",
        )
        assertTrue(
            abs(forwards.transform.height - backwards.transform.height) <= ARM_TOLERANCE,
            "same height: ${forwards.transform.height} vs ${backwards.transform.height}",
        )
    }

    @Test
    fun `a drag that barely moves still has a usable minimum size`() {
        val drawn = draw("rectangle") {
            dragOnCanvas(Offset(80f, 80f), Offset(81f, 81f))
        }

        val t = drawn.single().transform
        // Without the 0.01 floor a mis-click would create a shape too small to select or resize.
        assertTrue(t.width >= 0.01f, "width must not collapse, was ${t.width}")
        assertTrue(t.height >= 0.01f, "height must not collapse, was ${t.height}")
    }

    // ── Line and arrow: endpoints stored relative to the bounding box ───────────

    @Test
    fun `a line stores its two endpoints inside its own bounding box`() {
        val drawn = draw("line") {
            dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f))
        }

        val shape = drawn.single()
        assertEquals("line", shape.shapeType)
        assertNormalised(shape)
        assertEquals(2, shape.points.size, "a line is exactly its two ends")
        // Points are relative to the bounding box the transform describes, so they span it corner
        // to corner: this drag went down-right, so it runs (0,0) -> (1,1).
        shape.points.forEach { p ->
            assertTrue(p.x in -0.001f..1.001f, "point x must sit inside the box, was ${p.x}")
            assertTrue(p.y in -0.001f..1.001f, "point y must sit inside the box, was ${p.y}")
        }
        assertTrue(abs(shape.points[0].x - 0f) < 0.05f && abs(shape.points[0].y - 0f) < 0.05f)
        assertTrue(abs(shape.points[1].x - 1f) < 0.05f && abs(shape.points[1].y - 1f) < 0.05f)
    }

    @Test
    fun `a line drawn up-right keeps its direction rather than its bounding box`() {
        val drawn = draw("line") {
            dragOnCanvas(Offset(40f, 120f), Offset(160f, 30f))
        }

        val shape = drawn.single()
        // The box is the same either way; only the points say which way the line actually runs.
        // Bottom-left to top-right means the first point is low and the second is high.
        assertTrue(shape.points[0].y > shape.points[1].y, "the line must still run upwards")
        assertTrue(shape.points[0].x < shape.points[1].x, "and rightwards")
    }

    @Test
    fun `an arrow is stored the same way as a line but keeps its own type`() {
        val drawn = draw("arrow") {
            dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f))
        }

        val shape = drawn.single()
        assertEquals("arrow", shape.shapeType, "an arrow head is drawn from the type, not the points")
        assertEquals(2, shape.points.size)
    }

    // ── Freehand: every sampled point ──────────────────────────────────────────

    @Test
    fun `freehand keeps the whole stroke, normalised into its bounding box`() {
        val drawn = draw("freehand") {
            onNodeWithTag(canvasTag).performMouseInput {
                moveTo(Offset(40f, 40f))
                press()
                moveTo(Offset(80f, 60f))
                moveTo(Offset(120f, 40f))
                moveTo(Offset(160f, 100f))
                release()
            }
            waitForIdle()
        }

        val shape = drawn.single()
        assertEquals("freehand", shape.shapeType)
        assertNormalised(shape)
        assertTrue(shape.points.size >= 4, "every sampled point must be kept, got ${shape.points.size}")
        shape.points.forEach { p ->
            assertTrue(p.x in -0.001f..1.001f, "point x must be normalised into the box, was ${p.x}")
            assertTrue(p.y in -0.001f..1.001f, "point y must be normalised into the box, was ${p.y}")
        }
        // Normalisation is relative to the stroke's own extent, so it must touch both edges.
        assertTrue(shape.points.any { it.x < 0.02f } && shape.points.any { it.x > 0.98f })
    }

    @Test
    fun `a second freehand stroke does not inherit the first one's points`() {
        val drawn = draw("freehand") {
            onNodeWithTag(canvasTag).performMouseInput {
                moveTo(Offset(40f, 40f)); press(); moveTo(Offset(60f, 60f)); moveTo(Offset(80f, 80f)); release()
            }
            waitForIdle()
            onNodeWithTag(canvasTag).performMouseInput {
                moveTo(Offset(200f, 40f)); press(); moveTo(Offset(220f, 60f)); release()
            }
            waitForIdle()
        }

        assertEquals(2, drawn.size, "each stroke is its own shape")
        // The buffer is cleared on drag end; if it were not, the second stroke would carry the
        // first one's points and its bounding box would stretch back across the canvas.
        assertTrue(
            drawn[1].points.size < drawn[0].points.size + 3,
            "the second stroke kept ${drawn[1].points.size} points against the first's ${drawn[0].points.size}",
        )
    }

    // ── When drawing is off ────────────────────────────────────────────────────

    @Test
    fun `the select tool draws nothing`() {
        val drawn = draw("select") {
            dragOnCanvas(Offset(40f, 30f), Offset(160f, 120f))
        }

        assertTrue(drawn.isEmpty(), "dragging in select mode must not create a shape")
    }

    @Test
    fun `a tap without a drag draws nothing`() {
        val drawn = draw("rectangle") {
            onNodeWithTag(canvasTag).performMouseInput { moveTo(Offset(80f, 80f)); press(); release() }
            waitForIdle()
        }

        // A click with no movement never starts the gesture, so a mis-click leaves no stray shape.
        assertNull(drawn.firstOrNull(), "a bare click must not create a shape")
    }
}
