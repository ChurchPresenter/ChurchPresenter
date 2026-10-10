package org.churchpresenter.bibleengine

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.bibleengine.engine.ScriptureEvent
import org.churchpresenter.bibleengine.engine.ScriptureReference
import org.churchpresenter.bibleengine.socket.Broadcaster
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BroadcasterTest {

    private class FakeSession : WebSocketSession {
        val sent = Channel<Frame>(Channel.UNLIMITED)
        override val coroutineContext: CoroutineContext = Job()
        override var masking = false
        override var maxFrameSize = Long.MAX_VALUE
        override val incoming = Channel<Frame>()
        override val outgoing = sent
        override val extensions: List<WebSocketExtension<*>> = emptyList()
        override suspend fun flush() = Unit
        override fun terminate() = Unit

        fun next(): String = runBlocking { withTimeout(5_000) { (sent.receive() as Frame.Text).readText() } }
    }

    private val broadcaster = Broadcaster()

    @AfterTest
    fun tearDown() {
        broadcaster.close()
    }

    @Test
    fun `a late joiner is replayed the latest status and version`() {
        broadcaster.broadcastStatus("""{"type":"engine_status","n":1}""")
        broadcaster.broadcastStatus("""{"type":"engine_status","n":2}""")
        broadcaster.broadcastVersion("""{"type":"version","v":"KJV"}""")
        val session = FakeSession()

        broadcaster.register(session)

        assertEquals("""{"type":"engine_status","n":2}""", session.next())
        assertEquals("""{"type":"version","v":"KJV"}""", session.next())
    }

    @Test
    fun `a version and a status go to every connected session`() {
        val a = FakeSession()
        val b = FakeSession()
        broadcaster.register(a)
        broadcaster.register(b)

        broadcaster.broadcastVersion("v1")
        broadcaster.broadcastStatus("s1")

        assertEquals(listOf("v1", "s1"), listOf(a.next(), a.next()))
        assertEquals(listOf("v1", "s1"), listOf(b.next(), b.next()))
    }

    @Test
    fun `an event is encoded once for every session`() {
        val session = FakeSession()
        broadcaster.register(session)

        broadcaster.broadcast(
            ScriptureEvent(
                type = "scripture.detected", id = "live",
                reference = ScriptureReference(43, "John", 3, 16, null, "John 3:16", "B043C003V016", null, "hebrew"),
                verseText = "For God so loved the world", confidence = 0.95,
                matchType = "explicit", translation = "KJV",
            ),
        )

        val text = session.next()
        assertTrue(text.contains("\"displayRef\":\"John 3:16\""), text)
    }

    @Test
    fun `an unregistered session gets nothing more and unregistering twice is harmless`() {
        val gone = FakeSession()
        val kept = FakeSession()
        broadcaster.register(gone)
        broadcaster.register(kept)

        broadcaster.unregister(gone)
        broadcaster.unregister(gone)
        broadcaster.broadcastStatus("after")

        assertEquals("after", kept.next())
        assertTrue(gone.sent.tryReceive().isFailure)
    }
}
