package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.utils.Constants
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four state broadcasts a desktop pushes at connected phones that nothing else covered:
 * [CompanionServer.updateAutoScrollInterval], [CompanionServer.updateLoopingState],
 * [CompanionServer.broadcastFreezeChange] and [CompanionServer.broadcastMediaState].
 *
 * Their siblings on either side ([CompanionServer.broadcastSlideChange],
 * [CompanionServer.updatePresentationLiveStatus]) were already covered *incidentally* — a test
 * called them to arrange state and then asserted the `/api/presentation-remote/status` response.
 * These four cannot be reached that way: three of the four store nothing readable over REST, and
 * `broadcastMediaState` stores nothing at all. What they produce is a WebSocket frame, so that is
 * what is asserted here.
 *
 * **No server is started and no port is bound.** `broadcast()` only touches the scope and the flow,
 * both built in the constructor, so `CompanionServer()` on its own is enough — which also means
 * this suite cannot collide with a sibling over a port. `broadcastChannel` is `internal` for this.
 *
 * Every payload here is hand-built by string concatenation rather than serialized, so each is
 * asserted by **parsing it back**: a test that string-matched would keep passing on output no phone
 * can decode. That is also why the escaping test below matters.
 */
class CompanionServerBroadcastStateTest {

    private lateinit var server: CompanionServer
    private lateinit var received: CopyOnWriteArrayList<String>
    private var collectorScope: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        server = CompanionServer()
        received = CopyOnWriteArrayList()
        val scope = CoroutineScope(Dispatchers.IO).also { collectorScope = it }
        scope.launch { server.broadcastChannel.collect { received.add(it) } }
        // The flow has no replay, so a broadcast sent before the collector attached would be lost
        // and the test would fail on timing rather than behaviour. Wait for the subscription itself.
        runBlocking {
            withTimeoutOrNull(5_000) { server.broadcastChannel.subscriptionCount.first { it > 0 } }
                ?: error("collector never subscribed")
        }
    }

    @AfterTest
    fun tearDown() {
        runCatching { collectorScope?.cancel() }
        collectorScope = null
    }

    /** Waits for a frame of [type] to arrive and returns its payload, parsed. */
    private fun awaitPayload(type: String): Map<String, kotlinx.serialization.json.JsonElement> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            framesOf(type).lastOrNull()?.let { return it }
            Thread.sleep(2)
        }
        throw AssertionError("no $type frame arrived; saw ${received.map { typeOf(it) }}")
    }

    private fun typeOf(raw: String): String =
        json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content.orEmpty()

    /** Every frame of [type] received so far, each with its payload parsed as an object. */
    private fun framesOf(type: String): List<Map<String, kotlinx.serialization.json.JsonElement>> =
        received.mapNotNull { raw ->
            val msg = json.parseToJsonElement(raw).jsonObject
            if (msg["type"]?.jsonPrimitive?.content != type) return@mapNotNull null
            val payload = msg["payload"]?.jsonPrimitive?.content.orEmpty()
            if (payload.isBlank()) emptyMap() else json.parseToJsonElement(payload).jsonObject
        }

    // ── Auto-scroll interval ──────────────────────────────────────────────────

    @Test
    fun `a new auto-scroll interval reaches connected phones`() {
        server.updateAutoScrollInterval(9)

        val payload = awaitPayload(Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED)

        assertEquals(9, payload.getValue("autoScrollInterval").jsonPrimitive.int)
    }

    @Test
    fun `re-sending the same auto-scroll interval broadcasts nothing`() {
        // main.kt drives this from a LaunchedEffect keyed on the setting, so the desktop can call it
        // with an unchanged value on any recomposition. Without the guard every connected phone
        // takes a frame it has no use for.
        server.updateAutoScrollInterval(9)
        awaitPayload(Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED)

        server.updateAutoScrollInterval(9)
        // Nothing to wait *for*, so wait on a later change instead — and one that is a different
        // value, so its own arrival cannot be mistaken for the suppressed one.
        server.updateAutoScrollInterval(11)
        awaitUntil("the second interval change") {
            framesOf(Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED)
                .any { it.getValue("autoScrollInterval").jsonPrimitive.int == 11 }
        }

        assertEquals(
            listOf(9, 11),
            framesOf(Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED)
                .map { it.getValue("autoScrollInterval").jsonPrimitive.int },
            "the repeated 9 should have been swallowed by the guard",
        )
    }

    @Test
    fun `the default auto-scroll interval is not re-broadcast as a change`() {
        // Five is what the field already holds, so this is the same guard reached from the value the
        // server starts on rather than one a test set.
        server.updateAutoScrollInterval(5)
        server.updateAutoScrollInterval(7)
        awaitPayload(Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED)

        assertEquals(
            listOf(7),
            framesOf(Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED)
                .map { it.getValue("autoScrollInterval").jsonPrimitive.int },
        )
    }

    // ── Looping ───────────────────────────────────────────────────────────────

    @Test
    fun `turning looping off reaches connected phones`() {
        // The server starts out looping, so false is the value that is actually a change.
        server.updateLoopingState(false)

        val payload = awaitPayload(Constants.WS_EVENT_PRESENTATION_LOOP_CHANGED)

        assertEquals(false, payload.getValue("looping").jsonPrimitive.boolean)
    }

    @Test
    fun `re-sending the same looping state broadcasts nothing`() {
        server.updateLoopingState(false)
        awaitPayload(Constants.WS_EVENT_PRESENTATION_LOOP_CHANGED)

        server.updateLoopingState(false)
        server.updateLoopingState(true)
        awaitUntil("looping back on") {
            framesOf(Constants.WS_EVENT_PRESENTATION_LOOP_CHANGED)
                .any { it.getValue("looping").jsonPrimitive.boolean }
        }

        assertEquals(
            listOf(false, true),
            framesOf(Constants.WS_EVENT_PRESENTATION_LOOP_CHANGED)
                .map { it.getValue("looping").jsonPrimitive.boolean },
            "the repeated false should have been swallowed by the guard",
        )
    }

    // ── Freeze ────────────────────────────────────────────────────────────────

    @Test
    fun `a freeze reaches connected phones`() {
        server.broadcastFreezeChange(true)

        assertEquals(
            true,
            awaitPayload(Constants.WS_EVENT_PRESENTATION_FREEZE_CHANGED)
                .getValue("frozen").jsonPrimitive.boolean,
        )
    }

    @Test
    fun `freeze has no matching-value guard, and that is deliberate`() {
        // Unlike the two above, freeze re-broadcasts an unchanged value. It has to: the remote's
        // freeze button is a toggle whose state the phone mirrors, so a phone that connected while
        // frozen and then missed a frame is re-synced by the next press rather than left inverted.
        server.broadcastFreezeChange(true)
        awaitPayload(Constants.WS_EVENT_PRESENTATION_FREEZE_CHANGED)

        server.broadcastFreezeChange(true)
        awaitUntil("the second freeze frame") {
            framesOf(Constants.WS_EVENT_PRESENTATION_FREEZE_CHANGED).size == 2
        }

        assertEquals(
            listOf(true, true),
            framesOf(Constants.WS_EVENT_PRESENTATION_FREEZE_CHANGED)
                .map { it.getValue("frozen").jsonPrimitive.boolean },
        )
    }

    // ── Media state ───────────────────────────────────────────────────────────

    @Test
    fun `the media transport state reaches the mobile media tab`() {
        server.broadcastMediaState(
            isLive = true, isLoaded = true, isPlaying = true, title = "Offering Video",
            positionMs = 12_500, durationMs = 240_000, volume = 0.8f, muted = false,
            mediaType = "video", source = "/media/offering.mp4",
        )

        val p = awaitPayload(Constants.WS_EVENT_MEDIA_STATE_CHANGED)

        assertEquals(true, p.getValue("isPlaying").jsonPrimitive.boolean)
        assertEquals("Offering Video", p.getValue("title").jsonPrimitive.content)
        assertEquals(12_500L, p.getValue("positionMs").jsonPrimitive.long)
        assertEquals(240_000L, p.getValue("durationMs").jsonPrimitive.long)
        assertEquals("video", p.getValue("mediaType").jsonPrimitive.content)
        assertEquals("/media/offering.mp4", p.getValue("source").jsonPrimitive.content)
        assertEquals(false, p.getValue("muted").jsonPrimitive.boolean)
    }

    @Test
    fun `a media title containing quotes and newlines still decodes`() {
        // The payload is assembled by string concatenation, so an unescaped quote in a file's title
        // does not corrupt one field — it makes the whole frame unparseable, and the phone's media
        // tab stops updating with nothing on screen to say why. Filenames like this are ordinary.
        val awkward = "He said \"Go\"\n\tBack\\slash"

        server.broadcastMediaState(
            isLive = true, isLoaded = true, isPlaying = false, title = awkward,
            positionMs = 0, durationMs = 1_000, volume = 1f, muted = false,
            mediaType = "audio", source = """C:\Media\hymn"1".mp3""",
        )

        val p = awaitPayload(Constants.WS_EVENT_MEDIA_STATE_CHANGED)

        // Round-tripped, not string-matched: this asserts the frame parsed *and* that the escaping
        // is reversible rather than merely producing something that happens to be valid JSON.
        assertEquals(awkward, p.getValue("title").jsonPrimitive.content)
        assertEquals("""C:\Media\hymn"1".mp3""", p.getValue("source").jsonPrimitive.content)
    }

    @Test
    fun `an unloaded player still reports itself, rather than going silent`() {
        // What the phone gets when the operator stops a clip: the tab has to be told there is
        // nothing loaded, otherwise it goes on showing the last clip's transport as though it were
        // still there.
        server.broadcastMediaState(
            isLive = false, isLoaded = false, isPlaying = false, title = "",
            positionMs = 0, durationMs = 0, volume = 0.5f, muted = true,
            mediaType = "", source = "",
        )

        val p = awaitPayload(Constants.WS_EVENT_MEDIA_STATE_CHANGED)

        assertEquals(false, p.getValue("isLoaded").jsonPrimitive.boolean)
        assertEquals(false, p.getValue("isLive").jsonPrimitive.boolean)
        assertEquals(true, p.getValue("muted").jsonPrimitive.boolean)
        assertTrue(p.getValue("title").jsonPrimitive.content.isEmpty())
    }

    /** Bounded wait that ends on [condition] becoming true; the timeout only ever fails the test. */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(2)
        }
        throw AssertionError("timed out waiting for $what")
    }

    // ── Frames whose payload is not an object ─────────────────────────────────
    //
    // The two below are the only broadcasts that do not carry a JSON object: one carries nothing
    // at all and the other a bare integer. [framesOf] parses every payload as an object, so they
    // need the raw string — and that difference is exactly what a phone's decoder has to cope
    // with, so it is worth pinning rather than papering over.

    /** Every frame of [type] received so far, payload left as the raw string it was sent as. */
    private fun rawPayloadsOf(type: String): List<String> =
        received.mapNotNull { raw ->
            val msg = json.parseToJsonElement(raw).jsonObject
            if (msg["type"]?.jsonPrimitive?.content != type) return@mapNotNull null
            msg["payload"]?.jsonPrimitive?.content.orEmpty()
        }

    @Test
    fun `clearing the display reaches connected phones as an empty frame`() {
        // The phone blanks its own preview off this. It carries no payload because there is
        // nothing to say beyond "whatever was live is not any more" — but the frame itself still
        // has to arrive, or a remote keeps showing content the room can no longer see.
        server.broadcastDisplayCleared()

        awaitUntil("the display-cleared frame") {
            rawPayloadsOf(Constants.WS_EVENT_DISPLAY_CLEARED).isNotEmpty()
        }
        assertEquals("", rawPayloadsOf(Constants.WS_EVENT_DISPLAY_CLEARED).single())
    }

    @Test
    fun `the live song section reaches connected phones as a bare index`() {
        // Sent every time the operator moves through a song, so the phone can highlight the verse
        // being sung. The payload is the index on its own, not wrapped in an object.
        server.broadcastSongSectionSelected(3)

        awaitUntil("the song-section frame") {
            rawPayloadsOf(Constants.WS_EVENT_SONG_SECTION_SELECTED).isNotEmpty()
        }
        assertEquals("3", rawPayloadsOf(Constants.WS_EVENT_SONG_SECTION_SELECTED).single())
    }

    @Test
    fun `the same song section going live twice is broadcast twice`() {
        // No matching-value guard here, for the same reason freeze has none: a phone that missed
        // a frame is re-synced by the next one. Re-selecting the section being sung is also how an
        // operator re-fires it after a manual override, and the phone has to follow.
        server.broadcastSongSectionSelected(0)
        awaitUntil("the first frame") { rawPayloadsOf(Constants.WS_EVENT_SONG_SECTION_SELECTED).size == 1 }

        server.broadcastSongSectionSelected(0)

        awaitUntil("the second frame") { rawPayloadsOf(Constants.WS_EVENT_SONG_SECTION_SELECTED).size == 2 }
        assertEquals(listOf("0", "0"), rawPayloadsOf(Constants.WS_EVENT_SONG_SECTION_SELECTED))
    }
}
