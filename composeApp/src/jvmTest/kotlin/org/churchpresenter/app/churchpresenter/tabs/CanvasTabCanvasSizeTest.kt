package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scene row's canvas-size menu, and the off-canvas warning beside it (#608).
 *
 * Asserted on the scene graph that results, through a real `SceneViewModel`: the size a scene ends
 * up with, and where a layer ends up after Bring into view.
 */
@OptIn(ExperimentalTestApi::class)
class CanvasTabCanvasSizeTest {

    private val offCanvas = SceneSource.TextSource(
        id = "src-far",
        name = "Far",
        transform = SourceTransform(x = 1.5f, y = 0.4f, width = 0.2f, height = 0.2f),
    )

    @Test
    fun `picking a preset resizes that scene`() {
        canvasTab(seed = { addScene("Portrait") }) { vm, _ ->
            onNodeWithTag(CANVAS_SIZE_BUTTON_TAG).performClick()
            waitForIdle()
            clickCanvasLabel("9:16")

            val scene = vm.scenes.single()
            assertEquals(1080 to 1920, scene.canvasWidth to scene.canvasHeight)
        }
    }

    @Test
    fun `a custom size is applied with Set`() {
        canvasTab(seed = { addScene("Custom") }) { vm, _ ->
            onNodeWithTag(CANVAS_SIZE_BUTTON_TAG).performClick()
            waitForIdle()
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(CANVAS_SIZE_WIDTH_TAG)))
                .performTextReplacement("1280")
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(CANVAS_SIZE_HEIGHT_TAG)))
                .performTextReplacement("720")
            onNodeWithTag(CANVAS_SIZE_SET_TAG).performClick()
            waitForIdle()

            val scene = vm.scenes.single()
            assertEquals(1280 to 720, scene.canvasWidth to scene.canvasHeight)
        }
    }

    @Test
    fun `a layer off the canvas is flagged on its row and in a banner`() {
        canvasTab(seed = { addScene("Scene"); addSource(offCanvas) }) { _, _ ->
            assertTrue(
                onAllNodesWithContentDescription("Outside the canvas — not visible")
                    .fetchSemanticsNodes().isNotEmpty(),
                "the layer's row carries the warning",
            )
            assertTrue(
                onAllNodesWithText("1 layer is outside the canvas").fetchSemanticsNodes().isNotEmpty(),
                "and the banner counts it",
            )
        }
    }

    @Test
    fun `Bring into view puts the layer back inside and clears the banner`() {
        canvasTab(seed = { addScene("Scene"); addSource(offCanvas) }) { vm, _ ->
            onNodeWithTag(CANVAS_BRING_INTO_VIEW_TAG).performClick()
            waitForIdle()

            val moved = vm.currentScene!!.sources.single().transform
            assertEquals(CanvasPlacement.INSIDE, moved.placement(), "moved to $moved")
            assertEquals(0.4f, moved.y, "only the axis it crossed is changed")
            assertTrue(
                onAllNodesWithText("1 layer is outside the canvas")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
                "the banner is gone once nothing is outside",
            )
        }
    }
}
