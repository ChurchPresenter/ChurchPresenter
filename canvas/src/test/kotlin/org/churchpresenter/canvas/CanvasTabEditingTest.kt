@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.doubleClick
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasTabEditingTest {

    @Test
    fun `delete removes the selected layer`() = canvasTab(seed = {
        addScene("Welcome")
        seedSources("Title", "Logo")
        selectSource("src-Logo")
    }) { vm, _ ->
        onNode(isFocused()).performKeyInput { pressKey(Key.Delete) }
        waitForIdle()

        assertEquals(listOf("Title"), vm.sourceNames())
    }

    @Test
    fun `delete with no layer selected removes nothing`() = canvasTab(seed = {
        addScene("Welcome")
        seedSources("Title")
        selectSource("src-Title")
    }) { vm, _ ->
        vm.selectSource(null)
        waitForIdle()
        onNode(isFocused()).performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()

        assertEquals(listOf("Title"), vm.sourceNames())
    }

    @Test
    fun `a scene shaped unlike the output is flagged, and Fix gives it the output's shape`() =
        canvasTab(seed = {
            addScene("Old projector")
            updateCanvasSize(1024, 768)
        }) { vm, _ ->
            onNodeWithText("Scene is 4:3 but display is 16:9").assertExists()

            onNodeWithText("Fix").performClick()
            waitForIdle()

            assertEquals(1920, vm.currentScene?.canvasWidth)
            assertEquals(1080, vm.currentScene?.canvasHeight)
        }

    @Test
    fun `a two-layout scene is fixed through the layout the output draws`() =
        canvasTab(seed = {
            val scene = addScene("Both ways")
            updateCanvasSize(768, 1024)
            setDualLayout(scene.id, true)
        }) { vm, _ ->
            onNodeWithText("Fix").performClick()
            waitForIdle()

            assertEquals(1080, vm.currentScene?.canvasWidth, "the main canvas is the output turned sideways")
            assertEquals(1920, vm.currentScene?.canvasHeight)
        }

    @Test
    fun `several layers off the canvas are counted, and brought back together`() =
        canvasTab(seed = {
            addScene("Welcome")
            addSource(SceneSource.TextSource(id = "a", name = "A", transform = SourceTransform(x = 1.5f)))
            addSource(SceneSource.TextSource(id = "b", name = "B", transform = SourceTransform(y = -2f)))
        }) { vm, _ ->
            onNodeWithText("2 layers are outside the canvas").assertExists()

            onNodeWithTag(CANVAS_BRING_INTO_VIEW_TAG).performClick()
            waitForIdle()

            val stillOutside = onAllNodesWithText("layers are outside the canvas", substring = true)
            assertEquals(0, stillOutside.fetchSemanticsNodes().size)
            vm.currentScene!!.sources.forEach { source ->
                val t = source.transform
                assert(t.x >= 0f && t.x + t.width <= 1f && t.y >= 0f && t.y + t.height <= 1f) { "${source.name} at $t" }
            }
        }

    @Test
    fun `double-clicking a scene's name starts renaming it`() =
        canvasTab(seed = { addScene("Welcome") }) { _, _ ->
            onNodeWithText("Welcome").performMouseInput { doubleClick() }
            waitForIdle()

            onNodeWithContentDescription("Confirm rename").assertExists()
        }
}
