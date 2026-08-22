package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.AtemSettings
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.atem.FakeAtemSwitcher

/**
 * `POST /api/atem/still/{name}` all the way to a switcher: render the named lower third, then push
 * the frame into the ATEM media pool.
 *
 * This is the one-press path a Stream Deck button drives mid-service, and it is the only place the
 * render cache and the switcher meet. [CompanionServerLowerThirdTest] covers every way it refuses
 * before reaching either; what is new here is that the bytes are actually produced and actually
 * arrive.
 *
 * Three things make it affordable, all established by measurement rather than assumption:
 *
 * 1. **The render is ~900 ms of one-time skiko/Lottie warm-up, not per-frame work** — the same
 *    lottie at 1920×1080 and at 320×180 both cost that, and a second `prepare` of the same content
 *    costs 1 ms. So `@BeforeClass` renders once and every test then hits the cache.
 * 2. **That same warm-up step yields the expected payload size.** [FakeAtemSwitcher] completes a
 *    transfer when the byte count it was told to expect arrives, and the route does not know its
 *    own payload size in advance — so the test reads the frame out of the cache it just warmed and
 *    hands the size to the fake. One step, two purposes.
 * 3. **The route answers before the upload runs** — it responds `"uploading"` and does the work in a
 *    background coroutine, so every assertion about the switcher waits on the datagrams themselves
 *    rather than on the HTTP response.
 *
 * The lottie fixture has to render to something with detail in it. A blank or single-colour frame
 * run-length-encodes to about fifty bytes — one chunk — so "every byte arrives" would pass without a
 * transfer having been exercised at all. `payloadIsWorthTransferring` pins that.
 *
 * The clip endpoint is covered too. The reason #155 deferred it — "a clip renders every frame" — was
 * measured and is not the obstacle: sixty frames at this size render in **89 ms** once skiko is warm,
 * about 1.5 ms each. Two other things are what actually make it work:
 *
 * - [FakeAtemSwitcher] completes a transfer at a **single** `expectedTransferBytes`, and a clip is
 *   one transfer per frame. This fixture is static — every keyframe is `a: 0` — so all sixty frames
 *   encode to the same size and one expectation fits them all. An animated fixture would need the
 *   fake to learn per-frame sizes.
 * - The fake already answers `SMPC` with an `MPCS` describing the bank as fully ingested, so
 *   `awaitClipReady` ends on a real message rather than on its fifteen-second timeout.
 *
 * Still not covered: a clip upload that also cuts a key. That path sleeps for the clip's own
 * duration before taking the key off again, which is a wait rather than work.
 */
class CompanionServerAtemUploadTest {

    private lateinit var client: HttpClient

