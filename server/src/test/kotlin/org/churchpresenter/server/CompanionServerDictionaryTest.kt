package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.utils.Constants
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * The Strong's dictionary REST endpoints (`GET /api/dictionary`, `/{number}`, `/{number}/verses`) —
 * untouched by every other `CompanionServer*Test`.
 *
 * `StrongsDictionaryRepositoryTest` already pins the repository's own filtering/sorting/scoping
 * logic against a tiny fixture. The route answers from the server's own repository over the real
 * bundled dictionary, so these tests exercise only the route's behaviour that holds against it: the
 * request/response plumbing (status codes, param parsing), not exact result content.
 */
class CompanionServerDictionaryTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        server = CompanionServer()
        // Its own port: every CompanionServer suite claims a distinct one, and 39_721 is
        // CompanionServerQaModerationTest's. Sharing it means a bind failure whenever the previous
        // suite's socket has not finished closing.
        server.start(port = testPort(39_731))
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
        runCatching { client.close() }
        runCatching { server.stop() }
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    // ── GET /api/dictionary ────────────────────────────────────────────────────

    @Test
    fun `searching with no params answers 200 with a JSON array`() = runBlocking {
        val response = client.get(url(Constants.ENDPOINT_DICTIONARY))
        assertEquals(HttpStatusCode.OK, response.status)
        val results = json.parseToJsonElement(response.bodyAsText()).jsonArray // must parse as an array, not throw
        assertTrue(results.isNotEmpty(), "the bundled dictionary is never empty")
    }

    @Test
    fun `an out-of-range limit is coerced rather than rejected`() = runBlocking {
        val response = client.get(url("${Constants.ENDPOINT_DICTIONARY}?limit=0"))
        assertEquals(HttpStatusCode.OK, response.status)
        val results = json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(1, results.size, "limit=0 is coerced up to 1, not rejected or treated as unlimited")
    }

    // ── GET /api/dictionary/{number} ──────────────────────────────────────────

    @Test
    fun `looking up a number the dictionary does not have is a 404`() = runBlocking {
        val response = client.get(url("/api/dictionary/H9999999"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `looking up a real number from the bundled dictionary returns it`() = runBlocking {
        val response = client.get(url("/api/dictionary/H430"))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("H430", body["number"]?.jsonPrimitive?.content)
        assertTrue(body["word"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    // ── GET /api/dictionary/{number}/verses ───────────────────────────────────

    @Test
    fun `verses for a number are unavailable when no Bible is loaded`() = runBlocking {
        val response = client.get(url("/api/dictionary/H430/verses"))
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `once a Bible is loaded, verses for an unknown number is an empty, not a failing, response`() = runBlocking {
        val dir = Files.createTempDirectory("cp-dictionary-verses-test").toFile()
        server.updateBible(SpbFixture.loadedBible(dir), "KJV")

        val response = client.get(url("/api/dictionary/H9999999/verses"))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, json.parseToJsonElement(response.bodyAsText()).jsonObject["total"]?.jsonPrimitive?.int)
    }

    @Test
    fun `verses for a real, common number returns real verse references`() = runBlocking {
        val dir = Files.createTempDirectory("cp-dictionary-verses-real-test").toFile()
        server.updateBible(SpbFixture.loadedBible(dir), "KJV")

        val response = client.get(url("/api/dictionary/H430/verses"))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val total = body["total"]?.jsonPrimitive?.int ?: 0
        assertTrue(total > 0, "H430 must occur somewhere in the real Bible")
        val verses = body["verses"]!!.jsonArray
        assertTrue(verses.isNotEmpty())
        val first = verses.first().jsonObject
        assertTrue(first["bookName"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertTrue(first["reference"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `a blank number is a bad request on both the entry and its verses`() = runBlocking {
        assertEquals(HttpStatusCode.BadRequest, client.get(url("/api/dictionary/%20")).status)
        assertEquals(HttpStatusCode.BadRequest, client.get(url("/api/dictionary/%20/verses")).status)
    }

    // ── GET /api/bible, by book name ───────────────────────────────────────────

    @Test
    fun `a book asked for by name with a chapter comes back with only that chapter`() = runBlocking {
        server.updateBible(SpbFixture.loadedBible(Files.createTempDirectory("cp-bible-route").toFile()), "KJV")

        val response = client.get(url("${Constants.ENDPOINT_BIBLE}?book=genesis&chapter=2"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val books = body["books"]!!.jsonArray
        assertEquals(1, books.size, "the name matches one book, whatever its case")
        val chapters = books[0].jsonObject["chapters"]!!.jsonArray
        assertEquals(listOf(2), chapters.map { it.jsonObject["chapter"]!!.jsonPrimitive.int })
        assertEquals(1, body["verse-total"]?.jsonPrimitive?.int, "Genesis 2 has one verse in the fixture")
    }

    @Test
    fun `a book the Bible does not have comes back as no books rather than an error`() = runBlocking {
        server.updateBible(SpbFixture.loadedBible(Files.createTempDirectory("cp-bible-route-none").toFile()), "KJV")

        val response = client.get(url("${Constants.ENDPOINT_BIBLE}?book=Tobit"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["book-total"]?.jsonPrimitive?.int)
        assertEquals(0, body["books"]!!.jsonArray.size)
    }

    // ── GET /api/bible, by book id ─────────────────────────────────────────────

    @Test
    fun `a book asked for by id with a chapter comes back as that chapter's verses`() = runBlocking {
        server.updateBible(SpbFixture.loadedBible(Files.createTempDirectory("cp-bible-route-id").toFile()), "KJV")

        val found = client.get(url("${Constants.ENDPOINT_BIBLE}?book=1&chapter=1"))
        assertEquals(HttpStatusCode.OK, found.status)
        val body = json.parseToJsonElement(found.bodyAsText()).jsonObject
        assertEquals(1, body["chapter"]?.jsonPrimitive?.int)
        assertTrue(body["verses"]!!.jsonArray.isNotEmpty())

        assertEquals(HttpStatusCode.NotFound, client.get(url("${Constants.ENDPOINT_BIBLE}?book=1&chapter=999")).status)
    }

    @Test
    fun `a dictionary search and a number's verses take every filter they are given`() = runBlocking {
        server.updateBible(SpbFixture.loadedBible(Files.createTempDirectory("cp-dictionary-filters").toFile()), "KJV")

        val search = client.get(url("/api/dictionary?q=god&lang=hebrew&filter=word&limit=5&book=1&chapter=1&verse=1"))
        assertEquals(HttpStatusCode.OK, search.status)
        assertTrue(json.parseToJsonElement(search.bodyAsText()).jsonArray.size <= 5)

        val verses = client.get(url("/api/dictionary/H430/verses?limit=2&book=1&chapter=1&verse=1"))
        assertEquals(HttpStatusCode.OK, verses.status)
    }
}
