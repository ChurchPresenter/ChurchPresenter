package org.churchpresenter.web.offscreen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.awt.Canvas
import java.awt.Point
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Input as each platform's native JCEF path needs it: Linux keysyms in place of characters,
 * Windows' Enter as a carriage return, macOS's non-ASCII typing as an edit, the Linux copy-out
 * script; the wheel's per-platform scale and carried remainder; and where a pointer lands on a
 * letterboxed page.
 */
class OffscreenInputTest {
    private val source = Canvas()

    private fun key(id: Int, code: Int, char: Char, mods: Int = 0, location: Int = KeyEvent.KEY_LOCATION_STANDARD) =
        KeyEvent(source, id, 0, mods, if (id == KeyEvent.KEY_TYPED) KeyEvent.VK_UNDEFINED else code, char,
            if (id == KeyEvent.KEY_TYPED) KeyEvent.KEY_LOCATION_UNKNOWN else location)

    @Test
    fun `on Linux a key with no character goes with its keysym, not Delete's`() {
        val home = linuxKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_HOME, KeyEvent.CHAR_UNDEFINED))
        assertEquals(0xff50, home.keyChar.code)
    }

    @Test
    fun `on Linux a Ctrl chord goes with the letter's keysym`() {
        val ctrlA = linuxKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, '\u0001', KeyEvent.CTRL_DOWN_MASK))
        assertEquals('a'.code, ctrlA.keyChar.code)
    }

    @Test
    fun `on Linux a Cyrillic press goes with its key's keysym and a typed letter goes as it is`() {
        val press = linuxKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_G, 'п'))
        assertEquals('g'.code, press.keyChar.code)
        val typed = key(KeyEvent.KEY_TYPED, 0, 'п')
        assertSame(typed, linuxKeyEvent(typed))
    }

    @Test
    fun `on Linux a typed Enter goes as Return's keysym`() {
        assertEquals(0xff0d, linuxKeyEvent(key(KeyEvent.KEY_TYPED, 0, '\n')).keyChar.code)
    }

    @Test
    fun `on Linux a Latin character is its own keysym and a key with none goes as 0`() {
        val shifted = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'A', KeyEvent.SHIFT_DOWN_MASK)
        assertSame(shifted, linuxKeyEvent(shifted), "already its keysym")
        val unknown = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_UNDEFINED, KeyEvent.CHAR_UNDEFINED)
        assertEquals(0, linuxKeyEvent(unknown).keyChar.code)
        assertEquals(' '.code, keysymFor(KeyEvent.VK_SPACE, KeyEvent.KEY_LOCATION_STANDARD))
        assertEquals(0xffe4, keysymFor(KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_RIGHT))
    }

    @Test
    fun `the Linux keysyms cover the function keys, the keypad and both sides of a modifier`() {
        assertEquals(0xffbe, keysymFor(KeyEvent.VK_F1, KeyEvent.KEY_LOCATION_STANDARD))
        assertEquals(0xffca, keysymFor(KeyEvent.VK_F13, KeyEvent.KEY_LOCATION_STANDARD))
        assertEquals(0xffb7, keysymFor(KeyEvent.VK_NUMPAD7, KeyEvent.KEY_LOCATION_NUMPAD))
        assertEquals(0xffe1, keysymFor(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT))
        assertEquals(0xffe2, keysymFor(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT))
        assertEquals('5'.code, keysymFor(KeyEvent.VK_5, KeyEvent.KEY_LOCATION_STANDARD))
        assertNull(keysymFor(KeyEvent.VK_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD))
    }

    @Test
    fun `on Windows Enter types a carriage return and Ctrl+Enter keeps its line feed`() {
        val enter = key(KeyEvent.KEY_TYPED, 0, '\n')
        assertSame(enter, windowsKeyEvent(enter), "changed in place, so the scancode JCEF reads stays")
        assertEquals('\r', enter.keyChar)
        assertEquals('\n', windowsKeyEvent(key(KeyEvent.KEY_TYPED, 0, '\n', KeyEvent.CTRL_DOWN_MASK)).keyChar)
    }

    @Test
    fun `each platform's fix applies on that platform only`() {
        assertEquals('\r', keyEventFor(key(KeyEvent.KEY_TYPED, 0, '\n'), "Windows 11").keyChar)
        assertEquals(0xff0d, keyEventFor(key(KeyEvent.KEY_TYPED, 0, '\n'), "Linux").keyChar.code)
        assertEquals('\n', keyEventFor(key(KeyEvent.KEY_TYPED, 0, '\n'), "Mac OS X").keyChar)
    }

    @Test
    fun `on macOS a non-ASCII character is typed as an edit and ASCII is not`() {
        assertEquals('ї', macTypedText(key(KeyEvent.KEY_TYPED, 0, 'ї'), "Mac OS X"))
        assertNull(macTypedText(key(KeyEvent.KEY_TYPED, 0, 'a'), "Mac OS X"))
        assertNull(macTypedText(key(KeyEvent.KEY_TYPED, 0, 'ї'), "Windows 11"))
        assertEquals("document.execCommand('insertText', false, '\\u0457')", insertTextScript('ї'))
    }

    @Test
    fun `Linux copy and cut are Ctrl+C, Ctrl+Insert and Ctrl+X, not with Alt or Meta`() {
        fun pressed(code: Int, char: Char, mods: Int = KeyEvent.CTRL_DOWN_MASK) =
            LinuxClipboard.actionOf(key(KeyEvent.KEY_PRESSED, code, char, mods))
        assertEquals(LinuxClipboard.Action.COPY, pressed(KeyEvent.VK_C, 'c'))
        assertEquals(LinuxClipboard.Action.COPY, pressed(KeyEvent.VK_INSERT, KeyEvent.CHAR_UNDEFINED))
        assertEquals(LinuxClipboard.Action.CUT, pressed(KeyEvent.VK_X, 'x'))
        assertNull(pressed(KeyEvent.VK_C, 'c', KeyEvent.CTRL_DOWN_MASK or KeyEvent.ALT_DOWN_MASK))
        assertNull(LinuxClipboard.actionOf(key(KeyEvent.KEY_RELEASED, KeyEvent.VK_C, 'c', KeyEvent.CTRL_DOWN_MASK)))
        assertNull(LinuxClipboard.actionOf(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_V, 'v', KeyEvent.CTRL_DOWN_MASK)))
    }

    @Test
    fun `the copy-out script reports the selection, and only a cut deletes it`() {
        val copy = LinuxClipboard.readScript(LinuxClipboard.Action.COPY)
        val cut = LinuxClipboard.readScript(LinuxClipboard.Action.CUT)
        assertTrue("console.log(" in copy && "execCommand('delete')" !in copy)
        assertTrue("execCommand('delete')" in cut)
    }

    @Test
    fun `a console message carries the copied text back, and any other is not taken`() {
        assertEquals("héllo world", LinuxClipboard.textOf("__churchpresenter_clipboard__:h%C3%A9llo%20world"))
        assertNull(LinuxClipboard.textOf("an ordinary page message"))
        assertEquals(false, LinuxClipboard.handle("an ordinary page message"))
    }

    @Test
    fun `a wheel notch scrolls as Chrome does on each platform`() {
        // JCEF multiplies by 1 on macOS and Linux and by 2.5 on Windows, negated, and drops the fraction.
        assertEquals(-40, WheelScaler("Mac OS X").rotationFor(1.0))
        assertEquals(-40, WheelScaler("Windows 11").rotationFor(1.0))
        assertEquals(-53, WheelScaler("Linux").rotationFor(1.0))
        assertEquals(1, WheelScaler("Linux").scrollAmount)
        assertNull(WheelScaler("Windows 11").scrollAmount)
    }

    @Test
    fun `small touchpad steps add up rather than being lost`() {
        val wheel = WheelScaler("Windows 11")
        val sent = (1..10).sumOf { wheel.rotationFor(0.01) }
        // Ten steps of one pixel each: 10 px, sent in units JCEF scales by 2.5.
        assertEquals(-4, sent)
    }

    @Test
    fun `a pointer lands on the matching point of the letterboxed page`() {
        // A 16:9 page in a square box sits in the middle third of its height.
        val box = Size(900f, 900f)
        assertEquals(Point(960, 540), pagePoint(Offset(450f, 450f), box, 1920, 1080))
        assertEquals(Point(0, 0), pagePoint(Offset(0f, 0f), box, 1920, 1080), "the bar above clamps to the top")
        assertEquals(Point(1919, 1079), pagePoint(Offset(900f, 900f), box, 1920, 1080))
    }

    @Test
    fun `a page is centred in its box at its own aspect`() {
        val rect = fitRect(Size(1000f, 1000f), 1920, 1080)
        val got = listOf(rect.left, rect.top, rect.width, rect.height)
        listOf(0f, 218.75f, 1000f, 562.5f).zip(got).forEach { (want, got) ->
            assertEquals(want, got, absoluteTolerance = 0.001f)
        }
    }

    @Test
    fun `a page is laid out 1920 DIPs wide on a wide output and at its own width on a narrow one`() {
        val (fourK, fourKScale) = OffscreenPage.layoutFor(androidx.compose.ui.unit.IntSize(3840, 2160))
        assertEquals(androidx.compose.ui.unit.IntSize(1920, 1080) to 2.0, fourK to fourKScale)
        val (window, windowScale) = OffscreenPage.layoutFor(androidx.compose.ui.unit.IntSize(960, 512))
        assertEquals(androidx.compose.ui.unit.IntSize(960, 512) to 1.0, window to windowScale)
    }
}
