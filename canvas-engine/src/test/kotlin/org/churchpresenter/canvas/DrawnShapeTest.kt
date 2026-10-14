package org.churchpresenter.canvas

import androidx.compose.ui.geometry.Offset
import org.churchpresenter.core.models.scene.PathPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawnShapeTest {

    private val stroke = ShapeStroke(color = "#FF0000", fill = "#00FF0080", width = 4f)

    private fun draw(tool: String, start: Offset, end: Offset, freehand: List<PathPoint> = emptyList()) =
        drawnShape(tool, stroke, start, end, freehand)

    @Test
    fun `a rectangle covers the drag, whichever way it went`() {
        val forwards = draw("rectangle", Offset(0.1f, 0.2f), Offset(0.4f, 0.6f)).transform
        val backwards = draw("rectangle", Offset(0.4f, 0.6f), Offset(0.1f, 0.2f)).transform

        assertEquals(forwards, backwards)
        assertEquals(0.1f, forwards.x)
        assertEquals(0.3f, forwards.width, 0.0001f)
    }

    @Test
    fun `a click with no drag still leaves a shape big enough to grab`() {
        val shape = draw("ellipse", Offset(0.5f, 0.5f), Offset(0.5f, 0.5f))

        assertEquals(0.01f, shape.transform.width)
        assertEquals(0.01f, shape.transform.height)
    }

    @Test
    fun `the shape carries the tool, its name and the toolbar's colours`() {
        val shape = draw("ellipse", Offset(0f, 0f), Offset(0.2f, 0.2f))

        assertEquals("ellipse", shape.shapeType)
        assertEquals("Ellipse", shape.name)
        assertEquals("#FF0000", shape.strokeColor)
        assertEquals("#00FF0080", shape.fillColor)
        assertEquals(4f, shape.strokeWidth)
        assertTrue(shape.points.isEmpty(), "an enclosing shape has no points")
    }

    @Test
    fun `a line keeps its two ends inside its own box, in the direction it was drawn`() {
        val line = draw("line", Offset(0.6f, 0.2f), Offset(0.2f, 0.6f))

        assertEquals(0.2f, line.transform.x, 0.0001f)
        assertEquals(listOf(PathPoint(1f, 0f), PathPoint(0f, 1f)), line.points)
    }

    @Test
    fun `an arrow is stored like a line`() {
        val arrow = draw("arrow", Offset(0.1f, 0.1f), Offset(0.3f, 0.5f))

        assertEquals("arrow", arrow.shapeType)
        assertEquals(listOf(PathPoint(0f, 0f), PathPoint(1f, 1f)), arrow.points)
    }

    @Test
    fun `a perfectly flat line is still given a little height`() {
        val line = draw("line", Offset(0.1f, 0.5f), Offset(0.4f, 0.5f))

        assertEquals(0.01f, line.transform.height)
    }

    @Test
    fun `a freehand stroke is boxed to its own points and keeps their shape inside the box`() {
        val path = listOf(PathPoint(0.2f, 0.2f), PathPoint(0.4f, 0.3f), PathPoint(0.6f, 0.6f))

        val shape = draw("freehand", Offset.Zero, Offset.Zero, path)

        assertEquals(0.2f, shape.transform.x, 0.0001f)
        assertEquals(0.4f, shape.transform.width, 0.0001f)
        assertEquals(PathPoint(0f, 0f), shape.points.first())
        assertEquals(PathPoint(1f, 1f), shape.points.last())
        assertEquals(0.5f, shape.points[1].x, 0.0001f)
    }

    @Test
    fun `a freehand dot is still given a usable box`() {
        val shape = draw("freehand", Offset.Zero, Offset.Zero, listOf(PathPoint(0.3f, 0.3f)))

        assertEquals(0.01f, shape.transform.width)
        assertEquals(listOf(PathPoint(0f, 0f)), shape.points)
    }
}
