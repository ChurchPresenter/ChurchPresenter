package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * `GET /api/pictures` — the folder the desktop currently has open, which is what a phone asks for
 * when it opens its Pictures tab with no folder id to go on.
 *
 * **Its two siblings were covered and this one was not**, which is why it is worth its own suite.
 * `/api/pictures/{id}` and `/api/pictures/{id}/images/{index}` each have their key gate, their 404s
 * and their success path asserted elsewhere; the unparameterised route had none of the three. It is
 * also the only one of the three that can answer *"there is nothing to show yet"*, because it is the
 * only one not addressed by an id the caller already holds.
 *
 * **One shared server, plus a throwaway one for the empty case.** `CompanionServer` exposes no way
 * to *unload* a picture folder — `updatePictures` only ever sets it — so on the shared server the
 * "no folder open" case would depend on running before the others. That is the execution-order
 * dependency the project's test rules call out, so that one test stands up its own server instead
 * of assuming an order. The other two share the class's, because starting a Ktor engine is the
 * whole cost here (~1s) and neither of them needs a clean one.
 */
class CompanionServerPictureCatalogTest {

    private lateinit var client: HttpClient

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private lateinit var pictureDir: File
        private lateinit var server: CompanionServer
        private var port: Int = 0

        /** Distinct port each time, so a just-stopped listener cannot refuse the next bind. */
        private val nextPort = AtomicInteger(testPort(39_861))

        /** Starts a server and waits until it is actually listening, returning its port. */
        private fun startServer(target: CompanionServer): Int {
            target.start(port = nextPort.getAndIncrement())
            return runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!target.isRunning.value || target.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    target.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")
        }

        @JvmStatic
        @BeforeClass
        fun startSharedServer() {
            TestSingletons.latchToTestHome()
            pictureDir = Files.createTempDirectory("cp-picture-catalog").toFile()
            server = CompanionServer()
            port = startServer(server)
        }

        @JvmStatic
        @AfterClass
        fun stopSharedServer() {
            runCatching { server.stop() }
            runCatching { pictureDir.deleteRecursively() }
        }
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(CIO)
        server.updateApiKey(enabled = false, key = "")
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    private fun getPictures(apiKey: String? = null, atPort: Int = port): HttpResponse = runBlocking {
        client.get("http://127.0.0.1:$atPort${Constants.ENDPOINT_PICTURES}") {
            apiKey?.let { header(Constants.HEADER_API_KEY, it) }
        }
    }

    /** A plain GET against any path on the shared server, optionally carrying the api key. */
    private fun getting(path: String, apiKey: String? = null): HttpResponse = runBlocking {
        client.get("http://127.0.0.1:$port$path") {
            apiKey?.let { header(Constants.HEADER_API_KEY, it) }
        }
    }

    private fun posting(path: String, body: String, apiKey: String? = null): HttpResponse = runBlocking {
        client.post("http://127.0.0.1:$port$path") {
            apiKey?.let { header(Constants.HEADER_API_KEY, it) }
            setBody(body)
        }
    }

    private fun HttpResponse.obj(): JsonObject =
        json.parseToJsonElement(runBlocking { bodyAsText() }).jsonObject

    /** Puts a folder of [names] on the server as the open one. */
    private fun loadFolder(vararg names: String) {
        val files = names.map { name -> File(pictureDir, name).apply { writeBytes(byteArrayOf(1)) } }
        server.updatePictures(
            folderId = "folder-1",
            folderName = "Advent",
            folderPath = pictureDir.absolutePath,
            imageFiles = files,
        )
    }

    @Test
    fun `with no folder open the phone is told so, rather than given an empty folder`() {
        // The distinction the phone acts on: 503 means "the operator has not opened one yet", so it
        // keeps asking. An empty 200 would read as "the folder is open and has nothing in it" —
        // a different thing, and it would stop asking.
        val fresh = CompanionServer()
        val freshPort = startServer(fresh)
        try {
            assertEquals(HttpStatusCode.ServiceUnavailable, getPictures(atPort = freshPort).status)
        } finally {
            runCatching { fresh.stop() }
        }
    }

    @Test
    fun `the open folder is served with every image addressable`() {
        loadFolder("advent-01.jpg", "advent-02.jpg")

        val body = getPictures().obj()

        // The wire names are kebab-case and differ from the Kotlin properties, so they are asserted
        // as the phone sees them: renaming a property without its @SerialName would break the app.
        assertEquals("folder-1", body.getValue("folder-id").jsonPrimitive.content)
        assertEquals("Advent", body.getValue("folder-name").jsonPrimitive.content)
        assertEquals(2, body.getValue("image-total").jsonPrimitive.int)

        val images = body.getValue("images").jsonArray
        assertEquals(2, images.size)
        // The url is what the phone fetches next, so it has to carry this folder's own id — a
        // catalogue whose entries point elsewhere shows the wrong pictures, not none.
        assertTrue(
            images.all { "/folder-1/images/" in it.jsonObject.getValue("thumbnail-url").jsonPrimitive.content },
            "each entry must address this folder: $images",
        )
        assertEquals(
            listOf(0, 1),
            images.map { it.jsonObject.getValue("index").jsonPrimitive.int },
            "index is how the phone asks for an image, so it has to match the position served",
        )
    }

