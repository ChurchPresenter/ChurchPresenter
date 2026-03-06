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
        // Intentionally no-op — calling CefApp.dispose() during shutdown
        // triggers a native crash in libjcef Context::Shutdown().
        // The JVM process exit handles cleanup safely.
    }

    /**
     * Routes all audio streams from this JVM process to the given PulseAudio/PipeWire sink.
     * Finds sink inputs belonging to our PID and moves them to the target device.
     */
    fun routeAudioToDevice(deviceId: String) {
        if (deviceId.isBlank()) return
        try {
            val pid = ProcessHandle.current().pid()
            val proc = ProcessBuilder("pactl", "list", "sink-inputs")
                .redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            val indices = mutableListOf<String>()
            var currentIndex: String? = null
            var isOurProcess = false
            for (line in output.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Sink Input #")) {
                    if (isOurProcess && currentIndex != null) indices.add(currentIndex)
                    currentIndex = trimmed.removePrefix("Sink Input #").trim()
                    isOurProcess = false
                }
                if (trimmed.contains("application.process.id") && trimmed.contains("\"$pid\"")) {
                    isOurProcess = true
                }
            }
            if (isOurProcess && currentIndex != null) indices.add(currentIndex)

            for (idx in indices) {
                ProcessBuilder("pactl", "move-sink-input", idx, deviceId)
                    .redirectErrorStream(true).start().waitFor()
                println("Routed audio stream $idx to sink $deviceId")
            }
        } catch (e: Exception) {
            System.err.println("Failed to route audio: ${e.message}")
        }
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
 * [onSnapshot]  — called periodically with an [ImageBitmap] screen capture.
 * [navController] — optional controller for back/forward navigation.
 */
@Composable
fun EmbeddedWebView(
    url: String,
    modifier: Modifier = Modifier,
    onUrlChanged: ((String) -> Unit)? = null,
    onTitleChanged: ((String) -> Unit)? = null,
    onSnapshot: ((ImageBitmap) -> Unit)? = null,
    navController: WebNavController? = null,
    onBrowserCreated: ((CefBrowser) -> Unit)? = null
) {
    if (url.isBlank() || !CefManager.initialized) return

    val client = remember { CefManager.createClient() } ?: return
    val initialUrl = remember { url }
    val browser = remember { client.createBrowser(initialUrl, false, false) }

    LaunchedEffect(browser) {
        navController?.browser = browser
        onBrowserCreated?.invoke(browser)
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

        // Snapshot timer — captures browser via Robot screen capture
        val robot = try { java.awt.Robot() } catch (_: Exception) { null }
        val timer = if (onSnapshot != null && robot != null) {
            javax.swing.Timer(150) {
                try {
                    val comp = browser.getUIComponent()
                    if (comp.width > 0 && comp.height > 0 && comp.isShowing) {
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
            client.removeLifeSpanHandler()
            browser.close(true)
            client.dispose()
        }
    }

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

/** Full-screen presenter variant that captures snapshots for the live preview. */
@Composable
fun WebsitePresenter(
    url: String,
    modifier: Modifier = Modifier,
    onSnapshot: ((ImageBitmap) -> Unit)? = null,
    onBrowserCreated: ((CefBrowser) -> Unit)? = null,
    audioDeviceId: String = ""
) {
    // Periodically route CEF audio streams to the configured device.
    // New streams may appear as the user navigates to pages with audio/video.
    LaunchedEffect(audioDeviceId) {
        if (audioDeviceId.isNotBlank()) {
            kotlinx.coroutines.delay(2000) // Wait for initial audio streams
            while (true) {
                CefManager.routeAudioToDevice(audioDeviceId)
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    EmbeddedWebView(
        url = url,
        modifier = modifier.fillMaxSize(),
        onSnapshot = onSnapshot,
        onBrowserCreated = onBrowserCreated
    )
}
