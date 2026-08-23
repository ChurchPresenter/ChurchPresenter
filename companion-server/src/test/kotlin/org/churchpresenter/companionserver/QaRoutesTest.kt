package org.churchpresenter.companionserver

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.core.models.qa.QuestionStatus
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Q&A routes, from this module's side of the boundary.
 *
 * The store behind them is a [FakeQaStore] rather than the app's `QAManager`, so what is under test
 * here is the *route contract*: who is allowed through, what the operator prompt carries, what the
 * status code is when a question is missing, and — the rule with teeth — that a request the
 * operator refuses changes nothing. `CompanionServerQaTest` and `CompanionServerQaModerationTest`
 * in `:composeApp` drive the same endpoints against the real `QAManager`; between them the routes
 * and the store are each covered by the module that owns them.
 *
 * Every moderation endpoint suspends on a `PendingQAAdminRequest` until the desktop resolves it, so
 * [operator] stands in for the person at the machine and every test says which way they answered.
 */
class QaRoutesTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private const val PASSWORD = "let-me-in"

        /** Resolves whatever the routes ask the desktop, and records what they asked. */
        private lateinit var operatorScope: CoroutineScope
        private lateinit var prompts: MutableList<CompanionServer.PendingQAAdminRequest>
        @Volatile private var operatorAnswer = true

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_931))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")

            prompts = mutableListOf()
            operatorScope = CoroutineScope(Dispatchers.IO + Job())
            operatorScope.launch {
                server.onQAAdminRequest.collect { pending ->
                    prompts += pending
                    pending.decision.complete(operatorAnswer)
                }
            }
            operatorScope.launch {
                server.onQaAdminConnect.collect { it.decision.complete(operatorAnswer) }
            }
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            operatorScope.cancel()
            runCatching { server.stop() }
        }
    }

    private lateinit var qa: FakeQaStore

    @BeforeTest
    fun setUp() {
        client = HttpClient(CIO)
        qa = FakeQaStore()
        server.qaStore = qa
        server.qaAdminPassword = PASSWORD
        server.qaVotingEnabled = true
        server.qaCooldownSeconds = 30
        prompts.clear()
        operatorAnswer = true
    }

    @AfterTest
    fun tearDown() {
        runCatching { client.close() }
        server.qaStore = null
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    private fun url(path: String) = "http://127.0.0.1:$port$path"
    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.field(name: String): String? =
        Json.parseToJsonElement(text()).jsonObject[name]?.jsonPrimitive?.content

    private fun get(path: String, password: String? = PASSWORD): HttpResponse = runBlocking {
        client.get(url(path)) { password?.let { header("X-QA-Password", it) } }
    }

    private fun post(path: String, body: String = "{}", password: String? = PASSWORD): HttpResponse =
        runBlocking {
            client.post(url(path)) {
                password?.let { header("X-QA-Password", it) }
                header("X-Device-Id", "phone-1")
                setBody(body)
            }
        }

    private fun del(path: String, password: String? = PASSWORD): HttpResponse = runBlocking {
        client.delete(url(path)) { password?.let { header("X-QA-Password", it) } }
    }

    /** Answers the next operator prompt with [allow] for the duration of [block]. */
    private fun <T> operator(allow: Boolean, block: () -> T): T {
        operatorAnswer = allow
        try { return block() } finally { operatorAnswer = true }
    }

    // ── The public side: what a phone that scanned the QR code can reach ─────────

    @Test
    fun `the submission page is served without a password`() {
        val page = get("/qa", password = null)
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.text().contains("<html", ignoreCase = true), "a phone gets a page, not JSON")
    }

    @Test
    fun `the voting and admin pages are served too`() {
        assertEquals(HttpStatusCode.OK, get("/qa/vote", password = null).status)
        assertEquals(HttpStatusCode.OK, get("/qa/admin", password = null).status)
    }

    @Test
    fun `status reports the session, the cooldown and what is on screen`() {
        val shown = qa.seed("On the projector", QuestionStatus.APPROVED)
        qa.displayQuestion(shown.id)
        server.qaCooldownSeconds = 45

        val body = get("/api/qa/status", password = null)

        assertEquals("true", body.field("sessionActive"))
        assertEquals("45", body.field("cooldownSeconds"))
        assertEquals(shown.id, body.field("displayedQuestionId"))
        assertEquals("true", body.field("votingEnabled"))
    }

    @Test
    fun `status with no store attached reports a closed session rather than failing`() {
        server.qaStore = null

        val body = get("/api/qa/status", password = null)

        assertEquals(HttpStatusCode.OK, body.status)
        assertEquals("false", body.field("sessionActive"))
        assertEquals("", body.field("displayedQuestionId"))
    }

    @Test
    fun `a submitted question comes back with the id the phone will refer to`() {
        val res = post("/api/qa/submit", """{"text":"Why Sunday?","name":"Ada"}""", password = null)

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("Why Sunday?", res.field("text"))
        assertEquals("Ada", res.field("submitterName"))
        assertEquals(1, qa.questions.size)
    }

    @Test
    fun `submitting into a closed session is refused`() {
        qa.sessionActive = false

        val res = post("/api/qa/submit", """{"text":"Anyone there?"}""", password = null)

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertTrue(qa.questions.isEmpty(), "nothing may be recorded while the session is closed")
    }

    @Test
    fun `submitting with no store attached is refused`() {
        server.qaStore = null

        assertEquals(
            HttpStatusCode.Forbidden,
            post("/api/qa/submit", """{"text":"Hello?"}""", password = null).status,
        )
    }

    @Test
    fun `a malformed submission is a bad request`() {
        assertEquals(
            HttpStatusCode.BadRequest,
            post("/api/qa/submit", "not json at all", password = null).status,
        )
    }

    @Test
    fun `an empty question is refused before it reaches the store`() {
        assertEquals(
            HttpStatusCode.BadRequest,
            post("/api/qa/submit", """{"text":"   "}""", password = null).status,
        )
        assertTrue(qa.questions.isEmpty())
    }

    @Test
    fun `a second question from the same phone inside the cooldown is a rate limit`() {
        assertEquals(HttpStatusCode.OK, post("/api/qa/submit", """{"text":"First"}""", password = null).status)

        val second = post("/api/qa/submit", """{"text":"Second"}""", password = null)

        assertEquals(HttpStatusCode.TooManyRequests, second.status)
        assertEquals(1, qa.questions.size, "the rate-limited question is not recorded")
    }

    @Test
    fun `a refusal that is not a rate limit is a plain forbidden`() {
        // Session open, cooldown off, but the store declines — the route must not claim rate limiting.
        server.qaCooldownSeconds = 0
        post("/api/qa/submit", """{"text":"First"}""", password = null)
        qa.sessionActive = false

        assertEquals(
            HttpStatusCode.Forbidden,
            post("/api/qa/submit", """{"text":"Second"}""", password = null).status,
        )
    }

    // ── Voting ──────────────────────────────────────────────────────────────────

    @Test
    fun `the approved list carries each question's own vote state`() {
        val q = qa.seed("Vote for me", QuestionStatus.APPROVED)
        qa.seed("Still pending")

        val body = get("/api/qa/approved", password = null).text()

        assertTrue(body.contains(q.id), "an approved question is listed")
        assertTrue(!body.contains("Still pending"), "a pending question is not offered for voting")
        assertTrue(body.contains(""""voted":null"""), "this phone has not voted yet")
    }

    @Test
    fun `the approved list escapes text that would otherwise break the JSON`() {
        qa.seed("""He said "hi"\and left""", QuestionStatus.APPROVED)

        val body = get("/api/qa/approved", password = null).text()

        // Parses at all is the assertion — an unescaped quote makes this throw.
        Json.parseToJsonElement(body)
    }

    @Test
    fun `voting is closed when the operator has not enabled it`() {
        server.qaVotingEnabled = false

        assertEquals(HttpStatusCode.Forbidden, get("/api/qa/approved", password = null).status)
        assertEquals(
            HttpStatusCode.Forbidden,
            post("/api/qa/vote", """{"questionId":"q1"}""", password = null).status,
        )
    }

    @Test
    fun `the approved list is empty rather than an error with no session`() {
        qa.sessionActive = false

        val res = get("/api/qa/approved", password = null)

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("[]", res.text())
    }

    @Test
    fun `a vote is recorded and reported back`() {
        val q = qa.seed("Vote for me", QuestionStatus.APPROVED)

        val res = post("/api/qa/vote", """{"questionId":"${q.id}"}""", password = null)

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("up", res.field("voted"))
        assertEquals(1, qa.findQuestion(q.id)?.voteCount)
    }

    @Test
    fun `voting the same way twice takes the vote back`() {
        val q = qa.seed("Vote for me", QuestionStatus.APPROVED)
        post("/api/qa/vote", """{"questionId":"${q.id}","direction":"up"}""", password = null)

        val res = post("/api/qa/vote", """{"questionId":"${q.id}","direction":"up"}""", password = null)

        assertEquals("null", Json.parseToJsonElement(res.text()).jsonObject["voted"].toString())
        assertEquals(0, qa.findQuestion(q.id)?.voteCount)
    }

    @Test
    fun `a direction that is not down is treated as up`() {
        val q = qa.seed("Vote for me", QuestionStatus.APPROVED)

        val res = post("/api/qa/vote", """{"questionId":"${q.id}","direction":"sideways"}""", password = null)

        assertEquals("up", res.field("voted"))
    }

    @Test
    fun `voting down is recorded as down`() {
        val q = qa.seed("Vote for me", QuestionStatus.APPROVED)

        val res = post("/api/qa/vote", """{"questionId":"${q.id}","direction":"down"}""", password = null)

        assertEquals("down", res.field("voted"))
        assertEquals(-1, qa.findQuestion(q.id)?.voteCount)
    }

    @Test
    fun `voting for a question that is not there is a not-found`() {
        assertEquals(
            HttpStatusCode.NotFound,
            post("/api/qa/vote", """{"questionId":"nope"}""", password = null).status,
        )
    }

    @Test
    fun `a question still awaiting moderation cannot be voted on`() {
        val q = qa.seed("Not approved yet")

        assertEquals(
            HttpStatusCode.Forbidden,
            post("/api/qa/vote", """{"questionId":"${q.id}"}""", password = null).status,
        )
    }

    @Test
    fun `a malformed vote is a bad request`() {
        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/vote", "{{{", password = null).status)
    }

    // ── The admin password gate ─────────────────────────────────────────────────

    @Test
    fun `moderation is closed to a phone without the password`() {
        assertEquals(HttpStatusCode.Unauthorized, get("/api/qa/questions", password = "wrong").status)
        assertEquals(HttpStatusCode.Unauthorized, post("/api/qa/add", """{"text":"x"}""", "wrong").status)
        assertEquals(HttpStatusCode.Unauthorized, del("/api/qa/questions/q1", password = "wrong").status)
    }

    @Test
    fun `no password configured leaves moderation open`() {
        server.qaAdminPassword = ""

        assertEquals(HttpStatusCode.OK, get("/api/qa/questions", password = null).status)
    }

    @Test
    fun `the auth handshake asks the operator before letting a device in`() {
        assertEquals(HttpStatusCode.OK, post("/api/qa/auth").status)
    }

    @Test
    fun `a device the operator refuses is not let in`() {
        val res = operator(allow = false) { post("/api/qa/auth") }

        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    // ── Moderation ──────────────────────────────────────────────────────────────

    @Test
    fun `the queue lists every question by default`() {
        qa.seed("One")
        qa.seed("Two", QuestionStatus.APPROVED)

        val body = get("/api/qa/questions").text()

        assertTrue(body.contains("One") && body.contains("Two"))
    }

    @Test
    fun `the queue can be filtered to one status`() {
        qa.seed("Pending one")
        qa.seed("Approved one", QuestionStatus.APPROVED)

        val body = get("/api/qa/questions?status=approved").text()

        assertTrue(body.contains("Approved one"))
        assertTrue(!body.contains("Pending one"))
    }

    @Test
    fun `a status filter that names nothing real falls back to the whole queue`() {
        qa.seed("One")

        assertTrue(get("/api/qa/questions?status=banana").text().contains("One"))
    }

    @Test
    fun `the queue is an empty list rather than an error with no store attached`() {
        server.qaStore = null

        assertEquals("[]", get("/api/qa/questions").text())
    }

    @Test
    fun `an approved question moves out of the queue and the operator saw its text`() {
        val q = qa.seed("Approve me")

        val res = post("/api/qa/questions/${q.id}/approve")

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(QuestionStatus.APPROVED, qa.findQuestion(q.id)?.status)
        assertEquals("Approve me", prompts.last().text)
        assertEquals("approve", prompts.last().action)
    }

    @Test
    fun `a refused approval leaves the question pending`() {
        val q = qa.seed("Approve me")

        val res = operator(allow = false) { post("/api/qa/questions/${q.id}/approve") }

        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertEquals(QuestionStatus.PENDING, qa.findQuestion(q.id)?.status)
    }

    @Test
    fun `approving a question that does not exist is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/nope/approve").status)
    }

    @Test
    fun `an approved edit replaces the text`() {
        val q = qa.seed("Typo heer")

        val res = post("/api/qa/questions/${q.id}/edit", """{"text":"Typo here"}""")

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("Typo here", qa.findQuestion(q.id)?.text)
    }

    @Test
    fun `a refused edit leaves the text alone`() {
        val q = qa.seed("Original")

        operator(allow = false) { post("/api/qa/questions/${q.id}/edit", """{"text":"Changed"}""") }

        assertEquals("Original", qa.findQuestion(q.id)?.text)
    }

    @Test
    fun `a malformed edit is refused before the operator is bothered`() {
        val q = qa.seed("Original")
        prompts.clear()

        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/questions/${q.id}/edit", "nonsense").status)
        assertTrue(prompts.isEmpty(), "the desktop must not be interrupted by a request that cannot be carried out")
    }

    @Test
    fun `editing a question that does not exist is a not-found`() {
        assertEquals(
            HttpStatusCode.NotFound,
            post("/api/qa/questions/nope/edit", """{"text":"x"}""").status,
        )
    }

    @Test
    fun `an approved deny takes the question out of the queue`() {
        val q = qa.seed("Deny me")

        assertEquals(HttpStatusCode.OK, post("/api/qa/questions/${q.id}/deny").status)
        assertEquals(QuestionStatus.DENIED, qa.findQuestion(q.id)?.status)
    }

    @Test
    fun `a refused deny leaves the question pending`() {
        val q = qa.seed("Deny me")

        operator(allow = false) { post("/api/qa/questions/${q.id}/deny") }

        assertEquals(QuestionStatus.PENDING, qa.findQuestion(q.id)?.status)
    }

    @Test
    fun `denying a question that does not exist is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/nope/deny").status)
    }

    @Test
    fun `an approved done marks the question answered`() {
        val q = qa.seed("Answer me", QuestionStatus.APPROVED)

        assertEquals(HttpStatusCode.OK, post("/api/qa/questions/${q.id}/done").status)
        assertEquals(QuestionStatus.DONE, qa.findQuestion(q.id)?.status)
    }

    @Test
    fun `a refused done leaves it approved`() {
        val q = qa.seed("Answer me", QuestionStatus.APPROVED)

        operator(allow = false) { post("/api/qa/questions/${q.id}/done") }

        assertEquals(QuestionStatus.APPROVED, qa.findQuestion(q.id)?.status)
    }

    @Test
    fun `marking a question done that does not exist is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/nope/done").status)
    }

    @Test
    fun `an approved display puts the question on the projector`() {
        val q = qa.seed("Show me", QuestionStatus.APPROVED)

        assertEquals(HttpStatusCode.OK, post("/api/qa/questions/${q.id}/display").status)
        assertEquals(q.id, qa.displayedQuestion?.id)
    }

    @Test
    fun `a refused display leaves the projector alone`() {
        val q = qa.seed("Show me", QuestionStatus.APPROVED)

        operator(allow = false) { post("/api/qa/questions/${q.id}/display") }

        assertNull(qa.displayedQuestion)
    }

    @Test
    fun `a question that has not been approved cannot be displayed`() {
        val q = qa.seed("Not yet")

        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/${q.id}/display").status)
        assertNull(qa.displayedQuestion)
    }

    @Test
    fun `an approved delete removes the question`() {
        val q = qa.seed("Delete me")

        assertEquals(HttpStatusCode.OK, del("/api/qa/questions/${q.id}").status)
        assertNull(qa.findQuestion(q.id))
    }

    @Test
    fun `a refused delete keeps the question`() {
        val q = qa.seed("Delete me")

        operator(allow = false) { del("/api/qa/questions/${q.id}") }

        assertEquals("Delete me", qa.findQuestion(q.id)?.text)
    }

    @Test
    fun `deleting a question that does not exist is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, del("/api/qa/questions/nope").status)
    }

    @Test
    fun `an approved add returns the new question`() {
        val res = post("/api/qa/add", """{"text":"From the desk"}""")

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("From the desk", res.field("text"))
        assertEquals(1, qa.questions.size)
    }

    @Test
    fun `a refused add adds nothing`() {
        operator(allow = false) { post("/api/qa/add", """{"text":"From the desk"}""") }

        assertTrue(qa.questions.isEmpty())
    }

    @Test
    fun `a malformed add is refused before the operator is bothered`() {
        prompts.clear()

        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/add", "]not json[").status)
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun `adding with no session open fails rather than inventing one`() {
        qa.sessionActive = false

        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/add", """{"text":"x"}""").status)
    }

    @Test
    fun `clear-display takes the question off the projector`() {
        val q = qa.seed("On screen", QuestionStatus.APPROVED)
        qa.displayQuestion(q.id)

        assertEquals(HttpStatusCode.OK, post("/api/qa/clear-display").status)
        assertNull(qa.displayedQuestion)
    }

    @Test
    fun `clear-all empties the session`() {
        qa.seed("One")
        qa.seed("Two", QuestionStatus.APPROVED)

        assertEquals(HttpStatusCode.OK, post("/api/qa/clear-all").status)
        assertTrue(qa.questions.isEmpty())
    }
}
