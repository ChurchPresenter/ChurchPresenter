package org.churchpresenter.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.utils.Constants
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.network.CefRequest
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.swing.Timer
import kotlinx.coroutines.delay
import java.lang.invoke.MethodHandles

private const val ASCII_MAX = 128
private const val AUDIO_INIT_DELAY_MS = 2000L
private const val AUDIO_RETRY_DELAY_MS = 5000L

/**
 * Manages a single CefApp instance for the entire application.
 * Must call [init] once at startup before any WebView is used.
 */
object CefManager {
    private var cefApp: CefApp? = null
    var initialized = false
        private set

    /** True when [init] was skipped because the running macOS version is below [MIN_MACOS_MAJOR]. */
    var macOsUnsupported = false
        private set

    /** True when [init] was skipped because the running Windows version is below [MIN_WINDOWS_MAJOR]. */
    var windowsUnsupported = false
        private set

    /**
     * Chromium 139+ (bundled here as CEF 143, see build.gradle.kts) dropped support for
     * macOS 11 (Big Sur) — Chrome 138 was the last version to run there. CefApp.startup()
     * fails on unsupported macOS versions with no exception cause (just returns false deep in
     * native code), so detect this ahead of time instead of attempting init and reporting a
     * misleading "JCef did not initialize correctly!" crash to Sentry for a known, unfixable case.
     */
    private const val MIN_MACOS_MAJOR = 12

    /**
     * The same wall on the other platform: Chromium dropped Windows 7/8/8.1 at version 110, and
     * the CEF 143 bundled here loads `chrome_elf.dll` against APIs those releases do not export.
     * The failure is an `UnsatisfiedLinkError` reading "The specified procedure could not be
     * found" — caught below, so the app survives, but reported to Sentry as if a broken install
     * were to blame. `os.version` is 6.1 on Windows 7, 6.3 on 8.1 and 10.0 on both 10 and 11.
     */
    private const val MIN_WINDOWS_MAJOR = 10

    internal fun isUnsupportedWindows(
        osName: String = System.getProperty("os.name", ""),
        osVersion: String = System.getProperty("os.version", "")
    ): Boolean {
        if (!osName.lowercase().contains("win")) return false
        val major = osVersion.substringBefore('.').toIntOrNull() ?: return false
        return major < MIN_WINDOWS_MAJOR
    }

    internal fun isUnsupportedMacOS(
        osName: String = System.getProperty("os.name", ""),
        osVersion: String = System.getProperty("os.version", "")
    ): Boolean {
        if (!osName.lowercase().contains("mac")) return false
        val major = osVersion.substringBefore('.').toIntOrNull() ?: return false
        return major < MIN_MACOS_MAJOR
    }

    /**
     * Programmatically export internal java.desktop packages required by JCEF on macOS.
     *
     * CefBrowserWindowMac.getWindowHandle() directly references sun.awt.AWTAccessor in
     * compiled bytecode.  The JVM module system blocks this unless java.desktop exports
     * sun.awt to the unnamed module (equivalent to --add-exports=java.desktop/sun.awt=ALL-UNNAMED).
     *
     * Two approaches are tried in order:
     *
     * 1. setAccessible path — fast, requires --add-opens=java.base/java.lang=ALL-UNNAMED
     *    (supplied by tasks.withType<JavaExec> in build.gradle.kts when running via Gradle).
     *
     * 2. Trusted IMPL_LOOKUP path — works with ZERO JVM flags.
     *    MethodHandles.Lookup.IMPL_LOOKUP is the JDK's own fully-trusted lookup (used
     *    internally by java.lang.invoke).  Reading it via sun.misc.Unsafe bypasses all
     *    Java access checks, yielding a MethodHandle that can invoke any method in any
     *    module — including Module.implAddExportsToAllUnnamed.
     *    This is the same technique used by ByteBuddy and Mockito for JDK 9–21.
     */
    internal fun patchJcefModuleAccess() {
        val packages = listOf("sun.awt", "sun.lwawt", "sun.lwawt.macosx")
        val javaDesktop = ModuleLayer.boot().findModule("java.desktop").orElse(null) ?: return

        // --- Approach 1: plain setAccessible ---
        runCatching {
            val m = Module::class.java
                .getDeclaredMethod("implAddExportsToAllUnnamed", String::class.java)
            m.isAccessible = true
            packages.forEach { m.invoke(javaDesktop, it) }
        }.onSuccess { return }

        // --- Approach 2: IMPL_LOOKUP via sun.misc.Unsafe ---
        runCatching {
            // jdk.unsupported opens sun.misc → isAccessible=true works on theUnsafe
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe

            // Read IMPL_LOOKUP directly from memory — no Java access check involved.
            // MethodHandles.lookup().javaClass gives us java.lang.invoke.MethodHandles$Lookup.
            val implLookupField = MethodHandles.lookup().javaClass
                .getDeclaredField("IMPL_LOOKUP")
            @Suppress("DEPRECATION")
            val trustedLookup = unsafe.getObject(
                unsafe.staticFieldBase(implLookupField),
                unsafe.staticFieldOffset(implLookupField)
            ) as MethodHandles.Lookup

            // IMPL_LOOKUP is fully trusted — findVirtual succeeds on package-private methods
            val mt = java.lang.invoke.MethodType.methodType(Void.TYPE, String::class.java)
            val mh = trustedLookup.findVirtual(
                Module::class.java, "implAddExportsToAllUnnamed", mt
            )
            packages.forEach { pkg -> mh.invokeWithArguments(javaDesktop, pkg) }
        }.onSuccess { return }
    }

