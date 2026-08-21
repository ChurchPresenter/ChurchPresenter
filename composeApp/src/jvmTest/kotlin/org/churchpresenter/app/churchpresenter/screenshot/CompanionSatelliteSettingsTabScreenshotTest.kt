@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CompanionSatelliteSettingsTab
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.companionsatellite.CompanionConnectionStatus
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The Companion Satellite tab of the settings dialog, in both themes.
 *
 * A card per configured connection, each holding the connection details on the left and the three
 * placements — tab, left sidebar, right sidebar — on the right. What changes the shape of a card:
 *
 *  - **Whether a view model is supplied.** Without one the connect row does not exist at all; with
 *    one the card gains a Connect button and a status line. The tab takes it as an optional
 *    parameter, and both are real states of the app.
 *  - **Which placements are ticked.** Each ticked placement unfolds its own rows, columns, bitmap
 *    size and button-size cap, because each is a separate device registration with Companion.
 *  - **How many connections are configured.** The Remove button only appears past the first.
 *
 * **The status line is driven for real.** `CompanionSatelliteViewModel` publishes its states behind
 * a read-only map, so connecting, connected and the error line cannot be posed by a fixture — each
 * is produced against a stand-in Companion started here: a plain socket that speaks the few lines of
 * the satellite protocol needed to finish, stall, or refuse the device registration. The refusal is
 * what puts a *fixed* message on screen; a dead port would have shown the host OS's wording for a
 * refused connection instead, which is not the same sentence on every platform.
 *
 * **Every id in these images is a fixture.** `CompanionSatelliteSettings` generates its `id` and
 * `deviceId` with `UUID.randomUUID()`, and the device id is drawn in a text field — left to itself
 * it would be a different image on every recording.
 */
class CompanionSatelliteSettingsTabScreenshotTest {

    private val viewModels = mutableListOf<CompanionSatelliteViewModel>()
    private val fakes = mutableListOf<FakeCompanion>()

    @AfterTest
    fun cleanUp() {
        viewModels.forEach { runCatching { it.dispose() } }
        viewModels.clear()
        fakes.forEach { runCatching { it.stop() } }
        fakes.clear()
    }

    // ── The card, without a view model ──────────────────────────────────────────────────────────

    /**
     * As it opens: one connection, nothing ticked, and no connect row.
     *
     * With no placement ticked, the three blocks on the right are a checkbox and a label each — the
     * fields under them only exist for a placement that is actually registered.
     */
    @Test
    fun `as it opens`() = shoot("defaults", settings = settings(connection()))

    /** All three placements ticked, each with a grid shape of its own. */
    @Test
    fun `every placement in use`() = shoot(
        "placements",
        settings = settings(
            connection {
                copy(
                    host = "companion.local",
                    showInTab = true,
                    showInLeftSidebar = true,
                    showInRightSidebar = true,
                    leftSidebarDeviceId = "booth-left",
                    rightSidebarDeviceId = "booth-right",
                    leftSidebarRows = 2,
                    leftSidebarColumns = 4,
                    leftSidebarBitmapSize = 96,
                    leftSidebarMaxButtonSizeDp = 64,
                    rightSidebarRows = 8,
                    rightSidebarColumns = 2,
                    autoConnect = true,
                )
            },
        ),
    )

    /** Two connections, which is what brings out the Remove button on each card. */
    @Test
    fun `two connections`() = shoot(
        "two_connections",
        settings = settings(
            connection { copy(name = "Booth", host = "companion.local", showInTab = true) },
            connection(id = SECOND_ID, deviceId = SECOND_DEVICE_ID) {
                copy(name = "Stage", host = "stage-pc", port = 16623, showInLeftSidebar = true)
            },
        ),
    ) { scrollToBottom() }

    // ── The connect row, with a view model ──────────────────────────────────────────────────────

    /** Connect is offered but disabled: no host, and no placement to register. */
    @Test
    fun `nothing to connect to`() = shoot(
        "connect_disabled",
        settings = settings(connection()),
        viewModel = viewModel(),
    )

    /** Configured and idle — Connect armed, and the status says it is not connected. */
    @Test
    fun `ready to connect`() = shoot(
        "status_disconnected",
        settings = settings(connection { copy(host = "companion.local", showInTab = true) }),
        viewModel = viewModel(),
    )

    /** Mid-registration: the stand-in accepts the socket and then says nothing. */
    @Test
    fun `connecting`() = shootAgainst("status_connecting", Registration.STALL, CompanionConnectionStatus.CONNECTING)

    /** Registered: the button becomes Disconnect and the status line drops away entirely. */
    @Test
    fun `connected`() = shootAgainst("status_connected", Registration.ACCEPT, CompanionConnectionStatus.CONNECTED)

    /** Refused: Companion's own reason is shown beside the button. */
    @Test
    fun `a refused registration`() = shootAgainst("status_error", Registration.REFUSE, CompanionConnectionStatus.ERROR)

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Connects a real view model to a stand-in Companion and shoots the card once [expected] lands.
     *
     * Two connection objects with the **same id**: the view model is pointed at the stand-in's
     * OS-assigned port, while the card is drawn over a fixed host and port. The id is what the
     * status is keyed by, so the card still shows the status its own connection produced — without
     * drawing a port number that changes on every recording, and differs between the two themes,
     * which are rendered one after the other with a stand-in each.
     */
    private fun shootAgainst(
        name: String,
        registration: Registration,
        expected: CompanionConnectionStatus,
    ) = stackedThemes(SECTION, name) { mode, file ->
        val fake = FakeCompanion(registration).also { fakes += it; it.start() }
        val model = viewModel()
        model.connectAll(connectionFor(host = LOOPBACK, port = fake.port))
        awaitStatus(model, expected)
        render(mode, file, settings(connectionFor(host = DISPLAY_HOST, port = DISPLAY_PORT)), model) {}
    }

