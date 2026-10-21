package org.churchpresenter.web.offscreen

import java.awt.Canvas
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The key dispatcher: only while the page has focus and the key is its window's does it take a key,
 * and then it sends what each platform needs, the typed edit on macOS and the copy-out script on
 * Linux included.
 */
class PageKeysTest {
    private class Recording : KeyTarget {
        val keys = mutableListOf<KeyEvent>()
        val scripts = mutableListOf<String>()
        override fun key(e: KeyEvent) {
            keys += e
        }
        override fun run(script: String) {
            scripts += script
        }
    }

    private val target = Recording()
    private var focused = true
    private var mine = true

    private fun keys(os: String) = PageKeys(target, { mine }, { focused }, os)

    private fun key(id: Int, code: Int, char: Char, mods: Int = 0) = KeyEvent(
        Canvas(), id, 0, mods,
        if (id == KeyEvent.KEY_TYPED) KeyEvent.VK_UNDEFINED else code,
        char,
        if (id == KeyEvent.KEY_TYPED) KeyEvent.KEY_LOCATION_UNKNOWN else KeyEvent.KEY_LOCATION_STANDARD,
    )

    @Test
    fun `a key is left to the app while the page has no focus or it is another window's`() {
        focused = false
        assertFalse(keys("Mac OS X").dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a')))
        focused = true
        mine = false
        assertFalse(keys("Mac OS X").dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a')))
        assertTrue(target.keys.isEmpty())
    }

    @Test
    fun `a focused page takes the key and sends it on`() {
        val e = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a')
        assertTrue(keys("Mac OS X").dispatchKeyEvent(e))
        assertEquals(listOf(e), target.keys)
    }

    @Test
    fun `on macOS a non-ASCII character goes as an edit, not a key`() {
        keys("Mac OS X").dispatchKeyEvent(key(KeyEvent.KEY_TYPED, 0, 'є'))
        assertTrue(target.keys.isEmpty())
        assertEquals(listOf(insertTextScript('є')), target.scripts)
    }

    @Test
    fun `on Linux a copy sends the key, then reads the selection out`() {
        keys("Linux").dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_C, 'c', KeyEvent.CTRL_DOWN_MASK))
        assertEquals('c'.code, target.keys.single().keyChar.code)
        assertEquals(listOf(LinuxClipboard.readScript(LinuxClipboard.Action.COPY)), target.scripts)
    }

    @Test
    fun `on Linux a cut is done by the script alone`() {
        keys("Linux").dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_X, 'x', KeyEvent.CTRL_DOWN_MASK))
        assertTrue(target.keys.isEmpty())
        assertEquals(listOf(LinuxClipboard.readScript(LinuxClipboard.Action.CUT)), target.scripts)
    }

    @Test
    fun `a key from no window belongs to none`() {
        assertFalse(inWindow(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a'), null))
    }
}
