package org.churchpresenter.app.churchpresenter.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.qa.QAManager
import java.io.File
import org.junit.AfterClass
import org.junit.BeforeClass
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort
import org.churchpresenter.companionserver.CompanionServer
import org.churchpresenter.companionserver.PendingConnectionRequest

/**
 * The audience Q&A HTTP API, driven over real HTTP against a running server.
 *
 * This is the only part of the app a stranger can reach. Everyone in the room scans a QR code and
 * posts to it from their own phone, and with the tunnel enabled it is reachable from the open
 * internet — so what these pin is mostly refusal: submitting into a closed session, voting when
 * voting is off, voting for a question the moderator has not approved, and reading the moderation
 * queue without the password. Each of those is a way for something to reach the screen that the
 * operator never agreed to.
 *
 * The one number that has to be right in the other direction is the vote count, because it decides
 * the order questions get asked in.
 *
 * The server builds its own Netty instance rather than exposing a Ktor module, so — as in
 * [CompanionServerTest] — it is started on a free port and driven with a real client, once for the
 * whole class.
 */
class CompanionServerQaTest {

    private lateinit var client: HttpClient

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The server is started ONCE for the class, not per test.
     *
     * Binding a Netty instance costs about a second, which on a class this size is half a minute of
     * every future test run. Nothing here needs a fresh server — the Q&A state all hangs off a
     * [QAManager] and three flags on the server object, and [resetState] puts those back before each
     * test.
     *
     * `user.home` is deliberately NOT swapped. It already points at `build/test-home` for the whole
     * test JVM, and `CompanionServer` writes through `InstanceLinkLogger`, which resolves its log
     * path lazily and keeps it for the life of the JVM — so a swap here would make the logger latch
     * onto a directory this class then deletes, breaking whichever later test reads that log. The
     * only file this class needs gone is the Q&A state, which [resetState] removes per test.
     */
    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        /** The state file `QAManager` restores itself from, under the test JVM's own home. */
        private val qaStateFile
            get() = File(System.getProperty("user.home"), ".churchpresenter/qa_state.json")

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_620))
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
            qaStateFile.delete()
        }
    }

    @BeforeTest
    fun resetState() {
        client = HttpClient(CIO)
        // QAManager restores the previous session from disk when it is constructed, so the file
        // has to go too — otherwise each test starts holding the questions the last one asked.
        qaStateFile.delete()
        server.qaStore = null
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

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    /**
     * A manager with an open session — the state every action but the refusals requires.
     *
     * The cooldown is switched off: every request here comes from 127.0.0.1, so the real one would
     * rate-limit the second submission of any test that needs two questions. The test that is
     * ABOUT rate limiting sets its own.
     */
    private fun openSession(): QAManager = QAManager().also {
        it.toggleSession()
        server.qaStore = it
        server.qaCooldownSeconds = 0
    }

    /**
     * Answers the operator prompt that every remote moderation action waits on, with [allow].
     *
     * Moderating from a phone is a remote action like any other: the endpoint suspends until the
     * desktop resolves it. Without something playing the operator, those requests never respond.
     * Returns the prompts that were answered, so a test can assert what the desktop was shown.
     */
    private fun playOperator(allow: Boolean = true): MutableList<CompanionServer.PendingQAAdminRequest> {
        val seen = mutableListOf<CompanionServer.PendingQAAdminRequest>()
        operatorScope = CoroutineScope(Dispatchers.IO).also { scope ->
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

    private var operatorScope: CoroutineScope? = null

    private fun get(path: String, password: String? = null): HttpResponse = runBlocking {
        client.get(url(path)) { password?.let { p -> header("X-QA-Password", p) } }
    }

    private fun post(
        path: String,
        body: String = "",
        password: String? = null,
        deviceId: String? = null,
    ): HttpResponse = runBlocking {
        client.post(url(path)) {
            password?.let { p -> header("X-QA-Password", p) }
            deviceId?.let { d -> header(Constants.HEADER_DEVICE_ID, d) }
            setBody(body)
        }
    }

    /**
     * Answers the *connection* prompt raised by `/api/qa/auth`, with [allow].
     *
     * A different flow from [playOperator]: that one answers a moderation action, this one answers
     * the one-off handshake a device performs before it can moderate at all. Shares the same scope
     * so a test can play both, and waits on the subscription for the same reason.
     */
    private fun playConnectOperator(allow: Boolean = true): MutableList<PendingConnectionRequest> {
        val seen = mutableListOf<PendingConnectionRequest>()
        val scope = operatorScope ?: CoroutineScope(Dispatchers.IO).also { operatorScope = it }
        scope.launch {
            server.onQaAdminConnect.collect { pending ->
                seen.add(pending)
                pending.decision.complete(allow)
            }
        }
        runBlocking {
            withTimeout(5_000) { server.onQaAdminConnect.subscriptionCount.first { it > 0 } }
        }
        return seen
    }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.obj(): JsonObject = json.parseToJsonElement(text()).jsonObject
    private fun HttpResponse.array(): JsonArray = json.parseToJsonElement(text()).jsonArray
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.content.toInt()

    private fun submit(text: String, name: String = "") =
        post("/api/qa/submit", """{"text":"$text","name":"$name"}""")

    // ── What the phone asks first ───────────────────────────────────────────────

    @Test
    fun `the status endpoint answers before any session exists`() {
        // The page loads before the operator has opened a session; it must be told so, not error.
        val status = get("/api/qa/status").obj()

        assertEquals("false", status.getValue("sessionActive").jsonPrimitive.content)
        assertEquals("", status.str("displayedQuestionId"))
    }

    @Test
    fun `the status endpoint reports the session and its rules`() {
        openSession()
        server.qaCooldownSeconds = 45
        server.qaVotingEnabled = true

        val status = get("/api/qa/status").obj()

        assertEquals("true", status.getValue("sessionActive").jsonPrimitive.content)
        assertEquals(45, status.int("cooldownSeconds"), "the phone counts its own cooldown from this")
        assertEquals("true", status.getValue("votingEnabled").jsonPrimitive.content)
    }

    // ── Submitting ──────────────────────────────────────────────────────────────

    @Test
    fun `a question cannot be submitted into a closed session`() {
        assertEquals(
            HttpStatusCode.Forbidden,
            submit("Where is the nursery?").status,
            "a closed session is the operator's only way to stop questions arriving",
        )
    }

    @Test
    fun `a question submitted into an open session comes back with an id`() {
        val qa = openSession()

        val response = submit("Where is the nursery?", name = "Sam")

        assertEquals(HttpStatusCode.OK, response.status)
        val question = response.obj()
        assertEquals("Where is the nursery?", question.str("text"))
        assertEquals("Sam", question.str("submitterName"))
        assertTrue(question.str("id").isNotBlank(), "the phone tracks its own question by this id")
        assertEquals(1, qa.questions.size)
    }

    @Test
    fun `a submitted question waits for moderation rather than going straight up`() {
        val qa = openSession()

        submit("Where is the nursery?")

        assertEquals(
            "PENDING",
            qa.questions.single().status.name,
            "anything else would put an unread question from a stranger on the wall",
        )
    }

    @Test
    fun `an empty question is refused`() {
        openSession()

        assertEquals(HttpStatusCode.BadRequest, submit("   ").status, "a blank row in the queue helps nobody")
    }

    @Test
    fun `a malformed submission is refused rather than crashing the endpoint`() {
        openSession()

        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/submit", "this is not json").status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/submit", """{"name":"Sam"}""").status)
    }

    @Test
    fun `a second question from the same phone is rate limited`() {
        openSession()
        server.qaCooldownSeconds = 3_600

        assertEquals(HttpStatusCode.OK, submit("First question").status)
        val second = submit("Second question")

        assertEquals(
            HttpStatusCode.TooManyRequests,
            second.status,
            "without this one person can flood the moderation queue faster than it can be read",
        )
        assertTrue("wait" in second.text().lowercase(), "and must be told why: ${second.text()}")
    }

    @Test
    fun `an anonymous question is accepted`() {
        openSession()

        val question = submit("Where is the nursery?").obj()

        assertEquals("", question.str("submitterName"), "a name is optional — asking anonymously is the point")
    }

    // ── Voting ──────────────────────────────────────────────────────────────────

    @Test
    fun `voting is refused entirely when it is switched off`() {
        openSession()
        server.qaVotingEnabled = false

        assertEquals(HttpStatusCode.Forbidden, get("/api/qa/approved").status)
        assertEquals(HttpStatusCode.Forbidden, post("/api/qa/vote", """{"questionId":"x"}""").status)
    }

    @Test
    fun `only approved questions are offered for voting`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        submit("Pending question")
        submit("Approved question").also { qa.approveQuestion(it.obj().str("id")) }

        val offered = get("/api/qa/approved").array()

        assertEquals(1, offered.size, "a question the moderator has not read must not be visible to the room")
        assertEquals("Approved question", offered.single().jsonObject.str("text"))
    }

    @Test
    fun `a vote is counted`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        val id = submit("Approved question").obj().str("id").also { qa.approveQuestion(it) }

        val response = post("/api/qa/vote", """{"questionId":"$id","direction":"up"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("up", response.obj().str("voted"))
        assertEquals(
            1,
            get("/api/qa/approved").array().single().jsonObject.int("voteCount"),
            "the count decides the order questions are asked in",
        )
    }

    @Test
    fun `a phone cannot vote for the same question twice`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        val id = submit("Approved question").obj().str("id").also { qa.approveQuestion(it) }

        repeat(3) { post("/api/qa/vote", """{"questionId":"$id","direction":"up"}""") }

        assertEquals(
            1,
            get("/api/qa/approved").array().single().jsonObject.int("voteCount"),
            "one phone holding the button would otherwise decide the whole running order",
        )
    }

    @Test
    fun `a question that is not approved cannot be voted for`() {
        openSession()
        server.qaVotingEnabled = true
        val id = submit("Pending question").obj().str("id")

        assertEquals(
            HttpStatusCode.Forbidden,
            post("/api/qa/vote", """{"questionId":"$id"}""").status,
            "votes on unread questions would rank the queue before anyone has moderated it",
        )
    }

    @Test
    fun `voting for a question that does not exist is a not-found`() {
        openSession()
        server.qaVotingEnabled = true

        assertEquals(HttpStatusCode.NotFound, post("/api/qa/vote", """{"questionId":"no-such-question"}""").status)
    }

    @Test
    fun `a malformed vote is refused`() {
        openSession()
        server.qaVotingEnabled = true

        assertEquals(HttpStatusCode.BadRequest, post("/api/qa/vote", "not json at all").status)
    }

    @Test
    fun `the vote list is empty rather than an error when no session is open`() {
        server.qaStore = QAManager()
        server.qaVotingEnabled = true

        val response = get("/api/qa/approved")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.array().isEmpty(), "the voting page has to load between sessions too")
    }

    // ── Behind a proxy ──────────────────────────────────────────────────────────

    @Test
    fun `two phones behind the same tunnel are told apart by their forwarded address`() {
        openSession()
        server.qaCooldownSeconds = 3_600

        val first = runBlocking {
            client.post(url("/api/qa/submit")) {
                header("CF-Connecting-IP", "203.0.113.10")
                setBody("""{"text":"First phone"}""")
            }
        }
        val second = runBlocking {
            client.post(url("/api/qa/submit")) {
                header("CF-Connecting-IP", "203.0.113.11")
                setBody("""{"text":"Second phone"}""")
            }
        }

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(
            HttpStatusCode.OK,
            second.status,
            "with the tunnel on every request shares one socket address; the cooldown must key on the forwarded one",
        )
    }

    @Test
    fun `a forwarded chain is keyed on the phone at its head`() {
        openSession()
        server.qaCooldownSeconds = 3_600

        val first = runBlocking {
            client.post(url("/api/qa/submit")) {
                header("X-Forwarded-For", "203.0.113.20, 70.41.3.18")
                setBody("""{"text":"First phone"}""")
            }
        }
        val again = runBlocking {
            client.post(url("/api/qa/submit")) {
                header("X-Forwarded-For", "203.0.113.20, 198.51.100.7")
                setBody("""{"text":"Same phone again"}""")
            }
        }

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(
            HttpStatusCode.TooManyRequests,
            again.status,
            "only the first hop identifies the phone; a changed proxy must not reset its cooldown",
        )
    }

    @Test
    fun `a vote is attributed to the forwarded address rather than the tunnel`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        val id = submit("Where is the nursery?").obj().str("id")
        qa.approveQuestion(id)

        val voted = runBlocking {
            client.post(url("/api/qa/vote")) {
                header("CF-Connecting-IP", "203.0.113.30")
                setBody("""{"questionId":"$id","direction":"up"}""")
            }
        }

        assertEquals(HttpStatusCode.OK, voted.status)
        assertEquals("up", qa.getVoteDirection(id, "203.0.113.30"))
        assertNull(qa.getVoteDirection(id, "127.0.0.1"), "the vote must not be recorded against the tunnel")
    }

    @Test
    fun `the approved list marks which way this phone already voted`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        val id = submit("Where is the nursery?").obj().str("id")
        qa.approveQuestion(id)
        qa.voteForQuestion(id, "203.0.113.40", "down")

        val mine = runBlocking {
            client.get(url("/api/qa/approved")) { header("CF-Connecting-IP", "203.0.113.40") }
        }.array()
        val theirs = runBlocking {
            client.get(url("/api/qa/approved")) { header("CF-Connecting-IP", "203.0.113.41") }
        }.array()

        assertEquals("down", mine[0].jsonObject.str("voted"))
        assertTrue(theirs[0].jsonObject.getValue("voted") is JsonNull)
    }

    @Test
    fun `a question with quotes and newlines survives the hand-built voting json`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        val id = submit("plain").obj().str("id")
        qa.editQuestion(id, "He said \"hi\"\nthen left")
        qa.approveQuestion(id)

        val listed = get("/api/qa/approved").array()

        assertEquals("He said \"hi\"\nthen left", listed[0].jsonObject.str("text"))
    }

    @Test
    fun `a downvote is recorded as one, and anything else counts as up`() {
        // The direction arrives as free text from the phone. Only "down" means down; everything
        // else — a typo, an older client sending nothing, a crafted value — has to fall to "up"
        // rather than be stored verbatim and then fail to match on the next read.
        val qa = openSession()
        server.qaVotingEnabled = true
        val down = submit("Downvote me").obj().str("id")
        val odd = submit("Odd direction").obj().str("id")
        qa.approveQuestion(down)
        qa.approveQuestion(odd)

        val downResponse = post("/api/qa/vote", """{"questionId":"$down","direction":"down"}""")
        val oddResponse = post("/api/qa/vote", """{"questionId":"$odd","direction":"sideways"}""")

        assertEquals(HttpStatusCode.OK, downResponse.status)
        assertEquals("down", downResponse.obj().str("voted"))
        assertEquals("down", qa.getVoteDirection(down, "127.0.0.1"))
        assertEquals(HttpStatusCode.OK, oddResponse.status)
        assertEquals("up", qa.getVoteDirection(odd, "127.0.0.1"), "an unrecognised direction is an upvote")
    }

    @Test
    fun `a vote behind a plain forwarding proxy is keyed on the forwarded address`() {
        // The tunnel sends CF-Connecting-IP, but a church running its own reverse proxy sends only
        // X-Forwarded-For. Both have to identify the phone, or one deployment lets a single vote
        // count for the whole room.
        val qa = openSession()
        server.qaVotingEnabled = true
        val id = submit("Where is the nursery?").obj().str("id")
        qa.approveQuestion(id)

        val voted = runBlocking {
            client.post(url("/api/qa/vote")) {
                header("X-Forwarded-For", "203.0.113.50, 198.51.100.7")
                setBody("""{"questionId":"$id","direction":"up"}""")
            }
        }

        assertEquals(HttpStatusCode.OK, voted.status)
        assertEquals("up", qa.getVoteDirection(id, "203.0.113.50"))
        assertNull(qa.getVoteDirection(id, "127.0.0.1"), "the vote must not be recorded against the proxy")
    }

    @Test
    fun `the approved list behind a plain forwarding proxy marks this phone's vote`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        val id = submit("Where is the nursery?").obj().str("id")
        qa.approveQuestion(id)
        qa.voteForQuestion(id, "203.0.113.60", "up")

        val mine = runBlocking {
            client.get(url("/api/qa/approved")) {
                header("X-Forwarded-For", "203.0.113.60, 198.51.100.7")
            }
        }.array()

        assertEquals("up", mine.single().jsonObject.getValue("voted").jsonPrimitive.content)
    }
    // ── The pages a phone loads ─────────────────────────────────────────────────

    @Test
    fun `the submission page is served as html`() {
        val page = get("/qa")

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.text().contains("<html", ignoreCase = true))
    }

    @Test
    fun `the admin page is served as html`() {
        val page = get("/qa/admin")

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.text().contains("<html", ignoreCase = true))
    }

    @Test
    fun `the voting page is served as html`() {
        val page = get("/qa/vote")

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.text().contains("<html", ignoreCase = true))
    }

    // ── The moderation queue ────────────────────────────────────────────────────

    @Test
    fun `the queue is readable when no password has been set`() {
        val qa = openSession()
        server.qaAdminPassword = ""
        submit("Where is the nursery?")

        val queue = get("/api/qa/questions").array()

        assertEquals(1, queue.size)
        assertEquals(qa.questions.single().id, queue.single().jsonObject.str("id"))
    }

    @Test
    fun `the queue is closed to anyone without the password`() {
        openSession()
        server.qaAdminPassword = "let-me-in"
        submit("Where is the nursery?")

        val response = get("/api/qa/questions")

        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status,
            "the queue holds unmoderated text from strangers and is reachable over the tunnel",
        )
    }

    @Test
    fun `the wrong password does not open the queue`() {
        openSession()
        server.qaAdminPassword = "let-me-in"

        assertEquals(HttpStatusCode.Unauthorized, get("/api/qa/questions", password = "let-me-inn").status)
    }

    @Test
    fun `the right password opens the queue`() {
        openSession()
        server.qaAdminPassword = "let-me-in"
        submit("Where is the nursery?")

        val response = get("/api/qa/questions", password = "let-me-in")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, response.array().size)
    }

    @Test
    fun `the queue can be filtered by status`() {
        val qa = openSession()
        submit("Pending question")
        submit("Approved question").also { qa.approveQuestion(it.obj().str("id")) }

        val approved = get("/api/qa/questions?status=approved").array()
        val pending = get("/api/qa/questions?status=pending").array()

        assertEquals(listOf("Approved question"), approved.map { it.jsonObject.str("text") })
        assertEquals(listOf("Pending question"), pending.map { it.jsonObject.str("text") })
    }

    @Test
    fun `an unknown status filter returns everything rather than nothing`() {
        openSession()
        submit("Where is the nursery?")

        assertEquals(
            1,
            get("/api/qa/questions?status=not-a-status").array().size,
            "a typo in a query parameter must not read as 'no questions have been asked'",
        )
    }

    @Test
    fun `approving a question through the api moves it into the voting list`() {
        val qa = openSession()
        server.qaVotingEnabled = true
        playOperator(allow = true)
        val id = submit("Where is the nursery?").obj().str("id")

        val response = post("/api/qa/questions/$id/approve")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("APPROVED", qa.findQuestion(id)?.status?.name)
        assertEquals(1, get("/api/qa/approved").array().size)
    }

    @Test
    fun `approving a question that is not there is a not-found`() {
        openSession()
        playOperator(allow = true)

        assertEquals(HttpStatusCode.NotFound, post("/api/qa/questions/no-such-question/approve").status)
    }

    @Test
    fun `a remote moderation action asks the operator first`() {
        val qa = openSession()
        val prompts = playOperator(allow = true)
        val id = submit("Where is the nursery?").obj().str("id")

        post("/api/qa/questions/$id/approve")

        val prompt = prompts.single()
        assertEquals("approve", prompt.action, "the desktop prompt has to say what is being asked for")
        assertEquals(id, prompt.questionId)
        assertEquals("Where is the nursery?", prompt.text, "and show the text, or the operator is approving blind")
        assertEquals("APPROVED", qa.findQuestion(id)?.status?.name)
    }

    @Test
    fun `an operator who refuses leaves the question alone`() {
        val qa = openSession()
        playOperator(allow = false)
        val id = submit("Where is the nursery?").obj().str("id")

        val response = post("/api/qa/questions/$id/approve")

        assertEquals(HttpStatusCode.Forbidden, response.status, "the phone has to be told, not left waiting")
        assertEquals("PENDING", qa.findQuestion(id)?.status?.name)
    }

    @Test
    fun `moderating without the password is refused`() {
        val qa = openSession()
        server.qaAdminPassword = "let-me-in"
        val id = submit("Where is the nursery?").obj().str("id")

        val response = post("/api/qa/questions/$id/approve")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("PENDING", qa.findQuestion(id)?.status?.name, "and the question must be left as it was")
    }

    // ── The admin connection handshake ──────────────────────────────────────────

    @Test
    fun `an approved device is let into the admin panel`() {
        server.qaAdminPassword = "let-me-in"
        playConnectOperator(allow = true)

        val response = post("/api/qa/auth", password = "let-me-in", deviceId = "moderator-phone")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("true", response.obj().str("ok"))
    }

    @Test
    fun `a denied device is told so, not left waiting`() {
        server.qaAdminPassword = "let-me-in"
        playConnectOperator(allow = false)

        val response = post("/api/qa/auth", password = "let-me-in", deviceId = "someones-phone")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("connection denied", response.obj().str("error"))
    }

    @Test
    fun `the prompt names the device asking to moderate`() {
        // The operator is approving a device, not a request — the id is the only thing on the
        // prompt that distinguishes "the moderator's phone" from anyone else on the wifi.
        server.qaAdminPassword = "let-me-in"
        val prompts = playConnectOperator()

        post("/api/qa/auth", password = "let-me-in", deviceId = "moderator-phone")

        assertEquals("moderator-phone", prompts.single().clientId)
    }

    @Test
    fun `a wrong password never raises a prompt on the desktop`() {
        // The password gate runs first for a reason. If a bad password still asked the operator,
        // anyone on the church wifi could put a dialog on the desktop during a service, repeatedly,
        // without ever knowing the password.
        server.qaAdminPassword = "let-me-in"
        val prompts = playConnectOperator()

        val response = post("/api/qa/auth", password = "guess", deviceId = "someones-phone")

        // The 401 is the positive signal that the route ran to completion: the prompt would have
        // had to be raised before the response, and was not. Nothing here waits on a duration.
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(prompts.isEmpty(), "the operator must not be shown a device that failed the gate")
    }

    @Test
    fun `with no password set the operator is still asked`() {
        // An unset password is the default, and it makes `checkQaAdmin` a no-op — so on this path
        // the operator's approval is the *only* thing standing between a stranger's phone and the
        // moderation queue.
        server.qaAdminPassword = ""
        val prompts = playConnectOperator()

        val response = post("/api/qa/auth", deviceId = "a-stranger")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("a-stranger", prompts.single().clientId, "approval is the whole gate here")
    }

    @Test
    fun `a device that sends no id is still put to the operator`() {
        // Older clients send no device header. Refusing outright would lock them out; the prompt
        // just cannot name them, and the operator decides on that basis.
        server.qaAdminPassword = ""
        val prompts = playConnectOperator()

        val response = post("/api/qa/auth")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", prompts.single().clientId)
    }
}
