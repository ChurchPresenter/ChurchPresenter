package org.churchpresenter.osrproto

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefBrowserSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefBrowserHeadless
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.CefSettings
import org.cef.handler.CefContextMenuHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/** What `-Purl`, `-Psize` and `-Pfps` (or `-Dosr.*`) asked for. */
private data class Config(val url: String, val width: Int, val height: Int, val fps: Int, val scale: Double)

private fun config(): Config {
    val fps = System.getProperty("osr.fps")?.toInt() ?: 60
    val url = System.getProperty("osr.url") ?: testPageUrl()
    System.getProperty("osr.size")?.let { size ->
        val (w, h) = size.split("x").map(String::toInt)
        return Config(url, w, h, fps, System.getProperty("osr.scale")?.toDouble() ?: 1.0)
    }
    val (w, h, scale) = outputSize()
    return Config(url, w, h, fps, scale)
}

/** How often the AWT event thread hands over messages other threads sent to Chromium's windows. */
private const val SENT_MESSAGE_PUMP_MS = 5

/**
 * On Windows, has the AWT event thread deliver messages other threads have *sent* (not posted) to
 * the windows it owns, every [SENT_MESSAGE_PUMP_MS].
 *
 * With windowless rendering, JCEF runs Chromium's message loop from the AWT event thread, so
 * Chromium's own windows (its IME window among them) belong to it. That thread parks in Java between
 * events and never looks at its Win32 queue, so a `SendMessage` to one of those windows from another
 * thread — a language switch reaching the IME window, sent from AWT's toolkit thread — waits forever,
 * and both windows hang. `PeekMessage` with `PM_QS_SENDMESSAGE` delivers exactly those pending sent
 * messages and nothing else, leaving the queue to whoever reads it. `-PnoSentPump=1` turns it off.
 */
private fun startSentMessagePump() {
    if (!System.getProperty("os.name").startsWith("Windows") || System.getProperty("osr.noSentPump") != null) return
    val msg = WinUser.MSG()
    val flags = PM_NOREMOVE or PM_QS_SENDMESSAGE
    SwingUtilities.invokeLater {
        Timer(SENT_MESSAGE_PUMP_MS) { User32.INSTANCE.PeekMessage(msg, null, 0, 0, flags) }.start()
        println("sent-message pump on")
    }
}

/** `PeekMessage` flags: leave posted messages in the queue, and handle only sent ones. */
private const val PM_NOREMOVE = 0x0000
private const val PM_QS_SENDMESSAGE = 0x0040 shl 16

/** The page is laid out this many DIPs wide on any output at least this wide. */
private const val LAYOUT_WIDTH = 1920

/**
 * The render size and scale for the Output display: its real pixels (logical size × the OS's scale),
 * with the page laid out [LAYOUT_WIDTH] DIPs wide — so a 4K output shows a sharp 1080p-shaped page
 * rather than a 4K-wide one. With no second display the Output is a window, rendered at 1080p.
 */
private fun outputSize(): Triple<Int, Int, Double> {
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.getOrNull(1)
        ?: return Triple(LAYOUT_WIDTH, 1080, 1.0)
    val config = device.defaultConfiguration
    val w = (config.bounds.width * config.defaultTransform.scaleX).roundToInt()
    val h = (config.bounds.height * config.defaultTransform.scaleY).roundToInt()
    return Triple(w, h, maxOf(1.0, w.toDouble() / LAYOUT_WIDTH))
}

/** The bundled test page (CSS animation, a clock, a form with a `<select>`), written out so Chromium can load it as a file. */
private fun testPageUrl(): String {
    val file = File(System.getProperty("java.io.tmpdir"), "osr-test-page.html")
    file.writeText(object {}.javaClass.getResource("/test-page.html")!!.readText())
    return file.toURI().toString()
}

