@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.companionserver.CompanionServer
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Everything the tab only composes while the server is actually running: the Restart button, the
 * Server URL row and its QR button, and the lower-third trigger URLs.
 *
 * `isRunning` belongs to `CompanionServer`, not to the settings, so no fixture can produce this
 * state — a real server has to be listening. One is started for the **whole class** and stopped
 * afterwards, because a start costs ~600 ms and a stop ~1 s; per test that would blow the budget
 * several times over, while amortised across the class it costs nothing per test.
 *
 * The price of sharing is that **nothing here may stop or restart it** — the enable switch and the
 * Restart button are therefore only asserted to exist, and are actually clicked in
 * `ServerSettingsTabLifecycleTest`, which owns a server of its own.
 */
class ServerSettingsTabRunningTest {

    companion object {
        private lateinit var server: CompanionServer
        private var port = 0

        @BeforeClass
        @JvmStatic
        fun startServer() {
            port = freeServerPort()
            server = CompanionServer()
            server.start(port, "127.0.0.1")
            // Wait on the positive signal the server itself publishes, never on a fixed pause.
            val deadline = System.currentTimeMillis() + 10_000
            while (!server.isRunning.value && System.currentTimeMillis() < deadline) Thread.sleep(5)
            check(server.isRunning.value) { "the shared test server did not start within 10s" }
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            if (::server.isInitialized) server.stop()
        }
    }

    private val url get() = "http://127.0.0.1:$port"

    // ── Status ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a running server reads as Running and its switch is on`() = serverTab(server = server) { _, _ ->
        onNodeWithText(ServerLabel.RUNNING).assertExists("the status must read Running")
        onNodeWithText(ServerLabel.STOPPED).assertDoesNotExist()
        serverSwitch(Switch.ENABLE).assertIsOn()
    }

    /**
     * The switch follows the **server**, not the stored flag. A settings file that says the server is
     * off while it is actually listening must still show it as on — otherwise an operator would be
     * told the opposite of what is true.
     */
    @Test
    fun `the switch follows the server rather than the stored flag`() {
        serverTab(initial = serverSettings { copy(enabled = false) }, server = server) { get, _ ->
            assertEquals(false, get().serverSettings.enabled, "the fixture says off")
            // A listening server must read as on even though the fixture says off.
            serverSwitch(Switch.ENABLE).assertIsOn()
            onNodeWithText(ServerLabel.RUNNING).assertExists()
        }
    }

    // ── Restart ─────────────────────────────────────────────────────────────────────────────────

    /** Only offered while running — there is nothing to restart otherwise. */
    @Test
    fun `Restart is offered while running`() = serverTab(server = server) { _, _ ->
        onNodeWithText(ServerLabel.RESTART).assertExists("Restart must be offered while running")
    }

    /**
     * The port and host cannot be edited while the server is up — both fields are disabled, so the
     * operator has to stop it first, which is the reason Restart exists at all.
     *
     * A disabled field publishes no set-text action, so it is found by what it displays rather than
     * by being editable. That absence is itself the assertion: nothing on the tab can be typed into
     * while it is running and nothing is configured.
     */
    @Test
    fun `the port and host cannot be typed into while running`() = serverTab(server = server) { _, _ ->
        boxShowing("8765").assertIsNotEnabled()
        assertEquals(
            0,
            serverTextFields().fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "no field on a running, unconfigured tab may accept typing",
        )
    }

    // ── Server URL ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the server URL row appears with the address the server is listening on`() =
        serverTab(server = server) { _, _ ->
            onNodeWithText(ServerLabel.SERVER_URL).assertExists("the URL row must appear")
            assertFieldShows(url, "the URL field")
        }

    /**
     * The QR button is asserted but never clicked: it opens a `DialogWindow`, a real AWT window,
     * which throws `HeadlessException` under this suite's headless JVM.
     */
    @Test
    fun `the QR button is offered beside the URL`() = serverTab(server = server) { _, _ ->
        onNodeWithText(ServerLabel.QR).assertExists("the QR button must be offered")
    }

    @Test
    fun `neither the URL row nor Restart nor QR exists while stopped`() = serverTab { _, _ ->
        onNodeWithText(ServerLabel.SERVER_URL).assertDoesNotExist()
        onNodeWithText(ServerLabel.RESTART).assertDoesNotExist()
        onNodeWithText(ServerLabel.QR).assertDoesNotExist()
        onNodeWithText(ServerLabel.STOPPED).assertExists()
    }

    // ── Lower-third triggers ────────────────────────────────────────────────────────────────────

    /**
     * With the server up the triggers section stops telling the operator to start it. With no lower
     * thirds configured it says that instead, which is the branch this fixture lands on.
     */
    @Test
    fun `the triggers section stops asking for the server once it is running`() =
        serverTab(server = server) { _, _ ->
            onNodeWithText(ServerLabel.NO_TRIGGERS_SERVER_OFF)
                .assertDoesNotExist()
            onNodeWithText(ServerLabel.SECTION_TRIGGERS).assertExists("the section is still headed")
        }

    @Test
    fun `the triggers section asks for the server while it is stopped`() = serverTab { _, _ ->
        onNodeWithText(ServerLabel.NO_TRIGGERS_SERVER_OFF)
            .assertExists("a stopped server must be explained rather than showing dead URLs")
    }

    // ── The shared server itself ────────────────────────────────────────────────────────────────

    @Test
    fun `the shared server is listening on the port it was given`() {
        assertTrue(server.isRunning.value, "the class's server must still be up")
        assertEquals(url, server.serverUrl.value, "and publishing the URL the tab renders")
    }
}
