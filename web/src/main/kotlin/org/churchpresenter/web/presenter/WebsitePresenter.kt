package org.churchpresenter.web.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import org.churchpresenter.sharedui.composables.LocalOutputCursorHidden
import org.churchpresenter.sharedui.composables.outputCursorScript
import org.churchpresenter.settings.utils.Constants
import androidx.compose.ui.graphics.ImageBitmap
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.network.CefRequest
import java.awt.Component
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.Timer
import kotlinx.coroutines.delay
import java.lang.invoke.MethodHandles

/**
 * Free space JCEF needs to unpack, with room to spare — the download is a bundled Chromium and runs
 * to a couple of hundred megabytes.
 */
private const val JCEF_REQUIRED_BYTES = 400L * 1024 * 1024
private const val AUDIO_INIT_DELAY_MS = 2000L
private const val SNAPSHOT_INTERVAL_MS = 150
private const val AUDIO_RETRY_DELAY_MS = 5000L

/**
 * Choosing where JCEF's native install goes, and getting it there.
 *
 * Its own object rather than more members on [CefManager]: picking a directory, probing it, and
 * moving on to the next one when an install fails is one job with one reason to change — and it is
 * the half of the engine's startup that can be exercised without a browser.
 */
internal object JcefInstall {

    /**
     * Root directories for JCEF's native install + web cache, best first.
     *
     * On Windows, prefer an ASCII-safe, username-free path under %ProgramData%
     * (e.g. C:\ProgramData\ChurchPresenter) — a home directory containing non-ASCII characters is
     * a suspected trigger for native-load failures, and a fresh extraction here also clears any
     * partially-corrupted install. ~/.churchpresenter follows it, because ProgramData is shared
     * between accounts: a directory this user can create is not necessarily one this user can
     * extract into when another account got there first, and that is only knowable by trying.
     *
     * On macOS/Linux the home directory is the only candidate — no accent problem, no ProgramData.
     *
     * A list rather than one chosen path because the choice cannot be made up front. The old form
     * probed `canWrite()` and committed to whatever passed; the failure reported from the field
     * passed that probe and then hit `Access is denied` extracting `chrome_elf.dll`, which left the
     * browser engine down for the session with a usable directory sitting unused beside it.
     */
    internal fun rootCandidates(
        osName: String = System.getProperty("os.name", ""),
        programData: String? = System.getenv("ProgramData"),
        homeDir: String = System.getProperty("user.home")
    ): List<File> {
        val home = File(homeDir, ".churchpresenter")
        if (!osName.lowercase().contains("win")) return listOf(home)
        return listOf(File(programData ?: "C:\\ProgramData", "ChurchPresenter"), home)
            .distinctBy { it.absolutePath }
    }

    /**
     * Whether [dir] can actually be written to, established by creating it and putting a file in it.
     *
     * `File.canWrite()` does not answer this on Windows: it reports the read-only attribute and not
     * the ACL, so a directory the account cannot write to can still come back true. The only
     * reliable probe is the write itself, and it is trivially cheap beside the ~100 MB extraction
     * it is deciding whether to start.
     */
    internal fun directoryIsWritable(dir: File): Boolean = runCatching {
        dir.mkdirs()
        if (!dir.isDirectory) return@runCatching false
        val probe = File(dir, ".write-probe-" + System.nanoTime())
        probe.createNewFile().also { probe.delete() }
    }.getOrDefault(false)

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
    internal fun installBlocker(writable: Boolean, usableSpaceBytes: Long): String? = when {
        !writable -> "permission_denied"
        usableSpaceBytes in 1 until JCEF_REQUIRED_BYTES -> "disk_space"
        else -> null
    }

