package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.core.models.songs.SongItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * The mobile-companion / Instance Link server, exercised against a REAL running instance.
 *
 * `CompanionServer.start()` builds its own Netty server rather than exposing a separable Ktor
 * module, so Ktor's `testApplication` harness can't reach it without a production refactor.
 * Starting it on a free port and driving it over real HTTP/WebSocket tests the same pipeline a
 * phone or a follower instance actually talks to — including the connect snapshot, which is the
 * source of a whole class of "follower shows stale content" bugs.
 *
 * Not covered, deliberately: that a session which dies *during* its snapshot still releases its
 * broadcast collector. The collector is launched before the snapshot so nothing emitted meanwhile is
 * lost, which puts it outside the handler's own `try`/`finally` — it is cancelled from the session
 * job's completion instead. Reaching that path means making a snapshot `send` fail, and the only
 * lever a test has is closing the client early, which races the snapshot rather than landing inside
 * it: sometimes the frames are already buffered and nothing throws. A test that only sometimes
 * exercises what it claims to is worse than this note.
 */
class CompanionServerTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun startServer() {
        server = CompanionServer()
        // Well outside the default 8765 so a running dev instance can't be hit by accident;
        // findFreePort walks upward from here if it is taken.
        server.start(port = testPort(39_517))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")

