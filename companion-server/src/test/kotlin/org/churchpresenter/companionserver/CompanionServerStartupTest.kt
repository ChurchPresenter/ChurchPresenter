package org.churchpresenter.companionserver

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.utils.Constants
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Getting the server onto a port, and refusing an upload before reading it.
 *
 * Both are things every other suite depends on and none of them tests: they all start a server and
 * read the port back out of [CompanionServer.serverUrl] precisely *because* the requested port is
 * not always the one it lands on. That walk is what the first half here pins.
 *
 * The second half is the size guard on uploads. It answers from the `Content-Length` header, before
 * a byte of the body is read, which is the only reason a 200 MB refusal is cheap — so the test has
 * to lie about the length rather than actually send that much, and that means a raw socket rather
 * than a client that would compute the header itself.
 */
class CompanionServerStartupTest {

    private val servers = mutableListOf<CompanionServer>()
    private val sockets = mutableListOf<ServerSocket>()

    @AfterTest
    fun tearDown() {
        servers.forEach { runCatching { it.stop() } }
        sockets.forEach { runCatching { it.close() } }
    }

    /** Starts a server and returns the port it actually bound, not the one it was asked for. */
    private fun startOn(requested: Int): Int {
        val server = CompanionServer().also { servers += it }
        server.start(port = requested)
        return runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
    }

    @Test
    fun `a free port is taken as asked`() {
        val requested = testPort(39_951)

        assertEquals(requested, startOn(requested), "nothing was holding it, so nothing should move")
    }

    @Test
    fun `a port already in use is walked past rather than failing to start`() {
        val requested = testPort(39_953)
        sockets += ServerSocket(requested)

        val bound = startOn(requested)

        assertNotEquals(requested, bound, "the socket above holds that port")
        assertTrue(bound > requested, "the scan walks upward from the requested port")
        assertTrue(
            runBlocking {
                HttpClient(CIO).use { it.get("http://127.0.0.1:$bound${Constants.ENDPOINT_STATUS}").status.value } < 500
            },
            "the server is genuinely listening where it says it is",
        )
    }

    @Test
    fun `stopping twice is harmless`() {
        val server = CompanionServer().also { servers += it }
        server.start(port = testPort(39_955))
        runBlocking { withTimeoutOrNull(10_000) { while (!server.isRunning.value) kotlinx.coroutines.delay(25) } }

        server.stop()
        server.stop()

        assertTrue(!server.isRunning.value)
        assertEquals("", server.serverUrl.value)
    }

    @Test
    fun `an upload is refused on its declared size, before the body is read`() {
        val port = startOn(testPort(39_957))
        val body = """{"name":"deck.pptx","data":"data:x;base64,AAAA"}"""

        // A hand-written request: the point is a Content-Length far larger than what follows, which
        // no HTTP client would let us send. The server must answer from the header alone.
        val response = Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(
                (
                    "POST ${Constants.ENDPOINT_PRESENTATIONS_UPLOAD} HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$port\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${300L * 1024 * 1024}\r\n" +
                        "\r\n" + body
                    ).toByteArray()
            )
            socket.getOutputStream().flush()
            socket.soTimeout = 5_000
            // The status line only: the connection is keep-alive, so reading to EOF would block
            // until the timeout even after the server has answered.
            socket.getInputStream().bufferedReader().readLine().orEmpty()
        }

        assertTrue(
            response.startsWith("HTTP/1.1 413"),
            "300 MB has to be refused as Payload Too Large without waiting for it: $response",
        )
    }
}
