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
import androidx.compose.ui.test.runComposeUiTest
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OBSSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.OBSSettingsTab
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.theme.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * The OBS tab of the settings dialog, in both themes.
 *
 * **It is a gated form.** Until OBS is switched on the tab is one switch and nothing else; switching
 * it on unfolds a connection card and a card of twelve scene mappings — the two shapes are different
 * enough to be separate images.
 *
 * **The status line cannot be posed from a fixture.** It is owned by [OBSWebSocketManager] behind a
 * read-only `State`, so each of its four states is produced by driving the manager for real against
 * a stand-in OBS started here — a Ktor WebSocket server on an OS-assigned port that speaks enough of
 * the protocol to finish, stall, or refuse the handshake. That keeps the images honest and, more to
 * the point, keeps them deterministic: the error text drawn on screen is then the app's own message
 * about the handshake rather than the host OS's wording for a refused connection, which differs by
 * platform.
 *
 * Every wait here ends on the manager publishing the status being waited for; none ends on a timeout.
 */
class ObsSettingsTabScreenshotTest {

    private val managers = mutableListOf<OBSWebSocketManager>()
    private val servers = mutableListOf<FakeObs>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.disconnect() } }
        managers.clear()
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    // ── The gate ────────────────────────────────────────────────────────────────────────────────

    /** Switched off, which is the default: one switch, and neither card below it. */
    @Test
    fun `as it opens, switched off`() = shoot("disabled")

    /** Switched on: the connection card and the twelve scene mappings appear. */
    @Test
    fun `switched on`() = shoot("enabled", settings = obs { copy(enabled = true) })

    // ── The status line ─────────────────────────────────────────────────────────────────────────

    /**
     * Mid-handshake.
     *
     * The stand-in accepts the socket and then says nothing, so the manager sits here rather than
     * moving on — Connect is disabled and the status reads Connecting.
     */
    @Test
    fun `connecting`() = shootAgainst(
        "status_connecting",
        Handshake.STALL,
        OBSWebSocketManager.ConnectionStatus.CONNECTING,
    )

    /** Connected: the button becomes Disconnect and the status goes green. */
    @Test
    fun `connected`() = shootAgainst(
        "status_connected",
        Handshake.COMPLETE,
        OBSWebSocketManager.ConnectionStatus.CONNECTED,
    )

    /**
     * A handshake the app rejects.
     *
     * The stand-in answers Identify with the wrong opcode, so the message beside the status is the
     * app's own — a refused connection would have been the host OS's wording instead, which is not
     * the same sentence on every platform.
     */
    @Test
    fun `a failed handshake`() = shootAgainst(
        "status_error",
        Handshake.REJECT,
        OBSWebSocketManager.ConnectionStatus.ERROR,
    )

    // ── Scene mappings ──────────────────────────────────────────────────────────────────────────

    /** Every mode pointed at a scene, a default scene set, and a password stored (and masked). */
    @Test
    fun `scenes mapped`() = shoot("scenes_mapped", settings = mapped())

    // Not shot: the foot of the mapping list. All twelve rows fit in one viewport, so scrolling to
    // the last of them lands where the tab already was and produces `scenes_mapped` again.

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Connects a real manager to a stand-in OBS and shoots the tab once [expected] is published.
     *
     * The card is drawn over a **fixed** host and port rather than the stand-in's own. The stand-in
     * binds whatever port the OS hands it, which is a different number on every run — and, because
     * the two themes are rendered one after the other with a server each, a different number in the
     * two halves of the same image. What is being photographed is the status line; the address
     * beside it only has to be stable.
     */
    private fun shootAgainst(
        name: String,
        handshake: Handshake,
        expected: OBSWebSocketManager.ConnectionStatus,
    ) = stackedThemes(SECTION, name) { mode, file ->
        val server = FakeObs(handshake).also { servers += it; it.start() }
        val manager = OBSWebSocketManager().also { managers += it }
        manager.connect(LOOPBACK, server.port, "")
        awaitStatus(manager, expected)
        render(mode, file, obs { copy(enabled = true, host = DISPLAY_HOST, port = DISPLAY_PORT) }, manager) {}
    }

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        render(mode, file, settings, OBSWebSocketManager().also { managers += it }, drive)
    }

    private fun render(
        mode: ThemeMode,
        file: java.io.File,
        settings: AppSettings,
        manager: OBSWebSocketManager,
        drive: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        var current by remember { mutableStateOf(settings) }
                        OBSSettingsTab(
                            settings = current,
                            onSettingsChange = { transform -> current = transform(current) },
                            obsManager = manager,
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

    /** Waits on the status itself — the positive signal the manager publishes — never on a pause. */
    private fun awaitStatus(manager: OBSWebSocketManager, expected: OBSWebSocketManager.ConnectionStatus) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (manager.status.value != expected && System.currentTimeMillis() < deadline) Thread.sleep(10)
        check(manager.status.value == expected) {
            "OBS never reached $expected — it is ${manager.status.value}"
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun obs(edit: OBSSettings.() -> OBSSettings) = AppSettings(obsSettings = OBSSettings().edit())

    private fun mapped() = obs {
        copy(
            enabled = true,
            host = "obs.local",
            password = "hunter2",
            defaultScene = "Camera Wide",
            sceneMappings = mapOf(
                Presenting.BIBLE.name to "Scripture",
                Presenting.LYRICS.name to "Lyrics",
                Presenting.PICTURES.name to "Slides",
                Presenting.PRESENTATION.name to "Slides",
                Presenting.MEDIA.name to "Video Roll",
                Presenting.LOWER_THIRD.name to "Camera + LT",
                Presenting.ANNOUNCEMENTS.name to "Announcements",
                Presenting.WEBSITE.name to "Browser",
                Presenting.CANVAS.name to "Canvas",
                Presenting.QA.name to "Q&A",
                Presenting.STT.name to "Captions",
                Presenting.NONE.name to "Camera Wide",
            ),
        )
    }

    /** How far the stand-in lets the handshake get. */
    private enum class Handshake { COMPLETE, STALL, REJECT }

    /**
     * A stand-in OBS, in the three shapes this tab's status line has.
     *
     * [Handshake.COMPLETE] greets and identifies as the real thing does; [Handshake.STALL] accepts
     * the socket and then says nothing, holding the client mid-handshake; [Handshake.REJECT] answers
     * Identify with an opcode the app does not accept, which is what puts the app's own message on
     * screen instead of the operating system's.
     *
     * Bound on port 0 and read back after starting — never "find a free port, then bind it", which
     * loses the race against anything else in the suite that opens one.
     */
    private class FakeObs(private val handshake: Handshake) {
        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    try {
                        if (handshake == Handshake.STALL) {
                            for (frame in incoming) if (frame is Frame.Text) frame.readText()
                            return@webSocket
                        }
                        send(Frame.Text(HELLO))
                        incoming.receive()
                        send(Frame.Text(if (handshake == Handshake.REJECT) REFUSED else IDENTIFIED))
                        for (frame in incoming) if (frame is Frame.Text) frame.readText()
                    } catch (_: Exception) {
                        // the client hangs up when the test ends, which is not a failure here
                    }
                }
            }
        }

        fun start() {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        fun stop() = server.stop(0, 0)

        private companion object {
            const val HELLO = """{"op":0,"d":{"obsWebSocketVersion":"5.1.0","rpcVersion":1}}"""
            const val IDENTIFIED = """{"op":2,"d":{"negotiatedRpcVersion":1}}"""
            /** Not opcode 2 — the app reads this as authentication having failed. */
            const val REFUSED = """{"op":3,"d":{}}"""
        }
    }

    private companion object {
        const val SECTION = "obsSettingsTab"
        /** Where the stand-in actually listens, and what the card is drawn with instead. */
        const val LOOPBACK = "127.0.0.1"
        const val DISPLAY_HOST = "obs.local"
        const val DISPLAY_PORT = 4455

        /** Only ever the failure path: every wait here ends on the status the manager publishes. */
        const val AWAIT_TIMEOUT_MS = 10_000L
    }
}
