@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.app.churchpresenter.dialogs.QARemoteContent
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Q&A sharing dialog — the two QR codes, one for the room to post questions and one for a second
 * moderator's admin panel, plus everything about how the QR looks on the projector — in both themes.
 *
 * Shot through `QARemoteContent` for the same reason as the presentation remote: a `DialogWindow` is
 * an OS window and cannot be photographed headless, and the content is `internal` so it can be.
 *
 * `availableFonts` is passed empty rather than read from `GraphicsEnvironment`: the installed set
 * differs from machine to machine, and a font list in the image would make these recordings differ
 * by whose laptop recorded them.
 */
class QARemoteDialogScreenshotTest {

    private fun shoot(
        name: String,
        serverUrl: String = SERVER_URL,
        qaDisplayUrl: String = "",
        apiKeyEnabled: Boolean = false,
        apiKey: String = "",
        tunnelStatus: TunnelStatus = TunnelStatus.Idle,
        tunnelUrl: String = "",
        qaSettings: QASettings = QASettings(),
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Box(Modifier.size(760.dp, 700.dp)) {
                        var current by remember { mutableStateOf(AppSettings(qaSettings = qaSettings)) }
                        QARemoteContent(
                            serverUrl = serverUrl,
                            qaDisplayUrl = qaDisplayUrl,
                            onQaDisplayUrlChanged = {},
                            apiKeyEnabled = apiKeyEnabled,
                            apiKey = apiKey,
                            tunnelStatus = tunnelStatus,
                            tunnelUrl = tunnelUrl,
                            onStartTunnel = {},
                            onStopTunnel = {},
                            qaSettings = current.qaSettings,
                            onSettingsChange = { transform -> current = transform(current) },
                            availableFonts = emptyList(),
                            onDismiss = {},
                        )
                    }
                }
            }
            drive()
            waitForIdle()
            captureTo(file)
        }
    }

    // ── The two QR codes ────────────────────────────────────────────────────────────────────────

    @Test
    fun `sharing over the local network`() = shoot("local_only")

    /** No server, so neither QR has an address to carry. */
    @Test
    fun `the server is not running`() = shoot("no_server", serverUrl = "")

    /** With an API key set, the admin QR carries the password and the submission one does not. */
    @Test
    fun `the admin QR carries the API key`() =
        shoot("with_api_key", apiKeyEnabled = true, apiKey = "s3rmon k3y")

    // ── Public access, one image per tunnel state ───────────────────────────────────────────────

    @Test
    fun `the tunnel binary downloading`() = shoot("tunnel_downloading", tunnelStatus = TunnelStatus.Downloading)

    @Test
    fun `the tunnel starting`() = shoot("tunnel_starting", tunnelStatus = TunnelStatus.Starting)

    @Test
    fun `the tunnel up, sharing the local address`() = shoot(
        "tunnel_local",
        tunnelStatus = TunnelStatus.Connected(TUNNEL_URL),
        tunnelUrl = TUNNEL_URL,
        qaDisplayUrl = SERVER_URL,
    )

    @Test
    fun `the tunnel up, sharing the public address`() = shoot(
        "tunnel_public",
        tunnelStatus = TunnelStatus.Connected(TUNNEL_URL),
        tunnelUrl = TUNNEL_URL,
        qaDisplayUrl = TUNNEL_URL,
    )

    @Test
    fun `the tunnel failed`() =
        shoot("tunnel_error", tunnelStatus = TunnelStatus.Error("cloudflared exited: no route to host"))

    // ── How the QR and the questions look on the projector ──────────────────────────────────────

    @Test
    fun `the styling controls`() = shoot("styling", qaSettings = QASettings(qrCodeMessage = "Scan to ask a question"))

    @Test
    fun `styling carrying non-default values`() = shoot(
        "styling_customised",
        qaSettings = QASettings(
            qrCodeMessage = "Scan to ask a question",
            qrForegroundColor = "#1B2A5B",
            qrBackgroundColor = "#FFD54F",
            qrBackgroundOpacity = 80,
            textColor = "#FFD54F",
            bold = true,
            italic = true,
        ),
    )

    private companion object {
        const val SECTION = "qaRemoteDialog"

        const val SERVER_URL = "http://192.168.1.5:8080"
        const val TUNNEL_URL = "https://quiet-hymn-4271.trycloudflare.com"
    }
}
