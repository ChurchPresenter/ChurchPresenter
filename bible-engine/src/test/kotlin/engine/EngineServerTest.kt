package engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine's own WebSocket server, driven over loopback exactly as ChurchPresenter drives it —
 * a real Netty server on a real port with a real client, rather than reaching past the socket.
 *
 * `EngineServer.start` runs with a **blank STT url**, which the engine documents as a deliberate
 * WS-input-only setup rather than an error: transcripts arrive over this same socket instead of
 * from socket.io. That is what makes the whole server testable with no speech backend at all.
 *
 * Every read here is one the protocol guarantees will arrive, and each ends on the frame itself.
 * There are no speculative reads — an extra read that no frame answers costs its whole timeout,
 * which is the shape `AGENT.md` rules out.
 *
 * **Not covered:** the port-search loop that steps over a busy port (the common collision is with
 * ChurchPresenter's own Companion server). Netty takes several seconds to fail a bind on a taken
 * port, so a test of it costs that wait no matter how the port is occupied — its price is a
 * duration rather than the work itself. The bound port IS asserted on the happy path below.
 */
class EngineServerTest {

    private lateinit var temp: File
    private var handle: EngineHandle? = null
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        temp = Files.createTempDirectory("engine-server-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        handle?.stop()
        handle = null
        temp.deleteRecursively()
    }

    /**
     * A minimal but structurally real `.spb` — header, book manifest, separator, verse rows.
     * The loader rejects a translation carrying fewer than ten verses (a guard against a truncated
     * download registering as a usable Bible), so this writes comfortably past that.
     */
    private fun writeSpb() {
        val sb = StringBuilder()
        sb.append("##Title:TST Test\n")
        sb.append("##Abbreviation:TST\n")
        sb.append("43\tJohn\t21\n")
        sb.append("19\tPsalms\t150\n")
        sb.append("-----\n")
        sb.append("B043C003V016\t43\t3\t16\tFor God so loved the world, that he gave his only begotten Son.\n")
        sb.append("B043C003V017\t43\t3\t17\tFor God sent not his Son into the world to condemn the world.\n")
        for (v in 1..20) {
            sb.append("B019C023V%03d".format(v)).append("\t19\t23\t").append(v)
                .append("\tThe LORD is my shepherd, verse ").append(v).append(" of this psalm.\n")
        }
        File(temp, "ENG_TST.spb").writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun startEngine(port: Int = 39_940): EngineHandle {
        writeSpb()
        val started = assertNotNull(
            EngineServer.start(sttUrl = "", bibleRoot = temp.absolutePath, port = port),
            "the engine started",
        )
        handle = started
        return started
    }

    /** A connected client session: `send` writes a frame, `next` reads the one that answers it. */
    private class Session(
        val send: suspend (String) -> Unit,
        val next: suspend () -> String,
    )

    private fun <T> connected(port: Int, body: suspend Session.() -> T): T = runBlocking {
        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            var result: T? = null
            client.webSocket(host = "127.0.0.1", port = port, path = "/bible-engine") {
                val session = Session(
                    send = { text -> send(Frame.Text(text)) },
                    next = {
                        // Ends on the frame; the timeout exists only to fail the test.
                        assertNotNull(
                            withTimeoutOrNull(10_000) { (incoming.receive() as? Frame.Text)?.readText() },
                            "expected a frame from the engine",
                        )
                    },
                )
                result = session.body()
            }
            result!!
        } finally {
            client.close()
        }
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    @Test
    fun `the engine refuses to start without a bible root`() {
        assertNull(EngineServer.start(sttUrl = "", bibleRoot = "", port = 39_950))
    }

    @Test
    fun `a bible root holding no usable translation is refused`() {
        // A truncated download must not register as a Bible; the loader wants ten verses.
        File(temp, "ENG_SHORT.spb").writeText(
            "##Title:Short\n##Abbreviation:SHT\n43\tJohn\t21\n-----\nB043C003V016\t43\t3\t16\tOne verse only.\n",
            Charsets.UTF_8,
        )
        assertNull(EngineServer.start(sttUrl = "", bibleRoot = temp.absolutePath, port = 39_952))
    }

    @Test
    fun `starting reports the port it actually bound`() {
        val started = startEngine()
        assertTrue(started.boundPort in 39_940..39_949, "bound within the search range: ${started.boundPort}")
    }

    @Test
    fun `stopping is safe to call twice`() {
        val started = startEngine()
        started.stop()
        started.stop()
        handle = null
    }

    // ── The socket ────────────────────────────────────────────────────────────

    @Test
    fun `every connecting client is sent the engine status straight away`() {
        val started = startEngine()
        val first = connected(started.boundPort) { next() }

        val obj = json.parseToJsonElement(first).jsonObject
        assertEquals("engine_status", obj["type"]?.jsonPrimitive?.content, "got: $first")
        // A blank STT url is a deliberate WS-input-only setup, not a failure to connect — the
        // two flags say so separately so a consumer can tell them apart.
        assertEquals("false", obj["sttConnected"]?.jsonPrimitive?.content)
        assertEquals("false", obj["sttConfigured"]?.jsonPrimitive?.content)

        // The Broadcaster replays the latest status to every late joiner, so a client connecting
        // mid-service is not left with a blank status bar. Same engine, to pay one startup.
        val second = connected(started.boundPort) { next() }
        assertTrue(second.contains("engine_status"), "the late joiner got it too: $second")
    }

    @Test
    fun `a ping is answered with a pong`() {
        val started = startEngine()
        val pong = connected(started.boundPort) {
            next()                       // the replayed status
            send("""{"type":"ping"}""")
            next()
        }
        assertTrue(pong.contains("pong"), "got: $pong")
    }

    @Test
    fun `a transcript naming a reference produces a detection`() {
        val started = startEngine()
        val event = connected(started.boundPort) {
            next()                       // status
            send("""{"type":"transcription_update","text":"let us read John chapter 3 verse 16","id":"u1"}""")
            next()
        }

        val obj = json.parseToJsonElement(event).jsonObject
        assertEquals("scripture.detected", obj["type"]?.jsonPrimitive?.content, "got: $event")
        val reference = assertNotNull(obj["reference"]).jsonObject
        assertEquals("43", reference["bookId"]?.jsonPrimitive?.content, "John")
        assertEquals("3", reference["chapter"]?.jsonPrimitive?.content)
        assertEquals("16", reference["verseStart"]?.jsonPrimitive?.content)
        assertEquals("John 3:16", reference["displayRef"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the detection carries back the id of the utterance that produced it`() {
        // The id is the clock-free correlation key shared with the STT rows and the operator log.
        val started = startEngine()
        val event = connected(started.boundPort) {
            next()
            send("""{"type":"transcription_update","text":"turn to John chapter 3 verse 17","id":"utterance-99"}""")
            next()
        }
        assertEquals("utterance-99", json.parseToJsonElement(event).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a translation frame is detected on too`() {
        val started = startEngine()
        val event = connected(started.boundPort) {
            next()
            send("""{"type":"translation_update","text":"read with me John chapter 3 verse 16","id":"t1"}""")
            next()
        }
        assertTrue(event.contains("scripture.detected"), "the translated side detects too: $event")
    }

    @Test
    fun `malformed JSON is ignored rather than dropping the connection`() {
        val started = startEngine()
        val pong = connected(started.boundPort) {
            next()
            send("this is not json at all")
            // The ping is the positive signal that the session survived the garbage frame.
            send("""{"type":"ping"}""")
            next()
        }
        assertTrue(pong.contains("pong"), "the session survived: $pong")
    }

    @Test
    fun `a message with no type is ignored`() {
        val started = startEngine()
        val pong = connected(started.boundPort) {
            next()
            send("""{"text":"no type field here"}""")
            send("""{"type":"ping"}""")
            next()
        }
        assertTrue(pong.contains("pong"))
    }

    @Test
    fun `a tuning change is accepted over the socket`() {
        val started = startEngine()
        val pong = connected(started.boundPort) {
            next()
            send("""{"type":"set_tuning","level":"strict"}""")
            send("""{"type":"ping"}""")
            next()
        }
        assertTrue(pong.contains("pong"), "the tuning message was accepted")
    }

    // ── The version-corpus startup report ─────────────────────────────────────
    //
    // Version detection cannot answer from a corpus of fewer than two renderings, and used to say
    // so nowhere: on a folder holding one translation per language it reported nothing forever and
    // looked identical to a broken feature. These pin the line that tells the operator which it is.

    @Test
    fun `a corpus that could never answer is reported as inactive, naming the root`() {
        val saved = Config.bibleRoot
        try {
            Config.bibleRoot = temp.absolutePath
            val report = EngineServer.versionCorpusReport(emptyList())
            assertTrue(report.contains("inactive"), "got: $report")
            assertTrue(report.contains(temp.absolutePath), "names the folder to look in: $report")
        } finally {
            Config.bibleRoot = saved
        }
    }

    @Test
    fun `a single-translation corpus is reported as inactive and says what would fix it`() {
        val report = EngineServer.versionCorpusReport(listOf("KJV"))
        assertTrue(report.contains("inactive"), "got: $report")
        assertTrue(report.contains("KJV"), "names what it did index: $report")
        // The actionable half: the operator's folder can hold several bibles and still fail, because
        // the two must share the language being read. A bare count would not say that.
        assertTrue(report.contains("language being read"), "explains the same-language rule: $report")
    }

    @Test
    fun `a usable corpus reports its size and every translation in it`() {
        val report = EngineServer.versionCorpusReport(listOf("KJV", "NASB", "ESV"))
        assertTrue(report.contains("ready"), "got: $report")
        assertTrue(report.contains("3 translations"), "states the size: $report")
        for (label in listOf("KJV", "NASB", "ESV")) {
            assertTrue(report.contains(label), "names $label: $report")
        }
        assertTrue(!report.contains("inactive"), "a usable corpus is not reported as inactive: $report")
    }
}
