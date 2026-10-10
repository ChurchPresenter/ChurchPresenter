@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleSidebarSurfacesTest {

    @BeforeTest
    fun latch() {
        TestSingletons.latchSkikoHostOs()
    }

    @Test
    fun `two left-sidebar surfaces get a chip each and the chosen one is drawn`() = runComposeUiTest {
        val drawn = mutableListOf<Pair<String, CompanionSurfacePlacement>>()
        val connections = listOf(
            CompanionSatelliteSettings(id = "a", name = "Front", host = "10.0.0.1", showInLeftSidebar = true),
            CompanionSatelliteSettings(id = "b", name = "Back", host = "10.0.0.2", showInLeftSidebar = true),
            CompanionSatelliteSettings(id = "c", name = "Right", host = "10.0.0.3", showInRightSidebar = true),
        )
        setContent {
            MaterialTheme {
                ScheduleSidebar(
                    modifier = Modifier,
                    link = InstanceLinkBridge(),
                    connections = connections,
                    schedule = { Text("schedule") },
                    companionSurface = { connection, placement ->
                        drawn += connection.name to placement
                        Text("surface ${connection.name}")
                    },
                )
            }
        }
        waitForIdle()
        assertEquals("Front" to CompanionSurfacePlacement.LEFT_SIDEBAR, drawn.last())
        onNodeWithText("Back").performClick()
        waitForIdle()
        onNodeWithText("surface Back").assertExists()
        assertTrue(drawn.none { it.first == "Right" }, "a right-sidebar surface is not drawn on the left")
    }
}
