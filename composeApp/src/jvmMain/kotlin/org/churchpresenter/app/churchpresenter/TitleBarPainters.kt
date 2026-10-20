package org.churchpresenter.app.churchpresenter

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.Window
import java.awt.Color as AwtColor

/** The painter for the platform [osName] names, or null where there is none. */
internal fun titleBarPainter(osName: String): ((Window, TitleBarStyle) -> Unit)? {
    val os = osName.lowercase()
    return when {
        os.contains("win") -> ::paintWindowsTitleBar
        os.contains("mac") -> MacAppearance::paint
        os.contains("linux") -> ::paintGtkTitleBar
        else -> null
    }
}

// ── Windows ─────────────────────────────────────────────────────────────────────────────────────

/** DWM's window attributes: dark mode (19 before Windows 10 20H1), then Windows 11's colours. */
internal const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
internal const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
internal const val DWMWA_BORDER_COLOR = 34
internal const val DWMWA_CAPTION_COLOR = 35
internal const val DWMWA_TEXT_COLOR = 36

/** A colour as Windows' COLORREF: 0x00BBGGRR. */
internal fun colorRef(color: AwtColor): Int = (color.blue shl BLUE_SHIFT) or (color.green shl GREEN_SHIFT) or color.red

private const val BLUE_SHIFT = 16
private const val GREEN_SHIFT = 8

/**
 * Sets a window's DWM attributes to [style] through [set], which takes an attribute and its value
 * and returns the HRESULT. Windows 10 refuses the colours, and keeps the dark or light caption.
 */
internal fun setDwmTitleBar(style: TitleBarStyle, set: (attribute: Int, value: Int) -> Int) {
    val dark = if (style.dark) 1 else 0
    if (set(DWMWA_USE_IMMERSIVE_DARK_MODE, dark) != 0) set(DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, dark)
    set(DWMWA_CAPTION_COLOR, colorRef(style.caption))
    set(DWMWA_BORDER_COLOR, colorRef(style.caption))
    set(DWMWA_TEXT_COLOR, colorRef(style.text))
}

private fun paintWindowsTitleBar(window: Window, style: TitleBarStyle) {
    val hwnd = Native.getWindowPointer(window) ?: return
    setDwmTitleBar(style) { attribute, value ->
        Dwm.api.DwmSetWindowAttribute(hwnd, attribute, intArrayOf(value), Int.SIZE_BYTES)
    }
    // The frame is redrawn now rather than when the window is next activated.
    Dwm.user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_REDRAW_FRAME)
}

/** SWP_NOSIZE, NOMOVE, NOZORDER and NOACTIVATE with SWP_FRAMECHANGED: the frame redrawn, nothing else. */
private const val SWP_REDRAW_FRAME = 0x0001 or 0x0002 or 0x0004 or 0x0010 or 0x0020

private object Dwm {
    val api: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }
    val user32: User32Api by lazy { Native.load("user32", User32Api::class.java) }
}

/** PascalCase because `Native.load` binds each function to the DLL's symbol of the same name. */
@Suppress("FunctionNaming")
private interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntArray, size: Int): Int
}

@Suppress("FunctionNaming", "LongParameterList")
private interface User32Api : Library {
    fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
}

// ── Linux ───────────────────────────────────────────────────────────────────────────────────────

/**
 * The `_GTK_THEME_VARIANT` GNOME's window manager draws a frame by. A GTK theme has only a dark
 * variant, so "light" is the desktop theme's own frame — dark grey on Ubuntu 20.04's Yaru.
 */
internal fun gtkThemeVariant(style: TitleBarStyle): String = if (style.dark) "dark" else "light"

private fun paintGtkTitleBar(window: Window, style: TitleBarStyle) {
    val id = Native.getWindowID(window)
    if (id == 0L) return
    with(XServer) {
        val display = display ?: return
        val value = gtkThemeVariant(style).toByteArray()
        api.XChangeProperty(
            display, NativeLong(id), api.XInternAtom(display, "_GTK_THEME_VARIANT", false),
            api.XInternAtom(display, "UTF8_STRING", false), BYTE_FORMAT, PROP_MODE_REPLACE, value, value.size,
        )
        api.XFlush(display)
    }
}

private const val BYTE_FORMAT = 8
private const val PROP_MODE_REPLACE = 0

/** The app's own connection to the X server, opened once; null without one (Wayland only, headless). */
private object XServer {
    val api: XlibApi by lazy { Native.load("X11", XlibApi::class.java) }
    val display: Pointer? by lazy { runCatching { api.XOpenDisplay(null) }.getOrNull() }
}

@Suppress("FunctionNaming", "LongParameterList")
private interface XlibApi : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XInternAtom(display: Pointer, name: String, onlyIfExists: Boolean): NativeLong
    fun XChangeProperty(
        display: Pointer,
        window: NativeLong,
        property: NativeLong,
        type: NativeLong,
        format: Int,
        mode: Int,
        data: ByteArray,
        elements: Int,
    ): Int
    fun XFlush(display: Pointer): Int
}

// ── macOS ───────────────────────────────────────────────────────────────────────────────────────

/** The AppKit appearance whose title bars match [style]. */
internal fun macAppearanceName(style: TitleBarStyle): String =
    if (style.dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"

/**
 * macOS draws every title bar by the app's appearance, so it is set once for the app, not per
 * window: `NSApp.appearance`, set on AppKit's own thread through the Objective-C runtime.
 */
private object MacAppearance {
    private var applied: String? = null
    private val objc by lazy { NativeLibrary.getInstance("objc") }

    @Suppress("UnusedParameter")
    fun paint(window: Window, style: TitleBarStyle) {
        val name = macAppearanceName(style)
        if (name == applied) return
        applied = name
        val nsName = send(cls("NSString"), sel("stringWithUTF8String:"), name)
        val appearance = send(cls("NSAppearance"), sel("appearanceNamed:"), nsName)
        send(
            send(cls("NSApplication"), sel("sharedApplication")),
            sel("performSelectorOnMainThread:withObject:waitUntilDone:"),
            sel("setAppearance:"), appearance, 0,
        )
    }

    private fun cls(name: String): Pointer = objc.getFunction("objc_getClass").invokePointer(arrayOf(name))
    private fun sel(name: String): Pointer = objc.getFunction("sel_registerName").invokePointer(arrayOf(name))
    private fun send(vararg args: Any?): Pointer = objc.getFunction("objc_msgSend").invokePointer(arrayOf(*args))
}
