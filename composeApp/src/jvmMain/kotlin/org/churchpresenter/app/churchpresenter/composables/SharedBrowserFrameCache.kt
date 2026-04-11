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
import java.awt.image.BufferedImage
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

    init {
        // Kill all browser processes on JVM shutdown to prevent orphaned Chrome/Edge windows
        Runtime.getRuntime().addShutdownHook(Thread {
            synchronized(this@SharedBrowserFrameCache) {
                entries.values.forEach { stopBrowser(it) }
                entries.clear()
            }
        })
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
        entry.refCount++
        if (entry.refCount == 1) {
            entry.captureJob = scope.launch {
                try {
                    startBrowser(entry, sourceId, url, renderWidth, renderHeight, customCss, fps, forceTransparent)
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
                        put("expression", "document.documentElement.style.background='transparent';document.body.style.background='transparent';")
                    })
                } else {
                    // Reset to default (opaque white) background
                    cdp.sendAsync("Emulation.setDefaultBackgroundColorOverride", buildJsonObject {})
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put("expression", "document.documentElement.style.background='';document.body.style.background='';")
                    })
                }
            } catch (_: Exception) {}
        }
    }

    /** Update capture FPS without restarting the browser. */
    fun setFps(sourceId: String, fps: Int) {
        val entry = synchronized(this) { entries[sourceId] } ?: return
        entry.captureIntervalMs = (1000L / fps.coerceIn(1, 60)).coerceAtLeast(16)
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
                delay(2000) // wait for page load
                if (forceTransparent) {
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put("expression", "document.documentElement.style.background='transparent';document.body.style.background='transparent';")
                    })
                }
                if (customCss.isNotBlank()) {
                    val escapedCss = customCss
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "")
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put("expression", "var s=document.createElement('style');s.textContent='$escapedCss';document.head.appendChild(s);")
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

    private fun findBrowserExecutable(): String? {
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
                    else listOf("google-chrome-stable", "google-chrome", "chromium-browser", "chromium", "microsoft-edge-stable")
        val whichCmd = if (isWindows) "where" else "which"
        for (name in names) {
            try {
                val proc = ProcessBuilder(whichCmd, name).redirectErrorStream(true).start()
                val output = proc.inputStream.bufferedReader().readText().trim()
                if (proc.waitFor() == 0 && output.isNotBlank()) {
                    val path = output.lines().first().trim()
                    if (java.io.File(path).exists()) return path
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    /** Detect the major version of a Chrome/Edge binary, returns 0 if unknown. */
    private fun detectChromeVersion(browserPath: String): Int {
        return try {
            val proc = ProcessBuilder(browserPath, "--version").redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            // Output like "Google Chrome 120.0.6099.130" or "Microsoft Edge 119.0.2151.72"
            val match = Regex("(\\d+)\\.").find(output)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 }
    }

    // ── CDP Browser Lifecycle ──────────────────────────────────────

    private suspend fun startBrowser(
        entry: CacheEntry,
        sourceId: String,
        url: String,
        renderWidth: Int,
        renderHeight: Int,
        customCss: String,
        fps: Int,
        forceTransparent: Boolean
    ) {
        val browserPath = findBrowserExecutable()
        if (browserPath == null) {
            System.err.println("[BrowserSource] No Chrome or Edge browser found on system")
            entry.error.value = "Chrome or Edge not found. Install a Chromium browser to use Browser sources."
            return
        }

        val port = findFreePort()
        entry.debugPort = port
        System.err.println("[BrowserSource] Launching headless browser: $browserPath on port $port")

        // Launch headless browser
        // Use --headless=new (Chrome 112+) with --headless fallback for older versions.
        // --disable-gpu prevents white-window issues on machines without GPU acceleration.
        // --window-position puts the window off-screen as a safety net if headless fails.
        val headlessFlag = if (detectChromeVersion(browserPath) >= 112) "--headless=new" else "--headless"
        val command = listOf(
            browserPath,
            headlessFlag,
            "--remote-debugging-port=$port",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-popup-blocking",
            "--disable-translate",
            "--disable-gpu",
            "--disable-software-rasterizer",
            "--mute-audio",
            "--window-size=$renderWidth,$renderHeight",
            "--window-position=-32000,-32000",
            "about:blank"
        )

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

        // Wait for CDP to be ready
        val ready = waitForCdpReady(port, timeoutMs = 15000)
        if (!ready) {
            System.err.println("[BrowserSource] CDP did not become ready in time")
            entry.error.value = "Browser failed to start"
            killProcess(process)
            entry.browserProcess = null
            return
        }
        System.err.println("[BrowserSource] CDP ready on port $port")

        // Get the page's WebSocket URL
        val wsUrl = withContext(Dispatchers.IO) { getPageWebSocketUrl(port) }
        if (wsUrl == null) {
            System.err.println("[BrowserSource] Could not get page WebSocket URL")
            killProcess(process)
            entry.browserProcess = null
            return
        }
        System.err.println("[BrowserSource] Connecting WebSocket: $wsUrl")

        // Connect WebSocket
        val cdp = CdpConnection()
        val connected = withContext(Dispatchers.IO) { cdp.connect(wsUrl) }
        if (!connected) {
            System.err.println("[BrowserSource] WebSocket connection failed")
            killProcess(process)
            entry.browserProcess = null
            return
        }
        entry.cdpConnection = cdp
        cdp.onUrlChanged = { url -> entry.currentUrl.value = url }
        System.err.println("[BrowserSource] WebSocket connected")

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
            delay(3000)

            // Inject transparency CSS
            if (forceTransparent) {
                cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                    put("expression", "document.documentElement.style.background='transparent';document.body.style.background='transparent';")
                })
            }
            if (customCss.isNotBlank()) {
                val escapedCss = customCss
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "")
                cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                    put("expression", "var s=document.createElement('style');s.textContent='$escapedCss';document.head.appendChild(s);")
                })
            }
        }

        // Start capture loop
        entry.captureIntervalMs = (1000L / fps.coerceIn(1, 60)).coerceAtLeast(33)
        System.err.println("[BrowserSource] Starting capture loop at ${fps}fps")

        var frameCount = 0
        while (currentCoroutineContext().isActive) {
            try {
                val response = cdp.sendAsync("Page.captureScreenshot", buildJsonObject {
                    put("format", "png")
                })

                if (response == null) {
                    if (frameCount == 0) {
                        System.err.println("[BrowserSource] captureScreenshot returned null")
                    }
                    delay(entry.captureIntervalMs)
                    continue
                }

                val data = response["data"]?.jsonPrimitive?.contentOrNull
                if (data == null) {
                    if (frameCount == 0) {
                        System.err.println("[BrowserSource] captureScreenshot response has no 'data': ${response.keys}")
                    }
                    delay(entry.captureIntervalMs)
                    continue
                }

                val pngBytes = withContext(Dispatchers.IO) {
                    Base64.getDecoder().decode(data)
                }
                val img = withContext(Dispatchers.IO) {
                    ImageIO.read(ByteArrayInputStream(pngBytes))
                }
                if (img != null) {
                    entry.frame.value = img.toComposeImageBitmap()
                    frameCount++
                    if (frameCount == 1) {
                        System.err.println("[BrowserSource] First frame captured: ${img.width}x${img.height}")
                    }
                } else {
                    if (frameCount == 0) {
                        System.err.println("[BrowserSource] ImageIO.read returned null (${pngBytes.size} bytes)")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (frameCount == 0) {
                    System.err.println("[BrowserSource] Capture error: ${e.message}")
                }
            }
            delay(entry.captureIntervalMs)
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
                if (response.statusCode() == 200) return true
            } catch (_: Exception) {
                // Not ready yet
            }
            delay(500)
        }
        return false
    }

    private fun getPageWebSocketUrl(port: Int): String? {
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

        entry.frame.value = null
    }

    private fun killProcess(process: Process) {
        try {
            if (System.getProperty("os.name", "").lowercase().contains("win")) {
                try {
                    val pid = process.pid()
                    ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString())
                        .redirectErrorStream(true).start()
                        .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Throwable) {}
            } else {
                process.destroyForcibly()
            }
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Throwable) {
            process.destroyForcibly()
        }
    }

    // ── CDP WebSocket Connection ───────────────────────────────────

    private class CdpConnection {
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
                        System.err.println("[BrowserSource] WebSocket error: ${error::class.simpleName}: ${error.message}")
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
                    .get(10, java.util.concurrent.TimeUnit.SECONDS)
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
                socket.sendText(msg.toString(), true)?.get(5, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: Exception) {
                pending.remove(id)
                System.err.println("[BrowserSource] CDP sendText '$method' failed: ${e::class.simpleName}: ${e.message}")
                return null
            }

            // Wait for the response, but don't block coroutine cancellation
            return try {
                withContext(Dispatchers.IO) {
                    future.get(30, java.util.concurrent.TimeUnit.SECONDS)
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
            try {
                val json = Json.parseToJsonElement(text).jsonObject
                val id = json["id"]?.jsonPrimitive?.intOrNull
                if (id != null) {
                    // Response to a command
                    val result = json["result"]?.jsonObject
                    val error = json["error"]?.jsonObject
                    if (error != null) {
                        System.err.println("[BrowserSource] CDP error for id=$id: $error")
                    }
                    pending.remove(id)?.complete(result)
                } else {
                    // CDP event — check for navigation URL changes
                    val method = json["method"]?.jsonPrimitive?.contentOrNull
                    if (method == "Page.frameNavigated") {
                        val frame = json["params"]?.jsonObject?.get("frame")?.jsonObject
                        val url = frame?.get("url")?.jsonPrimitive?.contentOrNull
                        val parentId = frame?.get("parentId")?.jsonPrimitive?.contentOrNull
                        // Only track main frame navigations (no parentId = main frame)
                        if (url != null && parentId == null) {
                            onUrlChanged?.invoke(url)
                        }
                    }
                }
            } catch (e: Exception) {
                System.err.println("[BrowserSource] handleMessage error: ${e.message}")
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
