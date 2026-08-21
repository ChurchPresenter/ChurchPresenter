package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * `POST /api/presentation-remote/upload` — the speaker's own phone dropping a deck onto the
 * desktop from the presentation-remote page.
 *
 * It is a second, separate implementation of the same idea as `/api/presentations/upload`
 * (covered by [CompanionServerPresentationTest]): same base64 data-URI body, same
 * `~/.churchpresenter/device_presentations/` destination, but a different gate in front of it —
 * the presentation-remote enable flag and password rather than the API key and the
 * file-upload-enabled switch. The two having drifted apart is exactly what would go unnoticed,
 * so this drives the remote one end to end rather than assuming the other suite speaks for it.
 *
 * `user.home` is isolated for the class — the handler resolves it per request, so one temp home
 * covers every test — and the upload directory is emptied between tests, which the duplicate-name
 * and "no directory was created" assertions both depend on. The server starts once per class for
 * the usual reason: a start/stop pair costs about as much as everything else here put together.
 *
 * Not covered: the 200 MB `Content-Length` refusal, which needs a request that actually declares
 * that length — a smaller body under a forged header just stalls the client waiting to finish
 * sending.
 */
class CompanionServerPresentationRemoteUploadTest {

    private lateinit var client: HttpClient
    private var operatorScope: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val uploadDir: File
        get() = File(tempHome, ".churchpresenter/device_presentations")

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private var realHome: String? = null
        private lateinit var tempHome: File

        @JvmStatic
        @BeforeClass
        fun startServer() {
            TestSingletons.latchToTestHome()
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-presentation-remote-upload-home").toFile()
            System.setProperty("user.home", tempHome.absolutePath)

            server = CompanionServer()
            server.start(port = testPort(39_850))
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
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }

    @BeforeTest
    fun setUp() {
        uploadDir.deleteRecursively()
        client = HttpClient(CIO)
        server.presentationRemoteEnabled = true
        server.presentationRemotePassword = ""
    }

    @AfterTest
    fun tearDown() {
        runCatching { operatorScope?.cancel() }
        operatorScope = null
        runCatching { client.close() }
    }

    // ── Driving the server ──────────────────────────────────────────────────────

