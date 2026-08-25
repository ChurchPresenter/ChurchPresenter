package org.churchpresenter.canvas

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Starting and stopping a browser source, and the loop in between.
 *
 * Every step of the shutdown leaks something invisible if it is skipped: a headless Chromium left
 * running, a temp profile directory left on disk, a stale frame left on an output that is supposed
 * to be blank. None of it shows up anywhere afterwards — the operator sees a tidy canvas and a
 * machine that is slowly filling with browsers, which is what `killZombieBrowsers` exists to mop up
 * on the next launch.
 *
 * The capture loop matters for the opposite reason: it must survive a grab failing. A page that
 * throws once — a navigation mid-screenshot, a moment with no renderer — must not end the capture,
 * or the source freezes on its last frame for the rest of the service.
 */
class BrowserSourceLifecycleTest {

    /** A stand-in for the headless browser process. */
    private class FakeBrowser : Process() {
        var killed = false
            private set

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = 0
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true
        override fun exitValue(): Int = 0
        override fun isAlive(): Boolean = !killed
        override fun destroy() { killed = true }
        override fun destroyForcibly(): Process { killed = true; return this }
    }

    /** A CDP endpoint that answers screenshot requests with [png], or with nothing when it is null. */
    private class FakeCdp(private val png: String?) {
        val screenshots = AtomicInteger()
        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            install(WebSockets)
            routing {
                webSocket("/devtools/page/FAKE") {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val json = Json.parseToJsonElement(frame.readText()).jsonObject
                        val id = json["id"]?.jsonPrimitive?.intOrNull ?: continue
                        val method = json["method"]?.jsonPrimitive?.content
                        if (method == "Page.captureScreenshot") {
                            screenshots.incrementAndGet()
                            if (png == null) send(Frame.Text("""{"id":$id,"result":{}}"""))
                            else send(Frame.Text("""{"id":$id,"result":{"data":"$png"}}"""))
                        } else {
                            send(Frame.Text("""{"id":$id,"result":{}}"""))
                        }
                    }
                }
            }
        }

        fun start() {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        fun stop() = server.stop(0, 0)
    }

    private val servers = mutableListOf<FakeCdp>()
    private val connections = mutableListOf<SharedBrowserFrameCache.CdpConnection>()
    private val seeded = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        seeded.forEach { SharedBrowserFrameCache.entries.remove(it) }
        seeded.clear()
        connections.forEach { runCatching { it.close() } }
        connections.clear()
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    /** A 2x2 PNG, base64'd exactly as Chrome sends one back. */
    private fun tinyPng(): String {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun connect(png: String?): SharedBrowserFrameCache.CdpConnection {
        val fake = FakeCdp(png).also { servers += it; it.start() }
        return SharedBrowserFrameCache.CdpConnection().also {
            connections += it
            assertTrue(runBlocking { it.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE") })
        }
    }

    // ── Shutting one down ──────────────────────────────────────────────────────

    @Test
    fun `releasing the last subscriber kills the browser and clears the frame`() {
        val browser = FakeBrowser()
        val profile = kotlin.io.path.createTempDirectory("cp-browser-profile").toFile()
        profile.resolve("Preferences").writeText("{}")
        val entry = SharedBrowserFrameCache.CacheEntry(
            refCount = 1, browserProcess = browser, userDataDir = profile,
        )
        entry.frame.value = ImageBitmap(2, 2)
        SharedBrowserFrameCache.entries["src-stop"] = entry
        seeded += "src-stop"

        SharedBrowserFrameCache.release("src-stop")

        assertTrue(browser.killed, "a headless Chromium left running is invisible until the machine slows")
        assertFalse(profile.exists(), "the temp profile must not be left behind")
        assertNull(entry.frame.value, "the output must go blank, not hold the last picture")
        assertNull(entry.browserProcess)
        assertNull(entry.userDataDir)
        assertFalse(SharedBrowserFrameCache.entries.containsKey("src-stop"))
    }

    @Test
    fun `releasing one of two subscribers leaves the browser alone`() {
        val browser = FakeBrowser()
        val entry = SharedBrowserFrameCache.CacheEntry(refCount = 2, browserProcess = browser)
        SharedBrowserFrameCache.entries["src-shared"] = entry
        seeded += "src-shared"

        SharedBrowserFrameCache.release("src-shared")

        assertFalse(browser.killed, "the canvas and the output share one browser")
        assertTrue(SharedBrowserFrameCache.entries.containsKey("src-shared"))

        SharedBrowserFrameCache.release("src-shared")
        assertTrue(browser.killed)
    }

    @Test
    fun `stopping a browser that never started is not an error`() {
        val entry = SharedBrowserFrameCache.CacheEntry(refCount = 1)

        SharedBrowserFrameCache.stopBrowser(entry)

        assertNull(entry.browserProcess)
        assertNull(entry.userDataDir)
    }

    @Test
    fun `releasing something that was never acquired does nothing`() {
        SharedBrowserFrameCache.release("src-never-there")
    }

    @Test
    fun `stopping closes the CDP connection`() {
        val cdp = connect(null)
        val entry = SharedBrowserFrameCache.CacheEntry(refCount = 1).also { it.cdpConnection = cdp }

        SharedBrowserFrameCache.stopBrowser(entry)

        assertNull(entry.cdpConnection)
    }

    // ── The capture loop ───────────────────────────────────────────────────────

    @Test
    fun `the loop keeps screenshotting until it is cancelled`() {
        val cdp = connect(tinyPng())
        val entry = SharedBrowserFrameCache.CacheEntry()

        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch { SharedBrowserFrameCache.runCaptureLoop(entry, cdp, fps = 60) }
        try {
            awaitUntil("a frame to arrive") { entry.frame.value != null }
            val after = servers.last().screenshots.get()
            awaitUntil("more than one grab") { servers.last().screenshots.get() > after }
        } finally {
            runBlocking { job.cancel() }
        }
    }

    @Test
    fun `a grab that comes back empty does not stop the loop`() {
        // A page mid-navigation answers with no data. Ending the capture there would freeze the
        // source on its previous frame for the rest of the service.
        val cdp = connect(null)
        val entry = SharedBrowserFrameCache.CacheEntry()

        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch { SharedBrowserFrameCache.runCaptureLoop(entry, cdp, fps = 30) }
        try {
            awaitUntil("several attempts") { servers.last().screenshots.get() > 2 }
            assertNull(entry.frame.value, "nothing decodable came back, so nothing is shown")
        } finally {
            runBlocking { job.cancel() }
        }
    }

    @Test
    fun `an absurd frame rate is clamped to something the loop can actually run`() {
        val cdp = connect(null)
        val fast = SharedBrowserFrameCache.CacheEntry()
        val slow = SharedBrowserFrameCache.CacheEntry()

        val scope = CoroutineScope(Dispatchers.Default)
        val a = scope.launch { SharedBrowserFrameCache.runCaptureLoop(fast, cdp, fps = 10_000) }
        val b = scope.launch { SharedBrowserFrameCache.runCaptureLoop(slow, cdp, fps = 0) }
        try {
            awaitUntil("both intervals to be set") {
                fast.captureIntervalMs > 0 && slow.captureIntervalMs > 0
            }
            assertTrue(fast.captureIntervalMs >= 1, "a 10,000fps request must not become a busy loop")
            assertTrue(slow.captureIntervalMs > fast.captureIntervalMs, "0fps must clamp to the floor")
        } finally {
            runBlocking { a.cancel(); b.cancel() }
        }
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("timed out waiting for $what")
    }
}
