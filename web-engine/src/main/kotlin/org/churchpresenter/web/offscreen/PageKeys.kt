package org.churchpresenter.web.offscreen

import org.cef.browser.CefBrowserHeadless
import java.awt.KeyEventDispatcher
import java.awt.Window
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Sends the page window's key events to [target] while [focused] says the page has focus, and keeps
 * them from the rest of the app, as a text field would. [inWindow] says whether an event belongs to
 * that window. Alt+F4 is left to the window, so it still closes the app on Windows.
 *
 * The raw AWT event is taken rather than Compose's: Compose's key events carry no `KEY_TYPED`
 * (Chromium's char event), and JCEF's native code reads the AWT event's scancode and raw code. Each
 * platform's native key path then needs one thing put right, done by [keyEventFor] and [send].
 */
internal class PageKeys(
    private val target: KeyTarget,
    private val inWindow: (KeyEvent) -> Boolean,
    private val focused: () -> Boolean,
    private val os: String = System.getProperty("os.name"),
) : KeyEventDispatcher {
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (!focused() || !inWindow(e) || closesWindow(e)) return false
        send(e)
        return true
    }

    private fun send(e: KeyEvent) {
        val linux = os.startsWith("Linux")
        val clipboard = if (linux) LinuxClipboard.actionOf(e) else null
        val typed = macTypedText(e, os)
        val paste = if (clipboard == LinuxClipboard.Action.PASTE) LinuxClipboard.pasteText() else null
        when {
            typed != null -> target.run(insertTextScript(typed.toString()))
            paste != null -> target.run(insertTextScript(paste))
            clipboard == LinuxClipboard.Action.CUT -> target.run(LinuxClipboard.readScript(clipboard))
            clipboard == LinuxClipboard.Action.COPY -> {
                target.key(keyEventFor(e, os))
                target.run(LinuxClipboard.readScript(clipboard))
            }
            else -> target.key(keyEventFor(e, os))
        }
    }
}

/** Whether [e] is Alt+F4, which the window answers by closing. */
internal fun closesWindow(e: KeyEvent): Boolean =
    e.keyCode == KeyEvent.VK_F4 && e.modifiersEx and KeyEvent.ALT_DOWN_MASK != 0

/** Where [PageKeys] sends: a key event to the page, or a script to its focused frame. */
internal interface KeyTarget {
    fun key(e: KeyEvent)
    fun run(script: String)
}

/** [browser] as a [KeyTarget]. */
internal class BrowserKeys(private val browser: CefBrowserHeadless) : KeyTarget {
    override fun key(e: KeyEvent) = browser.key(e)
    override fun run(script: String) {
        browser.focusedFrame?.executeJavaScript(script, "", 0)
    }
}

/** Whether [e] comes from a component inside [window]. */
internal fun inWindow(e: KeyEvent, window: Window?): Boolean =
    window != null && SwingUtilities.getWindowAncestor(e.component) === window

/** [e] as JCEF's native key path on [os] needs it; see [linuxKeyEvent] and [windowsKeyEvent]. */
internal fun keyEventFor(e: KeyEvent, os: String): KeyEvent = when {
    os.startsWith("Linux") -> linuxKeyEvent(e)
    os.startsWith("Windows") -> windowsKeyEvent(e)
    else -> e
}

/**
 * [e] as JCEF's native Windows key path needs it. That path sends a typed key's character as the
 * `WM_CHAR` code, and AWT types Enter as a line feed, which Chromium ignores, so Enter goes as the
 * carriage return Windows itself sends. Ctrl+Enter stays a line feed, as it is in Windows. Changed in
 * place, so the event keeps the private scancode JCEF reads.
 */
internal fun windowsKeyEvent(e: KeyEvent): KeyEvent {
    val ctrl = e.modifiersEx and KeyEvent.CTRL_DOWN_MASK != 0
    if (e.id == KeyEvent.KEY_TYPED && e.keyChar == '\n' && !ctrl) e.keyChar = '\r'
    return e
}

/**
 * The character [e] types that has to go in as an edit on [os], or null. JCEF's native macOS key
 * path drops a `KEY_TYPED` whose character is outside ASCII (Cyrillic arrives from AWT intact and
 * never reaches the page), so on macOS such characters are inserted by [insertTextScript] instead. That
 * does not reach a page reading raw key events (a canvas game), and is not undone with the field's
 * other typing.
 */
internal fun macTypedText(e: KeyEvent, os: String): Char? =
    e.keyChar.takeIf { os.startsWith("Mac") && e.id == KeyEvent.KEY_TYPED && it.code > LAST_ASCII }

/** The script that types [text] into the page's focused field: `\uXXXX` escapes, so nothing needs quoting. */
internal fun insertTextScript(text: String): String =
    "document.execCommand('insertText', false, '${text.map { "\\u%04x".format(it.code) }.joinToString("")}')"

private const val LAST_ASCII = 0x7f
