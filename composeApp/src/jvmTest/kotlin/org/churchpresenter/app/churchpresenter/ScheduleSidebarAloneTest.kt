@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.InstanceLinkRole
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleSidebarAloneTest {

    @BeforeTest
    fun latch() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
    }

    private class Seen {
        var connects = 0
        var disconnects = 0
        val surfaces = mutableListOf<Pair<String, CompanionSurfacePlacement>>()
    }

    private fun sidebar(
        link: (Seen) -> InstanceLinkBridge,
        connections: List<CompanionSatelliteSettings> = emptyList(),
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                ScheduleSidebar(
                    modifier = Modifier.fillMaxSize(),
                    link = link(seen),
                    connections = connections,
                    schedule = { Text("the schedule") },
                    companionSurface = { connection, placement ->
                        seen.surfaces += connection.name to placement
                        Text("surface ${connection.name}")
                    },
                )
            }
        }
        waitForIdle()
        block(seen)
    }

    @Test
    fun `a controller that is connected says so and disconnects`() = sidebar(
        link = { seen ->
            InstanceLinkBridge(
                connectionStatus = InstanceLinkStatus.CONNECTED,
                followingHost = "10.0.0.9",
                role = InstanceLinkRole.CONTROLLER,
                onConnect = { seen.connects++ },
                onDisconnect = { seen.disconnects++ },
            )
        },
    ) { seen ->
        onNodeWithText("10.0.0.9", substring = true).assertExists()
        onNodeWithText("Disconnect").performClick()
        waitForIdle()
        assertEquals(1, seen.disconnects)
        assertEquals(0, seen.connects)
    }

    @Test
    fun `a link that is down offers Connect, and a primary shows its followers`() = sidebar(
        link = { seen ->
            InstanceLinkBridge(
                connectionStatus = InstanceLinkStatus.DISCONNECTED,
                followingHost = "10.0.0.9",
                followerCount = 2,
                onConnect = { seen.connects++ },
                onDisconnect = { seen.disconnects++ },
            )
        },
    ) { seen ->
        onNodeWithText("Connect").performClick()
        waitForIdle()
        assertEquals(1, seen.connects)
        assertEquals(0, seen.disconnects)
        onNodeWithText("2", substring = true).assertExists()
        onNodeWithText("the schedule").assertExists()
    }

    @Test
    fun `two left-sidebar surfaces get a chip each and the chosen one is drawn`() = sidebar(
        link = { InstanceLinkBridge() },
        connections = listOf(
            CompanionSatelliteSettings(id = "a", name = "Front", host = "10.0.0.1", showInLeftSidebar = true),
            CompanionSatelliteSettings(id = "b", name = "Back", host = "10.0.0.2", showInLeftSidebar = true),
            CompanionSatelliteSettings(id = "c", name = "Blank", host = "", showInLeftSidebar = true),
            CompanionSatelliteSettings(id = "d", name = "Right", host = "10.0.0.3", showInRightSidebar = true),
        ),
    ) { seen ->
        onNodeWithText("surface Front").assertExists()
        onNodeWithText("Back").performClick()
        waitForIdle()
        onNodeWithText("surface Back").assertExists()
        assertEquals(CompanionSurfacePlacement.LEFT_SIDEBAR, seen.surfaces.last().second)
        assertTrue(seen.surfaces.none { it.first == "Blank" || it.first == "Right" })
    }

    @Test
    fun `the left-sidebar surfaces follow the connections changing`() = runComposeUiTest {
        val front = CompanionSatelliteSettings(id = "a", name = "Front", host = "10.0.0.1", showInLeftSidebar = true)
        val back = CompanionSatelliteSettings(id = "b", name = "Back", host = "10.0.0.2", showInLeftSidebar = true)
        var connections by mutableStateOf(listOf(front, back))
        setContent {
            MaterialTheme {
                ScheduleSidebar(
                    modifier = Modifier.fillMaxSize(),
                    link = InstanceLinkBridge(),
                    connections = connections,
                    schedule = {},
                    companionSurface = { connection, _ -> Text("surface ${connection.name}") },
                )
            }
        }
        waitForIdle()
        onNodeWithText("Back").performClick()
        waitForIdle()
        connections = listOf(front)
        waitForIdle()
        onNodeWithText("surface Front").assertExists()
        onNodeWithText("surface Back").assertDoesNotExist()
        connections = emptyList()
        waitForIdle()
        onNodeWithText("surface Front").assertDoesNotExist()
    }
}
