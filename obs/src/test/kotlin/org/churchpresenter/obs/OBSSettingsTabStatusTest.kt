@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.obs

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.ktor.websocket.Frame
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The connected and failed states of the connection card, reached by connecting to a [ScriptedObs]:
 * the manager's status cannot be posed from a fixture, so something has to answer.
 */
class OBSSettingsTabStatusTest {

    @Test
    fun `a connected tab offers Disconnect, which disconnects`() {
        val manager = OBSWebSocketManager()
        ScriptedObs {
            send(Frame.Text(ScriptedObs.HELLO))
            incoming.receive()
            send(Frame.Text(ScriptedObs.IDENTIFIED))
            while (incoming.receiveCatching().isSuccess) { /* hold the link open */ }
        }.use { server ->
            try {
                obsTab(initial = obsEnabled(), manager = manager) { _, obs ->
                    obs.connect("127.0.0.1", server.port, "")
                    waitUntil(timeoutMillis = TIMEOUT_MS) {
                        obs.status.value == OBSWebSocketManager.ConnectionStatus.CONNECTED
                    }
                    waitForIdle()
                    onNodeWithText(ObsLabel.CONNECTED).assertExists("the status line must say connected")
                    onNodeWithText(ObsLabel.CONNECT).assertDoesNotExist()

                    onNodeWithText(ObsLabel.DISCONNECT).performClick()
                    waitForIdle()

                    assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
                    onNodeWithText(ObsLabel.CONNECT).assertExists("and Connect must be offered again")
                }
            } finally {
                manager.disconnect()
            }
        }
    }

    @Test
    fun `a failed connection shows the reason beside Error`() {
        val manager = OBSWebSocketManager()
        ScriptedObs { send(Frame.Text("""{"op":0}""")); incoming.receive() }.use { server ->
            try {
                obsTab(initial = obsEnabled(), manager = manager) { _, obs ->
                    obs.connect("127.0.0.1", server.port, "")
                    waitUntil(timeoutMillis = TIMEOUT_MS) {
                        obs.status.value == OBSWebSocketManager.ConnectionStatus.ERROR
                    }
                    waitForIdle()
                    onNode(hasText("${ObsLabel.ERROR}: Hello frame has no data")).assertExists()
                    onNodeWithText(ObsLabel.CONNECT).assertExists("a failed attempt can be retried")
                }
            } finally {
                manager.disconnect()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