    /**
     * "policy" when [message] is a machine refusing to load the library, or null for a real failure.
     *
     * Matched on the message because it is only knowable after the load has been attempted — unlike
     * [installBlocker], whose two causes can be asked about beforehand. Matching English wording is
     * a real limitation and the reason this only ever *suppresses* an event: a localised Windows
     * falls through and is reported as before, which is the safe direction to be wrong in.
     */
    internal fun policyBlock(message: String?): String? {
        val text = message?.lowercase() ?: return null
        val blocked = listOf("application control policy", "blocked by group policy", "blocked this file")
        return if (blocked.any { it in text }) "policy" else null
    }

    /** How an attempt to install JCEF into the candidate roots ended. */
    internal sealed interface Outcome {
        /** It installed into [root]. */
        data class Installed(val root: File) : Outcome
        /** Nothing was attempted; [reason] is the last candidate's blocker. */
        data class Blocked(val reason: String) : Outcome
        /** An attempt ran and threw; [cause] came from [root]. */
        data class Failed(val root: File, val cause: Throwable) : Outcome
    }

    /**
     * Builds the CEF app against [root], throwing whatever the install or the native load throws.
     *
     * The throw is the point: it is what tells [installIntoFirstUsableRoot] this root did not work.
     */
    fun buildCefApp(root: File): CefApp =
        CefAppBuilder().also { configure(it, root, readDmiTexts()) }.build()

    /**
     * Points [builder] at [root] — the engine under `jcef`, its cache under `webview-cache` — and
     * falls back to software rendering when [dmiTexts] name a virtual machine, whose GPU drivers
     * Chromium cannot rely on.
     */
    internal fun configure(builder: CefAppBuilder, root: File, dmiTexts: List<String>) {
        val installDir = File(root, "jcef")
        installDir.mkdirs()
        val cacheDir = File(root, "webview-cache")
        cacheDir.mkdirs()

        builder.setInstallDir(installDir)
        builder.setProgressHandler(ConsoleProgressHandler())
        builder.setAppHandler(object : MavenCefAppHandlerAdapter() {})
        builder.cefSettings.windowless_rendering_enabled = false
        builder.cefSettings.cache_path = cacheDir.absolutePath
        if (isVirtualizedEnvironment(dmiTexts)) {
            builder.addJcefArgs("--disable-gpu")
            builder.addJcefArgs("--disable-gpu-compositing")
            builder.addJcefArgs("--enable-unsafe-swiftshader")
        }
    }

    /** The machine's DMI product and vendor names, where Linux exposes them; empty everywhere else. */
    internal fun readDmiTexts(dmiDir: File = File("/sys/class/dmi/id")): List<String> =
        listOf("product_name", "sys_vendor").mapNotNull { file ->
            runCatching { File(dmiDir, file).readText().trim() }.getOrNull()
        }

    /**
     * [build] against [root], and once more after wiping `root/jcef` when the native load fails.
     *
     * "The specified procedure could not be found" from `libcef.dll` means the DLLs on disk do not
     * belong together — an interrupted extraction, or files left by an older build beside newer
     * ones. No retry against the same files can change that, but a fresh extraction can, and it
     * costs one download only on a machine that was already unable to browse. A second failure is
     * a real one and propagates for the next root to try.
     */
    internal fun <T> buildRepairing(root: File, build: (File) -> T): T = try {
        build(root)
    } catch (e: UnsatisfiedLinkError) {
        // A library the system lacks is missing from every extraction; re-downloading cannot help.
        if (missingSystemLibrary(e) != null) throw e
        File(root, "jcef").deleteRecursively()
        build(root)
    }

    /** Installs into the first usable root, with the real candidates and the real probes. */
    fun install(attempt: (File) -> Unit): Outcome = installIntoFirstUsableRoot(
        roots = rootCandidates(),
        // Two of the ways this fails are the machine rather than the app, and both are knowable
        // before the download starts. Asking beforehand keeps them out of the crash reports and is
        // locale-independent — the exceptions they raise say "Access is denied" and "There is not
        // enough space on the disk" only on an English Windows, so classifying them after the fact
        // would work in one language.
        blockerFor = { root ->
            val installDir = File(root, "jcef")
            installBlocker(directoryIsWritable(installDir), installDir.usableSpace)
        },
        stopRetrying = { policyBlock(it.message) != null || missingSystemLibrary(it) != null },
        attempt = attempt,
    )

