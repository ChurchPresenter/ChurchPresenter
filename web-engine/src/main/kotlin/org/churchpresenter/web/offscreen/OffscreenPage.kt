package org.churchpresenter.web.offscreen

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.StateFlow
import org.cef.CefBrowserSettings
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefBrowserHeadless
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.web.presenter.CefManager
import org.churchpresenter.web.presenter.MobileUserAgentHandler
import org.churchpresenter.web.presenter.PageDisplayHandler
import org.churchpresenter.web.presenter.PopupsInPlace
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Timer
import kotlin.math.roundToInt

/**
 * The live web page, rendered once off screen by one windowless Chromium and drawn by every output
 * from the same [frames]; see [OffscreenWebView] and [OffscreenWebControl].
 *
 * The page is laid out [LAYOUT_WIDTH] DIPs wide on any output at least that wide, at a device scale
 * that makes its pixels the largest output's (a 4K projector gets a sharp 1080p-shaped page, not a
 * 4K-wide one); an output of another shape draws it letterboxed. Built and closed on the AWT event
 * thread, which with windowless rendering is Chromium's own.
 */
class OffscreenPage internal constructor(
    private val client: CefClient,
    url: String,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    options: Options,
) {
    /**
     * How a page runs: on [os], reporting to Sentry or not, and when it asks for mobile pages.
     * [start] creates the native browser, [send] is where reports go, and [clock] and [sessionsSent]
     * time a session and count the run's summaries: all seams for tests.
     */
    internal class Options(
        val os: String,
        val reports: Boolean,
        val mobileMode: () -> Boolean,
        val start: (CefBrowserHeadless) -> Unit = ::startBrowser,
        val send: (OffscreenReport) -> Unit = if (reports) ::sendToSentry else ::sendFailuresOnly,
        val clock: () -> Long = System::currentTimeMillis,
        val sessionsSent: AtomicInteger = OffscreenReporter.SESSIONS_SENT,
    )

    private val os = options.os
    private val clock = options.clock
    internal val stats = OffscreenStats()
    private val rgba = os.startsWith("Linux")
    private var pixels = IntSize(LAYOUT_WIDTH, DEFAULT_HEIGHT)
    private var density = 1.0
    private val reporter = OffscreenReporter(
        tags = ::tags,
        clock = options.clock,
        send = options.send,
        sessionsSent = options.sessionsSent,
    )
    private val store = OffscreenFrames(
        dipWidth = { headless.viewWidth },
        rgba = rgba,
        stats = stats,
        onPaintFailed = reporter::paintThrew,
    )
    private val headless: CefBrowserHeadless = run {
        client.addContextMenuHandler(NoContextMenu)
        client.addDisplayHandler(ConsoleClipboardHandler(onUrlChanged, onTitleChanged, os))
        client.addLifeSpanHandler(PopupsInPlace)
        client.addRequestHandler(MobileUserAgentHandler(options.mobileMode, ::rendererEnded))
        val (view, scale) = layoutFor(pixels)
        val settings = CefBrowserSettings().apply { windowless_frame_rate = FRAME_RATE }
        CefBrowserHeadless(client, url, settings, view.width, view.height, scale, store)
    }
    private val requested = LinkedHashMap<Any, Pair<IntSize, Double>>()
    private val resizeTimer = Timer(RESIZE_SETTLE_MS) { applySize() }.apply { isRepeats = false }
    private val tickTimer = Timer(TICK_MS) { reporter.tick(stats.drain(), stats.copyP95Ms()) }
    private var closed = false
    private var reported = false
    private var lastReload = Long.MIN_VALUE / 2

    /** The page's browser: what the Web tab steers (address, history, zoom, scripts). */
    val browser: CefBrowserHeadless get() = headless

    /** The latest picture of the page, or null before Chromium's first paint. */
    val frames: StateFlow<OffscreenFrame?> get() = store.frames

    /** The AWT cursor type the page asks for under the pointer. */
    val cursor: StateFlow<Int> get() = store.cursor

    init {
        options.start(headless)
        tickTimer.start()
        openPages += this
        CrashReporter.breadcrumb("Off-screen web page opened", category = "web")
    }

    /** Loads [url] unless the page is already there, as when the address came from the page itself. */
    fun load(url: String) {
        if (url.isNotBlank() && url != headless.url) headless.loadURL(url)
    }

    /**
     * An output window [surface] is [size] pixels; the page renders at the largest of them. An output
     * lays the page out [LAYOUT_WIDTH] DIPs wide whatever the screen's scale; a page of its own (the
     * Web tab's preview) passes its screen's [density], so it reads at the same size as the app.
     */
    fun requestSize(surface: Any, size: IntSize, density: Float = 1f) {
        if (closed || size.width <= 0 || size.height <= 0) return
        requested[surface] = size to density.toDouble()
        resizeTimer.restart()
    }

    /** [surface] no longer shows the page. */
    fun releaseSize(surface: Any) {
        if (requested.remove(surface) != null && !closed) resizeTimer.restart()
    }

    /** Renders at the largest requested size; run by the resize timer once requests have settled. */
    internal fun applySize() {
        if (closed) return
        val (largest, largestDensity) = requested.values.maxByOrNull { (size, _) -> size.width.toLong() * size.height }
            ?: return
        if (largest == pixels && largestDensity == density) return
        pixels = largest
        density = largestDensity
        val (view, scale) = layoutFor(largest, largestDensity)
        headless.resize(view.width, view.height, scale)
        CrashReporter.breadcrumb("Off-screen web page resized to ${largest.width}x${largest.height}", category = "web")
    }

    /** Closes the browser and frees the frames once no window can still be drawing the last one. */
    fun close() {
        if (closed) return
        closed = true
        resizeTimer.stop()
        tickTimer.stop()
        finishReports()
        openPages -= this
        runCatching { headless.close(true) }
        runCatching { client.dispose() }
        val free = store.close()
        Timer(FREE_AFTER_MS) { free() }.apply { isRepeats = false }.start()
        CrashReporter.breadcrumb("Off-screen web page closed", category = "web")
    }

    /**
     * The page's renderer process ended: reported, and the page reloaded so an output is not left
     * blank, unless it ended within [RELOAD_AGAIN_AFTER_MS] of the last reload, which would only loop.
     */
    private fun rendererEnded(status: CefRequestHandler.TerminationStatus, errorCode: Int, message: String) {
        reporter.rendererEnded(status.name, errorCode, message)
        CrashReporter.breadcrumb("Off-screen web page's renderer ended: ${status.name}", category = "web")
        val now = clock()
        if (closed || now - lastReload < RELOAD_AGAIN_AFTER_MS) return
        lastReload = now
        headless.reload()
    }

    /** Sends what the page's last second and its session came to, once. */
    private fun finishReports() {
        if (reported) return
        reported = true
        reporter.tick(stats.drain(), stats.copyP95Ms())
        reporter.finish(stats.copyP95Ms())
    }

    private fun tags(): Map<String, String> = mapOf(
        "os" to os,
        "web.offscreen.render_api" to (System.getProperty("skiko.renderApi") ?: "default"),
        OffscreenReporter.TAG_SIZE to "${pixels.width}x${pixels.height}",
        "web.offscreen.scale" to "%.2f".format(headless.scale),
        "web.offscreen.rgba" to rgba.toString(),
    )

    companion object {
        /** The page is laid out this many DIPs wide on any output at least this wide. */
        const val LAYOUT_WIDTH = 1920
        private const val DEFAULT_HEIGHT = 1080
        private const val RESIZE_SETTLE_MS = 250
        private const val TICK_MS = 1000
        private const val FREE_AFTER_MS = 1000
        private const val RELOAD_AGAIN_AFTER_MS = 10_000L

        /** The pages open now, so a quit can still report them; touched on the event thread only. */
        private val openPages = LinkedHashSet<OffscreenPage>()

        /**
         * Sends the session summary of every page still open, as the app quits: a page live at the
         * quit is never closed, so its session would otherwise go unreported. The pages stay open.
         */
        fun reportOpenSessions() = openPages.toList().forEach(OffscreenPage::finishReports)

        /**
         * Opens [url] off screen, or returns null when the engine did not start windowless or the
         * page fails to start (reported). [onUrlChanged] and [onTitleChanged] hear the page's own
         * navigation. Without [reports] only a paint that throws goes to Sentry, not the page's
         * frame rate or session. Requests carry the mobile User-Agent while [mobileMode] says so.
         */
        fun open(
            url: String,
            onUrlChanged: (String) -> Unit,
            onTitleChanged: (String) -> Unit,
            os: String = System.getProperty("os.name"),
            reports: Boolean = true,
            mobileMode: () -> Boolean = { false },
        ): OffscreenPage? {
            if (!CefManager.offscreen) return null
            val client = CefManager.createClient() ?: return null
            return openWith(client, url, onUrlChanged, onTitleChanged, Options(os, reports, mobileMode))
        }

        /** [open] once there is a [client]: the page, or null after reporting why it failed. */
        internal fun openWith(
            client: CefClient,
            url: String,
            onUrlChanged: (String) -> Unit,
            onTitleChanged: (String) -> Unit,
            options: Options,
        ): OffscreenPage? {
            val os = options.os
            return runCatching { OffscreenPage(client, url, onUrlChanged, onTitleChanged, options) }
                .onFailure { e ->
                    runCatching { client.dispose() }
                    options.send(OffscreenReport.PageFailed(e, mapOf("os" to os)))
                    CrashReporter.breadcrumb(
                        "Off-screen web page failed; drawing the windowed browser",
                        category = "web",
                    )
                }
                .getOrNull()
        }

        /** The page's size in DIPs, and the device scale, for an output of [pixels] on a screen of [density]. */
        internal fun layoutFor(pixels: IntSize, density: Double = 1.0): Pair<IntSize, Double> {
            val scale = maxOf(1.0, density, pixels.width.toDouble() / LAYOUT_WIDTH)
            return IntSize((pixels.width / scale).roundToInt(), (pixels.height / scale).roundToInt()) to scale
        }
    }
}

