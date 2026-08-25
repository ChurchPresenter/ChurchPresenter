package org.churchpresenter.stt

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two things [STTManager] pulls over REST rather than receiving on the socket: the
 * highlighted-word list, and the Help Dev capture of the live session's `.db`.
 *
 * Both run against a real loopback `HttpServer` on an ephemeral port. The point of a real server
 * rather than a stubbed client is that the failure cases are the interesting ones and they are HTTP
 * facts — a non-200, a body missing the field, a server that is not there at all — and every one of
 * them has to end as "leave what is on screen alone", never as a thrown exception on a background
 * thread during a service.
 *
 * The `.db` capture is what turns a live service into replayable training data, and it has already
 * been wrong in a way that only showed up afterwards: a snapshot ending five minutes early turned
 * seven references the engine really did detect into replay "misses". So the two things asserted
 * hardest are that the downloaded bytes land under the plain file name (the server names it with a
 * path) and that a failed download leaves neither a partial file nor a `.tmp` behind for the next
 * run to trust.
 *
 * `user.home` is swapped per test — the snapshot resolves its directory from it on every call.
 *
 * Not covered here: the capture loop's 60-second tick and its `helpDevModeEnabled == false` gate.
 * The first iteration runs immediately, so the "on" path is a positive signal; the "off" path has no
 * signal at all short of waiting out the tick, which is over the suite's 1s bar.
 */
class STTManagerCaptureTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var tempHome: File
    private var realHome: String? = null

    private val created = mutableListOf<STTManager>()

    /** Every path the manager asked for, so a test can assert what was requested rather than guess. */
    private val requested = ConcurrentLinkedQueue<String>()

    /** What each endpoint should answer with, per test. */
    private var wordsResponse: Pair<Int, String> = 200 to """{"success":true,"enabled":true,"words":[]}"""
    private var statusResponse: Pair<Int, String> = 200 to """{"state":{"db_name":"session.db"}}"""
    private var downloadResponse: Pair<Int, ByteArray> = 200 to ByteArray(0)

    @BeforeTest
    fun startServer() {
        // :composeApp latched InstanceLinkLogger here, because it resolves its directory once per
        // JVM and the swap below would have stranded it in a deleted temp dir. That class is in
        // :companion-server and is not on this module's classpath at all, so there is nothing to
        // latch — this module's suite reaches no once-per-JVM home resolver.

        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-stt-capture-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/api/word-highlighting/words") { exchange ->
            requested.add(exchange.requestURI.toString())
            respond(exchange, wordsResponse.first, wordsResponse.second.toByteArray())
        }
        server.createContext("/api/transcription/status") { exchange ->
            requested.add(exchange.requestURI.toString())
            respond(exchange, statusResponse.first, statusResponse.second.toByteArray())
        }
        server.createContext("/api/file-manager/download") { exchange ->
            requested.add(exchange.requestURI.toString())
            respond(exchange, downloadResponse.first, downloadResponse.second)
        }
        server.start()
        baseUrl = "http://${InetAddress.getLoopbackAddress().hostAddress}:${server.address.port}"
    }

    @AfterTest
    fun stopServer() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        server.stop(0)
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun respond(exchange: HttpExchange, status: Int, body: ByteArray) {
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun stt(): STTManager = STTManager().also { created.add(it) }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private val logDir: File get() = File(tempHome, ".churchpresenter/bible-stt-logs")

    private fun snapshots(): List<String> =
        logDir.listFiles().orEmpty().map { it.name }.sorted()

    /**
     * A loopback port with nothing bound to it, so a connection is refused immediately.
     *
     * It has to be a genuinely closed socket. An `HttpServer` that was created and stopped without
     * ever being started still holds its listening socket, so the kernel completes the handshake
     * into a backlog nobody serves — the client then waits forever rather than being refused, and
     * neither `HttpClient.newHttpClient()` nor this call site sets a timeout to end it.
     */
    private fun deadUrl(): String {
        val port = java.net.ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        return "http://${InetAddress.getLoopbackAddress().hostAddress}:$port"
    }

    // ── Highlighted words ───────────────────────────────────────────────────────

    @Test
    fun `the highlighted word list is pulled when the socket comes up`() {
        wordsResponse = 200 to """
            {"success":true,"enabled":true,"words":[
              {"word":"grace","color":"#ffff00","case_sensitive":true,"is_regex":false},
              {"word":"faith.*","color":"#00ff00","case_sensitive":false,"is_regex":true}
            ]}
        """.trimIndent()
        val stt = stt()

        stt.fetchWordHighlighting(baseUrl)

        awaitUntil("both words to be applied") { stt.highlightedWords.size == 2 }
        assertTrue(stt.wordHighlightingEnabled.value)
        val grace = stt.highlightedWords.single { it.word == "grace" }
        assertEquals("#ffff00", grace.color)
        assertTrue(grace.caseSensitive)
        assertFalse(grace.isRegex)
        assertTrue(stt.highlightedWords.single { it.word == "faith.*" }.isRegex)
    }

    @Test
    fun `words in a colour group the operator switched off are left out`() {
        // The STT server keeps sending every word and names the groups that are off, so filtering
        // here is what actually stops them being highlighted on screen.
        wordsResponse = 200 to """
            {"success":true,"enabled":true,"disabled_colors":["#00ff00"],"words":[
              {"word":"shown","color":"#ffff00"},
              {"word":"hidden","color":"#00ff00"}
            ]}
        """.trimIndent()
        val stt = stt()

        stt.fetchWordHighlighting(baseUrl)

        awaitUntil("the surviving word") { stt.highlightedWords.size == 1 }
        assertEquals("shown", stt.highlightedWords.single().word)
    }

    @Test
    fun `highlighting switched off server-side comes through as off`() {
        wordsResponse = 200 to """{"success":true,"enabled":false,"words":[{"word":"grace","color":"#ffff00"}]}"""
        val stt = stt()

        stt.fetchWordHighlighting(baseUrl)

        awaitUntil("the word list to arrive") { stt.highlightedWords.isNotEmpty() }
        assertFalse(stt.wordHighlightingEnabled.value, "the words are known but must not be painted")
    }

    @Test
    fun `a word with no colour of its own gets the default one`() {
        wordsResponse = 200 to """{"success":true,"words":[{"word":"grace"}]}"""
        val stt = stt()

        stt.fetchWordHighlighting(baseUrl)

        awaitUntil("the word to arrive") { stt.highlightedWords.isNotEmpty() }
        assertEquals("#ffff00", stt.highlightedWords.single().color)
    }

    @Test
    fun `a server that reports failure leaves the existing words alone`() {
        val stt = stt()
        stt.handleWordHighlightingUpdate(org.json.JSONObject("""{"words":[{"word":"kept","color":"#ffff00"}]}"""))
        wordsResponse = 200 to """{"success":false}"""

        stt.fetchWordHighlighting(baseUrl)

        awaitUntil("the request to be answered") { requested.any { it.startsWith("/api/word-highlighting/words") } }
        assertEquals(listOf("kept"), stt.highlightedWords.map { it.word })
    }

    @Test
    fun `a highlighting endpoint that errors is ignored`() {
        val stt = stt()
        wordsResponse = 500 to "upstream exploded"

        stt.fetchWordHighlighting(baseUrl)

        awaitUntil("the request to be answered") { requested.any { it.startsWith("/api/word-highlighting/words") } }
        assertTrue(stt.highlightedWords.isEmpty())
        assertTrue(stt.wordHighlightingEnabled.value, "the default stays until a server says otherwise")
    }

    @Test
    fun `a server that is not there at all is ignored`() {
        val stt = stt()

        stt.fetchWordHighlighting(deadUrl())

        assertTrue(stt.highlightedWords.isEmpty(), "highlighting is optional; a refused connection must not throw")
    }

    // ── Help Dev .db capture ────────────────────────────────────────────────────

    @Test
    fun `a session snapshot is downloaded under its plain file name`() {
        // The server reports the db by its path on that machine; the archive is a flat folder.
        statusResponse = 200 to """{"state":{"db_name":"recordings/2026-08-02_101010.db"}}"""
        downloadResponse = 200 to "sqlite bytes".toByteArray()
        val stt = stt()

        stt.captureDbSnapshot(baseUrl)

        assertEquals(listOf("2026-08-02_101010.db"), snapshots())
        assertEquals("sqlite bytes", File(logDir, "2026-08-02_101010.db").readText())
        assertTrue(
            requested.any { it == "/api/file-manager/download?path=recordings%2F2026-08-02_101010.db" },
            "the path has to be encoded or a db in a subfolder is never found; asked: $requested"
        )
    }

    @Test
    fun `a later snapshot replaces the earlier one`() {
        statusResponse = 200 to """{"state":{"db_name":"session.db"}}"""
        downloadResponse = 200 to "first".toByteArray()
        val stt = stt()
        stt.captureDbSnapshot(baseUrl)

        downloadResponse = 200 to "second, longer".toByteArray()
        stt.captureDbSnapshot(baseUrl)

        assertEquals(listOf("session.db"), snapshots(), "the archive keeps one file per session, not one per tick")
        assertEquals("second, longer", File(logDir, "session.db").readText())
    }

    @Test
    fun `a status endpoint that errors captures nothing`() {
        statusResponse = 500 to "no"
        val stt = stt()

        stt.captureDbSnapshot(baseUrl)

        assertTrue(snapshots().isEmpty())
    }

    @Test
    fun `a status body with no session captures nothing`() {
        val stt = stt()

        statusResponse = 200 to """{"ok":true}"""
        stt.captureDbSnapshot(baseUrl)
        statusResponse = 200 to """{"state":{}}"""
        stt.captureDbSnapshot(baseUrl)

        assertTrue(snapshots().isEmpty(), "no recording in progress is normal, not an error")
    }

    @Test
    fun `a failed download leaves nothing behind for the next run to trust`() {
        statusResponse = 200 to """{"state":{"db_name":"session.db"}}"""
        downloadResponse = 404 to ByteArray(0)
        val stt = stt()

        stt.captureDbSnapshot(baseUrl)

        assertTrue(snapshots().isEmpty(), "neither a partial .db nor a leftover .tmp")
    }

    @Test
    fun `the capture loop takes its first snapshot straight away`() {
        statusResponse = 200 to """{"state":{"db_name":"live.db"}}"""
        downloadResponse = 200 to "live".toByteArray()
        val stt = stt()
        stt.helpDevModeEnabled = true

        stt.startDbCapture(baseUrl)

        awaitUntil("the first snapshot") { snapshots() == listOf("live.db") }
    }

    @Test
    fun `disconnecting takes one last snapshot after the loop stops`() {
        // Without it the archived .db ends at the last 60-second tick, which is how a service came
        // back five minutes short and turned detections the engine really made into replay misses.
        statusResponse = 200 to """{"state":{"db_name":"live.db"}}"""
        downloadResponse = 200 to "during".toByteArray()
        val stt = stt()
        stt.helpDevModeEnabled = true
        stt.startDbCapture(baseUrl)
        awaitUntil("the loop's own snapshot") { snapshots() == listOf("live.db") }

        downloadResponse = 200 to "the last word".toByteArray()
        stt.disconnect()

        awaitUntil("the closing snapshot") { File(logDir, "live.db").readText() == "the last word" }
    }
}
