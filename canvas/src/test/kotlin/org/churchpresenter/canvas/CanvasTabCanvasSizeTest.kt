package org.churchpresenter.canvas

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
import org.churchpresenter.settings.MergeTile
import org.churchpresenter.settings.OutputMerge
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
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

    @Test
    fun `two layers off the canvas are counted and both brought back`() {
        val alsoFar = offCanvas.copy(id = "src-far-2", name = "Also far")
        canvasTab(seed = { addScene("Scene"); addSource(offCanvas); addSource(alsoFar) }) { vm, _ ->
            assertTrue(onAllNodesWithText("2 layers are outside the canvas").fetchSemanticsNodes().isNotEmpty())

            onNodeWithTag(CANVAS_BRING_INTO_VIEW_TAG).performClick()
            waitForIdle()

            assertTrue(vm.currentScene!!.sources.all { it.transform.placement() == CanvasPlacement.INSIDE })
        }
    }

    @Test
    fun `the size menu offers each assigned screen and each merged picture`() {
        val wall = OutputProfile(
            id = "wall",
            name = "Wall",
            merge = OutputMerge(listOf(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 1920, 0))),
        )
        val ndi = ScreenAssignment(ndiWidth = 1920, ndiHeight = 1080, activeProfileId = "wall")
        val screens = listOf(
            ScreenAssignment(targetDisplay = 1, screenName = "Stage"),
            ScreenAssignment(targetDisplay = 2),
            ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE),
        )
        canvasTab(
            seed = { addScene("Wall scene") },
            settings = {
                it.copy(
                    projectionSettings = it.projectionSettings.copy(
                        screenAssignments = screens,
                        ndiOutputs = listOf(ndi, ndi),
                        outputProfiles = listOf(wall),
                    ),
                )
            },
        ) { vm, _ ->
            onNodeWithTag(CANVAS_SIZE_BUTTON_TAG).performClick()
            waitForIdle()
            assertTrue(onAllNodesWithText("Match Stage").fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodesWithText("Match Screen 2").fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodesWithText("Match Screen 3").fetchSemanticsNodes().isEmpty())

            clickCanvasLabel("Match Wall (merged)")

            val scene = vm.scenes.single()
            assertEquals(3840 to 1080, scene.canvasWidth to scene.canvasHeight)
        }
    }
}
