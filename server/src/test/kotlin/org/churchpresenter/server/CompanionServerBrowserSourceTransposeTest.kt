package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The musicians' transpose buttons on a Browser Source page (issue #649): the handshake that asks
 * the desktop, the press route, and the state the page is sent back.
 *
 * The desktop's answer is played by a collector on [CompanionServer.onMusicianConnect] — the same
 * flow main.kt answers from — so the real approve/deny path runs with no dialog.
 */
class CompanionServerBrowserSourceTransposeTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private val answers = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var port: Int = 0

    @BeforeTest
    fun setUp() {
        server = CompanionServer(shutdownGraceMs = 0)
        server.start(port = testPort(39_815))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateBrowserSourceOutputs(listOf(
            ScreenAssignment(browserSourceEnabled = true),
            ScreenAssignment(browserSourceEnabled = true),
        ))
    }

    @AfterTest
    fun tearDown() {
        answers.cancel()
        runCatching { client.close() }
        runCatching { server.stop() }
    }

    private fun api(output: Int, action: String) =
        "http://127.0.0.1:$port/api${Constants.ENDPOINT_BROWSER_SOURCE}/$output/$action"

    private fun post(output: Int, action: String, device: String?, body: String = ""): HttpResponse =
        runBlocking {
            client.post(api(output, action)) {
                device?.let { header(Constants.HEADER_DEVICE_ID, it) }
                setBody(body)
            }
        }

    /** The desktop, answering every connection request with [approve]. */
    private fun desktopAnswers(approve: Boolean) = runBlocking {
        answers.launch {
            server.onMusicianConnect.collect { it.decision.complete(approve) }
        }
        server.onMusicianConnect.subscriptionCount.first { it > 0 }
    }

    /** Every press the routes accept, in order. */
    private fun pressesHeard(): MutableList<BrowserSourceTransposeCommand> {
        val heard = mutableListOf<BrowserSourceTransposeCommand>()
        runBlocking {
            answers.launch { server.onBrowserSourceTranspose.collect { synchronized(heard) { heard.add(it) } } }
            server.onBrowserSourceTranspose.subscriptionCount.first { it > 0 }
        }
        return heard
    }

    // ── Which outputs have buttons at all ────────────────────────────────────

    @Test
    fun `an output whose profile has no transpose buttons refuses the handshake`() {
        desktopAnswers(approve = true)
        server.updateTransposeControls(setOf(1))
        assertEquals(HttpStatusCode.Forbidden, post(1, "auth", "tablet").status)
    }

    @Test
    fun `an output whose profile has no transpose buttons refuses a press, even from an approved device`() {
        desktopAnswers(approve = true)
        server.updateTransposeControls(setOf(0, 1))
        assertEquals(HttpStatusCode.OK, post(1, "auth", "tablet").status)

        server.updateTransposeControls(setOf(1))
        assertEquals(HttpStatusCode.Forbidden, post(1, "transpose", "tablet", """{"delta":1}""").status)
    }

    @Test
    fun `an unknown output is a 404`() {
        assertEquals(HttpStatusCode.NotFound, post(9, "auth", "tablet").status)
    }

    // ── Approval ──────────────────────────────────────────────────────────────

    @Test
    fun `a device the desktop has not approved cannot press`() {
        server.updateTransposeControls(setOf(0))
        assertEquals(HttpStatusCode.Forbidden, post(1, "transpose", "tablet", """{"delta":1}""").status)
        assertEquals(HttpStatusCode.Forbidden, post(1, "transpose", null, """{"delta":1}""").status)
    }

    @Test
    fun `a device the desktop denies is refused, and still cannot press`() {
        desktopAnswers(approve = false)
        server.updateTransposeControls(setOf(0))
        assertEquals(HttpStatusCode.Forbidden, post(1, "auth", "tablet").status)
        assertEquals(HttpStatusCode.Forbidden, post(1, "transpose", "tablet", """{"delta":1}""").status)
    }

    @Test
    fun `an approved device's presses reach the app, for its own output`() {
        desktopAnswers(approve = true)
        val heard = pressesHeard()
        server.updateTransposeControls(setOf(1))
        assertEquals(HttpStatusCode.OK, post(2, "auth", "tablet").status)

        assertEquals(HttpStatusCode.OK, post(2, "transpose", "tablet", """{"delta":1}""").status)
        assertEquals(HttpStatusCode.OK, post(2, "transpose", "tablet", """{"delta":-1}""").status)
        assertEquals(HttpStatusCode.OK, post(2, "transpose", "tablet", """{"reset":true}""").status)

        runBlocking { withTimeout(5_000) { while (synchronized(heard) { heard.size } < 3) kotlinx.coroutines.yield() } }
        assertEquals(
            listOf(
                BrowserSourceTransposeCommand(1, delta = 1),
                BrowserSourceTransposeCommand(1, delta = -1),
                BrowserSourceTransposeCommand(1, reset = true),
            ),
            synchronized(heard) { heard.toList() },
        )
    }

    @Test
    fun `a press that says nothing it understands is a bad request`() {
        desktopAnswers(approve = true)
        server.updateTransposeControls(setOf(0))
        post(1, "auth", "tablet")
        assertEquals(HttpStatusCode.BadRequest, post(1, "transpose", "tablet", """{"delta":5}""").status)
        assertEquals(HttpStatusCode.BadRequest, post(1, "transpose", "tablet", "not json").status)
    }

    // ── The state sent back to the page ──────────────────────────────────────

    private fun wsUrl(output: Int) = "ws://127.0.0.1:$port/api${Constants.ENDPOINT_BROWSER_SOURCE}/$output/ws"

    @Test
    fun `a page whose profile offers the buttons is told the current transpose`() = runBlocking {
        val frames = MutableSharedFlow<BrowserSourceFrame>(extraBufferCapacity = 4)
        server.registerBrowserSourceFrames(0, frames)
        server.updateBrowserSourceTranspose(mapOf(0 to 2))
        server.updateTransposeControls(setOf(0))

        var state: String? = null
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = wsUrl(1)) {
                state = (incoming.receive() as Frame.Text).readText()
            }
        }
        assertEquals("""{"transpose":2,"controls":true}""", state)
    }

    @Test
    fun `a page is told when its buttons are taken away, so it can hide them`() = runBlocking {
        val frames = MutableSharedFlow<BrowserSourceFrame>(extraBufferCapacity = 4)
        server.registerBrowserSourceFrames(0, frames)
        server.updateTransposeControls(setOf(0))

        val states = mutableListOf<String>()
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = wsUrl(1)) {
                states += (incoming.receive() as Frame.Text).readText()
                server.updateTransposeControls(emptySet())
                states += (incoming.receive() as Frame.Text).readText()
            }
        }
        assertEquals(
            listOf("""{"transpose":0,"controls":true}""", """{"transpose":0,"controls":false}"""),
            states,
        )
    }

    @Test
    fun `a page whose profile never offers the buttons is only ever sent frames`() = runBlocking {
        val frames = MutableSharedFlow<BrowserSourceFrame>(extraBufferCapacity = 4)
        server.registerBrowserSourceFrames(0, frames)
        server.updateBrowserSourceTranspose(mapOf(0 to 3))
        server.updateTransposeControls(setOf(1))

        var first: Frame? = null
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = wsUrl(1)) {
                frames.subscriptionCount.first { it > 0 }
                // The transpose job starts beside the frame job; a frame emitted after both are
                // subscribed is the first thing a page with no buttons may receive.
                frames.emit(BrowserSourceFrame(0, 0, 1, 1, 1, 1, png = byteArrayOf(1)))
                first = incoming.receive()
            }
        }
        assertTrue(first is Frame.Binary, "expected the frame, got $first")
    }

    // ── Parsing a press ──────────────────────────────────────────────────────

    @Test
    fun `a press parses to a step or a reset, and nothing else`() {
        assertEquals(BrowserSourceTransposeCommand(3, delta = 1), parseTransposeCommand(3, """{"delta":1}"""))
        assertEquals(BrowserSourceTransposeCommand(3, delta = -1), parseTransposeCommand(3, """{"delta":-1}"""))
        assertEquals(BrowserSourceTransposeCommand(3, reset = true), parseTransposeCommand(3, """{"reset":true}"""))
        assertNull(parseTransposeCommand(3, """{"delta":2}"""))
        assertNull(parseTransposeCommand(3, """{"reset":false}"""))
        assertNull(parseTransposeCommand(3, """[1]"""))
        assertNull(parseTransposeCommand(3, ""))
    }
}
