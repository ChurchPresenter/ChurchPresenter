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
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefBrowserSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefBrowserHeadless
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter
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
    return builder.build()
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        println("Uncaught on ${thread.name}:")
        e.printStackTrace(System.out)
    }
    val cfg = config()
    println("OSR prototype: ${cfg.width}x${cfg.height} px @ ${cfg.fps} fps, scale ${cfg.scale}, ${cfg.url}")
    val stats = Stats()
    val cef = startCef()
    val client = cef.createClient()
    // OSR draws no native context menu; dropping its items stops Chromium from waiting on one.
    client.addContextMenuHandler(object : CefContextMenuHandlerAdapter() {
        override fun onBeforeContextMenu(b: CefBrowser?, f: CefFrame?, p: CefContextMenuParams?, model: CefMenuModel) {
            model.clear()
        }
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
        OutputWindow(store, stats, cfg, shutdown)
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
 * While [pageFocused], sends every key event of [window] to [browser] untouched and keeps it from
 * Compose. Compose's own key events carry no `KEY_TYPED` (Chromium's char event) and wrap the AWT
 * event, so the raw AWT event is taken here instead, with its real id, scancode and raw code.
 */
@Composable
private fun ForwardKeysWhileFocused(window: Window, pageFocused: AtomicBoolean, browser: CefBrowserHeadless) {
    DisposableEffect(window, browser) {
        val dispatcher = KeyEventDispatcher { e ->
            if (!pageFocused.get() || SwingUtilities.getWindowAncestor(e.component) !== window) {
                false
            } else {
                if (System.getProperty("osr.logKeys") != null) {
                    println("key id=${e.id} code=${e.keyCode} char=${e.keyChar.code} '${e.keyChar}' mods=${e.modifiersEx}")
                }
                if (isMac && e.id == KeyEvent.KEY_TYPED && e.keyChar.code > 0x7f) {
                    insertText(browser, e.keyChar)
                } else {
                    browser.key(e)
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
                    val url = if ("://" in text) text else "https://$text"
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
