package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a layer sits against its canvas, and pulling one back inside (#608).
 *
 * The canvas clips what it draws, so these decide whether the Sources list and the banner say a
 * layer is cut off or gone -- and "Bring into view" is only as good as the least move it makes.
 */
class CanvasPlacementTest {

    private fun t(x: Float, y: Float, w: Float = 0.2f, h: Float = 0.2f) = SourceTransform(x, y, w, h)

    /** Compared within a hair: `1f - 0.2f` is not bit-for-bit `0.8f`. */
    private fun assertAt(transform: SourceTransform, x: Float, y: Float) {
        assertEquals(x, transform.x, TOLERANCE, "x of $transform")
        assertEquals(y, transform.y, TOLERANCE, "y of $transform")
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }

    @Test
    fun `a layer wholly on the canvas is inside`() {
        assertEquals(CanvasPlacement.INSIDE, t(0.4f, 0.4f).placement())
        assertEquals(CanvasPlacement.INSIDE, SourceTransform().placement(), "the full-canvas default")
    }

    @Test
    fun `a layer flush with an edge is still inside`() {
        assertEquals(CanvasPlacement.INSIDE, t(0f, 0f).placement())
        assertEquals(CanvasPlacement.INSIDE, t(0.8f, 0.8f).placement())
        // Float rounding on a snapped edge must not read as poking out.
        assertEquals(CanvasPlacement.INSIDE, t(0.8000005f, 0f).placement())
    }

    @Test
    fun `a layer past one edge is partly outside`() {
        assertEquals(CanvasPlacement.PARTLY_OUTSIDE, t(-0.1f, 0.4f).placement())
        assertEquals(CanvasPlacement.PARTLY_OUTSIDE, t(0.9f, 0.4f).placement())
        assertEquals(CanvasPlacement.PARTLY_OUTSIDE, t(0.4f, 0.95f).placement())
        assertEquals(CanvasPlacement.PARTLY_OUTSIDE, t(-0.1f, -0.1f, 1.2f, 1.2f).placement(), "larger than the canvas")
    }

    @Test
    fun `a layer that does not touch the canvas is outside`() {
        assertEquals(CanvasPlacement.OUTSIDE, t(1.5f, 0.4f).placement())
        assertEquals(CanvasPlacement.OUTSIDE, t(-0.5f, 0.4f).placement())
        assertEquals(CanvasPlacement.OUTSIDE, t(0.4f, 1f).placement(), "starting exactly at the bottom edge")
    }

    @Test
    fun `bringing into view leaves a layer that is already inside alone`() {
        val inside = t(0.3f, 0.6f)
        assertEquals(inside, inside.broughtIntoView())
    }

    @Test
    fun `bringing into view moves a layer only as far as the edge it crossed`() {
        assertAt(t(1.5f, 0.4f).broughtIntoView(), x = 0.8f, y = 0.4f)
        assertAt(t(-0.3f, 0.4f).broughtIntoView(), x = 0f, y = 0.4f)
        assertAt(t(0.3f, 0.9f).broughtIntoView(), x = 0.3f, y = 0.8f)
    }

    @Test
    fun `bringing into view shrinks a layer larger than the canvas to fit it`() {
        val fitted = t(-0.2f, 0.1f, 1.4f, 0.5f).broughtIntoView()
        assertAt(fitted, x = 0f, y = 0.1f)
        assertEquals(1f, fitted.width)
        assertEquals(0.5f, fitted.height)
        assertEquals(CanvasPlacement.INSIDE, fitted.placement())
    }

    @Test
    fun `bringing into view keeps rotation and opacity`() {
        val turned = SourceTransform(x = 1.2f, y = 0f, width = 0.2f, height = 0.2f, rotation = 30f, opacity = 0.5f)
        val fitted = turned.broughtIntoView()
        assertEquals(30f, fitted.rotation)
        assertEquals(0.5f, fitted.opacity)
    }
}