private fun startCef(): CefApp {
    val root = File(System.getProperty("user.dir"), "build/cef").apply { mkdirs() }
    val builder = CefAppBuilder()
    builder.setInstallDir(File(root, "jcef"))
    builder.setAppHandler(object : MavenCefAppHandlerAdapter() {})
    builder.cefSettings.windowless_rendering_enabled = true
    builder.cefSettings.cache_path = File(root, "cache").absolutePath
    // Let a video start with sound without a click, as on an output nobody touches.
    builder.addJcefArgs("--autoplay-policy=no-user-gesture-required")
    // Smooth scrolling restarts its animation on every small step and loses about half of a
    // touchpad's movement; the page jumps instead. `-PsmoothScroll=1` keeps it, for comparison.
    if (System.getProperty("osr.smoothScroll") == null) {
        builder.addJcefArgs("--disable-smooth-scrolling")
        println("smooth scrolling off")
    }
    // `-PcefArgs="--disable-gpu --other"`: extra Chromium switches, to compare render paths.
    System.getProperty("osr.cefArgs")?.split(' ')?.filter(String::isNotBlank)?.forEach { arg ->
        builder.addJcefArgs(arg)
        println("chromium switch $arg")
    }
    return builder.build()
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        println("Uncaught on ${thread.name}:")
        e.printStackTrace(System.out)
    }
    // On Linux skiko waits for the screen's refresh inside the buffer swap on the AWT event thread,
    // which with windowless rendering is also Chromium's browser thread, so on video paints miss
    // refreshes and both windows fall to about 55/s. `-Pvsync=true` turns it back on.
    if (isLinux && System.getProperty("skiko.vsync.enabled") == null) System.setProperty("skiko.vsync.enabled", "false")
    val cfg = config()
    println("OSR prototype: ${cfg.width}x${cfg.height} px @ ${cfg.fps} fps, scale ${cfg.scale}, ${cfg.url}")
    val stats = Stats()
    val cef = startCef()
    startSentMessagePump()
    val client = cef.createClient()
    // OSR draws no native context menu; dropping its items stops Chromium from waiting on one.
    client.addContextMenuHandler(object : CefContextMenuHandlerAdapter() {
        override fun onBeforeContextMenu(b: CefBrowser?, f: CefFrame?, p: CefContextMenuParams?, model: CefMenuModel) {
            model.clear()
        }
    })
    // The Linux copy workaround reports the page's selection through a console message.
    client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
        override fun onConsoleMessage(b: CefBrowser?, level: CefSettings.LogSeverity?, message: String?, source: String?, line: Int) =
            message != null && LinuxClipboard.handle(message)
    })
    // target=_blank opens in place: a windowless browser has nowhere to put a popup window.
    client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
        override fun onBeforePopup(browser: CefBrowser, frame: CefFrame?, url: String?, name: String?): Boolean {
            url?.let(browser::loadURL)
            return true
        }
    })
    val settings = CefBrowserSettings().apply { windowless_frame_rate = cfg.fps }
    val viewW = (cfg.width / cfg.scale).toInt()
    val viewH = (cfg.height / cfg.scale).toInt()
    val store = FrameStore(stats, viewW)
    val browser = CefBrowserHeadless(client, cfg.url, settings, viewW, viewH, cfg.scale, store)
    SwingUtilities.invokeLater {
        browser.createImmediately()
        browser.setWindowlessFrameRate(cfg.fps)
    }
    stats.start("${cfg.width}x${cfg.height}@${cfg.fps}")

    application {
        val shutdown = {
            browser.close(true)
            client.dispose()
            cef.dispose()
            exitApplication()
            exitProcess(0)
        }
        // `-PnoOutput=1`: the Preview alone, to tell what the second window costs.
        if (System.getProperty("osr.noOutput") == null) OutputWindow(store, stats, cfg, shutdown)
        PreviewWindow(store, stats, browser, shutdown)
    }
}

