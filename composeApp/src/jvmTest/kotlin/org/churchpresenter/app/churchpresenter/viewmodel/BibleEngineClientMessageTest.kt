package org.churchpresenter.app.churchpresenter.viewmodel

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the Bible Lookup Engine says over the wire, and what the app does with it.
 *
 * Every automatic verse that lands on the screen during a service arrives as one of these JSON
 * frames. The engine is a separate module with its own release cycle, so this side has to be
 * forgiving about missing and null fields without ever forwarding a half-formed reference — a
 * detection with a bad book id would put the wrong passage in front of the congregation.
 *
 * `handleMessage` is private and only reachable from the WebSocket read loop, so — as with
 * `CrashReporter.scrubPii` — it is invoked directly by reflection. The link itself (connecting,
 * tuning, reconnecting) is covered by [BibleEngineClientLinkTest].
 */
class BibleEngineClientMessageTest {

    /** One [BibleEngineClient.onScripture] call, captured. */
    private data class Detection(
        val bookId: Int,
        val chapter: Int,
        val verseStart: Int,
        val verseEnd: Int?,
        val verseText: String,
        val matchType: String,
        val codeStart: String?,
        val codeEnd: String?,
        val segmentId: String?,
        val sessionId: String?,
        val tracks: List<String>,
    )