    /**
     * Root directory for JCEF's native install + web cache.
     *
     * On Windows, prefer an ASCII-safe, username-free path under %ProgramData%
     * (e.g. C:\ProgramData\ChurchPresenter) — a home directory containing non-ASCII
     * characters is a suspected trigger for native-load failures, and a fresh
     * extraction here also clears any partially-corrupted install. Fall back to
     * ~/.churchpresenter when ProgramData is missing or not writable.
     *
     * On macOS/Linux the home directory is used unchanged — no accent problem, no ProgramData.
     */
    internal fun jcefRootDir(
        osName: String = System.getProperty("os.name", ""),
        programData: String? = System.getenv("ProgramData"),
        homeDir: String = System.getProperty("user.home")
    ): File {
        val isWindows = osName.lowercase().contains("win")
        if (isWindows) {
            val candidate = File(programData ?: "C:\\ProgramData", "ChurchPresenter")
            // Only use it if we can actually create and write to it (ProgramData ACLs vary).
            val usable = runCatching { candidate.mkdirs(); candidate.isDirectory && candidate.canWrite() }
                .getOrDefault(false)
            if (usable) return candidate
        }
        return File(homeDir, ".churchpresenter")
    }

    /**
     * One-time cleanup of the legacy JCEF footprint left under ~/.churchpresenter after a
     * Windows user is relocated to %ProgramData%. Only runs when [activeRoot] is NOT the home
     * directory — i.e. we actually moved. If ProgramData was unwritable and we fell back to the
     * home dir, that location is live and must be left untouched. Deletes only the jcef and
     * webview-cache subdirs (never the parent, which holds settings/song cache/crash reports).
     * Best-effort on a daemon thread: ~100 MB recursive delete shouldn't block startup, and
     * locked files are simply skipped and retried on a future launch.
     */
    internal fun cleanupLegacyJcef(activeRoot: File, homeDir: String = System.getProperty("user.home")) {
        val homeRoot = File(homeDir, ".churchpresenter")
        if (activeRoot.absolutePath == homeRoot.absolutePath) return
        val legacyDirs = listOf(File(homeRoot, "jcef"), File(homeRoot, "webview-cache"))
        if (legacyDirs.none { it.isDirectory }) return
        Thread {
            legacyDirs.forEach { dir ->
                runCatching { if (dir.isDirectory) dir.deleteRecursively() }
            }
        }.apply { isDaemon = true; name = "jcef-legacy-cleanup" }.start()
    }

    fun init() {
        if (initialized) return
        if (isUnsupportedMacOS()) {
            macOsUnsupported = true
            return
        }
        if (isUnsupportedWindows()) {
            windowsUnsupported = true
            return
        }
        // Must run before any JCEF class is loaded — CefBrowserWindowMac.getWindowHandle()
        // directly references sun.awt.AWTAccessor which the JVM module system blocks by default.
        patchJcefModuleAccess()
        val root = jcefRootDir()
        val installDir = File(root, "jcef")
        try {
            installDir.mkdirs()
            val cacheDir = File(root, "webview-cache")
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
            if (isVirtualizedEnvironment(dmiTexts)) {
                builder.addJcefArgs("--disable-gpu")
                builder.addJcefArgs("--disable-gpu-compositing")
                builder.addJcefArgs("--enable-unsafe-swiftshader")
            }

            cefApp = builder.build()
            initialized = true
            // Now that the relocated install works, reclaim the orphaned old footprint.
            cleanupLegacyJcef(root)
        } catch (t: Throwable) {
            // JCEF native load can fail with UnsatisfiedLinkError (an Error, not an
            // Exception) — e.g. a broken/partial chrome_elf.dll install, a missing VC++
            // runtime, or a non-ASCII install path. Catch Throwable so the whole app
            // does not crash at startup; embedded web features simply stay unavailable.
            cefApp = null
            initialized = false
            // Best-effort telemetry: distinguish the accented-path theory from the
            // VC++-runtime theory at a glance in Sentry. Never let telemetry throw.
            runCatching {
                CrashReporter.setTag("jcef.path_ascii", installDir.path.all { it.code < ASCII_MAX }.toString())
                CrashReporter.setContext("jcef", mapOf(
                    "installDir" to installDir.path,
                    "os" to (System.getProperty("os.name") ?: ""),
                    "arch" to (System.getProperty("os.arch") ?: "")
                ))
            }
            CrashReporter.reportException(t, context = "CefManager.init")
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

            val indices = sinkInputIndicesForProcess(output, pid)

            for (idx in indices) {
                ProcessBuilder("pactl", "move-sink-input", idx, deviceId)
                    .redirectErrorStream(true).start().waitFor()
            }
        } catch (_: Exception) {
        }
    }
}

