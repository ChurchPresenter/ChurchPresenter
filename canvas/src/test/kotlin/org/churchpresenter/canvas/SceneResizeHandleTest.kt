package org.churchpresenter.canvas

import androidx.compose.ui.geometry.Offset
import org.churchpresenter.core.models.scene.SourceTransform
import java.awt.Cursor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The eight resize grips' arithmetic — what a drag of each one does to a source's transform.
 *
 * Which edges move and which stay put differs per handle, and the two halves are not symmetric:
 * dragging the **west** edge right moves `x` *and* shrinks `width`, while dragging the **east** edge
 * right only grows `width`. Swap those and a source leaps across the screen as it is resized, in
 * front of a congregation. The same asymmetry applies on the vertical axis, and the corners combine
 * both.
 *
 * Driven directly rather than through the grips: each is an 8dp box placed at a rotated offset
 * outside the source's own bounds, so a positional test would have to reproduce the placement maths
 * to find out where it landed and would then be asserting its own arithmetic. The gesture that calls
 * these — one `detectDragGestures` with a `coerceAtLeast` floor — stays uncovered; everything it
 * decides is here.
 *
 * A drag is in pixels and a transform in normalised 0..1, so every expectation below is the pixel
 * delta divided by the canvas dimension: on a 1000 x 500 canvas, 100px right is +0.1 and 50px down
 * is +0.1.
 */
class SceneResizeHandleTest {

    private val w = 1000f
    private val h = 500f

    /** A source occupying the middle of the canvas, so every edge has room to move either way. */
    private val source = SourceTransform(x = 0.2f, y = 0.2f, width = 0.4f, height = 0.4f)

    private val handles = resizeHandles(w, h)

    private fun handleAt(anchorX: Float, anchorY: Float): ResizeHandleDef =
        handles.single { it.anchorX == anchorX && it.anchorY == anchorY }

    private fun drag(anchorX: Float, anchorY: Float, dxPx: Float, dyPx: Float): SourceTransform =
        handleAt(anchorX, anchorY).onDrag(source, Offset(dxPx, dyPx))

    private fun assertClose(expected: Float, actual: Float, what: String = "value") =
        assertTrue(abs(expected - actual) < 0.0001f, "$what: expected $expected, was $actual")

    // ── The set itself ──────────────────────────────────────────────────────────

    @Test
    fun `there are eight handles, one per edge and corner`() {
        assertEquals(8, handles.size)
        assertEquals(
            listOf(
                0f to 0f, 0.5f to 0f, 1f to 0f,
                0f to 0.5f, 1f to 0.5f,
                0f to 1f, 0.5f to 1f, 1f to 1f,
            ),
            handles.map { it.anchorX to it.anchorY },
            "the drawing order is NW, N, NE, W, E, SW, S, SE",
        )
    }

    @Test
    fun `each handle offers the resize cursor for the direction it drags`() {
        // The cursor is the only thing telling the operator what a grip will do before they use it.
        assertEquals(Cursor.NW_RESIZE_CURSOR, handleAt(0f, 0f).cursor)
        assertEquals(Cursor.N_RESIZE_CURSOR, handleAt(0.5f, 0f).cursor)
        assertEquals(Cursor.NE_RESIZE_CURSOR, handleAt(1f, 0f).cursor)
        assertEquals(Cursor.W_RESIZE_CURSOR, handleAt(0f, 0.5f).cursor)
        assertEquals(Cursor.E_RESIZE_CURSOR, handleAt(1f, 0.5f).cursor)
        assertEquals(Cursor.SW_RESIZE_CURSOR, handleAt(0f, 1f).cursor)
        assertEquals(Cursor.S_RESIZE_CURSOR, handleAt(0.5f, 1f).cursor)
        assertEquals(Cursor.SE_RESIZE_CURSOR, handleAt(1f, 1f).cursor)
    }

    // ── The four edges ──────────────────────────────────────────────────────────

    @Test
    fun `dragging the west edge right moves the left edge in and narrows the source`() {
        val t = drag(0f, 0.5f, dxPx = 100f, dyPx = 0f)

        assertClose(0.3f, t.x, "x follows the dragged edge")
        assertClose(0.3f, t.width, "and the width shrinks by the same amount")
        assertClose(0.2f, t.y, "the vertical axis is untouched")
        assertClose(0.4f, t.height, "the vertical axis is untouched")
    }

