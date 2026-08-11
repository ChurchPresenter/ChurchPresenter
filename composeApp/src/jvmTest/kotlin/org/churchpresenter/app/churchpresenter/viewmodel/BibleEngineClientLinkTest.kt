package org.churchpresenter.app.churchpresenter.viewmodel

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The link to the Bible Lookup Engine, driven against a REAL WebSocket server.
 *
 * The client's contract with the engine is not just "receive JSON": it has to announce its tuning
 * the moment it connects, push every later tuning change, and — the part that matters mid-service —
 * climb back on its own when the engine restarts, without the operator touching anything. None of
 * that is observable without a server on the other end, so a throwaway Ktor server stands in for
 * the engine here, the same approach [org.churchpresenter.app.churchpresenter.server.CompanionServerTest]
 * takes.
 *
 * Message parsing is covered separately by [BibleEngineClientMessageTest].
 */
class BibleEngineClientLinkTest {

    /** One [BibleEngineClient.onScripture] call, captured. */
    private data class Detection(val bookId: Int, val chapter: Int, val verseStart: Int, val verseText: String)

    private val detections = LinkedBlockingQueue<Detection>()
    private val created = mutableListOf<BibleEngineClient>()
    private lateinit var engine: FakeEngine

    /**
     * A stand-in for the engine's `/bible-engine` endpoint: records what it is sent, pushes back on
     * demand.
     *
     * Binds port 0 and reports back what it got, rather than taking a port picked from a probe
     * socket that was already closed: in the gap before the bind, anything else in the suite that
     * starts a server can take that port and this one dies with `Address already in use`.
     */
    private class FakeEngine {
        val received = LinkedBlockingQueue<String>()
        val sessions = CopyOnWriteArrayList<DefaultWebSocketServerSession>()
        val connectionCount = AtomicInteger()

        /** Long enough for a healthy loopback handshake; only reached when the fixture is broken. */
        private val PROBE_TIMEOUT_MS = 5_000L

        /** Valid only after [start]. */
        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            install(WebSockets)
            routing {
                webSocket("/bible-engine") {
                    sessions.add(this)
                    connectionCount.incrementAndGet()
                    try {
                        for (frame in incoming) if (frame is Frame.Text) received.add(frame.readText())
                    } finally {
                        sessions.remove(this)
                    }
                }
            }
        }

        fun start() {
            server.start(wait = false)
            // resolvedConnectors() suspends until the bind completes, so this is the port Netty is
            // actually listening on.
            port = runBlocking { server.engine.resolvedConnectors().first().port }
            proveUsable()
        }

        /**
         * Opens and closes one real WebSocket against the fixture before any test uses it.
         *
         * **A bound port is not a working endpoint**, and this class has failed on exactly that gap.
         * A full-suite run answered every connect with `Connection reset` — not *refused* — for the
         * whole 15s budget, so something was listening on that port and tearing the upgrade down.
         * `resolvedConnectors()` cannot see that, and the client then looked guilty for a fault that
         * was never its own.
         *
         * This does not claim to know the cause; three explanations have already been disproved (the
         * server not being up — a reset is not a refusal; a pooled connection to a closed port; and
         * port reuse across tests — 120 rounds of the start/stop/port-0 cycle produced neither reuse
         * nor a single failure). What it does is move the failure to where it can be read: if the
         * fixture cannot talk to itself, setup says so and names the port, in under a second instead
         * of after a 15s timeout that blames the wrong component.
         */
        private fun proveUsable() {
            val probe = HttpClient(CIO) { install(ClientWebSockets) }
            val reached = runCatching {
                runBlocking {
                    withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                        probe.webSocket(host = "127.0.0.1", port = port, path = "/bible-engine") { }
                        true
                    }
                }
            }
            runCatching { probe.close() }
            // The probe's own connection must not be counted as a client under test.
            sessions.clear()
            received.clear()
            connectionCount.set(0)

