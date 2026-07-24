package org.churchpresenter.app.churchpresenter.utils

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * [LiveMapReporter] fires an anonymous city-level ping on launch. The launch/retry is network and
 * timing bound, but the two decisions that shape the request are pure: which os tag the platform
 * maps to, and how the ping url is assembled — including the dev-build split that keeps IDE/`run`
 * launches out of the real-user stats, and the opt-in updateCheck parameter.
 */
class LiveMapReporterTest {

    @Test
    fun `os names map to the website's platform tags`() {
        assertEquals("windows", LiveMapReporter.osTag("Windows 11"))
        assertEquals("macos", LiveMapReporter.osTag("Mac OS X"))
        assertEquals("linux", LiveMapReporter.osTag("Linux"))
    }

    @Test
    fun `an unrecognised os is reported as unknown rather than guessed`() {
        assertEquals("unknown", LiveMapReporter.osTag("SunOS"))
        assertEquals("unknown", LiveMapReporter.osTag(""))
    }

    @Test
    fun `the ping url carries platform, os and version`() {
        val url = LiveMapReporter.buildPingUrl(
            os = "macos", version = "26.1.0", updateCheckInterval = null, isDevBuild = false,
        )
        assertTrue(url.startsWith("https://www.churchpresenter.org/api/ping?"), url)
        assertTrue("platform=desktop" in url)
        assertTrue("os=macos" in url)
        assertTrue("version=26.1.0" in url)
    }

    @Test
    fun `a dev build is tagged with the dev source, a release build is not`() {
        val dev = LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = true)
        assertTrue("src=dev" in dev, dev)

        val release = LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = false)
        assertFalse("src=dev" in release, release)
    }

    @Test
    fun `the configured update-check interval is included when set and omitted when null`() {
        val withInterval = LiveMapReporter.buildPingUrl(
            "windows", "26.1.0", UpdateCheckInterval.WEEKLY, isDevBuild = false,
        )
        assertTrue("updateCheck=weekly" in withInterval, withInterval)

        val without = LiveMapReporter.buildPingUrl("windows", "26.1.0", null, isDevBuild = false)
        assertFalse("updateCheck" in without, without)
    }

    private fun pingThatFails(times: Int): Pair<suspend () -> Boolean, () -> Int> {
        var calls = 0
        val ping: suspend () -> Boolean = {
            calls++
            calls > times
        }
        return ping to { calls }
    }

    private fun retry(ping: suspend () -> Boolean, quick: Int = 3, slow: Int = 15) = runBlocking {
        LiveMapReporter.pingWithRetry(
            ping,
            quickAttempts = quick, quickDelay = Duration.ZERO,
            slowAttempts = slow, slowDelay = Duration.ZERO,
        )
    }

    @Test
    fun `a first-try success pings exactly once`() {
        val (ping, calls) = pingThatFails(0)
        retry(ping)
        assertEquals(1, calls(), "a success on the first attempt must not retry")
    }

    @Test
    fun `it stops as soon as a quick retry succeeds`() {
        val (ping, calls) = pingThatFails(2) // 1st and 2nd fail, 3rd succeeds
        retry(ping)
        assertEquals(3, calls(), "stops on the third (successful) quick attempt")
    }

    @Test
    fun `it falls through to the slow retries when every quick attempt fails`() {
        val (ping, calls) = pingThatFails(3) // 3 quick fail, then succeeds on the 1st slow
        retry(ping)
        assertEquals(4, calls(), "3 quick + 1 slow attempt before success")
    }

    @Test
    fun `it gives up after quick plus slow attempts are exhausted`() {
        val (ping, calls) = pingThatFails(Int.MAX_VALUE) // never succeeds
        retry(ping, quick = 3, slow = 15)
        assertEquals(18, calls(), "every attempt tried, then it stops rather than looping forever")
    }

    private class Captured(var hits: Int = 0, var installId: String? = null)

    private fun withServer(block: (url: String, captured: Captured) -> Unit) {
        val captured = Captured()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/ping") { ex ->
            captured.hits++
            captured.installId = ex.requestHeaders.getFirst("X-Install-Id")
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/api/ping?platform=desktop", captured)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `ping sends the request and forwards the install id header`() {
        withServer { url, captured ->
            runBlocking { LiveMapReporter.ping(url, installId = "install-abc") }
            assertEquals(1, captured.hits, "a reachable server is pinged exactly once, with no retry")
            assertEquals("install-abc", captured.installId)
        }
    }

    @Test
    fun `ping omits the install id header when none is supplied`() {
        withServer { url, captured ->
            runBlocking { LiveMapReporter.ping(url, installId = null) }
            assertEquals(1, captured.hits)
            assertNull(captured.installId, "a null id must not send the X-Install-Id header")
        }
    }

    @Test
    fun `ping omits the install id header when the id is blank`() {
        withServer { url, captured ->
            runBlocking { LiveMapReporter.ping(url, installId = "   ") }
            assertEquals(1, captured.hits)
            assertNull(captured.installId, "a blank id is treated as no id")
        }
    }
}
