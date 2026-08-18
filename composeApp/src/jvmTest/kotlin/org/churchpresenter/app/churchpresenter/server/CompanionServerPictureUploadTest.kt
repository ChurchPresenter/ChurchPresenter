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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * A photo taken on a phone and sent to the desktop, and picking one to show.
 *
 * The upload endpoint is the only way content *enters* the app from a device, so it is also the only
 * place a device can write to the filesystem. Three of its rules matter:
 *
 * - **The name is taken apart before it is used.** `File(name).name` strips any path a client sends,
 *   so `../../evil.jpg` cannot escape the upload folder. Asserted directly, because the failure mode
 *   is a file written wherever the sender liked.
 * - **A repeat name does not overwrite.** Two phones photographing the same thing both send
 *   `image.jpg`; the second is renamed rather than replacing the first.
 * - **The catalog is sorted by file name, not upload order**, because the desktop's own
 *   `PicturesViewModel` sorts that way — and index N has to mean the same photo on both sides or the
 *   operator picks a different picture than the phone showed.
 *
 * Selection is the other half: a phone may send an index or a file name, and the file name wins,
 * precisely so a sort-order mismatch cannot put the wrong photo on the screen.
 *
 * `user.home` is redirected for the class, since the endpoint writes into `~/.churchpresenter`.
 */
class CompanionServerPictureUploadTest {

    private lateinit var client: HttpClient
    private var listenerScope: CoroutineScope? = null

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private val json = Json { ignoreUnknownKeys = true }

        private var realHome: String? = null
        private lateinit var tempHome: File

        @JvmStatic
        @BeforeClass
        fun startServer() {
            TestSingletons.latchToTestHome()
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-picture-upload").toFile()
            System.setProperty("user.home", tempHome.absolutePath)
            server = CompanionServer()
            server.start(port = testPort(39_723))
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
            runCatching { tempHome.deleteRecursively() }
        }

        /** A 1x1 transparent PNG — the smallest thing that is really an image. */
        const val TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwACdgFOA9d5uAAAAABJRU5ErkJggg=="