@Composable
private fun OutputWindow(store: FrameStore, stats: Stats, cfg: Config, onClose: () -> Unit) {
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val second = screens.getOrNull(1)?.defaultConfiguration?.bounds
    val state = remember {
        if (second != null) {
            WindowState(position = WindowPosition(second.x.dp, second.y.dp), size = DpSize(second.width.dp, second.height.dp))
        } else {
            WindowState(size = DpSize((cfg.width / 2).dp, (cfg.height / 2).dp))
        }
    }
    Window(onCloseRequest = onClose, state = state, title = "Output", undecorated = second != null) {
        DisableInputMethodsIfAsked(window)
        FrameView(store, stats, "output", Modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
private fun PreviewWindow(store: FrameStore, stats: Stats, browser: CefBrowserHeadless, onClose: () -> Unit) {
    val state = remember { WindowState(size = DpSize(640.dp, 420.dp)) }
    Window(onCloseRequest = onClose, state = state, title = "Preview") {
        DisableInputMethodsIfAsked(window)
        val pageFocused = remember { AtomicBoolean(false) }
        ForwardKeysWhileFocused(window, pageFocused, browser)
        Column(Modifier.fillMaxSize().background(Color(0xFF202020))) {
            UrlBar(browser)
            val focus = remember { FocusRequester() }
            val cursor by store.cursor.collectAsState()
            FrameView(
                store, stats, "preview", small = true,
                modifier = Modifier.fillMaxSize()
                    .focusRequester(focus)
                    .onFocusChanged {
                        pageFocused.set(it.isFocused)
                        browser.setFocus(it.isFocused)
                    }
                    .focusable()
                    .forwardPointer(browser, cursor, onPress = { focus.requestFocus() }),
            )
        }
    }
}

/**
 * With `-PnoIme=1`, turns input methods off on [window] and everything in it. A diagnostic for the
 * Windows freeze: with windowless rendering, CEF's windows belong to the AWT event thread, and a
 * language-switch message sent to them hangs both windows. This tests whether AWT's input-method
 * calls are the other half of that wait. It also stops IME composition in the URL bar.
 */
@Composable
private fun DisableInputMethodsIfAsked(window: Window) {
    DisposableEffect(window) {
        if (System.getProperty("osr.noIme") != null) {
            fun off(c: Component) {
                c.enableInputMethods(false)
                (c as? Container)?.components?.forEach(::off)
            }
            off(window)
            println("input methods off: ${(window as? Frame)?.title}")
        }
        onDispose { }
    }
}

private val isMac = System.getProperty("os.name").startsWith("Mac")
private val isLinux = System.getProperty("os.name").startsWith("Linux")

/**
 * Types [char] into the page's focused field. JCEF's native macOS key path drops a `KEY_TYPED` whose
 * character is outside ASCII (Cyrillic arrives from AWT intact and never reaches the page), so on
 * macOS such characters go in as an edit instead. Prototype workaround; it does not reach a page that
 * reads raw key events (a canvas game) and is not undone with the field's other typing.
 */
private fun insertText(browser: CefBrowserHeadless, char: Char) {
    val literal = "\\u%04x".format(char.code)
    browser.focusedFrame?.executeJavaScript("document.execCommand('insertText', false, '$literal')", "", 0)
}

/**
 * While [pageFocused], sends every key event of [window] to [browser] (on Linux re-made by
 * [linuxKeyEvent], with copy and cut through [LinuxClipboard]) and keeps it from Compose. Compose's own key events carry no `KEY_TYPED` (Chromium's char event) and wrap the AWT
 * event, so the raw AWT event is taken here instead, with its real id, scancode and raw code.
 */
@Composable
private fun ForwardKeysWhileFocused(window: Window, pageFocused: AtomicBoolean, browser: CefBrowserHeadless) {
    DisposableEffect(window, browser) {
        val dispatcher = KeyEventDispatcher { e ->
            if (!pageFocused.get() || SwingUtilities.getWindowAncestor(e.component) !== window) {
                false
            } else {
                val sent = if (isLinux) linuxKeyEvent(e) else e
                if (System.getProperty("osr.logKeys") != null) {
                    val keysym = if (sent !== e) " sent=0x%x".format(sent.keyChar.code) else ""
                    println("key id=${e.id} code=${e.keyCode} char=${e.keyChar.code} '${e.keyChar}' mods=${e.modifiersEx}$keysym")
                }
                val clipboard = if (isLinux) LinuxClipboard.actionOf(e) else null
                when {
                    isMac && e.id == KeyEvent.KEY_TYPED && e.keyChar.code > 0x7f -> insertText(browser, e.keyChar)
                    clipboard == LinuxClipboard.Action.CUT -> LinuxClipboard.read(browser, clipboard)
                    clipboard == LinuxClipboard.Action.COPY -> {
                        browser.key(sent)
                        LinuxClipboard.read(browser, clipboard)
                    }
                    else -> browser.key(sent)
                }
                true
            }
        }
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        manager.addKeyEventDispatcher(dispatcher)
        onDispose { manager.removeKeyEventDispatcher(dispatcher) }
    }
}

@Composable
private fun UrlBar(browser: CefBrowserHeadless) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(6.dp).background(Color(0xFF303030))) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().padding(6.dp).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && text.isNotBlank()) {
                    val url = if ("://" in text || NO_HOST_SCHEMES.any(text::startsWith)) text else "https://$text"
                    browser.loadURL(url)
                    true
                } else false
            },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        BasicText("Type a URL, Enter to load", style = TextStyle(color = Color.Gray, fontSize = 13.sp))
                    }
                    inner()
                }
            },
        )
    }
}

/** Schemes the URL bar takes as typed, with no `//`. */
private val NO_HOST_SCHEMES = listOf("about:", "data:", "javascript:")
