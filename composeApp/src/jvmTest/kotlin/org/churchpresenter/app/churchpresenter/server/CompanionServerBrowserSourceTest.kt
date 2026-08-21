package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.BrowserSourceFrame
import org.churchpresenter.settings.utils.Constants
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * `/browser-source/{index}` — the OBS overlay page and its guards. All configuration ([updateBrowserSourceOutputs]),
 * no OBS/Chromium/ATEM needed: the route only needs a [ScreenAssignment] to exist at that index.
 *
 * The WebSocket delta stream and its binary frame encoding are separate concerns: no fake browser
 * renders anything here, so only [BrowserSourceHub.encodeBrowserSourceFrameMessage]'s own header
 * packing (widened to `internal`) is pinned directly, byte for byte.
 */
class CompanionServerBrowserSourceTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeTest
    fun setUp() {
        server = CompanionServer()
        server.start(port = testPort(39_810))
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
    fun tearDown() {
        runCatching { client.close() }
        runCatching { server.stop() }
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"
    private fun get(path: String, apiKey: String? = null): HttpResponse = runBlocking {
        client.get(url(path)) { apiKey?.let { header(Constants.HEADER_API_KEY, it) } }
    }

    // ── GET /browser-source/{index} ───────────────────────────────────────────

    @Test
    fun `an unconfigured output index is a 404`() {
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_BROWSER_SOURCE}/1").status)
    }

    @Test
    fun `a non-numeric index is a 404, not a 500`() {
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_BROWSER_SOURCE}/not-a-number").status)
    }

    @Test
    fun `a configured, enabled output serves its overlay page with a no-store header`() {
        server.updateBrowserSourceOutputs(listOf(ScreenAssignment(browserSourceEnabled = true)))
        val response = get("${Constants.ENDPOINT_BROWSER_SOURCE}/1")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(runBlocking { response.bodyAsText() }.contains("<html", ignoreCase = true))
    }

    @Test
    fun `a disabled output is a 404`() {
        server.updateBrowserSourceOutputs(listOf(ScreenAssignment(browserSourceEnabled = false)))
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_BROWSER_SOURCE}/1").status)
    }

    @Test
    fun `the path index is 1-based -- index 1 is the first configured output`() {
        server.updateBrowserSourceOutputs(listOf(
            ScreenAssignment(browserSourceEnabled = true),
            ScreenAssignment(browserSourceEnabled = false),
        ))
        assertEquals(HttpStatusCode.OK, get("${Constants.ENDPOINT_BROWSER_SOURCE}/1").status)
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_BROWSER_SOURCE}/2").status)
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_BROWSER_SOURCE}/3").status)
    }

    @Test
    fun `an api key requirement on the output is enforced independently of the global api key`() {
        server.updateApiKey(enabled = false, key = "secret123")
        server.updateBrowserSourceOutputs(listOf(
            ScreenAssignment(browserSourceEnabled = true, browserSourceApiKeyRequired = true),
        ))

        assertEquals(HttpStatusCode.Unauthorized, get("${Constants.ENDPOINT_BROWSER_SOURCE}/1").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            get("${Constants.ENDPOINT_BROWSER_SOURCE}/1", apiKey = "wrong").status,
        )
        assertEquals(HttpStatusCode.OK, get("${Constants.ENDPOINT_BROWSER_SOURCE}/1", apiKey = "secret123").status)
    }

    @Test
    fun `no api key is required when the output does not ask for one, even with a global key set`() {
        server.updateApiKey(enabled = true, key = "secret123")
        server.updateBrowserSourceOutputs(listOf(
            ScreenAssignment(browserSourceEnabled = true, browserSourceApiKeyRequired = false),
        ))
        assertEquals(HttpStatusCode.OK, get("${Constants.ENDPOINT_BROWSER_SOURCE}/1").status)
    }

    // ── encodeBrowserSourceFrameMessage ────────────────────────────────────────

    @Test
    fun `encodeBrowserSourceFrameMessage packs a 24-byte big-endian header followed by the raw PNG bytes`() {
        val frame = BrowserSourceFrame(
            x = 10,
            y = 20,
            rectWidth = 30,
            rectHeight = 40,
            fullWidth = 1920,
            fullHeight = 1080,
            png = byteArrayOf(1, 2, 3),
        )
        val encoded = server.browserSource.encodeBrowserSourceFrameMessage(frame)

        assertEquals(24 + 3, encoded.size)
        val buf = ByteBuffer.wrap(encoded)
        assertEquals(10, buf.int)
        assertEquals(20, buf.int)
        assertEquals(30, buf.int)
        assertEquals(40, buf.int)
        assertEquals(1920, buf.int)
        assertEquals(1080, buf.int)
        val remaining = ByteArray(3)
        buf.get(remaining)
        assertEquals(listOf<Byte>(1, 2, 3), remaining.toList())
    }

    @Test
    fun `encodeBrowserSourceFrameMessage with an empty PNG payload is just the header`() {
        val frame = BrowserSourceFrame(0, 0, 0, 0, 0, 0, png = ByteArray(0))
        assertEquals(24, server.browserSource.encodeBrowserSourceFrameMessage(frame).size)
    }

    // ── WebSocket delta stream ───────────────────────────────────────────────

    private fun wsPath(index: Int) = "/api${Constants.ENDPOINT_BROWSER_SOURCE}/$index/ws"

    private fun connectAndGetCloseReason(index: Int): CloseReason? = runBlocking {
        var reason: CloseReason? = null
        withTimeoutOrNull(5_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${wsPath(index)}") {
                reason = closeReason.await()
            }
        }
        reason
    }

    @Test
    fun `connecting to an unconfigured output index closes with cannot-accept`() {
        val reason = connectAndGetCloseReason(1)
        assertEquals(CloseReason.Codes.CANNOT_ACCEPT.code, reason?.code)
    }

    @Test
    fun `connecting to a disabled output closes with cannot-accept`() {
        server.updateBrowserSourceOutputs(listOf(ScreenAssignment(browserSourceEnabled = false)))
        val reason = connectAndGetCloseReason(1)
        assertEquals(CloseReason.Codes.CANNOT_ACCEPT.code, reason?.code)
    }

    @Test
    fun `connecting with a required api key missing closes with violated-policy`() {
        server.updateApiKey(enabled = false, key = "secret123")
        server.updateBrowserSourceOutputs(listOf(ScreenAssignment(
            browserSourceEnabled = true,
            browserSourceApiKeyRequired = true,
        )))
        val reason = connectAndGetCloseReason(1)
        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
    }

    @Test
    fun `connecting before a renderer has registered its frame flow closes with try-again-later`() {
        server.updateBrowserSourceOutputs(listOf(ScreenAssignment(browserSourceEnabled = true)))
        val reason = connectAndGetCloseReason(1)
        assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, reason?.code)
    }

    @Test
    fun `a registered renderer's frames are streamed to the client, encoded`() = runBlocking {
        server.updateBrowserSourceOutputs(listOf(ScreenAssignment(browserSourceEnabled = true)))
        val frames = MutableSharedFlow<BrowserSourceFrame>(extraBufferCapacity = 4)
        server.registerBrowserSourceFrames(0, frames)
        val frame = BrowserSourceFrame(
            x = 1,
            y = 2,
            rectWidth = 3,
            rectHeight = 4,
            fullWidth = 5,
            fullHeight = 6,
            png = byteArrayOf(9, 8, 7),
        )
        val expected = server.browserSource.encodeBrowserSourceFrameMessage(frame)

        var received: Frame? = null
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${wsPath(1)}") {
                frames.subscriptionCount.first { it > 0 }
                frames.emit(frame)
                received = incoming.receive()
            }
        }

        assertNotNull(received, "expected a binary frame")
        val bytes = (received as Frame.Binary).data
        assertEquals(expected.toList(), bytes.toList())
    }

    @Test
    fun `replacing a renderer's frame flow closes any session still connected to the old one`() = runBlocking {
        server.updateBrowserSourceOutputs(listOf(ScreenAssignment(browserSourceEnabled = true)))
        val oldFlow = MutableSharedFlow<BrowserSourceFrame>(extraBufferCapacity = 4)
        server.registerBrowserSourceFrames(0, oldFlow)

        var reason: CloseReason? = null
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${wsPath(1)}") {
                oldFlow.subscriptionCount.first { it > 0 }
                val newFlow = MutableSharedFlow<BrowserSourceFrame>(extraBufferCapacity = 4)
                server.registerBrowserSourceFrames(0, newFlow)
                reason = closeReason.await()
            }
        }

        assertEquals(CloseReason.Codes.SERVICE_RESTART.code, reason?.code)
    }
}
