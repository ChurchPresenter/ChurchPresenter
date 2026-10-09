@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.QASettings
import org.churchpresenter.server.TunnelStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Q&A window while public access is on and its QR code shows the public address: switching the
 * code back to the local address, and turning public access off, which also puts the code back.
 */
class QARemotePublicAccessTest {

    private companion object {
        const val SERVER = "http://192.168.1.50:8080"
        const val TUNNEL = "https://abc-def.trycloudflare.com"
    }

    private class Seen {
        val displayUrls = mutableListOf<String>()
        var stopped = 0
    }

    private fun publicAccess(block: androidx.compose.ui.test.ComposeUiTest.(Seen) -> Unit) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                QARemoteContent(
                    serverUrl = SERVER,
                    qaDisplayUrl = TUNNEL,
                    onQaDisplayUrlChanged = { seen.displayUrls += it },
                    apiKeyEnabled = true,
                    apiKey = "secret",
                    tunnelStatus = TunnelStatus.Connected(TUNNEL),
                    tunnelUrl = TUNNEL,
                    onStartTunnel = {},
                    onStopTunnel = { seen.stopped++ },
                    qaSettings = QASettings(),
                    onSettingsChange = {},
                    onDismiss = {},
                )
            }
        }
        block(seen)
    }

    @Test
    fun `Local puts the QR code back on the local address`() = publicAccess { seen ->
        onNodeWithText("Local").performScrollTo().performClick()
        assertEquals(listOf(SERVER), seen.displayUrls)
    }

    @Test
    fun `turning public access off stops the tunnel and puts the code back`() = publicAccess { seen ->
        onNodeWithText("Disable Public Access").performScrollTo().performClick()
        assertEquals(1, seen.stopped)
        assertEquals(listOf(SERVER), seen.displayUrls)
    }
}
