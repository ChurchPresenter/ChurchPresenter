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
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * What the companion server **refuses**, and with which status.
 *
 * The happy paths are covered by [CompanionServerTest] and [CompanionServerRemoteControlTest]; every
 * one of the 74 routes is already reached by those. What was not covered is the other half of each
 * handler — the guards. That matters more than the volume suggests, because these are the checks that
 * stand between a phone on the church WiFi and the machine driving the screen: the API key, the
 * upload switch, the size cap, the extension whitelist. A guard that returns 200 instead of refusing
 * is a remote-write hole, and a guard that refuses with the wrong status is one a client cannot
 * distinguish from a network fault.
 *
 * Same harness as the sibling classes — a real `CompanionServer` on a free port, driven over real
 * HTTP — because `start()` builds its own Netty server rather than exposing a separable Ktor module.
 * A high port so a running dev instance cannot be hit by accident.
 */
class CompanionServerRefusalTest {

    private lateinit var client: HttpClient

    companion object {
        const val KEY = "correct-horse-battery-staple"
        /** A minimal JSON upload body: the handlers check name/data before decoding anything. */
        const val UPLOAD_BODY = """{"name":"deck.pptx","data":"AAAA"}"""

        private lateinit var server: CompanionServer
        private var port: Int = 0

        /**
         * One server for the whole class, as the sibling suites do.
         *
         * Binding and unbinding Netty per test costs about a second each; the two guards these tests
         * flip are both live settings with public setters, so they are reset per test instead.
         */
        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_711))
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
    }

    /** Both guards are shared state on one server, so every test starts from the shipped defaults. */
    @BeforeTest
    fun resetState() {
        client = HttpClient(CIO)
        server.updateApiKey(enabled = false, key = "")
        server.updateFileUploadEnabled(true)
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun getting(path: String, key: String? = null): HttpResponse = runBlocking {
        client.get(url(path)) { key?.let { header(Constants.HEADER_API_KEY, it) } }
    }

    private fun posting(path: String, body: String = UPLOAD_BODY, key: String? = null): HttpResponse =
        runBlocking {
            client.post(url(path)) {
                key?.let { header(Constants.HEADER_API_KEY, it) }
                setBody(body)
            }
        }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }

    // ── The API key ─────────────────────────────────────────────────────────────

    @Test
    fun `with no key configured a request is served`() {
        // The default: a church on a trusted LAN that has not turned the key on.
        assertEquals(HttpStatusCode.OK, getting(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `a wrong key is rejected as unauthorized`() {
        server.updateApiKey(enabled = true, key = KEY)
        val response = getting(Constants.ENDPOINT_SONGS, key = "not-the-key")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.text().contains("Invalid API key"), "the client must be told why")
    }

    @Test
    fun `a missing key is rejected once one is required`() {
        server.updateApiKey(enabled = true, key = KEY)
        assertEquals(HttpStatusCode.Unauthorized, getting(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `the correct key is accepted`() {
        server.updateApiKey(enabled = true, key = KEY)
        assertEquals(HttpStatusCode.OK, getting(Constants.ENDPOINT_SONGS, key = KEY).status)
    }

    @Test
    fun `the key may be given as a query parameter as well as a header`() {
        // Phones opening a link cannot set headers, so the query form has to work too.
        server.updateApiKey(enabled = true, key = KEY)
        val response = getting("${Constants.ENDPOINT_SONGS}?${Constants.QUERY_PARAM_API_KEY}=$KEY")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a key that is enabled but empty does not lock everyone out`() {
        // Turning the switch on before typing a key must not brick the server for the room.
        server.updateApiKey(enabled = true, key = "")
        assertEquals(HttpStatusCode.OK, getting(Constants.ENDPOINT_SONGS).status)
    }

    @Test
    fun `disabling the key again stops it being required`() {
        server.updateApiKey(enabled = true, key = KEY)
        assertEquals(HttpStatusCode.Unauthorized, getting(Constants.ENDPOINT_SONGS).status)

        server.updateApiKey(enabled = false, key = KEY)
        assertEquals(
            HttpStatusCode.OK,
            getting(Constants.ENDPOINT_SONGS).status,
            "the setting is live — it must not need a restart to take effect",
        )
    }

    @Test
    fun `a prefix of the real key is not accepted`() {
        // The comparison is constant-time over the whole value, not a startsWith.
        server.updateApiKey(enabled = true, key = KEY)
        assertEquals(HttpStatusCode.Unauthorized, getting(Constants.ENDPOINT_SONGS, key = KEY.take(10)).status)
    }

    // ── The upload switch ───────────────────────────────────────────────────────

    @Test
    fun `presentation upload is refused while uploads are disabled`() {
        server.updateFileUploadEnabled(false)
        val response = posting("${Constants.ENDPOINT_PRESENTATIONS}/upload")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.text().contains("file upload is disabled"))
    }

    @Test
    fun `media upload is refused while uploads are disabled`() {
        server.updateFileUploadEnabled(false)
        val response = posting(Constants.ENDPOINT_MEDIA_UPLOAD)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.text().contains("file upload is disabled"))
    }

    @Test
    fun `the upload switch is checked after the key, so an unauthorized caller learns nothing about it`() {
        // Order matters: answering 403 to an unauthenticated caller would confirm the endpoint
        // exists and report a setting to someone who has not authenticated.
        server.updateApiKey(enabled = true, key = KEY)
        server.updateFileUploadEnabled(false)
        assertEquals(
            HttpStatusCode.Unauthorized,
            posting("${Constants.ENDPOINT_PRESENTATIONS}/upload").status,
            "the key is checked first",
        )
    }

    @Test
    fun `re-enabling uploads takes effect without a restart`() {
        server.updateFileUploadEnabled(false)
        assertEquals(HttpStatusCode.Forbidden, posting(Constants.ENDPOINT_MEDIA_UPLOAD).status)

        server.updateFileUploadEnabled(true)
        assertTrue(
            posting(Constants.ENDPOINT_MEDIA_UPLOAD).status != HttpStatusCode.Forbidden,
            "the switch is live; the request gets past the gate and is judged on its content",
        )
    }

    // ── What an upload body must look like ──────────────────────────────────────

    @Test
    fun `an upload with no name or data is a bad request`() {
        server.updateFileUploadEnabled(true)
        val response = posting("${Constants.ENDPOINT_PRESENTATIONS}/upload", body = """{}""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.text().contains("name and data are required"))
    }

    @Test
    fun `an upload naming a blank file is a bad request`() {
        server.updateFileUploadEnabled(true)
        val response = posting("${Constants.ENDPOINT_PRESENTATIONS}/upload", body = """{"name":"","data":"AAAA"}""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a presentation the app cannot open is refused on its extension`() {
        // The whitelist is pdf/ppt/pptx/key — anything else would be handed to a parser that
        // cannot read it, or worse, written to disk and offered as a deck.
        server.updateFileUploadEnabled(true)
        val response = posting(
            "${Constants.ENDPOINT_PRESENTATIONS}/upload",
            body = """{"name":"payload.sh","data":"AAAA"}""",
        )
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(response.text().contains("unsupported file type"), "and it says which type")
    }

    @Test
    fun `an oversized presentation is refused before its body is read`() {
        // Declared via Content-Length, so the check happens without the 200MB actually arriving.
        server.updateFileUploadEnabled(true)
        val response = runBlocking {
            client.post(url("${Constants.ENDPOINT_PRESENTATIONS}/upload")) {
                header("Content-Length", (201L * 1024 * 1024).toString())
                setBody(UPLOAD_BODY)
            }
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(response.text().contains("too large"))
    }

    // ── Unknown things ──────────────────────────────────────────────────────────

    @Test
    fun `a path the server does not serve is a plain not-found`() {
        assertEquals(HttpStatusCode.NotFound, getting("/api/no-such-endpoint").status)
    }
}