private val VM_DMI_KEYWORDS = listOf("Virtual", "QEMU", "VMware", "VirtualBox", "KVM", "Xen", "Hyper-V", "Standard PC")

/** True when any of [dmiTexts] (`/sys/class/dmi/id/product_name`, `sys_vendor`) names a known VM/hypervisor. */
internal fun isVirtualizedEnvironment(dmiTexts: List<String>): Boolean =
    dmiTexts.any { text -> VM_DMI_KEYWORDS.any { keyword -> text.contains(keyword, ignoreCase = true) } }

/** Parses `pactl list sink-inputs` output for the sink input indexes belonging to [pid]. */
internal fun sinkInputIndicesForProcess(pactlOutput: String, pid: Long): List<String> {
    val indices = mutableListOf<String>()
    var currentIndex: String? = null
    var isOurProcess = false
    for (line in pactlOutput.lines()) {
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
    return indices
}

/** Navigation controller for an [EmbeddedWebView]. */
class WebNavController {
    internal var browser: CefBrowser? = null
    var mobileMode: Boolean = false
    fun goBack() { browser?.goBack() }
    fun goForward() { browser?.goForward() }
    fun canGoBack(): Boolean = browser?.canGoBack() ?: false
    fun canGoForward(): Boolean = browser?.canGoForward() ?: false

    fun setMobileEmulation(enabled: Boolean) {
        mobileMode = enabled
        browser?.reload()
    }

    companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    }
}

@Composable
fun rememberWebNavController() = remember { WebNavController() }

/** Iframe navigations must not overwrite the address bar / title with a sub-frame's URL. */
internal fun handleAddressChange(frame: CefFrame, url: String, onUrlChanged: ((String) -> Unit)?) {
    if (frame.isMain) onUrlChanged?.invoke(url)
}

/** A blank/null target (e.g. `window.open()` with no URL yet) has nothing to navigate to. */
internal fun handlePopupTarget(browser: CefBrowser, targetUrl: String?) {
    if (!targetUrl.isNullOrBlank()) {
        browser.loadURL(targetUrl)
    }
}

/** Only override the User-Agent while mobile emulation is on; a real page load has no request to tag. */
internal fun applyMobileUserAgent(mobileModeEnabled: Boolean, request: CefRequest?) {
    if (mobileModeEnabled && request != null) {
        request.setHeaderByName("User-Agent", WebNavController.MOBILE_USER_AGENT, true)
    }
}

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
        client.addDisplayHandler(displayHandler(onUrlChanged, onTitleChanged))
        // Intercept popups (target="_blank" links) — load in current browser instead
        client.addLifeSpanHandler(popupCancellingHandler())
        // Override User-Agent for mobile emulation
        client.addRequestHandler(mobileUserAgentHandler { navController?.mobileMode == true })

        // Snapshot timer — captures browser via Robot screen capture
        val robot = try { Robot() } catch (_: Exception) { null }
        val timer = if (onSnapshot != null && robot != null) {
            Timer(150) {
                try {
                    val comp = browser.getUIComponent()
                    if (comp.width > 0 && comp.height > 0 && comp.isShowing) {
                        val loc = comp.locationOnScreen
                        val rect = Rectangle(loc.x, loc.y, comp.width, comp.height)
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
            client.removeRequestHandler()
            // Hide and detach the heavyweight AWT component before closing —
            // on macOS, JCEF's native Canvas stays visible over Compose layers otherwise
            try {
                val comp = browser.getUIComponent()
                comp.isVisible = false
                comp.parent?.remove(comp)
            } catch (_: Exception) {}
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
    onUrlChanged: ((String) -> Unit)? = null,
    onTitleChanged: ((String) -> Unit)? = null,
    audioDeviceId: String = "",
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL
) {
    // Key mode: solid white frame (mixer sees "fully visible")
    if (outputRole == Constants.OUTPUT_ROLE_KEY) {
        Box(modifier = modifier.fillMaxSize().background(Color.White))
        return
    }
    // Periodically route CEF audio streams to the configured device.
    // New streams may appear as the user navigates to pages with audio/video.
    LaunchedEffect(audioDeviceId) {
        if (audioDeviceId.isNotBlank()) {
            delay(AUDIO_INIT_DELAY_MS) // Wait for initial audio streams
            while (true) {
                CefManager.routeAudioToDevice(audioDeviceId)
                delay(AUDIO_RETRY_DELAY_MS)
            }
        }
    }

    EmbeddedWebView(
        url = url,
        modifier = modifier.fillMaxSize(),
        onSnapshot = onSnapshot,
        onBrowserCreated = onBrowserCreated,
        onUrlChanged = onUrlChanged,
        onTitleChanged = onTitleChanged
    )
}
