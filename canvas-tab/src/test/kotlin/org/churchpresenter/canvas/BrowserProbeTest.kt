package org.churchpresenter.canvas

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two probes the browser source runs before it has a browser: finding one, and waiting for it.
 *
 * Both spawn or dial something outside the JVM, and both were written to fail quietly — a missing
 * Chrome and a Chrome that never comes up are ordinary on an operator's machine, and neither may
 * throw. That makes them exactly the code where a wrong answer goes unnoticed: `browserOnPath`
 * returning a path that does not exist would have the launcher fail with a native error instead of
 * the app's own "no browser found" message.
 */
class BrowserProbeTest {

    // ── Finding a browser on the PATH ──────────────────────────────────────────

    private val lookup = if (System.getProperty("os.name", "").lowercase().contains("win")) "where" else "which"

    @Test
    fun `a program that is on the path comes back as a file that exists`() {
        // Not a browser — the point is the shape of the answer, and every platform has a shell.
        val found = SharedBrowserFrameCache.browserOnPath(lookup, "java")

        assertTrue(found != null && java.io.File(found).exists(), "expected a real path, got $found")
    }

    @Test
    fun `a program that is not installed comes back as nothing`() {
        assertNull(SharedBrowserFrameCache.browserOnPath(lookup, "definitely-not-a-browser-xyzzy"))
    }

    @Test
    fun `a lookup command that does not exist is swallowed`() {
        // The launcher must not crash on a machine with no `which` at all.
        assertNull(SharedBrowserFrameCache.browserOnPath("no-such-lookup-command-xyzzy", "java"))
    }

    @Test
    fun `an empty program name finds nothing rather than something`() {
        assertNull(SharedBrowserFrameCache.browserOnPath(lookup, ""))
    }

    // ── Waiting for DevTools to answer ─────────────────────────────────────────

    @Test
    fun `a DevTools endpoint that answers is ready straight away`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/json/version") { exchange ->
            val body = """{"Browser":"HeadlessChrome/1.0"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            assertTrue(runBlocking { SharedBrowserFrameCache.waitForCdpReady(server.address.port, 5_000) })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an endpoint that refuses the connection is given up on, not waited on for ever`() {
        // A port nothing is listening on: every poll throws, and the loop has to end on its own
        // deadline rather than propagating the refusal.
        val deadPort = ServerSocket(0).use { it.localPort }

        val ready = runBlocking { SharedBrowserFrameCache.waitForCdpReady(deadPort, 150) }

        assertFalse(ready, "a browser that never came up must be reported as not ready")
    }

    @Test
    fun `an endpoint answering with an error status is not treated as ready`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/json/version") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            // Something is listening, so nothing throws — only the status code says it is not up yet.
            assertFalse(runBlocking { SharedBrowserFrameCache.waitForCdpReady(server.address.port, 150) })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a deadline already past polls nothing at all`() {
        var hits = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/json/version") { exchange ->
            hits++
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            assertFalse(runBlocking { SharedBrowserFrameCache.waitForCdpReady(server.address.port, 0) })
            assertEquals(0, hits, "a zero budget must not cost a request")
        } finally {
            server.stop(0)
        }
    }
}
