package org.churchpresenter.canvas

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.server.routing.routing
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SharedBrowserFrameCache] launches a real headless Chrome/Edge process and drives it over a CDP
 * WebSocket — [acquire] starts that unconditionally (there is no cheap "nothing to do" input, unlike
 * [SharedCameraFrameCache]'s unrecognized-device-scheme shortcut), so it is never called from this
 * suite: on a machine with a real browser installed it would actually launch one, which is slow,
 * heavyweight and not what a fast unit test should do. What's covered instead: every function that
 * guards on "no entry for this id" (all reachable without ever acquiring one), the pure discovery
 * helpers ([findBrowserExecutable], [findFreePort], both widened to `internal`), which only inspect
 * the filesystem/PATH/an ephemeral socket and never spawn a browser — and separately, the CDP
 * WebSocket protocol client itself ([SharedBrowserFrameCache.CdpConnection], widened to `internal`),
 * driven for real against a throwaway local Ktor WebSocket server standing in for Chrome's DevTools
 * endpoint. The same "fake the other end of the socket, not the client" approach
 * `OBSWebSocketManagerTest` already uses for this project's other hand-rolled WebSocket-JSON
 * protocol client.
 */
class SharedBrowserFrameCacheTest {

    private val servers = mutableListOf<FakeCdpBrowser>()
    private val connections = mutableListOf<SharedBrowserFrameCache.CdpConnection>()

    @AfterTest
    fun cleanUp() {
        connections.forEach { runCatching { it.close() } }
        connections.clear()
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    /** A stand-in for Chrome's CDP endpoint: echoes an `{"id":N,"result":{}}` for every request it
     *  receives, and lets a test push arbitrary CDP event frames on demand. */
    private class FakeCdpBrowser {
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

        /** Pushes an arbitrary CDP event frame straight to the connected client, bypassing the
         *  automatic id/result echo above.
         *
         *  Waits for the session first. `CdpConnection.connect` returning means the *client* side
         *  of the handshake finished; the server's own handler coroutine — the one that runs
         *  `sessions.add(this)` — may not have been dispatched yet. Sending into an empty list is
         *  silent, so the frame goes nowhere and the test waiting on it fails a timeout later,
         *  with nothing to say why. */
        fun sendEvent(json: String) {
            val deadline = System.currentTimeMillis() + 5_000
            while (sessions.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(5)
            if (sessions.isEmpty()) throw AssertionError("no client session connected to the fake CDP server")
            runBlocking { sessions.forEach { it.send(Frame.Text(json)) } }
        }
    }

    private fun startFakeCdpBrowser(): FakeCdpBrowser = FakeCdpBrowser().also { servers.add(it); it.start() }

    private fun cdpConnection(): SharedBrowserFrameCache.CdpConnection =
        SharedBrowserFrameCache.CdpConnection().also { connections.add(it) }

    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    // ── CdpConnection.connect ──────────────────────────────────────────────────────────────────

    @Test
    fun `connect succeeds against a real WebSocket endpoint`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        assertTrue(cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE"))
    }

    @Test
    fun `connect fails when nothing is listening on the target port`() {
        val cdp = cdpConnection()
        assertFalse(cdp.connect("ws://127.0.0.1:${unusedPort()}/devtools/page/FAKE"))
    }

    // ── CdpConnection.sendAsync ────────────────────────────────────────────────────────────────

    @Test
    fun `sendAsync before connecting returns null without sending anything`() {
        val cdp = cdpConnection()
        val result = runBlocking { cdp.sendAsync("Page.enable", null) }
        assertNull(result)
    }

    @Test
    fun `sendAsync sends the method and params and returns the server's result`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE")

        val result = runBlocking { cdp.sendAsync(
            "Page.navigate",
            buildJsonObject { put("url", "https://example.com") },
        ) }

        assertTrue(result != null, "the fake server always answers with a result object")
        val sent = Json.parseToJsonElement(
            fake.received.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("the fake server received nothing")
        ).jsonObject
        assertEquals("Page.navigate", sent["method"]?.jsonPrimitive?.content)
        assertEquals("https://example.com", sent["params"]?.jsonObject?.get("url")?.jsonPrimitive?.content)
    }

    @Test
    fun `sendAsync omits params entirely when none are given`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE")

        runBlocking { cdp.sendAsync("Page.enable", null) }

        val sent = Json.parseToJsonElement(
            fake.received.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("the fake server received nothing")
        ).jsonObject
        assertNull(sent["params"], "no params object must be sent when none were given")
    }

    // ── CdpConnection event handling → onUrlChanged ───────────────────────────────────────────

    @Test
    fun `a main-frame navigation event invokes onUrlChanged with the new URL`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        var reportedUrl: String? = null
        cdp.onUrlChanged = { reportedUrl = it }
        cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE")

        fake.sendEvent(
            """{"method":"Page.frameNavigated","params":{"frame":{"url":"https://example.com/page","parentId":null}}}"""
        )

        awaitUntil("onUrlChanged to be invoked") { reportedUrl != null }
        assertEquals("https://example.com/page", reportedUrl)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `a sub-frame (iframe) navigation event does not invoke onUrlChanged`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        var reportedUrl: String? = null
        cdp.onUrlChanged = { reportedUrl = it }
        cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE")

        fake.sendEvent(
            """{"method":"Page.frameNavigated","params":{"frame":{"url":"https://ads.example/iframe","parentId":"main-frame-id"}}}"""
        )
        // Prove the event was actually processed (not just "not yet arrived") by sending a real
        // command afterward and waiting for its round trip before asserting the negative.
        runBlocking { cdp.sendAsync("Page.enable", null) }

        assertNull(reportedUrl, "a navigation with a parentId is a sub-frame, not the main page")
    }

    @Test
    fun `a malformed frame does not break the connection for subsequent messages`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE")

        fake.sendEvent("not valid json at all")

        val result = runBlocking { cdp.sendAsync("Page.enable", null) }
        assertTrue(result != null, "a malformed message must be caught internally, not kill the connection")
    }

    // ── CdpConnection.close ────────────────────────────────────────────────────────────────────

    @Test
    fun `close does not throw when never connected`() {
        cdpConnection().close()
    }

    @Test
    fun `after close, sendAsync returns null again without sending anything`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE")
        cdp.close()

        val result = runBlocking { cdp.sendAsync("Page.enable", null) }
        assertNull(result)
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    // ── Guard clauses for an id that was never acquired ───────────────────────────────────────

    @Test
    fun `releasing an id that was never acquired does not throw`() {
        SharedBrowserFrameCache.release("never-acquired")
    }

    @Test
    fun `setTransparent on an unknown id is a no-op`() {
        SharedBrowserFrameCache.setTransparent("never-acquired", true)
        SharedBrowserFrameCache.setTransparent("never-acquired", false)
    }

    @Test
    fun `setFps on an unknown id is a no-op`() {
        SharedBrowserFrameCache.setFps("never-acquired", 30)
    }

    @Test
    fun `navigateTo on an unknown id is a no-op`() {
        SharedBrowserFrameCache.navigateTo("never-acquired", "https://example.com", "", false)
    }

    @Test
    fun `getCurrentUrl for an unknown id returns null`() {
        assertNull(SharedBrowserFrameCache.getCurrentUrl("never-acquired"))
    }

    // ── findBrowserExecutable ──────────────────────────────────────────────────────────────────

    @Test
    fun `findBrowserExecutable returns null or a real, existing executable path`() {
        val path = SharedBrowserFrameCache.findBrowserExecutable()
        if (path != null) assertTrue(File(path).exists(), "a non-null result must be a real file: $path")
    }

    @Test
    fun `findBrowserExecutable stays consistent under a forced Windows os name`() {
        val saved = System.getProperty("os.name", "")
        try {
            System.setProperty("os.name", "Windows 11")
            val path = SharedBrowserFrameCache.findBrowserExecutable()
            if (path != null) assertTrue(File(path).exists(), "a non-null result must be a real file: $path")
        } finally {
            System.setProperty("os.name", saved)
        }
    }

    @Test
    fun `findBrowserExecutable stays consistent under a forced generic Linux os name`() {
        val saved = System.getProperty("os.name", "")
        try {
            System.setProperty("os.name", "Generic Linux")
            val path = SharedBrowserFrameCache.findBrowserExecutable()
            if (path != null) assertTrue(File(path).exists(), "a non-null result must be a real file: $path")
        } finally {
            System.setProperty("os.name", saved)
        }
    }

    // ── findFreePort ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `findFreePort returns a usable port number`() {
        val port = SharedBrowserFrameCache.findFreePort()
        assertTrue(port in 1..65535, "expected a valid port, got $port")
    }

    // ── buildBrowserLaunchCommand ──────────────────────────────────────────────────────────────

    @Test
    fun `buildBrowserLaunchCommand includes the browser path, debug port, user data dir and window size`() {
        val command = buildBrowserLaunchCommand(
            "/usr/bin/google-chrome", 9222, "/tmp/cp-browser-xyz", 1920, 1080,
        )
        assertTrue(command.first() == "/usr/bin/google-chrome")
        assertTrue(command.contains("--remote-debugging-port=9222"))
        assertTrue(command.contains("--user-data-dir=/tmp/cp-browser-xyz"))
        assertTrue(command.contains("--window-size=1920,1080"))
        assertTrue(command.contains("--headless=new"))
    }

    // ── escapeForJsStringLiteral ───────────────────────────────────────────────────────────────

    @Test
    fun `escapeForJsStringLiteral escapes backslashes, single quotes and newlines, and drops carriage returns`() {
        assertEquals("a\\\\b\\'c\\nd", escapeForJsStringLiteral("a\\b'c\nd\r"))
    }

    @Test
    fun `escapeForJsStringLiteral leaves plain text unchanged`() {
        assertEquals("body { color: red; }", escapeForJsStringLiteral("body { color: red; }"))
    }

    // ── killProcess ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `killProcess terminates an already-exited process without throwing`() {
        val process = ProcessBuilder("true").start()
        process.waitFor()
        SharedBrowserFrameCache.killProcess(process)
    }
}
