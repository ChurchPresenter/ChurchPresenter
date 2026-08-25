package org.churchpresenter.canvas

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How a browser source finds the tab it is going to render.
 *
 * Before any CDP traffic happens, [SharedBrowserFrameCache.getPageWebSocketUrl] asks the freshly
 * launched browser's `/json` endpoint what targets it has and picks one to attach to. Everything
 * downstream — the whole WebSocket protocol client [SharedBrowserFrameCacheTest] covers — is aimed
 * at whatever this returns, so choosing the wrong target means the output renders the wrong thing
 * (or nothing) with no error anywhere.
 *
 * **The rule worth pinning is which target it picks.** A browser lists more than its page: extension
 * background pages, service workers and the like are all in `/json`, and several of them can sort
 * ahead of the page. Attaching to a background page yields a connection that works, accepts screen
 * capture commands and produces no frames — an output that is black for the congregation and healthy
 * from the app's point of view. So the `type == "page"` filter is the whole point of this function,
 * and the tests below are weighted toward it rather than toward the happy path.
 *
 * Driven against a real Ktor server on an ephemeral port — the endpoint is plain HTTP and a JSON
 * body, so there is no reason to fake the client's own parser. Same approach as
 * [SharedBrowserFrameCacheTest]'s WebSocket fake, one layer earlier.
 */
class SharedBrowserFrameCachePageDiscoveryTest {

    private val servers = mutableListOf<FakeDevToolsEndpoint>()

    @AfterTest
    fun cleanUp() {
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    /** Stands in for the `/json` target list a headless Chrome or Edge serves on its debug port. */
    private class FakeDevToolsEndpoint(private val body: String) {
        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            routing {
                get("/json") { call.respondText(body, io.ktor.http.ContentType.Application.Json) }
            }
        }

        fun start() {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        fun stop() = server.stop(0, 0)
    }

    private fun serving(body: String): Int {
        val endpoint = FakeDevToolsEndpoint(body)
        servers.add(endpoint)
        endpoint.start()
        return endpoint.port
    }

    @Test
    fun `the page tab is chosen over a background page listed ahead of it`() {
        val port = serving(
            """
            [
              {"type":"background_page","webSocketDebuggerUrl":"ws://localhost/devtools/page/EXT"},
              {"type":"page","webSocketDebuggerUrl":"ws://localhost/devtools/page/REAL"}
            ]
            """.trimIndent()
        )

        assertEquals(
            "ws://localhost/devtools/page/REAL",
            SharedBrowserFrameCache.getPageWebSocketUrl(port),
            "ordering must not decide this — the type does",
        )
    }

    @Test
    fun `a service worker ahead of the page does not win either`() {
        val port = serving(
            """
            [
              {"type":"service_worker","webSocketDebuggerUrl":"ws://localhost/devtools/page/SW"},
              {"type":"page","webSocketDebuggerUrl":"ws://localhost/devtools/page/REAL"}
            ]
            """.trimIndent()
        )

        assertEquals("ws://localhost/devtools/page/REAL", SharedBrowserFrameCache.getPageWebSocketUrl(port))
    }

    @Test
    fun `with no page-typed target at all it falls back to the first one`() {
        val port = serving(
            """[{"type":"other","webSocketDebuggerUrl":"ws://localhost/devtools/page/ONLY"}]"""
        )

        // Deliberate: a browser build that labels its tab something unexpected should still render
        // rather than refuse outright. The fallback is only reached once the filter finds nothing.
        assertEquals("ws://localhost/devtools/page/ONLY", SharedBrowserFrameCache.getPageWebSocketUrl(port))
    }

    @Test
    fun `an empty target list yields no url`() {
        assertNull(SharedBrowserFrameCache.getPageWebSocketUrl(serving("[]")))
    }

    @Test
    fun `a target with no debugger url yields no url`() {
        val port = serving("""[{"type":"page","title":"no socket here"}]""")

        assertNull(SharedBrowserFrameCache.getPageWebSocketUrl(port))
    }

    @Test
    fun `a body that is not the expected json yields no url rather than throwing`() {
        // The browser is a separate process on a port anything could be listening on; a parse failure
        // has to come back as "no target" so the caller retries, not as an exception through it.
        assertNull(SharedBrowserFrameCache.getPageWebSocketUrl(serving("""{"not":"an array"}""")))
    }

    @Test
    fun `nothing listening on the port yields no url`() {
        // A port taken and released, so it is genuinely closed and the connection is refused at once.
        // A server that was constructed but never started would still hold its listening socket, and
        // the request would hang instead of failing — this call sets no read timeout.
        val deadPort = ServerSocket(0).use { it.localPort }

        assertNull(SharedBrowserFrameCache.getPageWebSocketUrl(deadPort))
    }
}
