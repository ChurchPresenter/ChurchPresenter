package org.churchpresenter.canvas

import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.churchpresenter.diagnostics.CrashReporter
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook
import org.churchpresenter.diagnostics.Log

private const val MILLIS_PER_SECOND = 1000L
private const val MIN_FPS = 1
private const val MAX_FPS = 60
private const val MIN_CAPTURE_INTERVAL_MS = 16L
private const val MIN_STARTUP_CAPTURE_INTERVAL_MS = 33L
private const val NAVIGATE_SETTLE_MS = 2000L
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

    init {
        // Kill all browser processes on JVM shutdown to prevent orphaned Chrome/Edge windows
        addGuardedShutdownHook("browser-source") {
            synchronized(this@SharedBrowserFrameCache) {
                entries.values.forEach { stopBrowser(it) }
                entries.clear()
            }
        }
    }

    internal class CacheEntry(
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
        page: BrowserPage,
    ): BrowserFlows {
        val entry = entries.getOrPut(sourceId) { CacheEntry() }
        ResourceCensus.record(SharedResource.BROWSER_SOURCE, entries.size)
        entry.refCount++
        if (entry.refCount == 1) {
            entry.captureJob = scope.launch {
                fun failed(e: Exception) {
                    Log.warn("BrowserSource", "Failed to start CDP browser: ${e.message}")
                    entry.error.value = "Browser error: ${e.message}"
                }
                // Launching the process, its temp profile and the DevTools socket fail with I/O
                // errors; a malformed reply or a closed socket with the two runtime ones.
                try {
                    startBrowser(entry, page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    failed(e)
                } catch (e: IllegalArgumentException) {
                    failed(e)
                } catch (e: IllegalStateException) {
                    failed(e)
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
                            "document.documentElement.style.background='transparent';" +
                                "document.body.style.background='transparent';"
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
                            "document.documentElement.style.background='transparent';" +
                                "document.body.style.background='transparent';"
                        )
                    })
                }
                if (customCss.isNotBlank()) {
                    cdp.sendAsync("Runtime.evaluate", buildJsonObject {
                        put(
                            "expression",
                            "var s=document.createElement('style');" +
                                "s.textContent='${escapeForJsStringLiteral(customCss)}';document.head.appendChild(s);"
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





    // ── CDP Browser Lifecycle ──────────────────────────────────────



    /** The page one browser source shows, and how it is rendered and captured. */
    data class BrowserPage(
        val url: String,
        val renderWidth: Int,
        val renderHeight: Int,
        val customCss: String,
        val fps: Int,
        val forceTransparent: Boolean,
    )

    private suspend fun startBrowser(entry: CacheEntry, page: BrowserPage) {
        // Kill any zombie browsers from previous runs (once per session)
        withContext(Dispatchers.IO) { BrowserProcesses.killZombieBrowsers() }

        val browserPath = BrowserProcesses.findBrowserExecutable()
        if (browserPath == null) {
            Log.warn("BrowserSource", "No Chrome or Edge browser found on system")
            CrashReporter.reportWarning(
                "BrowserSource: No Chrome or Edge browser found on system",
                tags = mapOf("subsystem" to "browser-source")
            )
            entry.error.value = "Chrome or Edge not found. Install a Chromium browser to use Browser sources."
            return
        }

        val port = BrowserProcesses.findFreePort()
        entry.debugPort = port

        // Create a unique temp user-data-dir to avoid profile lock conflicts
        val userDataDir = withContext(Dispatchers.IO) {
            java.io.File.createTempFile("cp-browser-", "").apply {
                delete()
                mkdirs()
            }
        }
        entry.userDataDir = userDataDir

        Log.info(
            "BrowserSource",
            "Launching headless browser: $browserPath on port $port (userData=$userDataDir)"
        )

        val command = buildBrowserLaunchCommand(
            browserPath, port, userDataDir.absolutePath, page.renderWidth, page.renderHeight,
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

        val cdp = CdpPages.connectCdp(entry, port)
        if (cdp == null) {
            BrowserProcesses.killProcess(process)
            entry.browserProcess = null
            return
        }
        entry.cdpConnection = cdp
        cdp.onUrlChanged = { url -> entry.currentUrl.value = url }
        Log.info("BrowserSource", "WebSocket connected")

        CdpPages.configurePage(cdp, page)
        runCaptureLoop(entry, cdp, page.fps)
    }


    /** Screenshots the page at [fps] into the entry until the coroutine is cancelled. */
    private suspend fun runCaptureLoop(entry: CacheEntry, cdp: CdpConnection, fps: Int) {
        entry.captureIntervalMs = (
            MILLIS_PER_SECOND / fps.coerceIn(MIN_FPS, MAX_FPS)
        ).coerceAtLeast(MIN_STARTUP_CAPTURE_INTERVAL_MS)
        Log.info("BrowserSource", "Starting capture loop at ${fps}fps")

        var frameCount = 0
        while (currentCoroutineContext().isActive) {
            // A frame that will not decode (bad base64, unreadable PNG, a failed bitmap) is skipped.
            try {
                if (CdpPages.captureFrame(entry, cdp, first = frameCount == 0)) frameCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (frameCount == 0) Log.warn("BrowserSource", "Capture error: ${e.message}")
            } catch (e: IllegalArgumentException) {
                if (frameCount == 0) Log.warn("BrowserSource", "Capture error: ${e.message}")
            } catch (e: IllegalStateException) {
                if (frameCount == 0) Log.warn("BrowserSource", "Capture error: ${e.message}")
            }
            delay(entry.captureIntervalMs)
        }
    }






    private fun stopBrowser(entry: CacheEntry) {
        entry.captureJob?.cancel()
        entry.captureJob = null

        try { entry.cdpConnection?.close() } catch (_: Throwable) {}
        entry.cdpConnection = null

        val process = entry.browserProcess
        if (process != null) {
            BrowserProcesses.killProcess(process)
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
                        Log.warn(
                            "BrowserSource",
                            "WebSocket error: ${error::class.simpleName}: ${error.message}"
                        )
                        pending.values.forEach { it.complete(null) }
                    }

                    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                        Log.info("BrowserSource", "WebSocket closed: $statusCode $reason")
                        pending.values.forEach { it.complete(null) }
                        return CompletableFuture.completedFuture(null)
                    }
                }

                ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), listener)
                    .get(WEBSOCKET_CONNECT_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                true
            } catch (e: ExecutionException) {
                connectFailed(e)
            } catch (e: TimeoutException) {
                connectFailed(e)
            } catch (e: InterruptedException) {
                connectFailed(e)
            } catch (e: IllegalArgumentException) {
                // A debugger URL that is not a valid URI.
                connectFailed(e)
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

            if (!send(id, method, msg.toString())) return null

            // Wait for the response, but don't block coroutine cancellation
            return try {
                withContext(Dispatchers.IO) {
                    future.get(CDP_RESPONSE_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                }
            } catch (e: CancellationException) {
                pending.remove(id)
                throw e
            } catch (e: ExecutionException) {
                failed(id, method, "await", e)
            } catch (e: TimeoutException) {
                failed(id, method, "await", e)
            } catch (e: InterruptedException) {
                failed(id, method, "await", e)
            }
        }

        /** Sends [text] as [method]'s request [id]; false, with the reply forgotten, when it could not. */
        private fun send(id: Int, method: String, text: String): Boolean {
            val socket = ws ?: run {
                pending.remove(id)
                Log.warn("BrowserSource", "CDP send '$method': WebSocket is null")
                return false
            }
            return try {
                socket.sendText(text, true)?.get(WEBSOCKET_SEND_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                true
            } catch (e: ExecutionException) {
                sendFailed(id, method, e)
            } catch (e: TimeoutException) {
                sendFailed(id, method, e)
            } catch (e: InterruptedException) {
                sendFailed(id, method, e)
            } catch (e: IllegalStateException) {
                // The socket refuses a send while another is still in flight, or once it has closed.
                sendFailed(id, method, e)
            }
        }

        private fun sendFailed(id: Int, method: String, e: Exception): Boolean {
            failed(id, method, "sendText", e)
            return false
        }

        /** Forgets [id]'s pending reply and logs why the [step] of [method] failed. */
        private fun failed(id: Int, method: String, step: String, e: Exception): JsonObject? {
            pending.remove(id)
            Log.warn("BrowserSource", "CDP $step '$method' failed: ${e::class.simpleName}: ${e.message}")
            return null
        }

        private fun connectFailed(e: Exception): Boolean {
            Log.warn("BrowserSource", "WebSocket connect error: ${e.message}")
            return false
        }

        private fun handleMessage(text: String) {
            when (val message = parseCdpMessage(text)) {
                is CdpMessage.Response -> {
                    if (message.error != null) {
                        Log.warn("BrowserSource", "CDP error for id=${message.id}: ${message.error}")
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
sealed interface CdpMessage {
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
fun parseCdpMessage(text: String): CdpMessage {
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
    } catch (e: IllegalArgumentException) {
        // Not JSON, or not the shape a CDP message has -- serialization errors are this type too.
        Log.warn("BrowserSource", "handleMessage error: ${e.message}")
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
