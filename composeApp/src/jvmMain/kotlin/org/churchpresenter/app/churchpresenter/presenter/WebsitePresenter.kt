package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.io.File

/**
 * Manages a single CefApp instance for the entire application.
 * Must call [init] once at startup before any WebView is used.
 */
object CefManager {
    private var cefApp: CefApp? = null
    var initialized = false
        private set

    fun init() {
        if (initialized) return
        try {
            val installDir = File(System.getProperty("user.home"), ".churchpresenter/jcef")
            installDir.mkdirs()
            val cacheDir = File(System.getProperty("user.home"), ".churchpresenter/webview-cache")
            cacheDir.mkdirs()

            val builder = CefAppBuilder()
            builder.setInstallDir(installDir)
            builder.setProgressHandler(ConsoleProgressHandler())
            builder.setAppHandler(object : MavenCefAppHandlerAdapter() {})
            builder.cefSettings.windowless_rendering_enabled = false
            builder.cefSettings.cache_path = cacheDir.absolutePath
            // Fallback to software rendering on VMs / systems without proper GPU drivers
            val dmiTexts = listOf("product_name", "sys_vendor").mapNotNull { file ->
                runCatching { File("/sys/class/dmi/id/$file").readText().trim() }.getOrNull()
            }
            val vmKeywords = listOf("Virtual", "QEMU", "VMware", "VirtualBox", "KVM", "Xen", "Hyper-V", "Standard PC")
            val isVirtualized = dmiTexts.any { text ->
                vmKeywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
            }
            if (isVirtualized) {
                builder.addJcefArgs("--disable-gpu")
                builder.addJcefArgs("--disable-gpu-compositing")
                builder.addJcefArgs("--enable-unsafe-swiftshader")
            }

            cefApp = builder.build()
            initialized = true
            println("JCEF initialized successfully")
        } catch (e: Exception) {
            System.err.println("Failed to initialize JCEF: ${e.message}")
            e.printStackTrace()
        }
    }

    fun createClient(): CefClient? = cefApp?.createClient()

    fun dispose() {
        cefApp?.dispose()
    }
}

/** Navigation controller for an [EmbeddedWebView]. */
class WebNavController {
    internal var browser: CefBrowser? = null
    fun goBack() { browser?.goBack() }
    fun goForward() { browser?.goForward() }
    fun canGoBack(): Boolean = browser?.canGoBack() ?: false
    fun canGoForward(): Boolean = browser?.canGoForward() ?: false
}

@Composable
fun rememberWebNavController() = remember { WebNavController() }

/**
 * Embeds a Chromium browser (via JCEF) in a Compose SwingPanel.
 *
 * [onUrlChanged] — called whenever the page URL changes (main frame only).
 * [onTitleChanged] — called whenever the page title changes.
 * [onSnapshot]  — called every ~200 ms with an [ImageBitmap] of the current
 *                 browser frame, so other Compose surfaces can mirror it.
 *                 Pass null to disable snapshotting (saves CPU).
 * [navController] — optional controller for back/forward navigation.
 * [targetViewportWidth] — if > 0, zooms out so the browser renders at this
 *                          viewport width, producing a scaled-down preview that
 *                          matches the presenter's full-screen layout.
 */
@Composable
fun EmbeddedWebView(
    url: String,
    modifier: Modifier = Modifier,
    onUrlChanged: ((String) -> Unit)? = null,
    onTitleChanged: ((String) -> Unit)? = null,
    onSnapshot: ((ImageBitmap) -> Unit)? = null,
    navController: WebNavController? = null,
    targetViewportWidth: Int = 0
) {
    if (url.isBlank() || !CefManager.initialized) return

    val client = remember { CefManager.createClient() } ?: return
    // Load the initial URL directly to avoid an extra about:blank round-trip
    val initialUrl = remember { url }
    val browser = remember { client.createBrowser(initialUrl, false, false) }

    // Wire up the nav controller
    LaunchedEffect(browser) { navController?.browser = browser }

    // Inject CSS zoom so the browser viewport matches the presenter screen width.
    // We use JavaScript to set document.documentElement.style.zoom after each page
    // load and whenever the component resizes, producing a scaled-down preview that
    // matches the full-screen presenter layout.
    fun applyViewportZoom() {
        if (targetViewportWidth <= 0) return
        val comp = browser.getUIComponent()
        val actualWidth = comp.width
        if (actualWidth > 0 && actualWidth < targetViewportWidth) {
            val scale = actualWidth.toDouble() / targetViewportWidth.toDouble()
            browser.executeJavaScript(
                "document.documentElement.style.zoom='${scale}';",
                "", 0
            )
        }
    }

    if (targetViewportWidth > 0) {
        DisposableEffect(targetViewportWidth) {
            val comp = browser.getUIComponent()
            val resizeListener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    applyViewportZoom()
                }
            }
            comp.addComponentListener(resizeListener)
            onDispose { comp.removeComponentListener(resizeListener) }
        }
    }

    DisposableEffect(Unit) {
        val displayHandler = object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                if (frame.isMain) onUrlChanged?.invoke(url)
            }
            override fun onTitleChange(browser: CefBrowser, title: String) {
                onTitleChanged?.invoke(title)
            }
        }
        client.addDisplayHandler(displayHandler)

        // Re-apply CSS zoom after each page finishes loading
        val loadHandler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) applyViewportZoom()
            }
        }
        client.addLoadHandler(loadHandler)

        // Intercept popups (target="_blank" links) — load in current browser instead
        val lifeSpanHandler = object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser, frame: CefFrame, targetUrl: String?, targetFrameName: String?
            ): Boolean {
                if (!targetUrl.isNullOrBlank()) {
                    browser.loadURL(targetUrl)
                }
                return true // cancel the popup
            }
        }
        client.addLifeSpanHandler(lifeSpanHandler)

        // Snapshot timer — captures the browser via Robot screen capture every 500 ms
        val robot = try { java.awt.Robot() } catch (_: Exception) { null }
        val timer = if (onSnapshot != null && robot != null) {
            javax.swing.Timer(500) {
                try {
                    val comp = browser.getUIComponent()
                    if (comp.isShowing && comp.width > 0 && comp.height > 0) {
                        val loc = comp.locationOnScreen
                        val rect = java.awt.Rectangle(loc.x, loc.y, comp.width, comp.height)
                        val img = robot.createScreenCapture(rect)
                        onSnapshot(img.toComposeImageBitmap())
                    }
                } catch (_: Exception) {}
            }.also { it.start() }
        } else null

        onDispose {
            timer?.stop()
            navController?.browser = null
            client.removeDisplayHandler()
            client.removeLoadHandler()
            client.removeLifeSpanHandler()
            browser.close(true)
            client.dispose()
        }
    }

    // Navigate when URL changes (skip the initial URL — already loaded by createBrowser)
    LaunchedEffect(url) {
        if (url != initialUrl) {
            browser.loadURL(url)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { browser.getUIComponent() }
        )
    }
}

/** Full-screen presenter variant — no callbacks needed. */
@Composable
fun WebsitePresenter(
    url: String,
    modifier: Modifier = Modifier,
    targetViewportWidth: Int = 0
) {
    EmbeddedWebView(url = url, modifier = modifier.fillMaxSize(), targetViewportWidth = targetViewportWidth)
}