/** Creates [browser]'s native side and sets its frame rate. */
private fun startBrowser(browser: CefBrowserHeadless) {
    browser.createImmediately()
    browser.setWindowlessFrameRate(FRAME_RATE)
}

/** [report] when it is a paint that threw: a page whose sessions are not reported still reports that. */
private fun sendFailuresOnly(report: OffscreenReport) {
    if (report is OffscreenReport.PaintThrew || report is OffscreenReport.RendererEnded) sendToSentry(report)
}

private const val FRAME_RATE = 60

/** A windowless browser draws no native menu; dropping its items stops Chromium waiting on one. */
private object NoContextMenu : CefContextMenuHandlerAdapter() {
    override fun onBeforeContextMenu(
        browser: CefBrowser?,
        frame: CefFrame?,
        params: CefContextMenuParams?,
        model: CefMenuModel,
    ) {
        model.clear()
    }
}

/** [PageDisplayHandler], and on Linux the console messages [LinuxClipboard] copies out through. */
private class ConsoleClipboardHandler(
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    private val os: String,
) : PageDisplayHandler(onUrlChanged, onTitleChanged) {
    override fun onConsoleMessage(
        browser: CefBrowser?,
        level: CefSettings.LogSeverity?,
        message: String?,
        source: String?,
        line: Int,
    ): Boolean = os.startsWith("Linux") && message != null && LinuxClipboard.handle(message)
}
