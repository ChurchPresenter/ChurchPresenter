@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Go Live key on the Canvas tab: Enter on the tab root puts the current scene on the output and
 * records it, as the Go Live button does; Enter in a text field, or while a scene is being renamed,
 * does not.
 */
class CanvasGoLiveKeyTest {

    private fun ComposeUiTest.pressEnter() {
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    @Test
    fun `enter on the tab root puts the current scene live`() =
        canvasTab(seed = { addScene("Welcome") }) { vm, reports ->
            waitForIdle()

            pressEnter()

            assertEquals(listOf("Welcome"), reports.presented.map { it.name })
            val live = reports.wentLive.single() as ScheduleItem.SceneItem
            assertEquals(vm.currentScene?.id, live.sceneId)
        }

    @Test
    fun `with no scene enter puts nothing live`() = canvasTab { _, reports ->
        waitForIdle()

        pressEnter()

        assertTrue(reports.presented.isEmpty(), "no scene to present: ${reports.presented}")
        assertTrue(reports.wentLive.isEmpty())
    }

    @Test
    fun `enter typed in a source's text field does not go live`() = canvasTab(seed = {
        addScene("Welcome")
        addSource(SceneSource.TextSource(id = "t", name = "Title"))
        selectSource("t")
    }) { _, reports ->
        val field = onAllNodes(hasSetTextAction())[0]
        field.requestFocus()

        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(reports.presented.isEmpty(), "the field kept the key: ${reports.presented}")
    }

    @Test
    fun `enter while a scene is being renamed does not go live`() =
        canvasTab(seed = { addScene("Welcome") }) { _, reports ->
            onNodeWithText("Welcome").performMouseInput { doubleClick() }
            waitForIdle()
            onNodeWithContentDescription("Confirm rename").assertExists()

            pressEnter()
            onAllNodes(hasSetTextAction())[0].requestFocus()
            onAllNodes(hasSetTextAction())[0].performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            assertTrue(reports.presented.isEmpty(), "renaming, not going live: ${reports.presented}")
        }
}
