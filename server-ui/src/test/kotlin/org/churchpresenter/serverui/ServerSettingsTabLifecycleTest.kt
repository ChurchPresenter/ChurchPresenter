@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.churchpresenter.server.CompanionServer
import java.util.concurrent.Executors
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
 * the switch cannot be driven any other way: it asks `companionServer.requestStart(...)` for a start,
 * and the settings write it performs happens in the same callback.
 *
 * **On the cost.** A start is ~600 ms and a stop ~1 s, so these tests run longer than the suite's
 * usual bar. That bar exists to keep tests from *waiting* — retry backoffs, idle windows, timeouts
 * used as a success path. Here the duration is the work itself: a real listener being bound and
 * unbound. Nothing here sleeps: the requests run on a single-thread dispatcher the test hands the
 * server, and [settle] waits for everything queued on it to have run. There are two such tests, kept
 * to the minimum that covers starting, stopping and restarting.
 */
class ServerSettingsTabLifecycleTest {

    private val servers = mutableListOf<CompanionServer>()

    /** Where the servers' lifecycle requests run: one thread, in order, so it can be drained. */
    private val lifecycle = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /** Whatever a test leaves running is shut down here rather than leaking a bound port. */
    @AfterTest
    fun stopAll() {
        settle()
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
        lifecycle.close()
    }

    private fun newServer(): CompanionServer =
        CompanionServer(shutdownGraceMs = 0, lifecycleDispatcher = lifecycle).also { servers += it }

    /** Waits until every request the tab has made so far has run: they run in order on [lifecycle]. */
    private fun settle() = runBlocking(lifecycle) {}

    /** Settles the requests, then checks that they left the server [running]. */
    private fun assertRunning(server: CompanionServer, running: Boolean) {
        settle()
        assertEquals(running, server.isRunning.value, "the requests must leave isRunning=$running")
    }

    @Test
    fun `the enable switch starts the server and stores the port, and switching off stops it`() {
        val server = newServer()
        val port = freeServerPort()

        serverTab(initial = serverSettings { copy(port = port) }, server = server) { get, _ ->
            assertEquals(false, get().serverSettings.enabled, "the fixture starts disabled")
            serverSwitch(Switch.ENABLE).assertIsOff()
            onNodeWithText(ServerLabel.STOPPED).assertExists()

            serverSwitch(Switch.ENABLE).performScrollTo().performClick()
            assertRunning(server, true)
            waitForIdle()

            assertEquals(true, get().serverSettings.enabled, "switching on must be stored")
            assertEquals(port, get().serverSettings.port, "along with the port it was started on")
            assertTrue(server.isRunning.value, "and the server must actually be listening")
            serverSwitch(Switch.ENABLE).assertIsOn()
            onNodeWithText(ServerLabel.RUNNING).assertExists("the status must follow")
            onNodeWithText(ServerLabel.SERVER_URL).assertExists("and the URL row must appear")

            serverSwitch(Switch.ENABLE).performClick()
            assertRunning(server, false)
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
        val server = newServer()
        val port = freeServerPort()

        serverTab(initial = serverSettings { copy(port = port) }, server = server) { _, _ ->
            serverSwitch(Switch.ENABLE).performScrollTo().performClick()
            assertRunning(server, true)
            waitForIdle()
            val urlBefore = server.serverUrl.value
            assertTrue(urlBefore.endsWith(":$port"), "fixture: it must be up on the chosen port")

            onNodeWithContentDescription(ServerLabel.RESTART).performScrollTo().performClick()
            assertRunning(server, true)
            waitForIdle()

            assertTrue(server.isRunning.value, "Restart must leave the server listening")
            assertEquals(urlBefore, server.serverUrl.value, "on the same port it was given")
            onNodeWithText(ServerLabel.RUNNING).assertExists("and the tab must still read Running")
        }
    }
}
