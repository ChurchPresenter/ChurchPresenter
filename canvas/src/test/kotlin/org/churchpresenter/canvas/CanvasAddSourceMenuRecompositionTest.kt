@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SCENE_CHANGES = 4

class CanvasAddSourceMenuRecompositionTest {

    private fun ComposeUiTest.pickLast(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    @Test
    fun `the open menu survives the scene changing under it, then adds what is picked`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            canvasButton(CanvasLabel.ADD_SOURCE).performClick()
            waitForIdle()
            vm.seedSources("Under")
            waitForIdle()
            vm.seedSources("Again")
            waitForIdle()
            pickLast(CanvasLabel.COLOR)

            assertEquals(3, vm.sourceNames().size)
        }

    @Test
    fun `every item in the menu is drawn with the scene changing under it`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            canvasButton(CanvasLabel.ADD_SOURCE).performClick()
            waitForIdle()
            repeat(SCENE_CHANGES) { i ->
                vm.seedSources("s$i")
                waitForIdle()
            }
            pickLast("Image")

            assertEquals(SCENE_CHANGES + 1, vm.sourceNames().size)
        }
}
