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
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Selecting and moving a source on the canvas with the select tool.
 *
 * These are the two gestures the canvas exists for. Which source a click selects decides what every
 * control in the properties panel then edits, and a drag has to move the source the operator grabbed
 * — by the distance they dragged — while the snap logic pulls it onto the guides. A source that is
 * locked, or invisible, must ignore both.
 *
 * The canvas is 400dp wide at 16:9, so it lays out 400 x 225 and a normalised coordinate maps to
 * pixels by multiplying by those — confirmed by `SceneCanvasDrawingTest`, which reads the same
 * mapping back out of a drag. Sources publish no test tag, so they are addressed by pressing at a
 * position inside their bounds: they are descendants of the canvas node and hit-testing routes the
 * event to the topmost one, which is exactly what a real click does.
 */
class SceneCanvasSelectionTest {

    private val canvasTag = "scene-canvas"

    /** What the canvas reported back. */
    private class Reports {
        val selected = mutableListOf<String?>()
        val transforms = mutableListOf<Pair<String, SourceTransform>>()
        val drawn = mutableListOf<SceneSource.ShapeSource>()
    }

    /** A shape occupying the normalised box 0.1..0.4 on both axes — pixels 40..160 by 22.5..90. */
    private fun shape(
        id: String = "shape-1",
        x: Float = 0.1f,
        y: Float = 0.1f,
        locked: Boolean = false,
        visible: Boolean = true,
    ) = SceneSource.ShapeSource(
        id = id,
        name = id,
        transform = SourceTransform(x = x, y = y, width = 0.3f, height = 0.3f),
        shapeType = "rectangle",
        strokeColor = "#FFFFFF",
        fillColor = "#FF808080",
        strokeWidth = 2f,
        locked = locked,
        visible = visible,
    )

