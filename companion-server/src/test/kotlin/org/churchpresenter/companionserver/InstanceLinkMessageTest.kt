package org.churchpresenter.companionserver

import io.ktor.client.network.sockets.ConnectTimeoutException
import org.churchpresenter.settings.utils.Constants
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a **follower** does with each message its primary sends over the link.
 *
 * This is the whole inbound contract for multi-room mirroring, and it is pure: a raw JSON string in,
 * a callback out. The socket that delivers those strings needs a live primary; the dispatch does not,
 * and it is where the behaviour lives.
 *
 * Two properties run through every case and both matter more than the happy path:
 *
 *  * **A malformed payload is dropped, not propagated.** These arrive from a primary that may be a
 *    different version, so an unknown type or a payload that will not decode has to leave the
 *    follower showing what it was showing. Firing a callback with junk would put junk on the wall of
 *    a room nobody is watching from.
 *  * **Every decoded message counts as liveness**, including ones this build cannot act on. The UI's
 *    "last update Xs ago" is what tells an operator the link is alive rather than frozen, so it must
 *    tick on anything that arrived intact.
 */
class InstanceLinkMessageTest {

    @BeforeTest
    fun setUp() {
        // InstanceLinkLogger resolves its path from user.home once per JVM and every branch logs.
        LogHomeLatch.latch()
    }

    /** Records what the client handed back. */
    private class Recorder {
        val schedules = mutableListOf<List<ScheduleItemDto>>()
        val liveStates = mutableListOf<LiveStateDto>()
        val songSections = mutableListOf<Int>()
        val slides = mutableListOf<String>()
        var displayCleared = 0
        var bibleUpdated = 0
        var picturesUpdated = 0
        var backgroundsUpdated = 0
        var messagesReceived = 0
    }

    private fun clientWith(r: Recorder) = InstanceLinkClient(
        onStatusChanged = { },
        onScheduleUpdated = { r.schedules += it },
        onLiveStateUpdated = { r.liveStates += it },
        onDisplayCleared = { r.displayCleared++ },
        onSongSectionSelected = { r.songSections += it },
        onPresentationSlideChanged = { id, index, total, playing, live ->
            r.slides += "$id:$index:$total:$playing:$live"
        },
        onSongsUpdated = { },
        onMessageReceived = { r.messagesReceived++ },
        onBibleUpdated = { r.bibleUpdated++ },
        onPicturesUpdated = { r.picturesUpdated++ },
        onBackgroundsUpdated = { r.backgroundsUpdated++ },
    )

    private fun envelope(type: String, payload: String) =
        """{"type":"$type","payload":${quote(payload)}}"""

    private fun quote(s: String) = buildString {
        append('"')
        s.forEach { if (it == '"' || it == '\\') { append('\\'); append(it) } else append(it) }
        append('"')
    }

    // ── Messages that carry a payload ───────────────────────────────────────────

    @Test
    fun `a schedule update hands over the items it carried`() {
        val r = Recorder()
        clientWith(r).handleMessage(
            envelope(Constants.WS_EVENT_SCHEDULE_UPDATED, """{"items":[],"total":0}"""),
        )

        assertEquals(1, r.schedules.size, "the follower must be told the schedule changed")
    }

    @Test
    fun `a live state change hands over the state`() {
        val r = Recorder()
        clientWith(r).handleMessage(
            envelope(Constants.WS_EVENT_LIVE_STATE_CHANGED, """{"contentType":"BIBLE"}"""),
        )

        assertEquals(1, r.liveStates.size)
        assertEquals("BIBLE", r.liveStates[0].contentType)
    }

    @Test
    fun `a song section selection is read as an index`() {
        val r = Recorder()
        clientWith(r).handleMessage(envelope(Constants.WS_EVENT_SONG_SECTION_SELECTED, "3"))

        assertEquals(listOf(3), r.songSections)
    }

    @Test
    fun `a presentation slide change carries id, position and playback state`() {
        val r = Recorder()
        clientWith(r).handleMessage(
            envelope(
                Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED,
                """{"id":"deck-1","index":4,"total":20,"isPlaying":true,"isLive":true}""",
            ),
        )

        assertEquals(listOf("deck-1:4:20:true:true"), r.slides)
    }

    @Test
    fun `a slide change without playback flags defaults them to false`() {
        val r = Recorder()
        clientWith(r).handleMessage(
            envelope(Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED, """{"id":"d","index":0,"total":3}"""),
        )

        // An older primary omits these; assuming "playing" would start a follower's auto-advance
        // against a deck the primary is holding still.
        assertEquals(listOf("d:0:3:false:false"), r.slides)
    }

    // ── Messages that are pure signals ──────────────────────────────────────────

