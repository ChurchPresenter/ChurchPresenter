package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.churchpresenter.diagnostics.CrashReporter
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import org.churchpresenter.app.churchpresenter.utils.addGuardedShutdownHook

private const val HTTP_OK = 200
private const val MILLIS_PER_SECOND = 1000L
private const val MIN_FPS = 1
private const val MAX_FPS = 60
private const val MIN_CAPTURE_INTERVAL_MS = 16L
private const val MIN_STARTUP_CAPTURE_INTERVAL_MS = 33L
private const val NAVIGATE_SETTLE_MS = 2000L
private const val PAGE_LOAD_SETTLE_MS = 3000L
private const val DEVTOOLS_POLL_INTERVAL_MS = 500L
private const val WMIC_TIMEOUT_S = 5L
private const val PROCESS_KILL_TIMEOUT_S = 3L
private const val WEBSOCKET_CONNECT_TIMEOUT_S = 10L
private const val WEBSOCKET_SEND_TIMEOUT_S = 5L
private const val CDP_RESPONSE_TIMEOUT_S = 30L

/**
 * Shared browser frame cache using Chrome DevTools Protocol (CDP).
 *
 * Launches a headless system browser (Edge or Chrome) and captures
 * transparent PNG screenshots via CDP. Completely independent of JCEF,
 * so the Web Tab continues to work normally with windowless_rendering_enabled=false.
 */
object SharedBrowserFrameCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = mutableMapOf<String, CacheEntry>()
    private val httpClient = HttpClient.newHttpClient()
    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
    @Volatile private var zombiesCleaned = false

    init {
        // Kill all browser processes on JVM shutdown to prevent orphaned Chrome/Edge windows
        addGuardedShutdownHook("browser-source") {
            synchronized(this@SharedBrowserFrameCache) {
                entries.values.forEach { stopBrowser(it) }
                entries.clear()
            }
        }
    }

    private class CacheEntry(
        val frame: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null),
        val error: MutableStateFlow<String?> = MutableStateFlow(null),
        val currentUrl: MutableStateFlow<String> = MutableStateFlow(""),
        var refCount: Int = 0,
        var browserProcess: Process? = null,
        var captureJob: Job? = null,
        var cdpConnection: CdpConnection? = null,
        var debugPort: Int = 0,
        var userDataDir: java.io.File? = null,
        @Volatile var captureIntervalMs: Long = 33
    )

    data class BrowserFlows(
        val frame: StateFlow<ImageBitmap?>,
        val error: StateFlow<String?>,
        val currentUrl: StateFlow<String>
    )

    @Synchronized
    fun acquire(
        sourceId: String,
        url: String,
        renderWidth: Int,
        renderHeight: Int,
        customCss: String,
        fps: Int,
        forceTransparent: Boolean
    ): BrowserFlows {
        val entry = entries.getOrPut(sourceId) { CacheEntry() }
        ResourceCensus.record(SharedResource.BROWSER_SOURCE, entries.size)
        entry.refCount++
        if (entry.refCount == 1) {
            entry.captureJob = scope.launch {
                try {
                    startBrowser(entry, url, renderWidth, renderHeight, customCss, fps, forceTransparent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    System.err.println("[BrowserSource] Failed to start CDP browser: ${e.message}")
                    entry.error.value = "Browser error: ${e.message}"
                }
            }
        }
        return BrowserFlows(entry.frame, entry.error, entry.currentUrl)
    }

    /** Toggle transparent background on an existing browser via CDP. */
    fun setTransparent(sourceId: String, transparent: Boolean) {
        val entry = synchronized(this) { entries[sourceId] } ?: return
        val cdp = entry.cdpConnection ?: return
        scope.launch {
            try {
                if (transparent) {
                    cdp.sendAsync("Emulation.setDefaultBackgroundColorOverride", buildJsonObject {
                        put("color", buildJsonObject {
                            put("r", 0); put("g", 0); put("b", 0); put("a", 0)
                        })
                    })
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put(
                            "expression",
                            "document.documentElement.style.background='transparent';document.body.style.background='transparent';"
                        )
                    })
                } else {
                    // Reset to default (opaque white) background
                    cdp.sendAsync("Emulation.setDefaultBackgroundColorOverride", buildJsonObject {})
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put(
                            "expression",
                            "document.documentElement.style.background='';document.body.style.background='';"
                        )
                    })
                }
            } catch (_: Exception) {}
        }
    }

    /** Update capture FPS without restarting the browser. */
    fun setFps(sourceId: String, fps: Int) {
        val entry = synchronized(this) { entries[sourceId] } ?: return
        entry.captureIntervalMs = (
            MILLIS_PER_SECOND / fps.coerceIn(MIN_FPS, MAX_FPS)
        ).coerceAtLeast(MIN_CAPTURE_INTERVAL_MS)
    }

    /** Get the current URL flow for a source (for properties panel display). */
    fun getCurrentUrl(sourceId: String): StateFlow<String>? {
        return synchronized(this) { entries[sourceId] }?.currentUrl
    }

    /**
     * Navigate an existing browser to a new URL without restarting Chrome.
     */
    fun navigateTo(sourceId: String, url: String, customCss: String, forceTransparent: Boolean) {
        val entry = synchronized(this) { entries[sourceId] } ?: return
        val cdp = entry.cdpConnection ?: return
        scope.launch {
            try {
                entry.currentUrl.value = url
                cdp.sendAsync("Page.navigate", buildJsonObject { put("url", url) })
                delay(NAVIGATE_SETTLE_MS) // wait for page load
                if (forceTransparent) {
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put(
                            "expression",
                            "document.documentElement.style.background='transparent';document.body.style.background='transparent';"
                        )
                    })
                }
                if (customCss.isNotBlank()) {
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put(
                            "expression",
                            "var s=document.createElement('style');s.textContent='${escapeForJsStringLiteral(customCss)}';document.head.appendChild(s);"
                        )
                    })
                }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun release(sourceId: String) {
        val entry = entries[sourceId] ?: return
        entry.refCount--
        if (entry.refCount <= 0) {
            stopBrowser(entry)
            entries.remove(sourceId)
        }
    }

    // ── Browser Discovery ──────────────────────────────────────────

    /** The executable `which`/`where` reports for [name], when it exists on disk. */
    private fun browserOnPath(whichCmd: String, name: String): String? = try {
        val proc = ProcessBuilder(whichCmd, name).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        val path = output.lines().firstOrNull()?.trim()
        if (proc.waitFor() == 0 && !path.isNullOrBlank() && java.io.File(path).exists()) path else null
    } catch (_: Exception) {
        null
    }

    internal fun findBrowserExecutable(): String? {
        val osName = System.getProperty("os.name", "").lowercase()
        val isWindows = osName.contains("win")

        // Check well-known install paths first
        val candidates = when {
            isWindows -> listOf(
                "${System.getenv("ProgramFiles(x86)")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${System.getenv("ProgramFiles")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${System.getenv("LOCALAPPDATA")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${System.getenv("ProgramFiles")}\\Google\\Chrome\\Application\\chrome.exe",
                "${System.getenv("ProgramFiles(x86)")}\\Google\\Chrome\\Application\\chrome.exe",
                "${System.getenv("LOCALAPPDATA")}\\Google\\Chrome\\Application\\chrome.exe"
            )
            osName.contains("mac") -> listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                // Homebrew paths
                "/opt/homebrew/bin/chromium",
                "/usr/local/bin/chromium"
            )
            else -> listOf(
                "/usr/bin/google-chrome-stable",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium",
                "/snap/bin/chromium",
                "/usr/bin/microsoft-edge-stable",
                "/usr/bin/microsoft-edge"
            )
        }
        val found = candidates.firstOrNull { path ->
            try { java.io.File(path).exists() } catch (_: Exception) { false }
        }
        if (found != null) return found

        // Fallback: check PATH
        val names = if (isWindows) listOf("msedge.exe", "chrome.exe")
                    else listOf(
                        "google-chrome-stable",
                        "google-chrome",
                        "chromium-browser",
                        "chromium",
                        "microsoft-edge-stable"
                    )
        val whichCmd = if (isWindows) "where" else "which"
        return names.firstNotNullOfOrNull { name -> browserOnPath(whichCmd, name) }
    }

    internal fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    /**
     * Kill orphaned headless Chrome/Edge processes from previous runs.
     * Only runs once per app session, on the first acquire() call.
     */
    private fun killZombieBrowsers() {
        if (zombiesCleaned) return
        zombiesCleaned = true
        try {
            if (isWindows) {
                // Find headless Chrome/Edge processes via wmic
                val proc = ProcessBuilder(
                    "wmic", "process", "where",
                    "CommandLine like '%--headless%' and (Name='msedge.exe' or Name='chrome.exe')",
                    "get", "ProcessId"
                ).redirectErrorStream(true).start()
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor(WMIC_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                val pids = Regex("\\d+").findAll(output).map { it.value }.toList()
                for (pid in pids) {
                    System.err.println("[BrowserSource] Killing zombie browser process: PID $pid")
                    ProcessBuilder("taskkill", "/F", "/T", "/PID", pid)
                        .redirectErrorStream(true).start()
                        .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                }
            } else {
                // On Linux/macOS, kill headless chrome/edge processes
                ProcessBuilder("pkill", "-f", "--headless.*--remote-debugging-port")
                    .redirectErrorStream(true).start()
                    .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
            }
        } catch (_: Exception) {}
    }

    // ── CDP Browser Lifecycle ──────────────────────────────────────

    /** The CDP connection to the freshly launched browser, or null once the failure is reported. */
    private suspend fun connectCdp(entry: CacheEntry, port: Int): CdpConnection? {
        if (!waitForCdpReady(port, timeoutMs = 15000)) {
            System.err.println("[BrowserSource] CDP did not become ready in time")
            CrashReporter.reportWarning(
                "BrowserSource: CDP did not become ready in time",
                tags = mapOf("subsystem" to "browser-source")
            )
            entry.error.value = "Browser failed to start"
            return null
        }
        System.err.println("[BrowserSource] CDP ready on port $port")
        return openCdpWebSocket(port)
    }

    private suspend fun openCdpWebSocket(port: Int): CdpConnection? {
        val wsUrl = withContext(Dispatchers.IO) { getPageWebSocketUrl(port) }
        if (wsUrl == null) {
            System.err.println("[BrowserSource] Could not get page WebSocket URL")
            CrashReporter.reportWarning(
                "BrowserSource: Could not get page WebSocket URL",
                tags = mapOf("subsystem" to "browser-source")
            )
            return null
        }
        System.err.println("[BrowserSource] Connecting WebSocket: $wsUrl")
        val cdp = CdpConnection()
        val connected = withContext(Dispatchers.IO) { cdp.connect(wsUrl) }
        if (!connected) {
            System.err.println("[BrowserSource] WebSocket connection failed")
            CrashReporter.reportWarning(
                "BrowserSource: WebSocket connection to CDP failed",
                tags = mapOf("subsystem" to "browser-source")
            )
            return null
        }
        return cdp
    }

    private suspend fun startBrowser(
        entry: CacheEntry,
        url: String,
        renderWidth: Int,
        renderHeight: Int,
        customCss: String,
        fps: Int,
        forceTransparent: Boolean
    ) {
        // Kill any zombie browsers from previous runs (once per session)
        withContext(Dispatchers.IO) { killZombieBrowsers() }

        val browserPath = findBrowserExecutable()
        if (browserPath == null) {
            System.err.println("[BrowserSource] No Chrome or Edge browser found on system")
            CrashReporter.reportWarning(
                "BrowserSource: No Chrome or Edge browser found on system",
                tags = mapOf("subsystem" to "browser-source")
            )
            entry.error.value = "Chrome or Edge not found. Install a Chromium browser to use Browser sources."
            return
        }

        val port = findFreePort()
        entry.debugPort = port

        // Create a unique temp user-data-dir to avoid profile lock conflicts
        val userDataDir = withContext(Dispatchers.IO) {
            java.io.File.createTempFile("cp-browser-", "").apply {
                delete()
                mkdirs()
            }
        }
        entry.userDataDir = userDataDir

        System.err.println(
            "[BrowserSource] Launching headless browser: $browserPath on port $port (userData=$userDataDir)"
        )

        val command = buildBrowserLaunchCommand(browserPath, port, userDataDir.absolutePath, renderWidth, renderHeight)

        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(command).redirectErrorStream(true).start()
        }
        entry.browserProcess = process

        // Drain browser stdout/stderr to prevent pipe blocking
        scope.launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { /* discard */ }
                }
            } catch (_: Throwable) {}
        }

        val cdp = connectCdp(entry, port)
        if (cdp == null) {
            killProcess(process)
            entry.browserProcess = null
            return
        }
        entry.cdpConnection = cdp
        cdp.onUrlChanged = { url -> entry.currentUrl.value = url }
        System.err.println("[BrowserSource] WebSocket connected")

        configurePage(cdp, url, renderWidth, renderHeight, customCss, forceTransparent)
        runCaptureLoop(entry, cdp, fps)
    }

    /** Viewport, transparency and navigation for a freshly connected page. */
    private suspend fun configurePage(
        cdp: CdpConnection,
        url: String,
        renderWidth: Int,
        renderHeight: Int,
        customCss: String,
        forceTransparent: Boolean,
    ) {
    // Configure viewport and transparency
    var resp = cdp.sendAsync("Emulation.setDeviceMetricsOverride", buildJsonObject {
        put("width", renderWidth)
        put("height", renderHeight)
        put("deviceScaleFactor", 1)
        put("mobile", false)
    })
    System.err.println("[BrowserSource] setDeviceMetricsOverride: $resp")

    if (forceTransparent) {
        resp = cdp.sendAsync("Emulation.setDefaultBackgroundColorOverride", buildJsonObject {
            put("color", buildJsonObject {
                put("r", 0)
                put("g", 0)
                put("b", 0)
                put("a", 0)
            })
        })
        System.err.println("[BrowserSource] setDefaultBackgroundColorOverride: $resp")
    }

    cdp.sendAsync("Page.enable", null)

    // Navigate to the URL
    if (url.isNotBlank()) {
        resp = cdp.sendAsync("Page.navigate", buildJsonObject { put("url", url) })
        System.err.println("[BrowserSource] Page.navigate($url): $resp")

        // Wait for page to load
        delay(PAGE_LOAD_SETTLE_MS)

        // Inject transparency CSS
        if (forceTransparent) {
            cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                put(
                    "expression",
                    "document.documentElement.style.background='transparent';document.body.style.background='transparent';"
                )
            })
        }
        if (customCss.isNotBlank()) {
            cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                put(
                    "expression",
                    "var s=document.createElement('style');s.textContent='${escapeForJsStringLiteral(customCss)}';document.head.appendChild(s);"
                )
            })
        }
    }

    }

    /** Screenshots the page at [fps] into the entry until the coroutine is cancelled. */
    private suspend fun runCaptureLoop(entry: CacheEntry, cdp: CdpConnection, fps: Int) {
        entry.captureIntervalMs = (
            MILLIS_PER_SECOND / fps.coerceIn(MIN_FPS, MAX_FPS)
        ).coerceAtLeast(MIN_STARTUP_CAPTURE_INTERVAL_MS)
        System.err.println("[BrowserSource] Starting capture loop at ${fps}fps")

        var frameCount = 0
        while (currentCoroutineContext().isActive) {
            try {
                if (captureFrame(entry, cdp, first = frameCount == 0)) frameCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (frameCount == 0) System.err.println("[BrowserSource] Capture error: ${e.message}")
            }
            delay(entry.captureIntervalMs)
        }
    }

    /** Screenshots the page once into [entry]; false when nothing decodable came back. */
    private suspend fun captureFrame(entry: CacheEntry, cdp: CdpConnection, first: Boolean): Boolean {
        val response = cdp.sendAsync("Page.captureScreenshot", buildJsonObject { put("format", "png") })
        val data = response?.get("data")?.jsonPrimitive?.contentOrNull
        if (data == null) {
            if (first) reportMissingScreenshot(response)
            return false
        }
        val pngBytes = withContext(Dispatchers.IO) { Base64.getDecoder().decode(data) }
        val img = withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(pngBytes)) }
        if (img == null) {
            if (first) {
                System.err.println("[BrowserSource] ImageIO.read returned null (${pngBytes.size} bytes)")
            }
            return false
        }
        entry.frame.value = img.toComposeImageBitmap()
        if (first) {
            System.err.println("[BrowserSource] First frame captured: ${img.width}x${img.height}")
        }
        return true
    }

    private fun reportMissingScreenshot(response: JsonObject?) {
        if (response == null) {
            System.err.println("[BrowserSource] captureScreenshot returned null")
        } else {
            System.err.println("[BrowserSource] captureScreenshot response has no 'data': ${response.keys}")
        }
    }

    private suspend fun waitForCdpReady(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$port/json/version"))
                    .GET().build()
                val response = withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
                if (response.statusCode() == HTTP_OK) return true
            } catch (_: Exception) {
                // Not ready yet
            }
            delay(DEVTOOLS_POLL_INTERVAL_MS)
        }
        return false
    }

    internal fun getPageWebSocketUrl(port: Int): String? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/json"))
                .GET().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val pages = Json.parseToJsonElement(response.body()).jsonArray
            // Find the actual page tab, not extension background pages
            val page = pages.firstOrNull { entry ->
                entry.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "page"
            } ?: pages.firstOrNull()
            page?.jsonObject?.get("webSocketDebuggerUrl")?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            System.err.println("[BrowserSource] getPageWebSocketUrl error: ${e.message}")
            null
        }
    }

    private fun stopBrowser(entry: CacheEntry) {
        entry.captureJob?.cancel()
        entry.captureJob = null

        try { entry.cdpConnection?.close() } catch (_: Throwable) {}
        entry.cdpConnection = null

        val process = entry.browserProcess
        if (process != null) {
            killProcess(process)
            entry.browserProcess = null
        }

        // Clean up temp user-data-dir
        val dataDir = entry.userDataDir
        if (dataDir != null) {
            try { dataDir.deleteRecursively() } catch (_: Throwable) {}
            entry.userDataDir = null
        }

        entry.frame.value = null
    }

    internal fun killProcess(process: Process) {
        try {
            if (isWindows) {
                try {
                    val pid = process.pid()
                    ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString())
                        .redirectErrorStream(true).start()
                        .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Throwable) {}
            } else {
                process.destroyForcibly()
            }
            process.waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Throwable) {
            process.destroyForcibly()
        }
    }

    // ── CDP WebSocket Connection ───────────────────────────────────

    internal class CdpConnection {
        private var ws: WebSocket? = null
        private val msgId = AtomicInteger(0)
        private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonObject?>>()
        private val messageBuffer = StringBuilder()
        private var fragmentCount = 0
        var onUrlChanged: ((String) -> Unit)? = null

        fun connect(wsUrl: String): Boolean {
            return try {
                val listener = object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        // Request unlimited messages upfront — the default request(1)
                        // causes flow-control starvation with large CDP responses
                        webSocket.request(Long.MAX_VALUE)
                    }

                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                        messageBuffer.append(data)
                        fragmentCount++
                        if (last) {
                            val text = messageBuffer.toString()
                            messageBuffer.clear()
                            fragmentCount = 0
                            handleMessage(text)
                        }
                        return CompletableFuture.completedFuture(null)
                    }

                    override fun onError(webSocket: WebSocket, error: Throwable) {
                        System.err.println(
                            "[BrowserSource] WebSocket error: ${error::class.simpleName}: ${error.message}"
                        )
                        pending.values.forEach { it.complete(null) }
                    }

                    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                        System.err.println("[BrowserSource] WebSocket closed: $statusCode $reason")
                        pending.values.forEach { it.complete(null) }
                        return CompletableFuture.completedFuture(null)
                    }
                }

                ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), listener)
                    .get(WEBSOCKET_CONNECT_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                true
            } catch (e: Exception) {
                System.err.println("[BrowserSource] WebSocket connect error: ${e.message}")
                false
            }
        }

        /**
         * Send a CDP command and suspend until the response arrives.
         */
        suspend fun sendAsync(method: String, params: JsonObject?): JsonObject? {
            val id = msgId.incrementAndGet()
            val future = CompletableFuture<JsonObject?>()
            pending[id] = future

            val msg = buildJsonObject {
                put("id", id)
                put("method", method)
                if (params != null) put("params", params)
            }

            try {
                val socket = ws ?: run {
                    pending.remove(id)
                    System.err.println("[BrowserSource] CDP send '$method': WebSocket is null")
                    return null
                }
                socket.sendText(
                    msg.toString(),
                    true
                )?.get(WEBSOCKET_SEND_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: Exception) {
                pending.remove(id)
                System.err.println(
                    "[BrowserSource] CDP sendText '$method' failed: ${e::class.simpleName}: ${e.message}"
                )
                return null
            }

            // Wait for the response, but don't block coroutine cancellation
            return try {
                withContext(Dispatchers.IO) {
                    future.get(CDP_RESPONSE_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                }
            } catch (e: CancellationException) {
                pending.remove(id)
                throw e
            } catch (e: Exception) {
                pending.remove(id)
                System.err.println("[BrowserSource] CDP await '$method' failed: ${e::class.simpleName}: ${e.message}")
                null
            }
        }

        private fun handleMessage(text: String) {
            when (val message = parseCdpMessage(text)) {
                is CdpMessage.Response -> {
                    if (message.error != null) {
                        System.err.println("[BrowserSource] CDP error for id=${message.id}: ${message.error}")
                    }
                    pending.remove(message.id)?.complete(message.result)
                }
                is CdpMessage.MainFrameNavigated -> onUrlChanged?.invoke(message.url)
                CdpMessage.Ignored -> Unit
            }
        }

        fun close() {
            try {
                ws?.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            } catch (_: Throwable) {}
            pending.values.forEach { it.complete(null) }
            pending.clear()
            ws = null
        }
    }
}

