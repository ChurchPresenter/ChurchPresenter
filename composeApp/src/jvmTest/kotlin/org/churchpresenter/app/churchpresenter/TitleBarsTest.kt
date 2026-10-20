@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.HierarchyEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.awt.Color as AwtColor

/**
 * The title bars painted in the theme: what each platform is asked for, and when a window is painted.
 *
 * The native calls themselves (DWM, Xlib, AppKit) need the platform and a real window; they were
 * checked by hand on Windows 11, Ubuntu 20.04 (GNOME, X11) and macOS. These hold what they are fed.
 */
class TitleBarsTest {

    private val dark = titleBarStyle(Color(0xFF1E1E1E), Color(0xFFE4E6E7))
    private val light = titleBarStyle(Color(0xFFEFEFF1), Color(0xFF1A1C1E))

    @Test
    fun `the style is the surface and its text, dark by the surface`() {
        assertTrue(dark.dark)
        assertFalse(light.dark)
        assertEquals(AwtColor(0x1E, 0x1E, 0x1E), dark.caption)
        assertEquals(AwtColor(0x1A, 0x1C, 0x1E), light.text)
    }

    @Test
    fun `a new style paints every open window once, an unchanged one nothing`() {
        val painted = mutableListOf<Pair<String, TitleBarStyle>>()
        val bars = TitleBars(openWindows = { listOf("main", "settings") }, paint = { w, s -> painted += w to s })

        bars.update(dark)
        bars.update(dark)
        assertEquals(listOf("main" to dark, "settings" to dark), painted)

        painted.clear()
        bars.update(light)
        assertEquals(listOf("main" to light, "settings" to light), painted)
    }

    @Test
    fun `a window made later is painted in the current style, and none before the first`() {
        val painted = mutableListOf<Pair<String, TitleBarStyle>>()
        val bars = TitleBars<String>(openWindows = { emptyList() }, paint = { w, s -> painted += w to s })

        bars.opened("splash")
        assertTrue(painted.isEmpty())
        assertNull(bars.style)

        bars.update(dark)
        bars.opened("about")
        assertEquals(listOf("about" to dark), painted)
    }

    @Test
    fun `windows gets dark mode, then the caption, border and text as COLORREFs`() {
        val set = mutableListOf<Pair<Int, Int>>()
        setDwmTitleBar(dark) { attribute, value -> set += attribute to value; 0 }

        assertEquals(
            listOf(
                DWMWA_USE_IMMERSIVE_DARK_MODE to 1,
                DWMWA_CAPTION_COLOR to 0x1E1E1E,
                DWMWA_BORDER_COLOR to 0x1E1E1E,
                DWMWA_TEXT_COLOR to 0xE7E6E4,
            ),
            set,
        )
    }

    @Test
    fun `an older windows 10 that refuses dark mode is asked by its old number`() {
        val set = mutableListOf<Pair<Int, Int>>()
        setDwmTitleBar(light) { attribute, value ->
            set += attribute to value
            if (attribute == DWMWA_USE_IMMERSIVE_DARK_MODE) E_INVALIDARG else 0
        }
        assertEquals(listOf(DWMWA_USE_IMMERSIVE_DARK_MODE to 0, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD to 0), set.take(2))
    }

    @Test
    fun `a COLORREF is blue, green, red from the top`() {
        assertEquals(0x00332211, colorRef(AwtColor(0x11, 0x22, 0x33)))
    }

    @Test
    fun `GNOME and macOS are asked for the dark or light variant`() {
        assertEquals("dark", gtkThemeVariant(dark))
        assertEquals("light", gtkThemeVariant(light))
        assertEquals("NSAppearanceNameDarkAqua", macAppearanceName(dark))
        assertEquals("NSAppearanceNameAqua", macAppearanceName(light))
    }

    @Test
    fun `each platform has a painter, and an unknown one none`() {
        assertNotNull(titleBarPainter("Windows 11"))
        assertNotNull(titleBarPainter("Mac OS X"))
        assertNotNull(titleBarPainter("Linux"))
        assertNull(titleBarPainter("FreeBSD"))
    }

    @Test
    fun `the theme in effect is handed on, and handed on again when it changes`() = runComposeUiTest {
        val handed = mutableListOf<TitleBarStyle>()
        var mode by mutableStateOf(ThemeMode.DARK)
        setContent { ChurchPresenterTheme(mode) { ThemedTitleBars(onStyle = { handed += it }) } }
        waitForIdle()
        assertTrue(handed.last().dark)

        mode = ThemeMode.LIGHT
        waitForIdle()
        assertFalse(handed.last().dark)
    }

    @Test
    fun `the app's own title bars take the theme without a window to paint`() = runComposeUiTest {
        // Headless there are no windows, so whichever platform runs this, nothing native is called.
        setContent { ChurchPresenterTheme(ThemeMode.DARK) { ThemedTitleBars() } }
        waitForIdle()
        SwingUtilities.invokeAndWait {}
        assertTrue(Window.getWindows().isEmpty())
        assertEquals(true, AppTitleBars.style?.dark, "the dark theme reached the app's title bars")
    }

    @Test
    fun `only a window made displayable is one to paint`() {
        val panel = JPanel()
        val shown = HierarchyEvent(
            panel, HierarchyEvent.HIERARCHY_CHANGED, panel, null, HierarchyEvent.SHOWING_CHANGED.toLong(),
        )
        val madeDisplayable = HierarchyEvent(
            panel, HierarchyEvent.HIERARCHY_CHANGED, panel, null, HierarchyEvent.DISPLAYABILITY_CHANGED.toLong(),
        )
        assertNull(madeTitledWindow(shown), "not a displayability change")
        assertNull(madeTitledWindow(madeDisplayable), "a panel, not a window")
        assertNull(madeTitledWindow(ActionEvent(panel, 0, "")), "not a hierarchy event")
    }

    @Test
    fun `each platform's title bars watch for new windows, an unknown platform has none`() {
        val watched = mutableListOf<TitleBars<Window>>()
        assertNull(appTitleBars("FreeBSD", watch = { watched += it }))
        assertTrue(watched.isEmpty())

        val bars = assertNotNull(appTitleBars("Linux", watch = { watched += it }))
        assertSame(bars, watched.single())
        // Headless, there are no windows to paint: the style is still taken for those made later.
        bars.update(dark)
        assertEquals(dark, bars.style)
    }

    @Test
    fun `a painter that fails leaves the title bar and goes on`() {
        val painted = mutableListOf<String>()
        val paint = loggingFailures<String> { window, _ ->
            check(window != "broken") { "no native window" }
            painted += window
        }
        paint("broken", dark)
        paint("main", dark)
        assertEquals(listOf("main"), painted)
    }

    private companion object {
        const val E_INVALIDARG = 0x80070057.toInt()
    }
}