    private val detections = mutableListOf<Detection>()
    private val created = mutableListOf<BibleEngineClient>()

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        detections.clear()
    }

    private fun client(): BibleEngineClient =
        BibleEngineClient { book, chapter, start, end, text, match, codeStart, codeEnd, segment, session, tracks ->
            detections.add(Detection(book, chapter, start, end, text, match, codeStart, codeEnd, segment, session, tracks))
        }.also { created.add(it) }

    /** Feeds [raw] through the private WebSocket message handler. */
    private fun BibleEngineClient.receive(raw: String) {
        BibleEngineClient::class.java
            .getDeclaredMethod("handleMessage", String::class.java)
            .apply { isAccessible = true }
            .invoke(this, raw)
    }

    private fun detection(
        type: String = "scripture.detected",
        reference: String = """{"bookId":43,"chapter":3,"verseStart":16}""",
        extra: String = "",
    ) = """{"type":"$type","reference":$reference${if (extra.isEmpty()) "" else ",$extra"}}"""

    // ── A well-formed detection ─────────────────────────────────────────────────

    @Test
    fun `a full detection is forwarded field for field`() {
        val c = client()
        c.receive(
            """{"type":"scripture.detected",
                "reference":{"bookId":43,"chapter":3,"verseStart":16,"verseEnd":18,
                             "canonicalCodeStart":"B043C003V016","canonicalCodeEnd":"B043C003V018"},
                "verseText":"For God so loved the world",
                "matchType":"explicit",
                "segmentId":"seg-9",
                "sessionId":"service-2026-07-22",
                "tracks":["transcription","translation"]}"""
        )
        val d = detections.single()
        assertEquals(43, d.bookId)
        assertEquals(3, d.chapter)
        assertEquals(16, d.verseStart)
        assertEquals(18, d.verseEnd)
        assertEquals("For God so loved the world", d.verseText)
        assertEquals("explicit", d.matchType)
        assertEquals("B043C003V016", d.codeStart)
        assertEquals("B043C003V018", d.codeEnd)
        assertEquals("seg-9", d.segmentId)
        assertEquals("service-2026-07-22", d.sessionId)
        assertEquals(listOf("transcription", "translation"), d.tracks)
    }

    @Test
    fun `every scripture event type is forwarded, whatever the suffix`() {
        val c = client()
        c.receive(detection(type = "scripture.detected"))
        c.receive(detection(type = "scripture.continuation"))
        c.receive(detection(type = "scripture.some.future.variant"))
        assertEquals(3, detections.size, "the engine may add event kinds; the app should not have to be taught each one")
    }

    // ── Frames that must be ignored ─────────────────────────────────────────────

    @Test
    fun `a frame that is not json is ignored`() {
        val c = client()
        c.receive("not json at all")
        c.receive("")
        assertTrue(detections.isEmpty())
    }

    @Test
    fun `an unrelated message type is ignored`() {
        val c = client()
        c.receive("""{"type":"heartbeat"}""")
        c.receive("""{"type":"log","message":"hello"}""")
        assertTrue(detections.isEmpty())
    }

    @Test
    fun `a type that merely starts with the same letters is not a scripture event`() {
        val c = client()
        c.receive("""{"type":"scriptures","reference":{"bookId":43,"chapter":3,"verseStart":16}}""")
        assertTrue(detections.isEmpty(), "the prefix is 'scripture.', dot included")
    }

    @Test
    fun `a message with no type at all is ignored`() {
        val c = client()
        c.receive("""{"reference":{"bookId":43,"chapter":3,"verseStart":16}}""")
        assertTrue(detections.isEmpty())
    }

    @Test
    fun `a detection with no reference is dropped`() {
        val c = client()
        c.receive("""{"type":"scripture.detected","verseText":"For God so loved the world"}""")
        assertTrue(detections.isEmpty(), "verse text with no reference cannot be put on screen")
    }

    @Test
    fun `a detection with no book is dropped`() {
        val c = client()
        c.receive("""{"type":"scripture.detected","reference":{"chapter":3,"verseStart":16}}""")
        assertTrue(detections.isEmpty(), "a missing book would land the reference in whatever book is book 0")
    }

    @Test
    fun `a detection with a negative book is dropped`() {
        val c = client()
        c.receive("""{"type":"scripture.detected","reference":{"bookId":-1,"chapter":3,"verseStart":16}}""")
        assertTrue(detections.isEmpty())
    }

    @Test
    fun `a non-numeric book is treated as missing`() {
        val c = client()
        c.receive("""{"type":"scripture.detected","reference":{"bookId":"John","chapter":3,"verseStart":16}}""")
        assertTrue(detections.isEmpty())
    }

    // ── Optional fields ─────────────────────────────────────────────────────────

    @Test
    fun `a single-verse detection has no end verse`() {
        val c = client()
        c.receive(detection())
        assertNull(detections.single().verseEnd, "an end verse equal to the start would render as a one-verse range")
    }

    @Test
    fun `an explicitly null end verse is treated as a single verse`() {
        val c = client()
        c.receive(detection(reference = """{"bookId":43,"chapter":3,"verseStart":16,"verseEnd":null}"""))
        assertNull(detections.single().verseEnd)
    }

    @Test
    fun `missing chapter and verse fall back to zero rather than dropping the detection`() {
        val c = client()
        c.receive(detection(reference = """{"bookId":43}"""))
        val d = detections.single()
        assertEquals(0, d.chapter)
        assertEquals(0, d.verseStart)
    }

    @Test
    fun `a detection with no verse text still comes through`() {
        val c = client()
        c.receive(detection())
        assertEquals("", detections.single().verseText, "the app looks the text up itself from its own Bible")
    }

    @Test
    fun `an unstated match type is treated as a reverse lookup`() {
        val c = client()
        c.receive(detection())
        assertEquals(
            "reverse",
            detections.single().matchType,
            "reverse is the cautious default: it is the tier that stages rather than going straight live"
        )
    }

    @Test
    fun `missing canonical codes come through as null, not as empty strings`() {
        val c = client()
        c.receive(detection())
        assertNull(detections.single().codeStart, "an empty code would be mistaken for a real one downstream")
        assertNull(detections.single().codeEnd)
    }

    @Test
    fun `an empty canonical code is normalised to null`() {
        val c = client()
        c.receive(detection(reference = """{"bookId":43,"chapter":3,"verseStart":16,"canonicalCodeStart":"","canonicalCodeEnd":""}"""))
        assertNull(detections.single().codeStart)
        assertNull(detections.single().codeEnd)
    }

    @Test
    fun `an explicitly null canonical end code is null`() {
        val c = client()
        c.receive(
            detection(reference = """{"bookId":43,"chapter":3,"verseStart":16,"canonicalCodeStart":"B043C003V016","canonicalCodeEnd":null}""")
        )
        assertEquals("B043C003V016", detections.single().codeStart)
        assertNull(detections.single().codeEnd)
    }

    @Test
    fun `missing, null and empty correlation ids all come through as null`() {
        val c = client()
        c.receive(detection())
        c.receive(detection(extra = """"segmentId":null,"sessionId":null"""))
        c.receive(detection(extra = """"segmentId":"","sessionId":"""""))
        assertEquals(3, detections.size)
        assertTrue(detections.all { it.segmentId == null && it.sessionId == null }, "an empty id joins nothing")
    }

    // ── Corroborating tracks ────────────────────────────────────────────────────

    @Test
    fun `a detection with no tracks reports an empty list`() {
        val c = client()
        c.receive(detection())
        assertEquals(emptyList(), detections.single().tracks)
    }

    @Test
    fun `a single corroborating track is reported`() {
        val c = client()
        c.receive(detection(extra = """"tracks":["transcription"]"""))
        assertEquals(listOf("transcription"), detections.single().tracks)
    }

    @Test
    fun `blank track entries are dropped`() {
        val c = client()
        c.receive(detection(extra = """"tracks":["transcription","","translation"]"""))
        assertEquals(listOf("transcription", "translation"), detections.single().tracks)
    }

    @Test
    fun `an empty track list stays empty`() {
        val c = client()
        c.receive(detection(extra = """"tracks":[]"""))
        assertEquals(emptyList(), detections.single().tracks)
    }

    // ── Engine status ───────────────────────────────────────────────────────────

    @Test
    fun `the engine's own stt health is unknown until it says`() {
        assertNull(client().engineSttConnected.value, "unknown must be distinguishable from disconnected")
    }

    @Test
    fun `an engine reporting a healthy stt link is believed`() {
        val c = client()
        c.receive("""{"type":"engine_status","sttConfigured":true,"sttConnected":true}""")
        assertEquals(true, c.engineSttConnected.value)
    }

    @Test
    fun `an engine reporting a broken stt link is believed`() {
        val c = client()
        c.receive("""{"type":"engine_status","sttConfigured":true,"sttConnected":false}""")
        assertEquals(false, c.engineSttConnected.value)
    }

    @Test
    fun `an engine with no stt configured is not flagged as broken`() {
        val c = client()
        c.receive("""{"type":"engine_status","sttConfigured":false,"sttConnected":false}""")
        assertEquals(
            true,
            c.engineSttConnected.value,
            "a websocket-input-only engine has no upstream to lose; flagging it would report a non-error"
        )
    }

    @Test
    fun `an engine that omits the configured flag is assumed to have one`() {
        val c = client()
        c.receive("""{"type":"engine_status","sttConnected":false}""")
        assertEquals(false, c.engineSttConnected.value)
    }

    @Test
    fun `an engine status with nothing in it reads as disconnected`() {
        val c = client()
        c.receive("""{"type":"engine_status"}""")
        assertEquals(false, c.engineSttConnected.value)
    }

    @Test
    fun `engine status transitions are tracked`() {
        val c = client()
        c.receive("""{"type":"engine_status","sttConnected":true}""")
        c.receive("""{"type":"engine_status","sttConnected":false}""")
        assertEquals(false, c.engineSttConnected.value)
        c.receive("""{"type":"engine_status","sttConnected":true}""")
        assertEquals(true, c.engineSttConnected.value)
    }

    @Test
    fun `an engine status is never mistaken for a detection`() {
        val c = client()
        c.receive("""{"type":"engine_status","sttConnected":true,"reference":{"bookId":43,"chapter":3,"verseStart":16}}""")
        assertTrue(detections.isEmpty())
    }

    // ── Start-up failure ────────────────────────────────────────────────────────

    @Test
    fun `a fresh client is disconnected and not in a failed state`() {
        val c = client()
        assertFalse(c.connected.value)
        assertFalse(c.startFailed.value)
    }

    @Test
    fun `an in-process engine with no bible folder reports a start failure`() {
        val c = client()
        c.start(
            sttUrl = "http://127.0.0.1:9999", bibleRoot = "", bibleFiles = emptyList(),
            runLocal = true, host = "127.0.0.1", port = 0, level = "off"
        )
        awaitUntil("the failed start to be reported") { c.startFailed.value }
        assertFalse(c.connected.value, "there is no engine to connect to, so the retry loop must not run")
    }

    @Test
    fun `stopping clears a start failure so the next attempt starts clean`() {
        val c = client()
        c.start(
            sttUrl = "http://127.0.0.1:9999", bibleRoot = "", bibleFiles = emptyList(),
            runLocal = true, host = "127.0.0.1", port = 0, level = "off"
        )
        awaitUntil("the failed start to be reported") { c.startFailed.value }

        c.stop()

        assertFalse(c.startFailed.value)
        assertNull(c.engineSttConnected.value)
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }
}