/** The three things a frame arriving on the CDP socket can turn out to be. */
internal sealed interface CdpMessage {
    /** An answer to a command this end sent, identified by the id it was sent with. */
    data class Response(val id: Int, val result: JsonObject?, val error: JsonObject?) : CdpMessage

    /** The page navigated itself — a redirect, a link, a script — to [url]. */
    data class MainFrameNavigated(val url: String) : CdpMessage

    /** Anything else on the socket: other events, sub-frame navigations, unparseable text. */
    data object Ignored : CdpMessage
}

/**
 * Sorts one raw CDP frame into what the connection should do about it.
 *
 * Chrome multiplexes command responses and unsolicited events down the same socket, told apart only
 * by whether the frame carries an `id`. Getting that backwards strands a waiting `sendAsync` on its
 * 30-second timeout, so the distinction is made once, here.
 *
 * **Only main-frame navigations count.** A page with an ad iframe emits `Page.frameNavigated` for the
 * iframe too, and reporting that as the source's URL would show the operator an advert's address
 * instead of the page they loaded. The main frame is the one with no `parentId`.
 *
 * Anything unparseable is [CdpMessage.Ignored] rather than thrown: this runs on the WebSocket's own
 * callback thread, where an exception would take the connection down and freeze the source on its
 * last frame.
 */
