package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Searching Pexels and Pixabay for a background, and downloading the one that was picked.
 *
 * Four different response shapes are parsed here — photos and videos from two APIs that agree on
 * nothing — and each is mapped onto the same [StockMediaClient.StockMediaItem] the picker shows. A
 * field read from the wrong key does not fail: it maps to an empty URL, and the grid fills with
 * blank tiles or downloads a file that will not open, with nothing logged anywhere.
 *
 * The other half is what the outcome says when the request does not succeed. Each user supplies
 * their own free API key, so a wrong or exhausted key is the ordinary failure — and the dialog can
 * only tell someone to check their key, wait for the quota, or check the network if these three
 * come back distinguishable rather than as one generic failure.
 *
 * Every request here is served by a `MockEngine` swapped in through
 * [StockMediaClient.httpOverride], so nothing in this class touches the network. Downloads are
 * redirected into a temp directory the same way. Both are reset after each test —
 * `StockMediaClient` is a JVM-wide object.
 */
class StockMediaClientTest {

    private lateinit var dir: File
    private val requests = mutableListOf<HttpRequestData>()

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-stock-media-test").toFile()
        StockMediaClient.downloadDirOverride = File(dir, "stock-backgrounds")
    }

    @AfterTest
    fun resetClient() {
        StockMediaClient.httpOverride = null
        StockMediaClient.downloadDirOverride = null
        requests.clear()
        dir.deleteRecursively()
    }

    /** Serves [body] as JSON for every request, recording what was asked for. */
    private fun respondWith(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        StockMediaClient.httpOverride = HttpClient(
            MockEngine { request ->
                requests.add(request)
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
    }

    /** Serves [bytes] as a file body — what a download or a thumbnail fetch gets back. */
    private fun respondWithBytes(bytes: ByteArray, status: HttpStatusCode = HttpStatusCode.OK) {
        StockMediaClient.httpOverride = HttpClient(
            MockEngine { request ->
                requests.add(request)
                respond(content = bytes, status = status)
            },
        )
    }

    /** Fails every request the way an unplugged network cable does. */
    private fun failToConnect() {
        StockMediaClient.httpOverride = HttpClient(
            MockEngine { throw java.io.IOException("no route to host") },
        )
    }

    private fun search(
        source: StockMediaClient.StockSource,
        mediaType: StockMediaClient.StockMediaType,
        key: String = "test-key",
        query: String = "mountains",
        page: Int = 1,
    ) = runBlocking { StockMediaClient.search(source, key, mediaType, query, page) }

    private fun items(outcome: StockMediaClient.SearchOutcome): List<StockMediaClient.StockMediaItem> =
        assertIs<StockMediaClient.SearchOutcome.Success>(outcome, "expected a successful search, got $outcome").items

    private val pexels = StockMediaClient.StockSource.PEXELS
    private val pixabay = StockMediaClient.StockSource.PIXABAY
    private val photo = StockMediaClient.StockMediaType.PHOTO
    private val video = StockMediaClient.StockMediaType.VIDEO

    // ── Pexels photos ───────────────────────────────────────────────────────────

    @Test
    fun `a pexels photo is listed with a preview and a full-size download`() {
        respondWith(
            """{"photos":[{"id":12345,"src":{"original":"https://img/orig.jpg","large2x":"https://img/large.jpg"}}]}""",
        )

        val item = items(search(pexels, photo)).single()

        assertEquals("12345", item.id)
        assertEquals(pexels, item.source)
        assertEquals(false, item.isVideo)
        assertEquals("https://img/large.jpg", item.thumbnailUrl, "the grid shows the smaller one")
        assertEquals("https://img/orig.jpg", item.downloadUrl, "and a projected background wants the original")
    }

    @Test
    fun `a further page of pexels results is offered only when there is one`() {
        respondWith("""{"photos":[],"next_page":"https://api.pexels.com/v1/search?page=2"}""")
        assertTrue(assertIs<StockMediaClient.SearchOutcome.Success>(search(pexels, photo)).hasMore)

        respondWith("""{"photos":[]}""")
        assertTrue(
            !assertIs<StockMediaClient.SearchOutcome.Success>(search(pexels, photo)).hasMore,
            "offering a page that is not there ends in an empty grid",
        )
    }

    // ── Pexels videos ───────────────────────────────────────────────────────────

    @Test
    fun `an hd rendition is preferred for a pexels video`() {
        respondWith(
            """{"videos":[{"id":77,"image":"https://img/thumb.jpg","video_files":[
                {"link":"https://v/sd.mp4","quality":"sd","file_type":"video/mp4"},
                {"link":"https://v/hd.mp4","quality":"hd","file_type":"video/mp4"}]}]}""",
        )

        val item = items(search(pexels, video)).single()

        assertEquals("https://v/hd.mp4", item.downloadUrl, "a background is projected full screen — sd would be soft")
        assertEquals("https://img/thumb.jpg", item.thumbnailUrl)
        assertTrue(item.isVideo)
    }

    @Test
    fun `an mp4 is taken when nothing is marked hd`() {
        respondWith(
            """{"videos":[{"id":77,"image":"https://img/thumb.jpg","video_files":[
                {"link":"https://v/clip.webm","quality":"sd","file_type":"video/webm"},
                {"link":"https://v/clip.mp4","quality":"sd","file_type":"video/mp4"}]}]}""",
        )

        assertEquals(
            "https://v/clip.mp4",
            items(search(pexels, video)).single().downloadUrl,
            "mp4 is the format the player is sure of",
        )
    }

    @Test
    fun `whatever rendition exists is taken rather than none`() {
        respondWith(
            """{"videos":[{"id":77,"image":"https://img/thumb.jpg","video_files":[
                {"link":"https://v/only.webm","quality":"sd","file_type":"video/webm"}]}]}""",
        )

        assertEquals("https://v/only.webm", items(search(pexels, video)).single().downloadUrl)
    }

    @Test
    fun `a video with no renditions at all is left out of the grid`() {
        respondWith(
            """{"videos":[
                {"id":77,"image":"https://img/a.jpg","video_files":[]},
                {"id":78,"image":"https://img/b.jpg","video_files":[{"link":"https://v/b.mp4"}]}]}""",
        )

        assertEquals(
            listOf("78"),
            items(search(pexels, video)).map { it.id },
            "a tile that cannot be downloaded is worse than one tile fewer",
        )
    }

    // ── Pixabay photos ──────────────────────────────────────────────────────────

    @Test
    fun `a pixabay photo is listed with a preview and a large download`() {
        respondWith(
            """{"hits":[{"id":999,"previewURL":"https://img/prev.jpg","largeImageURL":"https://img/large.jpg"}],"totalHits":1}""",
        )

        val item = items(search(pixabay, photo)).single()

        assertEquals("999", item.id)
        assertEquals(pixabay, item.source)
        assertEquals("https://img/prev.jpg", item.thumbnailUrl)
        assertEquals("https://img/large.jpg", item.downloadUrl)
    }

    @Test
    fun `pixabay offers another page only when this one came back full`() {
        // Pixabay has no next-page link, so a full page is the only signal there may be more.
        val full = (1..24).joinToString(",") { """{"id":$it,"previewURL":"p$it","largeImageURL":"l$it"}""" }
        respondWith("""{"hits":[$full]}""")
        assertTrue(assertIs<StockMediaClient.SearchOutcome.Success>(search(pixabay, photo)).hasMore)

        respondWith("""{"hits":[{"id":1,"previewURL":"p","largeImageURL":"l"}]}""")
        assertTrue(!assertIs<StockMediaClient.SearchOutcome.Success>(search(pixabay, photo)).hasMore)
    }

    // ── Pixabay videos ──────────────────────────────────────────────────────────

    @Test
    fun `the medium rendition of a pixabay video is preferred`() {
        respondWith(
            """{"hits":[{"id":55,"videos":{
                "large":{"url":"https://v/large.mp4","thumbnail":"https://img/large.jpg"},
                "medium":{"url":"https://v/medium.mp4","thumbnail":"https://img/medium.jpg"},
                "small":{"url":"https://v/small.mp4"}}}]}""",
        )

        val item = items(search(pixabay, video)).single()

        assertEquals("https://v/medium.mp4", item.downloadUrl, "large is often far larger than a projector needs")
        assertEquals("https://img/medium.jpg", item.thumbnailUrl)
    }

    @Test
    fun `the renditions are tried in turn until one exists`() {
        respondWith("""{"hits":[{"id":55,"videos":{"tiny":{"url":"https://v/tiny.mp4"}}}]}""")

        assertEquals("https://v/tiny.mp4", items(search(pixabay, video)).single().downloadUrl)
    }

    @Test
    fun `a pixabay video with no thumbnail falls back to the video itself`() {
        respondWith("""{"hits":[{"id":55,"videos":{"medium":{"url":"https://v/medium.mp4"}}}]}""")

        val item = items(search(pixabay, video)).single()

        assertEquals("https://v/medium.mp4", item.thumbnailUrl, "an empty thumbnail would be a blank tile")
    }

    @Test
    fun `a pixabay video with no renditions is left out of the grid`() {
        respondWith("""{"hits":[{"id":55,"videos":{}},{"id":56,"videos":{"small":{"url":"https://v/s.mp4"}}}]}""")

        assertEquals(listOf("56"), items(search(pixabay, video)).map { it.id })
    }

    // ── What the search actually asks for ───────────────────────────────────────

    @Test
    fun `the pexels key travels as an authorization header`() {
        respondWith("""{"photos":[]}""")

        search(pexels, photo, key = "my-pexels-key", query = "autumn leaves", page = 3)

        val request = requests.single()
        assertEquals("my-pexels-key", request.headers[HttpHeaders.Authorization], "Pexels rejects the request without it")
        assertEquals("autumn leaves", request.url.parameters["query"])
        assertEquals("3", request.url.parameters["page"])
        assertEquals("landscape", request.url.parameters["orientation"], "a portrait background does not fill a screen")
    }

    @Test
    fun `the pixabay key travels as a query parameter`() {
        respondWith("""{"hits":[]}""")

        search(pixabay, photo, key = "my-pixabay-key", query = "autumn leaves")

        val request = requests.single()
        assertEquals("my-pixabay-key", request.url.parameters["key"], "Pixabay takes the key in the URL, not a header")
        assertEquals("autumn leaves", request.url.parameters["q"])
        assertEquals("horizontal", request.url.parameters["orientation"])
        assertEquals("photo", request.url.parameters["image_type"])
    }

    @Test
    fun `photos and videos go to different endpoints`() {
        respondWith("""{"photos":[],"videos":[],"hits":[]}""")

        search(pexels, photo)
        search(pexels, video)
        search(pixabay, photo)
        search(pixabay, video)

        assertEquals(
            listOf("/v1/search", "/videos/search", "/api/", "/api/videos/"),
            requests.map { it.url.encodedPath },
            "asking the photo endpoint for videos returns photos, which then parse as nothing",
        )
    }

    @Test
    fun `both sources ask for the same page size the grid is built for`() {
        respondWith("""{"photos":[],"hits":[]}""")

        search(pexels, photo)
        search(pixabay, photo)

        assertEquals(listOf("24", "24"), requests.map { it.url.parameters["per_page"] })
    }

    // ── When the search does not succeed ────────────────────────────────────────

    @Test
    fun `no key at all is refused without asking anyone`() {
        respondWith("""{"photos":[]}""")

        val outcome = search(pexels, photo, key = "   ")

        assertEquals(StockMediaClient.SearchOutcome.InvalidKey, outcome)
        assertTrue(requests.isEmpty(), "a blank key is a question for the settings dialog, not for Pexels")
    }

    @Test
    fun `a rejected key is reported as a key problem`() {
        respondWith("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized)
        assertEquals(StockMediaClient.SearchOutcome.InvalidKey, search(pexels, photo))

        respondWith("""{"error":"forbidden"}""", HttpStatusCode.Forbidden)
        assertEquals(
            StockMediaClient.SearchOutcome.InvalidKey,
            search(pixabay, photo),
            "403 is what Pixabay answers a bad key with — it has to read the same as 401",
        )
    }

    @Test
    fun `an exhausted quota is reported as rate limiting rather than a bad key`() {
        respondWith("""{"error":"too many requests"}""", HttpStatusCode.TooManyRequests)

        assertEquals(
            StockMediaClient.SearchOutcome.RateLimited,
            search(pexels, photo),
            "telling someone their key is wrong when it is merely used up sends them to regenerate it",
        )
    }

    @Test
    fun `any other refusal is a plain failure`() {
        respondWith("""{"error":"boom"}""", HttpStatusCode.InternalServerError)

        assertEquals(StockMediaClient.SearchOutcome.Failure, search(pexels, photo))
    }

    @Test
    fun `an unreachable network is told apart from a refusal`() {
        failToConnect()

        assertEquals(
            StockMediaClient.SearchOutcome.NetworkError,
            search(pexels, photo),
            "the church hall with no internet needs a different message from the one with a bad key",
        )
    }

    @Test
    fun `a reply that is not the expected shape fails rather than crashing the dialog`() {
        respondWith("""{"photos":"this should have been a list"}""")

        assertEquals(StockMediaClient.SearchOutcome.NetworkError, search(pexels, photo))
    }

    @Test
    fun `a search that matched nothing is a success with nothing in it`() {
        respondWith("""{"photos":[],"total_results":0}""")

        assertTrue(items(search(pexels, photo)).isEmpty(), "no results is an answer, not an error")
    }

    // ── Downloading the one that was picked ─────────────────────────────────────

    private fun item(
        id: String = "12345",
        source: StockMediaClient.StockSource = pexels,
        isVideo: Boolean = false,
        url: String = "https://img/orig.jpg",
    ) = StockMediaClient.StockMediaItem(
        id = id, source = source, isVideo = isVideo, thumbnailUrl = url, downloadUrl = url,
    )

    @Test
    fun `a downloaded photo is written where the app can find it again`() {
        respondWithBytes("jpeg-bytes".toByteArray())

        val outcome = runBlocking { StockMediaClient.download(item()) }

        val file = assertIs<StockMediaClient.DownloadOutcome.Success>(outcome).file
        assertEquals("pexels_12345.jpg", file.name, "the name has to say where it came from, so two ids never collide")
        assertEquals("jpeg-bytes", file.readText())
        assertTrue(file.parentFile.name == "stock-backgrounds")
    }

    @Test
    fun `a downloaded video is named as one`() {
        respondWithBytes("mp4-bytes".toByteArray())

        val outcome = runBlocking { StockMediaClient.download(item(id = "77", source = pixabay, isVideo = true, url = "https://v/hd.mp4")) }

        assertEquals(
            "pixabay_77.mp4",
            assertIs<StockMediaClient.DownloadOutcome.Success>(outcome).file.name,
            "the extension is what the player picks its backend from",
        )
    }

    @Test
    fun `the download folder is created the first time something is saved`() {
        StockMediaClient.downloadDirOverride = File(dir, "not/created/yet")
        respondWithBytes("bytes".toByteArray())

        val outcome = runBlocking { StockMediaClient.download(item()) }

        assertTrue(assertIs<StockMediaClient.DownloadOutcome.Success>(outcome).file.exists())
    }

    @Test
    fun `a refused download is reported as a failure`() {
        StockMediaClient.httpOverride = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })

        assertEquals(StockMediaClient.DownloadOutcome.Failure, runBlocking { StockMediaClient.download(item()) })
    }

    @Test
    fun `a download that never connects is told apart from one that was refused`() {
        failToConnect()

        assertEquals(StockMediaClient.DownloadOutcome.NetworkError, runBlocking { StockMediaClient.download(item()) })
    }

    @Test
    fun `downloading the same picture twice does not leave two copies`() {
        respondWithBytes("bytes".toByteArray())

        runBlocking { StockMediaClient.download(item()) }
        runBlocking { StockMediaClient.download(item()) }

        assertEquals(
            1,
            StockMediaClient.downloadDirOverride?.listFiles()?.size,
            "the id names the file, so re-downloading overwrites rather than piling up",
        )
    }

    // ── Thumbnails ──────────────────────────────────────────────────────────────

    @Test
    fun `a thumbnail comes back as bytes`() {
        respondWithBytes("png-bytes".toByteArray())

        val bytes = runBlocking { StockMediaClient.fetchThumbnailBytes("https://img/thumb.jpg") }

        assertEquals("png-bytes", bytes?.decodeToString())
    }

    @Test
    fun `a thumbnail that cannot be fetched leaves a blank tile rather than failing the grid`() {
        StockMediaClient.httpOverride = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        assertNull(runBlocking { StockMediaClient.fetchThumbnailBytes("https://img/gone.jpg") })

        failToConnect()
        assertNull(
            runBlocking { StockMediaClient.fetchThumbnailBytes("https://img/thumb.jpg") },
            "one unreachable thumbnail must not take the whole search result down",
        )
    }
}
