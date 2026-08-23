package org.churchpresenter.companionserver

import io.ktor.server.engine.BaseApplicationResponse
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What happens when a follower or a phone abandons a file download half way.
 *
 * The reported case was an 11 MB Bible module served to an Instance Link follower: the response
 * ended with none of it written, Ktor raised `BodyLengthIsTooSmall`, and because nothing caught it
 * the call went through `StatusPages` — which tried to write an error body into a response whose
 * headers were already on the wire — and was reported as a server fault. A download that stops
 * because the other end left is not a fault of this server's, and there is nothing left to send.
 *
 * The real abort cannot be staged here without racing a socket, so the rule is exercised at the
 * step that decides: the send itself is the parameter.
 */
class FileDownloadDropTest {

    private val server = CompanionServer()

    private fun send(failure: Throwable?) = runBlocking {
        sendOrDropOnClientExit(server, "/api/bible/file/translation/{index}") {
            if (failure != null) throw failure
        }
    }

    @Test
    fun `a body that ends before its Content-Length is dropped rather than reported`() {
        send(BaseApplicationResponse.BodyLengthIsTooSmall(11_066_478L, 0L))
    }

    @Test
    fun `a socket that dies mid-download is dropped too`() {
        send(IOException("Connection reset by peer"))
    }

    @Test
    fun `a fault of our own still escapes`() {
        val failure = assertFailsWith<IllegalStateException> { send(IllegalStateException("bad state")) }
        assertEquals("bad state", failure.message)
    }

    @Test
    fun `an ordinary send is left alone`() {
        var sent = false
        runBlocking {
            sendOrDropOnClientExit(server, "/api/bible/file") { sent = true }
        }
        assertEquals(true, sent)
    }
}
