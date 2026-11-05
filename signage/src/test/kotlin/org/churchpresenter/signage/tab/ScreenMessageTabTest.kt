@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.signage.core.Features
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A message written, changed and cleared in the Signage tab itself, over a paired screen. */
class ScreenMessageTabTest {
    private val world = SignageWorld()

    @AfterTest
    fun cleanUp() = world.close()

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitUntilGone(text: String) =
        waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    @Test
    fun `a message is written from the tab, changed, and cleared`() = signageTab(world) {
        runBlocking {
            world.controller.start()
            withTimeout(SignageWorld.WAIT_MS) { world.controller.state.first { it.ready } }
            world.pairScreen(features = listOf(Features.MESSAGE), load = false)
        }
        waitForText("Message")
        onNode(hasText("Message") and hasClickAction()).performClick()
        field(MESSAGE_TEXT_TAG).performTextInput("Doors open")
        onNodeWithText("Show").performClick()
        waitForText("Doors open")
        waitForText("Showing on 1 of 1 screens")
        assertEquals("Doors open", world.controller.state.value.settings.message?.text)
        onNodeWithText("Change").performClick()
        onNodeWithText("Message to screens").assertExists()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Clear").performClick()
        waitUntilGone("Doors open")
        assertNull(world.controller.state.value.settings.message)
    }
}
