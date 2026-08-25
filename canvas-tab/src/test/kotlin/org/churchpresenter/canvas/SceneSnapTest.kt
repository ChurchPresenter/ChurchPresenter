package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scene-canvas drag snapping — how a dragged source clicks onto the canvas guides (left/centre/
 * right, top/centre/bottom) and onto other sources' edges within the snap threshold. Coordinates are
 * normalised 0..1 and the threshold is 6px / canvas size, so at 1000px the window to snap is 0.006.
 * A wrong result snaps to the wrong guide or fails to snap; the guides, the threshold edge, the
 * source targets, and the exclude/invisible rules are pinned here.
 */
class SceneSnapTest {

    private val w = 1000f
    private val h = 1000f            // threshold = 6/1000 = 0.006 on each axis
    private val size = 0.2f          // the dragged source is 0.2 x 0.2

    private fun snap(x: Float, y: Float, sources: List<SceneSource> = emptyList(), excludeId: String = "self") =
        computeSnap(x, y, size, size, sources, excludeId, canvasWidth = w, canvasHeight = h)

    private fun approx(a: Float, b: Float) = abs(a - b) <= 0.0005f

    private fun source(id: String, x: Float, visible: Boolean = true) = SceneSource.ImageSource(
        id = id, name = id, filePath = "",
        transform = SourceTransform(x = x, y = 0f, width = 0.1f, height = 0.1f),
        visible = visible,
    )

    @Test
    fun `a left edge just inside the threshold snaps to the canvas left`() {
        val r = snap(x = 0.003f, y = 0.4f) // left edge 0.003 is within 0.006 of guide 0
        assertTrue(approx(0f, r.x), "x snaps so the left edge sits on 0, was ${r.x}")
        assertTrue(r.snapLines.any { it.orientation == SnapOrientation.VERTICAL && approx(it.position, 0f) })
    }

    @Test
    fun `a centre near the canvas centre snaps the centre to 0-5`() {
        // centre = x + 0.1; put it at 0.502 so it's 0.002 from the 0.5 guide.
        val r = snap(x = 0.402f, y = 0.4f)
        assertTrue(approx(0.4f, r.x), "x moves 0.002 so the centre lands on 0.5, was ${r.x}")
    }

    @Test
    fun `a top edge near the canvas top snaps on the vertical axis`() {
        // x=0.31 keeps every x-edge (0.31/0.41/0.51) clear of the guides; top edge 0.004 is within 0.006 of 0.
        val r = snap(x = 0.31f, y = 0.004f)
        assertTrue(approx(0f, r.y), "y snaps to 0, was ${r.y}")
        assertTrue(r.snapLines.any { it.orientation == SnapOrientation.HORIZONTAL })
    }

    @Test
    fun `an edge outside the threshold does not snap`() {
        // edges 0.2 / 0.275 / 0.35 are all more than 0.006 from any guide (0, 0.5, 1).
        val r = snap(x = 0.2f, y = 0.2f)
        assertTrue(approx(0.2f, r.x) && approx(0.2f, r.y), "unchanged, was (${r.x}, ${r.y})")
        assertEquals(emptyList(), r.snapLines, "no guides shown when nothing snaps")
    }

    // These use a source edge at 0.2 and drag to 0.203 — deliberately away from every canvas guide
    // (0/0.5/1), so the only thing that could snap x is the other source's edge.
    @Test
    fun `a dragged source snaps to another source's edge`() {
        val r = snap(x = 0.203f, y = 0.4f, sources = listOf(source("other", x = 0.2f)))
        assertTrue(approx(0.2f, r.x), "x snaps onto the other source's edge at 0.2, was ${r.x}")
    }

    @Test
    fun `the dragged source's own geometry is excluded`() {
        val r = snap(x = 0.203f, y = 0.4f, sources = listOf(source("self", x = 0.2f)), excludeId = "self")
        assertTrue(approx(0.203f, r.x), "no self-snap, x unchanged, was ${r.x}")
    }

    @Test
    fun `an invisible source is not a snap target`() {
        val r = snap(x = 0.203f, y = 0.4f, sources = listOf(source("other", x = 0.2f, visible = false)))
        assertTrue(approx(0.203f, r.x), "a hidden source offers no edge to snap to, was ${r.x}")
    }

    // ── One guide at a time ─────────────────────────────────────────────────────

    @Test
    fun `a closer vertical guide replaces the one already found, rather than joining it`() {
        // The source's left edge is inside the threshold of the canvas edge, and its right edge is
        // closer still to a neighbour. Two lines would draw two guides at once and tell the operator
        // the source had snapped to both, when it can only have snapped to one.
        val neighbour = SceneSource.ImageSource(
            id = "other", name = "other", filePath = "",
            transform = SourceTransform(x = 0.2045f, y = 0.5f, width = 0.1f, height = 0.1f),
        )

        val r = snap(x = 0.004f, y = 0.4f, sources = listOf(neighbour))

        val vertical = r.snapLines.filter { it.orientation == SnapOrientation.VERTICAL }
        assertEquals(1, vertical.size, "one vertical guide, not one per candidate: $vertical")
        assertTrue(approx(0.2045f, vertical.single().position), "the closer guide is the one kept")
    }

    @Test
    fun `a closer horizontal guide replaces the one already found`() {
        val neighbour = SceneSource.ImageSource(
            id = "other", name = "other", filePath = "",
            transform = SourceTransform(x = 0.5f, y = 0.2045f, width = 0.1f, height = 0.1f),
        )

        val r = snap(x = 0.4f, y = 0.004f, sources = listOf(neighbour))

        val horizontal = r.snapLines.filter { it.orientation == SnapOrientation.HORIZONTAL }
        assertEquals(1, horizontal.size, "one horizontal guide, not one per candidate: $horizontal")
        assertTrue(approx(0.2045f, horizontal.single().position))
    }

    @Test
    fun `snapping on both axes at once draws one guide on each`() {
        val r = snap(x = 0.003f, y = 0.003f)

        assertEquals(1, r.snapLines.count { it.orientation == SnapOrientation.VERTICAL })
        assertEquals(1, r.snapLines.count { it.orientation == SnapOrientation.HORIZONTAL })
    }
}
