@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.companionserver.CompanionServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the tab's headline control: the switch that actually starts and stops the companion server,
 * and the Restart button beside the port.
 *
 * These are the only tests in the suite that make the tab bind a socket. Every other class either
 * uses a stopped server or shares one started once for the class, precisely to avoid this cost — but
 * the switch cannot be driven any other way: it calls `companionServer.start(...)` directly, and the
 * settings write it performs happens in the same callback.
 *
 * **On the cost.** A start is ~600 ms and a stop ~1 s, so these tests run longer than the suite's
 * usual bar. That bar exists to keep tests from *waiting* — retry backoffs, idle windows, timeouts
 * used as a success path. Here the duration is the work itself: a real listener being bound and
 * unbound. Nothing here sleeps; the one wait is a bounded poll on `isRunning`, which ends on the
 * positive signal the server publishes and only ever fails by timing out. There are two such tests,
 * kept to the minimum that covers starting, stopping and restarting.
 */
class ServerSettingsTabLifecycleTest {

    private val servers = mutableListOf<CompanionServer>()

    /** Whatever a test leaves running is shut down here rather than leaking a bound port. */
    @AfterTest
    fun stopAll() {
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    private fun track(server: CompanionServer): CompanionServer = server.also { servers += it }

    /** Waits for [running] on the server's own signal, never on a fixed pause. */
    private fun awaitRunning(server: CompanionServer, running: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (server.isRunning.value != running && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertEquals(running, server.isRunning.value, "the server did not reach isRunning=$running in 10s")
    }

    @Test
    fun `the enable switch starts the server and stores the port, and switching off stops it`() {
        val server = track(CompanionServer())
        val port = freeServerPort()

        serverTab(initial = serverSettings { copy(port = port) }, server = server) { get, _ ->
            assertEquals(false, get().serverSettings.enabled, "the fixture starts disabled")
            serverSwitch(Switch.ENABLE).assertIsOff()
            onNodeWithText(ServerLabel.STOPPED).assertExists()

            serverSwitch(Switch.ENABLE).performScrollTo().performClick()
            awaitRunning(server, true)
            waitForIdle()

            assertEquals(true, get().serverSettings.enabled, "switching on must be stored")
            assertEquals(port, get().serverSettings.port, "along with the port it was started on")
            assertTrue(server.isRunning.value, "and the server must actually be listening")
            serverSwitch(Switch.ENABLE).assertIsOn()
            onNodeWithText(ServerLabel.RUNNING).assertExists("the status must follow")
            onNodeWithText(ServerLabel.SERVER_URL).assertExists("and the URL row must appear")

            serverSwitch(Switch.ENABLE).performClick()
            awaitRunning(server, false)
            waitForIdle()

            assertEquals(false, get().serverSettings.enabled, "switching off must be stored")
            serverSwitch(Switch.ENABLE).assertIsOff()
            onNodeWithText(ServerLabel.STOPPED).assertExists("and the status must follow back")
            onNodeWithText(ServerLabel.SERVER_URL).assertDoesNotExist() // taking the URL row with it
        }
    }

    /**
     * Restart stops and starts in one click. The port cannot be edited while the server is up — the
     * field is disabled — so it is set in the fixture, and what is asserted is that the server is
     * still listening on it afterwards and the tab still reads Running.
     */
    @Test
    fun `Restart cycles the server`() {
        val server = track(CompanionServer())
        val port = freeServerPort()

        serverTab(initial = serverSettings { copy(port = port) }, server = server) { _, _ ->
            serverSwitch(Switch.ENABLE).performScrollTo().performClick()
            awaitRunning(server, true)
            waitForIdle()
            val urlBefore = server.serverUrl.value
            assertTrue(urlBefore.endsWith(":$port"), "fixture: it must be up on the chosen port")

            onNodeWithText(ServerLabel.RESTART).performScrollTo().performClick()
            awaitRunning(server, true)
            waitForIdle()

            assertTrue(server.isRunning.value, "Restart must leave the server listening")
            assertEquals(urlBefore, server.serverUrl.value, "on the same port it was given")
            onNodeWithText(ServerLabel.RUNNING).assertExists("and the tab must still read Running")
        }
    }
}
