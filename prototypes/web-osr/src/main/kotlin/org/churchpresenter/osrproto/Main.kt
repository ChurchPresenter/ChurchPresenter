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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/** What `-Purl`, `-Psize` and `-Pfps` (or `-Dosr.*`) asked for. */
private data class Config(val url: String, val width: Int, val height: Int, val fps: Int, val scale: Double)

private fun config(): Config {
    val (w, h) = (System.getProperty("osr.size") ?: "1920x1080").split("x").map(String::toInt)
    val fps = System.getProperty("osr.fps")?.toInt() ?: 60
    val scale = System.getProperty("osr.scale")?.toDouble() ?: 1.0
    val url = System.getProperty("osr.url") ?: testPageUrl()
    return Config(url, w, h, fps, scale)
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
    val store = FrameStore(stats, cfg.scale.toFloat())
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
        FrameView(store, stats, "output", Modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
private fun PreviewWindow(store: FrameStore, stats: Stats, browser: CefBrowserHeadless, onClose: () -> Unit) {
    val state = remember { WindowState(size = DpSize(640.dp, 420.dp)) }
    Window(onCloseRequest = onClose, state = state, title = "Preview") {
        Column(Modifier.fillMaxSize().background(Color(0xFF202020))) {
            UrlBar(browser)
            val focus = remember { FocusRequester() }
            val cursor by store.cursor.collectAsState()
            FrameView(
                store, stats, "preview",
                Modifier.fillMaxSize()
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        (event.nativeKeyEvent as? KeyEvent)?.let(browser::key)
                        true
                    }
                    .forwardPointer(browser, cursor, onPress = {
                        focus.requestFocus()
                        browser.setFocus(true)
                    }),
            )
        }
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
                val awt = e.nativeKeyEvent as? KeyEvent
                if (awt?.id == KeyEvent.KEY_PRESSED && awt.keyCode == KeyEvent.VK_ENTER && text.isNotBlank()) {
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
