package org.churchpresenter.stt

import io.socket.client.Socket
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the manager does with each event the STT server sends.
 *
 * These handlers used to be unreachable: they are registered inside `connect()` on a socket.io
 * `Socket`, which can only be had by dialling a real server. `installHandlers` takes the two-method
 * [SttSocket] instead, so the fake below can fire an event and the assertion is on what the manager
 * actually did with it.
 */
class STTManagerHandlerTest {

    /** Records subscriptions and emits, and lets a test fire an event at whatever subscribed. */
    private class FakeSocket : SttSocket {
        val emitted = mutableListOf<String>()
        private val handlers = mutableMapOf<String, (Array<out Any>) -> Unit>()

        override fun on(event: String, listener: (Array<out Any>) -> Unit) {
            handlers[event] = listener
        }

        override fun emit(event: String) {
            emitted += event
        }

        fun fire(event: String, vararg args: Any) {
            handlers.getValue(event)(args)
        }

        fun subscribed(): Set<String> = handlers.keys
    }

    private fun install(): Pair<STTManager, FakeSocket> {
        val manager = STTManager()
        val socket = FakeSocket()
        // A port nothing is listening on: the connect handler fires a REST fetch and a capture poll
        // at it, and both are best-effort, so they fail quietly and the transitions still happen.
        manager.installHandlers(socket, "http://127.0.0.1:1")
        return manager to socket
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("timed out waiting for $what")
    }

    // ── What it subscribes to ───────────────────────────────────────────────────

    @Test
    fun `every event the server can send is subscribed to`() {
        val (_, socket) = install()

        assertEquals(
            setOf(
                Socket.EVENT_CONNECT,
                Socket.EVENT_DISCONNECT,
                Socket.EVENT_CONNECT_ERROR,
                "transcription_update",
                "translation_update",
                "word_highlighting_update",
            ),
            socket.subscribed(),
        )
    }

    // ── Connect ─────────────────────────────────────────────────────────────────

    @Test
    fun `coming up asks for the backlog the tab would otherwise start empty without`() {
        val (manager, socket) = install()

        socket.fire(Socket.EVENT_CONNECT)

        awaitUntil("the connection to be reported") { manager.connected.value }
        assertEquals(listOf("request_all_entries", "request_all_translation_entries"), socket.emitted)
        manager.disconnect()
    }

    @Test
    fun `coming up clears the connecting and error flags`() {
        val (manager, socket) = install()
        manager.applyConnectError()

        socket.fire(Socket.EVENT_CONNECT)

        awaitUntil("the connection to be reported") { manager.connected.value }
        assertFalse(manager.connectError.value)
        assertFalse(manager.connecting.value)
        manager.disconnect()
    }

    // ── Disconnect ──────────────────────────────────────────────────────────────

    @Test
    fun `a drop we did not ask for is reported as reconnecting`() {
        val (manager, socket) = install()
        manager.applyConnected()

        socket.fire(Socket.EVENT_DISCONNECT, "transport close")

        awaitUntil("the reconnect to be reported") { manager.reconnecting.value }
        assertFalse(manager.connected.value)
    }

    @Test
    fun `disconnecting on purpose is not reported as reconnecting`() {
        val (manager, socket) = install()
        manager.applyConnected()

        socket.fire(Socket.EVENT_DISCONNECT, "io client disconnect")

        awaitUntil("the disconnect to land") { !manager.connected.value }
        assertFalse(manager.reconnecting.value, "we closed the link ourselves")
    }

    @Test
    fun `a disconnect with no reason at all is still handled`() {
        val (manager, socket) = install()
        manager.applyConnected()

        socket.fire(Socket.EVENT_DISCONNECT)

        awaitUntil("the disconnect to land") { !manager.connected.value }
    }

    // ── Connect error ───────────────────────────────────────────────────────────

    @Test
    fun `a failed dial is reported instead of leaving the ui spinning`() {
        val (manager, socket) = install()

        socket.fire(Socket.EVENT_CONNECT_ERROR)

        awaitUntil("the failure to be reported") { manager.connectError.value }
        assertFalse(manager.connecting.value)
    }

    // ── Payload events ──────────────────────────────────────────────────────────

    @Test
    fun `a transcription update reaches the segment list`() {
        val (manager, socket) = install()

        socket.fire(
            "transcription_update",
            JSONObject("""{"segments":[{"id":0,"timestamp":"00:00","text":"a spoken line","completed":true}]}"""),
        )

        awaitUntil("the caption to arrive") { manager.segments.any { it.text == "a spoken line" } }
    }

    @Test
    fun `a translation update reaches the translation list`() {
        val (manager, socket) = install()

        socket.fire(
            "translation_update",
            JSONObject("""{"segments":[{"id":0,"translated_text":"une ligne","completed":true}]}"""),
        )

        awaitUntil("the translation to arrive") { manager.translationSegments.any { it.text == "une ligne" } }
    }

    @Test
    fun `a word highlighting update reaches the word list`() {
        val (manager, socket) = install()

        socket.fire(
            "word_highlighting_update",
            JSONObject("""{"enabled":true,"words":[{"word":"grace","color":"#ff0000"}]}"""),
        )

        awaitUntil("the word to arrive") { manager.highlightedWords.any { it.word == "grace" } }
        assertTrue(manager.wordHighlightingEnabled.value)
    }

    // ── Payloads the server should not send, but might ──────────────────────────

    @Test
    fun `a payload-carrying event with no payload is ignored rather than crashing`() {
        val (manager, socket) = install()

        socket.fire("transcription_update")
        socket.fire("translation_update")
        socket.fire("word_highlighting_update")

        assertTrue(manager.segments.isEmpty())
        assertTrue(manager.translationSegments.isEmpty())
    }

    @Test
    fun `a payload of the wrong type is ignored rather than crashing`() {
        val (manager, socket) = install()

        socket.fire("transcription_update", "not an object")
        socket.fire("translation_update", 42)
        socket.fire("word_highlighting_update", "nonsense")

        assertTrue(manager.segments.isEmpty())
        assertTrue(manager.translationSegments.isEmpty())
    }
}
