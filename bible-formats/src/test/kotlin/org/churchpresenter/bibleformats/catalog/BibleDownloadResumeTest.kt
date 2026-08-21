package org.churchpresenter.bibleformats.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [BibleInstallSupport.downloadTo]'s behaviour when the body stops arriving part-way.
 *
 * This is what a user on a throttled link actually hits: the connection is alive, bytes flow for a
 * while, then nothing. Before the resume existed, one such stall threw away every byte already on
 * disk, so a module that took longer to fetch than the stall interval could never install, however
 * many times it was retried by hand.
 *
 * A stall is modelled as a body shorter than the `Content-Length` the response promised, which is
 * what a dropped connection looks like from the reading end — and, unlike a channel closed with an
 * exception, is something `MockEngine` reproduces faithfully. Nothing here waits on a clock: every
 * call passes `retryFloorMs = 0`, so the backoff is exactly zero.
 */
class BibleDownloadResumeTest {

    private lateinit var dir: File
    private lateinit var destination: File

    /** 1 KiB of non-repeating content, so a spliced or restarted file cannot pass by accident. */
    private val body = ByteArray(1024) { (it * 31 + 7).toByte() }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-download-resume").toFile()
        destination = File(dir, "module.zip")
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun rangeStartOf(range: String?): Int =
        range?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0

    private fun download(
        engine: MockEngine,
        expectedBytes: Long = body.size.toLong(),
        onProgress: (Float) -> Unit = {},
    ) = runBlocking {
        BibleInstallSupport.downloadTo(
            url = "https://example.invalid/module.zip",
            destination = destination,
            http = HttpClient(engine),
            expectedBytes = expectedBytes,
            retryFloorMs = 0L,
            onProgress = onProgress,
        )
    }

    /** A whole-body answer that stops after [delivered] bytes but claims the full length. */
    private fun MockRequestHandleScope.truncated(delivered: Int) = respond(
        content = ByteReadChannel(body.copyOfRange(0, delivered)),
        headers = Headers.build { append(HttpHeaders.ContentLength, body.size.toString()) },
    )

    /** The honest tail from [start], as a server that understands `Range` answers. */
    private fun MockRequestHandleScope.tailFrom(start: Int) = respond(
        content = ByteReadChannel(body.copyOfRange(start, body.size)),
        status = HttpStatusCode.PartialContent,
        headers = Headers.build {
            append(HttpHeaders.ContentLength, (body.size - start).toString())
            append(HttpHeaders.ContentRange, "bytes $start-${body.size - 1}/${body.size}")
        },
    )

    private fun MockRequestHandleScope.whole() = respond(
        content = ByteReadChannel(body),
        headers = Headers.build { append(HttpHeaders.ContentLength, body.size.toString()) },
    )

    @Test
    fun `a stalled download resumes from the bytes already on disk`() {
        val engine = MockEngine { request ->
            val start = rangeStartOf(request.headers[HttpHeaders.Range])
            if (start == 0) truncated(delivered = 400) else tailFrom(start)
        }

        val result = download(engine)

        assertEquals(200, result.status)
        assertEquals(body.size.toLong(), result.bytesWritten)
        assertContentEquals(body, destination.readBytes())
        assertEquals(2, engine.requestHistory.size)
        assertEquals("bytes=400-", engine.requestHistory[1].headers[HttpHeaders.Range])
    }