    /**
     * Tries [attempt] against each of [roots] in turn and reports how it ended.
     *
     * A root that [blockerFor] rules out is skipped without an attempt; one whose attempt throws is
     * followed by the next root, unless [stopRetrying] says the failure is about the machine rather
     * than the directory — a software-policy block applies everywhere, so trying a second path only
     * spends another download to be refused identically.
     *
     * The attempt is a parameter because it is the one step that cannot run in a test: it downloads
     * and extracts a Chromium build. Everything around it — the order, the skipping, the stop
     * condition, and which outcome is reported — is the part that was wrong in production, and this
     * way it is the part under test.
     */
    // Throwable is deliberate: a JCEF install fails with UnsatisfiedLinkError as readily as with an
    // IOException, and both mean "this directory did not work, try the next one".
    @Suppress("TooGenericExceptionCaught")
    internal fun installIntoFirstUsableRoot(
        roots: List<File>,
        blockerFor: (File) -> String?,
        stopRetrying: (Throwable) -> Boolean,
        attempt: (File) -> Unit,
    ): Outcome {
        var lastOutcome: Outcome = Outcome.Blocked("no_install_dir")
        for (root in roots) {
            val blocker = blockerFor(root)
            if (blocker != null) {
                lastOutcome = Outcome.Blocked(blocker)
                continue
            }
            try {
                attempt(root)
                return Outcome.Installed(root)
            } catch (t: Throwable) {
                lastOutcome = Outcome.Failed(root, t)
                if (stopRetrying(t)) return lastOutcome
            }
        }
        return lastOutcome
    }
}

/**
 * Manages a single CefApp instance for the entire application.
 * Must call [init] once at startup before any WebView is used.
 */
object CefManager {
    /** The engine the app runs on: its state, and what an install's outcome means for it. */
    private val engine = CefEngine()

    /**
     * Whether a usable engine exists right now.
     *
     * Compose-backed, because it can go from true to false mid-session: [createClient] clears it when
     * the native side turns out to be dead, and the tab's "web engine unavailable" panel is only
     * reached if that write recomposes its reader.
     */
    val initialized: Boolean get() = engine.initialized

    /** True when [init] was skipped because the running macOS version is below [MIN_MACOS_MAJOR]. */
    val macOsUnsupported: Boolean get() = engine.macOsUnsupported

    /** True when [init] was skipped because this Windows predates Windows 10 -- see [isUnsupportedWindowsForJcef]. */
    val windowsUnsupported: Boolean get() = engine.windowsUnsupported

    /**
     * The root the browser engine actually installed into, once it has.
     *
     * Anything that wants to reach the engine's own directories has to ask, rather than assume
     * `~/.churchpresenter`: on Windows [JcefInstall.rootCandidates] prefers `%ProgramData%`, so a
     * hardcoded home path names a directory the engine never used — deleting nothing while the
     * live cache stays where it is.
     */
    val installRoot: File? get() = engine.installRoot

    /** The engine's disk cache, or null before it has installed. */
    val webviewCacheDir: File? get() = installRoot?.let { File(it, "webview-cache") }

    /**
     * True when the machine's own software policy refused to load the browser engine.
     *
     * A managed Windows build can carry an Application Control (WDAC/AppLocker) rule that blocks
     * unsigned or unknown binaries, and JCEF's `jcef.dll` is downloaded at first use rather than
     * shipped signed by us — so it is exactly what such a rule stops. Nothing the app does changes
     * that, and the operator cannot act on "install the Visual C++ Redistributable", which is what
     * the Web tab otherwise tells them.
     */
    val blockedByPolicy: Boolean get() = engine.blockedByPolicy

