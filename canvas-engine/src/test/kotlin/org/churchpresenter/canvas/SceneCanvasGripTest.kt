@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.DEFAULT_CROP
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The selected layer's grip: drawn above every layer, so a layer another one covers can still be
 * moved once it is selected (#937), and, holding Option, a picture set to Fill pans inside its box.
 *
 * Same 400 x 225 canvas as `SceneCanvasSelectionTest`.
 */
class SceneCanvasGripTest {

    private val canvasTag = "scene-canvas"

    private class Reports {
        val selected = mutableListOf<String?>()
        val transforms = mutableListOf<Pair<String, SourceTransform>>()
    }

    /** A picture over the left half of the canvas, set to [scale]. */
    private fun picture(scale: String = "FIT") = SceneSource.ImageSource(
        id = "picture",
        name = "Picture",
        transform = SourceTransform(x = 0f, y = 0f, width = 0.5f, height = 1f),
        filePath = "/missing/picture.png",
        contentScale = scale,
    )

    /** A shape covering the whole canvas, in front of everything listed before it. */
    private val cover = SceneSource.ShapeSource(
        id = "cover",
        name = "Cover",
        transform = SourceTransform(),
        shapeType = "rectangle",
        strokeColor = "#FFFFFF",
        fillColor = "#80000000",
        strokeWidth = 2f,
    )

    private fun canvas(sources: List<SceneSource>, selectedId: String?, block: ComposeUiTest.(Reports) -> Unit) {
        val reports = Reports()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.width(400.dp)) {
                        SceneCanvas(
                            modifier = Modifier.testTag(canvasTag),
                            scene = Scene(
                                id = "s",
                                name = "S",
                                canvasWidth = 1920,
                                canvasHeight = 1080,
                                sources = sources,
                            ),
                            selectedSourceId = selectedId,
                            onSourceSelected = { reports.selected += it },
                            onTransformChanged = { id, t -> reports.transforms += id to t },
                            activeTool = "select",
                        )
                    }
                }
            }
            block(reports)
        }
    }

    private fun ComposeUiTest.dragBy(fromX: Float, fromY: Float, dx: Float, dy: Float) {
        onNodeWithTag(canvasTag).performMouseInput {
            moveTo(Offset(fromX, fromY))
            press()
            moveTo(Offset(fromX + dx / 2f, fromY + dy / 2f))
            moveTo(Offset(fromX + dx, fromY + dy))
            release()
        }
        waitForIdle()
    }

    @Test
    fun `a selected layer under another one still moves when dragged`() =
        canvas(listOf(picture(), cover), selectedId = "picture") { reports ->
            dragBy(fromX = 100f, fromY = 110f, dx = 60f, dy = 0f)

            val (id, moved) = reports.transforms.last()
            assertEquals("picture", id, "the selected layer moves, not the one covering it")
            assertTrue(moved.x > 0.05f, "it moved right, x was ${moved.x}")
            assertTrue(reports.selected.isEmpty(), "and the press did not select the cover instead")
        }

    @Test
    fun `holding Option pans a Fill picture inside its box instead of moving it`() =
        canvas(listOf(picture(scale = "FILL")), selectedId = "picture") { reports ->
            onNodeWithTag(canvasTag).performKeyInput { keyDown(Key.AltLeft) }
            dragBy(fromX = 100f, fromY = 110f, dx = 50f, dy = 0f)
            onNodeWithTag(canvasTag).performKeyInput { keyUp(Key.AltLeft) }

            val moved = reports.transforms.last().second
            assertEquals(0f, moved.x, "the box stays where it is")
            assertTrue(moved.cropX < DEFAULT_CROP, "dragging right shows more of the left, crop was ${moved.cropX}")
        }

    @Test
    fun `Option does nothing special to a picture that fits its box`() =
        canvas(listOf(picture(scale = "FIT")), selectedId = "picture") { reports ->
            onNodeWithTag(canvasTag).performKeyInput { keyDown(Key.AltLeft) }
            dragBy(fromX = 100f, fromY = 110f, dx = 50f, dy = 0f)
            onNodeWithTag(canvasTag).performKeyInput { keyUp(Key.AltLeft) }

            val moved = reports.transforms.last().second
            assertTrue(moved.x > 0.05f, "a Fit picture has nothing to pan, so the box moves")
            assertEquals(DEFAULT_CROP, moved.cropX)
        }

    @Test
    fun `a drag across the whole box pans the crop all the way, and no further`() {
        val box = SourceTransform(width = 0.5f, height = 1f)
        assertEquals(0.25f, box.pannedBy(dx = 50f, dy = 0f, canvasWidth = 400f, canvasHeight = 225f).cropX)
        assertEquals(0f, box.pannedBy(dx = 1000f, dy = 0f, canvasWidth = 400f, canvasHeight = 225f).cropX)
        assertEquals(1f, box.pannedBy(dx = 0f, dy = -1000f, canvasWidth = 400f, canvasHeight = 225f).cropY)
        assertEquals(box, box.pannedBy(dx = 10f, dy = 10f, canvasWidth = 0f, canvasHeight = 0f))
    }
}