    @Test
    fun `a server that ignores the range starts the file over instead of splicing it`() {
        // The second answer is a 200 with the whole body, not the tail asked for. Appending it would
        // leave a file 400 bytes too long and corrupt from the seam onwards.
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Range] == null) truncated(delivered = 400) else whole()
        }

        val result = download(engine)

        assertEquals(body.size.toLong(), result.bytesWritten)
        assertContentEquals(body, destination.readBytes())
    }

    @Test
    fun `a 206 whose range starts somewhere else is not trusted as a tail`() {
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Range] == null) {
                truncated(delivered = 400)
            } else {
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.PartialContent,
                    headers = Headers.build {
                        append(HttpHeaders.ContentLength, body.size.toString())
                        append(HttpHeaders.ContentRange, "bytes 0-${body.size - 1}/${body.size}")
                    },
                )
            }
        }

        val result = download(engine)

        assertEquals(body.size.toLong(), result.bytesWritten)
        assertContentEquals(body, destination.readBytes())
    }

    @Test
    fun `a range request for a file that is already complete finishes it`() {
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Range] == null) {
                // Everything arrived, but the length header claims one more byte than exists, so the
                // download resumes — and is told there is no such byte.
                respond(
                    content = ByteReadChannel(body),
                    headers = Headers.build { append(HttpHeaders.ContentLength, (body.size + 1).toString()) },
                )
            } else {
                respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.RequestedRangeNotSatisfiable)
            }
        }

        val result = download(engine)

        assertEquals(200, result.status)
        assertEquals(body.size.toLong(), result.bytesWritten)
        assertContentEquals(body, destination.readBytes())
    }

    @Test
    fun `a download that never delivers a byte gives up as stalled`() {
        val engine = MockEngine { truncated(delivered = 0) }

        val failure = assertFailsWith<BibleInstallSupport.DownloadStalledException> { download(engine) }

        assertEquals(3, engine.requestHistory.size, "three fruitless attempts, then it stops")
        assertEquals(3, failure.attempts)
        assertEquals(0L, failure.bytesWritten)
    }

    @Test
    fun `progress buys back the retry budget, so a link that keeps stopping still finishes`() {
        // The reported failure in miniature: every attempt stops early, but each one moves the file
        // forward. A flat budget of three attempts would abandon this download at 300 of 1024 bytes.
        val chunk = 100
        val engine = MockEngine { request ->
            val start = rangeStartOf(request.headers[HttpHeaders.Range])
            val end = minOf(start + chunk, body.size)
            when {
                end == body.size && start == 0 -> whole()
                end == body.size -> tailFrom(start)
                start == 0 -> truncated(delivered = end)
                else -> respond(
                    content = ByteReadChannel(body.copyOfRange(start, end)),
                    status = HttpStatusCode.PartialContent,
                    headers = Headers.build {
                        append(HttpHeaders.ContentLength, (body.size - start).toString())
                        append(HttpHeaders.ContentRange, "bytes $start-${body.size - 1}/${body.size}")
                    },
                )
            }
        }

        val result = download(engine)

        assertEquals(body.size.toLong(), result.bytesWritten)
        assertContentEquals(body, destination.readBytes())
        assertTrue(engine.requestHistory.size > 3, "was ${engine.requestHistory.size} attempts")
    }

    @Test
    fun `an error page is never retried`() {
        val engine = MockEngine { respond(content = ByteReadChannel("not found"), status = HttpStatusCode.NotFound) }

        val result = download(engine)

        assertEquals(404, result.status)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `a connection that fails outright is reported as a failure, not as a stall`() {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            throw IOException("no route to host")
        }

        val failure = assertFailsWith<IOException> { download(engine) }

        assertTrue(
            failure !is BibleInstallSupport.DownloadStalledException,
            "an unreachable host is not a slow connection and must not be described as one",
        )
        assertEquals(3, attempts)
    }

    @Test
    fun `a module download asks for a longer connect than the shared client allows`() {
        // The shared client's 8 s connect is sized for the catalogue, which has a cached listing to
        // fall back on. A module has none, and the reported failure was a user whose handshake to
        // ebible.org never finished inside it — so the request must override that, not inherit it.
        val engine = MockEngine { whole() }

        download(engine)

        val configured = engine.requestHistory.single()
            .getCapabilityOrNull(HttpTimeoutCapability)
            ?.connectTimeoutMillis
        assertNotNull(configured, "the download request set no connect timeout of its own")
        assertTrue(configured >= 30_000L, "was $configured ms")
    }

    @Test
    fun `a connection the engine tears down resumes instead of passing as a cancellation`() {
        // What the reported warning actually was: the response job dies with the connection, so the
        // read fails with `Job was cancelled` while nothing here asked for anything to stop. Taking
        // that at face value abandoned the install without a word and without a resume.
        // MockEngine records an attempt only once its handler returns, so attempts are counted here.
        var attempts = 0
        val engine = MockEngine { request ->
            attempts++
            if (attempts == 1) throw CancellationException("Job was cancelled")
            val start = rangeStartOf(request.headers[HttpHeaders.Range])
            if (start == 0) whole() else tailFrom(start)
        }

        val result = download(engine)

        assertEquals(body.size.toLong(), result.bytesWritten)
        assertContentEquals(body, destination.readBytes())
        assertEquals(2, attempts)
    }

    @Test
    fun `the user closing the dialog still cancels the download`() {
        val job = Job()
        var attempts = 0
        val engine = MockEngine {
            attempts++
            job.cancel()
            throw CancellationException("Job was cancelled")
        }

        assertFailsWith<CancellationException> {
            runBlocking(job) {
                BibleInstallSupport.downloadTo(
                    url = "https://example.invalid/module.zip",
                    destination = destination,
                    http = HttpClient(engine),
                    expectedBytes = body.size.toLong(),
                    retryFloorMs = 0L,
                ) {}
            }
        }

        assertEquals(1, attempts, "a cancelled install must not retry")
    }

    @Test
    fun `a body of unknown length is taken at its word`() {
        // eBible's catalogue publishes no size and its server may omit Content-Length, so there is
        // nothing to compare against — an early end has to be accepted rather than retried forever.
        val engine = MockEngine { respond(content = ByteReadChannel(body.copyOfRange(0, 400))) }

        val result = download(engine, expectedBytes = 0)

        assertEquals(400L, result.bytesWritten)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `the progress bar never goes backwards across a restart`() {
        val engine = MockEngine { request ->
            // The second attempt ignores the range and starts from zero again.
            if (request.headers[HttpHeaders.Range] == null) truncated(delivered = 900) else whole()
        }

        val fractions = mutableListOf<Float>()
        download(engine) { fractions.add(it) }

        assertEquals(fractions.sorted(), fractions, "reported $fractions")
        assertTrue(fractions.last() <= BibleInstallSupport.DOWNLOAD_END)
    }
}
