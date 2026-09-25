@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.dialogs.CalendarInviteFailedContent
import org.churchpresenter.app.churchpresenter.server.CalendarSyncService
import org.churchpresenter.app.churchpresenter.server.RelayEndpoints
import org.churchpresenter.app.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.calendar.sync.RelayReply
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Calendar on phones" card wired to a real [CalendarSyncService]: what it shows comes from the
 * service's own flows, and its buttons reach the service.
 *
 * Its states as pictures are `CalendarSyncCardScreenshotTest`; this is the wiring between the two.
 */
class CalendarSyncCardTest {

    private val folder: File = Files.createTempDirectory("calendar-sync-card").toFile()
    private var settings = CalendarSyncSettings(enabled = true, relayUrl = "https://relay.example")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    /** A relay that registers, answers a round, and holds one enrolled phone. */
    private inner class Relay : RelayTransport {
        val calls = mutableListOf<String>()
        var registered = false

        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply {
            calls += "$method $url"
            if (url == CLIENT_KEY_URL) return RelayReply(200, """{"clientKey":"key-1"}""")
            val path = url.substringAfter("/i/").substringAfter("/").substringBefore("?")
            return when {
                path == "register" && !registered -> {
                    registered = true
                    RelayReply(201, """{"desktopToken":"tok"}""")
                }
                path == "changes" -> RelayReply(
                    200,
                    """{"rev":4,"records":[],"tombstones":[],"devices":[{"id":"phone-1","nameBox":"",""" +
                        """"pairedAt":"2026-09-20T00:00:00Z","lastSeen":"2026-09-20T10:30:00Z"}],""" +
                        """"lastDesktopInstall":""}""",
                )
                path == "state" -> RelayReply(200, """{"rev":5}""")
                path.startsWith("devices/") && method == "DELETE" -> RelayReply(200, "{}")
                else -> RelayReply(404, "{}")
            }
        }
    }

    private val relay = Relay()

    private fun service() = CalendarSyncService(
        folder = folder,
        songFolder = null,
        settings = { settings },
        saveSettings = { settings = it },
        transport = relay,
        endpoints = RelayEndpoints("https://relay.example", CLIENT_KEY_URL),
    )

    @Test
    fun `the card shows what the service reports and its buttons reach it`() {
        val sync = service()
        // A paired desktop that has just synced: the buttons are drawn and a phone is listed.
        runBlocking { sync.syncOnStartup() }
        assertTrue(settings.isPaired)

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CalendarSyncCard(
                        settings = AppSettings(calendarSync = settings),
                        onSettingsChange = {},
                        sync = sync,
                        labelFor = { id -> if (id == "phone-1") "Anna" else "" },
                    )
                }
            }
            waitForIdle()
            onAllNodesWithText("Anna", substring = true).onFirst()
                .assertExists("the operator's own label for the phone, from the Remote Clients card")

            onAllNodesWithText("Sync now")[0].performClick()
            waitForIdle()
            onAllNodesWithText("Revoke")[0].performClick()
            waitForIdle()
        }

        assertTrue(relay.calls.any { it.startsWith("DELETE") }, "Revoke reaches the relay through the service")
        assertTrue(relay.calls.count { it.contains("/changes") } >= 2, "Sync now runs another round")
    }

    @Test
    fun `unpairing through the card forgets the pairing`() {
        val sync = service()
        runBlocking { sync.syncOnStartup() }

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CalendarSyncCard(
                        settings = AppSettings(calendarSync = settings),
                        onSettingsChange = {},
                        sync = sync,
                    )
                }
            }
            waitForIdle()
            onAllNodesWithText("Start over with a new key")[0].performClick()
            waitForIdle()
        }

        assertTrue(settings.desktopToken.isEmpty())
        assertEquals(CalendarSyncStatus.Unpaired, sync.status.value)
    }

    @Test
    fun `a device with no name of its own is listed by whatever there is`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CalendarSyncCardContent(
                        settings = AppSettings(
                            calendarSync = CalendarSyncSettings(
                                enabled = true,
                                instanceId = "inst-1",
                                desktopToken = "tok",
                                instanceKey = "k".repeat(43),
                            ),
                        ),
                        onSettingsChange = {},
                        status = CalendarSyncStatus.Unpaired,
                        devices = listOf(PairedDevice("phone-9", "", "", "")),
                        labelFor = { "" },
                        onSyncNow = {},
                        onUnpair = {},
                        onRevoke = {},
                        zone = ZoneId.of("UTC"),
                        locale = Locale.US,
                    )
                }
            }
            // Neither a label nor a name it calls itself: its id is what is left to show, and a
            // device that has never checked in draws no "last seen" line under it.
            onAllNodesWithText("phone-9")[0].assertExists()
        }
    }

    @Test
    fun `the invite dialog says why there is no QR`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CalendarInviteFailedContent(
                        status = CalendarSyncStatus.Failed("connection refused"),
                        onDismiss = {},
                    )
                }
            }
            onAllNodesWithText("connection refused", substring = true)[0]
                .assertExists("the reason is the sync card's own words for it")
        }
    }

    @Test
    fun `a time the relay wrote is shown as a local time, and anything else as it came`() {
        val clock = LocalTimeText(ZoneId.of("UTC"), Locale.US)

        // Not compared to a literal: the locale's own separator before AM is a narrow no-break space.
        val formatted = clock.format("2026-09-20T10:30:00Z")
        assertTrue(formatted.startsWith("10:30") && formatted.endsWith("AM"), formatted)
        assertEquals("never", clock.format("never"))
        assertTrue(LocalTimeText.system().format("nonsense") == "nonsense")
    }
}

private const val CLIENT_KEY_URL = "https://keys.example/k3v9q"
