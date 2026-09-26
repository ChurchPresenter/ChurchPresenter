package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import org.churchpresenter.core.models.scene.SceneAlternateLayout
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A scene edited as a landscape and a portrait layout side by side (#608).
 *
 * Asserted on the scene a real `SceneViewModel` ends up holding: whether it has a second layout, and
 * which layout a drag or a Bring into view changed.
 */
@OptIn(ExperimentalTestApi::class)
class CanvasTabDualLayoutTest {

    private val main = CANVAS_LAYOUT_TAG_PREFIX + "main"
    private val alternate = CANVAS_LAYOUT_TAG_PREFIX + "alternate"

    private val box = SceneSource.ShapeSource(
        id = "box",
        name = "Box",
        transform = SourceTransform(x = 0.25f, y = 0.25f, width = 0.5f, height = 0.5f),
    )

    private fun SceneViewModel.dualScene(alternateLayout: SceneAlternateLayout = SceneAlternateLayout()) {
        val scene = addScene("Both")
        addSource(box)
        setDualLayout(scene.id, true)
        alternateLayout.transforms.forEach { (id, t) -> updateTransform(id, t, alternate = true) }
    }

    private fun ComposeUiTest.canvasCount(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.openSizeMenuAndToggle() {
        onNodeWithTag(CANVAS_SIZE_BUTTON_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CANVAS_DUAL_LAYOUT_TAG).performClick()
        waitForIdle()
    }

    @Test
    fun `a scene has one canvas until Landscape and portrait is ticked`() {
        canvasTab(seed = { addScene("One"); addSource(box) }) { vm, _ ->
            assertEquals(0, canvasCount(main), "one layout is drawn on its own, uncaptioned")
            assertEquals(0, canvasCount(alternate))

            openSizeMenuAndToggle()

            assertNotNull(vm.currentScene?.alternate)
            assertEquals(1, canvasCount(main))
            assertEquals(1, canvasCount(alternate))
            assertTrue(onAllNodesWithText("Landscape 1920×1080").fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodesWithText("Portrait 1080×1920").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `unticking it asks first, and Remove goes back to one canvas`() {
        canvasTab(seed = { dualScene() }) { vm, _ ->
            openSizeMenuAndToggle()

            assertNotNull(vm.currentScene?.alternate, "nothing is lost before the answer")
            clickCanvasLabel("Remove")

            assertNull(vm.currentScene?.alternate)
            assertEquals(0, canvasCount(alternate))
        }
    }

    @Test
    fun `Cancel keeps both layouts`() {
        canvasTab(seed = { dualScene() }) { vm, _ ->
            openSizeMenuAndToggle()
            clickCanvasLabel("Cancel")

            assertNotNull(vm.currentScene?.alternate)
            assertEquals(1, canvasCount(alternate))
        }
    }

    @Test
    fun `dragging a layer in the portrait canvas leaves the landscape one alone`() {
        canvasTab(seed = { dualScene(); selectSource(box.id) }) { vm, _ ->
            onNodeWithTag(alternate).performMouseInput {
                moveTo(center)
                press()
                moveTo(center.copy(x = center.x + 10f))
                moveTo(center.copy(x = center.x + 20f))
                release()
            }
            waitForIdle()

            val scene = assertNotNull(vm.currentScene)
            assertEquals(box.transform, scene.sources.single().transform, "the landscape layout did not move")
            val moved = assertNotNull(scene.alternate?.transforms?.get(box.id), "the portrait layout took the move")
            assertNotEquals(box.transform.x, moved.x)
        }
    }

    @Test
    fun `dragging in the landscape canvas leaves the portrait one alone`() {
        canvasTab(seed = { dualScene(); selectSource(box.id) }) { vm, _ ->
            onNodeWithTag(main).performMouseInput {
                moveTo(center)
                press()
                moveTo(center.copy(x = center.x + 10f))
                moveTo(center.copy(x = center.x + 20f))
                release()
            }
            waitForIdle()

            val scene = assertNotNull(vm.currentScene)
            assertNotEquals(box.transform.x, scene.sources.single().transform.x, "the landscape layout took the move")
            assertEquals(emptyMap(), scene.alternate?.transforms, "the portrait layout did not move")
        }
    }

    @Test
    fun `a layer outside only the portrait canvas is flagged as such, and brought back there`() {
        val outside = SourceTransform(x = 1.5f, y = 0.4f, width = 0.2f, height = 0.2f)
        canvasTab(seed = { dualScene(SceneAlternateLayout(mapOf(box.id to outside))) }) { vm, _ ->
            assertTrue(
                onAllNodesWithContentDescription("Portrait: Outside the canvas — not visible")
                    .fetchSemanticsNodes().isNotEmpty(),
                "the row names the layout it is outside",
            )

            onNodeWithTag(CANVAS_BRING_INTO_VIEW_TAG).performClick()
            waitForIdle()

            val scene = assertNotNull(vm.currentScene)
            assertEquals(box.transform, scene.sources.single().transform, "the landscape layout was fine")
            val fixed = assertNotNull(scene.alternate?.transforms?.get(box.id))
            assertEquals(CanvasPlacement.INSIDE, fixed.placement(), "moved to $fixed")
        }
    }

    @Test
    fun `a layer outside only the landscape canvas is fixed there alone`() {
        val far = box.copy(transform = SourceTransform(x = 1.5f, y = 0.4f, width = 0.2f, height = 0.2f))
        val inside = SourceTransform(x = 0.1f, y = 0.1f, width = 0.2f, height = 0.2f)
        canvasTab(seed = {
            val scene = addScene("Both")
            addSource(far)
            setDualLayout(scene.id, true)
            updateTransform(far.id, inside, alternate = true)
        }) { vm, _ ->
            assertTrue(
                onAllNodesWithContentDescription("Landscape: Outside the canvas — not visible")
                    .fetchSemanticsNodes().isNotEmpty(),
            )

            onNodeWithTag(CANVAS_BRING_INTO_VIEW_TAG).performClick()
            waitForIdle()

            val scene = assertNotNull(vm.currentScene)
            assertEquals(CanvasPlacement.INSIDE, scene.sources.single().transform.placement())
            assertEquals(inside, scene.alternate?.transforms?.get(far.id), "the portrait layout was fine")
        }
    }
}
