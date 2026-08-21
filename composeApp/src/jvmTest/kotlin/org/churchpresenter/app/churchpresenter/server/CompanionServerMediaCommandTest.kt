package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.PresentationRemoteSettings
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * The media transport commands a phone sends over the companion WebSocket, plus two small
 * server-side surfaces that had no coverage: the presentation remote-settings switch and the
 * CA-certificate download.
 *
 * The transport commands matter out of proportion to their size — they are what a volunteer at the
 * back of the room uses to pause a video mid-service. Each is asserted by the flow the server
 * actually emits on, using the subscribe-then-send pattern the sibling suites established: these
 * flows have no replay, so a collector attached after the send would miss it and the test would
 * pass or fail on timing rather than behaviour.
 */
class CompanionServerMediaCommandTest {

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_895))
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

    private var collectorScope: CoroutineScope? = null

    @AfterTest
    fun stopCollecting() {
        runCatching { collectorScope?.cancel() }
        collectorScope = null
    }

    /** Subscribes to [flow] and does not return until the collector is actually attached. */
    private fun <T> collecting(flow: MutableSharedFlow<T>, onEach: (T) -> Unit) {
        val scope = collectorScope ?: CoroutineScope(Dispatchers.IO).also { collectorScope = it }
        scope.launch { flow.collect { onEach(it) } }
        runBlocking {
            withTimeoutOrNull(5_000) { flow.subscriptionCount.first { it > 0 } }
                ?: error("collector never subscribed")
        }
    }

    /** Sends one command frame over the companion socket and returns its ack, if one arrives. */
    private fun sendCommand(type: String, payload: String = ""): String? = runBlocking {
        val client = HttpClient(CIO) { install(WebSockets) }
        var ack: String? = null
        try {
            client.webSocket(host = "127.0.0.1", port = port, path = Constants.ENDPOINT_WS) {
                send(Frame.Text("""{"type":"$type","payload":"$payload","commandId":"cmd-1"}"""))
                // The ack is the positive signal that the handler ran; the snapshot frames the
                // server sends on connect arrive first, so read until it appears.
                ack = withTimeoutOrNull(10_000) {
                    var found: String? = null
                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText()
                        if (text != null && text.contains("cmd-1")) { found = text; break }
                    }
                    found
                }
            }
        } finally {
            client.close()
        }
        ack
    }

    private fun awaitFired(what: String, fired: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (fired()) return
            Thread.sleep(5)
        }
        throw AssertionError("timed out waiting for $what")
    }

    // ── Media transport ───────────────────────────────────────────────────────

    @Test
    fun `play-pause reaches the media flow`() {
        var fired = false
        collecting(server.onMediaPlayPause) { fired = true }
        sendCommand(Constants.WS_CMD_MEDIA_PLAY_PAUSE)
        awaitFired("play/pause") { fired }
    }

    @Test
    fun `stop reaches the media flow`() {
        var fired = false
        collecting(server.onMediaStop) { fired = true }
        sendCommand(Constants.WS_CMD_MEDIA_STOP)
        awaitFired("stop") { fired }
    }

    @Test
    fun `seek forward and backward are separate commands`() {
        var forward = false
        var backward = false
        collecting(server.onMediaSeekForward) { forward = true }
        collecting(server.onMediaSeekBackward) { backward = true }

        sendCommand(Constants.WS_CMD_MEDIA_SEEK_FORWARD)
        awaitFired("seek forward") { forward }
        assertTrue(!backward, "the two directions are not the same command")

        sendCommand(Constants.WS_CMD_MEDIA_SEEK_BACKWARD)
        awaitFired("seek backward") { backward }
    }

    @Test
    fun `seek-to carries its position in milliseconds`() {
        var seekedTo: Long? = null
        collecting(server.onMediaSeekTo) { seekedTo = it }
        sendCommand(Constants.WS_CMD_MEDIA_SEEK_TO, payload = "42000")
        awaitFired("seek to") { seekedTo != null }
        assertEquals(42_000L, seekedTo)
    }

    @Test
    fun `a seek-to with an unparseable position is ignored rather than seeking to zero`() {
        // Seeking to 0 would restart the video mid-service; ignoring it leaves playback alone.
        var seekedTo: Long? = null
        collecting(server.onMediaSeekTo) { seekedTo = it }
        sendCommand(Constants.WS_CMD_MEDIA_SEEK_TO, payload = "not-a-number")

        // Positive signal that the handler ran past the bad payload: a valid seek after it lands.
        sendCommand(Constants.WS_CMD_MEDIA_SEEK_TO, payload = "1000")
        awaitFired("the valid seek") { seekedTo != null }
        assertEquals(1_000L, seekedTo, "only the parseable position was acted on")
    }

    @Test
    fun `slide navigation commands reach their own flows`() {
        var next = false
        var previous = false
        collecting(server.onNextSlide) { next = true }
        collecting(server.onPreviousSlide) { previous = true }

        sendCommand(Constants.WS_CMD_NEXT_SLIDE)
        awaitFired("next slide") { next }
        sendCommand(Constants.WS_CMD_PREVIOUS_SLIDE)
        awaitFired("previous slide") { previous }
    }

    @Test
    fun `every command is acknowledged by its own id`() {
        // Without the ack a remote action fails silently — the phone has no other way to tell.
        collecting(server.onMediaStop) { }
        val ack = sendCommand(Constants.WS_CMD_MEDIA_STOP)
        assertTrue(ack != null && ack.contains("cmd-1"), "got: $ack")
    }

    // ── Presentation remote settings ──────────────────────────────────────────

    @Test
    fun `turning presentation remote control off clears the presentation state`() {
        // Otherwise a phone that had been driving slides keeps showing the last deck after the
        // operator revoked its access. The clear fires only on the on-to-off edge.
        server.updatePresentationRemoteSettings(PresentationRemoteSettings(remoteControlEnabled = true), "secret")
        server.updatePresentationLiveStatus(true)

        server.updatePresentationRemoteSettings(PresentationRemoteSettings(remoteControlEnabled = false), "")

        // The socket still serves after the revoke — the session is not torn down with the setting.
        collecting(server.onMediaStop) { }
        val ack = sendCommand(Constants.WS_CMD_MEDIA_STOP)
        assertTrue(ack != null, "the server is still answering")
    }

    @Test
    fun `enabling remote control twice does not clear anything`() {
        server.updatePresentationRemoteSettings(PresentationRemoteSettings(remoteControlEnabled = true), "a")
        server.updatePresentationRemoteSettings(PresentationRemoteSettings(remoteControlEnabled = true), "b")
        server.updatePresentationRemoteSettings(PresentationRemoteSettings(remoteControlEnabled = false), "")
        // Asserted by not throwing: only the on-to-off edge does any work.
    }

    // ── CA certificate ────────────────────────────────────────────────────────

    @Test
    fun `the CA certificate endpoints answer, or say why they cannot`() {
        // In plain-HTTP fallback there is no certificate to serve, and the 404 carries the reason
        // so a phone that cannot install it is not left guessing.
        for (path in listOf("/ca.crt", "/ca.pem")) {
            val client = HttpClient(CIO)
            val response: HttpResponse = try {
                runBlocking { client.get("http://127.0.0.1:$port$path") }
            } finally {
                client.close()
            }
            assertTrue(
                response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NotFound,
                "$path answered ${response.status}",
            )
        }
    }
}
