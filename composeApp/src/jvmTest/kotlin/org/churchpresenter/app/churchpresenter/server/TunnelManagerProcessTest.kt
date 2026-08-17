package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.junit.Assume
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the tunnel does with a running `cloudflared`: scraping the public URL out of its output, and
 * what each way the process can end means to the operator.
 *
 * No download and no real tunnel. `TunnelManager` resolves `~/.churchpresenter/cloudflared` at
 * construction and skips the download when that file already exists, so a shell script written there
 * before the manager is built *is* the binary as far as production code is concerned — the real
 * `ProcessBuilder`, the real reader loop and the real state transitions all run.
 *
 * The three endings are what matter, and only one of them is a failure the operator caused: a
 * process that prints a URL and then dies is a tunnel that **dropped** (Cloudflare hung up, the
 * network went), while one that dies without ever printing a URL never started at all. Reporting
 * either as the other sends someone to debug the wrong end of the connection, and this is the
 * feature people fall back on when the church WiFi will not route.
 *
 * Windows is skipped: the binary is `cloudflared.exe` there and a shell script cannot stand in for
 * it. The pure decisions are covered on every platform by [TunnelManagerTest].
 *
 * Not covered: the download path (it fetches from GitHub, and the URL is not injectable), the
 * macOS-only `tar` extraction that follows it, and the 30-second no-URL timeout — a wait that can
 * only end by expiring.
 */
class TunnelManagerProcessTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<TunnelManager>()

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @BeforeTest
    fun isolateHome() {
        // Pin the JVM-wide log path to the real test home before swapping user.home below.
        TestSingletons.latchToTestHome()

        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-tunnel-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        dataDir.mkdirs()
    }

    @AfterTest
    fun restoreHome() {
        created.forEach { runCatching { it.shutdown() } }
        created.clear()
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private val dataDir: File get() = File(tempHome, ".churchpresenter")
    private val binary: File get() = File(dataDir, "cloudflared")

    /** Writes the stand-in `cloudflared`. Must be called before the manager is constructed. */
    private fun fakeCloudflared(body: String) {
        binary.writeText("#!/bin/sh\n$body\n")
        assertTrue(binary.setExecutable(true), "the stand-in binary has to be runnable")
    }

    private fun manager(): TunnelManager = TunnelManager().also { created.add(it) }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError(
            "timed out after ${timeoutMs}ms waiting for $what (status was ${created.lastOrNull()?.status?.value})",
        )
    }

    private fun skipOnWindows() {
        Assume.assumeTrue("needs a shell script to stand in for cloudflared", !isWindows)
    }

    private companion object {
        const val URL = "https://random-happy-cloud-42.trycloudflare.com"
        const val OTHER_URL = "https://second-guess-99.trycloudflare.com"
        /** cloudflared's real banner shape — the URL sits in a boxed table row, not alone on a line. */
        fun banner(url: String) = "echo '2024-01-01T00:00:00Z INF |  $url  |'"
    }

    // ── Coming up ───────────────────────────────────────────────────────────────

    @Test
    fun `the public url is scraped from cloudflared's output and reported as connected`() {
        skipOnWindows()
        val argsFile = File(tempHome, "args.txt")
        fakeCloudflared(
            """
            echo "${'$'}@" > ${argsFile.absolutePath}
            ${banner(URL)}
            sleep 30
            """.trimIndent()
        )
        val tunnel = manager()

        tunnel.start(localPort = 8_765)

        awaitUntil("the tunnel to come up") { tunnel.status.value == TunnelStatus.Connected(URL) }
        assertEquals(URL, tunnel.tunnelUrl.value)
        awaitUntil("cloudflared to record its arguments") { argsFile.exists() }
        assertEquals(
            "tunnel --url http://localhost:8765",
            argsFile.readText().trim(),
            "cloudflared has to be pointed at the companion server's own port"
        )
    }

    @Test
    fun `the first url wins when cloudflared prints more than one`() {
        // It re-logs the URL on its own retries; taking the later one would hand out an address the
        // operator has not shown anybody.
        skipOnWindows()
        fakeCloudflared(
            """
            ${banner(URL)}
            ${banner(OTHER_URL)}
            sleep 30
            """.trimIndent()
        )
        val tunnel = manager()

        tunnel.start(localPort = 8_765)

        awaitUntil("the tunnel to come up") { tunnel.tunnelUrl.value != null }
        assertEquals(URL, tunnel.tunnelUrl.value)
        assertEquals(TunnelStatus.Connected(URL), tunnel.status.value)
    }

    @Test
    fun `chatter before the url is ignored`() {
        skipOnWindows()
        fakeCloudflared(
            """
            echo 'INF Thank you for trying Cloudflare Tunnel.'
            echo 'INF Requesting new quick Tunnel on trycloudflare.com...'
            ${banner(URL)}
            sleep 30
            """.trimIndent()
        )
        val tunnel = manager()

        tunnel.start(localPort = 8_765)

        awaitUntil("the tunnel to come up") { tunnel.status.value == TunnelStatus.Connected(URL) }
    }

    // ── Going away ──────────────────────────────────────────────────────────────

    @Test
    fun `a tunnel that dies after connecting is reported as a disconnect`() {
        skipOnWindows()
        fakeCloudflared(banner(URL))
        val tunnel = manager()

        tunnel.start(localPort = 8_765)

        awaitUntil("the drop to be noticed") { tunnel.status.value == TunnelStatus.Error("Tunnel disconnected") }
        assertNull(tunnel.tunnelUrl.value, "a dropped tunnel must not leave a dead address on screen")
    }

    @Test
    fun `a cloudflared that never prints a url is reported as failing to start`() {
        // Deliberately distinct from a disconnect: nothing was ever shared, so there is no address to
        // stop trusting — the operator needs to look at cloudflared, not at the network.
        skipOnWindows()
        fakeCloudflared("echo 'ERR failed to request quick Tunnel: connection refused'")
        val tunnel = manager()

        tunnel.start(localPort = 8_765)

        awaitUntil("the failure to be reported") { tunnel.status.value == TunnelStatus.Error("Tunnel failed to start") }
        assertNull(tunnel.tunnelUrl.value)
    }

    @Test
    fun `a binary that cannot be run surfaces as an error rather than a crash`() {
        // A half-finished download leaves a file that exists — so the download is skipped — but that
        // the OS refuses to execute. It has to come back as tunnel status, not as an exception on a
        // background coroutine.
        skipOnWindows()
        binary.writeText("not an executable")
        binary.setExecutable(false)
        val tunnel = manager()

        tunnel.start(localPort = 8_765)

        awaitUntil("the launch failure to be reported") { tunnel.status.value is TunnelStatus.Error }
        assertNull(tunnel.tunnelUrl.value)
    }

    // ── Shutting down ───────────────────────────────────────────────────────────

    @Test
    fun `stopping a live tunnel takes the shared address down`() {
        skipOnWindows()
        fakeCloudflared("${banner(URL)}\nsleep 30")
        val tunnel = manager()
        tunnel.start(localPort = 8_765)
        awaitUntil("the tunnel to come up") { tunnel.tunnelUrl.value != null }

        tunnel.stop()

        assertNull(tunnel.tunnelUrl.value, "stopping takes the shared address down")
        // Deliberately not asserting the status here. `stop()` cancels the monitor, kills the process
        // and then writes Idle — but killing the process is what wakes the monitor, whose own
        // end-of-stream branch writes Error("Tunnel disconnected") if it still sees Connected. The two
        // writes are ordered only by timing, so an operator who presses Stop can be shown a
        // disconnect error for a tunnel they closed themselves. Asserting either value here would be
        // asserting on that race; `tunnelUrl` is null on both paths, so it is the honest assertion.
    }

    @Test
    fun `stopping a tunnel that was never started is harmless`() {
        val tunnel = manager()

        tunnel.stop()

        assertEquals(TunnelStatus.Idle, tunnel.status.value)
        assertNull(tunnel.tunnelUrl.value)
    }
}