    companion object {
        /**
         * 320×180 keeps the render cheap. Two offset solid layers rather than one full-canvas one:
         * a uniform frame run-length-encodes to about fifty bytes, which fits in a single chunk and
         * would make "every byte arrives" true without the transfer ever being exercised.
         * [payloadIsWorthTransferring] holds that property so a later simplification cannot quietly
         * hollow the suite out.
         */
        private const val LOTTIE = """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":320,"h":180,"assets":[],"layers":[""" +
            """{"ddd":0,"ind":1,"ty":1,"nm":"left","sr":1,"ks":{"o":{"a":0,"k":100},""" +
            """"p":{"a":0,"k":[80,90,0]},"a":{"a":0,"k":[60,45,0]},"s":{"a":0,"k":[100,100,100]}},""" +
            """"sc":"#0088ff","sw":120,"sh":90,"ip":0,"op":60,"st":0},""" +
            """{"ddd":0,"ind":2,"ty":1,"nm":"back","sr":1,"ks":{"o":{"a":0,"k":100},""" +
            """"p":{"a":0,"k":[160,90,0]},"a":{"a":0,"k":[160,90,0]},"s":{"a":0,"k":[100,100,100]}},""" +
            """"sc":"#ff8800","sw":320,"sh":180,"ip":0,"op":60,"st":0}]}"""

        private const val CLIP_FPS = 30.0
        private const val RENDER_W = 320
        private const val RENDER_H = 180

        private lateinit var server: CompanionServer
        private lateinit var lottieFolder: File
        private lateinit var tempHome: File
        private var realHome: String? = null

        /** Size of the encoded frame the route will send, read from the warmed cache. */
        private var payloadBytes: Int = 0

        /** Frames in the clip variant, and the size of each (identical — the fixture is static). */
        private var clipFrames: Int = 0
        private var clipFrameBytes: Int = 0

        /**
         * The port the server is ASKED for. Never build a URL from it: `CompanionServer.start` runs
         * it through `findFreePort`, which walks upward when the port is taken, so the server can
         * end up one along. [boundPort] is where it actually is.
         */
        private val requestedPort = testPort(39_880)

        /** Where the server really listens, read back from its own URL once it is up. */
        private var boundPort: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            // Both JVM-wide user.home consumers first: the Instance Link log path, and skiko's
            // native-library unpack directory — this class renders, so it touches skia.
            TestSingletons.latchToTestHome()
            TestSingletons.latchSkikoNativeLibrary()
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-atem-upload-home").toFile()
            System.setProperty("user.home", tempHome.absolutePath)

            lottieFolder = Files.createTempDirectory("cp-atem-upload-lotties").toFile()
            File(lottieFolder, "Welcome.json").writeText(LOTTIE)

            // Pay the render warm-up here rather than inside the first test, and take the payload
            // size the fake needs from the same render.
            val variant = LottieRenderCache.atemVariant(LOTTIE, settings(host = "127.0.0.1", port = 1), clip = false)
            val cached = runBlocking { LottieRenderCache.prepare(LOTTIE, variant).await() }
            payloadBytes = LottieRenderCache.Reader(cached).use {
                it.nextAtemFrame(RENDER_W, RENDER_H).data.size
            }

            // Same again for the clip variant. Every frame is the same size here, which is what lets
            // one expectedTransferBytes serve all of them; assert that rather than trust it.
            val clipVariant = LottieRenderCache.atemVariant(
                LOTTIE, settings(host = "127.0.0.1", port = 1), clip = true, fps = CLIP_FPS
            )
            val cachedClip = runBlocking { LottieRenderCache.prepare(LOTTIE, clipVariant).await() }
            LottieRenderCache.Reader(cachedClip).use { reader ->
                clipFrames = reader.frameCount
                val sizes = (0 until clipFrames).map { reader.nextAtemFrame(RENDER_W, RENDER_H).data.size }
                clipFrameBytes = sizes.first()
                check(sizes.all { it == clipFrameBytes }) {
                    "the clip fixture stopped being static — frame sizes $sizes cannot share one expectation"
                }
            }

            server = CompanionServer()
            server.start(port = requestedPort)
            boundPort = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("companion server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop()
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
            lottieFolder.deleteRecursively()
        }

        private fun settings(host: String, port: Int) = AtemSettings(
            host = host,
            port = port,
            renderWidth = RENDER_W,
            renderHeight = RENDER_H,
            clipFps = CLIP_FPS,
            detectedStillSlots = 64,
            detectedMixEffects = 4,
            detectedKeyersPerMe = listOf(4, 4, 4, 4),
            detectedDownstreamKeyers = 2,
        )
    }

    @BeforeTest
    fun dropInheritedConnection() {
        // These suites share one JVM (jvmTestSerial), so the pooled connection is shared too. Every
        // class here is expected to leave it clean; none of them is trusted to.
        AtemConnectionManager.invalidate()
    }

    @AfterTest
    fun releaseConnection() {
        runBlocking { server.atem.cancelUpload() }
        AtemConnectionManager.invalidate()
        if (::client.isInitialized) client.close()
    }

    private fun http(): HttpClient {
        if (!::client.isInitialized) client = HttpClient(CIO)
        return client
    }

