package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.atem.FakeAtemSwitcher

/**
 * `/api/atem/key/on` and `/api/atem/key/off` all the way through to a switcher — the Stream Deck
 * button that cuts a lower third on air.
 *
 * [CompanionServerLowerThirdTest] covers every way these routes *refuse*, and says in its own doc
 * that the successful path cannot be driven because with no ATEM on the network it ends in a
 * five-second socket timeout. That was true of a real switcher only: [FakeAtemSwitcher] speaks the
 * protocol over loopback UDP, so the route can be driven end to end — through `AtemConnectionManager`,
 * a real `AtemClient` and a real datagram exchange — in a few milliseconds.
 *
 * What that reaches is the half of each request that no refusal test can see: the response says
 * which target was driven, and the *switcher* is what proves the answer is not merely plausible.
 * The two are asserted together deliberately — a route that reports `"me":2,"key":3` while sending
 * the switcher M/E 1 keyer 1 is the failure that matters, and either assertion alone passes through
 * it. The 1-based/0-based conversion happening twice on each request (query → internal → response)
 * is exactly where that goes wrong.
 *
 * Each test gets its own switcher on its own port, so `updateAtemConfig`'s host/port change
 * invalidates the shared connection for free; the teardown invalidates again so no cached client
 * outlives its fake.
 *
 * Not covered: the `cutKey` fallback taken when `tryRun` finds the shared connection busy (holding
 * the mutex from a test means racing a lock, not asserting behaviour), and the 502 path for a
 * switcher that never answers — that one ends on `AtemClient`'s five-second timeout, which is a
 * duration rather than a test.
 */
class CompanionServerAtemKeyTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_870))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    testPort(39_870)
                }
            } ?: error("companion server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop()
        }
    }

    @AfterTest
    fun releaseConnection() {
        // The manager caches one client process-wide; drop it so no keepalive loop outlives the
        // fake switcher it was talking to.
        AtemConnectionManager.invalidate()
        if (::client.isInitialized) client.close()
    }

    private fun http(): HttpClient {
        if (!::client.isInitialized) client = HttpClient(CIO)
        return client
    }

    /** A switcher big enough that no test trips validation before reaching it. */
    private fun switcher() = FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4)

    private fun configure(fake: FakeAtemSwitcher, settings: AtemSettings = AtemSettings()) {
        server.updateAtemConfig(
            settings.copy(
                host = "127.0.0.1",
                port = fake.port,
                detectedMixEffects = 4,
                detectedKeyersPerMe = listOf(4, 4, 4, 4),
                detectedDownstreamKeyers = 2,
            ),
            lowerThirdFolder = ""
        )
    }

    private fun press(path: String): HttpResponse = runBlocking { http().post("http://127.0.0.1:$port$path") }

    // ── Upstream keys ───────────────────────────────────────────────────────────

    @Test
    fun `cutting an upstream key on air reaches the switcher and reports the target it drove`() {
        switcher().use { fake ->
            configure(fake)

            val response = press("/api/atem/key/on?me=2&key=3")

            val body = runBlocking { response.bodyAsText() }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains(""""status":"on""""), body)
            assertTrue(body.contains(""""me":2"""), body)
            assertTrue(body.contains(""""key":3"""), body)

            val cmd = fake.awaitCommandsNamed("CKOn", 1).single()
            assertEquals(1, cmd[0].toInt(), "the response said M/E 2, so the switcher must get index 1")
            assertEquals(2, cmd[1].toInt(), "the response said key 3, so the switcher must get index 2")
            assertEquals(1, cmd[2].toInt(), "on air")
        }
    }

    @Test
    fun `taking an upstream key off air sends the same target with the on-air flag cleared`() {
        switcher().use { fake ->
            configure(fake)

            val response = press("/api/atem/key/off?me=1&key=1")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(runBlocking { response.bodyAsText() }.contains(""""status":"off""""))

            val cmd = fake.awaitCommandsNamed("CKOn", 1).single()
            assertEquals(0, cmd[0].toInt())
            assertEquals(0, cmd[1].toInt())
            assertEquals(0, cmd[2].toInt(), "off air — the same command carries the state, not a different one")
        }
    }

    @Test
    fun `a press that names no target uses the operator's configured defaults`() {
        // This is the ordinary Stream Deck button: no query string, whatever the ATEM settings say.
        switcher().use { fake ->
            configure(fake, AtemSettings(keyMixEffect = 1, keyIndex = 2))

            val response = press("/api/atem/key/on")

            val body = runBlocking { response.bodyAsText() }
            assertTrue(body.contains(""""me":2"""), body)
            assertTrue(body.contains(""""key":3"""), body)

            val cmd = fake.awaitCommandsNamed("CKOn", 1).single()
            assertEquals(1, cmd[0].toInt())
            assertEquals(2, cmd[1].toInt())
        }
    }

    // ── Downstream keys ─────────────────────────────────────────────────────────

    @Test
    fun `keytype dsk drives a downstream keyer instead of an upstream one`() {
        switcher().use { fake ->
            configure(fake)

            val response = press("/api/atem/key/on?keytype=dsk&key=2")

            val body = runBlocking { response.bodyAsText() }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains(""""dsk":2"""), body)
            assertTrue(!body.contains(""""me":"""), "a downstream key has no M/E to report: $body")

            val cmd = fake.commandsNamed("CDsL").firstOrNull() ?: fake.awaitCommandsNamed("DDsA", 1).first()
            assertEquals(1, cmd[0].toInt(), "the response said DSK 2, so the switcher must get index 1")
            assertTrue(fake.commandsNamed("CKOn").isEmpty(), "no upstream keyer may be touched")
        }
    }

    @Test
    fun `the persisted downstream preference applies with no keytype on the request`() {
        switcher().use { fake ->
            configure(fake, AtemSettings(useDownstreamKey = true, dskIndex = 1))

            val response = press("/api/atem/key/on")

            assertTrue(runBlocking { response.bodyAsText() }.contains(""""dsk":2"""))
            val cmd = fake.commandsNamed("CDsL").firstOrNull() ?: fake.awaitCommandsNamed("DDsA", 1).first()
            assertEquals(1, cmd[0].toInt())
        }
    }

    @Test
    fun `keytype usk overrides a persisted downstream preference`() {
        switcher().use { fake ->
            configure(fake, AtemSettings(useDownstreamKey = true, dskIndex = 1))

            val response = press("/api/atem/key/on?keytype=usk&me=1&key=1")

            assertTrue(runBlocking { response.bodyAsText() }.contains(""""me":1"""))
            fake.awaitCommandsNamed("CKOn", 1)
            assertTrue(
                fake.commandsNamed("CDsL").isEmpty() && fake.commandsNamed("DDsA").isEmpty(),
                "the override has to win, or a request aimed at the programme bus lands on the DSK"
            )
        }
    }

    // ── The shared connection ───────────────────────────────────────────────────

    @Test
    fun `a second press reuses the open connection and still reaches the switcher`() {
        // The ATEM drops an idle UDP session after a few seconds and the manager caches one client,
        // so the second button press is a different code path from the first: it must not silently
        // land on a stale session.
        switcher().use { fake ->
            configure(fake)

            assertEquals(HttpStatusCode.OK, press("/api/atem/key/on?me=1&key=1").status)
            assertEquals(HttpStatusCode.OK, press("/api/atem/key/off?me=1&key=1").status)

            val commands = fake.awaitCommandsNamed("CKOn", 2)
            assertEquals(1, commands[0][2].toInt(), "first press cut the key on")
            assertEquals(0, commands[1][2].toInt(), "second press took it off again")
        }
    }
}
