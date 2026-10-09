@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.calendar.sync.RelayReply
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.server.RelayEndpoints
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import java.nio.file.Files
import kotlin.test.Test

/**
 * The states the other suites never reach: the link row while connecting, failed or idle, the
 * calendar card in every status it can report, and the Server tab carrying the calendar card.
 */
class ServerUiStatesTest {

    @Test
    fun `the link row names every state, a failure by its own words when it has some`() = runComposeUiTest {
        var status by mutableStateOf(InstanceLinkStatus.CONNECTING)
        var error by mutableStateOf<String?>(null)
        setContent { MaterialTheme { ConnectionStatusRow(status = status, errorLabel = error) } }
        onNodeWithText("Connecting…").assertExists()
        status = InstanceLinkStatus.DISCONNECTED
        waitForIdle()
        onNodeWithText("Disconnected").assertExists()
        status = InstanceLinkStatus.CONNECTED
        waitForIdle()
        onNodeWithText("Connected").assertExists()
        status = InstanceLinkStatus.ERROR
        waitForIdle()
        onNodeWithText("Connection failed — retrying…").assertExists()
        error = "Link lost"
        waitForIdle()
        onNodeWithText("Link lost").assertExists()
    }

    @Test
    fun `the calendar card says what every status means`() = runComposeUiTest {
        var status by mutableStateOf<CalendarSyncStatus>(CalendarSyncStatus.Off)
        setContent {
            MaterialTheme {
                CalendarSyncCardContent(
                    settings = AppSettings(
                        calendarSync = CalendarSyncSettings(
                            enabled = true, instanceId = "inst-1", desktopToken = "tok", instanceKey = "k".repeat(43),
                        ),
                    ),
                    onSettingsChange = {},
                    status = status,
                    devices = listOf(PairedDevice("phone-9", "", "", "")),
                    labelFor = { "" },
                    onSyncNow = {},
                    onUnpair = {},
                    onRevoke = {},
                )
            }
        }
        for (each in listOf(
            CalendarSyncStatus.Syncing,
            CalendarSyncStatus.Failed("offline"),
            CalendarSyncStatus.TimedOut,
            CalendarSyncStatus.Unauthorized,
            CalendarSyncStatus.OtherDesktop("other"),
        )) {
            status = each
            waitForIdle()
        }
        onNodeWithText("Another computer is syncing this calendar", substring = true).assertExists()
    }

    @Test
    fun `the Server tab carries the calendar card when the app has calendar sync`() {
        val folder = Files.createTempDirectory("server-ui-calendar").toFile()
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
            serverTab(calendarSync = sync, builtInRelayUrl = "https://relay.built-in") { _, _ ->
                onNodeWithText("Calendar on phones & tablets").performScrollTo().assertExists()
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `with no relay of its own the card names the built-in one, and its own when it has one`() = runComposeUiTest {
        var relay by mutableStateOf("")
        setContent {
            MaterialTheme {
                CalendarSyncCardContent(
                    settings = AppSettings(
                        calendarSync = CalendarSyncSettings(
                            enabled = true, relayUrl = relay, instanceId = "inst-1", desktopToken = "tok",
                            instanceKey = "k".repeat(43),
                        ),
                    ),
                    onSettingsChange = {},
                    status = CalendarSyncStatus.Unpaired,
                    devices = emptyList(),
                    labelFor = { "" },
                    onSyncNow = {},
                    onUnpair = {},
                    onRevoke = {},
                    builtInRelayUrl = "https://relay.built-in",
                )
            }
        }
        onNodeWithText("https://relay.built-in", substring = true).assertExists()
        relay = "https://relay.own"
        waitForIdle()
        onNodeWithText("https://relay.own", substring = true).assertExists()
        onNodeWithText("https://relay.built-in", substring = true).assertDoesNotExist()
    }
}