    /**
     * The Linux system library the engine needs and this machine lacks -- `libnspr4.so` -- or null.
     * The operator can install it; nothing the app downloads can stand in for it.
     */
    val missingLibrary: String? get() = engine.missingLibrary

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

    fun init() = engine.init(isUnsupportedMacOS(), isUnsupportedWindowsForJcef()) {
        // Must run before any JCEF class is loaded — CefBrowserWindowMac.getWindowHandle()
        // directly references sun.awt.AWTAccessor which the JVM module system blocks by default.
        patchJcefModuleAccess()
        JcefInstall.install { root ->
            val app = JcefInstall.buildRepairing(root, JcefInstall::buildCefApp)
            engine.clientSource = app::createClient
        }
    }

    /** A client for a new browser, or null when the web engine cannot provide one; see [CefEngine.createClient]. */
    fun createClient(): CefClient? = engine.createClient()

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
    EmbeddedBrowser(
        url = url,
        modifier = modifier,
        onUrlChanged = onUrlChanged,
        onTitleChanged = onTitleChanged,
        onSnapshot = onSnapshot,
        navController = navController,
        onBrowserCreated = onBrowserCreated,
        createClient = CefManager::createClient,
        capture = remember { screenCapture() },
    )
}

/** The screen capture snapshots are taken with, or null where there is none (a headless JVM). */
internal fun screenCapture(): ((Rectangle) -> BufferedImage)? =
    runCatching { Robot() }.getOrNull()?.let { robot -> robot::createScreenCapture }

/**
 * [EmbeddedWebView] once the engine is up: a browser from [createClient]'s client, wired to the
 * callbacks, and drawn by [panel].
 *
 * The three things only a running Chromium and a real screen provide come in as parameters — the
 * client, the AWT bridge that draws the browser's component ([panel], a `SwingPanel`), and the
 * screen [capture] snapshots are taken with — so everything else here runs under test.
 */
@Composable
internal fun EmbeddedBrowser(
    url: String,
    modifier: Modifier,
    onUrlChanged: ((String) -> Unit)?,
    onTitleChanged: ((String) -> Unit)?,
    onSnapshot: ((ImageBitmap) -> Unit)?,
    navController: WebNavController?,
    onBrowserCreated: ((CefBrowser) -> Unit)?,
    createClient: () -> CefClient?,
    capture: ((Rectangle) -> BufferedImage)?,
    panel: @Composable (Component, Modifier) -> Unit = { component, panelModifier ->
        SwingPanel(modifier = panelModifier, factory = { component })
    },
) {
    val client = remember { createClient() } ?: return
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

    // On an output that hides the pointer, the page hides it too; see LocalOutputCursorHidden.
    val hideCursor = LocalOutputCursorHidden.current
    val hideCursorState = rememberUpdatedState(hideCursor)
    LaunchedEffect(browser, hideCursor) {
        browser.executeJavaScript(outputCursorScript(hideCursor), "", 0)
    }

    DisposableEffect(Unit) {
        client.addDisplayHandler(PageDisplayHandler(onUrlChanged, onTitleChanged))
        // Intercept popups (target="_blank" links) — load in current browser instead
        client.addLifeSpanHandler(PopupsInPlace)
        client.addRequestHandler(MobileUserAgentHandler { navController?.mobileMode == true })
        client.addLoadHandler(CursorRestoringLoadHandler { hideCursorState.value })

        // Snapshot timer — captures the browser's area of the screen
        val timer = if (onSnapshot != null && capture != null) {
            Timer(SNAPSHOT_INTERVAL_MS) {
                captureSnapshot(browser.getUIComponent(), capture)?.let(onSnapshot)
            }.also { it.start() }
        } else null

        onDispose {
            timer?.stop()
            navController?.browser = null
            client.removeDisplayHandler()
            client.removeLifeSpanHandler()
            client.removeRequestHandler()
            client.removeLoadHandler()
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
        panel(browser.getUIComponent(), Modifier.fillMaxSize())
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
