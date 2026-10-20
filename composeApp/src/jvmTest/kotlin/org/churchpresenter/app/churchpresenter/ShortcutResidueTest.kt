package org.churchpresenter.app.churchpresenter

import java.awt.KeyEventDispatcher
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which typed characters a Ctrl shortcut leaves behind, and are dropped before a text field. */
class ShortcutResidueTest {

    private val source = JPanel()

    private fun typed(char: Char, modifiers: Int) =
        KeyEvent(source, KeyEvent.KEY_TYPED, 0L, modifiers, KeyEvent.VK_UNDEFINED, char)

    @Test
    fun `ctrl with a symbol key types nothing`() {
        assertTrue(isShortcutResidue(typed('=', InputEvent.CTRL_DOWN_MASK)))
        assertTrue(isShortcutResidue(typed('-', InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)))
    }

    @Test
    fun `plain typing, altgr and control characters go through`() {
        assertFalse(isShortcutResidue(typed('=', 0)), "a plain =")
        assertFalse(isShortcutResidue(typed('@', InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK)), "AltGr")
        assertFalse(isShortcutResidue(typed('€', InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK)))
        assertFalse(isShortcutResidue(typed('\u0001', InputEvent.CTRL_DOWN_MASK)), "Ctrl+A's control character")
        assertFalse(isShortcutResidue(typed('=', InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK)), "Ctrl+Meta")
    }

    @Test
    fun `a key press is never caught, only the character it types`() {
        val press = KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_EQUALS, '=')
        assertFalse(isShortcutResidue(press))
    }

    @Test
    fun `windows and linux drop the residue before any window, macOS gets no filter`() {
        val installed = mutableListOf<KeyEventDispatcher>()
        installShortcutResidueFilter(osName = "Mac OS X", addDispatcher = { installed += it })
        assertTrue(installed.isEmpty())

        installShortcutResidueFilter(osName = "Linux", addDispatcher = { installed += it })
        val filter = installed.single()
        assertTrue(filter.dispatchKeyEvent(typed('=', InputEvent.CTRL_DOWN_MASK)), "consumed")
        assertFalse(filter.dispatchKeyEvent(typed('=', 0)))
    }
}
