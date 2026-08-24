package org.churchpresenter.companionserver

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every command a connected client can send over the WebSocket, and what the desktop is told.
 *
 * The handler is three groups tried in order — selection, transport, schedule — and a command that
 * no group claims falls through to a single `unknown_command` ack. **That fall-through is the reason
 * this walks every type rather than a sample**: a command silently claimed by the wrong group, or by
 * none, looks identical from the phone (it just never happens) and the ack is the only evidence.
 *
 * Each test asserts two things: the ack the client gets back, and the event the desktop was actually
 * handed. Asserting only the ack would pass for a handler that acked and did nothing.
 *
 * Waits are on the event itself, never on a delay — [awaitEvent] subscribes before sending, so there
 * is no window in which the emission could be missed.
 */
class WsCommandTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_961))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            runCatching { server.stop() }
        }
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(io.ktor.client.engine.cio.CIO) { install(WebSockets) }
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    private fun frame(type: String, payload: String = "", commandId: String = "c1") =
        """{"type":"$type","payload":${Json.encodeToString(String.serializer(), payload)},""" +
            """"commandId":"$commandId"}"""

    /**
     * Sends [frame] and returns the acks the server wrote back, ignoring the connect snapshot.
     *
     * The snapshot is drained by reading until the stream goes quiet, then the frame is sent; the
     * quiet window only has to outlast a loopback gap, so it is short.
     */
    private fun send(frame: String, quietMs: Long = 250): List<String> = runBlocking {
        val received = mutableListOf<String>()
        withTimeoutOrNull(quietMs + 10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                while (withTimeoutOrNull(quietMs) { incoming.receive() } != null) Unit
                send(Frame.Text(frame))
                while (true) {
                    val f = withTimeoutOrNull(quietMs) { incoming.receive() } ?: break
                    if (f is Frame.Text) received.add(f.readText())
                }
            }
        }
        received
    }

    /**
     * The `ok`/`reason` of the ack carrying [commandId], or null if none came back.
     *
     * An ack is a `WebSocketMessage` of type `command_ack` whose `payload` is *itself* a JSON string
     * holding the [CommandAckPayload] — so it takes two parses, not one. Everything else the server
     * writes down the socket (state broadcasts, the connect snapshot) is skipped by the type check.
     */
    private fun ackOf(frames: List<String>, commandId: String = "c1"): Pair<Boolean, String?>? =
        frames.asSequence()
            .mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            .filter { it["type"]?.jsonPrimitive?.content == Constants.WS_EVENT_COMMAND_ACK }
            .mapNotNull { outer ->
                val payload = outer["payload"]?.jsonPrimitive?.content ?: return@mapNotNull null
                runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()
            }
            .firstOrNull { it["commandId"]?.jsonPrimitive?.content == commandId }
            ?.let {
                // contentOrNull, not content: the server encodes defaults, so an ack with no reason
                // carries an explicit `"reason":null`, and `content` would hand back the string "null".
                (it["ok"]?.jsonPrimitive?.content?.toBoolean() ?: false) to it["reason"]?.jsonPrimitive?.contentOrNull
            }

    /**
     * Subscribes to [flow] *before* [act] runs and returns what it emitted, or fails on the deadline.
     *
     * Subscribing first is the point: these are `MutableSharedFlow`s with no replay, so a collector
     * started after the send would miss the emission and the test would fail for the wrong reason.
     */
    private fun <T> awaitEvent(
        flow: MutableSharedFlow<T>,
        what: String,
        act: () -> Unit,
    ): T = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val seen = CompletableDeferred<T>()
        // Read the count BEFORE subscribing: the server keeps collectors of its own on some of these
        // flows, so "count is zero" is not the same question as "our collector attached".
        val before = flow.subscriptionCount.value
        // These flows have no replay, so sending before our collector attaches would miss the
        // emission and fail for the wrong reason. UNDISPATCHED runs the body on this thread up to
        // its first suspension, and `collect` registers the subscriber before it suspends — so the
        // subscription is live by the time `launch` returns, with nothing to wait for. This used to
        // poll the count against a two-second deadline, which depends on the IO dispatcher handing
        // out a thread in time and fails under CI load. Do not put the poll back.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { if (!seen.isCompleted) seen.complete(it) }
        }
        check(flow.subscriptionCount.value > before) { "the $what collector must attach synchronously" }
        try {
            act()
            withTimeoutOrNull(5_000) { seen.await() } ?: throw AssertionError("no $what event reached the desktop")
        } finally {
            scope.cancel()
        }
    }

    // ── Selection ───────────────────────────────────────────────────────────────

    @Test
    fun `selecting a song hands the desktop the song and acks`() {
        var acks: List<String> = emptyList()

        val song = awaitEvent(server.onSongSelected, "song-selected") {
            acks = send(frame(Constants.WS_CMD_SELECT_SONG,
                """{"id":"s1","songNumber":42,"title":"Amazing Grace","songbook":"Hymnal"}"""))
        }

        assertEquals(42, song.songNumber)
        assertEquals(true to null, ackOf(acks))
    }

    @Test
    fun `selecting a picture hands over the folder and index`() {
        var acks: List<String> = emptyList()

        val req = awaitEvent(server.onSelectPicture, "select-picture") {
            // The wire names are hyphenated (@SerialName on SelectPictureRequest), and a payload
            // that fails to decode is dropped rather than acked — which is what this caught.
            acks = send(frame(Constants.WS_CMD_SELECT_PICTURE, """{"folder-id":"f1","index":3,"file-name":"a.jpg"}"""))
        }

        assertEquals("f1", req.folderId)
        assertEquals(3, req.index)
        assertEquals("a.jpg", req.fileName)
        assertEquals(true to null, ackOf(acks))
    }

    @Test
    fun `selecting a song section hands over the number and section`() {
        val req = awaitEvent(server.onSelectSongSection, "select-song-section") {
            send(frame(Constants.WS_CMD_SELECT_SONG_SECTION, """{"number":"7","section":2,"lineIndex":1}"""))
        }

        assertEquals("7", req.number)
        assertEquals(2, req.section)
        assertEquals(1, req.lineIndex)
    }

    @Test
    fun `selecting a slide hands over the deck and index`() {
        val req = awaitEvent(server.onSelectSlide, "select-slide") {
            send(frame(Constants.WS_CMD_SELECT_SLIDE, """{"id":"deck-1","index":4}"""))
        }

        assertEquals("deck-1", req.id)
        assertEquals(4, req.index)
    }

    @Test
    fun `selecting a bible verse hands over the whole reference`() {
        val req = awaitEvent(server.onSelectBibleVerse, "select-bible-verse") {
            send(
                frame(
                    Constants.WS_CMD_SELECT_BIBLE_VERSE,
                    """{"bookName":"John","chapter":3,"verseNumber":16,"verseText":"For God so loved",""" +
                        """"verseRange":""}""",
                ),
            )
        }

        assertEquals("John", req.bookName)
        assertEquals(16, req.verseNumber)
    }

    @Test
    fun `a verse range is described by its range rather than a single verse`() {
        val req = awaitEvent(server.onSelectBibleVerse, "select-bible-verse") {
            send(
                frame(
                    Constants.WS_CMD_SELECT_BIBLE_VERSE,
                    """{"bookName":"John","chapter":3,"verseNumber":16,"verseText":"t","verseRange":"16-17"}""",
                ),
            )
        }

        assertEquals("16-17", req.verseRange)
    }

    @Test
    fun `clear takes everything off the screen`() {
        var acks: List<String> = emptyList()

        awaitEvent(server.onClear, Constants.WS_CMD_CLEAR) { acks = send(frame(Constants.WS_CMD_CLEAR)) }

        assertEquals(true to null, ackOf(acks))
    }

    @Test
    fun `bible hold carries the flag it was sent`() {
        assertTrue(awaitEvent(server.onBibleHold, "bible-hold") { send(frame(Constants.WS_CMD_BIBLE_HOLD, "true")) })
    }

    @Test
    fun `a bible hold payload that is not a boolean holds rather than dropping the command`() {
        assertTrue(
            awaitEvent(server.onBibleHold, "bible-hold") { send(frame(Constants.WS_CMD_BIBLE_HOLD, "banana")) },
            "an unreadable flag defaults to holding — safer than silently releasing what is on screen",
        )
    }

    // ── Transport ───────────────────────────────────────────────────────────────

    /** Sends [type] and asserts the matching Unit-valued event reached the desktop. */
    private fun assertSignals(flow: MutableSharedFlow<Unit>, type: String) {
        awaitEvent(flow, type) { send(frame(type)) }
    }

    @Test
    fun `the picture steppers reach the desktop`() {
        assertSignals(server.onNextPicture, Constants.WS_CMD_NEXT_PICTURE)
        assertSignals(server.onPreviousPicture, Constants.WS_CMD_PREVIOUS_PICTURE)
    }

    @Test
    fun `the slide steppers reach the desktop`() {
        assertSignals(server.onNextSlide, Constants.WS_CMD_NEXT_SLIDE)
        assertSignals(server.onPreviousSlide, Constants.WS_CMD_PREVIOUS_SLIDE)
    }

    @Test
    fun `play-pause, stop and mute reach the desktop`() {
        assertSignals(server.onMediaPlayPause, Constants.WS_CMD_MEDIA_PLAY_PAUSE)
        assertSignals(server.onMediaStop, Constants.WS_CMD_MEDIA_STOP)
        assertSignals(server.onMediaMuteToggle, Constants.WS_CMD_MEDIA_MUTE_TOGGLE)
    }

    @Test
    fun `the seek nudges reach the desktop`() {
        assertSignals(server.onMediaSeekForward, Constants.WS_CMD_MEDIA_SEEK_FORWARD)
        assertSignals(server.onMediaSeekBackward, Constants.WS_CMD_MEDIA_SEEK_BACKWARD)
    }

    @Test
    fun `seeking to a position carries the millisecond value`() {
        assertEquals(
            12_500L,
            awaitEvent(server.onMediaSeekTo, "media-seek-to") {
                send(frame(Constants.WS_CMD_MEDIA_SEEK_TO, " 12500 "))
            },
            "the payload is trimmed before it is read",
        )
    }

    @Test
    fun `a seek to something that is not a number is refused rather than seeking to zero`() {
        val acks = send(frame(Constants.WS_CMD_MEDIA_SEEK_TO, "soon"))

        assertEquals(false to "invalid_payload", ackOf(acks))
    }

    @Test
    fun `setting the volume carries the level`() {
        assertEquals(
            0.5f,
            awaitEvent(server.onMediaSetVolume, "media-set-volume") {
                send(frame(Constants.WS_CMD_MEDIA_SET_VOLUME, "0.5"))
            },
        )
    }

    @Test
    fun `a volume that is not a number is refused`() {
        assertEquals(false to "invalid_payload", ackOf(send(frame(Constants.WS_CMD_MEDIA_SET_VOLUME, "loud"))))
    }

    // ── The fall-through ────────────────────────────────────────────────────────

    @Test
    fun `a command no group claims is acked as unknown rather than dropped`() {
        assertEquals(
            false to "unknown_command", ackOf(send(frame("not.a.real.command"))),
            "silence would be indistinguishable from a handler that ran and did nothing",
        )
    }

    @Test
    fun `an empty command type is unknown too`() {
        assertEquals(false to "unknown_command", ackOf(send(frame(""))))
    }
}
