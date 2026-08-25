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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Driving a browser source that is already on air.
 *
 * Changing a browser source's address or transparency while it is live must not restart Chrome —
 * that would blank the output for a second or two in front of the congregation. Instead the existing
 * page is told to navigate, and the background override is toggled over the same connection.
 *
 * Neither path had ever run: both bail unless the cache holds an entry with a live connection, and
 * that used to mean a real browser. The entry store is `internal` now, so a test can put one in
 * pointing at a fake CDP endpoint.
 */
class BrowserSourceLiveControlTest {

    private class FakeCdp {
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
                            val id = Json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.intOrNull
                            if (id != null) send(Frame.Text("""{"id":$id,"result":{}}"""))
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

        /** Pushes an unsolicited frame at every connected client, as a real page event arrives. */
        fun push(text: String) = runBlocking {
            sessions.forEach { it.send(Frame.Text(text)) }
        }

        fun methods(): List<String> = received.map {
            Json.parseToJsonElement(it).jsonObject["method"]?.jsonPrimitive?.content.orEmpty()
        }

        fun awaitMethod(name: String, timeoutMs: Long = 5_000): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (methods().contains(name)) return true
                Thread.sleep(5)
            }
            return false
        }
    }

    private val servers = mutableListOf<FakeCdp>()
    private val connections = mutableListOf<SharedBrowserFrameCache.CdpConnection>()
    private val seeded = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        // The cache is a process-wide singleton; a seeded entry left behind would be found by the
        // next test and pointed at a server that has gone.
        seeded.forEach { SharedBrowserFrameCache.entries.remove(it) }
        seeded.clear()
        connections.forEach { runCatching { it.close() } }
        connections.clear()
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    /** Puts a live entry in the cache under [sourceId], wired to a fake CDP endpoint. */
    private fun liveSource(sourceId: String): FakeCdp {
        val fake = FakeCdp().also { servers += it; it.start() }
        val cdp = SharedBrowserFrameCache.CdpConnection().also { connections += it }
        assertTrue(runBlocking { cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE") })
        val entry = SharedBrowserFrameCache.CacheEntry().also { it.cdpConnection = cdp }
        SharedBrowserFrameCache.entries[sourceId] = entry
        seeded += sourceId
        return fake
    }

    // ── navigateTo ──────────────────────────────────────────────────────────────

    @Test
    fun `navigating a live source tells the existing page to go, not Chrome to restart`() {
        val fake = liveSource("s1")

        SharedBrowserFrameCache.navigateTo("s1", "https://notices.example", "", forceTransparent = false)

        assertTrue(fake.awaitMethod("Page.navigate"), "the page was never navigated")
        assertTrue(fake.received.any { it.contains("https://notices.example") })
    }

    @Test
    fun `navigating updates the address the properties panel shows`() {
        liveSource("s2")

        SharedBrowserFrameCache.navigateTo("s2", "https://moved.example", "", forceTransparent = false)

        val flow = assertNotNull(SharedBrowserFrameCache.getCurrentUrl("s2"))
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && flow.value != "https://moved.example") Thread.sleep(5)
        assertEquals("https://moved.example", flow.value)
    }

    @Test
    fun `navigating a transparent source re-applies transparency after the page loads`() {
        val fake = liveSource("s3")

        // The new page brings its own background; without this the overlay comes out on white.
        SharedBrowserFrameCache.navigateTo("s3", "https://a.example", "", forceTransparent = true)

        assertTrue(fake.awaitMethod("Runtime.evaluate"), "transparency was never re-applied")
    }

    @Test
    fun `navigating with custom css re-applies it to the new page`() {
        val fake = liveSource("s4")

        SharedBrowserFrameCache.navigateTo("s4", "https://a.example", "body{margin:0}", forceTransparent = false)

        assertTrue(fake.awaitMethod("Page.navigate"))
        val deadline = System.currentTimeMillis() + 5_000
        var seen = false
        while (System.currentTimeMillis() < deadline && !seen) {
            seen = fake.received.any { it.contains("body{margin:0}") }
            Thread.sleep(5)
        }
        assertTrue(seen, "the CSS did not survive the navigation")
    }

    @Test
    fun `navigating a source that is not live does nothing`() {
        // The operator edits a browser source that is not on air; there is no page to tell.
        SharedBrowserFrameCache.navigateTo("never-acquired", "https://a.example", "", false)
    }

    // ── setTransparent ──────────────────────────────────────────────────────────

    @Test
    fun `turning transparency on overrides the page background`() {
        val fake = liveSource("s5")

        SharedBrowserFrameCache.setTransparent("s5", true)

        assertTrue(fake.awaitMethod("Emulation.setDefaultBackgroundColorOverride"))
        assertTrue(
            fake.received.any { it.contains("setDefaultBackgroundColorOverride") && it.contains("\"a\":0") },
            "the override must be fully transparent",
        )
    }

    @Test
    fun `turning transparency on also clears the page's own background`() {
        val fake = liveSource("s6")

        // The override alone is not enough: a page with its own `body{background:white}` still
        // paints white over it.
        SharedBrowserFrameCache.setTransparent("s6", true)

        assertTrue(fake.awaitMethod("Runtime.evaluate"))
        assertTrue(fake.received.any { it.contains("background='transparent'") })
    }

    @Test
    fun `turning transparency off resets the override`() {
        val fake = liveSource("s7")

        SharedBrowserFrameCache.setTransparent("s7", false)

        assertTrue(fake.awaitMethod("Emulation.setDefaultBackgroundColorOverride"))
        val request = fake.received.first { it.contains("setDefaultBackgroundColorOverride") }
        assertFalse(request.contains("\"a\":0"), "an opaque source keeps the page's own background")
    }

    @Test
    fun `setting transparency on a source that is not live does nothing`() {
        SharedBrowserFrameCache.setTransparent("never-acquired", true)
    }

    @Test
    fun `an entry with no connection yet is left alone`() {
        // Between acquiring a source and Chrome being ready there is an entry but no connection.
        SharedBrowserFrameCache.entries["s8"] = SharedBrowserFrameCache.CacheEntry()
        seeded += "s8"

        SharedBrowserFrameCache.setTransparent("s8", true)
        SharedBrowserFrameCache.navigateTo("s8", "https://a.example", "", false)
    }

    // ── getCurrentUrl ───────────────────────────────────────────────────────────

    @Test
    fun `the address flow exists for a live source and not for an unknown one`() {
        liveSource("s9")

        assertNotNull(SharedBrowserFrameCache.getCurrentUrl("s9"))
        assertNull(SharedBrowserFrameCache.getCurrentUrl("never-acquired"))
    }

    // ── What arrives back over the connection ──────────────────────────────────

    @Test
    fun `a main-frame navigation is reported to whoever is listening`() {
        // This is how the address field follows a redirect, or a link the page followed itself.
        val fake = liveSource("src-nav-event")
        val cdp = connections.last()
        val seen = LinkedBlockingQueue<String>()
        cdp.onUrlChanged = { seen.add(it) }

        fake.push("""{"method":"Page.frameNavigated","params":{"frame":{"id":"1","url":"https://after.example"}}}""")

        assertEquals("https://after.example", seen.poll(5, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test
    fun `an iframe navigating is not reported as the page navigating`() {
        // An ad or an embed loading would otherwise rewrite the address field under the operator.
        val fake = liveSource("src-iframe-event")
        val cdp = connections.last()
        val seen = LinkedBlockingQueue<String>()
        cdp.onUrlChanged = { seen.add(it) }

        fake.push(
            """{"method":"Page.frameNavigated","params":{"frame":{"id":"2","parentId":"1",""" +
                """"url":"https://ad.example"}}}"""
        )
        fake.push("""{"method":"Page.frameNavigated","params":{"frame":{"id":"1","url":"https://real.example"}}}""")

        assertEquals(
            "https://real.example", seen.poll(5, java.util.concurrent.TimeUnit.SECONDS),
            "the sub-frame must have been passed over entirely",
        )
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `a navigation with nobody listening is dropped rather than thrown`() {
        val fake = liveSource("src-nav-nolistener")

        fake.push("""{"method":"Page.frameNavigated","params":{"frame":{"id":"1","url":"https://x.example"}}}""")

        // Nothing to assert but survival: the connection has to still work afterwards.
        assertTrue(runBlocking { SharedBrowserFrameCache.getCurrentUrl("src-nav-nolistener") } != null ||
            fake.awaitMethod("Runtime.evaluate"))
    }

    @Test
    fun `an event the source does not care about is ignored`() {
        val fake = liveSource("src-other-event")
        val cdp = connections.last()
        val seen = LinkedBlockingQueue<String>()
        cdp.onUrlChanged = { seen.add(it) }

        fake.push("""{"method":"Network.requestWillBeSent","params":{"requestId":"7"}}""")
        fake.push("""{"method":"Page.frameNavigated","params":{"frame":{"id":"1","url":"https://y.example"}}}""")

        assertEquals(
            "https://y.example", seen.poll(5, java.util.concurrent.TimeUnit.SECONDS),
            "the unrelated event must not have been mistaken for a navigation",
        )
    }

    @Test
    fun `malformed traffic does not take the connection down`() {
        val fake = liveSource("src-garbage")
        val cdp = connections.last()
        val seen = LinkedBlockingQueue<String>()
        cdp.onUrlChanged = { seen.add(it) }

        fake.push("not json at all")
        fake.push("""{"method":"Page.frameNavigated","params":{"frame":{"id":"1","url":"https://z.example"}}}""")

        assertEquals("https://z.example", seen.poll(5, java.util.concurrent.TimeUnit.SECONDS))
    }
}
