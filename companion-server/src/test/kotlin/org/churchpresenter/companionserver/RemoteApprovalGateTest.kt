package org.churchpresenter.companionserver

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The operator's veto over content a remote device tries to put on screen.
 *
 * Until this existed, only the schedule endpoints asked: selecting a picture, a slide, a verse or a
 * song section, clearing the display, and all three uploads happened **first** and raised a toast
 * afterwards. A phone that had never been approved could put anything on the screen mid-service and
 * write files into `~/.churchpresenter`, and the only recourse was to notice the toast and block the
 * device after the fact.
 *
 * Two rules carry the weight here, and both are asserted per endpoint:
 *  - **A denial changes nothing.** The route awaits the decision before it acts, so a refused
 *    request must leave the screen and the disk exactly as they were — not act and then undo.
 *  - **A blocked device is blocked everywhere.** The block used to be enforced on the WebSocket
 *    only; every REST route took the shared API key as sufficient, so a blocked phone kept working
 *    over HTTP. That asymmetry is what [`blocked device`] tests pin.
 *
 * The gate is inert when nothing is collecting `onInstantApproval` — that is what lets every other
 * suite here drive the server without an operator — so each test attaches its own collector first
 * and waits for the subscription to land before sending.
 */
class RemoteApprovalGateTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_967))
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

    private var operatorScope: CoroutineScope? = null

    @BeforeTest
    fun setUp() {
        client = HttpClient(CIO)
        server.blockedClientIds = emptySet()
    }

    @AfterTest
    fun tearDown() {
        operatorScope?.cancel()
        operatorScope = null
        runCatching { client.close() }
        server.blockedClientIds = emptySet()
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    /** Everything the operator was asked to approve, in order. */
    private val asked = CopyOnWriteArrayList<PendingInstantRequest>()

    /**
     * Stands an operator behind the server who answers [allow] to everything.
     *
     * Returns once the collector is actually attached — `requestApproval` allows outright when the
     * subscription count is zero, so sending before that would test nothing and pass.
     */
    private fun operatorAnswering(allow: Boolean) {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { operatorScope = it }
        val before = server.onInstantApproval.subscriptionCount.value
        scope.launch {
            server.onInstantApproval.collect { pending ->
                asked += pending
                pending.decision.complete(allow)
            }
        }
        runBlocking {
            withTimeoutOrNull(2_000) {
                while (server.onInstantApproval.subscriptionCount.value <= before) {
                    kotlinx.coroutines.delay(5)
                }
            } ?: error("the operator collector never attached")
        }
    }

    private fun post(path: String, body: String = "{}", deviceId: String = "phone-1"): HttpResponse =
        runBlocking {
            client.post("http://127.0.0.1:$port$path") {
                header(Constants.HEADER_DEVICE_ID, deviceId)
                setBody(body)
            }
        }

    // ── A denial changes nothing ────────────────────────────────────────────────

    @Test
    fun `a refused picture never reaches the screen`() {
        operatorAnswering(allow = false)

        val res = post("${Constants.ENDPOINT_PICTURES}/select", """{"folder-id":"f1","index":2}""")

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals(1, asked.size, "the operator was asked exactly once")
        assertEquals("present", asked.first().actionType)
    }

    @Test
    fun `an approved picture is applied and the operator saw what it was`() {
        operatorAnswering(allow = true)

        val res = post("${Constants.ENDPOINT_PICTURES}/select", """{"folder-id":"holiday","index":2}""")

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("holiday", asked.single().title, "the prompt names the folder, not the raw id")
    }

    @Test
    fun `a refused verse never reaches the screen`() {
        operatorAnswering(allow = false)

        val res = post(
            Constants.ENDPOINT_BIBLE_SELECT,
            """{"bookName":"John","chapter":3,"verseNumber":16,"verseText":"For God so loved","verseRange":""}""",
        )

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals("John 3:16", asked.single().title, "the operator decides from the reference")
    }

    @Test
    fun `a refused slide never reaches the screen`() {
        operatorAnswering(allow = false)

        val res = post("${Constants.ENDPOINT_PRESENTATIONS}/deck-1/select?index=4")

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals("Slide 5", asked.single().detail, "slides are 1-based on the prompt")
    }

    @Test
    fun `a refused song section never reaches the screen`() {
        operatorAnswering(allow = false)

        val res = post("${Constants.ENDPOINT_SONGS}/42/select?section=2")

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals("Song 42", asked.single().title)
    }

    // ── Uploads are refused before anything is written ──────────────────────────

    @Test
    fun `a refused deck is never written to disk`() {
        server.updateFileUploadEnabled(true)
        operatorAnswering(allow = false)
        val uploadDir = java.io.File(System.getProperty("user.home"), ".churchpresenter/device_presentations")
        val before = uploadDir.listFiles()?.size ?: 0

        val res = post(
            Constants.ENDPOINT_PRESENTATIONS_UPLOAD,
            """{"name":"refused.pptx","data":"data:application/x;base64,QUJD"}""",
        )

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals("upload", asked.single().actionType)
        assertEquals(
            before, uploadDir.listFiles()?.size ?: 0,
            "the gate runs before the write, so a refusal must leave no file behind",
        )
    }

    // ── A blocked device is blocked everywhere, not just on the socket ──────────

    @Test
    fun `a blocked device is refused by the rest routes it used to reach`() {
        server.blockedClientIds = setOf("bad-phone")
        operatorAnswering(allow = true)

        val picture = post("${Constants.ENDPOINT_PICTURES}/select", """{"folder-id":"f","index":0}""", "bad-phone")
        val slide = post("${Constants.ENDPOINT_PRESENTATIONS}/d/select?index=0", deviceId = "bad-phone")

        assertEquals(HttpStatusCode.Forbidden, picture.status)
        assertEquals(HttpStatusCode.Forbidden, slide.status)
        assertTrue(
            asked.isEmpty(),
            "a blocked device must not even raise a prompt — that is how blocking it achieves anything",
        )
    }

    @Test
    fun `a device that is not blocked still gets through`() {
        server.blockedClientIds = setOf("someone-else")
        operatorAnswering(allow = true)

        val res = post("${Constants.ENDPOINT_PICTURES}/select", """{"folder-id":"f","index":0}""", "good-phone")

        assertEquals(HttpStatusCode.OK, res.status)
    }
}
