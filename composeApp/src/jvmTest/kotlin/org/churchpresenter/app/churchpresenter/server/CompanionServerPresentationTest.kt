package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * `/api/presentations*` — untouched by every other `CompanionServer*Test`. None of it needs
 * PowerPoint/Keynote parsing: [CompanionServer.updatePresentation] takes already-rendered slide
 * JPEG bytes as plain [File]s, so the fixture below is just bytes on disk, not a real deck.
 *
 * `user.home` is isolated because the upload endpoint writes into
 * `~/.churchpresenter/device_presentations/`.
 */
class CompanionServerPresentationTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private var operatorScope: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    private var realHome: String? = null
    private lateinit var tempHome: File

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-presentation-test-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        server = CompanionServer()
        server.start(port = testPort(39_800))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        client = HttpClient(CIO)
    }

    @AfterTest
    fun tearDown() {
        runCatching { operatorScope?.cancel() }
        operatorScope = null
        runCatching { client.close() }
        runCatching { server.stop() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"
    private fun get(path: String): HttpResponse = runBlocking { client.get(url(path)) }
    private fun post(
        path: String,
        body: String,
    ): HttpResponse = runBlocking { client.post(url(path)) { setBody(body) } }
    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }

    private fun <T> collecting(flow: MutableSharedFlow<T>, onEach: (T) -> Unit) {
        val scope = operatorScope ?: CoroutineScope(Dispatchers.IO).also { operatorScope = it }
        scope.launch { flow.collect { onEach(it) } }
        runBlocking {
            withTimeoutOrNull(5_000) { flow.subscriptionCount.first { it > 0 } }
                ?: error("collector never subscribed")
        }
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /** Loads a presentation with [slideCount] one-byte-per-slide "JPEG" slides and waits for the
     *  server's own async render pipeline to publish it (updatePresentation dispatches to its own
     *  coroutine, so this polls the REST endpoint rather than assuming it's synchronous). */
    private fun loadPresentation(id: String, slideCount: Int = 2): List<ByteArray> {
        val slideBytes = (0 until slideCount).map { byteArrayOf(0x10, it.toByte()) }
        val dir = Files.createTempDirectory("cp-presentation-slides").toFile()
        val slideFiles = slideBytes.mapIndexed { i, bytes -> File(dir, "slide$i.jpg").apply { writeBytes(bytes) } }
        server.updatePresentation(
            id = id,
            filePath = "",
            fileName = "Test.pptx",
            fileType = "pptx",
            slideFiles = slideFiles,
        )
        awaitUntil("presentation $id to be published") { get("/api/presentations/$id").status == HttpStatusCode.OK }
        return slideBytes
    }

    // ── GET /api/presentations ─────────────────────────────────────────────────

    @Test
    fun `the catalog is empty before any presentation is loaded`() {
        val response = get(Constants.ENDPOINT_PRESENTATIONS)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, json.parseToJsonElement(response.text()).jsonObject["total"]?.jsonPrimitive?.int)
    }

    // ── GET /api/presentations/{id} ────────────────────────────────────────────

    @Test
    fun `an unknown presentation id is a 404`() {
        assertEquals(HttpStatusCode.NotFound, get("/api/presentations/no-such-id").status)
    }

    @Test
    fun `a loaded presentation is served by id with its slide count`() {
        loadPresentation("pres-1", slideCount = 3)
        val body = json.parseToJsonElement(get("/api/presentations/pres-1").text()).jsonObject
        assertEquals("Test.pptx", body["file-name"]?.jsonPrimitive?.content)
    }

    // ── GET /api/presentations/{id}/slides/{index} ────────────────────────────

    @Test
    fun `a slide is served as the exact bytes it was loaded with`() {
        val slides = loadPresentation("pres-2")
        val response = get("/api/presentations/pres-2/slides/0")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(slides[0].toList(), runBlocking { response.readRawBytes() }.toList())
    }

    @Test
    fun `an out-of-range slide index is a 404`() {
        loadPresentation("pres-3", slideCount = 2)
        assertEquals(HttpStatusCode.NotFound, get("/api/presentations/pres-3/slides/5").status)
    }

    @Test
    fun `a negative slide index is a 404`() {
        loadPresentation("pres-4", slideCount = 2)
        assertEquals(HttpStatusCode.NotFound, get("/api/presentations/pres-4/slides/-1").status)
    }

    @Test
    fun `slides for an unknown presentation id are a 404`() {
        assertEquals(HttpStatusCode.NotFound, get("/api/presentations/no-such-id/slides/0").status)
    }

    @Test
    fun `an invalid (non-numeric) slide index is a 400`() {
        loadPresentation("pres-5", slideCount = 1)
        assertEquals(HttpStatusCode.BadRequest, get("/api/presentations/pres-5/slides/not-a-number").status)
    }

    // ── POST /api/presentations/{id}/select ───────────────────────────────────

    @Test
    fun `selecting a slide acks ok and notifies onSelectSlide`() {
        loadPresentation("pres-6", slideCount = 3)
        val selected = mutableListOf<SelectSlideRequest>()
        collecting(server.onSelectSlide) { selected.add(it) }

        val response = post("/api/presentations/pres-6/select", """{"index":1}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.parseToJsonElement(response.text()).jsonObject["ok"]?.jsonPrimitive?.content == "true")
        awaitUntil("onSelectSlide") { selected.isNotEmpty() }
        assertEquals(SelectSlideRequest(id = "pres-6", index = 1), selected.single())
    }

    @Test
    fun `selecting with a missing index is a 400`() {
        assertEquals(HttpStatusCode.BadRequest, post("/api/presentations/pres-7/select", "{}").status)
    }

    @Test
    fun `selecting with a negative index is a 400`() {
        assertEquals(HttpStatusCode.BadRequest, post("/api/presentations/pres-7/select", """{"index":-1}""").status)
    }

    // ── POST /api/presentations/upload ────────────────────────────────────────

    @Test
    fun `uploading a PDF saves it under the isolated user_home and notifies onPresentationUploaded`() {
        val uploaded = mutableListOf<File>()
        collecting(server.onPresentationUploaded) { uploaded.add(it) }

        val pdfBytes = "%PDF-1.4 fake".toByteArray()
        val dataUri = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdfBytes)
        val response = post(
            "${Constants.ENDPOINT_PRESENTATIONS}/upload",
            """{"name":"sermon.pdf","data":"$dataUri"}""",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.text()).jsonObject
        assertEquals("sermon", body["name"]?.jsonPrimitive?.content, "the response name omits the extension")

        awaitUntil("onPresentationUploaded") { uploaded.isNotEmpty() }
        val saved = uploaded.single()
        assertTrue(
            saved.absolutePath.startsWith(tempHome.absolutePath),
            "must be saved under the isolated user.home, not the real one",
        )
        assertEquals(pdfBytes.toList(), saved.readBytes().toList())
    }

    @Test
    fun `uploading an unsupported file type is rejected`() {
        val dataUri = "data:text/plain;base64," + Base64.getEncoder().encodeToString("hi".toByteArray())
        val response = post(
            "${Constants.ENDPOINT_PRESENTATIONS}/upload",
            """{"name":"notes.txt","data":"$dataUri"}""",
        )
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    @Test
    fun `uploading with no data URI prefix is a 400`() {
        val response = post(
            "${Constants.ENDPOINT_PRESENTATIONS}/upload",
            """{"name":"sermon.pdf","data":"not-a-data-uri"}""",
        )
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `uploading is refused while file upload is disabled`() {
        server.updateFileUploadEnabled(false)
        val dataUri = "data:application/pdf;base64," + Base64.getEncoder().encodeToString("x".toByteArray())
        val response = post(
            "${Constants.ENDPOINT_PRESENTATIONS}/upload",
            """{"name":"sermon.pdf","data":"$dataUri"}""",
        )
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
