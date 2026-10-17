@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.churchpresenter.calendar.sync.RelayReply
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.profiles.LocalSettingsDevMode
import org.churchpresenter.server.CalendarJoin
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.RelayEndpoints
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarSyncCardComputersTest {

    private val folder: File = Files.createTempDirectory("calendar-sync-computers").toFile()
    private var settings = CalendarSyncSettings(enabled = true, relayUrl = "https://relay.example")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    /** A relay that registers this computer as the main one, with one follower that has joined. */
    private inner class Relay : RelayTransport {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private var registered = false
        var role = "main"
        var mainLastSeen = ""

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
                    """{"rev":4,"records":[],"tombstones":[],"devices":[],"lastDesktopInstall":"","role":"$role",""" +
                        """"mainLastSeen":"$mainLastSeen",""" +
                        """"desktops":[{"id":"office-1","role":"follower","nameBox":"",""" +
                        """"pairedAt":"2026-09-20T00:00:00Z","lastSeen":"2026-09-20T10:30:00Z"}]}""",
                )
                path == "state" -> RelayReply(200, """{"rev":5}""")
                path.startsWith("desktops/") -> RelayReply(200, "{}")
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
        multiComputer = { true },
    )

    private fun heard(prefix: String) = synchronized(relay.calls) { relay.calls.any { it.startsWith(prefix) } }

    @Test
    fun `in dev mode the card lists the computers and reaches the service for each of them`() {
        val sync = service()
        runBlocking { sync.syncOnStartup() }
        assertTrue(settings.isPaired)

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalSettingsDevMode provides true,
                        LocalCalendarDialogInPlace provides true,
                    ) {
                        CalendarSyncCard(
                            settings = AppSettings(calendarSync = settings),
                            onSettingsChange = {},
                            sync = sync,
                        )
                    }
                }
            }
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodes(hasText("office-1")).fetchSemanticsNodes().isNotEmpty()
            }

            // Adding one: the dialog, drawn in place, makes a code through the service.
            onNodeWithText("Add a computer…").performClick()
            onNode(hasSetTextAction()).performTextInput("Office")
            onNodeWithText("Create join code").performClick()
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodes(hasText("Join code for Office", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(heard("PUT https://relay.example/i/"))
            onNodeWithText("Done").performClick()
            waitForIdle()
            onNodeWithText("Create join code").assertDoesNotExist()

            onNodeWithText("Join another…").performClick()
            onNodeWithText("This computer stops syncing its own calendar.").assertExists()
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            onNodeWithText("Paste").assertDoesNotExist()

            // Last: once another computer is the main one, this card stops listing computers.
            onNodeWithText("Make main").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { heard("POST https://relay.example/i/") }
        }
    }

    @Test
    fun `removing a computer reaches the relay`() {
        val sync = service()
        runBlocking { sync.syncOnStartup() }
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalSettingsDevMode provides true) {
                        CalendarSyncCard(
                            settings = AppSettings(calendarSync = settings),
                            onSettingsChange = {},
                            sync = sync,
                        )
                    }
                }
            }
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodes(hasText("office-1")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Remove").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { heard("DELETE https://relay.example/i/") }
        }
    }

    @Test
    fun `a join file is written to and read back from where the chooser pointed`() = runBlocking {
        val join = CalendarJoin(
            relay = "https://relay.example",
            instance = "inst-1",
            desktop = "desk-2",
            token = "t".repeat(32),
            key = "A".repeat(43),
            role = CalendarSyncSettings.ROLE_VIEWER,
            expires = Instant.now().toString(),
        )
        val file = File(folder, CalendarJoin.FILE_NAME)
        assertEquals(folder.path, writeJoin(file, join))
        val (text, read) = readJoin(file)!!
        assertEquals(file, read)
        assertEquals(join.toJson(), text)
        assertNull(readJoin(File(folder, "missing.cpjoin")))
        assertNull(writeJoin(File(File(folder, "no-such-dir"), "x.cpjoin"), join))
    }

    @Test
    fun `a follower whose main computer has been silent a week can take over`() {
        relay.role = CalendarSyncSettings.ROLE_FOLLOWER
        relay.mainLastSeen = Instant.now().minusSeconds(DAYS_8).toString()
        settings = settings.copy(
            instanceId = "inst-1",
            instanceKey = "A".repeat(43),
            role = CalendarSyncSettings.ROLE_FOLLOWER,
            computerId = "me-1",
            computerToken = "t".repeat(32),
            mainName = "Sanctuary",
        )
        val sync = service()
        runBlocking { sync.syncNow() }
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalSettingsDevMode provides true) {
                        CalendarSyncCard(
                            settings = AppSettings(calendarSync = settings),
                            onSettingsChange = {},
                            sync = sync,
                        )
                    }
                }
            }
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodes(hasText("Make this the main computer")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Make this the main computer").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { heard("POST https://relay.example/i/") }
        }
    }

    @Test
    fun `a pasted code is handed to the service to join`() {
        val sync = service()
        runBlocking { sync.syncOnStartup() }
        val code = CalendarJoin(
            relay = "https://relay.example",
            instance = "inst-9",
            desktop = "desk-9",
            token = "t".repeat(32),
            key = "A".repeat(43),
            role = CalendarSyncSettings.ROLE_FOLLOWER,
            expires = Instant.now().plusSeconds(DAYS_8).toString(),
        ).toCode()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalSettingsDevMode provides true,
                        LocalCalendarDialogInPlace provides true,
                    ) {
                        CalendarSyncCard(
                            settings = AppSettings(calendarSync = settings),
                            onSettingsChange = {},
                            sync = sync,
                        )
                    }
                }
            }
            onNodeWithText("Join another…").performClick()
            onNode(hasSetTextAction()).performTextInput(code)
            onNodeWithText("Join").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { heard("GET https://relay.example/i/inst-9/") }
        }
    }

    private companion object {
        const val CLIENT_KEY_URL = "https://keys.example/k3v9q"

        /** Only there to fail the test: each wait ends as soon as the relay has heard the call. */
        const val WAIT_MS = 5_000L
        const val DAYS_8 = 8 * 86_400L
    }
}
