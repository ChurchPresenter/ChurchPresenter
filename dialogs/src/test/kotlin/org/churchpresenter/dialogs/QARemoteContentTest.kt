@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.server.TunnelStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Q&A remote window, and the two addresses it works out for the room.
 *
 * One of these goes on a screen for a congregation to scan and the other is the moderator's own way
 * in, so getting them mixed up either hides the queue or hands the room the controls. They are
 * *derived* — from the server address, an optional public tunnel, and whether an API key is set —
 * which is what this covers.
 *
 * `QARemoteDialog` opens a `DialogWindow` and sizes it with a grow-to-fit effect, neither of which
 * runs headless, so the body was lifted into `QARemoteContent`. Three things that content now takes
 * as parameters were previously reached for inside it: the font list (`GraphicsEnvironment`), the
 * clipboard writer, and the scroll state shared with the sizing effect. Injecting the clipboard is
 * what lets these tests press Copy and assert *which* address was handed over, rather than only
 * that a button exists.
 *
 * Left uncovered: the `DialogWindow` call, the grow-to-fit effect, and the two `SlimSlider`s (QR and
 * background opacity) — `SlimSlider` publishes no semantics of its own (a bare `Canvas`), and its
 * caption/readout sit on the same row here rather than stacked, so the pixel-geometry helper the
 * Background settings tab tests use for it doesn't apply.
 */
class QARemoteContentTest {

    private companion object {
        const val SERVER = "http://192.168.1.50:8080"
        const val TUNNEL = "https://abc-def.trycloudflare.com"
    }

    private object Label {
        const val COPY = "Copy URL"
    }

