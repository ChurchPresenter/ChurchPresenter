package org.churchpresenter.server

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [CompanionServer.requestStart], [CompanionServer.requestStop] and [CompanionServer.requestRestart]:
 * the lifecycle the Server settings page drives, off the event thread (CHURCH-PRESENTER-DESKTOP-EK —
 * a stop there held the Options dialog for 1.5 s while Netty closed).
 *
 * A real server on a test port, stopped with no grace. Every wait is a join on the job a request
 * returns; nothing polls or sleeps.
 */
class CompanionServerLifecycleRequestTest {

    private val server = CompanionServer(shutdownGraceMs = 0)
    private val port = testPort(39_970)

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    private fun Job.await() = runBlocking { join() }

    @Test
    fun `a requested start brings the server up and a requested stop takes it down`() {
        server.requestStart(port, "127.0.0.1").await()

        assertTrue(server.isRunning.value, "the start must have run when its job completes")
        assertTrue(server.serverUrl.value.startsWith("http://127.0.0.1:"), server.serverUrl.value)

        server.requestStop().await()

        assertFalse(server.isRunning.value)
        assertEquals("", server.serverUrl.value, "a stopped server offers no URL")
    }

    @Test
    fun `a requested restart leaves the server up on the port it was given`() {
        server.requestStart(port, "127.0.0.1").await()
        val before = server.serverUrl.value

        server.requestRestart(port, "127.0.0.1").await()

        assertTrue(server.isRunning.value, "a restart ends with the server up")
        assertEquals(before, server.serverUrl.value, "on the same port, which the stop freed")
    }

    @Test
    fun `requests run in the order they were made`() {
        // A stop asked for straight after a start must not overtake it: the switch flicked on and
        // off again has to end off. Only the last job is joined; the order is the dispatcher's.
        server.requestStart(port, "127.0.0.1")
        server.requestStop().await()

        assertFalse(server.isRunning.value, "the stop ran after the start, not before it")
    }
}
