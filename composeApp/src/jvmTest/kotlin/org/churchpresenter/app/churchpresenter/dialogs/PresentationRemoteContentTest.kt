@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PresentationRemoteContentTest {

    private class Result {
        var dismissed = 0
        var startTunnelCalls = 0
        var stopTunnelCalls = 0
        var displayUrlChangedTo: String? = null
        var copiedText: String? = null
    }

    private fun dialog(
        settings: AppSettings = AppSettings(),
        serverUrl: String = "http://192.168.1.5:8080",
        apiKeyEnabled: Boolean = false,
        apiKey: String = "",
        tunnelStatus: TunnelStatus = TunnelStatus.Idle,
        tunnelUrl: String = "",
        presentationDisplayUrl: String = "",
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
                MaterialTheme {
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
                        onPresentationDisplayUrlChanged = { result.displayUrlChangedTo = it },
                        onStartTunnel = { result.startTunnelCalls++ },
                        onStopTunnel = { result.stopTunnelCalls++ },
                        onDismiss = { result.dismissed++ },
                        copyText = { result.copiedText = it },
                    )
                }
            }
            block(result)
        }
    }

    @Test
    fun `the remote control switch starts off by default and can be turned on`() = dialog {
        onAllNodes(isToggleable())[0].assertIsOff().performClick()
        onAllNodes(isToggleable())[0].assertIsOn()
    }

    @Test
    fun `the remote control switch reflects an already-enabled setting`() = dialog(
        settings = AppSettings().let {
            it.copy(presentationRemoteSettings = it.presentationRemoteSettings.copy(remoteControlEnabled = true))
        },
    ) {
        onAllNodes(isToggleable())[0].assertIsOn()
    }

    @Test
    fun `with no server running the QR section is replaced with a hint`() = dialog(serverUrl = "") {
        onNodeWithText(
            "Server not running — start the companion server in Settings to enable remote control",
        ).assertExists()
        onNodeWithText("Copy URL").assertDoesNotExist()
    }

    @Test
    fun `the copy button copies the QR url`() = dialog { result ->
        onNodeWithText("Copy URL").performClick()
        assertEquals("http://192.168.1.5:8080/presentation-remote", result.copiedText)
    }

    @Test
    fun `an enabled api key is appended to the copied url`() = dialog(apiKeyEnabled = true,
        apiKey = "secret123") { result ->
        onNodeWithText("Copy URL").performClick()
        assertEquals("http://192.168.1.5:8080/presentation-remote?password=secret123", result.copiedText)
    }

    @Test
    fun `an enabled api key with a blank value is not appended to the copied url`() = dialog(apiKeyEnabled = true,
        apiKey = "") { result ->
        onNodeWithText("Copy URL").performClick()
        assertEquals("http://192.168.1.5:8080/presentation-remote", result.copiedText)
    }

    @Test
    fun `a manually chosen display url overrides the server url in the qr code`() = dialog(
        presentationDisplayUrl = "https://custom.example.com",
    ) { result ->
        onNodeWithText("Copy URL").performClick()
        assertEquals("https://custom.example.com/presentation-remote", result.copiedText)
    }

    @Test
    fun `an api key too long to fit a QR code still renders the copy fallback`() = dialog(
        apiKeyEnabled = true,
        apiKey = "k".repeat(5000),
    ) { result ->
        // The 5000-character URL is rendered above the button, so how far down the button lands
        // depends on where that text wraps -- which depends on font metrics, and so differs between
        // this machine and CI. Scroll it into view rather than clicking wherever it happened to be:
        // an off-screen node is still in the tree, so the click silently does nothing and the
        // failure reads as "the callback never fired".
        onNodeWithText("Copy URL").performScrollTo().performClick()
        assertEquals("http://192.168.1.5:8080/presentation-remote?password=" + "k".repeat(5000), result.copiedText)
    }

    @Test
    fun `an idle tunnel offers to enable public access`() = dialog { result ->
        onNodeWithText("Enable Public Access").performClick()
        assertEquals(1, result.startTunnelCalls)
    }

    @Test
    fun `a downloading tunnel shows its own status`() = dialog(tunnelStatus = TunnelStatus.Downloading) {
        onNodeWithText("Downloading tunnel client…").assertExists()
    }

    @Test
    fun `a starting tunnel shows its own status`() = dialog(tunnelStatus = TunnelStatus.Starting) {
        onNodeWithText("Starting tunnel…").assertExists()
    }

    @Test
    fun `a connected tunnel offers local and public switching`() = dialog(
        tunnelStatus = TunnelStatus.Connected("https://x.trycloudflare.com"),
        tunnelUrl = "https://x.trycloudflare.com",
    ) { result ->
        onNodeWithText("Public").performClick()
        assertEquals("https://x.trycloudflare.com", result.displayUrlChangedTo)

        onNodeWithText("Local").performClick()
        assertEquals("http://192.168.1.5:8080", result.displayUrlChangedTo)
    }

    @Test
    fun `a connected tunnel already showing the public url can switch back to local`() = dialog(
        tunnelStatus = TunnelStatus.Connected("https://x.trycloudflare.com"),
        tunnelUrl = "https://x.trycloudflare.com",
        presentationDisplayUrl = "https://x.trycloudflare.com",
    ) { result ->
        onNodeWithText("Local").performClick()
        assertEquals("http://192.168.1.5:8080", result.displayUrlChangedTo)
    }

    @Test
    fun `a connected tunnel already showing the server url by exact match can switch to public`() = dialog(
        tunnelStatus = TunnelStatus.Connected("https://x.trycloudflare.com"),
        tunnelUrl = "https://x.trycloudflare.com",
        presentationDisplayUrl = "http://192.168.1.5:8080",
    ) { result ->
        onNodeWithText("Public").performClick()
        assertEquals("https://x.trycloudflare.com", result.displayUrlChangedTo)
    }

    @Test
    fun `a connected tunnel can be disabled`() = dialog(
        tunnelStatus = TunnelStatus.Connected("https://x.trycloudflare.com"),
        tunnelUrl = "https://x.trycloudflare.com",
    ) { result ->
        onNodeWithText("Disable Public Access").performClick()
        assertEquals(1, result.stopTunnelCalls)
    }

    @Test
    fun `a tunnel error shows its message and can be retried`() = dialog(
        tunnelStatus = TunnelStatus.Error("Could not reach the tunnel service"),
    ) { result ->
        onNodeWithText("Could not reach the tunnel service").assertExists()
        onNodeWithText("Retry").performClick()
        assertEquals(1, result.startTunnelCalls)
    }

    @Test
    fun `Close dismisses the dialog`() = dialog { result ->
        onNodeWithText("Close").performClick()
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `nothing fires before any control is touched`() = dialog { result ->
        assertEquals(0, result.dismissed)
        assertEquals(0, result.startTunnelCalls)
        assertEquals(0, result.stopTunnelCalls)
        assertNull(result.displayUrlChangedTo)
        assertNull(result.copiedText)
    }
}
