@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.PresentationRemoteDialogContent
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Presentation Remote dialog — the QR code an operator hands a speaker so they can advance
 * slides from their own phone — in both themes.
 *
 * Shot through `PresentationRemoteDialogContent` rather than the `DialogWindow` that wraps it: a
 * dialog window is an OS window, which a headless test has no way to photograph. The content is what
 * the window contains, and it is `internal` for exactly this reason.
 *
 * The box is the dialog's own 400x720, so what is reviewed is the dialog at the size it really
 * opens at — including where its content overflows, which is what makes it grow itself in the app.
 */
class PresentationRemoteDialogScreenshotTest {

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        serverUrl: String = SERVER_URL,
        apiKeyEnabled: Boolean = false,
        apiKey: String = "",
        tunnelStatus: TunnelStatus = TunnelStatus.Idle,
        tunnelUrl: String = "",
        presentationDisplayUrl: String = "",
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Box(Modifier.size(400.dp, 720.dp)) {
                        var current by remember { mutableStateOf(settings) }
                        PresentationRemoteDialogContent(
                            settings = current,
                            onSettingsChange = { transform -> current = transform(current) },
                            serverUrl = serverUrl,
                            apiKeyEnabled = apiKeyEnabled,
                            apiKey = apiKey,
                            tunnelStatus = tunnelStatus,
                            tunnelUrl = tunnelUrl,
                            presentationDisplayUrl = presentationDisplayUrl,
                            onPresentationDisplayUrlChanged = {},
                            onStartTunnel = {},
                            onStopTunnel = {},
                            onDismiss = {},
                        )
                    }
                }
            }
            waitForIdle()
            captureTo(file)
        }
    }

    // ── The switch that gates the whole thing ───────────────────────────────────────────────────

    @Test
    fun `remote control off`() = shoot("disabled")

    /** Switched on: the QR appears, and with it the public-access section in its idle state. */
    @Test
    fun `remote control on`() = shoot("enabled", settings = enabled())

    /** No server, so there is no address to put in a QR code and the dialog says only that. */
    @Test
    fun `the server is not running`() = shoot("no_server", settings = enabled(), serverUrl = "")

    /** With an API key set the QR carries the password, so the speaker is not asked for one. */
    @Test
    fun `the QR carries the API key`() =
        shoot("with_api_key", settings = enabled(), apiKeyEnabled = true, apiKey = "s3rmon-k3y")

    // ── Public access, one image per tunnel state ───────────────────────────────────────────────
    // Idle is not shot on its own: it is what `enabled` above already shows, since a dialog that has
    // just been switched on has not started a tunnel yet.

    @Test
    fun `the tunnel binary downloading`() =
        shoot("tunnel_downloading", settings = enabled(), tunnelStatus = TunnelStatus.Downloading)

    @Test
    fun `the tunnel starting`() =
        shoot("tunnel_starting", settings = enabled(), tunnelStatus = TunnelStatus.Starting)

    /** Connected but still pointing at the LAN address — Local is the chosen one. */
    @Test
    fun `the tunnel up, showing the local address`() = shoot(
        "tunnel_local",
        settings = enabled(),
        tunnelStatus = TunnelStatus.Connected(TUNNEL_URL),
        tunnelUrl = TUNNEL_URL,
        presentationDisplayUrl = SERVER_URL,
    )

    /** The public address chosen, so the QR is the one a phone off the WiFi can reach. */
    @Test
    fun `the tunnel up, showing the public address`() = shoot(
        "tunnel_public",
        settings = enabled(),
        tunnelStatus = TunnelStatus.Connected(TUNNEL_URL),
        tunnelUrl = TUNNEL_URL,
        presentationDisplayUrl = TUNNEL_URL,
    )

    @Test
    fun `the tunnel failed`() = shoot(
        "tunnel_error",
        settings = enabled(),
        tunnelStatus = TunnelStatus.Error("cloudflared exited: no route to host"),
    )

    private fun enabled() = AppSettings().let {
        it.copy(presentationRemoteSettings = it.presentationRemoteSettings.copy(remoteControlEnabled = true))
    }

    private companion object {
        const val SECTION = "presentationRemoteDialog"

        const val SERVER_URL = "http://192.168.1.5:8080"
        const val TUNNEL_URL = "https://quiet-hymn-4271.trycloudflare.com"
    }
}
