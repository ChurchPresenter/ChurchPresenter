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

class CanvasTabSceneActionsTest {

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
}
