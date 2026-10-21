package org.churchpresenter.web.offscreen

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.net.URLDecoder
import javax.swing.Timer

/**
 * Copying out of the page on Linux.
 *
 * On Ctrl+C a windowless Chromium on X11 takes ownership of the clipboard but serves no text from it,
 * so the clipboard ends up empty; pasting into the page works. So on Ctrl+C (or Ctrl+Insert) the key
 * still goes to the page, whose own `copy` handlers run, and a script reads the selection and reports
 * it in a console message; [handle] then puts it on the system clipboard from Java, a moment later so
 * it lands after Chromium's claim. Ctrl+X is done by the script alone (read, then delete the
 * selection), since a cut sent to the page first would leave nothing to read. Text in password fields
 * is never read.
 */
internal object LinuxClipboard {
    enum class Action { COPY, CUT }

    /** What [e] asks of the clipboard, or null for any other key. */
    fun actionOf(e: KeyEvent): Action? {
        if (e.id != KeyEvent.KEY_PRESSED) return null
        val mods = e.modifiersEx and (KeyEvent.CTRL_DOWN_MASK or KeyEvent.ALT_DOWN_MASK or KeyEvent.META_DOWN_MASK)
        if (mods != KeyEvent.CTRL_DOWN_MASK) return null
        return when (e.keyCode) {
            KeyEvent.VK_C, KeyEvent.VK_INSERT -> Action.COPY
            KeyEvent.VK_X -> Action.CUT
            else -> null
        }
    }

    /**
     * The script that reads the focused frame's selection into a console message for [handle]; for
     * [Action.CUT], it deletes the selection too.
     */
    fun readScript(action: Action): String {
        val cut = if (action == Action.CUT) "if(t)document.execCommand('delete');" else ""
        return "(function(){var e=document.activeElement,t='';" +
            "if(e&&(e.tagName==='TEXTAREA'||(e.tagName==='INPUT'&&" +
            "/^(text|search|url|tel|email|number)$/i.test(e.type))))" +
            "{t=e.value.substring(e.selectionStart,e.selectionEnd)}" +
            "else if(!(e&&e.tagName==='INPUT')){t=String(getSelection())}" +
            "if(t)console.log('$PREFIX'+encodeURIComponent(t));$cut})()"
    }

    /** The text a console [message] from [readScript] carries, or null for any other message. */
    fun textOf(message: String): String? =
        if (message.startsWith(PREFIX)) URLDecoder.decode(message.removePrefix(PREFIX), Charsets.UTF_8) else null

    /** Takes a console [message] from [readScript] and puts its text on the clipboard; false for any other message. */
    fun handle(message: String): Boolean {
        val text = textOf(message) ?: return false
        Timer(CLAIM_AFTER_MS) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }.apply { isRepeats = false }.start()
        return true
    }

    private const val PREFIX = "__churchpresenter_clipboard__:"

    /** Long enough for Chromium to have taken the clipboard before Java takes it back. */
    private const val CLAIM_AFTER_MS = 150
}
