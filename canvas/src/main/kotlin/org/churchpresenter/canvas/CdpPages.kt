package org.churchpresenter.canvas

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.churchpresenter.diagnostics.CrashReporter
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO
import org.churchpresenter.diagnostics.Log

/** One browser source's page over DevTools: connecting to it, setting it up, and screenshotting it. */
internal object CdpPages {
    /** The CDP connection to the freshly launched browser, or null once the failure is reported. */
    internal suspend fun connectCdp(
        entry: SharedBrowserFrameCache.CacheEntry,
        port: Int,
        readyTimeoutMs: Long = CDP_READY_TIMEOUT_MS,
    ): SharedBrowserFrameCache.CdpConnection? {
        if (!BrowserProcesses.waitForCdpReady(port, timeoutMs = readyTimeoutMs)) {
            Log.warn("BrowserSource", "CDP did not become ready in time")
            CrashReporter.reportWarning(
                "BrowserSource: CDP did not become ready in time",
                tags = mapOf("subsystem" to "browser-source")
            )
            entry.error.value = "Browser failed to start"
            return null
        }
        Log.info("BrowserSource", "CDP ready on port $port")
        return openCdpWebSocket(port)
    }

    private suspend fun openCdpWebSocket(port: Int): SharedBrowserFrameCache.CdpConnection? {
        val wsUrl = withContext(Dispatchers.IO) { BrowserProcesses.getPageWebSocketUrl(port) }
        if (wsUrl == null) {
            Log.warn("BrowserSource", "Could not get page WebSocket URL")
            CrashReporter.reportWarning(
                "BrowserSource: Could not get page WebSocket URL",
                tags = mapOf("subsystem" to "browser-source")
            )
            return null
        }
        Log.info("BrowserSource", "Connecting WebSocket: $wsUrl")
        val cdp = SharedBrowserFrameCache.CdpConnection()
        val connected = withContext(Dispatchers.IO) { cdp.connect(wsUrl) }
        if (!connected) {
            Log.warn("BrowserSource", "WebSocket connection failed")
            CrashReporter.reportWarning(
                "BrowserSource: WebSocket connection to CDP failed",
                tags = mapOf("subsystem" to "browser-source")
            )
            return null
        }
        return cdp
    }

    /** Viewport, transparency and navigation for a freshly connected page. */
    internal suspend fun configurePage(
        cdp: SharedBrowserFrameCache.CdpConnection,
        page: SharedBrowserFrameCache.BrowserPage,
        settleMs: Long = PAGE_LOAD_SETTLE_MS,
    ) {
        val url = page.url
        val renderWidth = page.renderWidth
        val renderHeight = page.renderHeight
        val customCss = page.customCss
        val forceTransparent = page.forceTransparent
        // Configure viewport and transparency
        var resp = cdp.sendAsync("Emulation.setDeviceMetricsOverride", buildJsonObject {
            put("width", renderWidth)
            put("height", renderHeight)
            put("deviceScaleFactor", 1)
            put("mobile", false)
        })
        Log.info("BrowserSource", "setDeviceMetricsOverride: $resp")

        if (forceTransparent) {
            resp = cdp.sendAsync("Emulation.setDefaultBackgroundColorOverride", buildJsonObject {
                put("color", buildJsonObject {
                    put("r", 0)
                    put("g", 0)
                    put("b", 0)
                    put("a", 0)
                })
            })
            Log.info("BrowserSource", "setDefaultBackgroundColorOverride: $resp")
        }

        cdp.sendAsync("Page.enable", null)

        // Navigate to the URL
        if (url.isNotBlank()) {
            resp = cdp.sendAsync("Page.navigate", buildJsonObject { put("url", url) })
            Log.info("BrowserSource", "Page.navigate($url): $resp")

            // Wait for page to load
            delay(settleMs)

            // Inject transparency CSS
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
        }
    }

    /** Screenshots the page once into [entry]; false when nothing decodable came back. */
    internal suspend fun captureFrame(
        entry: SharedBrowserFrameCache.CacheEntry,
        cdp: SharedBrowserFrameCache.CdpConnection,
        first: Boolean,
    ): Boolean {
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
                Log.warn("BrowserSource", "ImageIO.read returned null (${pngBytes.size} bytes)")
            }
            return false
        }
        entry.frame.value = img.toComposeImageBitmap()
        if (first) {
            Log.info("BrowserSource", "First frame captured: ${img.width}x${img.height}")
        }
        return true
    }

    private fun reportMissingScreenshot(response: JsonObject?) {
        if (response == null) {
            Log.warn("BrowserSource", "captureScreenshot returned null")
        } else {
            Log.warn("BrowserSource", "captureScreenshot response has no 'data': ${response.keys}")
        }
    }
}

private const val PAGE_LOAD_SETTLE_MS = 3000L

/** How long a freshly launched browser has to answer on its debug port. */
private const val CDP_READY_TIMEOUT_MS = 15_000L