    private fun switcher() = FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4)
        .also { it.expectedTransferBytes = payloadBytes }

    /**
     * Runs [body] against a fresh switcher, then ends the upload it started and drops the shared
     * ATEM connection -- both **before** that switcher closes.
     *
     * The order is the whole point. `POST /api/atem/still|clip` answers `"uploading"` and does the
     * transfer on `CompanionServer`'s own scope, so when the body's assertions are satisfied the
     * coroutine is still running -- typically in the `delay(KEY_SETTLE_MS)` tail, or the
     * clip-duration wait before the automatic key-off. Left alone it reaches its next
     * `AtemConnectionManager.use` *after* the test invalidated, opens a connection to the fake this
     * test is about to close, and caches it.
     *
     * What follows is a cascade rather than one bad test. `AtemClient.isAlive()` is `socket != null`,
     * which for UDP stays true with nothing listening, and the fake binds an **ephemeral** port the
     * OS can hand out again -- so `ensureConnected` believes it is already connected, skips the
     * handshake, and the next test's switcher records not one command of any name. Each poisoned
     * test then burns its 5s deadline while leaving ~8s of stuck work behind, so the backlog grows
     * faster than the suite drains it. That is the shape five of these tests failed with on CI, and
     * it survived the first attempt at this helper because the three clip tests did not use it.
     *
     * `cancelUpload()` joins, so it returns only once the coroutine has actually stopped and nothing
     * can touch the connection behind us.
     */
    private fun withSwitcher(
        newSwitcher: () -> FakeAtemSwitcher = { switcher() },
        configureWith: (FakeAtemSwitcher) -> Unit = { configure(it) },
        body: (FakeAtemSwitcher) -> Unit,
    ) {
        newSwitcher().use { fake ->
            configureWith(fake)
            try {
                body(fake)
            } finally {
                runBlocking { server.atem.cancelUpload() }
                AtemConnectionManager.invalidate()
            }
        }
    }

    private fun configure(fake: FakeAtemSwitcher) {
        server.updateAtemConfig(settings("127.0.0.1", fake.port), lowerThirdFolder = lottieFolder.absolutePath)
    }

    /**
     * The still's destination slot, read out of the transfer request. Offsets are the FTSD layout
     * `AtemClientProtocolTest` pins byte for byte: transferId at 0, storeId at 2, frame index at 6.
     * Reading storeId by mistake is silent — it is 0 for the still store, which looks like slot 1.
     */
    private fun frameIndexOf(fake: FakeAtemSwitcher): Int {
        val start = fake.commandsNamed("FTSD").single()
        return ((start[6].toInt() and 0xFF) shl 8) or (start[7].toInt() and 0xFF)
    }

    private fun upload(query: String): HttpResponse =
        runBlocking { http().post("http://127.0.0.1:$boundPort/api/atem/still/Welcome$query") }

    // ── The fixture ─────────────────────────────────────────────────────────────

    @Test
    fun `payloadIsWorthTransferring`() {
        // Guards every other assertion in this class: a uniform frame encodes to ~50 bytes and rides
        // in a single chunk, so the byte-count assertions below would hold trivially.
        assertTrue(
            payloadBytes > 1_000,
            "the rendered frame is only $payloadBytes bytes — the fixture has stopped producing detail"
        )
        // And the clip has to be worth calling a clip: "one transfer per frame" says nothing if the
        // fixture renders to a single frame.
        assertTrue(
            clipFrames > 30,
            "the clip is only $clipFrames frames — the per-frame transfer assertions would prove nothing"
        )
    }

    // ── The upload itself ───────────────────────────────────────────────────────

    @Test
    fun `a still is rendered and every byte of it reaches the switcher`() {
        withSwitcher { fake ->

            val response = upload("?slot=3")

            // The route answers before doing the work — that is the contract, not a race.
            assertEquals(HttpStatusCode.OK, response.status)
            val body = runBlocking { response.bodyAsText() }
            assertTrue(body.contains(""""status":"uploading""""), body)
            assertTrue(body.contains(""""slot":3"""), body)

            val locks = fake.awaitCommandsNamed("LOCK", 2)
            assertEquals(1, locks.first()[2].toInt(), "the media store is locked before the transfer")
            assertEquals(0, locks.last()[2].toInt(), "and released after it")

            assertEquals(1, fake.commandsNamed("FTSD").size, "one transfer was started")
            val sent = fake.commandsNamed("FTDa").sumOf {
                ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
            }
            assertEquals(payloadBytes, sent, "every encoded byte of the rendered frame reaches the switcher")
        }
    }

    @Test
    fun `the slot the operator asked for is the slot that is written`() {
        // The query is 1-based and the protocol is 0-based, and a still landing in the wrong slot
        // overwrites whatever the operator had prepared there.
        withSwitcher { fake ->

            upload("?slot=5")

            fake.awaitCommandsNamed("LOCK", 2)
            assertEquals(4, frameIndexOf(fake), "slot 5 on the request is index 4 on the wire")
        }
    }

    @Test
    fun `with no slot on the request the configured default is used`() {
        withSwitcher(
            configureWith = { fake ->
                server.updateAtemConfig(
                    settings("127.0.0.1", fake.port).copy(defaultStillSlot = 7),
                    lowerThirdFolder = lottieFolder.absolutePath
                )
            }
        ) { fake ->
            val body = runBlocking { upload("").bodyAsText() }

            assertTrue(body.contains(""""slot":8"""), "the response reports the 1-based slot: $body")
            fake.awaitCommandsNamed("LOCK", 2)
            assertEquals(7, frameIndexOf(fake))
        }
    }

    // ── Clips ───────────────────────────────────────────────────────────────────

    private fun clipSwitcher() = FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4)
        .also {
            it.expectedTransferBytes = clipFrameBytes
            // What the switcher reports back once the clip is committed; awaitClipReady waits for it.
            it.clipFramesOnCommit = clipFrames
        }

    private fun uploadClip(query: String): HttpResponse =
        runBlocking { http().post("http://127.0.0.1:$boundPort/api/atem/clip/Welcome$query") }

    @Test
    fun `a clip is rendered and every frame of it is transferred`() {
        withSwitcher({ clipSwitcher() }) { fake ->

            val response = uploadClip("?slot=1")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = runBlocking { response.bodyAsText() }
            assertTrue(body.contains(""""type":"clip""""), body)
            assertTrue(body.contains(""""slot":1"""), body)

            // The commit is the last thing the client sends, so it is the signal the whole clip is up.
            fake.awaitCommandsNamed("SMPC", 1)
            assertEquals(
                clipFrames, fake.commandsNamed("FTSD").size,
                "one transfer per frame — a clip that stops early plays as a freeze on air"
            )
            val sent = fake.commandsNamed("FTDa").sumOf {
                ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
            }
            assertEquals(clipFrames * clipFrameBytes, sent, "every encoded byte of every frame reaches the switcher")
        }
    }

    @Test
    fun `the clip lands in the slot the operator asked for`() {
        withSwitcher({ clipSwitcher() }) { fake ->

            uploadClip("?slot=2")

            val commit = fake.awaitCommandsNamed("SMPC", 1).single()
            // SMPC byte 0 is a field mask, not the index — reading it would look like slot 4 here.
            assertEquals(1, commit[1].toInt() and 0xFF, "slot 2 on the request is clip index 1 on the wire")
        }
    }

    @Test
    fun `a clip too long for its slot is refused before anything is rendered`() {
        // The capacity comes from the last connection to the switcher, so the refusal happens up
        // front and the caller gets a real error rather than a silent half-ingested clip.
        withSwitcher(
            newSwitcher = { clipSwitcher() },
            configureWith = { fake ->
                server.updateAtemConfig(
                    settings("127.0.0.1", fake.port).copy(detectedClipMaxFrames = listOf(5, 5)),
                    lowerThirdFolder = lottieFolder.absolutePath
                )
            }
        ) { fake ->
            val response = uploadClip("?slot=1")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            val body = runBlocking { response.bodyAsText() }
            assertTrue(body.contains("frames"), body)
            assertTrue(fake.commandsNamed("FTSD").isEmpty(), "nothing may be sent for a clip that cannot fit")
        }
    }

    // ── Upload then key ─────────────────────────────────────────────────────────

    @Test
    fun `asking for a key cuts it on only after the transfer has finished`() {
        // Keying before the still has landed puts the previous graphic on air — the ordering is the
        // whole point of doing both in one request.
        withSwitcher { fake ->

            val body = runBlocking { upload("?slot=1&key=1&me=1").bodyAsText() }
            assertTrue(body.contains(""""me":1"""), body)
            assertTrue(body.contains(""""key":1"""), body)

            fake.awaitCommandsNamed("CKOn", 1)
            // FTDC is the switcher's own "transfer done" reply, so it is never in the received
            // list; the last data chunk the client sent is the marker for "the frame is across".
            val order = synchronized(fake.received) { fake.received.map { it.first } }
            assertTrue("FTDa" in order, "data was sent, saw $order")
            assertTrue(
                order.indexOf("CKOn") > order.lastIndexOf("FTDa"),
                "the key must be cut only once the whole frame is across, saw $order"
            )
        }
    }

    @Test
    fun `an upload does not outlive the switcher it was aimed at`() {
        // The cascade this closes. The route answers "uploading" and transfers on the server's own
        // scope, so when a test's assertions are satisfied the coroutine is still running. Let it
        // run past the switcher's close and its next `AtemConnectionManager.use` dials an endpoint
        // nothing is listening on -- which holds the manager's mutex for the whole connect timeout
        // and can leave a client cached for a dead ephemeral port. Either way the NEXT test's
        // upload never reaches its switcher, and every wait in it times out with "got 0".
        //
        // cancelUpload() joins, so it returns only once the coroutine has stopped.
        switcher().use { fake ->
            configure(fake)

            upload("?slot=1")
            fake.awaitCommandsNamed("LOCK", 2)

            runBlocking { server.atem.cancelUpload() }

            assertNull(
                AtemUploadStatus.state.value,
                "a cancelled upload is over and is not a failure — it must leave no status behind"
            )
            AtemConnectionManager.invalidate()
        }

        // With nothing left over, the very next upload reaches its own switcher immediately.
        withSwitcher { fake ->
            upload("?slot=1")

            fake.awaitCommandsNamed("LOCK", 2)
            assertEquals(1, fake.commandsNamed("FTSD").size, "the next upload gets a real connection")
        }
    }

    @Test
    fun `no key is touched when the request does not ask for one`() {
        withSwitcher { fake ->

            upload("?slot=1")

            fake.awaitCommandsNamed("LOCK", 2)
            assertTrue(fake.commandsNamed("CKOn").isEmpty(), "an upload on its own must not put anything on air")
        }
    }
}
