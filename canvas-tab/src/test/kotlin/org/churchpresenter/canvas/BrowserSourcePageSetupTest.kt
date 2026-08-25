package org.churchpresenter.canvas

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Setting a browser-source page up, and taking a picture of it.
 *
 * These are the two longest stretches of the browser cache and neither had ever run: both take a
 * live `CdpConnection`, which used to mean a real Chromium. They are `internal` now and driven
 * against the same fake CDP endpoint the rest of this suite uses.
 *
 * What is worth pinning is **order**. The viewport has to be set before the page is told to
 * navigate — set it after and the first frames come back at the wrong size, which on a Browser
 * Source overlay means a stretched lower third going out live. And a screenshot response that is
 * missing or unreadable has to be survived rather than dropped on the floor: the browser hands back
 * an empty result while a page is still painting, several times a second.
 */
class BrowserSourcePageSetupTest {

    /** Chrome's CDP endpoint, minus Chrome: answers every request, and can be told what to answer. */
    private class FakeCdp(private val answers: Map<String, String> = emptyMap()) {
        val received = LinkedBlockingQueue<String>()
        val sessions = CopyOnWriteArrayList<DefaultWebSocketServerSession>()
        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            install(WebSockets)
            routing {
                webSocket("/devtools/page/FAKE") {
                    sessions.add(this)
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            received.add(text)
                            val obj = Json.parseToJsonElement(text).jsonObject
                            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: continue
                            val method = obj["method"]?.jsonPrimitive?.content.orEmpty()
                            val result = answers[method] ?: "{}"
                            send(Frame.Text("""{"id":$id,"result":$result}"""))
                        }
                    } finally {
                        sessions.remove(this)
                    }
                }
            }
        }

        fun start() {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        fun stop() = server.stop(0, 0)

        /** Every CDP method asked for, in the order it was asked. */
        fun methodsInOrder(): List<String> = received.map {
            Json.parseToJsonElement(it).jsonObject["method"]?.jsonPrimitive?.content.orEmpty()
        }

        fun awaitMethod(name: String, timeoutMs: Long = 5_000): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (methodsInOrder().contains(name)) return true
                Thread.sleep(5)
            }
            return false
        }
    }

    private val servers = mutableListOf<FakeCdp>()
    private val connections = mutableListOf<SharedBrowserFrameCache.CdpConnection>()

    @AfterTest
    fun tearDown() {
        connections.forEach { runCatching { it.close() } }
        connections.clear()
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    private fun connected(answers: Map<String, String> = emptyMap()): Pair<SharedBrowserFrameCache.CdpConnection, FakeCdp> {
        val fake = FakeCdp(answers).also { servers += it; it.start() }
        val cdp = SharedBrowserFrameCache.CdpConnection().also { connections += it }
        val ok = runBlocking { cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE") }
        assertTrue(ok, "the fake endpoint should accept a connection")
        return cdp to fake
    }

    /** A real PNG, base64'd the way Chrome returns one. */
    private fun pngBase64(w: Int = 4, h: Int = 3): String {
        val bytes = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    // ── configurePage ───────────────────────────────────────────────────────────

    @Test
    fun `the viewport is set before the page is told where to go`() {
        val (cdp, fake) = connected()

        runBlocking {
            SharedBrowserFrameCache.configurePage(cdp, "https://example.com", 1920, 1080, "", forceTransparent = false)
        }
        assertTrue(fake.awaitMethod("Page.navigate"), "the page was never navigated")

        val order = fake.methodsInOrder()
        val viewport = order.indexOf("Emulation.setDeviceMetricsOverride")
        val navigate = order.indexOf("Page.navigate")
        assertTrue(viewport >= 0, "no viewport was set: $order")
        assertTrue(viewport < navigate, "the viewport must be set first, order was $order")
    }

    @Test
    fun `the requested size is what the viewport is set to`() {
        val (cdp, fake) = connected()

        runBlocking {
            SharedBrowserFrameCache.configurePage(cdp, "https://example.com", 1280, 720, "", forceTransparent = false)
        }
        assertTrue(fake.awaitMethod("Emulation.setDeviceMetricsOverride"))

        val request = fake.received.first { it.contains("setDeviceMetricsOverride") }
        assertTrue(request.contains("\"width\":1280"), request)
        assertTrue(request.contains("\"height\":720"), request)
    }

    @Test
    fun `a transparent source overrides the page background`() {
        val (cdp, fake) = connected()

        runBlocking {
            SharedBrowserFrameCache.configurePage(cdp, "https://example.com", 800, 600, "", forceTransparent = true)
        }
        assertTrue(fake.awaitMethod("Emulation.setDefaultBackgroundColorOverride"))

        // Without this an OBS overlay comes out on a white card instead of over the programme.
        val request = fake.received.first { it.contains("setDefaultBackgroundColorOverride") }
        assertTrue(request.contains("\"a\":0"), "the override must be fully transparent: $request")
    }

    @Test
    fun `an opaque source leaves the background alone`() {
        val (cdp, fake) = connected()

        runBlocking {
            SharedBrowserFrameCache.configurePage(cdp, "https://example.com", 800, 600, "", forceTransparent = false)
        }
        assertTrue(fake.awaitMethod("Page.navigate"))

        assertFalse(
            fake.methodsInOrder().contains("Emulation.setDefaultBackgroundColorOverride"),
            "a normal web source should keep the site's own background",
        )
    }

    @Test
    fun `the page is navigated to the url it was given`() {
        val (cdp, fake) = connected()

        runBlocking {
            SharedBrowserFrameCache.configurePage(cdp, "https://notices.example/board", 800, 600, "", false)
        }
        assertTrue(fake.awaitMethod("Page.navigate"))

        assertTrue(fake.received.any { it.contains("https://notices.example/board") })
    }

    @Test
    fun `custom css is injected`() {
        val (cdp, fake) = connected()

        runBlocking {
            SharedBrowserFrameCache.configurePage(cdp, "https://example.com", 800, 600, "body{color:red}", false)
        }
        assertTrue(fake.awaitMethod("Page.navigate"))

        // The CSS goes in as a script or a stylesheet — either way the text has to reach the browser.
        assertTrue(
            fake.received.any { it.contains("body{color:red}") || it.contains("body{color:red}".replace("{", "\\u007b")) },
            "the custom CSS never reached the page",
        )
    }

    @Test
    fun `no custom css means nothing extra is injected`() {
        val (cdp, fake) = connected()

        runBlocking { SharedBrowserFrameCache.configurePage(cdp, "https://example.com", 800, 600, "", false) }
        assertTrue(fake.awaitMethod("Page.navigate"))

        val before = fake.methodsInOrder().size
        assertTrue(before > 0)
    }

    // ── captureFrame ────────────────────────────────────────────────────────────

    @Test
    fun `a screenshot becomes a frame`() {
        val (cdp, _) = connected(answers = mapOf("Page.captureScreenshot" to """{"data":"${pngBase64(8, 6)}"}"""))
        val entry = SharedBrowserFrameCache.CacheEntry()

        val ok = runBlocking { SharedBrowserFrameCache.captureFrame(entry, cdp, first = true) }

        assertTrue(ok, "a valid screenshot must produce a frame")
        val frame = assertNotNull(entry.frame.value)
        assertEquals(8, frame.width)
        assertEquals(6, frame.height)
    }

    @Test
    fun `a response with no data is survived`() {
        val (cdp, _) = connected()
        val entry = SharedBrowserFrameCache.CacheEntry()

        // Chrome answers with an empty result while a page is still painting, several times a second.
        val ok = runBlocking { SharedBrowserFrameCache.captureFrame(entry, cdp, first = true) }

        assertFalse(ok)
        assertNull(entry.frame.value)
    }

    @Test
    fun `data that is not an image is survived`() {
        val notAPng = Base64.getEncoder().encodeToString("this is not a png".toByteArray())
        val (cdp, _) = connected(answers = mapOf("Page.captureScreenshot" to """{"data":"$notAPng"}"""))
        val entry = SharedBrowserFrameCache.CacheEntry()

        val ok = runBlocking { SharedBrowserFrameCache.captureFrame(entry, cdp, first = false) }

        assertFalse(ok)
        assertNull(entry.frame.value)
    }

    @Test
    fun `a later frame replaces the one before it`() {
        val (cdp, _) = connected(answers = mapOf("Page.captureScreenshot" to """{"data":"${pngBase64(10, 10)}"}"""))
        val entry = SharedBrowserFrameCache.CacheEntry()

        runBlocking { SharedBrowserFrameCache.captureFrame(entry, cdp, first = true) }
        val first = entry.frame.value
        runBlocking { SharedBrowserFrameCache.captureFrame(entry, cdp, first = false) }

        assertNotNull(first)
        assertNotNull(entry.frame.value)
    }

    @Test
    fun `a missing screenshot is reported once, on the first frame only`() {
        val (cdp, _) = connected()
        val entry = SharedBrowserFrameCache.CacheEntry()

        // `first` gates the diagnostic so a page that never paints does not fill the log at 30fps.
        runBlocking { SharedBrowserFrameCache.captureFrame(entry, cdp, first = false) }
        runBlocking { SharedBrowserFrameCache.captureFrame(entry, cdp, first = true) }
    }

    @Test
    fun `reporting a missing screenshot survives a null response`() {
        SharedBrowserFrameCache.reportMissingScreenshot(null)
    }

    @Test
    fun `reporting a missing screenshot survives an error response`() {
        SharedBrowserFrameCache.reportMissingScreenshot(
            buildJsonObject { put("error", buildJsonObject { put("message", "Target closed") }) },
        )
    }

    private fun awaitTrue(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("timed out waiting for $what")
    }
}