    private fun shoot(
        name: String,
        settings: AppSettings,
        viewModel: CompanionSatelliteViewModel? = null,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file -> render(mode, file, settings, viewModel, drive) }

    private fun render(
        mode: ThemeMode,
        file: File,
        settings: AppSettings,
        viewModel: CompanionSatelliteViewModel?,
        drive: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        var current by remember { mutableStateOf(settings) }
                        CompanionSatelliteSettingsTab(
                            settings = current,
                            onSettingsChange = { transform -> current = transform(current) },
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
        waitForIdle()
        drive()
        waitForIdle()
        captureTo(file)
    }

    private fun ComposeUiTest.scrollToBottom() {
        onAllNodesWithText(ADD_CONNECTION)[0].performScrollTo()
        waitForIdle()
    }

    /** Waits on the status the view model publishes — the positive signal, never a fixed pause. */
    private fun awaitStatus(model: CompanionSatelliteViewModel, expected: CompanionConnectionStatus) {
        val slot = CompanionSurfaceSlot(CONNECTION_ID, CompanionSurfacePlacement.TAB)
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (model.connectionStates[slot]?.status != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        check(model.connectionStates[slot]?.status == expected) {
            "the satellite never reached $expected — it is ${model.connectionStates[slot]?.status}"
        }
    }

    private fun viewModel() = CompanionSatelliteViewModel().also { viewModels += it }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun settings(vararg connections: CompanionSatelliteSettings) =
        AppSettings(companionSatelliteConnections = connections.toList())

    /** A connection with fixed identities — the generated ones are random and are drawn on screen. */
    private fun connection(
        id: String = CONNECTION_ID,
        deviceId: String = DEVICE_ID,
        edit: CompanionSatelliteSettings.() -> CompanionSatelliteSettings = { this },
    ) = CompanionSatelliteSettings(id = id, deviceId = deviceId).edit()

    /** The connection the status images use, at whichever address they need. */
    private fun connectionFor(host: String, port: Int) = connection {
        copy(host = host, port = port, showInTab = true, reconnectDelayMs = 60_000)
    }

    /** How far the stand-in lets the device registration get. */
    private enum class Registration { ACCEPT, STALL, REFUSE }

    /**
     * A stand-in Companion, in the three shapes this card's status line has.
     *
     * The satellite protocol is line-based over a plain socket: the server greets with `BEGIN`, the
     * client answers with an `ADD-DEVICE`, and the server's reply decides the outcome — `OK` for
     * accepted, anything else carrying a `MESSAGE` for refused. A refusal message is **quoted**:
     * the parameter parser splits on unquoted spaces, so an unquoted reason arrives as its first
     * word and the card shows "Error: Surface". [Registration.STALL] simply never greets, which
     * holds the client in CONNECTING.
     *
     * Bound on port 0 and read back, so nothing races us for a port we probed and then released.
     */
    private class FakeCompanion(private val registration: Registration) {
        private val server = ServerSocket(0)
        val port: Int get() = server.localPort
        private var thread: Thread? = null

        fun start() {
            thread = Thread {
                runCatching {
                    server.accept().use { socket -> serve(socket) }
                }
            }.apply { isDaemon = true; start() }
        }

        private fun serve(socket: Socket) {
            val reader = socket.getInputStream().bufferedReader()
            val writer = socket.getOutputStream().bufferedWriter()
            if (registration == Registration.STALL) {
                while (reader.readLine() != null) Unit
                return
            }
            writer.write("BEGIN CompanionVersion=3.0 ApiVersion=1.7.0\n")
            writer.flush()
            reader.readLine() // the client's ADD-DEVICE
            writer.write(
                if (registration == Registration.ACCEPT) "ADD-DEVICE OK DEVICEID=$DEVICE_ID\n"
                else "ADD-DEVICE DEVICEID=$DEVICE_ID MESSAGE=\"Surface not found in Companion\"\n"
            )
            writer.flush()
            while (reader.readLine() != null) Unit
        }

        fun stop() {
            runCatching { server.close() }
            thread?.interrupt()
        }
    }

    private companion object {
        const val SECTION = "companionSatelliteSettingsTab"

        // Fixed identities — the real ones are random UUIDs and the device id is drawn on screen.
        const val CONNECTION_ID = "booth-connection"
        const val DEVICE_ID = "churchpresenter-booth"
        const val SECOND_ID = "stage-connection"
        const val SECOND_DEVICE_ID = "churchpresenter-stage"

        /** Where the stand-in actually listens, and what the card is drawn with instead. */
        const val LOOPBACK = "127.0.0.1"
        const val DISPLAY_HOST = "companion.local"
        const val DISPLAY_PORT = 16622

        /** Only ever the failure path: every wait ends on the status the view model publishes. */
        const val AWAIT_TIMEOUT_MS = 10_000L

        const val ADD_CONNECTION = "+ Add Connection"
    }
}
