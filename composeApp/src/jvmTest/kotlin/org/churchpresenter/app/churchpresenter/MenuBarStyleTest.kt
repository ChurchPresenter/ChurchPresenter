@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.awt.Color
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.MatteBorder
import javax.swing.plaf.MenuBarUI
import javax.swing.plaf.PopupMenuUI
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI
import javax.swing.plaf.basic.BasicMenuBarUI
import javax.swing.plaf.basic.BasicMenuItemUI
import javax.swing.plaf.basic.BasicMenuUI
import javax.swing.plaf.basic.BasicPopupMenuSeparatorUI
import javax.swing.plaf.basic.BasicPopupMenuUI
import javax.swing.plaf.basic.BasicSeparatorUI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Swing menu bar painted in the theme: every menu, popup and item takes the theme's colours and
 * the interface size's font, whichever look and feel the platform gave it.
 *
 * Painted into images rather than shown: lightweight Swing components paint headless, which is
 * enough to see that a hovered item and a ticked theme come out in the theme's colours.
 */
class MenuBarStyleTest {

    private val style = MenuBarStyle(
        background = Color(0x10, 0x12, 0x14),
        foreground = Color(0xE4, 0xE6, 0xE7),
        disabled = Color(0x80, 0x80, 0x80),
        selectionBackground = Color(0x2B, 0x3D, 0x50),
        selectionForeground = Color(0xFF, 0xFF, 0xFF),
        popupBackground = Color(0x1C, 0x1E, 0x20),
        outline = Color(0x40, 0x40, 0x40),
        font = Font(Font.DIALOG, Font.PLAIN, 18),
    )

    private class Bar(
        val bar: JMenuBar,
        val view: JMenu,
        val item: JMenuItem,
        val radio: JRadioButtonMenuItem,
        val check: JCheckBoxMenuItem,
        val submenu: JMenu,
    )

    private fun bar(): Bar {
        val bar = JMenuBar()
        val view = JMenu("View")
        val item = JMenuItem("Zoom In")
        val radio = JRadioButtonMenuItem("Dark", true)
        val check = JCheckBoxMenuItem("Always on top", true)
        val submenu = JMenu("Display").apply { add(JMenuItem("Screen 1")) }
        view.add(item)
        view.addSeparator()
        view.add(radio)
        view.add(check)
        view.add(submenu)
        bar.add(view)
        return Bar(bar, view, item, radio, check, submenu)
    }

