package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.swing.Timer
import kotlinx.coroutines.delay
import java.lang.invoke.MethodHandles

private const val ASCII_MAX = 128

/**
 * Free space JCEF needs to unpack, with room to spare — the download is a bundled Chromium and runs
 * to a couple of hundred megabytes.
 */
private const val JCEF_REQUIRED_BYTES = 400L * 1024 * 1024
private const val AUDIO_INIT_DELAY_MS = 2000L
private const val AUDIO_RETRY_DELAY_MS = 5000L

/**
 * Manages a single CefApp instance for the entire application.
 * Must call [init] once at startup before any WebView is used.
 */
object CefManager {
    private var cefApp: CefApp? = null

    /**
     * Whether a usable [CefApp] exists right now.
     *
     * Compose-backed rather than a plain `var` because it can go from true to false mid-session:
     * [createClient] clears it when the native side turns out to be dead, and the tab's
     * "web engine unavailable" panel is only reached if that write recomposes its reader.
     */
    var initialized by mutableStateOf(false)
        private set

    /** True when [init] was skipped because the running macOS version is below [MIN_MACOS_MAJOR]. */
    var macOsUnsupported = false
        private set

    /**
     * Chromium 139+ (bundled here as CEF 143, see build.gradle.kts) dropped support for
     * macOS 11 (Big Sur) — Chrome 138 was the last version to run there. CefApp.startup()
     * fails on unsupported macOS versions with no exception cause (just returns false deep in
     * native code), so detect this ahead of time instead of attempting init and reporting a
     * misleading "JCef did not initialize correctly!" crash to Sentry for a known, unfixable case.
     */
    private const val MIN_MACOS_MAJOR = 12

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

    /**
     * Why a JCEF install cannot be attempted into a directory in this state, or null to go ahead.
     *
     * Both answers are the machine: a directory the user cannot write to (ProgramData ACLs vary,
     * and a locked-down install is a normal corporate build), and a disk without room for a
     * bundled Chromium. Neither is a defect and neither becomes an event; what they do is stop the
     * app spending a download to discover it, and leave a tag saying which one it was.
     *
     * Zero is not "full": `File.usableSpace` answers 0 when it cannot determine the figure at all,
     * so treating it as no space would block the install on every machine whose filesystem does not
     * report one. An unknown figure goes ahead and lets the real attempt decide.
     */
    internal fun jcefInstallBlocker(writable: Boolean, usableSpaceBytes: Long): String? = when {
        !writable -> "permission_denied"
        usableSpaceBytes in 1 until JCEF_REQUIRED_BYTES -> "disk_space"
        else -> null
    }

    fun init() {
        if (initialized) return
        if (isUnsupportedMacOS()) {
            macOsUnsupported = true
            return
        }
        // Must run before any JCEF class is loaded — CefBrowserWindowMac.getWindowHandle()
        // directly references sun.awt.AWTAccessor which the JVM module system blocks by default.
        patchJcefModuleAccess()
        val root = jcefRootDir()
        val installDir = File(root, "jcef")
        try {
            installDir.mkdirs()

            // Two of the ways this fails are the machine rather than the app, and both are knowable
            // before the download starts. Asking beforehand keeps them out of the crash reports and
            // is locale-independent — the exceptions they raise say "Access is denied" and "There is
            // not enough space on the disk" only on an English Windows, so classifying them after
            // the fact would work in one language.
            val blocker = jcefInstallBlocker(installDir.canWrite(), installDir.usableSpace)
            if (blocker != null) {
                System.err.println("[JCEF] Not installing to ${installDir.path}: $blocker")
                cefApp = null
                initialized = false
                // No event: web features simply stay unavailable, which jcef.available already
                // says. The tag rides along on anything else this session reports.
                runCatching { CrashReporter.setTag("jcef.blocked", blocker) }
                return
            }

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

    /**
     * A client for a new browser, or null when the web engine cannot provide one.
     *
     * [CefAppBuilder.build] returning normally is not a promise that the native side came up:
     * jcefmaven hands back the process-wide singleton, whose startup can fail afterwards and
     * asynchronously, leaving it in `INITIALIZATION_FAILED`. `CefApp.createClient()` then throws —
     * and the only caller is [EmbeddedWebView], inside a `remember`, so the throw landed in
     * composition on the UI thread and took the whole app down.
     *
     * Recovering is simply returning null, which every caller already handles by drawing nothing.
     * [initialized] is cleared with it so the tab falls back to its explanatory panel and nothing
     * asks a second time. Filed as a warning, not an exception: the condition is recovered, and an
     * exception here would write a `crash-reports/` file for a session that carries on.
     */
    fun createClient(): CefClient? {
        val app = cefApp ?: return null
        return try {
            app.createClient()
        } catch (t: Throwable) {
            cefApp = null
            initialized = false
            runCatching { CrashReporter.setTag("jcef.blocked", "client_creation_failed") }
            CrashReporter.reportWarning(
                "JCEF client creation failed; web features disabled for this session",
                throwable = t,
                tags = mapOf("subsystem" to "webview", "jcef.recovered" to "true"),
            )
            null
        }
    }

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
    // createBrowser asks JCEF for the global CefRequestContext, which answers null once the native
    // side is down — and the caller then reads a field off it, so this arrived as
    // `NullPointerException: Cannot read field "N_CefHandle" because "result" is null`, thrown in
    // composition. There is no state to ask beforehand (CefApp's own state is not exposed here),
    // so the attempt is the check; drawing nothing is what every other unavailable path does.
    val browser = remember { runCatching { client.createBrowser(initialUrl, false, false) }.getOrNull() }
        ?: return

    LaunchedEffect(browser) {
        navController?.browser = browser
        onBrowserCreated?.invoke(browser)
    }

    DisposableEffect(Unit) {
        val displayHandler = object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                handleAddressChange(frame, url, onUrlChanged)
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
                handlePopupTarget(browser, targetUrl)
                return true // cancel the popup
            }
        }
        client.addLifeSpanHandler(lifeSpanHandler)

        // Override User-Agent for mobile emulation
        val requestHandler = object : CefRequestHandlerAdapter() {
            override fun getResourceRequestHandler(
                browser: CefBrowser?, frame: CefFrame?, request: CefRequest?,
                isNavigation: Boolean, isDownload: Boolean, requestInitiator: String?,
                disableDefaultHandling: BoolRef?
            ): CefResourceRequestHandler {
                return object : CefResourceRequestHandlerAdapter() {
                    override fun onBeforeResourceLoad(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?): Boolean {
                        applyMobileUserAgent(navController?.mobileMode == true, request)
                        return false
                    }
                }
            }
        }
        client.addRequestHandler(requestHandler)

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
            // JCEF tears its native context down from its own shutdown hook, which races composable
            // disposal on the way out of the app: both of these then throw
            // `IllegalStateException: CefApp was terminated` out of onDispose. There is nothing left
            // to release at that point — the process is going away — so failing to release it is not
            // a fault, and letting the throw escape disposal is.
            runCatching { browser.close(true) }
            runCatching { client.dispose() }
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
