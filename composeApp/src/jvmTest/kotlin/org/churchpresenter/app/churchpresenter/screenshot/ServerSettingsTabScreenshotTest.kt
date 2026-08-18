@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.data.settings.ServerSettings
import org.churchpresenter.app.churchpresenter.data.settings.StreamingSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ConnectionQrDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ServerLabel
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ServerSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.withIsolatedHome
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import kotlin.test.Test

/**
 * The Server tab of the settings dialog, in both themes.
 *
 * **Half the tab only exists while the server is actually listening.** The Restart button, the
 * Server URL row and its QR button, and every lower-third trigger URL are gated on
 * `CompanionServer.isRunning`, which belongs to the server rather than to the settings — no fixture
 * can fake it. So a real one is started, once for the whole class (a start costs ~600ms and a stop
 * ~1s; per image that would be the most expensive thing in this suite), on a **fixed port and with
 * a fixed host override**, because the URL it publishes is drawn into the images: left to itself it
 * would print this machine's LAN address and a port that changed per run.
 *
 * Nothing here clicks the enable switch or Restart — they would stop the shared server out from
 * under the other states, and `ServerSettingsTabLifecycleTest` already drives them for real.
 *
 * `RemoteClientManager` reads and writes `~/.churchpresenter/remote_clients.json` in its field
 * initialiser, so every render gets a `user.home` of its own through `withIsolatedHome` — otherwise
 * these images would show whichever phones and tablets the recording machine had paired with.
 *
 * The connection QR the tab's QR button opens is shot too, through its content composable: the
 * dialog itself is a `DialogWindow` — a real AWT window — which throws `HeadlessException` under
 * the suite's headless JVM.
 */
class ServerSettingsTabScreenshotTest {

    companion object {
        private var shared: CompanionServer? = null

        /**
         * The one running server, on a fixed port so the URL in the images never moves.
         *
         * Not `freeServerPort()`, which the behavior suite uses: a port picked at random is a
         * different Server URL on every recording and would rewrite these files for no change.
         */
        @BeforeClass
        @JvmStatic
        fun startServer() {
            val server = CompanionServer()
            server.start(PORT, HOST)
            // The positive signal the server itself publishes — never a fixed pause.
            val deadline = System.currentTimeMillis() + 10_000
            while (!server.isRunning.value && System.currentTimeMillis() < deadline) Thread.sleep(5)
            check(server.isRunning.value) { "the shared server did not start on port $PORT within 10s" }
            shared = server
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            shared?.stop()
            shared = null
            FIXTURES.deleteRecursively()
        }

        const val SECTION = "serverSettingsTab"

        /**
         * Fixed, so the Server URL row reads the same on every machine that records this -- and
         * NOT `testPort()`. This suite draws the port into the image (the Server URL row and the
         * connection QR encode it), so a per-fork offset would change every one of these files on
         * every run. It is in `serialTestClasses` instead, which is what keeps the bind safe.
         */
        const val PORT = 39_641
        const val HOST = "studio-pc"

        /** Fixed rather than generated: the Generate button makes a random UUID. */
        const val API_KEY = "3f9c1ba47e2d40518c6a"

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/server") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/server")

        /** Lower thirds the triggers card lists, one URL row each. */
        val LOWER_THIRDS = listOf("Announcement.json", "Speaker Name.json", "Welcome.json")