    private fun JMenuItem.render(): BufferedImage {
        size = preferredSize
        doLayout()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun BufferedImage.has(color: Color): Boolean =
        (0 until width).any { x -> (0 until height).any { y -> getRGB(x, y) == color.rgb } }

    @Test
    fun `the bar, its menus and every kind of item take the theme's colours and font`() {
        val menus = bar()
        styleMenuBar(menus.bar, style)

        assertEquals(style.background, menus.bar.background)
        assertTrue(menus.bar.isOpaque)
        assertIs<MatteBorder>(menus.bar.border, "a hairline under the bar, in the outline colour")
        assertEquals(style.foreground, menus.view.foreground)
        assertEquals(style.popupBackground, menus.view.popupMenu.background)
        for (entry in listOf(menus.item, menus.radio, menus.check, menus.submenu)) {
            assertEquals(style.popupBackground, entry.background, entry.text)
            assertEquals(style.foreground, entry.foreground, entry.text)
            assertEquals(style.font, entry.font, entry.text)
        }
        val separator = menus.view.popupMenu.components.filterIsInstance<JPopupMenu.Separator>().single()
        assertIs<BasicPopupMenuSeparatorUI>(separator.ui)
        assertEquals(style.outline, separator.foreground)
        assertEquals(style.popupBackground, menus.submenu.popupMenu.background, "a submenu's own popup too")
    }

    @Test
    fun `a hovered item is drawn in the theme's selection colour`() {
        val menus = bar()
        styleMenuBar(menus.bar, style)
        menus.item.isArmed = true
        assertTrue(menus.item.render().has(style.selectionBackground))
    }

    @Test
    fun `the ticked theme and a ticked option are marked in the text colour`() {
        val menus = bar()
        styleMenuBar(menus.bar, style)
        assertTrue(menus.radio.render().has(style.foreground), "the radio's dot")
        assertTrue(menus.check.render().has(style.foreground), "the check's mark")
        menus.radio.isSelected = false
        val unticked = menus.radio.render()
        menus.radio.text = ""
        assertTrue(!menus.radio.render().has(style.foreground), "no mark, and no text, draws nothing in it")
        assertTrue(unticked.width > 0)
    }

    @Test
    fun `the same style twice installs nothing new, a new one restyles everything`() {
        val menus = bar()
        styleMenuBar(menus.bar, style)
        val itemUi = menus.item.ui
        val barUi = menus.bar.ui
        styleMenuBar(menus.bar, style)
        assertSame(itemUi, menus.item.ui)
        assertSame(barUi, menus.bar.ui)

        val light = style.copy(background = Color.WHITE, foreground = Color.BLACK, popupBackground = Color.WHITE)
        styleMenuBar(menus.bar, light)
        assertNotSame(itemUi, menus.item.ui)
        assertIs<BasicMenuBarUI>(menus.bar.ui)
        assertEquals(Color.WHITE, menus.bar.background)
        assertEquals(Color.BLACK, menus.item.foreground)
    }

    @Test
    fun `an item added after the bar was styled is styled on the next pass`() {
        val menus = bar()
        styleMenuBar(menus.bar, style)
        val custom = JRadioButtonMenuItem("Custom")
        menus.view.add(custom)
        styleMenuBar(menus.bar, style)
        assertEquals(style.popupBackground, custom.background)
        assertEquals(style.font, custom.font)
    }

    @Test
    fun `a key Swing names in words is shown as its symbol, and stays bound`() {
        val menus = bar()
        val key = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK)
        val letter = KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)
        val newItem = JMenuItem("New").apply { accelerator = letter }
        menus.item.accelerator = key
        menus.view.add(newItem)

