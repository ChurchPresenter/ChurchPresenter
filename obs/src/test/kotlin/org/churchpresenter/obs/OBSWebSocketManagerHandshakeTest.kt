package org.churchpresenter.obs

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Handshakes that go wrong in ways a real OBS does not produce but a wrong port can: something else
 * listening there, a proxy, an OBS that crashes mid-handshake. Each must end in ERROR with a reason,
 * never in a spinner that stays on CONNECTING.
 */
class OBSWebSocketManagerHandshakeTest {

    private val closeables = mutableListOf<AutoCloseable>()
    private val managers = mutableListOf<OBSWebSocketManager>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.disconnect() } }
        closeables.forEach { runCatching { it.close() } }
    }

    private fun obs(script: suspend DefaultWebSocketServerSession.() -> Unit) =
        ScriptedObs(script).also { closeables += it }

    /** Connects a fresh manager to [server] and waits for it to give up. */
    private fun failedAgainst(server: ScriptedObs): OBSWebSocketManager {
        val manager = OBSWebSocketManager().also { managers += it }
        manager.connect("127.0.0.1", server.port, "")
        val deadline = System.currentTimeMillis() + 10_000
        while (manager.status.value != OBSWebSocketManager.ConnectionStatus.ERROR) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("still ${manager.status.value} after 10s")
            }
            Thread.sleep(POLL_MS)
        }
        return manager
    }

    private companion object {
        const val POLL_MS = 10L
    }

    @Test
    fun `a binary greeting is refused`() {
        val manager = failedAgainst(obs { send(Frame.Binary(true, byteArrayOf(1, 2, 3))); incoming.receive() })
        assertEquals("Expected Hello frame", manager.errorMessage.value)
    }

    @Test
    fun `a greeting that is not JSON is refused`() {
        val manager = failedAgainst(obs { send(Frame.Text("HTTP/1.1 nonsense")); incoming.receive() })
        assertTrue(manager.errorMessage.value.isNotBlank(), "the parse failure must say something")
    }

    @Test
    fun `a Hello with no data is refused`() {
        val manager = failedAgainst(obs { send(Frame.Text("""{"op":0}""")); incoming.receive() })
        assertEquals("Hello frame has no data", manager.errorMessage.value)
    }

    @Test
    fun `an Identified that is not text is refused`() {
        val manager = failedAgainst(
            obs {
                send(Frame.Text(ScriptedObs.HELLO))
                incoming.receive()
                send(Frame.Binary(true, byteArrayOf(2)))
                incoming.receive()
            },
        )
        assertEquals("Expected Identified frame", manager.errorMessage.value)
    }

    @Test
    fun `obs hanging up mid-handshake is reported`() {
        val manager = failedAgainst(
            obs {
                send(Frame.Text(ScriptedObs.HELLO))
                incoming.receive()
                close(CloseReason(CloseReason.Codes.GOING_AWAY, "bye"))
            },
        )
        assertTrue(manager.errorMessage.value.isNotBlank(), "a hang-up must still give a reason")
    }
}
