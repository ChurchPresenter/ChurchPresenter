package org.churchpresenter.app.churchpresenter.server

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
 * logic against a tiny stubbed fixture (`Res.readBytes` mocked via `DictionaryFixture`). That
 * approach does not carry over here: the mock is installed from the JUnit test thread, but Ktor
 * dispatches the request handler onto its own Netty worker thread pool, and empirically the mocked
 * `Res` is not visible there — the route ends up reading the real bundled dictionary regardless.
 * Rather than chase that cross-thread mocking gap, these tests exercise only the route's own
 * behaviour that holds against the *real* dictionary: the request/response plumbing (status codes,
 * param parsing), not exact result content, which `StrongsDictionaryRepositoryTest` already owns.
 */
class CompanionServerDictionaryTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        // This test deliberately reads the real bundled dictionary (see class doc comment); reset
        // first so it never depends on whatever another test already loaded into these singletons.
        StrongsDictionaryRepository.cache.clear()
        StrongsDictionaryRepository.interlinear.resetForTest()

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
}
