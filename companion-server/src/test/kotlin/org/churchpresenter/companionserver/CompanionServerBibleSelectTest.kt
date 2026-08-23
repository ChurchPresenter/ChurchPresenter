package org.churchpresenter.companionserver

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionServerBibleSelectTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private var operatorScope: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        server = CompanionServer()
        server.start(port = testPort(39_830))
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
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

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

    @Test
    fun `selecting a verse acks ok and notifies onSelectBibleVerse`() = runBlocking {
        val received = mutableListOf<SelectBibleVerseRequest>()
        collecting(server.onSelectBibleVerse) { received.add(it) }

        val req = SelectBibleVerseRequest(
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseText = "For God so loved the world.",
        )
        val response = client.post(url(Constants.ENDPOINT_BIBLE_SELECT)) {
            setBody(json.encodeToString(SelectBibleVerseRequest.serializer(), req))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"ok":true}""", response.bodyAsText())
        awaitUntil("onSelectBibleVerse") { received.isNotEmpty() }
        assertEquals(req, received.single())
    }

    @Test
    fun `an invalid body is a 400, not a 500`() = runBlocking {
        val response = client.post(url(Constants.ENDPOINT_BIBLE_SELECT)) {
            setBody("not json at all")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a multi-verse range is passed through as sent`() = runBlocking {
        val received = mutableListOf<SelectBibleVerseRequest>()
        collecting(server.onSelectBibleVerse) { received.add(it) }

        val req = SelectBibleVerseRequest(bookName = "Genesis", chapter = 1, verseNumber = 1, verseRange = "1-3")
        client.post(url(Constants.ENDPOINT_BIBLE_SELECT)) {
            setBody(json.encodeToString(SelectBibleVerseRequest.serializer(), req))
        }

        awaitUntil("onSelectBibleVerse") { received.isNotEmpty() }
        assertEquals("1-3", received.single().verseRange)
    }
}