            val answered = reached.getOrElse { cause ->
                throw IllegalStateException(
                    "fake engine on port $port is bound but unusable — " +
                        "${cause.javaClass.simpleName}: ${cause.message}",
                    cause,
                )
            }
            checkNotNull(answered) {
                "fake engine on port $port accepted no WebSocket within ${PROBE_TIMEOUT_MS}ms"
            }
        }

        fun push(text: String) = runBlocking { sessions.toList().forEach { it.send(Frame.Text(text)) } }
        fun dropConnections() = runBlocking { sessions.toList().forEach { it.close() } }
        fun stop() = server.stop(0, 0)
    }

    @BeforeTest
    fun startEngine() {
        engine = FakeEngine()
        engine.start()
    }

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        runCatching { engine.stop() }
        detections.clear()
    }

    /**
     * The production reconnect floor is 2s, so a client that has to retry even once cannot come up
     * inside this class's time budget — and a single failed connect under load was enough to make
     * `connect()` below time out, which is what made this class flaky. A few milliseconds keeps the
     * retry *shape* (doubling, jitter, cap) while costing nothing. The 2s default and the shape
     * itself are pinned by [BibleEngineRetryDelayTest], which needs no server at all.
     */
    private fun client(retryFloorMs: Long = 25L): BibleEngineClient =
        BibleEngineClient(
            onScripture = { e -> detections.add(Detection(e.bookId, e.chapter, e.verseStart, e.verseText)) },
            onVersion = {},
            retryFloorMs = retryFloorMs,
        ).also { created.add(it) }

    /**
     * Connects [this] to the fake engine (no in-process engine) and waits for the link to come up.
     *
     * Waits for the engine to have registered the session as well as for the client to report
     * itself connected: the two happen on opposite ends of the handshake, and `push` sends to the
     * sessions the engine currently holds, so pushing on the strength of the client's view alone
     * can deliver a frame to nobody and leave the test waiting for something already dropped.
     */
    private fun BibleEngineClient.connect(level: String = "balanced", speed: String = "balanced") {
        start(
            sttUrl = "http://127.0.0.1:1", bibleRoot = "", bibleFiles = emptyList(),
            runLocal = false, host = "127.0.0.1", port = engine.port, level = level, continuationSpeed = speed
        )
        awaitUntil("the engine link to come up") { connected.value && engine.sessions.isNotEmpty() }
    }

    private fun nextFrame(timeoutSeconds: Long = 10): String =
        engine.received.poll(timeoutSeconds, TimeUnit.SECONDS) ?: throw AssertionError("the engine was sent nothing")

    private fun nextDetection(timeoutSeconds: Long = 10): Detection =
        detections.poll(timeoutSeconds, TimeUnit.SECONDS) ?: throw AssertionError("no detection reached the app")

    private fun awaitUntil(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    // ── Connecting ──────────────────────────────────────────────────────────────

    @Test
    fun `the client connects to a running engine`() {
        val c = client()

        c.connect()

        // The live session, not the running total: this class sets a 25ms retry floor precisely so a
        // connect that loses a race retries instead of failing, and a retried attempt leaves a
        // second entry in that total for ever. What must hold is that the client ends up holding one
        // link — a dead attempt's session is removed when its handler unwinds, so this settles at 1
        // and would never settle if the client stacked links.
        awaitUntil("the client to be holding exactly one link") { engine.sessions.size == 1 }
        assertFalse(c.startFailed.value, "connecting to a remote engine never starts one in-process")
    }

    @Test
    fun `the client announces its tuning the moment it connects`() {
        val c = client()
        c.connect(level = "aggressive", speed = "fast")
        assertEquals(
            """{"type":"set_tuning","level":"aggressive","continuationSpeed":"fast"}""",
            nextFrame(),
            "an engine that is never told the level would run at its own default"
        )
    }

    @Test
    fun `starting again drops the previous link rather than stacking a second one`() {
        val c = client()
        c.connect()
        nextFrame()

        c.start(
            sttUrl = "http://127.0.0.1:1", bibleRoot = "", bibleFiles = emptyList(),
            runLocal = false, host = "127.0.0.1", port = engine.port, level = "off"
        )
        awaitUntil("the second link to come up") { c.connected.value && engine.connectionCount.get() == 2 }
        awaitUntil("the first connection to be dropped") { engine.sessions.size == 1 }
    }

    // ── Pushing tuning changes ──────────────────────────────────────────────────

    @Test
    fun `changing the level pushes it to the engine`() {
        val c = client()
        c.connect(level = "off", speed = "balanced")
        assertEquals("""{"type":"set_tuning","level":"off","continuationSpeed":"balanced"}""", nextFrame())

        c.setLevel("aggressive")
        assertEquals("""{"type":"set_tuning","level":"aggressive","continuationSpeed":"balanced"}""", nextFrame())
    }

    @Test
    fun `changing the verse speed pushes it alongside the current level`() {
        val c = client()
        c.connect(level = "aggressive", speed = "balanced")
        nextFrame()

        c.setContinuationSpeed("fast")
        assertEquals(
            """{"type":"set_tuning","level":"aggressive","continuationSpeed":"fast"}""",
            nextFrame(),
            "the two settings travel together; pushing one must not reset the other"
        )
    }

    @Test
    fun `tuning changes made while disconnected are applied on the next connect`() {
        val c = client()
        c.setLevel("aggressive")        // no session yet — must not throw
        c.setContinuationSpeed("fast")

        c.connect(level = "cautious", speed = "slow")

        assertEquals(
            """{"type":"set_tuning","level":"cautious","continuationSpeed":"slow"}""",
            nextFrame(),
            "start() carries the settings; a pre-connect push is dropped rather than replayed"
        )
    }

    // ── Receiving detections ────────────────────────────────────────────────────

    @Test
    fun `a detection from the engine reaches the app`() {
        val c = client()
        c.connect()
        engine.push(
            """{"type":"scripture.detected","reference":{"bookId":43,"chapter":3,"verseStart":16},
                "verseText":"For God so loved the world"}"""
        )
        val d = nextDetection()
        assertEquals(43, d.bookId)
        assertEquals(3, d.chapter)
        assertEquals(16, d.verseStart)
        assertEquals("For God so loved the world", d.verseText)
    }

    @Test
    fun `detections keep arriving after the first one`() {
        val c = client()
        c.connect()
        repeat(3) { i ->
            engine.push("""{"type":"scripture.detected","reference":{"bookId":43,"chapter":3,"verseStart":${16 + i}}}""")
        }
        assertEquals(listOf(16, 17, 18), List(3) { nextDetection().verseStart }, "the read loop must not stop after one frame")
    }

    @Test
    fun `a malformed frame does not take the link down`() {
        val c = client()
        c.connect()
        engine.push("not json")
        engine.push("""{"type":"scripture.detected","reference":{"bookId":43,"chapter":3,"verseStart":16}}""")

        assertEquals(16, nextDetection().verseStart, "one bad frame from the engine must not end the service's auto-follow")
        assertTrue(c.connected.value)
    }

    @Test
    fun `the engine's stt health arrives over the link`() {
        val c = client()
        c.connect()
        assertNull(c.engineSttConnected.value)

        engine.push("""{"type":"engine_status","sttConfigured":true,"sttConnected":true}""")
        awaitUntil("the engine status to arrive") { c.engineSttConnected.value == true }
    }

    // ── Dropping ────────────────────────────────────────────────────────────────

    @Test
    fun `the client climbs back on its own after the engine drops it`() {
        val c = client()
        c.connect()
        nextFrame()

        engine.dropConnections()

        // Not waiting on the link going *down* first: the reconnect floor here is milliseconds, so
        // the down state can come and go between two polls. The connection count only ever climbs.
        awaitUntil("the client to reconnect unaided") { engine.connectionCount.get() == 2 && c.connected.value }
        assertEquals(
            """{"type":"set_tuning","level":"balanced","continuationSpeed":"balanced"}""",
            nextFrame(),
            "a reconnected client re-announces its tuning; an engine told nothing runs at its own default"
        )
    }

    @Test
    fun `the engine's stt health goes back to unknown while the link is down`() {
        val c = client()
        c.connect()
        engine.push("""{"type":"engine_status","sttConnected":true}""")
        awaitUntil("the engine status to arrive") { c.engineSttConnected.value == true }

        engine.dropConnections()

        awaitUntil("the stale status to be dropped") { c.engineSttConnected.value == null }
    }

    // ── Stopping ────────────────────────────────────────────────────────────────

    @Test
    fun `stopping closes the link and clears its state`() {
        val c = client()
        c.connect()
        engine.push("""{"type":"engine_status","sttConnected":true}""")
        awaitUntil("the engine status to arrive") { c.engineSttConnected.value == true }

        c.stop()

        assertFalse(c.connected.value)
        assertNull(c.engineSttConnected.value)
        awaitUntil("the engine to see the client go") { engine.sessions.isEmpty() }
    }

    @Test
    fun `a stopped client stops delivering detections`() {
        val stopped = client()
        stopped.connect()
        stopped.stop()
        awaitUntil("the link to close") { engine.sessions.isEmpty() }

        // Pushed into the void: nobody is listening. If the stopped client were still attached it
        // would be first into the shared queue, ahead of the live client's verse below.
        engine.push("""{"type":"scripture.detected","reference":{"bookId":43,"chapter":3,"verseStart":16}}""")

        val live = client()
        live.connect()
        engine.push("""{"type":"scripture.detected","reference":{"bookId":43,"chapter":3,"verseStart":17}}""")

        assertEquals(17, nextDetection().verseStart, "a stopped client must not put verses on screen")
    }

    @Test
    fun `disposing leaves the client shut down`() {
        val c = client()
        c.connect()
        c.dispose()
        assertFalse(c.connected.value)
        awaitUntil("the engine to see the client go") { engine.sessions.isEmpty() }
    }
}
