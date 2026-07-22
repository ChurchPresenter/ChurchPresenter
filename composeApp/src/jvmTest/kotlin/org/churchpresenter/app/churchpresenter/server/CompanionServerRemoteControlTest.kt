package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.file.Files
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The remote-control half of the companion API: the song catalogue, the schedule, and the two
 * gates in front of them.
 *
 * Everything here is reachable from any phone on the church wifi, so the two gates are the point.
 * The API key decides who may ask at all; the approval flow decides whether asking does anything —
 * `POST /api/schedule/add` does not add anything, it suspends until the operator presses Allow on
 * the desktop, and only then answers the phone. A regression that skips either gate is invisible
 * from the phone (its request works, which is what it wanted) and shows up as items appearing in
 * a service nobody agreed to.
 *
 * The instant actions are the deliberate exception: navigating within an already-live song, and
 * clearing the screen, fire immediately. Those are the operator's own remote in their own hand,
 * and an approval prompt per slide would make it useless.
 *
 * As in [CompanionServerQaTest] the server is started once for the class and its state reset per
 * test; `user.home` is left alone for the reason documented there.
 */
class CompanionServerRemoteControlTest {

    private lateinit var client: HttpClient
    private var operatorScope: CoroutineScope? = null

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = 39_640)
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
    fun resetState() {
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateApiKey(enabled = false, key = "")
        server.presentationRemoteEnabled = false
        server.presentationRemotePassword = ""
        server.clearPresentationState()
        server.updateSongs(emptyList())
        server.updateSchedule(emptyList())
    }

    @AfterTest
    fun closeClient() {
        runCatching { operatorScope?.cancel() }
        operatorScope = null
        runCatching { client.close() }
    }

    // ── Driving the server ──────────────────────────────────────────────────────

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun get(path: String, apiKey: String? = null): HttpResponse = runBlocking {
        client.get(url(path)) { apiKey?.let { header(Constants.HEADER_API_KEY, it) } }
    }

    private fun post(path: String, body: String = "", apiKey: String? = null): HttpResponse = runBlocking {
        client.post(url(path)) {
            apiKey?.let { header(Constants.HEADER_API_KEY, it) }
            setBody(body)
        }
    }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.obj(): JsonObject = json.parseToJsonElement(text()).jsonObject
    private fun HttpResponse.array(): JsonArray = json.parseToJsonElement(text()).jsonArray
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    /**
     * Starts collecting [flow] on a scope cancelled after the test, and does not return until the
     * collector is actually subscribed.
     *
     * These are all `MutableSharedFlow`s with no replay: an event emitted before the collector is
     * attached is dropped for good. Launching a collector and immediately driving the server is
     * therefore a race — it passes on a quiet machine and loses the event on a loaded one. Waiting
     * on `subscriptionCount` is the positive signal that the collector is listening; nothing here
     * waits on a duration.
     */
    private fun <T> collecting(flow: MutableSharedFlow<T>, onEach: (T) -> Unit) {
        val scope = operatorScope ?: CoroutineScope(Dispatchers.IO).also { operatorScope = it }
        scope.launch { flow.collect { onEach(it) } }
        runBlocking {
            withTimeoutOrNull(5_000) { flow.subscriptionCount.first { it > 0 } }
                ?: error("collector never subscribed")
        }
    }

    /**
     * Answers every add/project approval prompt with [allow], and records the items asked about.
     *
     * Without something playing the operator these endpoints never respond — which is exactly what
     * they are supposed to do, and is asserted on its own below.
     */
    private fun playOperator(allow: Boolean = true): MutableList<ScheduleItem> {
        val asked = mutableListOf<ScheduleItem>()
        collecting(server.onAddToSchedule) { asked.add(it.item); it.decision.complete(allow) }
        collecting(server.onProject) { asked.add(it.item); it.decision.complete(allow) }
        collecting(server.onAddBatchToSchedule) { asked.addAll(it.items); it.decision.complete(allow) }
        return asked
    }

    private fun song(number: String, title: String, songbook: String = "Hymnal") = SongItem(
        number = number,
        title = title,
        songbook = songbook,
        author = "Newton",
        lyrics = listOf("[Verse 1]", "Amazing grace", "", "{Chorus}", "how sweet the sound"),
    )

    // ── The API key gate ────────────────────────────────────────────────────────

    @Test
    fun `with no key configured the api is open to the local network`() {
        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `with a key configured a request without one is refused`() {
        server.updateApiKey(enabled = true, key = "s3cret")

        assertEquals(
            HttpStatusCode.Unauthorized,
            get(Constants.ENDPOINT_SONGS).status,
            "the key is the only thing between the schedule and everyone else on the church wifi",
        )
    }

    @Test
    fun `the key is accepted in a header or a query parameter`() {
        server.updateApiKey(enabled = true, key = "s3cret")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_SONGS, apiKey = "s3cret").status)
        assertEquals(
            HttpStatusCode.OK,
            get("${Constants.ENDPOINT_SONGS}?${Constants.QUERY_PARAM_API_KEY}=s3cret").status,
            "the query form is what a browser-source URL and a QR code can carry",
        )
    }

    @Test
    fun `a wrong key is refused`() {
        server.updateApiKey(enabled = true, key = "s3cret")

        assertEquals(HttpStatusCode.Unauthorized, get(Constants.ENDPOINT_SONGS, apiKey = "s3cre").status)
        assertEquals(HttpStatusCode.Unauthorized, get(Constants.ENDPOINT_SONGS, apiKey = "S3CRET").status)
        assertEquals(HttpStatusCode.Unauthorized, get(Constants.ENDPOINT_SONGS, apiKey = "").status)
    }

    @Test
    fun `a key that has been switched off stops being required`() {
        server.updateApiKey(enabled = true, key = "s3cret")
        assertEquals(HttpStatusCode.Unauthorized, get(Constants.ENDPOINT_SONGS).status)

        server.updateApiKey(enabled = false, key = "s3cret")

        assertEquals(
            HttpStatusCode.OK,
            get(Constants.ENDPOINT_SONGS).status,
            "the setting has to take effect without restarting the server mid-service",
        )
    }

    @Test
    fun `protection asked for without a key set does not lock everyone out`() {
        // The settings tab can be left with the switch on and the field empty.
        server.updateApiKey(enabled = true, key = "")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `the gate is on the schedule and the projector too, not only the catalogue`() {
        server.updateApiKey(enabled = true, key = "s3cret")

        assertEquals(HttpStatusCode.Unauthorized, get(Constants.ENDPOINT_SCHEDULE).status)
        assertEquals(HttpStatusCode.Unauthorized, post(Constants.ENDPOINT_SCHEDULE_ADD, """{"item":{"songNumber":1}}""").status)
        assertEquals(HttpStatusCode.Unauthorized, post(Constants.ENDPOINT_PROJECT, """{"item":{"songNumber":1}}""").status)
        assertEquals(HttpStatusCode.Unauthorized, post(Constants.ENDPOINT_CLEAR).status)
    }

    // ── The song catalogue ──────────────────────────────────────────────────────

    @Test
    fun `the catalogue groups songs by songbook and counts them`() {
        server.updateSongs(
            listOf(
                song("1", "Amazing Grace", "Hymnal"),
                song("2", "How Great Thou Art", "Hymnal"),
                song("1", "Shout to the Lord", "Songs of Praise"),
            ),
        )

        val catalog = get(Constants.ENDPOINT_SONGS).obj()

        assertEquals("3", catalog.str("total"), "the phone shows this before it downloads anything")
        assertEquals("2", catalog.str("songBooks"))
        assertEquals(
            listOf("Hymnal", "Songs of Praise"),
            catalog.getValue("song-book").jsonArray.map { it.jsonObject.str("book-name") },
        )
    }

    @Test
    fun `the catalogue can be narrowed to one songbook`() {
        server.updateSongs(listOf(song("1", "Amazing Grace", "Hymnal"), song("1", "Shout", "Songs of Praise")))

        val filtered = get("${Constants.ENDPOINT_SONGS}?${Constants.QUERY_PARAM_SONGBOOK}=Hymnal").obj()

        assertEquals("1", filtered.str("songBooks"))
        assertEquals("1", filtered.str("total"))
    }

    @Test
    fun `a songbook nobody has returns an empty catalogue rather than everything`() {
        server.updateSongs(listOf(song("1", "Amazing Grace", "Hymnal")))

        val filtered = get("${Constants.ENDPOINT_SONGS}?${Constants.QUERY_PARAM_SONGBOOK}=Not+A+Book").obj()

        assertEquals("0", filtered.str("total"), "silently ignoring the filter would look like the wrong songbook")
    }

    @Test
    fun `an empty library is a valid answer`() {
        val catalog = get(Constants.ENDPOINT_SONGS).obj()

        assertEquals("0", catalog.str("total"))
        assertTrue(catalog.getValue("song-book").jsonArray.isEmpty())
    }

    // ── One song ────────────────────────────────────────────────────────────────

    @Test
    fun `a song is fetched by its number, with its lyrics`() {
        server.updateSongs(listOf(song("42", "Amazing Grace")))

        val detail = get("${Constants.ENDPOINT_SONGS}/42").obj()

        assertEquals("Amazing Grace", detail.str("title"))
        assertEquals("Newton", detail.str("author"))
        assertTrue(detail.getValue("sections").jsonArray.isNotEmpty(), "a song with no lyrics cannot be sung from")
    }

    @Test
    fun `a number shared by two songbooks is disambiguated by name`() {
        server.updateSongs(
            listOf(song("1", "Amazing Grace", "Hymnal"), song("1", "Shout to the Lord", "Songs of Praise")),
        )

        assertEquals(
            "Shout to the Lord",
            get("${Constants.ENDPOINT_SONGS}/1?${Constants.QUERY_PARAM_SONGBOOK}=Songs+of+Praise").obj().str("title"),
            "without this the phone can only ever reach whichever songbook loaded first",
        )
    }

    @Test
    fun `a song can be fetched by title when it has no number`() {
        server.updateSongs(listOf(song("", "Untitled Worship Song")))

        assertEquals(
            "Untitled Worship Song",
            get("${Constants.ENDPOINT_SONGS}/_?title=Untitled+Worship+Song").obj().str("title"),
            "'_' stands in for the empty number so the path still has a segment",
        )
    }

    @Test
    fun `a song can be fetched by its position in the library`() {
        server.updateSongs(listOf(song("1", "First"), song("2", "Second")))

        assertEquals("Second", get("${Constants.ENDPOINT_SONGS}/anything?id=1").obj().str("title"))
    }

    @Test
    fun `a song that is not in the library is a not-found`() {
        server.updateSongs(listOf(song("1", "Amazing Grace")))

        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_SONGS}/999").status)
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_SONGS}/1?songbook=Wrong+Book").status)
    }

    // ── Navigating a live song ──────────────────────────────────────────────────

    @Test
    fun `selecting a section of the live song fires without asking`() {
        // This is the operator's own remote: a prompt per slide would make it unusable.
        val selected = CompletableDeferred<SelectSongSectionRequest>()
        collecting(server.onSelectSongSection) { selected.complete(it) }

        val response = post("${Constants.ENDPOINT_SONGS}/42/select?section=2")

        assertEquals(HttpStatusCode.OK, response.status)
        val request = assertNotNull(runBlocking { withTimeoutOrNull(2_000) { selected.await() } })
        assertEquals(2, request.section)
    }

    @Test
    fun `the section can be sent in the body instead of the query`() {
        val selected = CompletableDeferred<SelectSongSectionRequest>()
        collecting(server.onSelectSongSection) { selected.complete(it) }

        post("${Constants.ENDPOINT_SONGS}/42/select", """{"number":"42","section":3}""")

        assertEquals(3, assertNotNull(runBlocking { withTimeoutOrNull(2_000) { selected.await() } }).section)
    }

    /**
     * Documents a KNOWN GAP: the endpoint's own doc comment offers `{"section": 2}` as the body
     * form, but that body cannot be decoded — `SelectSongSectionRequest.number` has no default, so
     * the parse fails, the section reads as absent, and the request is rejected as a bad one.
     *
     * A client following the documentation gets a 400 with no hint that the missing field is one
     * the URL already carries. The fix is a default on `number` (it is overwritten from the path
     * segment anyway); this expectation then becomes an OK.
     */
    @Test
    fun `the documented body form without a number is rejected -- known gap`() {
        assertEquals(
            HttpStatusCode.BadRequest,
            post("${Constants.ENDPOINT_SONGS}/42/select", """{"section":2}""").status,
            "current behaviour: the documented body is refused unless it repeats the song number",
        )
    }

    @Test
    fun `a selection with no section is refused`() {
        assertEquals(HttpStatusCode.BadRequest, post("${Constants.ENDPOINT_SONGS}/42/select").status)
        assertEquals(HttpStatusCode.BadRequest, post("${Constants.ENDPOINT_SONGS}/42/select?section=-1").status)
        assertEquals(HttpStatusCode.BadRequest, post("${Constants.ENDPOINT_SONGS}/42/select?section=abc").status)
    }

    @Test
    fun `clearing the screen fires without asking`() {
        val cleared = CompletableDeferred<Unit>()
        collecting(server.onClear) { cleared.complete(Unit) }

        val response = post(Constants.ENDPOINT_CLEAR)

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(
            runBlocking { withTimeoutOrNull(2_000) { cleared.await() } },
            "clearing is the panic button — it cannot wait behind a dialog",
        )
    }

    // ── The schedule ────────────────────────────────────────────────────────────

    @Test
    fun `the schedule endpoint shows what the desktop pushed`() {
        server.updateSchedule(
            listOf(
                ScheduleItem.SongItem(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal"),
                ScheduleItem.BibleVerseItem(id = "2", bookName = "John", chapter = 3, verseNumber = 16, verseText = "…"),
            ),
        )

        val response = get(Constants.ENDPOINT_SCHEDULE).obj()
        val schedule = response.getValue("items").jsonArray

        assertEquals("2", response.str("total"))
        assertEquals(2, schedule.size)
        assertEquals(listOf("song", "bible"), schedule.map { it.jsonObject.str("type") })
        assertEquals(
            listOf("42 - Amazing Grace", "John 3:16"),
            schedule.map { it.jsonObject.str("displayText") },
            "the phone shows the same row labels the operator sees",
        )
    }

    @Test
    fun `an item added from a phone waits for the operator`() {
        val asked = playOperator(allow = true)

        val response = post(Constants.ENDPOINT_SCHEDULE_ADD, """{"item":{"songNumber":42,"title":"Amazing Grace"}}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("true", response.obj().str("ok"))
        val item = assertIs<ScheduleItem.SongItem>(asked.single())
        assertEquals(42, item.songNumber, "the desktop prompt has to name what is being added")
    }

    @Test
    fun `an operator who refuses sends the phone a refusal`() {
        playOperator(allow = false)

        val response = post(Constants.ENDPOINT_SCHEDULE_ADD, """{"item":{"songNumber":42}}""")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("denied", response.obj().str("reason"), "the phone has to say why, not just fail")
    }

    @Test
    fun `nothing recognisable in the body is refused before anyone is asked`() {
        val asked = playOperator(allow = true)

        assertEquals(HttpStatusCode.BadRequest, post(Constants.ENDPOINT_SCHEDULE_ADD, """{"item":{"displayText":"nothing"}}""").status)
        assertEquals(HttpStatusCode.BadRequest, post(Constants.ENDPOINT_SCHEDULE_ADD, "not json").status)
        assertTrue(asked.isEmpty(), "a junk request must not raise a dialog in front of the operator mid-service")
    }

    @Test
    fun `a passage added from a phone is approved as one batch`() {
        val asked = playOperator(allow = true)

        val response = post(
            Constants.ENDPOINT_SCHEDULE_ADD_BATCH,
            """{"items":[
                {"bookName":"John","chapter":3,"verseNumber":16},
                {"bookName":"John","chapter":3,"verseNumber":17},
                {"bookName":"John","chapter":3,"verseNumber":18}
            ]}""",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("3", response.obj().str("added"))
        assertEquals(
            listOf("John 3:16", "John 3:17", "John 3:18"),
            asked.map { it.displayText },
            "three prompts for one passage would be unusable; one prompt covers the group",
        )
    }

    @Test
    fun `a refused batch adds nothing at all`() {
        playOperator(allow = false)

        val response = post(
            Constants.ENDPOINT_SCHEDULE_ADD_BATCH,
            """{"items":[{"bookName":"John","chapter":3,"verseNumber":16}]}""",
        )

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `a batch with nothing recognisable in it is refused`() {
        val asked = playOperator(allow = true)

        assertEquals(HttpStatusCode.BadRequest, post(Constants.ENDPOINT_SCHEDULE_ADD_BATCH, """{"items":[]}""").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            post(Constants.ENDPOINT_SCHEDULE_ADD_BATCH, """{"items":[{"displayText":"nothing"}]}""").status,
        )
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `going live from a phone waits for the operator too`() {
        val asked = playOperator(allow = true)

        val response = post(Constants.ENDPOINT_PROJECT, """{"item":{"bookName":"John","chapter":3,"verseNumber":16}}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertIs<ScheduleItem.BibleVerseItem>(
            asked.single(),
            "projecting puts it straight on the wall — this is the last request that should skip the prompt",
        )
    }

    @Test
    fun `a refused projection leaves the screen alone`() {
        playOperator(allow = false)

        assertEquals(
            HttpStatusCode.Forbidden,
            post(Constants.ENDPOINT_PROJECT, """{"item":{"bookName":"John","chapter":3,"verseNumber":16}}""").status,
        )
    }

    @Test
    fun `an unanswered request is left waiting rather than allowed`() {
        // Nothing plays the operator here: the desktop prompt is still on screen, unanswered.
        val response = runBlocking {
            withTimeoutOrNull(1_500) { client.post(url(Constants.ENDPOINT_SCHEDULE_ADD)) { setBody("""{"item":{"songNumber":42}}""") } }
        }

        assertEquals(
            null,
            response,
            "timing out is correct; answering OK while the dialog is still open would add it behind the operator's back",
        )
    }

    // ── The WebSocket command channel ───────────────────────────────────────────

    /**
     * Opens a session, sends [frames], and collects everything that comes back until [quietMs]
     * passes with nothing new.
     *
     * The channel has no per-command terminator to wait on — a command may answer with an ack, a
     * legacy `{"ok":…}` reply, a broadcast, or nothing at all — so an idle window is the only way
     * to know the reply is complete. It is short on purpose: this is loopback, and it only has to
     * outlast the gap between two frames the server writes back to back.
     */
    private fun sendOverWebSocket(vararg frames: String, quietMs: Long = 250): List<String> = runBlocking {
        val received = mutableListOf<String>()
        withTimeoutOrNull(quietMs + 10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                // Drain the connect snapshot first so it is not mistaken for a reply.
                while (withTimeoutOrNull(quietMs) { incoming.receive() } != null) Unit
                frames.forEach { send(Frame.Text(it)) }
                while (true) {
                    val frame = withTimeoutOrNull(quietMs) { incoming.receive() } ?: break
                    if (frame is Frame.Text) received.add(frame.readText())
                }
            }
        }
        received
    }

    private fun command(type: String, payload: String = "", commandId: String? = null): String =
        json.encodeToString(
            WebSocketMessage.serializer(),
            WebSocketMessage(type = type, payload = payload, commandId = commandId),
        )

    /** The acknowledgement carried back for [commandId], if one was sent. */
    private fun List<String>.ackFor(commandId: String): CommandAckPayload? =
        mapNotNull { runCatching { json.decodeFromString(WebSocketMessage.serializer(), it) }.getOrNull() }
            .filter { it.type == Constants.WS_EVENT_COMMAND_ACK }
            .mapNotNull { runCatching { json.decodeFromString(CommandAckPayload.serializer(), it.payload) }.getOrNull() }
            .firstOrNull { it.commandId == commandId }

    @Test
    fun `a command sent over the socket reaches the app`() {
        val selected = CompletableDeferred<SelectBibleVerseRequest>()
        collecting(server.onSelectBibleVerse) { selected.complete(it) }

        sendOverWebSocket(
            command(
                Constants.WS_CMD_SELECT_BIBLE_VERSE,
                """{"bookName":"John","chapter":3,"verseNumber":16,"verseText":"For God so loved…"}""",
            ),
        )

        val request = assertNotNull(runBlocking { withTimeoutOrNull(2_000) { selected.await() } })
        assertEquals("John", request.bookName)
        assertEquals(16, request.verseNumber)
    }

    @Test
    fun `a command carrying an id is acknowledged`() {
        // A linked instance drives the primary over this channel and waits on the ack; without one
        // the follower reports the command as failed and retries it.
        collecting(server.onClear) { }

        val ack = sendOverWebSocket(command(Constants.WS_CMD_CLEAR, commandId = "cmd-1")).ackFor("cmd-1")

        assertEquals(true, assertNotNull(ack).ok)
    }

    @Test
    fun `a command sent without an id is answered with silence rather than an ack`() {
        // The mobile app has never sent one; adding an unsolicited ack frame would change the wire
        // format underneath it.
        collecting(server.onClear) { }

        val replies = sendOverWebSocket(command(Constants.WS_CMD_CLEAR))

        assertTrue(
            replies.none { Constants.WS_EVENT_COMMAND_ACK in it },
            "an ack nobody asked for would reach every existing phone: $replies",
        )
    }

    @Test
    fun `a command this build does not know is refused by name`() {
        val ack = sendOverWebSocket(command("teleport_the_lectern", commandId = "cmd-2")).ackFor("cmd-2")

        assertEquals(false, assertNotNull(ack).ok)
        assertEquals(
            "unknown_command",
            ack.reason,
            "a follower on a newer build has to be able to tell an unsupported command from a failed one",
        )
    }

    @Test
    fun `an approval-bound command is acknowledged as pending, not as done`() {
        // The operator may take minutes. The ack says "queued"; the outcome follows separately.
        collecting(server.onAddToSchedule) { it.decision.complete(true) }

        val ack = sendOverWebSocket(
            command(Constants.WS_CMD_ADD_TO_SCHEDULE, """{"item":{"songNumber":42}}""", commandId = "cmd-3"),
        ).ackFor("cmd-3")

        assertEquals(true, assertNotNull(ack).ok)
        assertEquals("pending_approval", ack.reason, "reporting it as applied would let a follower drift")
    }

    @Test
    fun `an approved command answers the phone as well`() {
        val asked = mutableListOf<ScheduleItem>()
        collecting(server.onAddToSchedule) { asked.add(it.item); it.decision.complete(true) }

        val replies = sendOverWebSocket(
            command(Constants.WS_CMD_ADD_TO_SCHEDULE, """{"item":{"songNumber":42,"title":"Amazing Grace"}}"""),
        )

        assertTrue(replies.any { """"ok":true""" in it }, "the mobile app watches for this reply: $replies")
        assertIs<ScheduleItem.SongItem>(asked.single())
    }

    @Test
    fun `a refused command tells the phone it was refused`() {
        collecting(server.onAddToSchedule) { it.decision.complete(false) }

        val replies = sendOverWebSocket(command(Constants.WS_CMD_ADD_TO_SCHEDULE, """{"item":{"songNumber":42}}"""))

        assertTrue(replies.any { """"reason":"denied"""" in it }, "$replies")
    }

    @Test
    fun `a batch with nothing recognisable in it is refused rather than queued`() {
        val ack = sendOverWebSocket(
            command(Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE, """{"items":[{"displayText":"nothing"}]}""", commandId = "cmd-4"),
        ).ackFor("cmd-4")

        assertEquals(false, assertNotNull(ack).ok)
        assertEquals("invalid_payload", ack.reason, "queueing an empty batch would raise a dialog over nothing")
    }

    @Test
    fun `removing a schedule item asks the operator, naming the row`() {
        server.updateSchedule(
            listOf(ScheduleItem.SongItem(id = "row-1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")),
        )
        val asked = CompletableDeferred<PendingRemoveRequest>()
        collecting(server.onRemoveFromSchedule) { asked.complete(it); it.decision.complete(true) }

        sendOverWebSocket(command(Constants.WS_CMD_REMOVE_FROM_SCHEDULE, """{"id":"row-1"}"""))

        val request = assertNotNull(runBlocking { withTimeoutOrNull(2_000) { asked.await() } })
        assertEquals("row-1", request.id)
        assertEquals(
            "42 - Amazing Grace",
            request.label,
            "the prompt has to say which row is about to go, not just its id",
        )
    }

    @Test
    fun `removing a row that is no longer there still names something`() {
        val asked = CompletableDeferred<PendingRemoveRequest>()
        collecting(server.onRemoveFromSchedule) { asked.complete(it); it.decision.complete(false) }

        sendOverWebSocket(command(Constants.WS_CMD_REMOVE_FROM_SCHEDULE, """{"id":"already-gone"}"""))

        assertEquals(
            "already-gone",
            assertNotNull(runBlocking { withTimeoutOrNull(2_000) { asked.await() } }).label,
            "a blank prompt is worse than an ugly one",
        )
    }

    @Test
    fun `the instant navigation commands fire without approval`() {
        val fired = mutableListOf<String>()
        collecting(server.onNextSlide) { fired.add("next_slide") }
        collecting(server.onPreviousSlide) { fired.add("previous_slide") }
        collecting(server.onNextPicture) { fired.add("next_picture") }
        collecting(server.onPreviousPicture) { fired.add("previous_picture") }

        sendOverWebSocket(
            command(Constants.WS_CMD_NEXT_SLIDE),
            command(Constants.WS_CMD_PREVIOUS_SLIDE),
            command(Constants.WS_CMD_NEXT_PICTURE),
            command(Constants.WS_CMD_PREVIOUS_PICTURE),
        )

        assertEquals(
            setOf("next_slide", "previous_slide", "next_picture", "previous_picture"),
            fired.toSet(),
            "these are the buttons under the operator's thumb during a service",
        )
    }

    @Test
    fun `the media transport commands carry their values`() {
        val seekTo = CompletableDeferred<Long>()
        val volume = CompletableDeferred<Float>()
        collecting(server.onMediaSeekTo) { seekTo.complete(it) }
        collecting(server.onMediaSetVolume) { volume.complete(it) }

        sendOverWebSocket(
            command(Constants.WS_CMD_MEDIA_SEEK_TO, "125000"),
            command(Constants.WS_CMD_MEDIA_SET_VOLUME, "0.35"),
        )

        assertEquals(125_000L, runBlocking { withTimeoutOrNull(2_000) { seekTo.await() } })
        assertEquals(0.35f, runBlocking { withTimeoutOrNull(2_000) { volume.await() } })
    }

    @Test
    fun `a malformed frame does not take the session down`() {
        val cleared = CompletableDeferred<Unit>()
        collecting(server.onClear) { cleared.complete(Unit) }

        val replies = sendOverWebSocket(
            "this is not a message at all",
            command(Constants.WS_CMD_SELECT_BIBLE_VERSE, "{ not json"),
            command(Constants.WS_CMD_CLEAR, commandId = "cmd-5"),
        )

        assertNotNull(
            runBlocking { withTimeoutOrNull(2_000) { cleared.await() } },
            "one bad frame from one phone must not cost the operator the whole channel",
        )
        assertEquals(true, assertNotNull(replies.ackFor("cmd-5")).ok)
    }

    // ── What a phone can read: scripture ────────────────────────────────────────

    /** Loads the three-book fixture module into the server as the primary Bible. */
    private fun loadBible(translation: String = "KJV"): File {
        val dir = Files.createTempDirectory("cp-server-bible").toFile()
        server.updateBible(SpbFixture.loadedBible(dir), translation)
        return dir
    }

    @Test
    fun `scripture is unavailable rather than empty when no bible is loaded`() {
        // A phone must be able to tell "not set up yet" from "this translation has no books".
        // A loaded Bible cannot be taken back out of a running server, so this one case gets its
        // own instance instead of depending on which test in the class ran first.
        val fresh = CompanionServer()
        try {
            fresh.start(port = 39_660)
            val freshPort = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!fresh.isRunning.value || fresh.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    fresh.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")

            val response = runBlocking {
                client.get("http://127.0.0.1:$freshPort${Constants.ENDPOINT_BIBLE}")
            }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        } finally {
            runCatching { fresh.stop() }
        }
    }

    @Test
    fun `the bible catalogue lists the books of the loaded translation`() {
        val dir = loadBible("KJV")
        try {
            val catalog = get(Constants.ENDPOINT_BIBLE).obj()

            assertEquals("KJV", catalog.str("translation"), "the phone labels every verse with this")
            assertEquals(
                listOf("Genesis", "Psalms", "John"),
                catalog.getValue("books").jsonArray.map { it.jsonObject.str("book-name") },
                "in the module's own order, which is not alphabetical and not by id",
            )
            assertEquals("3", catalog.str("book-total"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the catalogue can be narrowed to one book`() {
        val dir = loadBible()
        try {
            val filtered = get("${Constants.ENDPOINT_BIBLE}?${Constants.QUERY_PARAM_BOOK}=John").obj()

            assertEquals("1", filtered.str("book-total"))
            assertEquals(
                "John",
                filtered.getValue("books").jsonArray.single().jsonObject.str("book-name"),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a book name is matched however it is capitalised`() {
        val dir = loadBible()
        try {
            // The phone sends back whatever the user typed into its search box.
            assertEquals("1", get("${Constants.ENDPOINT_BIBLE}?${Constants.QUERY_PARAM_BOOK}=john").obj().str("book-total"))
            assertEquals("1", get("${Constants.ENDPOINT_BIBLE}?${Constants.QUERY_PARAM_BOOK}=JOHN").obj().str("book-total"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a book that is not in this translation comes back empty`() {
        val dir = loadBible()
        try {
            val filtered = get("${Constants.ENDPOINT_BIBLE}?${Constants.QUERY_PARAM_BOOK}=Habakkuk").obj()

            assertEquals(
                "0",
                filtered.str("book-total"),
                "falling back to the whole bible would look like the wrong book was opened",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a chapter is fetched with its verse text`() {
        val dir = loadBible()
        try {
            val chapter = get(
                "${Constants.ENDPOINT_BIBLE}?${Constants.QUERY_PARAM_BOOK}=43&${Constants.QUERY_PARAM_CHAPTER}=3",
            ).obj()

            assertEquals("John", chapter.str("book-name"), "asked for by id, answered with the name to show")
            assertEquals("3", chapter.str("chapter"))
            assertEquals("2", chapter.str("verse-total"))
            assertEquals(
                listOf("For God so loved the world.", "For God sent not his Son to condemn the world."),
                chapter.getValue("verses").jsonArray.map { it.jsonObject.str("text") },
                "the text is what goes on the wall — a catalogue without it is only an index",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `verses come back numbered as the translation numbers them`() {
        val dir = loadBible()
        try {
            val chapter = get(
                "${Constants.ENDPOINT_BIBLE}?${Constants.QUERY_PARAM_BOOK}=43&${Constants.QUERY_PARAM_CHAPTER}=3",
            ).obj()

            assertEquals(
                listOf("16", "17"),
                chapter.getValue("verses").jsonArray.map { it.jsonObject.str("verse") },
                "John 3 starts at 16 in the fixture — renumbering from 1 would misquote every reference",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a chapter that is not in the book is a not-found`() {
        val dir = loadBible()
        try {
            assertEquals(
                HttpStatusCode.NotFound,
                get("${Constants.ENDPOINT_BIBLE}?${Constants.QUERY_PARAM_BOOK}=43&${Constants.QUERY_PARAM_CHAPTER}=99").status,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── What a phone can read: pictures ─────────────────────────────────────────

    /** Registers a folder of real image files with the server, as the pictures tab does. */
    private fun loadPictures(vararg names: String): File {
        val dir = Files.createTempDirectory("cp-server-pictures").toFile()
        val files = names.map { name ->
            File(dir, name).also { file ->
                // A real, decodable 1×1 image — the endpoint serves the bytes as they are.
                javax.imageio.ImageIO.write(
                    java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB),
                    file.extension.ifEmpty { "jpg" },
                    file,
                )
            }
        }
        server.updatePictures("folder-1", "Baptism", dir.absolutePath, files)
        return dir
    }

    @Test
    fun `a picture folder lists its images with somewhere to fetch each one`() {
        val dir = loadPictures("first.jpg", "second.jpg", "third.png")
        try {
            val folder = get("${Constants.ENDPOINT_PICTURES}/folder-1").obj()

            assertEquals("Baptism", folder.str("folder-name"))
            assertEquals("3", folder.str("image-total"))
            val images = folder.getValue("images").jsonArray
            assertEquals(listOf("first.jpg", "second.jpg", "third.png"), images.map { it.jsonObject.str("file-name") })
            assertEquals(
                "${Constants.ENDPOINT_PICTURES}/folder-1/images/0",
                images.first().jsonObject.str("thumbnail-url"),
                "the phone follows this url as-is; building it itself would duplicate the scheme",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an image is served as an image`() {
        val dir = loadPictures("first.jpg", "second.png")
        try {
            val jpeg = get("${Constants.ENDPOINT_PICTURES}/folder-1/images/0")
            val png = get("${Constants.ENDPOINT_PICTURES}/folder-1/images/1")

            assertEquals(HttpStatusCode.OK, jpeg.status)
            assertEquals("image/jpeg", jpeg.headers[io.ktor.http.HttpHeaders.ContentType]?.substringBefore(';'))
            assertEquals(
                "image/png",
                png.headers[io.ktor.http.HttpHeaders.ContentType]?.substringBefore(';'),
                "a png served as a jpeg fails to decode on some phones rather than falling back",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a folder nobody registered is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_PICTURES}/no-such-folder").status)
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_PICTURES}/no-such-folder/images/0").status)
    }

    @Test
    fun `an image index past the end of the folder is a not-found`() {
        val dir = loadPictures("only.jpg")
        try {
            assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_PICTURES}/folder-1/images/1").status)
            assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_PICTURES}/folder-1/images/-1").status)
            assertEquals(HttpStatusCode.BadRequest, get("${Constants.ENDPOINT_PICTURES}/folder-1/images/abc").status)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an image deleted from disk since the folder was registered is a not-found`() {
        // The folder is scanned once; a file can be moved or renamed between then and the request.
        val dir = loadPictures("first.jpg")
        try {
            File(dir, "first.jpg").delete()

            assertEquals(
                HttpStatusCode.NotFound,
                get("${Constants.ENDPOINT_PICTURES}/folder-1/images/0").status,
                "the phone shows a broken tile; a 500 would look like the server had fallen over",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── What a phone can read: the server itself ────────────────────────────────

    @Test
    fun `the info endpoint names the port the phone reached it on`() {
        val info = get(Constants.ENDPOINT_INFO).obj()

        assertEquals(port.toString(), info.str("port"), "a phone that found the app by discovery confirms it here")
    }

    @Test
    fun `the status endpoint describes what this instance is holding`() {
        val dir = loadBible("KJV")
        try {
            server.updateSongs(listOf(song("1", "Amazing Grace", "Hymnal"), song("1", "Shout", "Songs of Praise")))

            val status = get(Constants.ENDPOINT_STATUS)
            val body = status.obj()

            assertEquals(listOf("KJV"), body.getValue("bibles").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(
                listOf("Hymnal", "Songs of Praise"),
                body.getValue("songbooks").jsonArray.map { it.jsonPrimitive.content },
                "the phone offers these as filters before it downloads any songs",
            )
            assertTrue(
                status.headers[Constants.HEADER_SERVER_VERSION]?.isNotBlank() == true,
                "the version header is how a companion decides which commands this build understands",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── The presentation remote ─────────────────────────────────────────────────

    /**
     * The presentation remote is a web page a speaker opens on their own phone to advance their own
     * slides. It is deliberately NOT behind the API key — the speaker is not a companion device and
     * has no key — so it has a gate of its own: a switch the operator turns on, an optional
     * password, and a one-time approval prompt when a device first authenticates. Every one of
     * those is the only thing standing between a visitor on the church wifi and the sermon slides.
     */
    private fun remotePost(path: String, password: String? = null): HttpResponse = runBlocking {
        client.post(url("/api/presentation-remote/$path")) {
            password?.let { header(Constants.HEADER_PRESENTATION_PASSWORD, it) }
        }
    }

    @Test
    fun `the remote is refused entirely while it is switched off`() {
        server.presentationRemoteEnabled = false

        listOf("next", "previous", "goto/3", "freeze", "play-pause", "loop", "go-live").forEach { action ->
            assertEquals(
                HttpStatusCode.Forbidden,
                remotePost(action).status,
                "'$action' worked with the remote switched off",
            )
        }
    }

    @Test
    fun `the remote needs the password once it has one`() {
        server.presentationRemoteEnabled = true
        server.presentationRemotePassword = "sermon"

        assertEquals(HttpStatusCode.Unauthorized, remotePost("next").status)
        assertEquals(HttpStatusCode.Unauthorized, remotePost("next", password = "wrong").status)
        assertEquals(HttpStatusCode.OK, remotePost("next", password = "sermon").status)
    }

    @Test
    fun `the password may also travel in the url the speaker was given`() {
        server.presentationRemoteEnabled = true
        server.presentationRemotePassword = "sermon"

        val response = runBlocking { client.post(url("/api/presentation-remote/next?password=sermon")) }

        assertEquals(HttpStatusCode.OK, response.status, "the speaker is sent a link, not asked to type a header")
    }

    @Test
    fun `an enabled remote with no password set is open by design`() {
        server.presentationRemoteEnabled = true
        server.presentationRemotePassword = ""

        assertEquals(HttpStatusCode.OK, remotePost("next").status)
    }

    @Test
    fun `the status of the remote is readable without authenticating`() {
        // The page has to be able to say "ask the operator to switch this on" before it can do
        // anything else.
        server.presentationRemoteEnabled = false

        val status = get("/api/presentation-remote/status").obj()

        assertEquals("false", status.str("enabled"))
    }

    @Test
    fun `the status says whether a password will be needed`() {
        server.presentationRemoteEnabled = true
        server.presentationRemotePassword = "sermon"

        assertEquals(
            "true",
            get("/api/presentation-remote/status").obj().str("passwordRequired"),
            "the page shows its password field based on this",
        )
    }

    @Test
    fun `the status reports where in the deck the presentation is`() {
        server.presentationRemoteEnabled = true
        server.broadcastSlideChange(id = "deck-1", index = 4, total = 12, isPlaying = true)

        val status = get("/api/presentation-remote/status").obj()

        assertEquals("deck-1", status.str("id"))
        assertEquals("4", status.str("index"), "the speaker's phone shows 'slide 5 of 12' from this")
        assertEquals("12", status.str("total"))
        assertEquals("true", status.str("isPlaying"))
    }

    @Test
    fun `a first connection asks the operator to approve the device`() {
        server.presentationRemoteEnabled = true
        val prompts = mutableListOf<PendingConnectionRequest>()
        collecting(server.onPresentationRemoteConnect) { prompts.add(it); it.decision.complete(true) }

        val response = runBlocking {
            client.post(url("/api/presentation-remote/auth")) { header(Constants.HEADER_DEVICE_ID, "speaker-phone") }
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("speaker-phone", prompts.single().clientId, "the prompt names the device asking")
    }

    @Test
    fun `a device the operator refuses is turned away`() {
        server.presentationRemoteEnabled = true
        collecting(server.onPresentationRemoteConnect) { it.decision.complete(false) }

        assertEquals(HttpStatusCode.Forbidden, remotePost("auth").status)
    }

    @Test
    fun `advancing a slide moves one forward and stops at the end of the deck`() {
        server.presentationRemoteEnabled = true
        server.broadcastSlideChange(id = "deck-1", index = 4, total = 12, isPlaying = false)
        val goto = CompletableDeferred<Int>()
        collecting(server.onPresentationGoto) { goto.complete(it) }

        remotePost("next")

        assertEquals(5, runBlocking { withTimeoutOrNull(2_000) { goto.await() } })
    }

    @Test
    fun `the last slide does not advance past the end`() {
        server.presentationRemoteEnabled = true
        server.broadcastSlideChange(id = "deck-1", index = 11, total = 12, isPlaying = false)
        val goto = CompletableDeferred<Int>()
        collecting(server.onPresentationGoto) { goto.complete(it) }

        remotePost("next")

        assertEquals(
            11,
            runBlocking { withTimeoutOrNull(2_000) { goto.await() } },
            "running off the end mid-sermon would blank the screen",
        )
    }

    @Test
    fun `the first slide does not go back past the start`() {
        server.presentationRemoteEnabled = true
        server.broadcastSlideChange(id = "deck-1", index = 0, total = 12, isPlaying = false)
        val goto = CompletableDeferred<Int>()
        collecting(server.onPresentationGoto) { goto.complete(it) }

        remotePost("previous")

        assertEquals(0, runBlocking { withTimeoutOrNull(2_000) { goto.await() } })
    }

    @Test
    fun `jumping to a slide outside the deck lands on the nearest one`() {
        server.presentationRemoteEnabled = true
        server.broadcastSlideChange(id = "deck-1", index = 0, total = 12, isPlaying = false)
        val jumps = mutableListOf<Int>()
        collecting(server.onPresentationGoto) { jumps.add(it) }

        remotePost("goto/99")
        remotePost("goto/0")

        assertEquals(
            listOf(11, 0),
            runBlocking { withTimeoutOrNull(2_000) { while (jumps.size < 2) kotlinx.coroutines.delay(10); jumps.toList() } },
            "a stale phone asking for a slide the deck no longer has must not crash the deck",
        )
    }

    @Test
    fun `a jump to something that is not a slide number is refused`() {
        server.presentationRemoteEnabled = true

        assertEquals(HttpStatusCode.BadRequest, remotePost("goto/not-a-number").status)
    }

    @Test
    fun `the remote's own buttons reach the app`() {
        server.presentationRemoteEnabled = true
        val fired = mutableListOf<String>()
        collecting(server.onPresentationFreezeToggle) { fired.add("freeze") }
        collecting(server.onPresentationPlayPause) { fired.add("play-pause") }
        collecting(server.onPresentationLoopToggle) { fired.add("loop") }
        collecting(server.onPresentationGoLive) { fired.add("go-live") }

        listOf("freeze", "play-pause", "loop", "go-live").forEach { remotePost(it) }

        assertEquals(
            setOf("freeze", "play-pause", "loop", "go-live"),
            runBlocking { withTimeoutOrNull(2_000) { while (fired.size < 4) kotlinx.coroutines.delay(10); fired.toSet() } },
        )
    }

    // ── Files a linked instance downloads ───────────────────────────────────────

    @Test
    fun `a local media file in the schedule can be streamed`() {
        // How a follower instance plays the primary's video without a copy of the file.
        val dir = Files.createTempDirectory("cp-server-media").toFile()
        try {
            val clip = File(dir, "clip.mp4").also { it.writeBytes(ByteArray(4_096) { i -> (i % 251).toByte() }) }
            server.updateSchedule(
                listOf(
                    ScheduleItem.MediaItem(
                        id = "media-1", mediaUrl = clip.absolutePath, mediaTitle = "Baptism video", mediaType = "local",
                    ),
                ),
            )

            val response = get("${Constants.ENDPOINT_MEDIA_STREAM}/media-1")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(4_096, runBlocking { response.readRawBytes().size })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a media stream can be seeked into rather than downloaded whole`() {
        val dir = Files.createTempDirectory("cp-server-media").toFile()
        try {
            val clip = File(dir, "clip.mp4").also { it.writeBytes(ByteArray(4_096) { i -> (i % 251).toByte() }) }
            server.updateSchedule(
                listOf(
                    ScheduleItem.MediaItem(
                        id = "media-1", mediaUrl = clip.absolutePath, mediaTitle = "Baptism video", mediaType = "local",
                    ),
                ),
            )

            val response = runBlocking {
                client.get(url("${Constants.ENDPOINT_MEDIA_STREAM}/media-1")) {
                    header(io.ktor.http.HttpHeaders.Range, "bytes=1024-2047")
                }
            }

            assertEquals(
                HttpStatusCode.PartialContent,
                response.status,
                "without range support a follower has to download the whole clip before it can seek",
            )
            assertEquals(1_024, runBlocking { response.readRawBytes().size })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a media item that is not in the schedule is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_MEDIA_STREAM}/no-such-item").status)
    }

    @Test
    fun `a streamed media item is not offered once its file has gone`() {
        val dir = Files.createTempDirectory("cp-server-media").toFile()
        try {
            val clip = File(dir, "clip.mp4").also { it.writeBytes(ByteArray(16)) }
            server.updateSchedule(
                listOf(
                    ScheduleItem.MediaItem(
                        id = "media-1", mediaUrl = clip.absolutePath, mediaTitle = "Clip", mediaType = "local",
                    ),
                ),
            )
            clip.delete()

            assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_MEDIA_STREAM}/media-1").status)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a streamed item is only offered for files, not for links`() {
        // A YouTube URL is not ours to serve; the follower opens it itself.
        server.updateSchedule(
            listOf(
                ScheduleItem.MediaItem(
                    id = "media-1", mediaUrl = "https://youtube.com/watch?v=x", mediaTitle = "Clip", mediaType = "youtube",
                ),
            ),
        )

        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_MEDIA_STREAM}/media-1").status)
    }

    @Test
    fun `the bible file is offered for download once one is loaded`() {
        val dir = Files.createTempDirectory("cp-server-biblefile").toFile()
        try {
            val file = SpbFixture.spbFile(dir)
            server.updateBible(SpbFixture.loadedBible(dir), "KJV", filePath = file.absolutePath)

            val response = get(Constants.ENDPOINT_BIBLE_FILE)

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(
                "##Title:" in response.text(),
                "a follower loads these bytes through the same parser the desktop uses",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `no bible file is offered when the path is unknown`() {
        val dir = Files.createTempDirectory("cp-server-biblefile").toFile()
        try {
            // Loaded from memory with no file behind it — the follower must be told, not fed an
            // empty file it would then cache as a bible.
            server.updateBible(SpbFixture.loadedBible(dir), "KJV")

            assertEquals(HttpStatusCode.NotFound, get(Constants.ENDPOINT_BIBLE_FILE).status)
        } finally {
            dir.deleteRecursively()
        }
    }
}
