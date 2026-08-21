package org.churchpresenter.app.churchpresenter.server

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.models.qa.QuestionStatus
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.app.churchpresenter.testPort

/**
 * Moderating a Q&A session from a phone.
 *
 * Everything a moderator can do to a question over the network — edit, deny, mark done, display,
 * delete, add one themselves, clear the lot — and only approve and submit had been exercised.
 *
 * **Every one of these actions waits on the desktop operator.** The endpoint suspends on a
 * `PendingQAAdminRequest` until someone at the machine resolves it, so the rule with teeth is that a
 * refusal changes nothing: a phone that is told "no" must not have edited, deleted or displayed
 * anything. That is asserted for each action, alongside what the desktop was actually shown in the
 * prompt — the operator decides from that text, so a prompt naming the wrong question is worse than
 * no prompt.
 *
 * The one exception is `clear-all`, which has no operator gate at all: the password check is the only
 * thing standing in front of it. That asymmetry is deliberate in the server and is pinned here so it
 * cannot be lost by accident.
 *
 * Driven against a real server over real HTTP, as the sibling suites are, because `start()` builds
 * its own Netty server rather than exposing a separable Ktor module.
 */
class CompanionServerQaModerationTest {

    private lateinit var client: HttpClient
    private var operatorScope: CoroutineScope? = null

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * `user.home` is redirected for the whole class: `QAManager` persists its session under it and
         * restores that file on construction, so without this each test would start holding the
         * questions the previous one asked — and the real one would be writing into the developer's
         * own `~/.churchpresenter`.
         */
        private var realHome: String? = null
        private lateinit var tempHome: File

        /**
         * Where `QAManager` persists a session, deleted per test so none inherits the last one's.
         *
         * The name matters: it is `qa_state.json`, not `qa_session.json`. Pointing at the wrong name
         * deletes nothing, and the next `QAManager()` restores every question the previous test asked —
         * which shows up as a count that is right for one test and wrong for the one after it.
         */
        private val qaStateFile: File
            get() = File(System.getProperty("user.home"), ".churchpresenter/qa_state.json")

        @JvmStatic
        @BeforeClass
        fun startServer() {
            TestSingletons.latchToTestHome()
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-qa-moderation").toFile()
            System.setProperty("user.home", tempHome.absolutePath)
            server = CompanionServer()
            server.start(port = testPort(39_721))
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
    }

    @BeforeTest
    fun resetState() {
        client = HttpClient(CIO)
        qaStateFile.delete()
        server.qaManager = null
        server.qaAdminPassword = ""
        server.qaVotingEnabled = false
        server.qaCooldownSeconds = 0
    }