    @Test
    fun `the catalogue is behind the api key like the rest of the api`() {
        // Without this the folder path and every file name in it are readable by anyone on the
        // church wifi, whatever the operator set the key to.
        loadFolder("advent-01.jpg")
        server.updateApiKey(enabled = true, key = "s3cret")

        assertEquals(HttpStatusCode.Unauthorized, getPictures().status)
        assertEquals(HttpStatusCode.OK, getPictures(apiKey = "s3cret").status)
    }

    @Test
    fun `every asset route behind the catalogue is behind the key too`() {
        // Guarding the catalogue alone would be theatre: the entries in it are URLs, and anyone who
        // has seen one — or guesses a folder id — can fetch the file directly. Each of these
        // carries its own check, so each is asserted rather than assumed from the one above.
        loadFolder("advent-01.jpg")
        server.updateApiKey(enabled = true, key = "s3cret")

        val guarded = listOf(
            "${Constants.ENDPOINT_PICTURES}/folder-1",
            "${Constants.ENDPOINT_PICTURES}/folder-1/images/0",
            "${Constants.ENDPOINT_BIBLE_FILE}/secondary",
            "${Constants.ENDPOINT_BIBLE_FILE}/translations",
            "${Constants.ENDPOINT_BIBLE_FILE}/translation/0",
            Constants.ENDPOINT_BACKGROUNDS,
            "${Constants.ENDPOINT_BACKGROUNDS}/asset/default",
            "${Constants.ENDPOINT_MEDIA_STREAM}/media-1",
        )

        guarded.forEach { path ->
            assertEquals(
                HttpStatusCode.Unauthorized,
                getting(path).status,
                "$path answered without the key",
            )
        }
    }

    @Test
    fun `a bible translation index that is not there is a not-found`() {
        // The follower asks by position in the primary's manifest, and the two can disagree — a
        // translation removed on the primary between the manifest and the download. A 404 tells it
        // to re-read the manifest; anything else looks like the link itself is broken.
        server.updateApiKey(enabled = false, key = "")

        assertEquals(
            HttpStatusCode.NotFound,
            getting("${Constants.ENDPOINT_BIBLE_FILE}/translation/99").status,
            "an index past the end of the manifest",
        )
        assertEquals(
            HttpStatusCode.NotFound,
            getting("${Constants.ENDPOINT_BIBLE_FILE}/translation/not-a-number").status,
            "and an index that is not a number at all",
        )
    }

    // ── What the desktop reads back off the server ──────────────────────────────

    /**
     * `getImageFile` and `activeFolderId` are the desktop's side of the same catalogue.
     *
     * The remote-select handler in `MainDesktop` resolves a picture chosen on a phone through
     * these rather than through the Pictures tab's own state, because the phone can select out of
     * a folder the tab does not currently have open — a `device_uploads` selection is the usual
     * case. Reading the wrong file here puts a different image on the screen than the one tapped.
     */
    @Test
    fun `an image chosen remotely resolves to the file at that index`() {
        loadFolder("advent-01.jpg", "advent-02.jpg")

        assertEquals("folder-1", server.activeFolderId)
        assertEquals("advent-01.jpg", server.getImageFile("folder-1", 0)?.name)
        assertEquals("advent-02.jpg", server.getImageFile("folder-1", 1)?.name)
    }

    @Test
    fun `an index or folder the server does not have resolves to nothing`() {
        // A phone holding a stale catalogue asks for an index that has since gone. Returning the
        // wrong file would be worse than returning none: the operator sees a picture they did not
        // choose and has no way to tell it was the wrong one.
        loadFolder("advent-01.jpg")

        assertNull(server.getImageFile("folder-1", 5), "past the end of the folder")
        assertNull(server.getImageFile("folder-1", -1), "before the start of it")
        assertNull(server.getImageFile("some-other-folder", 0), "a folder that is not the open one")
    }

    @Test
    fun `the picture write routes are behind the key too`() {
        loadFolder("advent-01.jpg")
        server.updateApiKey(enabled = true, key = "s3cret")

        assertEquals(
            HttpStatusCode.Unauthorized,
            posting("${Constants.ENDPOINT_PICTURES}/select", """{"folderId":"folder-1","index":0}""").status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            posting("${Constants.ENDPOINT_PICTURES}/upload", """{"name":"a.jpg","data":""}""").status,
        )
    }

    @Test
    fun `a media stream for an id the desktop does not know is a not-found`() {
        server.updateApiKey(enabled = false, key = "")

        assertEquals(
            HttpStatusCode.NotFound,
            getting("${Constants.ENDPOINT_MEDIA_STREAM}/no-such-media").status,
        )
    }

    @Test
    fun `a background asset for a slot with nothing configured is a not-found`() {
        server.updateApiKey(enabled = false, key = "")

        assertEquals(
            HttpStatusCode.NotFound,
            getting("${Constants.ENDPOINT_BACKGROUNDS}/asset/default?type=image").status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            getting("${Constants.ENDPOINT_BACKGROUNDS}/asset/default?type=video").status,
        )
    }
}
