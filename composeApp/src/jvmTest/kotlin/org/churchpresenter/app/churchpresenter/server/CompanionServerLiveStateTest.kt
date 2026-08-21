package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.models.songs.LyricSection
import org.churchpresenter.app.churchpresenter.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * What the server tells clients is **on screen right now** — `updateLiveState` and the
 * `live_state_changed` frame it produces.
 *
 * This is the single message a phone's "now showing" view and an Instance Link follower's mirrored
 * output are both built from, so what matters is that the content the app hands over survives the
 * trip intact for each kind of content, and that a follower joining late is told the current state
 * rather than having to wait for the next change. The other half is the de-duplication: content
 * setters fire on every redraw, so an unchanged state must not put a frame on the wire at all —
 * without that, one live verse can flood the shared broadcast buffer and evict messages destined
 * for a slow client.
 *
 * Live state is WebSocket-only (there is no GET for it), so every assertion here reads real frames
 * off a real socket against a real server — `start()` builds its own Netty server rather than
 * exposing a separable Ktor module.
 */
private fun liveStatePayloadOf(frame: Frame, json: Json): JsonObject? {
    val text = (frame as? Frame.Text)?.readText() ?: return null
    val obj = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return null
    if (obj["type"]?.jsonPrimitive?.content != Constants.WS_EVENT_LIVE_STATE_CHANGED) return null
    return json.parseToJsonElement(obj["payload"]!!.jsonPrimitive.content) as JsonObject
}

class CompanionServerLiveStateTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private val json = Json { ignoreUnknownKeys = true }

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_715))
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
        client = HttpClient(CIO) { install(WebSockets) }
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    /** Everything `updateLiveState` takes, so each test names only the fields it is about. */
    private fun goLive(
        mode: String,
        verse: SelectedVerse? = null,
        section: LyricSection? = null,
        picturePath: String? = null,
        announcement: String? = null,
        websiteUrl: String? = null,
        websiteTitle: String? = null,
        lowerThirdName: String? = null,
        verseCode: Triple<Int, Int, Int>? = null,
        songSectionIndex: Int? = null,
        songLineIndex: Int? = null,
    ) = server.updateLiveState(
        mode = mode,
        bibleVerse = verse,
        lyricSection = section,
        pictureImagePath = picturePath,
        mediaUrl = null,
        mediaType = null,
        announcementText = announcement,
        websiteUrl = websiteUrl,
        websiteTitle = websiteTitle,
        sceneId = null,
        sceneName = null,
        questionId = null,
        questionText = null,
        dictionaryWord = null,
        lowerThirdName = lowerThirdName,
        verseCode = verseCode,
        songSectionIndex = songSectionIndex,
        songLineIndex = songLineIndex,
    )

    /**
     * Connects and returns the live-state payload from the connect snapshot, or null if the
     * snapshot carried none.
     *
     * Stops at the live-state frame rather than reading until the socket goes quiet, so the cost is
     * the frame arriving and not a fixed window. The snapshot's last frame is the live state, so a
     * short idle window only has to cover "there is no live state at all".
     */
    private fun snapshotLiveState(quietMs: Long = 250): JsonObject? = runBlocking {
        var payload: JsonObject? = null
        withTimeoutOrNull(quietMs + 10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                var done = false
                while (!done) {
                    val frame = withTimeoutOrNull(quietMs) { incoming.receive() }
                    val obj = (frame as? Frame.Text)
                        ?.let { runCatching { json.parseToJsonElement(it.readText()) as JsonObject }.getOrNull() }
                    when {
                        frame == null -> done = true
                        obj?.get("type")?.jsonPrimitive?.content == Constants.WS_EVENT_LIVE_STATE_CHANGED -> {
                            payload = json.parseToJsonElement(
                                obj["payload"]!!.jsonPrimitive.content
                            ) as JsonObject
                            done = true
                        }
                    }
                }
            }
        }
        payload
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    /**
     * Asserts a field carries no value.
     *
     * The DTO is serialized with explicit nulls, so "not set" reaches the client as `"key": null`
     * rather than as a missing key — either shape is a client drawing nothing, and both are checked
     * here so this does not quietly pass on a typo'd key name.
     */
    private fun assertNotSet(state: JsonObject, key: String) {
        val value = state[key]
        assertTrue(
            value == null || value is JsonNull,
            "$key should carry no value, got $value",
        )
    }

    // ── The connect snapshot ────────────────────────────────────────────────────

    @Test
    fun `a client that connects mid-service is told what is already on screen`() {
        goLive(
            mode = "BIBLE",
            verse = SelectedVerse(
                bibleName = "King James Version",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "For God so loved the world.",
            ),
            verseCode = Triple(43, 3, 16),
        )

        val state = snapshotLiveState() ?: error("the snapshot carried no live state")

        assertEquals("BIBLE", state.str("contentType"))
        assertEquals("John", state.str("bookName"))
        assertEquals("3", state.str("chapter"))
        assertEquals("16", state.str("verseNumber"))
        assertEquals("For God so loved the world.", state.str("verseText"))
        // The canonical code is what a follower on a different translation looks the verse up by.
        assertEquals("43", state.str("verseCodeBook"))
        assertEquals("3", state.str("verseCodeChapter"))
        assertEquals("16", state.str("verseCodeVerse"))
    }

    @Test
    fun `a song section arrives with its lines and the position within it`() {
        goLive(
            mode = "SONG",
            section = LyricSection(
                title = "Amazing Grace",
                songNumber = 12,
                type = "verse",
                lines = listOf("Amazing grace, how sweet the sound", "That saved a wretch like me"),
            ),
            songSectionIndex = 2,
            songLineIndex = 1,
        )

        val state = snapshotLiveState() ?: error("the snapshot carried no live state")

        assertEquals("SONG", state.str("contentType"))
        assertEquals("Amazing Grace", state.str("songTitle"))
        assertEquals("12", state.str("songNumber"))
        assertEquals("verse", state.str("sectionType"))
        assertTrue(
            state["lines"].toString().contains("That saved a wretch like me"),
            "the lines on screen: ${state["lines"]}",
        )
        // Which line is lit — a stage monitor is useless without it.
        assertEquals("2", state.str("songSectionIndex"))
        assertEquals("1", state.str("songLineIndex"))
    }

    @Test
    fun `a blank field is omitted rather than sent as an empty string`() {
        // A client checks presence to decide what to draw, so "" and absent must not both occur.
        goLive(
            mode = "BIBLE",
            verse = SelectedVerse(bookName = "Psalms", chapter = 23, verseNumber = 1, verseRange = ""),
        )

        val state = snapshotLiveState() ?: error("the snapshot carried no live state")

        assertEquals("Psalms", state.str("bookName"))
        assertNotSet(state, "verseRange")
        assertNotSet(state, "songTitle")
    }

    @Test
    fun `a picture on screen is reported by folder and index, not by file path`() {
        // A phone cannot open the operator's filesystem, so the live state has to name the picture
        // in terms of the catalog it was already given.
        val folder = java.nio.file.Files.createTempDirectory("cp-live-pictures").toFile()
        try {
            val images = listOf("a.jpg", "b.jpg", "c.jpg").map { name ->
                java.io.File(folder, name).apply { writeBytes(ByteArray(4)) }
            }
            server.updatePictures("folder-1", "Slides", folder.absolutePath, images)

            goLive(mode = "PICTURE", picturePath = images[1].absolutePath)
            val state = snapshotLiveState() ?: error("the snapshot carried no live state")

            assertEquals("folder-1", state.str("pictureFolderId"))
            assertEquals("1", state.str("pictureIndex"), "the second image in that folder")
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a picture from outside every registered folder is reported without a location`() {
        goLive(mode = "PICTURE", picturePath = "/somewhere/else/unregistered.jpg")

        val state = snapshotLiveState() ?: error("the snapshot carried no live state")

        assertEquals("PICTURE", state.str("contentType"))
        assertNotSet(state, "pictureFolderId")
        assertNotSet(state, "pictureIndex")
    }

    // ── Broadcasting changes ────────────────────────────────────────────────────

    /** Distinguishes each test's marker state from the last one's; not a clock, so it is stable. */
    private var marker = 0

    /**
     * Connects, runs [change], and returns the live-state payloads that arrive until one satisfies
     * [until] — so the wait ends on the frame the test is about, not on a timer.
     *
     * The server keeps one live state for the whole class, and the connect snapshot replays it, so
     * a leftover from the previous test would otherwise be counted as one of this test's frames.
     * Setting a marker state before connecting makes the boundary explicit: everything up to and
     * including the marker belongs to the snapshot, everything after it to [change]. A test that
     * needs a particular state to already be live passes [syncOn] to sync on that instead.
     */
    private fun liveStatesAfter(
        change: () -> Unit,
        until: (JsonObject) -> Boolean,
        syncOn: ((JsonObject) -> Boolean)? = null,
    ): List<JsonObject> = runBlocking {
        val received = mutableListOf<JsonObject>()
        val markerMode = "MARKER_${marker++}"
        if (syncOn == null) goLive(mode = markerMode)
        val synced = syncOn ?: { it.str("contentType") == markerMode }
        withTimeoutOrNull(15_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                // Read the snapshot out of the way, ending on the marker — which is also the
                // positive signal that this session is registered for broadcasts, so pushing the
                // change now cannot race the registration.
                var done = false
                while (!done) {
                    val payload = liveStatePayloadOf(incoming.receive(), json)
                    if (payload != null && synced(payload)) done = true
                }
                change()
                var collected = false
                while (!collected) {
                    val payload = liveStatePayloadOf(incoming.receive(), json)
                    if (payload != null) {
                        received += payload
                        if (until(payload)) collected = true
                    }
                }
            }
        }
        received
    }

    @Test
    fun `going live after a client has connected reaches it`() {
        val states = liveStatesAfter(
            change = { goLive(mode = "ANNOUNCEMENT", announcement = "Coffee is served") },
            until = { it.str("announcementText") == "Coffee is served" },
        )

        assertTrue(states.isNotEmpty(), "the change should have been broadcast")
        assertEquals("ANNOUNCEMENT", states.last().str("contentType"))
    }

    @Test
    fun `showing the same thing again puts nothing on the wire`() {
        // Content setters fire on every redraw, so the same state arrives over and over. With
        // "First" already live, sending it again and then "Second" must put exactly one frame on
        // the wire; reading until "Second" arrives shows whether the repeat produced one before it.
        // (Only one frame is expected in flight on purpose: several back-to-back can be coalesced
        // by the broadcast buffer's own flood protection, which would make this racy.)
        val website = { title: String ->
            goLive(mode = "WEBSITE", websiteUrl = "https://example.org/$title", websiteTitle = title)
        }
        website("First")

        val states = liveStatesAfter(
            syncOn = { it.str("websiteTitle") == "First" },
            change = {
                website("First")   // identical to what is already live — must be swallowed
                website("Second")
            },
            until = { it.str("websiteTitle") == "Second" },
        )

        assertEquals(
            listOf("Second"),
            states.map { it.str("websiteTitle") },
            "the repeat must not have produced a frame of its own",
        )
    }

    @Test
    fun `switching content type replaces the previous live state entirely`() {
        val states = liveStatesAfter(
            change = {
                goLive(mode = "LOWER_THIRD", lowerThirdName = "Welcome")
                goLive(
                    mode = "BIBLE",
                    verse = SelectedVerse(bookName = "Genesis", chapter = 1, verseNumber = 1),
                )
            },
            until = { it.str("contentType") == "BIBLE" },
        )

        val last = states.last()
        assertEquals("Genesis", last.str("bookName"))
        assertNotSet(last, "lowerThirdName")
    }
}