    @AfterTest
    fun closeClient() {
        runCatching { operatorScope?.cancel() }
        operatorScope = null
        runCatching { client.close() }
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    /** A manager with an open session, and no cooldown (every request here is from 127.0.0.1). */
    private fun openSession(): QAManager = QAManager().also {
        it.toggleSession()
        server.qaManager = it
        server.qaCooldownSeconds = 0
    }

    /**
     * Answers the operator prompt every moderation action waits on, with [allow].
     *
     * Without something playing the operator these requests never respond at all. Returns the
     * prompts that were answered so a test can assert what the desktop was shown.
     */
    private fun playOperator(allow: Boolean = true): MutableList<CompanionServer.PendingQAAdminRequest> {
        val seen = mutableListOf<CompanionServer.PendingQAAdminRequest>()
        operatorScope = CoroutineScope(Dispatchers.IO + Job()).also { scope ->
            scope.launch {
                server.onQAAdminRequest.collect { pending ->
                    seen.add(pending)
                    pending.decision.complete(allow)
                }
            }
        }
        // The collector is not subscribed the moment `launch` returns, and onQAAdminRequest has no
        // replay: a request arriving before it subscribes has its prompt dropped, its decision
        // never completed, and the call hangs until the client gives up. Wait for the subscription
        // itself — a positive signal, so this ends as soon as the collector is live.
        runBlocking {
            withTimeout(5_000) { server.onQAAdminRequest.subscriptionCount.first { it > 0 } }
        }
        return seen
    }

    private fun post(path: String, body: String = "", password: String? = null): HttpResponse = runBlocking {
        client.post(url(path)) {
            password?.let { p -> header("X-QA-Password", p) }
            setBody(body)
        }
    }

    private fun delete(path: String, password: String? = null): HttpResponse = runBlocking {
        client.delete(url(path)) { password?.let { p -> header("X-QA-Password", p) } }
    }

    private fun get(path: String, password: String? = null): HttpResponse = runBlocking {
        client.get(url(path)) { password?.let { p -> header("X-QA-Password", p) } }
    }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.obj(): JsonObject = json.parseToJsonElement(text()).jsonObject
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    /** Submits a question as a phone would and returns its id. */
    private fun submitQuestion(text: String): String =
        post("/api/qa/submit", """{"text":"$text","name":""}""").obj().str("id")

    // ── Editing ─────────────────────────────────────────────────────────────────

    @Test
    fun `an approved edit replaces the question text`() {
        val qa = openSession()
        val id = submitQuestion("waht time is the servce")
        val prompts = playOperator(allow = true)

        val response = post("/api/qa/questions/$id/edit", """{"text":"what time is the service","name":""}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("what time is the service", qa.findQuestion(id)?.text)
        assertEquals("edit", prompts.single().action)
        assertEquals(
            "what time is the service",
            prompts.single().text,
            "the operator decides from the proposed text, so it has to be the one shown",
        )
    }

    @Test
    fun `a refused edit leaves the question alone`() {
        val qa = openSession()
        val id = submitQuestion("original wording")
        playOperator(allow = false)

        val response = post("/api/qa/questions/$id/edit", """{"text":"rewritten"}""")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("original wording", qa.findQuestion(id)?.text, "a refusal must change nothing")
    }

    @Test
    fun `editing a question that does not exist is a not-found`() {
        openSession()
        playOperator()

        val response = post("/api/qa/questions/nope/edit", """{"text":"anything"}""")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `a malformed edit is refused before the operator is bothered`() {
        openSession()
        val prompts = playOperator()

        val response = post("/api/qa/questions/any/edit", "not json")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, prompts.size, "there is nothing to ask about")
    }

    // ── Denying ─────────────────────────────────────────────────────────────────

    @Test
    fun `an approved deny moves the question out of the queue`() {
        val qa = openSession()
        val id = submitQuestion("off topic")
        val prompts = playOperator(allow = true)

        val response = post("/api/qa/questions/$id/deny")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(QuestionStatus.DENIED, qa.findQuestion(id)?.status)
        assertEquals("deny", prompts.single().action)
        assertEquals("off topic", prompts.single().text, "the prompt has to name the question")
    }

    @Test
    fun `a refused deny leaves the question pending`() {
        val qa = openSession()
        val id = submitQuestion("still waiting")
        playOperator(allow = false)

        val response = post("/api/qa/questions/$id/deny")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(QuestionStatus.PENDING, qa.findQuestion(id)?.status)
    }

    // ── Marking done ────────────────────────────────────────────────────────────

    @Test
    fun `an approved done marks the question answered`() {
        val qa = openSession()
        val id = submitQuestion("already answered")
        qa.approveQuestion(id)
        playOperator(allow = true)

        val response = post("/api/qa/questions/$id/done")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(QuestionStatus.DONE, qa.findQuestion(id)?.status)
    }

    @Test
    fun `a refused done leaves it approved`() {
        val qa = openSession()
        val id = submitQuestion("still on the list")
        qa.approveQuestion(id)
        playOperator(allow = false)

        val response = post("/api/qa/questions/$id/done")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(QuestionStatus.APPROVED, qa.findQuestion(id)?.status)
    }

    @Test
    fun `marking a question done that does not exist is a not-found`() {
        openSession()
        playOperator()

        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/nope/done").status)
    }

    // ── Displaying ──────────────────────────────────────────────────────────────

    @Test
    fun `an approved display puts the question on the projector`() {
        val qa = openSession()
        val id = submitQuestion("who wrote Hebrews")
        qa.approveQuestion(id)
        playOperator(allow = true)

        val response = post("/api/qa/questions/$id/display")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(id, qa.displayedQuestion?.id)
    }

    @Test
    fun `a refused display leaves the projector alone`() {
        val qa = openSession()
        val id = submitQuestion("not yet")
        qa.approveQuestion(id)
        playOperator(allow = false)

        val response = post("/api/qa/questions/$id/display")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(qa.displayedQuestion, "a refusal must not reach the screen")
    }

    @Test
    fun `an unapproved question cannot be displayed`() {
        val qa = openSession()
        val id = submitQuestion("straight to the screen")
        playOperator(allow = true)

        val response = post("/api/qa/questions/$id/display")

        // The operator said yes, and it still must not go up: moderation is not optional.
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertNull(qa.displayedQuestion)
    }

    // ── Deleting ────────────────────────────────────────────────────────────────

    @Test
    fun `an approved delete removes the question`() {
        val qa = openSession()
        val id = submitQuestion("delete me")
        val prompts = playOperator(allow = true)

        val response = delete("/api/qa/questions/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(qa.findQuestion(id))
        assertEquals("delete", prompts.single().action)
    }

    @Test
    fun `a refused delete keeps the question`() {
        val qa = openSession()
        val id = submitQuestion("keep me")
        playOperator(allow = false)

        val response = delete("/api/qa/questions/$id")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("keep me", qa.findQuestion(id)?.text)
    }

    @Test
    fun `deleting a question that does not exist is a not-found`() {
        openSession()
        playOperator()

        assertEquals(HttpStatusCode.NotFound, delete("/api/qa/questions/nope").status)
    }

    // ── Adding one from a phone ─────────────────────────────────────────────────

    @Test
    fun `an approved add returns the new question`() {
        val qa = openSession()
        val prompts = playOperator(allow = true)

        val response = post("/api/qa/add", """{"text":"a question the moderator typed"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("a question the moderator typed", response.obj().str("text"))
        assertEquals(1, qa.questions.size)
        assertEquals("add", prompts.single().action)
    }

    @Test
    fun `a refused add adds nothing`() {
        val qa = openSession()
        playOperator(allow = false)

        val response = post("/api/qa/add", """{"text":"not wanted"}""")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, qa.questions.size)
    }

    @Test
    fun `a malformed add is refused before the operator is bothered`() {
        openSession()
        val prompts = playOperator()

        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/add", "{").status)
        assertEquals(0, prompts.size)
    }

    @Test
    fun `adding with no session open fails rather than inventing one`() {
        // No openSession() — qaManager is null.
        playOperator(allow = true)

        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/add", """{"text":"orphan"}""").status)
    }

    // ── Clearing the display and the session ────────────────────────────────────

    @Test
    fun `an approved clear-display takes the question off the projector`() {
        val qa = openSession()
        val id = submitQuestion("on screen now")
        qa.approveQuestion(id)
        qa.displayQuestion(id)
        playOperator(allow = true)

        val response = post("/api/qa/clear-display")

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(qa.displayedQuestion)
    }

    @Test
    fun `a refused clear-display leaves it up`() {
        val qa = openSession()
        val id = submitQuestion("stays up")
        qa.approveQuestion(id)
        qa.displayQuestion(id)
        playOperator(allow = false)

        val response = post("/api/qa/clear-display")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(id, qa.displayedQuestion?.id)
    }

    @Test
    fun `clear-all empties the session without asking the operator`() {
        val qa = openSession()
        submitQuestion("one")
        val prompts = playOperator()

        val response = post("/api/qa/clear-all")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, qa.questions.size)
        // Deliberately ungated, unlike every other action here: the password is the only guard.
        assertEquals(0, prompts.size)
    }

    // ── With no session manager at all ──────────────────────────────────────────

    /**
     * Every moderation route reaches for `server.qaManager` with a safe call and falls back.
     *
     * That manager is null until the operator opens a session, and it is set back to null when one
     * is closed — so a moderator phone still holding the admin page from the last service posts
     * into exactly this state. Each route has to answer it rather than throw: an unanswered request
     * leaves the phone spinning on a page the operator cannot see.
     */
    @Test
    fun `moderating with no session open is a not-found rather than a crash`() {
        playOperator(allow = true)

        // resetState leaves qaManager null; no openSession() here on purpose.
        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/anything/approve").status)
        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/anything/deny").status)
        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/anything/done").status)
        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/anything/display").status)
        assertEquals(HttpStatusCode.NotFound, delete("/api/qa/questions/anything").status)
        assertEquals(
            HttpStatusCode.NotFound,
            post("/api/qa/questions/anything/edit", """{"text":"rewritten"}""").status,
        )
    }

    @Test
    fun `the prompt for a question that is not there carries no text`() {
        // The lookup that fills the prompt is a safe call too. With no manager there is nothing to
        // name, and the operator is shown an empty string rather than the word "null".
        val prompts = playOperator(allow = true)

        post("/api/qa/questions/anything/deny")

        assertEquals("", prompts.single().text)
        assertEquals("anything", prompts.single().questionId)
    }

    @Test
    fun `adding a question with no session open is refused`() {
        // Unlike the others this is a bad request, not a not-found: the question was never going to
        // exist, so there is nothing to fail to find.
        playOperator(allow = true)

        val response = post("/api/qa/add", """{"text":"from the desk","name":""}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `clearing with no session open succeeds and changes nothing`() {
        // These two are the exception: there is nothing to clear, and reporting a failure would
        // make the admin page show an error for having asked to tidy an already-empty screen.
        playOperator(allow = true)

        assertEquals(HttpStatusCode.OK, post("/api/qa/clear-display").status)
        assertEquals(HttpStatusCode.OK, post("/api/qa/clear-all").status)
    }

    @Test
    fun `the queue is an empty list rather than an error with no session open`() {
        // The moderator's page polls this on a timer. Between services it must render as empty.
        assertEquals("[]", get("/api/qa/questions").text())
    }
    // ── The password guard ──────────────────────────────────────────────────────

    @Test
    fun `moderation is closed to a phone without the password`() {
        val qa = openSession()
        val id = submitQuestion("guarded")
        server.qaAdminPassword = "let-me-in"
        val prompts = playOperator()

        assertEquals(HttpStatusCode.Unauthorized, post("/api/qa/questions/$id/deny").status)
        assertEquals(HttpStatusCode.Unauthorized, delete("/api/qa/questions/$id").status)
        assertEquals(HttpStatusCode.Unauthorized, post("/api/qa/clear-all").status)

        assertEquals(QuestionStatus.PENDING, qa.findQuestion(id)?.status)
        assertEquals(1, qa.questions.size)
        assertEquals(0, prompts.size, "the operator should never have been asked")
    }

    @Test
    fun `moderation opens with the right password`() {
        val qa = openSession()
        val id = submitQuestion("guarded")
        server.qaAdminPassword = "let-me-in"
        playOperator(allow = true)

        val response = post("/api/qa/questions/$id/deny", password = "let-me-in")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(QuestionStatus.DENIED, qa.findQuestion(id)?.status)
    }
}
