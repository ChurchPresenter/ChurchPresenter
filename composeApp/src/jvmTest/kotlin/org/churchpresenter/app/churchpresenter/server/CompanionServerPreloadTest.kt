package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * The server loading songs and a Bible off disk for itself.
 *
 * `preloadData` is what a phone's very first request depends on: it is called at startup and whenever
 * the storage settings change, and until it finishes the catalogs a device browses are empty. It runs
 * on the server's own IO scope, so these tests wait on the catalog appearing rather than on a delay.
 *
 * What matters is that it is **forgiving about its inputs and never takes the server down with it**.
 * The storage folders come from user settings, so they can be blank, missing, or full of files that
 * are not songs — and a startup that throws would leave a running app with a server that answers
 * nothing. Each of those is a test here, and each asserts the server still serves afterwards.
 *
 * Also covered: the Bible-file endpoints an Instance Link follower downloads modules through. They
 * hand over the actual `.spb` bytes, so a follower gets a real file or an honest 404 — never an empty
 * 200, which would leave the follower caching an unusable module.
 */
class CompanionServerPreloadTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private lateinit var workDir: File

        @JvmStatic
        @BeforeClass
        fun startServer() {
            workDir = Files.createTempDirectory("cp-preload").toFile()
            server = CompanionServer()
            server.start(port = testPort(39_725))
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
            runCatching { workDir.deleteRecursively() }
        }
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(CIO)
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun get(path: String): HttpResponse = runBlocking { client.get(url(path)) }
    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.bytes(): ByteArray = runBlocking { body<ByteArray>() }

    /** One row of the `.sps` line format: number#$#title#$#category#$#key#$#author#$#composer#$#lyrics. */
    private fun row(number: String, title: String) =
        listOf(number, title, "1", "G", "", "", "Verse line").joinToString("#\$#")

    /** A folder holding one real `.sps` library, named after [songbook] on its second header line. */
    private fun songFolder(songbook: String = "Hymnal", vararg rows: String): File =
        Files.createTempDirectory(workDir.toPath(), "songs").toFile().also { dir ->
            File(dir, "library.${Constants.EXTENSION_SPS}")
                .writeText("##SongPresenter\n##$songbook\n" + rows.joinToString("\n"), Charsets.UTF_8)
        }

    /** A folder holding one real `.spb` Bible module. */
    private fun bibleFolder(): Pair<File, String> {
        val dir = Files.createTempDirectory(workDir.toPath(), "bibles").toFile()
        SpbFixture.spbFile(dir, content = SpbFixture.sampleContent())
        return dir to "test.spb"
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val ok = runBlocking {
            withTimeoutOrNull(timeoutMs) {
                while (!condition()) kotlinx.coroutines.delay(15)
                true
            }
        }
        if (ok != true) throw AssertionError("timed out waiting for $what")
    }

    private fun songTitles(): List<String> =
        Regex("\"title\":\"([^\"]*)\"").findAll(get(Constants.ENDPOINT_SONGS).text())
            .map { it.groupValues[1] }
            .toList()

    // ── Loading songs ───────────────────────────────────────────────────────────

    @Test
    fun `songs on disk reach the catalog a phone browses`() {
        val dir = songFolder(rows = arrayOf(row("1", "Preloaded Grace")))

        server.preloadData(songStorageDir = dir.absolutePath, bibleStorageDir = "", primaryBibleFileName = "")

        awaitUntil("the song catalog to fill") { songTitles().contains("Preloaded Grace") }
    }

    @Test
    fun `a second library adds to the catalog rather than replacing it`() {
        val first = songFolder(songbook = "Hymnal", rows = arrayOf(row("1", "From The First Folder")))
        server.preloadData(first.absolutePath, "", "")
        awaitUntil("the first library") { songTitles().contains("From The First Folder") }

        val second = songFolder(songbook = "Chorus Book", rows = arrayOf(row("2", "From The Second Folder")))
        server.preloadData(second.absolutePath, "", "")

        awaitUntil("the second library") { songTitles().contains("From The Second Folder") }
    }

    @Test
    fun `a folder with no songs in it leaves the server serving`() {
        val empty = Files.createTempDirectory(workDir.toPath(), "empty").toFile()

        server.preloadData(empty.absolutePath, "", "")

        // Nothing to assert but that the server is still answering — a throwing preload would take
        // the whole companion API down while the app carried on looking fine.
        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `a folder that does not exist is survived`() {
        server.preloadData(File(workDir, "no-such-folder").absolutePath, "", "")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `a file that is not a song library is survived`() {
        val dir = Files.createTempDirectory(workDir.toPath(), "junk").toFile()
        File(dir, "broken.${Constants.EXTENSION_SPS}").writeText("this is not a song library at all")

        server.preloadData(dir.absolutePath, "", "")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `a non-song file in the folder is ignored`() {
        val dir = songFolder(rows = arrayOf(row("7", "The Real Song")))
        File(dir, "notes.txt").writeText("not a song")

        server.preloadData(dir.absolutePath, "", "")

        awaitUntil("the real song") { songTitles().contains("The Real Song") }
    }

    @Test
    fun `a blank song folder setting is a no-op`() {
        server.preloadData(songStorageDir = "", bibleStorageDir = "", primaryBibleFileName = "")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_SONGS).status)
    }

    // ── Loading a Bible ─────────────────────────────────────────────────────────

    @Test
    fun `a bible on disk reaches the catalog`() {
        val (dir, name) = bibleFolder()

        server.preloadData("", dir.absolutePath, name)

        awaitUntil("the bible catalog to fill") {
            get(Constants.ENDPOINT_BIBLE).text().contains("\"books\"")
        }
    }

    @Test
    fun `a bible file that is missing is survived`() {
        val (dir, _) = bibleFolder()

        server.preloadData("", dir.absolutePath, "not-there.spb")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_BIBLE).status)
    }

    @Test
    fun `a bible file that will not parse is survived`() {
        val dir = Files.createTempDirectory(workDir.toPath(), "badbible").toFile()
        File(dir, "broken.spb").writeText("not a bible module")

        server.preloadData("", dir.absolutePath, "broken.spb")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_BIBLE).status)
    }

    @Test
    fun `a bible folder with no file name named is a no-op`() {
        val (dir, _) = bibleFolder()

        server.preloadData("", dir.absolutePath, "")

        assertEquals(HttpStatusCode.OK, get(Constants.ENDPOINT_BIBLE).status)
    }

    // ── Handing a module to a follower ──────────────────────────────────────────

    @Test
    fun `the secondary bible file is served as its real bytes`() {
        val (dir, name) = bibleFolder()
        val file = File(dir, name)
        server.updateSecondaryBibleFilePath(file.absolutePath)

        val response = get("${Constants.ENDPOINT_BIBLE_FILE}/secondary")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.bytes().isNotEmpty(),
            "an empty 200 would leave the follower caching an unusable module",
        )
    }

    @Test
    fun `no secondary bible configured is a not-found`() {
        server.updateSecondaryBibleFilePath("")

        assertEquals(
            HttpStatusCode.NotFound,
            get("${Constants.ENDPOINT_BIBLE_FILE}/secondary").status,
        )
    }

    @Test
    fun `a module is served by its position in the stack`() {
        val (dir, name) = bibleFolder()
        server.updateBibleFilePaths(listOf(File(dir, name).absolutePath))

        val response = get("${Constants.ENDPOINT_BIBLE_FILE}/translation/0")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bytes().isNotEmpty())
    }

    @Test
    fun `a module arrives byte for byte, with a length the follower can trust`() {
        // The module is streamed from a handle opened before the response starts — a Bible cache
        // file can be deleted by its own instance while a follower is fetching it, and reopening it
        // later threw where nothing could catch it. What the follower receives must not change:
        // every byte of the file, and a Content-Length that matches them.
        val (dir, name) = bibleFolder()
        val file = File(dir, name)
        server.updateBibleFilePaths(listOf(file.absolutePath))

        val response = get("${Constants.ENDPOINT_BIBLE_FILE}/translation/0")
        val received = response.bytes()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(file.readBytes(), received)
        assertEquals(file.length(), response.contentLength())
    }

    @Test
    fun `a position past the end of the stack is a not-found`() {
        val (dir, name) = bibleFolder()
        server.updateBibleFilePaths(listOf(File(dir, name).absolutePath))

        assertEquals(
            HttpStatusCode.NotFound,
            get("${Constants.ENDPOINT_BIBLE_FILE}/translation/9").status,
        )
    }

    @Test
    fun `a path that no longer exists is dropped from the stack rather than served`() {
        val (dir, name) = bibleFolder()
        server.updateBibleFilePaths(
            listOf(File(dir, name).absolutePath, File(dir, "deleted.spb").absolutePath),
        )

        // The second entry never existed, so position 1 must not resolve to anything.
        assertEquals(
            HttpStatusCode.NotFound,
            get("${Constants.ENDPOINT_BIBLE_FILE}/translation/1").status,
        )
    }
}
