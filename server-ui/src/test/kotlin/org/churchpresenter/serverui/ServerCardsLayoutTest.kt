@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.calendar.sync.RelayReply
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.RelayEndpoints
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerCardsLayoutTest {

    @Test
    fun `a trailing two-digit variant is split off the name, and nothing else is`() {
        assertEquals("Pastor" to "01", splitVariant("Pastor - 01"))
        assertEquals(
            "Church Presenter - Software Engineer" to "04",
            splitVariant("Church Presenter - Software Engineer - 04"),
        )
        assertEquals("Welcome" to "", splitVariant("Welcome"))
        assertEquals("Style - 1" to "", splitVariant("Style - 1"))
        assertEquals("Year - 2026" to "", splitVariant("Year - 2026"))
    }

    @Test
    fun `the upload size reads as minutes of HD video, rounded`() {
        assertEquals(5, hdVideoMinutes(700))
        assertEquals(0, hdVideoMinutes(50))
        assertEquals(1, hdVideoMinutes(140))
        assertEquals(0, hdVideoMinutes(0))
    }

    @Test
    fun `the upload size says how much HD video it holds, and follows what is typed`() = serverTab(
        initial = serverSettings { copy(fileUploadEnabled = true, maxMediaUploadMb = 700) },
    ) { get, _ ->
        onNodeWithText("≈ 5 min of HD video").assertExists()
        retypeField("700", "50")
        onNodeWithText("≈ under a minute of HD video").assertExists()
        assertEquals(50, get().serverSettings.maxMediaUploadMb)
    }

    @Test
    fun `the client tabs count their lists, and an unnamed device says so`() = serverTab(
        seedClients = {
            allowPermanently("one")
            allowPermanently("two")
            blockPermanently("three")
            setLabel("one", "Booth iPad")
        },
    ) { _, _ ->
        val allowed = onNode(hasClickAction() and hasText(ServerLabel.ALLOWED) and hasText("2"))
        val blocked = onNode(hasClickAction() and hasText(ServerLabel.BLOCKED) and hasText("1"))
        allowed.assertIsSelected()
        blocked.assertIsNotSelected()
        onNodeWithText("Booth iPad").assertExists()
        onNodeWithText("Unnamed device").assertExists("two has no name of its own")

        blocked.performClick()
        waitForIdle()
        blocked.assertIsSelected()
        allowed.assertIsNotSelected()
        onNodeWithText("three").assertExists()
        assertTrue(onAllNodesWithText("Booth iPad").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a wide page puts the calendar beside the server, a narrow one under it`() {
        val folder = Files.createTempDirectory("server-ui-columns").toFile()
        try {
            val sync = CalendarSyncService(
                folder = folder,
                songFolder = null,
                settings = { CalendarSyncSettings() },
                saveSettings = {},
                transport = object : RelayTransport {
                    override fun send(method: String, url: String, headers: Map<String, String>, body: String?) =
                        RelayReply(404, "{}")
                },
                endpoints = RelayEndpoints("https://relay.example", "https://relay.example/key"),
            )
            withIsolatedHome {
                runComposeUiTest {
                    var width by mutableStateOf(WIDE)
                    setContent {
                        MaterialTheme {
                            Box(Modifier.requiredWidth(width)) {
                                ServerSettingsTab(
                                    settings = AppSettings(),
                                    onSettingsChange = {},
                                    companionServer = CompanionServer(shutdownGraceMs = 0),
                                    remoteClientManager = RemoteClientManager(),
                                    calendarSync = sync,
                                )
                            }
                        }
                    }
                    fun left(text: String) = onNodeWithText(text).fetchSemanticsNode().positionInRoot.x
                    assertTrue(
                        left("Calendar sync") - left(ServerLabel.SECTION_SERVER) > COLUMN_OFFSET_PX,
                        "two columns: the calendar starts to the right of the server",
                    )
                    width = NARROW
                    waitForIdle()
                    fun top(text: String) = onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
                    // The calendar card's own header draws a slightly larger chip, so its title sits
                    // a few pixels further in -- nothing like a column's width.
                    assertTrue(
                        abs(left("Calendar sync") - left(ServerLabel.SECTION_SERVER)) < SAME_EDGE_PX,
                        "one column: both cards start at the same edge",
                    )
                    assertTrue(top("Calendar sync") > top(ServerLabel.SECTION_SERVER), "and the calendar comes after")
                }
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    private companion object {
        val WIDE: Dp = 1300.dp
        val NARROW: Dp = 900.dp
        const val COLUMN_OFFSET_PX = 300f
        const val SAME_EDGE_PX = 20f
    }
}
