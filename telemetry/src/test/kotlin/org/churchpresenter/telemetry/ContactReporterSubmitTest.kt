package org.churchpresenter.telemetry

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ContactReporter.submit] against a host of the test's own: what each answer the form can give
 * becomes, and what is sent to it.
 */
class ContactReporterSubmitTest {

    private class Received(var userAgent: String? = null, var body: String = "")

    /** A one-route host answering every post with [status] and [body]. */
    private fun withHost(status: Int, body: String = "", block: (endpoint: String, received: Received) -> Unit) {
        val received = Received()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/contact-app") { ex ->
            received.userAgent = ex.requestHeaders.getFirst("User-Agent")
            received.body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/api/contact-app", received)
        } finally {
            server.stop(0)
        }
    }

    private val request = ContactReporter.ContactRequest(type = "bug", name = "Sam", message = "hello")

    @Test
    fun `an accepted message is a success, sent as JSON under this build's user agent`() =
        withHost(status = 200) { endpoint, received ->
            val outcome = runBlocking { ContactReporter.submit(request, "26.1.0", endpoint) }

            assertEquals(ContactReporter.Outcome.Success, outcome)
            assertEquals("ChurchPresenter/26.1.0", received.userAgent)
            assertTrue("\"message\":\"hello\"" in received.body, received.body)
        }

    @Test
    fun `a rejected message carries the reason the form gave`() =
        withHost(status = 400, body = """{"error":"Message is too short"}""") { endpoint, _ ->
            val outcome = runBlocking { ContactReporter.submit(request, "26.1.0", endpoint) }

            assertEquals(ContactReporter.Outcome.Invalid("Message is too short"), outcome)
        }

    @Test
    fun `a host that cannot be reached is a network error`() {
        val closed = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = closed.address.port
        closed.stop(0)

        val outcome = runBlocking {
            ContactReporter.submit(request, "26.1.0", "http://127.0.0.1:$port/api/contact-app")
        }

        assertEquals(ContactReporter.Outcome.NetworkError, outcome)
    }
}
