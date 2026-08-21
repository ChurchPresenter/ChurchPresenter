package org.churchpresenter.app.churchpresenter.data

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pulling a Planning Center attachment onto disk.
 *
 * This is how a service plan's PDFs and images actually arrive: the app resolves a one-time
 * download URL from the API and then fetches it, usually from S3 rather than from Planning Center
 * itself. Everything about that fetch is worth pinning because it is binary and it is a file the
 * operator will open — bytes have to land byte for byte, a server error must not leave a truncated
 * or empty file looking like a successful download, and the two failure kinds have to stay
 * distinguishable, since one is worth retrying and the other is not.
 *
 * Unlike the rest of this client, these two take their URL as a parameter, so they can be pointed
 * at a local server rather than at planningcenteronline.com.
 */
class PlanningCenterDownloadTest {

    private lateinit var dir: File

    /**
     * A stand-in for the attachment host: a few fixed routes covering the outcomes that matter.
     *
     * Binds port 0 and asks the engine what it got, rather than picking a free port up front and
     * binding it a moment later — between the probe socket closing and Netty binding, anything else
     * in the suite that starts a server can take the port, and this one then dies with
     * `BindException: Address already in use`.
     */
    private companion object {
        /** Every byte value, so a text-mode or encoding slip cannot pass unnoticed. */
        val HOST_PAYLOAD = ByteArray(512) { (it % 256).toByte() }

        /**
         * One host for the whole class, started on first use.
         *
         * It used to be one per test — eighteen Netty start/stops in a few seconds, each stopped
         * with a zero grace period while the next one bound. Roughly one run in six, a host then
         * never reached the point of serving [awaitRouting]'s probe inside its deadline and the
         * test that happened to be next failed for a reason that had nothing to do with it.
         * Nothing here mutates the host — the routes are fixed and each test downloads into its
         * own temp dir — so there is nothing for the tests to isolate from each other.
         */
        val host: FakeAttachmentHost by lazy {
            FakeAttachmentHost(HOST_PAYLOAD).also { started ->
                started.start()
                awaitRouting(started.port)
                Runtime.getRuntime().addShutdownHook(Thread { runCatching { started.stop() } })
            }
        }

        /**
         * Waits until the host actually *serves a route*, not merely until its port accepts.
         *
         * `engine.start(wait = false)` returns before Netty has bound, so the first request could
         * beat the server up and come back as a NetworkError. Waiting on a TCP connect fixed that
         * much — but binding the socket and installing the routing table are two different moments,
         * and a request landing between them is answered **404 by a server with no routes yet**.
         * That is not a NetworkError, it is a `Failure`, which is what `an attachment is written
         * where it was asked for` intermittently saw in a full suite: the outcome the test asserts
         * against is a plain "the server said no", indistinguishable from a real one.
         *
         * So the signal is the route replying 200 — the same thing the tests go on to rely on. The
         * deadline exists to fail loudly if the host never comes up, never as the success path.
         */
        fun awaitRouting(port: Int) {
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline) {
                val code = runCatching {
                    (java.net.URI("http://127.0.0.1:$port/attachment.pdf").toURL().openConnection()
                        as java.net.HttpURLConnection).run {
                        connectTimeout = 200
                        readTimeout = 200
                        requestMethod = "GET"
                        try { responseCode } finally { disconnect() }
                    }
                }.getOrNull()
                if (code == 200) return
                Thread.sleep(10)
            }
            error("fake attachment host never served its routes on port $port")
        }
    }

    private class FakeAttachmentHost(val payload: ByteArray) {
        /** Valid only after [start]; port 0 means "whatever the OS gives us". */
        var port: Int = 0
            private set

        private val engine = embeddedServer(Netty, port = 0) {
            routing {
                get("/attachment.pdf") { call.respondBytes(payload) }
                get("/empty") { call.respondBytes(ByteArray(0)) }
                get("/missing") { call.respondText("no such attachment", status = HttpStatusCode.NotFound) }
                get("/error") { call.respondText("upstream broke", status = HttpStatusCode.InternalServerError) }
                get("/expired") { call.respondText("link expired", status = HttpStatusCode.Forbidden) }
            }
        }

        fun start() {
            engine.start(wait = false)
            // resolvedConnectors() suspends until the bind completes, so the port it reports is the
            // one Netty is actually listening on.
            port = runBlocking { engine.resolvedConnectors().first().port }
        }

        fun stop() = engine.stop(0, 0)
        fun url(path: String) = "http://127.0.0.1:$port$path"
    }

    /** Every byte value, so a text-mode or encoding slip cannot pass unnoticed. */
    private val payload = HOST_PAYLOAD

    private val server: FakeAttachmentHost get() = host

    @BeforeTest
    fun makeDir() {
        dir = Files.createTempDirectory("cp-pco-download-test").toFile()
    }

    @AfterTest
    fun removeDir() {
        dir.deleteRecursively()
    }

    private fun download(path: String, into: File) =
        runBlocking { PlanningCenterClient.downloadFile(server.url(path), into) }

    private fun thumbnail(path: String) =
        runBlocking { PlanningCenterClient.fetchThumbnailBytes(server.url(path)) }

    // ── Downloading an attachment ───────────────────────────────────────────────

    @Test
    fun `an attachment is written where it was asked for`() {
        val target = File(dir, "plan.pdf")

        val outcome = download("/attachment.pdf", target)

        assertTrue(outcome is PlanningCenterClient.FileDownloadOutcome.Success, "got $outcome")
        assertEquals(target, (outcome as PlanningCenterClient.FileDownloadOutcome.Success).file)
        assertTrue(target.exists())
    }

    /**
     * A download that fails has to say *why* somewhere a human can reach.
     *
     * `NetworkError` on its own does not distinguish a refused connection from a timeout from a
     * closed client, and each wants a different answer. The `CrashReporter.reportWarning` alongside
     * carries the exception but returns immediately when Sentry is not enabled — which is every test
     * run, and every operator who has not opted in. This class's own
     * `an attachment is written where it was asked for` failed once in a full suite with nothing but
     * the word `NetworkError` recorded, which is what prompted this.
     *
     * `System.setErr` is JVM-wide, so it is restored in a `finally` rather than after the assertions.
     *
     * Points at port 1 rather than stopping [server]. Stopping the fixture mid-test frees its port,
     * and the next test binds port 0 and can be handed the same number back — which showed up
     * immediately as a sibling test downloading from a server that 404s its route. Port 1 is
     * privileged, never bound, and refuses instantly.
     */
    @Test
    fun `a failed download reports the reason, not just that it failed`() {
        val deadUrl = "http://127.0.0.1:1/attachment.pdf"
        val captured = ByteArrayOutputStream()
        val realErr = System.err

        val outcome = try {
            System.setErr(PrintStream(captured, true))
            runBlocking { PlanningCenterClient.downloadFile(deadUrl, File(dir, "plan.pdf")) }
        } finally {
            System.setErr(realErr)
        }

        assertEquals(PlanningCenterClient.FileDownloadOutcome.NetworkError, outcome)
        val reported = captured.toString()
        assertTrue(reported.contains("planning-center"), "nothing was reported at all: '$reported'")
        assertTrue(
            reported.contains(deadUrl),
            "the URL that failed has to be in it, or it names no target: '$reported'"
        )
        assertTrue(
            reported.substringAfter("failed — ").trim().isNotEmpty(),
            "and the exception, or it says no more than the outcome already did: '$reported'"
        )
    }

    @Test
    fun `every byte arrives exactly as it was sent`() {
        val target = File(dir, "plan.pdf")

        download("/attachment.pdf", target)

        assertContentEquals(
            payload,
            target.readBytes(),
            "attachments are PDFs and images; a byte read as text is a file that will not open",
        )
    }

    @Test
    fun `the folder is created if it is not there yet`() {
        val target = File(dir, "plans/2026/sunday/plan.pdf")

        val outcome = download("/attachment.pdf", target)

        assertTrue(outcome is PlanningCenterClient.FileDownloadOutcome.Success, "got $outcome")
        assertTrue(target.exists(), "the import folder is built as the download runs")
    }

    @Test
    fun `downloading again replaces what was there`() {
        val target = File(dir, "plan.pdf").also { it.writeText("an older copy") }

        download("/attachment.pdf", target)

        assertContentEquals(payload, target.readBytes(), "re-importing a plan must not leave the old file")
    }

    @Test
    fun `an attachment with no content still counts as downloaded`() {
        val target = File(dir, "empty.pdf")

        val outcome = download("/empty", target)

        assertTrue(outcome is PlanningCenterClient.FileDownloadOutcome.Success, "got $outcome")
        assertEquals(0, target.length(), "an empty attachment is what the server said it was")
    }

    // ── When it goes wrong ──────────────────────────────────────────────────────

    @Test
    fun `an attachment that is not there is a failure, not a network problem`() {
        val target = File(dir, "plan.pdf")

        val outcome = download("/missing", target)

        assertEquals(
            PlanningCenterClient.FileDownloadOutcome.Failure,
            outcome,
            "a 404 will fail the same way next time; only a network error is worth retrying",
        )
    }

    @Test
    fun `a server error is a failure`() {
        assertEquals(PlanningCenterClient.FileDownloadOutcome.Failure, download("/error", File(dir, "plan.pdf")))
    }

    @Test
    fun `an expired download link is a failure`() {
        // The API hands out short-lived URLs; using a stale one comes back as a refusal.
        assertEquals(PlanningCenterClient.FileDownloadOutcome.Failure, download("/expired", File(dir, "plan.pdf")))
    }

    @Test
    fun `a failed download leaves no file behind`() {
        val target = File(dir, "plan.pdf")

        download("/missing", target)

        assertFalse(
            target.exists(),
            "a half-written or empty file would look like a downloaded attachment in the picker",
        )
    }

    @Test
    fun `a failed download does not disturb an earlier copy`() {
        val target = File(dir, "plan.pdf").also { it.writeText("last week's plan") }

        download("/error", target)

        assertEquals("last week's plan", target.readText(), "a failed refresh must not destroy what was there")
    }

    @Test
    fun `a host that cannot be reached is a network problem`() {
        val target = File(dir, "plan.pdf")

        val outcome = runBlocking {
            // Port 1, for the reason the sibling test above gives. A port from `ServerSocket(0)`
            // closed again immediately goes straight back into the ephemeral pool, and the next
            // test's host binds port 0 and can be handed the same number: this download came back
            // answered 404 by a server whose routes were already gone, and next door the fixture
            // timed out waiting for routes on a port this test had just finished with.
            PlanningCenterClient.downloadFile("http://127.0.0.1:1/attachment.pdf", target)
        }

        assertEquals(
            PlanningCenterClient.FileDownloadOutcome.NetworkError,
            outcome,
            "a dropped connection is worth offering a retry for",
        )
        assertFalse(target.exists())
    }

    @Test
    fun `a url that is not a url is a network problem rather than a crash`() {
        val outcome = runBlocking {
            PlanningCenterClient.downloadFile("not a url at all", File(dir, "plan.pdf"))
        }

        assertEquals(PlanningCenterClient.FileDownloadOutcome.NetworkError, outcome)
    }

    // ── Thumbnails ──────────────────────────────────────────────────────────────

    @Test
    fun `a thumbnail comes back as its bytes`() {
        assertContentEquals(payload, thumbnail("/attachment.pdf"))
    }

    @Test
    fun `a thumbnail that is not there comes back as nothing`() {
        assertNull(thumbnail("/missing"), "a missing preview shows a placeholder, it does not fail the import")
    }

    @Test
    fun `a thumbnail from a server error comes back as nothing`() {
        assertNull(thumbnail("/error"))
    }

    @Test
    fun `a thumbnail from a host that is not there comes back as nothing`() {
        // Port 1 again, and for the same reason: a recycled ephemeral port gets answered, not refused.
        assertNull(runBlocking { PlanningCenterClient.fetchThumbnailBytes("http://127.0.0.1:1/thumb.png") })
    }

    @Test
    fun `an empty thumbnail is still an answer`() {
        assertContentEquals(
            ByteArray(0),
            thumbnail("/empty"),
            "an empty body is not the same as a failed request",
        )
    }
}
