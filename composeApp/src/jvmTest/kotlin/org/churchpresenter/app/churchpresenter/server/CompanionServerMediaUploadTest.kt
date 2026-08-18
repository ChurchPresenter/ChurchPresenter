package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * Sending a video, a song file or a deck from a phone to the desktop.
 *
 * These are the endpoints that accept the *largest* things a device can send, so their refusals are
 * the interesting part — an upload the desktop cannot play or open is worse than one it never
 * accepted, because it lands in the schedule and fails in front of the room instead of on the phone.
 *
 * Each is checked for four refusals: the extension it cannot handle, a size past the operator's own
 * limit, a name it cannot use, and uploads being switched off. The size limit is the one an operator
 * actually tunes (`maxMediaUploadMb`), and it is honoured from `Content-Length` — before the body is
 * read, which is the whole point of checking it there.
 *
 * The media path also has to survive two phones sending the same file name: the second is renamed
 * rather than replacing the first, and a name carrying a path cannot escape the upload folder.
 *
 * `user.home` is redirected for the class, since both endpoints write under `~/.churchpresenter`.
 *
 * Not covered here: what happens to a *deck* after it is saved — parsing and rasterising it needs a
 * real PowerPoint or Keynote file through `DeckRasterizer`. Only the validation in front of that is
 * tested, which is where all the refusals live.
 */
class CompanionServerMediaUploadTest {

    private lateinit var client: HttpClient

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
            tempHome = Files.createTempDirectory("cp-media-upload").toFile()
            System.setProperty("user.home", tempHome.absolutePath)
            server = CompanionServer()
            server.start(port = testPort(39_727))
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

        private val mediaDir: File
            get() = File(System.getProperty("user.home"), ".churchpresenter/device_media")

        private val deckDir: File
            get() = File(System.getProperty("user.home"), ".churchpresenter/device_presentations")
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(CIO)
        server.updateFileUploadEnabled(true)
        server.updateMaxMediaUploadMb(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB)
        mediaDir.deleteRecursively()
        deckDir.deleteRecursively()
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    /** Uploads [bytes] as a media file named [name], the way the companion app streams one. */
    private fun uploadMedia(
        name: String?,
        bytes: ByteArray = "not really a video, but real bytes".toByteArray(),
        contentLengthOverride: Long? = null,
    ): HttpResponse = runBlocking {
        val query = if (name == null) "" else "?name=$name"
        client.post(url("${Constants.ENDPOINT_MEDIA_UPLOAD}$query")) {
            header(Constants.HEADER_DEVICE_ID, "phone-1")
            contentLengthOverride?.let { header("Content-Length", it.toString()) }
            setBody(bytes)
        }
    }

    /** Uploads a deck as a base64 data URI, the way the companion app posts one. */
    private fun uploadDeck(name: String, data: String? = null): HttpResponse = runBlocking {
        val body = if (data == null) {
            val encoded = Base64.getEncoder().encodeToString("pretend deck".toByteArray())
            """{"name":"$name","data":"data:application/octet-stream;base64,$encoded"}"""
        } else {
            """{"name":"$name","data":"$data"}"""
        }
        client.post(url("${Constants.ENDPOINT_PRESENTATIONS}/upload")) {
            header(Constants.HEADER_DEVICE_ID, "phone-1")
            setBody(body)
        }
    }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.obj(): JsonObject = json.parseToJsonElement(text()).jsonObject
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private var nameSeq = 0
    private fun uniqueName(stem: String) = "t${nameSeq++}-$stem"

    // ── Media: what gets through ────────────────────────────────────────────────

    @Test
    fun `a video is written to disk and its path handed back`() {
        val name = uniqueName("sermon.mp4")

        val response = uploadMedia(name)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.obj()
        assertEquals(Constants.MEDIA_TYPE_LOCAL, body.str("mediaType"))
        assertTrue(body.str("path").endsWith(name), body.str("path"))
        assertTrue(File(mediaDir, name).exists(), "the bytes have to land on disk")
        assertTrue(File(mediaDir, name).length() > 0, "and not as an empty file")
    }

    @Test
    fun `an audio file is reported as audio, not as video`() {
        val response = uploadMedia(uniqueName("hymn.mp3"))

        assertEquals(HttpStatusCode.OK, response.status)
        // The desktop routes audio differently — it keeps playing while the operator changes tabs.
        assertEquals(Constants.MEDIA_TYPE_AUDIO, response.obj().str("mediaType"))
    }

