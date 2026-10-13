@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.server.TunnelStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The warning beside the tunnel's controls in both remote windows: shown while no API key protects
 * the server, hidden once one does.
 */
class TunnelUnprotectedWarningTest {

    private companion object {
        const val SERVER = "http://192.168.1.50:8080"
        const val WARNING = "Public access opens every remote control to the internet"
    }

    @Test
    fun `the server is unprotected with the key off or blank, protected with one set`() {
        assertTrue(tunnelUnprotected(apiKeyEnabled = false, apiKey = ""))
        assertTrue(tunnelUnprotected(apiKeyEnabled = false, apiKey = "secret"))
        assertTrue(tunnelUnprotected(apiKeyEnabled = true, apiKey = "  "))
        assertFalse(tunnelUnprotected(apiKeyEnabled = true, apiKey = "secret"))
    }

    private fun qaRemote(apiKeyEnabled: Boolean, apiKey: String, block: ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    QARemoteContent(
                        serverUrl = SERVER,
                        qaDisplayUrl = "",
                        onQaDisplayUrlChanged = {},
                        apiKeyEnabled = apiKeyEnabled,
                        apiKey = apiKey,
                        tunnelStatus = TunnelStatus.Idle,
                        tunnelUrl = "",
                        onStartTunnel = {},
                        onStopTunnel = {},
                        qaSettings = QASettings(),
                        onSettingsChange = {},
                        onDismiss = {},
                    )
                }
            }
            block()
        }

    private fun presentationRemote(apiKeyEnabled: Boolean, apiKey: String, block: ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    PresentationRemoteDialogContent(
                        settings = AppSettings(),
                        onSettingsChange = {},
                        serverUrl = SERVER,
                        apiKeyEnabled = apiKeyEnabled,
                        apiKey = apiKey,
                        tunnelStatus = TunnelStatus.Idle,
                        tunnelUrl = "",
                        presentationDisplayUrl = "",
                        onPresentationDisplayUrlChanged = {},
                        onStartTunnel = {},
                        onStopTunnel = {},
                        onDismiss = {},
                        copyText = {},
                    )
                }
            }
            block()
        }

    @Test
    fun `the Q&A window warns while the key is off`() = qaRemote(apiKeyEnabled = false, apiKey = "") {
        onNodeWithText(WARNING, substring = true).assertExists()
    }

    @Test
    fun `the Q&A window drops the warning once a key protects the server`() =
        qaRemote(apiKeyEnabled = true, apiKey = "secret") {
            onNodeWithText(WARNING, substring = true).assertDoesNotExist()
        }

    @Test
    fun `the presentation remote warns while the key is on but blank`() =
        presentationRemote(apiKeyEnabled = true, apiKey = "") {
            onNodeWithText(WARNING, substring = true).assertExists()
        }

    @Test
    fun `the presentation remote drops the warning once a key protects the server`() =
        presentationRemote(apiKeyEnabled = true, apiKey = "secret") {
            onNodeWithText(WARNING, substring = true).assertDoesNotExist()
        }
}