    @Test
    fun `the signal-only messages each reach their own callback`() {
        val r = Recorder()
        val c = clientWith(r)
        c.handleMessage(envelope(Constants.WS_EVENT_DISPLAY_CLEARED, ""))
        c.handleMessage(envelope(Constants.WS_EVENT_BIBLE_UPDATED, ""))
        c.handleMessage(envelope(Constants.WS_EVENT_PICTURES_UPDATED, ""))
        c.handleMessage(envelope(Constants.WS_EVENT_BACKGROUNDS_UPDATED, ""))

        // Each is a cache-invalidation or blanking signal; crossing them would re-download the wrong
        // thing or blank a screen that should still be showing content.
        assertEquals(1, r.displayCleared)
        assertEquals(1, r.bibleUpdated)
        assertEquals(1, r.picturesUpdated)
        assertEquals(1, r.backgroundsUpdated)
    }

    // ── Malformed and unknown input ─────────────────────────────────────────────

    @Test
    fun `a message that is not valid JSON is dropped`() {
        val r = Recorder()
        clientWith(r).handleMessage("this is not json")

        assertTrue(r.schedules.isEmpty() && r.liveStates.isEmpty())
        assertEquals(0, r.messagesReceived, "an undecodable envelope is not a sign of life")
    }

    @Test
    fun `an unknown message type is ignored but still counts as liveness`() {
        val r = Recorder()
        clientWith(r).handleMessage(envelope("something_from_a_newer_version", "{}"))

        assertEquals(0, r.displayCleared)
        // It arrived and decoded, so the link is demonstrably alive even though this build cannot
        // act on it. Not counting it would show a working link as frozen.
        assertEquals(1, r.messagesReceived)
    }

    @Test
    fun `a schedule update whose payload will not decode changes nothing`() {
        val r = Recorder()
        clientWith(r).handleMessage(envelope(Constants.WS_EVENT_SCHEDULE_UPDATED, "not-a-schedule"))

        // Better to keep showing the previous schedule than to replace it with nothing.
        assertTrue(r.schedules.isEmpty())
        assertEquals(1, r.messagesReceived, "the envelope still decoded")
    }

    @Test
    fun `a live state whose payload will not decode leaves the output alone`() {
        val r = Recorder()
        clientWith(r).handleMessage(envelope(Constants.WS_EVENT_LIVE_STATE_CHANGED, "{"))

        assertTrue(r.liveStates.isEmpty())
    }

    @Test
    fun `a song section that is not a number is ignored`() {
        val r = Recorder()
        clientWith(r).handleMessage(envelope(Constants.WS_EVENT_SONG_SECTION_SELECTED, "verse two"))

        assertTrue(r.songSections.isEmpty())
    }

    @Test
    fun `a slide change missing its id or position is ignored`() {
        val r = Recorder()
        val c = clientWith(r)
        c.handleMessage(envelope(Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED, """{"index":1,"total":2}"""))
        c.handleMessage(envelope(Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED, """{"id":"d","total":2}"""))
        c.handleMessage(envelope(Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED, """{"id":"d","index":1}"""))

        // All three fields are required to point at a slide; guessing any of them shows the wrong one.
        assertTrue(r.slides.isEmpty())
        assertEquals(3, r.messagesReceived, "they decoded, they were just unusable")
    }

    // ── Connection failure classification ───────────────────────────────────────

    @Test
    fun `each connection failure is classified for the operator`() {
        val c = clientWith(Recorder())

        // The follower shows this to whoever is setting the link up, and each one points at a
        // different fix: the port, the hostname, the network, or the certificate.
        assertEquals("refused", c.classifyConnectFailure(ConnectException("no")))
        assertEquals("dns", c.classifyConnectFailure(UnknownHostException("no")))
        assertEquals("timeout", c.classifyConnectFailure(SocketTimeoutException("no")))
        // Ktor's connect timeout is a subclass of ConnectException, and was filed as "refused"
        // until the when-branches were ordered the other way round.
        assertEquals("timeout", c.classifyConnectFailure(ConnectTimeoutException("no", null)))
        assertEquals("tls", c.classifyConnectFailure(SSLException("no")))
    }

    @Test
    fun `an unrecognised failure is classified rather than dropped`() {
        val c = clientWith(Recorder())

        assertEquals("other", c.classifyConnectFailure(IllegalStateException("something else")))
    }

    @Test
    fun `the peer address is taken out of a reported failure, and the reason kept`() {
        val c = clientWith(Recorder())

        // Ktor writes the whole target into the message. The host is what identifies somebody's
        // network; the rest is the only thing that says why an "other" failure happened.
        val redacted = c.redactHost(
            "Connect timeout has expired [url=ws://192.168.8.101:8765/ws, connect_timeout=5000 ms]",
            "192.168.8.101"
        )

        assertEquals(
            "Connect timeout has expired [url=ws://<peer>:8765/ws, connect_timeout=5000 ms]",
            redacted
        )
    }

    @Test
    fun `redaction handles a hostname, a missing message and an unset host`() {
        val c = clientWith(Recorder())

        // A hostname is replaced the same way an address is — it is the literal string Ktor built
        // the URL from, whatever shape it has.
        assertEquals("no route to <peer>", c.redactHost("no route to primary.local", "primary.local"))
        // An exception with no message must not become the string "null" in the report.
        assertEquals("", c.redactHost(null, "192.168.8.101"))
        // An empty host would otherwise match everywhere and shred the sentence.
        assertEquals("Connection refused", c.redactHost("Connection refused", ""))
    }
}