    @Test
    fun `the name handed back has no extension on it`() {
        val response = uploadMedia(uniqueName("titled.mp4"))

        // It becomes a schedule item's label, so the extension would show up in the service order.
        assertTrue(!response.obj().str("name").endsWith(".mp4"), response.obj().str("name"))
    }

    @Test
    fun `a repeated name is renamed rather than overwriting`() {
        val name = uniqueName("clip.mp4")
        uploadMedia(name)

        val second = uploadMedia(name)

        assertEquals(HttpStatusCode.OK, second.status)
        assertTrue(
            mediaDir.listFiles()!!.count { it.name.startsWith(name.removeSuffix(".mp4")) } == 2,
            "both uploads have to survive: ${mediaDir.list()?.toList()}",
        )
    }

    @Test
    fun `a path in the name cannot escape the media folder`() {
        val name = uniqueName("escaped.mp4")

        val response = uploadMedia("../../$name")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(File(mediaDir, name).exists())
        assertTrue(
            !File(mediaDir.parentFile.parentFile, name).exists(),
            "nothing may be written outside the media folder",
        )
    }

    // ── Media: what is refused ──────────────────────────────────────────────────

    @Test
    fun `a file type the desktop cannot play is refused`() {
        val response = uploadMedia(uniqueName("document.pdf"))

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(mediaDir.listFiles().isNullOrEmpty(), "nothing should have been written")
    }

    @Test
    fun `a file with no extension at all is refused`() {
        val response = uploadMedia(uniqueName("nameless"))

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    @Test
    fun `an upload with no name is refused`() {
        val response = uploadMedia(name = null)

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `an upload past the operator's size limit is refused before the body is read`() {
        server.updateMaxMediaUploadMb(1)

        // Content-Length is what the check reads, which is what lets it refuse without buffering
        // hundreds of megabytes first.
        val response = uploadMedia(
            uniqueName("huge.mp4"),
            bytes = ByteArray(2 * 1024 * 1024),
        )

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(mediaDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `the size limit is the operator's, not a fixed one`() {
        server.updateMaxMediaUploadMb(5)

        // The same two megabytes that were refused above now fit.
        val response = uploadMedia(uniqueName("fits.mp4"), bytes = ByteArray(2 * 1024 * 1024))

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a limit below one megabyte is floored rather than blocking everything`() {
        server.updateMaxMediaUploadMb(0)

        // Clamped to 1 MB, so a small file still gets through — a literal 0 would refuse every upload.
        val response = uploadMedia(uniqueName("small.mp4"))

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `media uploads can be switched off without restarting the server`() {
        server.updateFileUploadEnabled(false)

        val response = uploadMedia(uniqueName("blocked.mp4"))

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(mediaDir.listFiles().isNullOrEmpty())
    }

    // ── Decks: the validation in front of the rasterizer ────────────────────────

    @Test
    fun `a deck type the desktop cannot open is refused`() {
        val response = uploadDeck(uniqueName("notes.txt"))

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(deckDir.listFiles().isNullOrEmpty(), "nothing should have been written")
    }

    @Test
    fun `a deck with no data is refused`() {
        val response = runBlocking {
            client.post(url("${Constants.ENDPOINT_PRESENTATIONS}/upload")) {
                setBody("""{"name":"deck.pptx"}""")
            }
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a deck whose data is not a base64 uri is refused`() {
        val response = uploadDeck(uniqueName("deck.pptx"), data = "just some text")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `deck uploads obey the same off switch`() {
        server.updateFileUploadEnabled(false)

        val response = uploadDeck(uniqueName("deck.pptx"))

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `every deck type the desktop can open is accepted for saving`() {
        // The four extensions are the contract with the desktop's own importer; a refusal here is a
        // file the operator can open on the machine but not send from a phone.
        listOf("pptx", "ppt", "key", "pdf").forEach { ext ->
            val response = uploadDeck(uniqueName("deck.$ext"))

            assertTrue(
                response.status != HttpStatusCode.UnsupportedMediaType,
                "$ext should be accepted, got ${response.status}",
            )
        }
    }
}