        /** Paired devices — an id with a friendly name, one without, and a blocked one. */
        const val TABLET = "a41f7c92e0b84d13"
        const val PHONE = "c8b0e35a7d914f60"
        const val INTRUDER = "f92d64a1b0c74e88"
    }

    // ── The server card, stopped ────────────────────────────────────────────────────────────────

    /** A fresh install: the server is off, and the two switches under it are off with it. */
    @Test
    fun `as it opens`() = shoot("defaults")

    /** A port and a host override typed in — both editable only while the server is stopped. */
    @Test
    fun `a port and host override set`() = shoot(
        "port_and_host",
        settings = server { copy(port = 8099, serverHost = "booth.local") },
    )

    /** API key protection on, which unfolds the key field with its Generate and Copy buttons. */
    @Test
    fun `api key protection on`() = shoot(
        "api_key",
        settings = server { copy(apiKeyEnabled = true, apiKey = API_KEY) },
    )

    /** Uploads allowed, which unfolds the size limit under it. */
    @Test
    fun `file uploads allowed`() = shoot(
        "file_uploads",
        settings = server { copy(fileUploadEnabled = true, maxMediaUploadMb = 250) },
    )

    // ── The server card, running ────────────────────────────────────────────────────────────────

    /**
     * Listening: the status reads Running, the port and host are locked, and the Server URL row
     * appears with the QR button beside it.
     */
    @Test
    fun `a running server`() = shoot("running", server = shared, settings = running())

    // ── The connection QR ───────────────────────────────────────────────────────────────────────
    // Shot through `ConnectionQrDialogContent`, since the dialog itself is a `DialogWindow` — a real
    // AWT window, which a headless test cannot open. Sized to the window's own 400×500 so the
    // framing is the one an operator sees, not a stretched one.

    /** What the QR button opens: the deep link as a code, and again as text under it. */
    @Test
    fun `the connection QR`() = qr("qr_dialog", apiKey = null)

    /** With API key protection on, the key is carried in the link — scan it and the phone is paired. */
    @Test
    fun `the connection QR carrying an API key`() = qr("qr_dialog_api_key", apiKey = API_KEY)

    // ── Remote clients ──────────────────────────────────────────────────────────────────────────
    // Not shot on its own: nothing paired. With the server stopped the whole tab fits in one
    // viewport, so `defaults` already carries both empty lists — and the triggers card's "start the
    // server" note under them.

    /**
     * Two allowed and one blocked.
     *
     * One of the allowed carries a friendly name and one does not, because that changes the row: a
     * named device puts its name in the status color and drops its id to a small monospace line
     * under it, while an unnamed one shows the raw id alone.
     */
    @Test
    fun `clients allowed and blocked`() = shoot("clients_listed", seedClients = ::pairedDevices) {
        scrollTo(ServerLabel.SECTION_CLIENTS)
    }

    /** The pencil opens an inline editor for that device's friendly name. */
    @Test
    fun `naming a client`() = shoot("client_naming", seedClients = ::pairedDevices) {
        scrollTo(ServerLabel.SECTION_CLIENTS)
        onAllNodesWithContentDescription(EDIT_NAME)[1].performClick()
        waitForIdle()
    }

    // ── Lower third triggers ────────────────────────────────────────────────────────────────────
    // Scrolled to the take-down row at the foot of the card rather than to its heading: with the
    // server running the tab is taller than the window, and the heading is already in view — so
    // scrolling to it moves nothing and leaves the rows it introduces below the fold.

    /** Running, with three lower thirds in the folder: a copy pair each, and the take-down row. */
    @Test
    fun `triggers for each lower third`() = shoot(
        "triggers",
        server = shared,
        settings = running().withLowerThirds(),
    ) { scrollTo(CLEAR_DISPLAY) }

    /**
     * The same card once an ATEM is configured.
     *
     * Each row grows from two buttons to six — still and clip, with and without the key — and an
     * upstream-key section appears under the list.
     */
    @Test
    fun `triggers with an ATEM configured`() = shoot(
        "triggers_atem",
        server = shared,
        settings = running().withLowerThirds().copy(atemSettings = AtemSettings(host = "10.0.0.40")),
    ) { scrollTo(CLEAR_DISPLAY) }

    // Not shot: the same card with API key protection on. The key is threaded into the URLs these
    // buttons copy to the clipboard, and a copied URL is not drawn anywhere — the card looks the
    // same with and without one.

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    private fun ComposeUiTest.scrollTo(label: String) {
        onAllNodesWithText(label)[0].performScrollTo()
        waitForIdle()
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun qr(name: String, apiKey: String?) = captureComponent(SECTION, name) {
        Box(Modifier.size(400.dp, 500.dp)) {
            ConnectionQrDialogContent(
                serverUrl = "http://$HOST:$PORT",
                apiKey = apiKey,
                onDismiss = {},
            )
        }
    }

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        server: CompanionServer? = null,
        seedClients: RemoteClientManager.() -> Unit = {},
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        withIsolatedHome {
            val companion = server ?: CompanionServer()
            val clients = RemoteClientManager().apply(seedClients)
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize()) {
                                var current by remember { mutableStateOf(settings) }
                                ServerSettingsTab(
                                    settings = current,
                                    onSettingsChange = { transform -> current = transform(current) },
                                    companionServer = companion,
                                    remoteClientManager = clients,
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
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun server(change: ServerSettings.() -> ServerSettings): AppSettings =
        AppSettings().let { it.copy(serverSettings = it.serverSettings.change()) }

    /**
     * Settings that agree with the shared server.
     *
     * The port and host are the ones it was started on, so the locked fields and the Server URL row
     * read as one coherent setup rather than as settings that have drifted from what is listening.
     */
    private fun running(change: ServerSettings.() -> ServerSettings = { this }): AppSettings =
        server { copy(enabled = true, port = PORT, serverHost = HOST).change() }

    /** The same settings over a folder of lower thirds, which is what the triggers card lists. */
    private fun AppSettings.withLowerThirds(): AppSettings =
        copy(streamingSettings = StreamingSettings(lowerThirdFolder = lowerThirdFolder().absolutePath))

    private fun pairedDevices(clients: RemoteClientManager) {
        clients.allowPermanently(TABLET)
        clients.setLabel(TABLET, "Booth iPad")
        clients.allowPermanently(PHONE)
        clients.blockPermanently(INTRUDER)
    }

    /**
     * A fixed folder under a neutral root.
     *
     * The card lists what is in it by name, and a repo-relative `build/` path would resolve through
     * the developer's home directory. The contents only have to parse as an animation — the card
     * shows names and buttons, never a frame — so a one-layer stub is enough.
     */
    private fun lowerThirdFolder(): File {
        val dir = FIXTURES.absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()
        LOWER_THIRDS.forEach { File(dir, it).writeText(STUB_LOTTIE) }
        return dir
    }
}

/** Enough of a Lottie for the folder scan's "does this parse" check, and no more. */
private const val STUB_LOTTIE =
    """{"v":"5.7.4","fr":30,"ip":0,"op":30,"w":1920,"h":1080,"nm":"Stub","assets":[],"layers":[]}"""

/** The pencil on a client row, which carries no text of its own. */
private const val EDIT_NAME = "Set friendly name"

/** The last button on the triggers card — the scroll target for the foot of the tab. */
private const val CLEAR_DISPLAY = "Clear Display"