        for (pass in listOf(style, style, style.copy(foreground = Color.BLACK))) {
            styleMenuBar(menus.bar, pass)
            val shown = menus.item.accelerator
            assertEquals('=', shown.keyChar, "labelled with the symbol")
            assertEquals(KeyEvent.VK_UNDEFINED, shown.keyCode)
            assertEquals(InputEvent.CTRL_DOWN_MASK, shown.modifiers and InputEvent.CTRL_DOWN_MASK)
            val keys = assertNotNull(SwingUtilities.getUIInputMap(menus.item, JComponent.WHEN_IN_FOCUSED_WINDOW))
            assertNotNull(keys.get(key), "Ctrl and the = key still press it")
            assertNull(keys.get(shown), "a typed = does not press it a second time")
            assertSame(letter, newItem.accelerator, "a letter key is left as it is")
        }
    }

    @Test
    fun `a menu whose look and feel was replaced is styled again on the next pass`() {
        val menus = bar()
        styleMenuBar(menus.bar, style)
        val separator = menus.view.popupMenu.components.filterIsInstance<JPopupMenu.Separator>().single()
        menus.bar.setUI(object : MenuBarUI() {})
        menus.view.setUI(BasicMenuUI())
        menus.view.popupMenu.setUI(object : PopupMenuUI() {})
        menus.item.setUI(BasicMenuItemUI())
        menus.check.setUI(BasicCheckBoxMenuItemUI())
        menus.submenu.setUI(BasicMenuUI())
        separator.setUI(BasicSeparatorUI())

        styleMenuBar(menus.bar, style)

        assertIs<BasicMenuBarUI>(menus.bar.ui)
        assertIs<BasicPopupMenuUI>(menus.view.popupMenu.ui)
        assertIs<BasicPopupMenuSeparatorUI>(separator.ui)
        for (entry in listOf(menus.view, menus.item, menus.check, menus.submenu)) {
            assertTrue(entry.ui.javaClass.simpleName.startsWith("Themed"), entry.text)
        }
    }

    @Test
    fun `a submenu's arrow is drawn in the text colour`() {
        val menus = bar()
        styleMenuBar(menus.bar, style)
        menus.submenu.text = ""
        assertTrue(menus.submenu.render().has(style.foreground))
    }

    @Test
    fun `a typed-character accelerator, or one with no binding, is left as it is`() {
        val menus = bar()
        val typed = KeyStroke.getKeyStroke('x')
        menus.item.accelerator = typed
        styleMenuBar(menus.bar, style)
        assertSame(typed, menus.item.accelerator)

        // With no look and feel there is no binding to move, and the label still reads right.
        val unbound = JMenuItem("Loose").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK)
            setUI(null)
        }
        showKeySymbol(unbound)
        assertEquals('-', unbound.accelerator.keyChar)
    }

    @Test
    fun `the menus' font grows with the interface, from a plain one where the look and feel has none`() {
        val base = Font(Font.SERIF, Font.PLAIN, 14)
        assertEquals(21f, menuFont(1.5f, base).size2D)
        val fallback = menuFont(2f, lookAndFeelFont = null)
        assertEquals(Font.DIALOG, fallback.name)
        assertEquals(24f, fallback.size2D)
    }

    @Test
    fun `the theme in effect is the menu bar's style`() = runComposeUiTest {
        var styled: MenuBarStyle? = null
        var mode by mutableStateOf(ThemeMode.DARK)
        setContent { ChurchPresenterTheme(mode) { styled = themedMenuBarStyle() } }
        waitForIdle()
        val dark = assertNotNull(styled)
        mode = ThemeMode.LIGHT
        waitForIdle()
        val light = assertNotNull(styled)

        assertTrue(dark.background.red < light.background.red, "the dark theme's bar is the darker")
        assertTrue(dark.foreground.red > light.foreground.red, "and its text the lighter")
    }

    @Test
    fun `what is not a menu or an item is passed over`() {
        val menus = bar()
        val glue = JLabel("not a menu")
        val note = JLabel("not an item")
        menus.bar.add(glue)
        menus.view.popupMenu.add(note)
        val noteForeground = note.foreground

        styleMenuBar(menus.bar, style)

        assertEquals(style.popupBackground, menus.item.background, "the menus around them are styled")
        assertEquals(noteForeground, note.foreground)
    }

    @Test
    fun `an accelerator changed after its symbol was shown is followed`() {
        val menus = bar()
        menus.item.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK)
        styleMenuBar(menus.bar, style)
        val minus = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK)
        menus.item.accelerator = minus

        styleMenuBar(menus.bar, style)

        assertEquals('-', menus.item.accelerator.keyChar)
        val keys = assertNotNull(SwingUtilities.getUIInputMap(menus.item, JComponent.WHEN_IN_FOCUSED_WINDOW))
        assertNotNull(keys.get(minus), "the new key is the one bound")
    }

    @Test
    fun `the main window's bar is styled in the theme after each composition, and left alone on macOS`() =
        runComposeUiTest {
            val windows = bar()
            val mac = bar()
            setContent {
                ChurchPresenterTheme(ThemeMode.DARK) {
                    ThemedMenuBar(menuBar = { windows.bar }, macOs = false)
                    ThemedMenuBar(menuBar = { mac.bar }, macOs = true)
                }
            }
            waitForIdle()
            SwingUtilities.invokeAndWait {}

            assertIs<BasicMenuBarUI>(windows.bar.ui)
            assertTrue(windows.item.ui.javaClass.simpleName.startsWith("Themed"))
            assertNull(mac.bar.getClientProperty("churchpresenter.menuBarStyle"), "macOS keeps its own menus")
        }
}