    @Test
    fun `dragging the east edge right widens the source without moving it`() {
        val t = drag(1f, 0.5f, dxPx = 100f, dyPx = 0f)

        // The asymmetry with the west edge is the whole point: x must not move here.
        assertClose(0.2f, t.x, "the left edge stays anchored")
        assertClose(0.5f, t.width, "only the width grows")
    }

    @Test
    fun `dragging the north edge down moves the top edge in and shortens the source`() {
        val t = drag(0.5f, 0f, dxPx = 0f, dyPx = 50f)

        assertClose(0.3f, t.y)
        assertClose(0.3f, t.height)
        assertClose(0.2f, t.x, "the horizontal axis is untouched")
        assertClose(0.4f, t.width, "the horizontal axis is untouched")
    }

    @Test
    fun `dragging the south edge down lengthens the source without moving it`() {
        val t = drag(0.5f, 1f, dxPx = 0f, dyPx = 50f)

        assertClose(0.2f, t.y, "the top edge stays anchored")
        assertClose(0.5f, t.height, "only the height grows")
    }

    // ── The four corners ────────────────────────────────────────────────────────

    @Test
    fun `the north-west corner moves both edges in and shrinks both dimensions`() {
        val t = drag(0f, 0f, dxPx = 100f, dyPx = 50f)

        assertClose(0.3f, t.x)
        assertClose(0.3f, t.y)
        assertClose(0.3f, t.width)
        assertClose(0.3f, t.height)
    }

    @Test
    fun `the north-east corner moves only the top edge`() {
        val t = drag(1f, 0f, dxPx = 100f, dyPx = 50f)

        assertClose(0.2f, t.x, "the left edge is not the one being dragged")
        assertClose(0.3f, t.y, "but the top edge is")
        assertClose(0.5f, t.width, "so the width grows")
        assertClose(0.3f, t.height, "and the height shrinks")
    }

    @Test
    fun `the south-west corner moves only the left edge`() {
        val t = drag(0f, 1f, dxPx = 100f, dyPx = 50f)

        assertClose(0.3f, t.x)
        assertClose(0.2f, t.y, "the top edge stays put")
        assertClose(0.3f, t.width)
        assertClose(0.5f, t.height)
    }

    @Test
    fun `the south-east corner moves neither edge and grows both dimensions`() {
        val t = drag(1f, 1f, dxPx = 100f, dyPx = 50f)

        assertClose(0.2f, t.x)
        assertClose(0.2f, t.y)
        assertClose(0.5f, t.width)
        assertClose(0.5f, t.height)
    }

    // ── Properties that hold across the set ─────────────────────────────────────

    @Test
    fun `a zero drag leaves every handle's transform untouched`() {
        handles.forEach { handle ->
            assertEquals(
                source,
                handle.onDrag(source, Offset.Zero),
                "a handle pressed but not moved must change nothing",
            )
        }
    }

    @Test
    fun `no handle alters rotation or opacity`() {
        val rotated = source.copy(rotation = 30f, opacity = 0.5f)
        handles.forEach { handle ->
            val t = handle.onDrag(rotated, Offset(40f, 20f))
            assertEquals(30f, t.rotation, "resizing must not rotate")
            assertEquals(0.5f, t.opacity, "resizing must not change opacity")
        }
    }

    @Test
    fun `dragging an edge out and back returns the source to where it started`() {
        // Each handle's inverse is its own negated drag, which is what makes a resize feel
        // predictable when the operator overshoots and comes back.
        handles.forEach { handle ->
            val out = handle.onDrag(source, Offset(60f, 30f))
            val back = handle.onDrag(out, Offset(-60f, -30f))
            assertClose(source.x, back.x, "x after there-and-back")
            assertClose(source.y, back.y, "y after there-and-back")
            assertClose(source.width, back.width, "width after there-and-back")
            assertClose(source.height, back.height, "height after there-and-back")
        }
    }

    @Test
    fun `the canvas size is what converts a pixel drag into the transform`() {
        // The same drag on a canvas twice as wide moves half as far in normalised terms — this is
        // why the handles are built per canvas rather than once.
        val wide = resizeHandles(canvasWidth = 2000f, canvasHeight = h)
        val t = wide.single { it.anchorX == 1f && it.anchorY == 0.5f }.onDrag(source, Offset(100f, 0f))

        assertClose(0.45f, t.width, "100px of 2000 is +0.05, not +0.1")
    }
}