        client = HttpClient(CIO) { install(WebSockets) }
    }

    @AfterTest
    fun stopServer() {
        runCatching { client.close() }
        runCatching { server.stop() }
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    /**
     * Collects the connect snapshot: every frame until [quietMs] passes with no new one.
     *
     * The snapshot has no terminator frame to wait for, so an idle window is the only way to know
     * it has finished — but it is the whole cost of these tests, so keep it short. The server
     * writes the snapshot back to back on connect and this is all loopback, so frames land within
     * a millisecond or two of each other; the window only has to outlast that gap, not a network.
     */
    private fun connectAndCollect(quietMs: Long = 250): List<JsonObject> = runBlocking {
        val received = mutableListOf<JsonObject>()
        withTimeoutOrNull(quietMs + 10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                while (true) {
                    val frame = withTimeoutOrNull(quietMs) { incoming.receive() } ?: break
                    if (frame is Frame.Text) {
                        runCatching { json.parseToJsonElement(frame.readText()) as JsonObject }
                            .getOrNull()?.let { received.add(it) }
                    }
                }
            }
        }
        received
    }

    private fun List<JsonObject>.types(): List<String> =
        mapNotNull { it["type"]?.jsonPrimitive?.content }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    @Test
    fun `the server starts and reports a reachable url`() {
        assertTrue(server.isRunning.value)
        assertTrue(server.serverUrl.value.startsWith("http://"), "got ${server.serverUrl.value}")
    }

    @Test
    fun `starting an already-running server is a no-op`() {
        val urlBefore = server.serverUrl.value
        server.start(port = testPort(39_600))
        assertEquals(urlBefore, server.serverUrl.value, "a second start must not rebind or move the port")
    }

    // ── REST ────────────────────────────────────────────────────────────────────

    @Test
    fun `the info endpoint answers`() = runBlocking {
        val response = client.get(url(Constants.ENDPOINT_INFO))
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotBlank())
    }

    @Test
    fun `the songs endpoint returns a well-formed catalog`() = runBlocking {
        val response = client.get(url(Constants.ENDPOINT_SONGS))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertTrue("song-book" in body, "the catalog uses the kebab-case wire name: $body")
        assertTrue("total" in body)
    }

    @Test
    fun `the schedule endpoint reflects what the app pushed`() = runBlocking {
        server.updateSchedule(
            listOf(
                ScheduleItem.SongItem(id = "s1", songNumber = 1, title = "Amazing Grace", songbook = "Hymnal"),
                ScheduleItem.LabelItem(
                    id = "l1",
                    text = "Offering",
                    textColor = "#FFFFFF",
                    backgroundColor = "#000000",
                ),
            ),
        )
        val body = client.get(url(Constants.ENDPOINT_SCHEDULE)).bodyAsText()
        assertTrue("Amazing Grace" in body, "the pushed schedule should be served: $body")
        assertTrue("Offering" in body)
    }

    @Test
    fun `an unknown path returns 404 rather than an error page`() = runBlocking {
        assertEquals(HttpStatusCode.NotFound, client.get(url("/api/definitely-not-a-route")).status)
    }

    // ── WebSocket connect snapshot ──────────────────────────────────────────────

    /**
     * The unconditional part of the connect snapshot. These exist so a (re)connecting follower
     * refreshes the caches it keeps on disk; a missing event means that follower serves stale
     * content indefinitely, with no error and nothing in any log to notice by.
     *
     * `bible_updated`, `presentation_updated` and `pictures_updated` are deliberately NOT asserted
     * here — they are sent only when the corresponding content is actually loaded, and this server
     * is started empty.
     */
    @Test
    fun `a connecting client is sent the unconditional catalogue snapshot`() {
        val types = connectAndCollect().types()
        for (expected in listOf(
            Constants.WS_EVENT_SONGS_UPDATED,
            Constants.WS_EVENT_SCHEDULE_UPDATED,
            Constants.WS_EVENT_BACKGROUNDS_UPDATED,
            // Both of the pure invalidation signals belong here. They carry no payload, so there is
            // no "nothing is loaded" case to withhold them for — a follower needs them on every
            // reconnect or it trusts a cache the primary may have changed while it was away.
            Constants.WS_EVENT_SECONDARY_BIBLE_UPDATED,
        )) {
            assertTrue(expected in types, "connect snapshot is missing $expected; got $types")
        }
    }

    @Test
    fun `content-dependent snapshot events are withheld when nothing is loaded`() {
        // Sending these with no content behind them would have a follower cache an empty catalog.
        val types = connectAndCollect().types()
        for (conditional in listOf(
            Constants.WS_EVENT_BIBLE_UPDATED,
            Constants.WS_EVENT_PRESENTATION_UPDATED,
            Constants.WS_EVENT_PICTURES_UPDATED,
        )) {
            assertFalse(conditional in types, "$conditional was sent with no content loaded; got $types")
        }
    }

    /**
     * A follower must not be left trusting a secondary bible it cached in an earlier session.
     *
     * `secondary_bible_updated` is broadcast live whenever the secondary bible changes, but it used
     * to be absent from the connect snapshot — the exact bug already found and fixed for
     * `backgrounds_updated`. A follower that reconnected (app restart, network blip, or the normal
     * backoff reconnect) therefore kept serving its previously cached `.spb` **forever**, with no
     * error and nothing in any log to notice by.
     *
     * The positive assertion now lives in the snapshot test above, alongside `backgrounds_updated`;
     * what is checked here is the property that makes it unconditional — it is sent even though this
     * server has no secondary bible configured, because the event is an invalidation signal with an
     * empty payload rather than a catalogue.
     */
    @Test
    fun `the secondary bible invalidation is resent even with none configured`() {
        val types = connectAndCollect().types()
        assertTrue(
            Constants.WS_EVENT_SECONDARY_BIBLE_UPDATED in types,
            "a reconnecting follower must be told to re-check; got $types",
        )
    }

    @Test
    fun `snapshot messages are well-formed and typed`() {
        val messages = connectAndCollect()
        assertTrue(messages.isNotEmpty(), "a connecting client must receive something")
        for (message in messages) {
            assertNotNull(
                message["type"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                "every message needs a non-blank type: $message",
            )
        }
    }

    @Test
    fun `no snapshot message carries a null commandId`() {
        // `commandId` is annotated @EncodeDefault(NEVER) precisely so ordinary broadcasts do not
        // grow a "commandId":null field — that would change the wire format for every existing
        // mobile client.
        val messages = connectAndCollect()
        assertTrue(messages.isNotEmpty())
        for (message in messages) {
            assertFalse("commandId" in message, "broadcast wire format changed: $message")
        }
    }

    @Test
    fun `a live-state change is broadcast to a connected client`() = runBlocking {
        val seen = mutableListOf<String>()
        var broadcast = false
        withTimeoutOrNull(15_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                // The first snapshot frame is the positive signal that this session is registered
                // for broadcasts — pushing the change before that could race the registration. That
                // holds because the server subscribes to its broadcast flow before writing any
                // snapshot frame; it did not always, which is what made this test drop a broadcast
                // on a loaded CI runner.
                incoming.receive()

                server.updateSchedule(
                    listOf(ScheduleItem.LabelItem(
                        id = "after-connect",
                        text = "Broadcast Me",
                        textColor = "#FFF",
                        backgroundColor = "#000",
                    )),
                )

                // Read until the broadcast for THIS change arrives, rather than reading until the
                // socket goes quiet: the item's own text tells it apart from the connect snapshot's
                // schedule frame, so the test can stop the moment it has its answer.
                while (true) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val type = runCatching {
                            (json.parseToJsonElement(text) as JsonObject)["type"]?.jsonPrimitive?.content
                        }.getOrNull()
                        seen += type ?: "<unparseable>"
                        if (type == Constants.WS_EVENT_SCHEDULE_UPDATED && "Broadcast Me" in text) {
                            broadcast = true
                            break
                        }
                    }
                }
            }
        }
        assertTrue(broadcast, "a schedule change after connect should reach the client; got $seen")
    }
}
