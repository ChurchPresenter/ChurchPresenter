package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A page drawn on a phone's canvas, put on the outputs — `POST /api/drawing/frame` and `/end`.
 *
 * Going live asks the operator as a project request does, and that is the rule most worth pinning:
 * a page that is not a Go Live never takes the screen. The app's part (the prompt, the outputs) is
 * stood in for here by answering [CompanionServer.onDrawingRequest] directly, doing what the app
 * does on Allow: [DrawingDesk.approve], then the decision.
 */
class CompanionServerDrawingTest {

    private lateinit var client: HttpClient
    private lateinit var listenerScope: CoroutineScope
    private val requests = mutableListOf<PendingDrawingRequest>()
    private val ended = mutableListOf<String>()

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            TestSingletons.latchToTestHome()
            server = CompanionServer(shutdownGraceMs = 0)
            server.start(port = testPort(39_985))
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

        /** A 1x1 PNG — the smallest thing that is really a page. */
        private val PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwACdgFOA9d5uAAAAABJRU5ErkJggg==",
        )

        /** The same page with one byte changed past the signature, so two pages can be told apart. */
        private val OTHER_PNG: ByteArray = PNG.copyOf().also { it[PNG.size - 1] = 0x01 }
    }

    @BeforeTest
    fun setUp() {
        client = HttpClient(CIO)
        listenerScope = CoroutineScope(Dispatchers.IO + Job())
        listenerScope.launch { server.onDrawingRequest.collect { synchronized(requests) { requests += it } } }
        listenerScope.launch { server.onDrawingEnd.collect { synchronized(ended) { ended += it } } }
        server.devicePermissions = emptyMap()
        server.updateApiKey(enabled = false, key = "")
        // Each test starts with nobody's drawing live.
        server.drawings.slideChanged("NONE")
        server.drawingFrames.value = null
    }

    @AfterTest
    fun tearDown() {
        listenerScope.cancel()
        client.close()
    }

    private suspend fun frame(
        device: String,
        takeOver: Boolean,
        body: ByteArray = PNG,
        query: String = "",
    ): HttpResponse = client.post(
        "http://127.0.0.1:$port${Constants.ENDPOINT_DRAWING_FRAME}?takeOver=$takeOver$query",
    ) {
        header(Constants.HEADER_DEVICE_ID, device)
        contentType(ContentType.Image.PNG)
        setBody(body)
    }

    private suspend fun end(device: String): HttpResponse =
        client.post("http://127.0.0.1:$port${Constants.ENDPOINT_DRAWING_END}") {
            header(Constants.HEADER_DEVICE_ID, device)
        }

    /** Starts a Go Live from [device] and waits until it has reached the operator. */
    private suspend fun CoroutineScope.goLive(
        device: String,
        query: String = "",
    ): Pair<Deferred<HttpResponse>, PendingDrawingRequest> {
        val before = synchronized(requests) { requests.size }
        val response = async { frame(device, takeOver = true, query = query) }
        val request = withTimeout(5_000) {
            while (synchronized(requests) { requests.size } == before) kotlinx.coroutines.delay(5)
            synchronized(requests) { requests.last() }
        }
        return response to request
    }

    /** What the app does when the operator presses Allow. */
    private fun allow(request: PendingDrawingRequest): DrawingFrame? =
        server.drawings.approve(request).also { request.decision.complete(it != null) }

    @Test
    fun `go live waits for the operator and shows when allowed`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-1", query = "&title=Sermon&paper=%231B1D26")
        assertFalse(response.isCompleted, "the device is answered only once the operator has")
        assertEquals("tablet-1", request.clientId)
        assertEquals("Sermon", request.title)

        val shown = allow(request)

        assertEquals("#1B1D26", shown?.paper)
        val answer = response.await()
        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals("""{"ok":true,"showing":true}""", answer.bodyAsText())
        assertTrue(server.drawings.isLive("tablet-1"))
    }

    @Test
    fun `a denied go live is answered 403 as a project request is`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-2")

        request.decision.complete(false)

        val answer = response.await()
        assertEquals(HttpStatusCode.Forbidden, answer.status)
        assertEquals("""{"ok":false,"reason":"denied","showing":false}""", answer.bodyAsText())
        assertFalse(server.drawings.isLive("tablet-2"))
    }

    @Test
    fun `pages sent while waiting are held, and the latest is what goes live`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-3")

        val meanwhile = frame("tablet-3", takeOver = false, body = OTHER_PNG)

        assertEquals("""{"ok":true,"showing":false}""", meanwhile.bodyAsText())
        assertNull(server.drawingFrames.value, "nothing reaches the outputs before the operator says yes")
        assertContentEquals(OTHER_PNG, allow(request)?.png)
        response.await()
    }

    @Test
    fun `a page while the drawing is live replaces it without asking`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-4")
        allow(request)
        response.await()
        val asked = synchronized(requests) { requests.size }

        val page = frame("tablet-4", takeOver = false, body = OTHER_PNG)

        assertEquals("""{"ok":true,"showing":true}""", page.bodyAsText())
        assertEquals(asked, synchronized(requests) { requests.size }, "a live drawing's pages ask nobody")
        assertContentEquals(OTHER_PNG, server.drawingFrames.value?.png)
    }

    @Test
    fun `once anything else is live, a page is dropped and the next go live asks again`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-5")
        allow(request)
        response.await()

        server.drawings.slideChanged("LYRICS")
        val page = frame("tablet-5", takeOver = false)

        assertEquals("""{"ok":true,"showing":false}""", page.bodyAsText())
        assertNull(server.drawingFrames.value)
        val (again, second) = goLive("tablet-5")
        assertTrue(second !== request, "a new Go Live is a new request to the operator")
        second.decision.complete(false)
        again.await()
    }

    @Test
    fun `a page from a device that never went live shows nothing`() = runBlocking<Unit> {
        val page = frame("stranger", takeOver = false)

        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals("""{"ok":true,"showing":false}""", page.bodyAsText())
        assertNull(server.drawingFrames.value)
    }

    @Test
    fun `end takes a live drawing down`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-6")
        allow(request)
        response.await()

        val answer = end("tablet-6")

        assertEquals("""{"ok":true}""", answer.bodyAsText())
        assertFalse(server.drawings.isLive("tablet-6"))
        withTimeout(5_000) { while (synchronized(ended) { "tablet-6" !in ended }) kotlinx.coroutines.delay(5) }
    }

    @Test
    fun `end withdraws a go live still waiting, which is answered as denied`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-7")

        end("tablet-7")

        assertEquals(HttpStatusCode.Forbidden, response.await().status)
        assertTrue(request.decision.isCompleted)
        assertNull(server.drawings.approve(request), "an Allow after the device ended puts nothing up")
    }

    @Test
    fun `end with nothing live or waiting is a harmless ok`() = runBlocking<Unit> {
        val answer = end("idle")

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals("""{"ok":true}""", answer.bodyAsText())
        assertFalse(synchronized(ended) { "idle" in ended })
    }

    @Test
    fun `a second go live from the same device replaces the first, which is denied`() = runBlocking<Unit> {
        val (first, firstRequest) = goLive("tablet-8")
        val (second, secondRequest) = goLive("tablet-8")

        assertEquals(HttpStatusCode.Forbidden, first.await().status)
        assertTrue(firstRequest.decision.isCompleted)
        assertContentEquals(PNG, allow(secondRequest)?.png)
        assertEquals(HttpStatusCode.OK, second.await().status)
    }

    @Test
    fun `a body that is not a PNG is refused`() = runBlocking<Unit> {
        val answer = frame("tablet-9", takeOver = true, body = "not a picture".toByteArray())

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertEquals("""{"ok":false,"reason":"bad_image"}""", answer.bodyAsText())
    }

    @Test
    fun `an empty body is refused`() = runBlocking<Unit> {
        val answer = frame("tablet-9", takeOver = false, body = ByteArray(0))

        assertEquals(HttpStatusCode.BadRequest, answer.status)
    }

    @Test
    fun `a page over the cap is refused before anything is asked`() = runBlocking<Unit> {
        val huge = PNG + ByteArray(DRAWING_MAX_BYTES)

        val answer = frame("tablet-10", takeOver = true, body = huge)

        assertEquals(HttpStatusCode.PayloadTooLarge, answer.status)
        assertEquals("""{"ok":false,"reason":"too_large"}""", answer.bodyAsText())
    }

    @Test
    fun `a device without the go live permission is refused`() = runBlocking<Unit> {
        server.devicePermissions = mapOf("tablet-11" to DevicePermissions.ALL.with(DevicePermission.GO_LIVE, false))

        assertEquals(HttpStatusCode.Forbidden, frame("tablet-11", takeOver = true).status)
        assertEquals(HttpStatusCode.Forbidden, end("tablet-11").status)
    }

    @Test
    fun `a wrong api key is refused`() = runBlocking<Unit> {
        server.updateApiKey(enabled = true, key = "secret")

        assertEquals(HttpStatusCode.Unauthorized, frame("tablet-12", takeOver = true).status)
    }

    @Test
    fun `the live slide leaving drawing ends it, and a live state with a drawing keeps it`() = runBlocking<Unit> {
        val (response, request) = goLive("tablet-13")
        allow(request)
        response.await()

        server.updateLiveState(LiveContent(mode = "PICTURES", liveSlide = "DRAWING"))
        assertTrue(server.drawings.isLive("tablet-13"), "a picture staged under a live drawing does not end it")

        server.updateLiveState(LiveContent(mode = "LYRICS", liveSlide = "LYRICS"))
        assertFalse(server.drawings.isLive("tablet-13"))
    }

    @Test
    fun `isPng reads the signature only`() {
        assertTrue(isPng(PNG))
        assertFalse(isPng(ByteArray(0)))
        assertFalse(isPng(PNG.copyOf(8)), "a signature with nothing after it is not a page")
        assertFalse(isPng("GIF89a....".toByteArray()))
    }
}
