package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.churchpresenter.diagnostics.Log
import java.awt.AWTEvent
import java.awt.Dialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.HierarchyEvent
import javax.swing.SwingUtilities
import java.awt.Color as AwtColor

/** What the windows' title bars are painted with: the theme's surface, its text, and which it is. */
internal data class TitleBarStyle(val dark: Boolean, val caption: AwtColor, val text: AwtColor)

/** The title bar the theme asks for: the menu bar's own surface, so the two read as one band. */
internal fun titleBarStyle(surface: Color, onSurface: Color): TitleBarStyle =
    TitleBarStyle(dark = surface.luminance() < HALF, caption = surface.awt(), text = onSurface.awt())

private const val HALF = 0.5f

private fun Color.awt(): AwtColor = AwtColor(toArgb(), true)

/**
 * Paints the title bars of the app's windows in the theme, from the main window. The title bar is
 * the platform's — light on a light desktop whatever the app's theme — and neither Compose nor
 * Swing reaches it, so each platform is asked in its own way: Windows 11 takes the caption and text
 * colours themselves (Windows 10 only dark or light), GNOME the dark variant of its frame on a dark
 * theme, and macOS the dark or light appearance for the whole app.
 */
@Composable
internal fun ThemedTitleBars(
    onStyle: (TitleBarStyle) -> Unit = { SwingUtilities.invokeLater { AppTitleBars.update(it) } },
) {
    val colors = MaterialTheme.colorScheme
    val style = titleBarStyle(colors.surface, colors.onSurface)
    SideEffect { onStyle(style) }
}

/**
 * The title bars painted so far, and the style they are in: a change repaints every open window,
 * and a window that opens later is painted as it is made. [paint] is the platform's painter.
 */
internal class TitleBars<W>(
    private val openWindows: () -> List<W>,
    private val paint: (W, TitleBarStyle) -> Unit,
) {
    var style: TitleBarStyle? = null
        private set

    /** Paints every open window in [style], unless they already are. */
    fun update(style: TitleBarStyle) {
        if (style == this.style) return
        this.style = style
        openWindows().forEach { paint(it, style) }
    }

    /** Paints a window just made in the current style; before the first style there is none. */
    fun opened(window: W) {
        style?.let { paint(window, it) }
    }
}

/** The app's [TitleBars], made on first use. */
internal object AppTitleBars {
    private val titleBars: TitleBars<Window>? by lazy { appTitleBars(System.getProperty("os.name").orEmpty()) }

    /** The style the app's title bars are in; null before the first, or on a platform with no painter. */
    val style: TitleBarStyle? get() = titleBars?.style

    fun update(style: TitleBarStyle) {
        titleBars?.update(style)
    }
}

/**
 * The title bars of every window on the platform [osName] names, or null where there is no painter;
 * [watch] is handed them to paint each window as it is made.
 */
internal fun appTitleBars(osName: String, watch: (TitleBars<Window>) -> Unit = ::watchNewWindows): TitleBars<Window>? {
    val paint = titleBarPainter(osName) ?: return null
    return TitleBars(
        openWindows = { Window.getWindows().filter { it.isDisplayable && it.hasTitleBar() } },
        paint = loggingFailures(paint),
    ).also(watch)
}

/** [paint], with a failure logged rather than thrown: a title bar left as it was is no reason to stop. */
internal fun <W> loggingFailures(paint: (W, TitleBarStyle) -> Unit): (W, TitleBarStyle) -> Unit = { window, style ->
    runCatching { paint(window, style) }.onFailure { Log.warn(TAG, "Title bar not painted: $it") }
}

/**
 * Paints each window as it is made. Displayable is when the window's native one exists, before it
 * is first shown: painted then, a window never flashes the platform's own colours.
 */
private fun watchNewWindows(bars: TitleBars<Window>) {
    Toolkit.getDefaultToolkit().addAWTEventListener(
        { event -> madeTitledWindow(event)?.let(bars::opened) },
        AWTEvent.HIERARCHY_EVENT_MASK,
    )
}

private const val TAG = "TitleBars"

/** The window [event] says was just made displayable, when it is one with a title bar. */
internal fun madeTitledWindow(event: AWTEvent): Window? {
    val hierarchy = event as? HierarchyEvent ?: return null
    if (hierarchy.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() == 0L) return null
    return (hierarchy.changed as? Window)?.takeIf { it.isDisplayable && it.hasTitleBar() }
}

/** A frame or dialog with the platform's decorations; output windows are undecorated. */
private fun Window.hasTitleBar(): Boolean = when (this) {
    is Frame -> !isUndecorated
    is Dialog -> !isUndecorated
    else -> false
}