    private fun canvas(
        sources: List<SceneSource>,
        selectedId: String? = null,
        tool: String = "select",
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
                            onSourceSelected = { reports.selected += it },
                            onTransformChanged = { id, t -> reports.transforms += id to t },
                            activeTool = tool,
                            onShapeDrawn = { reports.drawn += it },
                        )
                    }
                }
            }
            block(reports)
        }
    }

    private fun ComposeUiTest.clickAt(x: Float, y: Float) {
        onNodeWithTag(canvasTag).performMouseInput {
            moveTo(Offset(x, y))
            press()
            release()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.dragBy(fromX: Float, fromY: Float, dx: Float, dy: Float) {
        onNodeWithTag(canvasTag).performMouseInput {
            moveTo(Offset(fromX, fromY))
            press()
            // Stepped rather than jumped: the move handler accumulates dragAmount per event, so a
            // single leap would exercise one large delta instead of the accumulation itself.
            moveTo(Offset(fromX + dx / 2f, fromY + dy / 2f))
            moveTo(Offset(fromX + dx, fromY + dy))
            release()
        }
        waitForIdle()
    }

    // ── Selecting ───────────────────────────────────────────────────────────────

    @Test
    fun `clicking a source selects it`() = canvas(listOf(shape())) { reports ->
        clickAt(100f, 56f) // inside the shape

        assertEquals(listOf<String?>("shape-1"), reports.selected)
    }

    @Test
    fun `clicking empty canvas clears the selection`() = canvas(listOf(shape())) { reports ->
        clickAt(350f, 200f) // outside the shape

        // Clearing is what lets the properties panel fall back to the scene itself.
        assertEquals(listOf<String?>(null), reports.selected)
    }

    @Test
    fun `clicking the top source selects it rather than the one beneath`() {
        val back = shape(id = "back")
        val front = shape(id = "front")
        // Later in the list = drawn in front, so a click in the overlap belongs to the front one.
        canvas(listOf(back, front)) { reports ->
            clickAt(100f, 56f)

            assertEquals(listOf<String?>("front"), reports.selected)
        }
    }

    @Test
    fun `a locked source can still be selected`() = canvas(listOf(shape(locked = true))) { reports ->
        clickAt(100f, 56f)

        // Locking prevents moving, not selecting — otherwise it could never be unlocked again.
        assertEquals(listOf<String?>("shape-1"), reports.selected)
    }

    @Test
    fun `an invisible source is not clickable`() = canvas(listOf(shape(visible = false))) { reports ->
        clickAt(100f, 56f)

        // A hidden source is skipped entirely, so the click falls through to the canvas.
        assertEquals(listOf<String?>(null), reports.selected)
    }

    // ── Moving ──────────────────────────────────────────────────────────────────

    @Test
    fun `dragging the selected source moves it by the drag distance`() =
        canvas(listOf(shape()), selectedId = "shape-1") { reports ->
            dragBy(fromX = 100f, fromY = 56f, dx = 80f, dy = 40f)

            assertTrue(reports.transforms.isNotEmpty(), "a drag must report a transform")
            val (id, moved) = reports.transforms.last()
            assertEquals("shape-1", id, "the dragged source must be the one that moves")
            // 80px right on a 400px canvas is +0.2; 40px down on 225px is about +0.178. The drag is
            // armed after slop so a little is lost, hence a tolerance rather than an equality.
            assertTrue(moved.x > 0.1f, "it must have moved right, x was ${moved.x}")
            assertTrue(moved.y > 0.1f, "and downwards, y was ${moved.y}")
            assertTrue(moved.x <= 0.1f + 0.2f + 0.01f, "but no further than dragged, x was ${moved.x}")
        }

    @Test
    fun `dragging leaves the source's size and rotation alone`() =
        canvas(listOf(shape()), selectedId = "shape-1") { reports ->
            dragBy(fromX = 100f, fromY = 56f, dx = 60f, dy = 30f)

            val moved = reports.transforms.last().second
            assertEquals(0.3f, moved.width, "a move must not resize")
            assertEquals(0.3f, moved.height, "a move must not resize")
            assertEquals(0f, moved.rotation, "nor rotate")
        }

    @Test
    fun `a locked source does not move when dragged`() =
        canvas(listOf(shape(locked = true)), selectedId = "shape-1") { reports ->
            // First prove these coordinates really do land on the source: without this the test
            // would pass just as well if the drag were missing it entirely.
            clickAt(100f, 56f)
            assertEquals(listOf<String?>("shape-1"), reports.selected, "the press must hit the source")

            dragBy(fromX = 100f, fromY = 56f, dx = 80f, dy = 40f)

            // The whole point of the lock: a background someone has positioned stays put even if
            // they grab it by accident mid-service.
            assertTrue(
                reports.transforms.isEmpty(),
                "a locked source must report no transform, got ${reports.transforms}",
            )
        }

    @Test
    fun `an unselected source does not move when dragged`() =
        canvas(listOf(shape()), selectedId = null) { reports ->
            // Same guard as above — the click proves the coordinates are on the source, so the
            // absence of a transform afterwards is about the branch and not about a missed press.
            clickAt(100f, 56f)
            assertEquals(listOf<String?>("shape-1"), reports.selected, "the press must hit the source")

            dragBy(fromX = 100f, fromY = 56f, dx = 80f, dy = 40f)

            // Only the selected source takes the drag modifier; the rest are tap-to-select only.
            assertTrue(reports.transforms.isEmpty(), "got ${reports.transforms}")
        }

    @Test
    fun `dragging a source towards the canvas edge snaps it flush`() =
        canvas(listOf(shape(x = 0.1f, y = 0.1f)), selectedId = "shape-1") { reports ->
            // Aim just past the left edge: the snap threshold pulls the left edge onto 0 exactly.
            dragBy(fromX = 100f, fromY = 56f, dx = -39f, dy = 0f)

            val moved = reports.transforms.last().second
            assertEquals(0f, moved.x, "the left edge must snap flush to the canvas edge")
        }

    @Test
    fun `a source held mid-drag in a corner snaps on both axes until it is let go`() =
        canvas(listOf(shape(x = 0.1f, y = 0.1f)), selectedId = "shape-1") { reports ->
            onNodeWithTag(canvasTag).performMouseInput {
                moveTo(Offset(100f, 56f))
                press()
                moveTo(Offset(80f, 45f))
                moveTo(Offset(61f, 34f))
            }
            waitForIdle()
            val held = reports.transforms.last().second

            onNodeWithTag(canvasTag).performMouseInput { release() }
            waitForIdle()

            assertEquals(0f, held.x, "the left edge sits on the canvas edge while the drag is held")
            assertEquals(0f, held.y, "and the top edge on the top")
            assertEquals(0f, reports.transforms.last().second.x)
            assertEquals(0f, reports.transforms.last().second.y)
        }

    @Test
    fun `dragging the rotate grip turns the selected source the way it was dragged`() =
        canvas(listOf(shape(x = 0.1f, y = 0.4f)), selectedId = "shape-1") { reports ->
            // The grip sits 25dp above the top edge's middle: (100, 90 - 25).
            onNodeWithTag(canvasTag).performMouseInput {
                moveTo(Offset(100f, 65f))
                press()
                moveTo(Offset(110f, 66f))
                moveTo(Offset(140f, 75f))
                moveTo(Offset(170f, 110f))
                release()
            }
            waitForIdle()

            val turned = reports.transforms.last().second
            assertTrue(turned.rotation > 0f, "a drag to the right turns it clockwise, was ${turned.rotation}")
            assertEquals(0.3f, turned.width, "turning leaves the size alone")
        }

    @Test
    fun `dragging the bottom-right grip grows the selected source`() =
        canvas(listOf(shape(x = 0.1f, y = 0.4f)), selectedId = "shape-1") { reports ->
            onNodeWithTag(canvasTag).performMouseInput {
                moveTo(Offset(160f, 157f))
                press()
                moveTo(Offset(170f, 162f))
                moveTo(Offset(190f, 172f))
                release()
            }
            waitForIdle()

            val grown = reports.transforms.last().second
            assertTrue(grown.width > 0.3f && grown.height > 0.3f, "both sides grow, was $grown")
            assertEquals(0.1f, grown.x, "and the top-left corner stays put")
        }

    @Test
    fun `with a drawing tool active a drag over the selected source draws instead of moving it`() =
        canvas(listOf(shape(x = 0.1f, y = 0.4f)), selectedId = "shape-1", tool = "rectangle") { reports ->
            dragBy(fromX = 100f, fromY = 120f, dx = 40f, dy = 30f)

            assertTrue(reports.transforms.isEmpty(), "the source stays where it is, got ${reports.transforms}")
            assertEquals("rectangle", reports.drawn.single().shapeType)
            assertTrue(reports.selected.isEmpty(), "and drawing selects nothing")
        }

    @Test
    fun `a selected but locked source offers no grips and does not move`() =
        canvas(listOf(shape(x = 0.1f, y = 0.4f, locked = true)), selectedId = "shape-1") { reports ->
            dragBy(fromX = 160f, fromY = 157f, dx = 30f, dy = 15f)
            dragBy(fromX = 100f, fromY = 120f, dx = 30f, dy = 0f)

            assertTrue(reports.transforms.isEmpty(), "got ${reports.transforms}")
        }
}