internal fun parseCdpMessage(text: String): CdpMessage {
    return try {
        val json = Json.parseToJsonElement(text).jsonObject
        val id = json["id"]?.jsonPrimitive?.intOrNull
        if (id != null) {
            return CdpMessage.Response(id, json["result"]?.jsonObject, json["error"]?.jsonObject)
        }
        if (json["method"]?.jsonPrimitive?.contentOrNull != "Page.frameNavigated") return CdpMessage.Ignored

        val frame = json["params"]?.jsonObject?.get("frame")?.jsonObject
        val url = frame?.get("url")?.jsonPrimitive?.contentOrNull
        val parentId = frame?.get("parentId")?.jsonPrimitive?.contentOrNull
        if (url != null && parentId == null) CdpMessage.MainFrameNavigated(url) else CdpMessage.Ignored
    } catch (e: Exception) {
        System.err.println("[BrowserSource] handleMessage error: ${e.message}")
        CdpMessage.Ignored
    }
}

/** Builds the headless-browser launch command line for a [SharedBrowserFrameCache] capture. */
internal fun buildBrowserLaunchCommand(
    browserPath: String,
    debugPort: Int,
    userDataDir: String,
    renderWidth: Int,
    renderHeight: Int,
): List<String> = listOf(
    browserPath,
    "--headless=new",
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$userDataDir",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-extensions",
    "--disable-popup-blocking",
    "--disable-translate",
    "--disable-gpu",
    "--disable-software-rasterizer",
    "--no-sandbox",
    "--mute-audio",
    "--window-size=$renderWidth,$renderHeight",
    "--window-position=-32000,-32000",
    "about:blank"
)

/** Escapes text for safe interpolation into a single-quoted JS string literal injected via CDP. */
internal fun escapeForJsStringLiteral(text: String): String = text
    .replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("\n", "\\n")
    .replace("\r", "")