    private class Harness {
        val clipboard = mutableListOf<String>()
        val urlChanges = mutableListOf<String>()
        var tunnelStarts = 0
        var tunnelStops = 0
        var dismissed = 0
        var settings = QASettings()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun qaRemote(
        serverUrl: String = SERVER,
        qaDisplayUrl: String = "",
        apiKeyEnabled: Boolean = false,
        apiKey: String = "",
        tunnelStatus: TunnelStatus = TunnelStatus.Idle,
        tunnelUrl: String = "",
        initialQaSettings: QASettings = QASettings(),

        block: ComposeUiTest.(Harness) -> Unit,
    ) {
        val h = Harness()
        h.settings = initialQaSettings
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var appSettings by remember { mutableStateOf(AppSettings(qaSettings = initialQaSettings)) }
                    QARemoteContent(
                        serverUrl = serverUrl,
                        qaDisplayUrl = qaDisplayUrl,
                        onQaDisplayUrlChanged = { h.urlChanges += it },
                        apiKeyEnabled = apiKeyEnabled,
                        apiKey = apiKey,
                        tunnelStatus = tunnelStatus,
                        tunnelUrl = tunnelUrl,
                        onStartTunnel = { h.tunnelStarts++ },
                        onStopTunnel = { h.tunnelStops++ },
                        qaSettings = appSettings.qaSettings,
                        onSettingsChange = { transform ->
                            appSettings = transform(appSettings)
                            h.settings = appSettings.qaSettings
                        },
                        copyText = { h.clipboard += it },
                        onDismiss = { h.dismissed++ },
                    )
                }
            }
            block(h)
        }
    }

    /** Exact match: the URL panels each render their address as one whole node. */
    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** Substring match, for asserting no address was built at all. */
    private fun ComposeUiTest.showsAnyContaining(fragment: String): Boolean =
        onAllNodes(hasText(fragment, substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** Presses the nth Copy button: the submission panel's is first, the moderator's second. */
    private fun ComposeUiTest.copy(index: Int) {
        onAllNodes(hasText(Label.COPY))[index].performClick()
        waitForIdle()
    }

    // ── The address the congregation scans ──────────────────────────────────────

    @Test
    fun `the submission address is the server with the Q and A path on it`() = qaRemote { h ->
        assertTrue(shows("$SERVER/qa"), "the room's address is the server plus /qa")
    }

    @Test
    fun `a configured display address is preferred over the server's own`() =
        qaRemote(qaDisplayUrl = TUNNEL, tunnelUrl = TUNNEL) { h ->
            assertTrue(shows("$TUNNEL/qa"), "the address chosen for display is the one to publish")
            assertTrue(!shows("$SERVER/qa"), "the local address must not be the one shown to the room")
        }

    @Test
    fun `with no server address at all nothing is offered to scan`() =
        qaRemote(serverUrl = "") { h ->
            assertTrue(
                !showsAnyContaining("/qa"),
                "an address cannot be built from nothing, so neither panel may show a path",
            )
        }

    // ── The moderator's own way in ──────────────────────────────────────────────

    @Test
    fun `the moderator address stays on the local server while the tunnel is unused`() = qaRemote { h ->
        assertTrue(shows("$SERVER/qa/admin"), "moderation stays on the LAN unless deliberately published")
    }

    @Test
    fun `the moderator address follows the tunnel only when the tunnel is what is being displayed`() =
        qaRemote(qaDisplayUrl = TUNNEL, tunnelUrl = TUNNEL) { h ->
            assertTrue(shows("$TUNNEL/qa/admin"), "publishing over the tunnel moves moderation there too")
        }

    @Test
    fun `a running tunnel that is not being displayed leaves moderation local`() =
        qaRemote(qaDisplayUrl = "", tunnelUrl = TUNNEL, tunnelStatus = TunnelStatus.Connected(TUNNEL)) { h ->
            // The tunnel exists but the room is being given the LAN address, so the moderator keeps it too.
            assertTrue(shows("$SERVER/qa/admin"), "a tunnel merely running must not move the moderator link")
        }

    // ── The API key in the moderator link ───────────────────────────────────────

    @Test
    fun `an enabled API key is carried in the moderator link so the QR opens straight in`() =
        qaRemote(apiKeyEnabled = true, apiKey = "secret123") { h ->
            assertTrue(
                shows("$SERVER/qa/admin?password=secret123"),
                "the moderator's QR has to get past the key without it being typed on a phone",
            )
        }

    @Test
    fun `a key with characters that would break a URL is encoded`() =
        qaRemote(apiKeyEnabled = true, apiKey = "p@ss word&more") { h ->
            assertTrue(
                shows("$SERVER/qa/admin?password=p%40ss+word%26more"),
                "an unencoded & would cut the key short and an unencoded space would break the link",
            )
        }

    @Test
    fun `a key that is set but not enabled is left out`() =
        qaRemote(apiKeyEnabled = false, apiKey = "secret123") { h ->
            assertTrue(shows("$SERVER/qa/admin"), "the plain link is the one to show")
            assertTrue(!shows("$SERVER/qa/admin?password=secret123"), "a disabled key must not be published")
        }

    @Test
    fun `an enabled but empty key adds nothing`() =
        qaRemote(apiKeyEnabled = true, apiKey = "") { h ->
            assertTrue(shows("$SERVER/qa/admin"), "there is no key to carry, so the link stays plain")
        }

    // ── Copying ─────────────────────────────────────────────────────────────────

    @Test
    fun `copying the first panel copies the address the congregation scans`() = qaRemote { h ->
        copy(0)
        assertEquals(listOf("$SERVER/qa"), h.clipboard)
    }

    @Test
    fun `copying the moderator panel copies the link including its key`() =
        qaRemote(apiKeyEnabled = true, apiKey = "secret123") { h ->
            copy(1)
            assertEquals(
                listOf("$SERVER/qa/admin?password=secret123"),
                h.clipboard,
                "the copied moderator link must be the one that actually gets in",
            )
        }

    // ── Tunnel state ────────────────────────────────────────────────────────────

    @Test
    fun `a tunnel error is reported rather than swallowed`() =
        qaRemote(tunnelStatus = TunnelStatus.Error("cloudflared exited")) { h ->
            onNodeWithText("cloudflared exited").assertIsDisplayed()
        }

    @Test
    fun `Enable Public Access starts the tunnel`() = qaRemote(tunnelStatus = TunnelStatus.Idle) { h ->
        onNodeWithText("Enable Public Access").performClick()
        assertEquals(1, h.tunnelStarts)
    }

    @Test
    fun `Downloading shows progress text, not the enable button`() =
        qaRemote(tunnelStatus = TunnelStatus.Downloading) { h ->
            onNodeWithText("Downloading tunnel client…").assertIsDisplayed()
            assertTrue(!shows("Enable Public Access"))
        }

    @Test
    fun `Starting shows progress text`() =
        qaRemote(tunnelStatus = TunnelStatus.Starting) { h ->
            onNodeWithText("Starting tunnel…").assertIsDisplayed()
        }

    @Test
    fun `Retry restarts the tunnel after an error`() =
        qaRemote(tunnelStatus = TunnelStatus.Error("boom")) { h ->
            onNodeWithText("Retry").performClick()
            assertEquals(1, h.tunnelStarts)
        }

    @Test
    fun `Connected with Local displayed - clicking Public switches the room to the tunnel`() =
        qaRemote(
            qaDisplayUrl = "",
            tunnelUrl = TUNNEL,
            tunnelStatus = TunnelStatus.Connected(TUNNEL),
        ) { h ->
            onNodeWithText("Public").performClick()
            assertEquals(listOf(TUNNEL), h.urlChanges)
        }

    @Test
    fun `Connected with Public displayed - clicking Local switches the room back`() =
        qaRemote(
            qaDisplayUrl = TUNNEL,
            tunnelUrl = TUNNEL,
            tunnelStatus = TunnelStatus.Connected(TUNNEL),
        ) { h ->
            onNodeWithText("Local").performClick()
            assertEquals(listOf(SERVER), h.urlChanges)
        }

    @Test
    fun `Disable Public Access stops the tunnel and moves the room back to local`() =
        qaRemote(
            qaDisplayUrl = TUNNEL,
            tunnelUrl = TUNNEL,
            tunnelStatus = TunnelStatus.Connected(TUNNEL),
        ) { h ->
            onNodeWithText("Disable Public Access").performClick()
            assertEquals(1, h.tunnelStops)
            assertEquals(listOf(SERVER), h.urlChanges)
        }

    // ── Close ───────────────────────────────────────────────────────────────────

    @Test
    fun `Close dismisses the dialog`() = qaRemote { h ->
        onNodeWithText("Close").performClick()
        assertEquals(1, h.dismissed)
    }

    // ── The cooldown field ──────────────────────────────────────────────────────

    @Test
    fun `changing the cooldown updates the setting`() = qaRemote { h ->
        onNode(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText("30")).performTextReplacement("45")
        waitForIdle()
        assertEquals(45, h.settings.rateLimitCooldownSeconds)
    }

    // ── The QR message field ────────────────────────────────────────────────────

    @Test
    fun `typing a QR message updates the setting`() = qaRemote { h ->
        onNode(hasSetTextAction() and hasText("")).performTextReplacement("Scan me!")
        waitForIdle()
        assertEquals("Scan me!", h.settings.qrCodeMessage)
    }

    @Test
    fun `the reset icon clears the QR message back to the default`() =
        qaRemote(initialQaSettings = QASettings(qrCodeMessage = "Custom message")) { h ->
            onNodeWithContentDescription("Reset to default").performClick()
            waitForIdle()
            assertEquals("", h.settings.qrCodeMessage)
        }
}
