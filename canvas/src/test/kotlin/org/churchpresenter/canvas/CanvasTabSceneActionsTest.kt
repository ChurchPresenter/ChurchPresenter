@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.sharedui.testing.confirmColorDialogWith
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import androidx.compose.ui.test.click
import androidx.compose.ui.test.ComposeUiTest

class CanvasTabSceneActionsTest {

    /** Recolours the drawing toolbar's field showing [fromHex]: the toolbar does not scroll, so no `recolor`. */
    private fun ComposeUiTest.recolorTool(fromHex: String, toHex: String) {
        onAllNodes(hasClickAction() and hasText(fromHex, ignoreCase = true))[0].performClick()
        waitForIdle()
        confirmColorDialogWith(toHex)
    }

    @Test
    fun `go live puts the current scene on the output and records that it went live`() =
        canvasTab(seed = { addScene("Welcome") }) { vm, reports ->
            canvasButton(CanvasLabel.GO_LIVE).performClick()
            waitForIdle()

            assertEquals(listOf("Welcome"), reports.presented.map { it.name })
            val live = reports.wentLive.single() as ScheduleItem.SceneItem
            assertEquals(vm.currentScene?.id, live.sceneId)
            assertEquals("Welcome", live.sceneName)
        }

    @Test
    fun `save preset is offered only where the host can keep one`() {
        canvasTab(seed = { addScene("Welcome") }) { _, _ ->
            assertFalse(hasCanvasButton("Save preset"))
        }
        canvasTab(seed = { addScene("Welcome") }, offerSavePreset = true) { vm, reports ->
            canvasButton("Save preset").performClick()
            waitForIdle()

            assertEquals(listOf(vm.currentScene!!.id to "Welcome"), reports.presets)
        }
    }

    @Test
    fun `with no scene the tab offers to create one`() = canvasTab { vm, _ ->
        onNodeWithText("Create Scene").performClick()
        waitForIdle()

        assertEquals(1, vm.scenes.size)
    }

    @Test
    fun `a drawing tool brings up its stroke and fill colours, and the stroke can be changed`() =
        canvasTab(seed = { addScene("Welcome") }) { _, _ ->
            onNodeWithText("□").performClick()
            waitForIdle()

            onAllNodes(hasClickAction() and hasText("#FFFFFF", ignoreCase = true))[0].performClick()
            waitForIdle()
            confirmColorDialogWith("#FF0000")

            assertTrue(onAllNodes(hasText("#FF0000", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodes(hasText("#00000000", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty())
        }

    @Test
    fun `a shape drawn on the canvas takes the toolbar's colours and becomes a layer`() =
        canvasTab(seed = { addScene("Welcome") }) { vm, _ ->
            onNodeWithText("□").performClick()
            waitForIdle()
            recolorTool(fromHex = "#FFFFFF", toHex = "#FF0000")
            recolorTool(fromHex = "#00000000", toHex = "#00FF00")

            onNodeWithTag(SCENE_CANVAS_TAG).performMouseInput {
                moveTo(Offset(width * 0.2f, height * 0.2f))
                press()
                moveTo(Offset(width * 0.2f + 8f, height * 0.2f + 8f))
                moveTo(Offset(width * 0.6f, height * 0.6f))
                release()
            }
            waitForIdle()

            val shape = vm.currentScene!!.sources.single() as SceneSource.ShapeSource
            assertEquals("rectangle", shape.shapeType)
            assertEquals("#FF0000" to "#00FF00", shape.strokeColor to shape.fillColor)
        }

    @Test
    fun `clicking a layer on the canvas selects it`() =
        canvasTab(seed = {
            addScene("Welcome")
            val middle = SourceTransform(x = 0.25f, y = 0.25f, width = 0.5f, height = 0.5f)
            addSource(SceneSource.ShapeSource(id = "box", name = "Box", transform = middle))
            selectSource(null)
        }) { vm, _ ->
            onNodeWithTag(SCENE_CANVAS_TAG).performMouseInput { click(center) }
            waitForIdle()

            assertEquals("box", vm.selectedSourceId.value)
        }

    @Test
    fun `clicking a layer's name in the list selects it`() =
        canvasTab(seed = { addScene("Welcome"); seedSources("Title", "Logo"); selectSource(null) }) { vm, _ ->
            onAllNodesWithText("Logo")[0].performClick()
            waitForIdle()

            assertEquals("src-Logo", vm.selectedSourceId.value)
        }

    @Test
    fun `editing the selected layer in the properties panel changes it in the scene`() =
        canvasTab(seed = { addScene("Welcome"); seedSources("Title") }) { vm, _ ->
            fieldShowing("Title").performTextReplacement("Headline")
            waitForIdle()

            assertEquals(listOf("Headline"), vm.sourceNames())
        }
}
