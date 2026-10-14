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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [SharedBrowserFrameCache] launches a real headless Chrome/Edge process and drives it over a CDP
 * WebSocket — [acquire] starts that unconditionally (there is no cheap "nothing to do" input, unlike
 * [SharedCameraFrameCache]'s unrecognized-device-scheme shortcut), so it is never called from this
 * suite: on a machine with a real browser installed it would actually launch one, which is slow,
 * heavyweight and not what a fast unit test should do. The entry around the browser — shared by
 * every layer showing one source, torn down by the last — is driven through
 * [SharedBrowserFrameCache.acquireWith] with a launch that connects to the fake endpoint below
 * instead. What's covered besides: every function that
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
    private val acquired = mutableSetOf<String>()
    private val profiles = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        // Entries first: the last release closes each one's connection, as the app's last layer does.
        acquired.forEach { id ->
            while (SharedBrowserFrameCache.getCurrentUrl(id) != null) SharedBrowserFrameCache.release(id)
        }
        acquired.clear()
        profiles.forEach { it.deleteRecursively() }
        profiles.clear()
        // Clients first, so each server's session ends on its own before its event loop is torn down.
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

        /**
         * Waits for every client session to end, then shuts the server down.
         *
         * Stopped while a session was still open, Netty terminated its event loop under the
         * connection's response pipeline, which then failed on that dead loop. The exception is
         * thrown on a Netty thread, so no test here saw it -- the next `runTest` in the same fork
         * did, and failed with "uncaught exceptions before the test started".
         */
        fun stop() {
            val deadline = System.currentTimeMillis() + SESSION_END_TIMEOUT_MS
            while (sessions.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(5)
            server.stop(gracePeriodMillis = 0, timeoutMillis = SHUTDOWN_TIMEOUT_MS)
        }

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

    @Test
    fun `an error reply to nothing asked, and a navigation nobody listens for, leave the connection working`() {
        val fake = startFakeCdpBrowser()
        val cdp = cdpConnection()
        cdp.connect("ws://127.0.0.1:${fake.port}/devtools/page/FAKE")

        fake.sendEvent("""{"id":9999,"error":{"code":-32000,"message":"No target"}}""")
        fake.sendEvent(
            """{"method":"Page.frameNavigated","params":{"frame":{"url":"https://example.com/x","parentId":null}}}"""
        )

        val result = runBlocking { cdp.sendAsync("Page.enable", null) }
        assertTrue(result != null, "a stray error and an unheard navigation must not break later requests")
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

    // ── The entry one source's layers share ───────────────────────────────────────────────────

    /**
     * Stands in for [SharedBrowserFrameCache]'s browser launch: fills the entry in as a launched browser
     * would — a process, a profile folder, a DevTools connection to [wsUrl] when there is one — then
     * idles until cancelled, as the capture loop does. Each launch's entry is handed to [next].
     */
    private inner class FakeLaunch(private val wsUrl: String? = null, private val withBrowser: Boolean = false) {
        private val started = LinkedBlockingQueue<SharedBrowserFrameCache.CacheEntry>()

        val start: suspend (SharedBrowserFrameCache.CacheEntry, SharedBrowserFrameCache.BrowserPage) -> Unit =
            { entry, _ ->
                if (withBrowser) {
                    entry.browserProcess = ProcessBuilder(shellCommand("exit 0")).start()
                    entry.userDataDir = Files.createTempDirectory("cp-browser-test").toFile().also { profiles += it }
                }
                if (wsUrl != null) entry.cdpConnection = cdpConnection().also { it.connect(wsUrl) }
                started.add(entry)
                awaitCancellation()
            }

        fun next(): SharedBrowserFrameCache.CacheEntry =
            assertNotNull(started.poll(5, TimeUnit.SECONDS), "the browser was never launched")
    }

    private fun acquire(
        id: String,
        start: suspend (SharedBrowserFrameCache.CacheEntry, SharedBrowserFrameCache.BrowserPage) -> Unit,
    ): SharedBrowserFrameCache.BrowserFlows {
        acquired += id
        return SharedBrowserFrameCache.acquireWith(id, PAGE, start)
    }

    private fun FakeCdpBrowser.wsUrl() = "ws://127.0.0.1:$port/devtools/page/FAKE"

    /** The next request the fake browser was sent, as `method` and `params`. */
    private fun FakeCdpBrowser.nextRequest(): Pair<String, JsonObject?> {
        val text = assertNotNull(received.poll(5, TimeUnit.SECONDS), "nothing was sent to the browser")
        val request = Json.parseToJsonElement(text).jsonObject
        return request.getValue("method").jsonPrimitive.content to request["params"]?.jsonObject
    }

    @Test
    fun `layers showing one source share one browser, which stops only when the last lets go`() {
        val fake = startFakeCdpBrowser()
        val launch = FakeLaunch(fake.wsUrl(), withBrowser = true)
        val first = acquire("shared", launch.start)
        val second = acquire("shared", launch.start)
        val entry = launch.next()
        val job = assertNotNull(entry.captureJob)
        val process = assertNotNull(entry.browserProcess)
        val profile = assertNotNull(entry.userDataDir)

        assertSame(first.frame, second.frame, "both layers read the one browser's frames")
        assertEquals(2, entry.refCount)

        SharedBrowserFrameCache.release("shared")
        assertTrue(job.isActive, "one layer letting go leaves the browser running for the other")
        assertSame(entry.currentUrl, SharedBrowserFrameCache.getCurrentUrl("shared"))

        SharedBrowserFrameCache.release("shared")
        assertTrue(job.isCancelled, "the last layer letting go stops the capture")
        assertFalse(process.isAlive)
        assertFalse(profile.exists(), "the browser's temporary profile is deleted with it")
        assertNull(entry.cdpConnection)
        assertNull(entry.browserProcess)
        assertNull(entry.frame.value)
        assertNull(SharedBrowserFrameCache.getCurrentUrl("shared"), "and the source is forgotten")
    }

    @Test
    fun `a browser that fails to start says why, on the source it was started for`() {
        val failures = listOf(
            IOException("no profile folder"),
            IllegalArgumentException("a reply that is not JSON"),
            IllegalStateException("the socket closed"),
        )
        failures.forEachIndexed { i, failure ->
            val flows = acquire("failing-$i") { _, _ -> throw failure }
            awaitUntil("the error for failing-$i") { flows.error.value != null }
            assertEquals("Browser error: ${failure.message}", flows.error.value)
        }
    }

    @Test
    fun `a new frame rate reaches the running browser, held between one and sixty`() {
        val launch = FakeLaunch()
        acquire("fps", launch.start)
        val entry = launch.next()

        SharedBrowserFrameCache.setFps("fps", 10)
        assertEquals(100, entry.captureIntervalMs)
        SharedBrowserFrameCache.setFps("fps", 500)
        assertEquals(16, entry.captureIntervalMs)
        SharedBrowserFrameCache.setFps("fps", 0)
        assertEquals(1000, entry.captureIntervalMs)
    }

    @Test
    fun `transparency is switched on the page it was asked for, and back`() {
        val fake = startFakeCdpBrowser()
        val launch = FakeLaunch(fake.wsUrl())
        acquire("glass", launch.start)
        launch.next()

        SharedBrowserFrameCache.setTransparent("glass", true)
        val (clear, clearParams) = fake.nextRequest()
        assertEquals("Emulation.setDefaultBackgroundColorOverride", clear)
        assertEquals("0", clearParams?.getValue("color")?.jsonObject?.getValue("a")?.jsonPrimitive?.content)
        val (script, scriptParams) = fake.nextRequest()
        assertEquals("Runtime.evaluate", script)
        assertTrue("transparent" in scriptParams?.getValue("expression")?.jsonPrimitive?.content.orEmpty())

        SharedBrowserFrameCache.setTransparent("glass", false)
        val (reset, resetParams) = fake.nextRequest()
        assertEquals("Emulation.setDefaultBackgroundColorOverride", reset)
        assertNull(resetParams?.get("color"), "the override is lifted, back to the page's own white")
        val (undo, undoParams) = fake.nextRequest()
        assertEquals("Runtime.evaluate", undo)
        assertFalse("transparent" in undoParams?.getValue("expression")?.jsonPrimitive?.content.orEmpty())
    }

    @Test
    fun `a page with no connection yet ignores transparency and navigation`() {
        val launch = FakeLaunch()
        val flows = acquire("unconnected", launch.start)
        launch.next()

        SharedBrowserFrameCache.setTransparent("unconnected", true)
        SharedBrowserFrameCache.navigateTo("unconnected", "https://example.org", "", forceTransparent = false)

        assertEquals("", flows.currentUrl.value, "nothing was navigated")
    }

    // ── findBrowserExecutable ──────────────────────────────────────────────────────────────────

    @Test
    fun `findBrowserExecutable returns null or a real, existing executable path`() {
        val path = BrowserProcesses.findBrowserExecutable()
        if (path != null) assertTrue(File(path).exists(), "a non-null result must be a real file: $path")
    }

    @Test
    fun `findBrowserExecutable stays consistent under a forced Windows os name`() {
        val saved = System.getProperty("os.name", "")
        try {
            System.setProperty("os.name", "Windows 11")
            val path = BrowserProcesses.findBrowserExecutable()
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
            val path = BrowserProcesses.findBrowserExecutable()
            if (path != null) assertTrue(File(path).exists(), "a non-null result must be a real file: $path")
        } finally {
            System.setProperty("os.name", saved)
        }
    }

    // ── findFreePort ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `findFreePort returns a usable port number`() {
        val port = BrowserProcesses.findFreePort()
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

    // ── Plain values ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the flows handed out carry the entry's own url`() {
        val entry = SharedBrowserFrameCache.CacheEntry(debugPort = 9222)
        entry.browserProcess = null
        val flows = SharedBrowserFrameCache.BrowserFlows(entry.frame, entry.error, entry.currentUrl)

        assertSame(entry.currentUrl, flows.currentUrl)
        assertEquals(9222, entry.debugPort)
        assertNull(entry.browserProcess)
    }

    @Test
    fun `a page keeps the frame rate it was asked for`() {
        val page = SharedBrowserFrameCache.BrowserPage(
            url = "https://example.com",
            renderWidth = 1280,
            renderHeight = 720,
            customCss = "",
            fps = 15,
            forceTransparent = false,
        )

        assertEquals(15, page.fps)
    }

    @Test
    fun `a new connection has no url listener`() {
        assertNull(cdpConnection().onUrlChanged)
    }

    // ── killProcess ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `killProcess terminates an already-exited process without throwing`() {
        val process = ProcessBuilder(shellCommand("exit 0")).start()
        process.waitFor()
        BrowserProcesses.killProcess(process)
    }
}

/** How long the fake CDP server waits for a closed client's session to end before stopping. */
private const val SESSION_END_TIMEOUT_MS = 5_000L

/** The bound on its shutdown once its sessions have ended; it returns as soon as Netty has stopped. */
private const val SHUTDOWN_TIMEOUT_MS = 5_000L

private val PAGE = SharedBrowserFrameCache.BrowserPage(
    url = "https://example.org", renderWidth = 640, renderHeight = 360,
    customCss = "", fps = 30, forceTransparent = false,
)
