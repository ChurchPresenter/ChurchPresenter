@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

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
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertTrue

/**
 * Turning a source with the rotation handle.
 *
 * The handle sits above the selected source's top edge, and dragging it around the centre sets the
 * angle. None of it ran before: the handle only composes for a *selected* source, and the existing
 * canvas tests drag the body rather than the handle.
 *
 * Rotation is the one transform an operator cannot undo by eye — a slide left at 3° reads as a
 * mistake rather than a design — so what is pinned here is that the angle follows the pointer, that
 * the source's position and size are left alone, and that a locked source refuses.
 */
class SceneCanvasRotationTest {

    private val canvasTag = "scene-canvas"

    private class Reports {
        val transforms = mutableListOf<Pair<String, SourceTransform>>()
    }

    private fun shape(
        id: String = "shape-1",
        rotation: Float = 0f,
        locked: Boolean = false,
    ) = SceneSource.ShapeSource(
        id = id,
        name = id,
        transform = SourceTransform(x = 0.3f, y = 0.3f, width = 0.4f, height = 0.4f, rotation = rotation),
        shapeType = "rectangle",
        strokeColor = "#FFFFFF",
        fillColor = "#FF808080",
        strokeWidth = 2f,
        locked = locked,
    )

    private fun canvas(
        sources: List<SceneSource>,
        selectedId: String? = "shape-1",
        block: ComposeUiTest.(Reports) -> Unit,
    ) {
        val reports = Reports()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.width(400.dp)) {
                        SceneCanvas(
                            modifier = Modifier.testTag(canvasTag),
                            scene = Scene(
                                id = "scene-1",
                                name = "Scene",
                                canvasWidth = 1920,
                                canvasHeight = 1080,
                                sources = sources,
                            ),
                            selectedSourceId = selectedId,
                            onSourceSelected = { },
                            onTransformChanged = { id, t -> reports.transforms += id to t },
                            activeTool = "select",
                        )
                    }
                }
            }
            block(reports)
        }
    }

    /**
     * Drags from just above the source's top edge — where the rotation handle sits — around to the
     * side, which is a quarter turn about the centre.
     *
     * The canvas is 400dp wide and 16:9, so about 225dp tall; the shape spans 0.3..0.7 of both, so
     * its centre is at (200, 112) and its top edge at y≈67. The handle is 25dp above that.
     */
    private fun ComposeUiTest.dragHandle(dx: Float, dy: Float, atDegrees: Float = 0f) {
        // The handle rides round the centre with the source, so at an angle it is no longer above
        // the top edge. Rotate its resting offset by the same amount to find it.
        val rad = Math.toRadians(atDegrees.toDouble())
        val startX = 200f + (0f * cos(rad) - (-70f) * sin(rad)).toFloat()
        val startY = 112f + (0f * sin(rad) + (-70f) * cos(rad)).toFloat()
        onNodeWithTag(canvasTag).performMouseInput {
            moveTo(Offset(startX, startY))
            press()
            moveTo(Offset(startX + dx / 2f, startY + dy / 2f))
            moveTo(Offset(startX + dx, startY + dy))
            release()
        }
        waitForIdle()
    }

    @Test
    fun `dragging the handle reports a rotation`() = canvas(listOf(shape())) { reports ->
        dragHandle(dx = 90f, dy = 70f)

        assertTrue(reports.transforms.isNotEmpty(), "the handle must report a transform")
        val (id, turned) = reports.transforms.last()
        assertEquals("shape-1", id)
        assertTrue(turned.rotation != 0f, "the angle must have moved, was ${turned.rotation}")
    }

    @Test
    fun `rotating leaves the source where it is and the size it was`() = canvas(listOf(shape())) { reports ->
        dragHandle(dx = 90f, dy = 70f)

        val turned = reports.transforms.last().second
        // Turning is not moving: an operator straightening a title must not find it has drifted.
        assertEquals(0.3f, turned.x)
        assertEquals(0.3f, turned.y)
        assertEquals(0.4f, turned.width)
        assertEquals(0.4f, turned.height)
    }

    @Test
    fun `dragging further keeps turning rather than stopping at the first angle`() =
        canvas(listOf(shape())) { reports ->
            dragHandle(dx = 120f, dy = 120f)

            val angles = reports.transforms.map { it.second.rotation }.distinct()
            assertTrue(angles.size > 1, "the angle should follow the pointer, saw $angles")
        }

    @Test
    fun `a source already at an angle turns from there`() = canvas(listOf(shape(rotation = 45f))) { reports ->
        dragHandle(dx = 60f, dy = 40f, atDegrees = 45f)

        assertTrue(reports.transforms.isNotEmpty())
        assertTrue(reports.transforms.last().second.rotation != 45f, "it must have moved off 45°")
    }

    @Test
    fun `a locked source does not turn`() = canvas(listOf(shape(locked = true))) { reports ->
        dragHandle(dx = 90f, dy = 70f)

        // Locking exists so a background cannot be nudged while placing something over it.
        assertTrue(reports.transforms.isEmpty(), "a locked source reported ${reports.transforms}")
    }

    @Test
    fun `an unselected source has no handle to drag`() = canvas(listOf(shape()), selectedId = null) { reports ->
        dragHandle(dx = 90f, dy = 70f)

        // The handle only composes for the selection, so this drag lands on empty canvas.
        assertTrue(reports.transforms.isEmpty())
    }
}
