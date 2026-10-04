package org.churchpresenter.canvas

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.diagnostics.CrashReportSweep
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CdpPagesTest {

    private val sweep = CrashReportSweep()
    private val browsers = mutableListOf<FakeDevTools>()
    private val connections = mutableListOf<SharedBrowserFrameCache.CdpConnection>()

    @BeforeTest
    fun mark() = sweep.mark()

    @AfterTest
    fun cleanUp() {
        connections.forEach { runCatching { it.close() } }
        browsers.forEach { runCatching { it.stop() } }
        sweep.sweep()
    }

    /** What the fake page answers a screenshot request with. */
    private enum class Shot { PNG, NOTHING, NOT_AN_IMAGE }

    /** A headless browser's debug port: its version, its target list, and one page's socket. */
    private class FakeDevTools(var shot: Shot = Shot.PNG, private val listsPage: Boolean = true) {
        val methods = CopyOnWriteArrayList<String>()
        val sessions = CopyOnWriteArrayList<DefaultWebSocketServerSession>()
        var port = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            install(WebSockets)
            routing {
                get("/json/version") { call.respondText("""{"Browser":"Fake/1.0"}""", ContentType.Application.Json) }
                get("/json") {
                    val body = if (listsPage) {
                        """[{"type":"page","webSocketDebuggerUrl":"ws://127.0.0.1:$port/devtools/page/FAKE"}]"""
                    } else {
                        "[]"
                    }
                    call.respondText(body, ContentType.Application.Json)
                }
                webSocket("/devtools/page/FAKE") {
                    sessions.add(this)
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val request = Json.parseToJsonElement(frame.readText()).jsonObject
                            val id = request["id"]?.jsonPrimitive?.intOrNull ?: continue
                            val method = request["method"]?.jsonPrimitive?.content.orEmpty()
                            methods += method
                            val result = if (method == "Page.captureScreenshot") screenshot() else "{}"
                            send(Frame.Text("""{"id":$id,"result":$result}"""))
                        }
                    } finally {
                        sessions.remove(this)
                    }
                }
            }
        }

        private fun screenshot(): String = when (shot) {
            Shot.PNG -> """{"data":"${pngBase64()}"}"""
            Shot.NOTHING -> "{}"
            Shot.NOT_AN_IMAGE -> """{"data":"${Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))}"}"""
        }

        fun start() = apply {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        fun stop() {
            val deadline = System.currentTimeMillis() + 5_000
            while (sessions.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.yield()
            server.stop(0, 5_000)
        }

        companion object {
            fun pngBase64(): String {
                val out = ByteArrayOutputStream()
                ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", out)
                return Base64.getEncoder().encodeToString(out.toByteArray())
            }
        }
    }

    private fun browser(shot: Shot = Shot.PNG, listsPage: Boolean = true) =
        FakeDevTools(shot, listsPage).start().also { browsers += it }

    private fun connect(browser: FakeDevTools, entry: SharedBrowserFrameCache.CacheEntry = SharedBrowserFrameCache.CacheEntry()) =
        runBlocking { CdpPages.connectCdp(entry, browser.port, readyTimeoutMs = 2_000) }?.also { connections += it }

    private val page = SharedBrowserFrameCache.BrowserPage(
        url = "https://example.org/lower-third",
        renderWidth = 1280,
        renderHeight = 720,
        customCss = "body { color: 'red' }",
        fps = 30,
        forceTransparent = true,
    )

    @Test
    fun `a browser that answers on its debug port is connected to its page`() {
        assertNotNull(connect(browser()))
    }

    @Test
    fun `a browser that never answers is reported as failing to start`() {
        val entry = SharedBrowserFrameCache.CacheEntry()
        val closedPort = ServerSocket(0).use { it.localPort }

        val cdp = runBlocking { CdpPages.connectCdp(entry, closedPort, readyTimeoutMs = 50) }

        assertNull(cdp)
        assertEquals("Browser failed to start", entry.error.value)
    }

    @Test
    fun `a browser listing no page is not connected to`() {
        assertNull(connect(browser(listsPage = false)))
    }

    @Test
    fun `a page is sized, cleared to transparent, navigated and styled`() {
        val fake = browser()
        val cdp = assertNotNull(connect(fake))

        runBlocking { CdpPages.configurePage(cdp, page, settleMs = 0) }

        assertEquals(
            listOf(
                "Emulation.setDeviceMetricsOverride",
                "Emulation.setDefaultBackgroundColorOverride",
                "Page.enable",
                "Page.navigate",
                "Runtime.evaluate",
                "Runtime.evaluate",
            ),
            fake.methods,
        )
    }

    @Test
    fun `an opaque page with no address and no style is only sized and enabled`() {
        val fake = browser()
        val cdp = assertNotNull(connect(fake))

        runBlocking {
            CdpPages.configurePage(cdp, page.copy(url = "", customCss = "", forceTransparent = false), settleMs = 0)
        }

        assertEquals(listOf("Emulation.setDeviceMetricsOverride", "Page.enable"), fake.methods)
    }

    @Test
    fun `a navigated opaque page with no style gets no script`() {
        val fake = browser()
        val cdp = assertNotNull(connect(fake))

        runBlocking { CdpPages.configurePage(cdp, page.copy(customCss = "", forceTransparent = false), settleMs = 0) }

        assertFalse("Runtime.evaluate" in fake.methods)
    }

    @Test
    fun `a screenshot goes on screen`() {
        val entry = SharedBrowserFrameCache.CacheEntry()
        val cdp = assertNotNull(connect(browser()))

        assertTrue(runBlocking { CdpPages.captureFrame(entry, cdp, first = true) })

        assertEquals(3, assertNotNull(entry.frame.value).width)
        assertTrue(runBlocking { CdpPages.captureFrame(entry, cdp, first = false) })
    }

    @Test
    fun `a reply with no picture leaves the screen as it was`() {
        val entry = SharedBrowserFrameCache.CacheEntry()
        val cdp = assertNotNull(connect(browser(Shot.NOTHING)))

        assertFalse(runBlocking { CdpPages.captureFrame(entry, cdp, first = true) })
        assertFalse(runBlocking { CdpPages.captureFrame(entry, cdp, first = false) })
        assertNull(entry.frame.value)
    }

    @Test
    fun `a picture that does not decode is skipped`() {
        val entry = SharedBrowserFrameCache.CacheEntry()
        val cdp = assertNotNull(connect(browser(Shot.NOT_AN_IMAGE)))

        assertFalse(runBlocking { CdpPages.captureFrame(entry, cdp, first = true) })
        assertFalse(runBlocking { CdpPages.captureFrame(entry, cdp, first = false) })
        assertNull(entry.frame.value)
    }

    @Test
    fun `the capture loop keeps the page on screen at its frame rate until stopped`() {
        val entry = SharedBrowserFrameCache.CacheEntry()
        val cdp = assertNotNull(connect(browser()))

        runBlocking {
            val loop = launch(Dispatchers.Default) { SharedBrowserFrameCache.runCaptureLoop(entry, cdp, fps = 120) }
            val deadline = System.currentTimeMillis() + 5_000
            while (entry.frame.value == null && System.currentTimeMillis() < deadline) kotlinx.coroutines.yield()
            loop.cancel()
            loop.join()
        }

        assertNotNull(entry.frame.value)
        assertEquals(33L, entry.captureIntervalMs, "never faster than the startup floor")
    }

    @Test
    fun `the capture loop rides out pictures that do not decode`() {
        val entry = SharedBrowserFrameCache.CacheEntry()
        val fake = browser(Shot.NOT_AN_IMAGE)
        val cdp = assertNotNull(connect(fake))

        runBlocking {
            val loop = launch(Dispatchers.Default) { SharedBrowserFrameCache.runCaptureLoop(entry, cdp, fps = 30) }
            val deadline = System.currentTimeMillis() + 5_000
            while (fake.methods.count { it == "Page.captureScreenshot" } < 2 && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.yield()
            }
            loop.cancel()
            loop.join()
        }

        assertNull(entry.frame.value)
        assertTrue(fake.methods.count { it == "Page.captureScreenshot" } >= 2, "a bad picture does not stop the loop")
    }
}