    private fun upload(body: String, password: String? = null, deviceId: String? = null): HttpResponse =
        runBlocking {
            client.post("http://127.0.0.1:$port/api/presentation-remote/upload") {
                password?.let { header(Constants.HEADER_PRESENTATION_PASSWORD, it) }
                deviceId?.let { header(Constants.HEADER_DEVICE_ID, it) }
                setBody(body)
            }
        }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }

    private fun dataUri(bytes: ByteArray, mime: String = "application/pdf") =
        "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

    private fun body(name: String, data: String) = """{"name":${jsonString(name)},"data":"$data"}"""

    private fun jsonString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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

    // ── The happy path ──────────────────────────────────────────────────────────

    @Test
    fun `an uploaded deck is saved under the isolated user_home and announced to the desktop`() {
        val uploaded = CopyOnWriteArrayList<File>()
        val actions = CopyOnWriteArrayList<CompanionServer.RemoteInstantAction>()
        collecting(server.onPresentationUploaded) { uploaded.add(it) }
        collecting(server.onInstantAction) { actions.add(it) }

        val bytes = "%PDF-1.4 sermon".toByteArray()
        val response = upload(body("sermon.pdf", dataUri(bytes)), deviceId = "speaker-phone")

        assertEquals(HttpStatusCode.OK, response.status)
        val answer = json.parseToJsonElement(response.text()).jsonObject
        assertEquals("sermon", answer["name"]?.jsonPrimitive?.content, "the response name omits the extension")

        awaitUntil("onPresentationUploaded") { uploaded.isNotEmpty() }
        val saved = uploaded.single()
        assertTrue(
            saved.absolutePath.startsWith(tempHome.absolutePath),
            "must be written under the isolated user.home, not the developer's real one"
        )
        assertEquals("sermon.pdf", saved.name)
        assertEquals(bytes.toList(), saved.readBytes().toList())

        awaitUntil("onInstantAction") { actions.isNotEmpty() }
        val action = actions.single()
        assertEquals("upload", action.actionType)
        assertEquals("sermon.pdf", action.title)
        assertEquals("speaker-phone", action.clientId, "the toast must name the phone that sent it")
    }

    @Test
    fun `every accepted deck format is written through`() {
        val uploaded = CopyOnWriteArrayList<File>()
        collecting(server.onPresentationUploaded) { uploaded.add(it) }

        listOf("deck.pptx", "old.ppt", "apple.key", "notes.pdf").forEach { name ->
            assertEquals(HttpStatusCode.OK, upload(body(name, dataUri(name.toByteArray()))).status, name)
        }

        awaitUntil("all four uploads") { uploaded.size == 4 }
        assertEquals(
            listOf("apple.key", "deck.pptx", "notes.pdf", "old.ppt"),
            uploaded.map { it.name }.sorted()
        )
    }

    @Test
    fun `a second upload of the same name keeps both files`() {
        val uploaded = CopyOnWriteArrayList<File>()
        collecting(server.onPresentationUploaded) { uploaded.add(it) }

        assertEquals(HttpStatusCode.OK, upload(body("sermon.pptx", dataUri("first".toByteArray()))).status)
        awaitUntil("the first upload") { uploaded.size == 1 }
        assertEquals(HttpStatusCode.OK, upload(body("sermon.pptx", dataUri("second".toByteArray()))).status)
        awaitUntil("the second upload") { uploaded.size == 2 }

        val (first, second) = uploaded
        assertEquals("sermon.pptx", first.name)
        assertNotEquals(first.name, second.name, "the second must not overwrite the first")
        assertTrue(second.name.startsWith("sermon_") && second.name.endsWith(".pptx"), "got ${second.name}")
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }

    @Test
    fun `a path is stripped from the name so an upload cannot escape the upload directory`() {
        val uploaded = CopyOnWriteArrayList<File>()
        collecting(server.onPresentationUploaded) { uploaded.add(it) }

        assertEquals(
            HttpStatusCode.OK,
            upload(body("../../../evil.pdf", dataUri("nope".toByteArray()))).status
        )

        awaitUntil("the upload") { uploaded.isNotEmpty() }
        val saved = uploaded.single()
        assertEquals("evil.pdf", saved.name)
        assertEquals(uploadDir.canonicalFile, saved.canonicalFile.parentFile)
    }

    // ── Refusals ────────────────────────────────────────────────────────────────

    @Test
    fun `uploading is refused while the presentation remote is disabled`() {
        server.presentationRemoteEnabled = false
        val response = upload(body("sermon.pdf", dataUri("x".toByteArray())))
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertFalse(uploadDir.exists(), "a refused upload must not create the upload directory")
    }

    @Test
    fun `uploading with the wrong password is unauthorized, and with the right one is accepted`() {
        server.presentationRemotePassword = "sermon"

        assertEquals(
            HttpStatusCode.Unauthorized,
            upload(body("a.pdf", dataUri("x".toByteArray())), password = "guess").status,
        )
        assertEquals(HttpStatusCode.OK, upload(body("a.pdf", dataUri("x".toByteArray())), password = "sermon").status)
    }

    @Test
    fun `a body with no name or no data is a 400`() {
        val data = dataUri("x".toByteArray())
        assertEquals(HttpStatusCode.BadRequest, upload("""{"data":"$data"}""").status)
        assertEquals(HttpStatusCode.BadRequest, upload("""{"name":"sermon.pdf"}""").status)
        assertEquals(HttpStatusCode.BadRequest, upload(body("   ", data)).status)
    }

    @Test
    fun `an unsupported file type is a 415 and names the extension`() {
        val response = upload(body("notes.txt", dataUri("hi".toByteArray(), mime = "text/plain")))
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(response.text().contains("txt"), "the error should name the rejected extension: ${response.text()}")
    }

    @Test
    fun `a plain base64 payload with no data URI prefix is a 400`() {
        val response = upload(body("sermon.pdf", Base64.getEncoder().encodeToString("x".toByteArray())))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `an undecodable payload fails as a 500 rather than escaping the handler`() {
        val response = upload(body("sermon.pdf", "data:application/pdf;base64,not valid base64!!"))
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.text().contains("upload failed"), response.text())
    }

    @Test
    fun `a body that is not JSON at all is reported rather than escaping the handler`() {
        assertEquals(HttpStatusCode.InternalServerError, upload("this is not json").status)
    }
}
