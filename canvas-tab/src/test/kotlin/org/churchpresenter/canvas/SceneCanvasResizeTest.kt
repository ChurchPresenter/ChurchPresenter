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
import kotlin.test.assertTrue

/**
 * Resizing a source by its corner handles.
 *
 * The eight handles round a selected source, which had never been dragged in a test — the existing
 * canvas suite drags the body (a move) and the rotation handle, not these.
 *
 * The corner an operator grabs decides which edges stay put: dragging the bottom-right must leave
 * the top-left where it is, and dragging the top-left must move the origin as well as the size. Get
 * that backwards and a source jumps across the canvas when it should only have grown.
 */
class SceneCanvasResizeTest {

    private val canvasTag = "scene-canvas"

    private class Reports {
        val transforms = mutableListOf<Pair<String, SourceTransform>>()
    }

    private fun shape(locked: Boolean = false) = SceneSource.ShapeSource(
        id = "shape-1",
        name = "shape-1",
        transform = SourceTransform(x = 0.25f, y = 0.25f, width = 0.5f, height = 0.5f),
        shapeType = "rectangle",
        strokeColor = "#FFFFFF",
        fillColor = "#FF808080",
        strokeWidth = 2f,
        locked = locked,
    )

    private fun canvas(
        sources: List<SceneSource> = listOf(shape()),
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
                                id = "scene-1", name = "Scene",
                                canvasWidth = 1920, canvasHeight = 1080,
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
     * Drags from a point on the canvas by ([dx], [dy]).
     *
     * The canvas is 400dp wide and 16:9, so about 225dp tall. The shape spans 0.25..0.75 of each,
     * putting its corners at (100, 56) and (300, 169).
     */
    private fun ComposeUiTest.dragFrom(x: Float, y: Float, dx: Float, dy: Float) {
        onNodeWithTag(canvasTag).performMouseInput {
            moveTo(Offset(x, y))
            press()
            moveTo(Offset(x + dx / 2f, y + dy / 2f))
            moveTo(Offset(x + dx, y + dy))
            release()
        }
        waitForIdle()
    }

    @Test
    fun `dragging the bottom-right corner outwards makes the source bigger`() = canvas { reports ->
        dragFrom(x = 300f, y = 169f, dx = 40f, dy = 20f)

        assertTrue(reports.transforms.isNotEmpty(), "the handle must report a transform")
        val grown = reports.transforms.last().second
        assertTrue(grown.width > 0.5f, "width was ${grown.width}")
        assertTrue(grown.height > 0.5f, "height was ${grown.height}")
    }

    @Test
    fun `dragging the bottom-right leaves the top-left where it was`() = canvas { reports ->
        dragFrom(x = 300f, y = 169f, dx = 40f, dy = 20f)

        val grown = reports.transforms.last().second
        // Growing from a corner must not shift the opposite one; otherwise the source appears to
        // slide while being resized.
        assertEquals(0.25f, grown.x)
        assertEquals(0.25f, grown.y)
    }

    @Test
    fun `dragging the bottom-right inwards makes the source smaller`() = canvas { reports ->
        dragFrom(x = 300f, y = 169f, dx = -40f, dy = -20f)

        val shrunk = reports.transforms.last().second
        assertTrue(shrunk.width < 0.5f, "width was ${shrunk.width}")
    }

    @Test
    fun `dragging the top-left moves the origin as well as the size`() = canvas { reports ->
        dragFrom(x = 100f, y = 56f, dx = 30f, dy = 20f)

        assertTrue(reports.transforms.isNotEmpty())
        val resized = reports.transforms.last().second
        assertTrue(resized.x > 0.25f, "the left edge should have come in, x was ${resized.x}")
        assertTrue(resized.width < 0.5f, "and the source narrowed, width was ${resized.width}")
    }

    @Test
    fun `resizing does not turn the source`() = canvas { reports ->
        dragFrom(x = 300f, y = 169f, dx = 40f, dy = 20f)

        assertEquals(0f, reports.transforms.last().second.rotation)
    }

    @Test
    fun `a locked source cannot be resized`() = canvas(listOf(shape(locked = true))) { reports ->
        dragFrom(x = 300f, y = 169f, dx = 40f, dy = 20f)

        assertTrue(reports.transforms.isEmpty(), "a locked source reported ${reports.transforms}")
    }

    @Test
    fun `an unselected source has no handles`() = canvas(selectedId = null) { reports ->
        dragFrom(x = 300f, y = 169f, dx = 40f, dy = 20f)

        assertTrue(reports.transforms.isEmpty())
    }
}
