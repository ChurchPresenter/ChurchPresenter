@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Which source the resize and rotation handles actually move.
 *
 * The canvas walks its sources with a plain `forEach`, so Compose identifies each by its *position*
 * in the list rather than by its id, and both handles used to put their gesture behind a
 * `pointerInput` keyed on something that does not change when the handles move to another source —
 * `Unit` for rotation, the handle's own index for resize. The callback that gesture captures carries
 * the id of the source it was built for, so anything that reuses the slot leaves the handle editing
 * the source that was there before. The body drag beside them was already keyed on `source.id`; the
 * handles were not.
 *
 * Keyed correctly, none of the list edits below can leave a handle pointed at the wrong source.
 * These assert that outcome; they are not a reproduction of the report that prompted the fix, which
 * has not been pinned down — see the module's `AGENT.md`.
 */
class SceneCanvasHandleSelectionTest {

    private val canvasTag = "scene-canvas"

    private fun shape(id: String, x: Float) = SceneSource.ShapeSource(
        id = id, name = id,
        transform = SourceTransform(x = x, y = 0.3f, width = 0.4f, height = 0.4f),
        shapeType = "rectangle", strokeColor = "#FFFFFF", fillColor = "#FF808080", strokeWidth = 2f,
    )

    /** Composes the canvas with both the selection and the source order under the test's control. */
    private fun canvas(
        sources: List<SceneSource>,
        block: ComposeUiTest.(
            reports: MutableList<Pair<String, SourceTransform>>,
            select: (String) -> Unit,
            reorder: (List<SceneSource>) -> Unit,
        ) -> Unit,
    ) {
        val reports = mutableListOf<Pair<String, SourceTransform>>()
        var choose: ((String) -> Unit)? = null
        var reseat: ((List<SceneSource>) -> Unit)? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var selected by remember { mutableStateOf(sources.first().id) }
                    var order by remember { mutableStateOf(sources) }
                    choose = { selected = it }
                    reseat = { order = it }
                    Box(Modifier.width(400.dp)) {
                        SceneCanvas(
                            modifier = Modifier.testTag(canvasTag),
                            scene = Scene(
                                id = "scene-1", name = "Scene",
                                canvasWidth = 1920, canvasHeight = 1080, sources = order,
                            ),
                            selectedSourceId = selected,
                            onSourceSelected = { },
                            onTransformChanged = { id, t -> reports += id to t },
                            activeTool = "select",
                        )
                    }
                }
            }
            block(
                reports,
                { id -> choose?.invoke(id); waitForIdle() },
                { list -> reseat?.invoke(list); waitForIdle() },
            )
        }
    }

    /** Drags the rotation handle of a source whose centre is at [centreX]. */
    private fun ComposeUiTest.dragRotationHandle(centreX: Float) {
        onNodeWithTag(canvasTag).performMouseInput {
            moveTo(Offset(centreX, 42f))
            press()
            moveTo(Offset(centreX + 45f, 77f))
            moveTo(Offset(centreX + 90f, 112f))
            release()
        }
        waitForIdle()
    }

    /** Drags the bottom-right resize handle of a source whose box ends at [rightX]. */
    private fun ComposeUiTest.dragResizeHandle(rightX: Float) {
        onNodeWithTag(canvasTag).performMouseInput {
            moveTo(Offset(rightX, 157f))
            press()
            moveTo(Offset(rightX + 10f, 167f))
            moveTo(Offset(rightX + 20f, 177f))
            release()
        }
        waitForIdle()
    }

    // ── The bug: the handle keeps the source it was built for ──────────────────

    @Test
    fun `the rotation handle follows the source into its new place in the list`() {
        val a = shape("shape-a", x = 0.3f)
        val b = shape("shape-b", x = 0.3f)

        canvas(listOf(a, b)) { reports, select, reorder ->
            dragRotationHandle(centreX = 200f)
            assertEquals("shape-a", reports.first().first, "the first drag must turn the selected source")
            reports.clear()

            // Source b moved forward, so it now occupies the slot a's handle was laid out in.
            reorder(listOf(b, a))
            select("shape-b")
            dragRotationHandle(centreX = 200f)

            assertTrue(reports.isNotEmpty(), "the second drag must have turned something")
            assertTrue(
                reports.all { it.first == "shape-b" },
                "every rotation must land on the selected source, got ${reports.map { it.first }.distinct()}",
            )
        }
    }

    @Test
    fun `the resize handles follow the source into its new place too`() {
        val a = shape("shape-a", x = 0.3f)
        val b = shape("shape-b", x = 0.3f)

        canvas(listOf(a, b)) { reports, select, reorder ->
            dragResizeHandle(rightX = 280f)
            reports.clear()

            reorder(listOf(b, a))
            select("shape-b")
            dragResizeHandle(rightX = 280f)

            assertTrue(reports.isNotEmpty(), "the second drag must have resized something")
            assertTrue(
                reports.all { it.first == "shape-b" },
                "every resize must land on the selected source, got ${reports.map { it.first }.distinct()}",
            )
        }
    }

    @Test
    fun `deleting the source above leaves the handle pointed at the right one`() {
        // The other way the slots shift: the source that was at index 1 is now at index 0.
        val a = shape("shape-a", x = 0.3f)
        val b = shape("shape-b", x = 0.3f)

        canvas(listOf(a, b)) { reports, select, reorder ->
            dragRotationHandle(centreX = 200f)
            reports.clear()

            reorder(listOf(b))
            select("shape-b")
            dragRotationHandle(centreX = 200f)

            assertTrue(reports.isNotEmpty())
            assertTrue(
                reports.all { it.first == "shape-b" },
                "got ${reports.map { it.first }.distinct()}",
            )
        }
    }

    @Test
    fun `an unmoved selection still turns the source it is on`() {
        val a = shape("shape-a", x = 0.3f)
        val b = shape("shape-b", x = 0.3f)

        canvas(listOf(a, b)) { reports, select, _ ->
            select("shape-b")
            dragRotationHandle(centreX = 200f)

            assertTrue(reports.isNotEmpty())
            assertTrue(reports.all { it.first == "shape-b" }, "got ${reports.map { it.first }.distinct()}")
        }
    }
}
