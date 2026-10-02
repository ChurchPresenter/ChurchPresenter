package org.churchpresenter.stt

import io.socket.client.Socket
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What each socket event does, driven through [STTManager.onSocketEvent] — the one function every
 * listener `connect()` registers calls — rather than through a live STT server.
 *
 * The events land on the main dispatcher, as the socket's own callbacks do, so each test waits for
 * the state the event sets rather than for a duration.
 */
class STTManagerSocketEventsTest {

    private val created = mutableListOf<STTManager>()

    private fun manager() = STTManager().also { created.add(it) }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    /** An address nothing listens on, so the REST calls a connect makes fail at once. */
    private val deadUrl: String by lazy {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        "http://127.0.0.1:$port"
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.onSpinWait()
        }
    }

    private fun STTManager.event(name: String, vararg args: Any?, emitted: MutableList<String> = mutableListOf()) =
        onSocketEvent(name, args, deadUrl) { emitted += it }

    @Test
    fun `listening covers every event the manager handles`() {
        assertEquals(
            setOf(
                Socket.EVENT_CONNECT, Socket.EVENT_DISCONNECT, Socket.EVENT_CONNECT_ERROR,
                "transcription_update", "translation_update", "word_highlighting_update",
            ),
            STTManager.SOCKET_EVENTS.toSet(),
        )
    }

    @Test
    fun `connecting goes live and asks for everything said so far`() {
        val stt = manager()
        val emitted = mutableListOf<String>()

        stt.event(Socket.EVENT_CONNECT, emitted = emitted)

        assertEquals(listOf("request_all_entries", "request_all_translation_entries"), emitted)
        awaitUntil("the connection to show") { stt.connected.value }
    }

    @Test
    fun `a dropped connection says it is reconnecting`() {
        val stt = manager()
        stt.applyConnected()

        stt.event(Socket.EVENT_DISCONNECT, "transport close")

        awaitUntil("the drop to show") { stt.reconnecting.value }
        assertFalse(stt.connected.value)
    }

    @Test
    fun `a server that cannot be reached is flagged`() {
        val stt = manager()
        stt.applyConnecting()

        stt.event(Socket.EVENT_CONNECT_ERROR)

        awaitUntil("the failure to show") { stt.connectError.value }
    }

    @Test
    fun `each update reaches the transcript it belongs to`() {
        val stt = manager()

        stt.event("transcription_update", JSONObject("""{"segments":[{"id":1,"text":"In the beginning"}]}"""))
        stt.event("translation_update", JSONObject("""{"segments":[{"id":1,"translated_text":"Au commencement"}]}"""))
        stt.event("word_highlighting_update", JSONObject("""{"words":[{"word":"beginning","color":"#ff0000"}]}"""))

        awaitUntil("the transcript") { stt.segments.singleOrNull()?.text == "In the beginning" }
        awaitUntil("the translation") { stt.translationSegments.singleOrNull()?.text == "Au commencement" }
        awaitUntil("the highlighted word") { stt.highlightedWords.singleOrNull()?.word == "beginning" }
    }

    @Test
    fun `a payload that is not an object, or no payload at all, is ignored`() {
        val stt = manager()
        stt.event("transcription_update", JSONObject("""{"segments":[{"id":1,"text":"kept"}]}"""))
        awaitUntil("the first transcript") { stt.segments.isNotEmpty() }

        stt.event("transcription_update", "not an object")
        stt.event("transcription_update")
        stt.event("an_event_nobody_handles", JSONObject("""{"segments":[]}"""))
        // A marker queued behind them: once it lands, anything they queued has landed too.
        stt.event("translation_update", JSONObject("""{"segments":[{"id":1,"text":"marker"}]}"""))
        awaitUntil("the marker") { stt.translationSegments.isNotEmpty() }

        assertEquals("kept", stt.segments.single().text)
        assertTrue(stt.inProgressText.value.isEmpty())
    }
}
