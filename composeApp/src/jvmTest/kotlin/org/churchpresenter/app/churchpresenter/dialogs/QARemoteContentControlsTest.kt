@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.retypeNumberField
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class QARemoteContentControlsTest {

    private companion object {
        const val SERVER = "http://192.168.1.50:8080"
        const val TUNNEL = "https://abc-def.trycloudflare.com"
    }

    @OptIn(ExperimentalTestApi::class)
    private fun qaRemoteTab(
        qaSettings: QASettings = QASettings(),
        serverUrl: String = SERVER,
        qaDisplayUrl: String = "",
        tunnelStatus: TunnelStatus = TunnelStatus.Idle,
        tunnelUrl: String = "",

        block: ComposeUiTest.(get: () -> QASettings, qaDisplayUrlChanges: () -> List<String>) -> Unit,
    ) = runComposeUiTest {
        var current = qaSettings
        val qaDisplayUrlChanges = mutableListOf<String>()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(AppSettings(qaSettings = current)) }
                QARemoteContent(
                    serverUrl = serverUrl,
                    qaDisplayUrl = qaDisplayUrl,
                    onQaDisplayUrlChanged = { qaDisplayUrlChanges += it },
                    apiKeyEnabled = false,
                    apiKey = "",
                    tunnelStatus = tunnelStatus,
                    tunnelUrl = tunnelUrl,
                    onStartTunnel = {},
                    onStopTunnel = {},
                    qaSettings = state.qaSettings,
                    onSettingsChange = { transform -> state = transform(state); current = state.qaSettings },
                    onDismiss = {},
                )
            }
        }
        block({ current }, { qaDisplayUrlChanges })
    }

    // ── Branches the other test's fixtures never hit ────────────────────────────────────────────────

    @Test
    fun `a display address that exactly equals the server also counts as local`() =
        qaRemoteTab(
            qaDisplayUrl = SERVER,
            tunnelUrl = TUNNEL,
            tunnelStatus = TunnelStatus.Connected(TUNNEL),
        ) { _, changes ->
            onNodeWithText("Public").performClick()
            waitForIdle()
            assertEquals(listOf(TUNNEL), changes(), "the Public button must still switch to the tunnel")
        }

    @Test
    fun `an admin address that cannot be built shows no QR image`() =
        qaRemoteTab(serverUrl = "", qaDisplayUrl = "https://mydisplay.example") { _, _ ->
            onNodeWithText("https://mydisplay.example/qa").assertIsDisplayed()
            onNodeWithContentDescription("Admin Panel").assertDoesNotExist()
        }

    @Test
    fun `with no address at all the server hint replaces every styling control`() =
        qaRemoteTab(serverUrl = "") { _, _ ->
            onNodeWithText("Start the companion server to enable Q&A").assertIsDisplayed()
            onNodeWithText("Display Styling").assertDoesNotExist()
            onNodeWithText("Position").assertDoesNotExist()
        }

    // ── Clamped-range fields ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a cooldown outside 0 to 600 is not stored`() = qaRemoteTab { get, _ ->
        retypeNumberField(showing = 30, to = 900)
        assertEquals(30, get().rateLimitCooldownSeconds, "900 is above the 0..600 range")
        retypeNumberField(showing = 900, to = 600)
        assertEquals(600, get().rateLimitCooldownSeconds, "600 is the top of the range and is accepted")
    }

}

// ── Locators local to this file ────────────────────────────────────────────────────────────────────

