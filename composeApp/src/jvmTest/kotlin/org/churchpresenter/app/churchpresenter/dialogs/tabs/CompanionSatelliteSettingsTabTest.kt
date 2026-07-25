package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Companion Satellite settings tab starts empty, offering only "Add Connection". Adding one
 * reveals a connection card of fields and placement toggles that write back into [AppSettings].
 * These drive the add button, a field and the placement toggles and assert the settings produced,
 * rather than only that the empty tab renders (which never runs any of those lambdas).
 */
@OptIn(ExperimentalTestApi::class)
class CompanionSatelliteSettingsTabTest {

    private fun runTab(block: ComposeUiTest.(get: () -> AppSettings) -> Unit) = runComposeUiTest {
        var current = AppSettings()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                CompanionSatelliteSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                    viewModel = null,
                )
            }
        }
        block { current }
    }

    @Test
    fun `the add button appends another connection`() = runTab { get ->
        assertEquals(1, get().companionSatelliteConnections.size, "one connection by default")
        onNodeWithText("Add Connection", substring = true).performClick()

        val connections = get().companionSatelliteConnections
        assertEquals(2, connections.size, "the add button must append a connection")
        assertEquals("Companion", connections.last().name, "the new one carries the default name")
    }

    @Test
    fun `editing the name and toggling placements writes back into the connection`() = runTab { get ->
        // The default connection's card is already shown; its first text field is the name.
        onAllNodes(hasSetTextAction())[0].performTextReplacement("Booth deck")
        assertEquals("Booth deck", get().companionSatelliteConnections.single().name)

        // The placement checkboxes all start off; flipping every toggle turns them on.
        val toggles = onAllNodes(isToggleable()).fetchSemanticsNodes().size
        for (i in 0 until toggles) onAllNodes(isToggleable())[i].performClick()

        val c = get().companionSatelliteConnections.single()
        assertTrue(c.showInTab, "the show-in-tab placement must set showInTab")
        assertTrue(c.showInLeftSidebar, "the left-sidebar placement must set showInLeftSidebar")
        assertTrue(c.showInRightSidebar, "the right-sidebar placement must set showInRightSidebar")
    }
}