        /** Today's upload folder, which the endpoint names after the calendar date. */
        private val uploadDir: File
            get() = File(
                System.getProperty("user.home"),
                ".churchpresenter/device_uploads/${LocalDate.now()}",
            )
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(CIO)
        server.updateFileUploadEnabled(true)
        uploadDir.deleteRecursively()
    }

    /**
     * A name prefix unique to each test.
     *
     * The server keeps today's catalog in memory for the life of the process, and nothing public
     * clears it — deleting the folder from disk does not. So indices accumulate across tests, and
     * every assertion here is about *relative* order within one test's own names rather than about
     * an absolute index.
     */
    private var nameSeq = 0
    private fun uniqueName(stem: String) = "test${nameSeq++}-$stem"

    @AfterTest
    fun closeClient() {
        runCatching { listenerScope?.cancel() }
        listenerScope = null
        runCatching { client.close() }
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    /** A four-pixel PNG as a data URI, which is what a phone actually posts. */
    private fun dataUri(): String {
        val png = Base64.getDecoder().decode(TINY_PNG_BASE64)
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
    }

    private fun upload(name: String, data: String = dataUri()): HttpResponse = runBlocking {
        client.post(url("${Constants.ENDPOINT_PICTURES}/upload")) {
            header(Constants.HEADER_DEVICE_ID, "phone-1")
            setBody("""{"name":"$name","data":"$data"}""")
        }
    }

    private fun selectPicture(body: String): HttpResponse = runBlocking {
        client.post(url("${Constants.ENDPOINT_PICTURES}/select")) {
            header(Constants.HEADER_DEVICE_ID, "phone-1")
            setBody(body)
        }
    }

    /** Collects what the desktop was asked to show, so a test asserts the choice not the status. */
    private fun listenForSelections(): MutableList<SelectPictureRequest> {
        val seen = mutableListOf<SelectPictureRequest>()
        listenerScope = CoroutineScope(Dispatchers.IO + Job()).also { scope ->
            scope.launch { server.onSelectPicture.collect { seen.add(it) } }
        }
        return seen
    }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.obj(): JsonObject = json.parseToJsonElement(text()).jsonObject
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.content.toInt()

    private fun awaitUntil(what: String, timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val ok = runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (!condition()) kotlinx.coroutines.delay(10)
                true
            }
        }
        if (ok != true) throw AssertionError("timed out waiting for $what")
    }

    // ── Uploading ───────────────────────────────────────────────────────────────

    @Test
    fun `an uploaded photo is written and catalogued`() {
        val name = uniqueName("sunday.png")

        val response = upload(name)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.obj()
        assertEquals(name, body.str("file-name"))
        assertTrue(body.int("image-index") >= 0, "it has to have a place in the catalog")
        assertTrue(body.str("folder-id").isNotBlank())
        assertTrue(File(uploadDir, name).exists(), "the bytes have to land on disk")
    }

    @Test
    fun `the folder is named after today`() {
        val folderId = upload(uniqueName("dated.png")).obj().str("folder-id")

        assertTrue(
            folderId.endsWith(LocalDate.now().toString()),
            "uploads from different days are catalogued separately: $folderId",
        )
    }

    @Test
    fun `a path in the name cannot escape the upload folder`() {
        val stem = uniqueName("escaped.png")

        val response = upload("../../$stem")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(stem, response.obj().str("file-name"), "the path has to be stripped")
        assertTrue(File(uploadDir, stem).exists())
        assertTrue(
            File(uploadDir.parentFile.parentFile, stem).let { !it.exists() },
            "nothing may be written outside the day's folder",
        )
    }

    @Test
    fun `a repeated name is renamed rather than overwriting`() {
        val name = uniqueName("image.png")
        upload(name)
        val second = upload(name)

        val secondName = second.obj().str("file-name")
        assertTrue(secondName != name, "the first photo must survive: $secondName")
        assertTrue(secondName.startsWith(name.removeSuffix(".png") + "_"), secondName)
        assertTrue(secondName.endsWith(".png"), secondName)
        assertTrue(File(uploadDir, name).exists() && File(uploadDir, secondName).exists())
    }

    @Test
    fun `the catalog is ordered by file name, not upload order`() {
        val selections = listenForSelections()
        val stem = uniqueName("")
        upload("${stem}c.png")
        upload("${stem}a.png")
        val folderId = upload("${stem}b.png").obj().str("folder-id")

        // Read the order out of the *current* catalog by asking the server to resolve each name.
        // The index in an upload response is a snapshot from the moment of that upload, so a photo
        // inserted before it later shifts it — comparing those numbers would be comparing snapshots
        // taken at different times.
        listOf("a", "b", "c").forEach { letter ->
            selectPicture("""{"folder-id":"$folderId","index":-1,"file-name":"$stem$letter.png"}""")
        }
        awaitUntil("all three to resolve") { selections.size == 3 }

        val (a, b, c) = selections.map { it.index }
        // Sorted by name, "b" lands between "a" and "c" however they arrived — the desktop sorts the
        // same way, so index N has to mean the same photo on both sides.
        assertTrue(a < b && b < c, "a=$a b=$b c=$c")
    }

    @Test
    fun `a request with no name is refused`() {
        val response = runBlocking {
            client.post(url("${Constants.ENDPOINT_PICTURES}/upload")) { setBody("""{"data":"x"}""") }
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `data that is not a base64 uri is refused`() {
        val response = upload(uniqueName("bad.png"), data = "just some text")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(uploadDir.listFiles().isNullOrEmpty(), "nothing should have been written")
    }

    @Test
    fun `uploads can be switched off without restarting the server`() {
        server.updateFileUploadEnabled(false)

        val response = upload(uniqueName("blocked.png"))

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(uploadDir.listFiles().isNullOrEmpty())
    }

    // ── Selecting ───────────────────────────────────────────────────────────────

    @Test
    fun `selecting by index asks the desktop for that index`() {
        val selections = listenForSelections()
        val folderId = upload(uniqueName("only.png")).obj().str("folder-id")

        val response = selectPicture("""{"folder-id":"$folderId","index":0}""")

        assertEquals(HttpStatusCode.OK, response.status)
        awaitUntil("the desktop to be asked") { selections.isNotEmpty() }
        assertEquals(0, selections.single().index)
        assertEquals(folderId, selections.single().folderId)
    }

    @Test
    fun `a file name beats the index it was sent with`() {
        val selections = listenForSelections()
        val stem = uniqueName("")
        upload("${stem}a.png")
        val bUpload = upload("${stem}b.png").obj()
        val folderId = bUpload.str("folder-id")
        val bIndex = bUpload.int("image-index")

        // The phone's index is stale (it sends 0); the name is the truth.
        val response = selectPicture("""{"folder-id":"$folderId","index":0,"file-name":"${stem}b.png"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        awaitUntil("the desktop to be asked") { selections.isNotEmpty() }
        assertEquals(
            bIndex,
            selections.single().index,
            "resolving by name is what stops a sort-order mismatch showing the wrong photo",
        )
    }

    @Test
    fun `a file name that is not in the folder falls back to the index`() {
        val selections = listenForSelections()
        val folderId = upload(uniqueName("present.png")).obj().str("folder-id")

        val response = selectPicture("""{"folder-id":"$folderId","index":0,"file-name":"gone.png"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        awaitUntil("the desktop to be asked") { selections.isNotEmpty() }
        assertEquals(0, selections.single().index)
    }

    @Test
    fun `a malformed selection is refused`() {
        val response = selectPicture("not json")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

}
