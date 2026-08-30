package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.network.sockets.ConnectTimeoutException
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.utils.Constants
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        TestSingletons.latchToTestHome()
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
        assertEquals("tls", c.classifyConnectFailure(SSLException("no")))
    }

    @Test
    fun `a ping timeout is classified and rate-limited like a refused connection`() {
        val c = clientWith(Recorder())

        // ktor's own pinger raises this when the primary misses the keepalive window. That is what
        // the heartbeat exists to notice: the link drops, the backoff reconnects, the operator sees
        // the status change. Five churches filed it as a defect because "other" reports the first
        // time it ever happens.
        val kind = c.classifyConnectFailure(IOException("Ping timeout"))

        assertEquals("ping_timeout", kind)
        assertFalse(
            c.shouldReportConnectFailure(kind, consecutiveFailures = 1),
            "one dropped keepalive on a hall's wifi is not worth an issue",
        )
        assertTrue(
            c.shouldReportConnectFailure(kind, consecutiveFailures = 10),
            "a link that keeps dropping still has to surface",
        )
    }

    @Test
    fun `an IOException that is not a ping timeout is still reported the first time`() {
        val c = clientWith(Recorder())
        val kind = c.classifyConnectFailure(IOException("broken pipe"))

        assertEquals("other", kind)
        assertTrue(c.shouldReportConnectFailure(kind, consecutiveFailures = 1))
    }

    @Test
    fun `an unrecognised failure is classified rather than dropped`() {
        val c = clientWith(Recorder())

        assertEquals("other", c.classifyConnectFailure(IllegalStateException("something else")))
    }

    @Test
    fun `a ktor connect timeout is a timeout and not a refusal`() {
        val c = clientWith(Recorder())

        // ConnectTimeoutException extends java.net.ConnectException, so an `is ConnectException`
        // arm placed first swallows it and every timed-out connect is filed as "refused" — the
        // bucket that means the operator has not started the primary yet. The two point at
        // different fixes, so the order of the arms is the behaviour here.
        assertEquals(
            "timeout",
            c.classifyConnectFailure(
                ConnectTimeoutException("Connect timeout has expired [url=ws://host:8763/ws]")
            )
        )
    }

    @Test
    fun `the benign kinds stay quiet until a run of failures persists`() {
        val c = clientWith(Recorder())

        // A follower routinely starts before its primary; the first refusals are that ordering.
        for (kind in listOf("refused", "dns")) {
            assertFalse(c.shouldReportConnectFailure(kind, consecutiveFailures = 1), kind)
            assertFalse(c.shouldReportConnectFailure(kind, consecutiveFailures = 9), kind)
            assertTrue(c.shouldReportConnectFailure(kind, consecutiveFailures = 10), kind)
        }
    }

    @Test
    fun `a failure that suggests a regression reports the first time`() {
        val c = clientWith(Recorder())

        for (kind in listOf("timeout", "tls", "other")) {
            assertTrue(c.shouldReportConnectFailure(kind, consecutiveFailures = 1), kind)
        }
    }
}
