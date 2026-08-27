package org.churchpresenter.app.churchpresenter.utils

import java.awt.HeadlessException
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * The system clipboard, for the app's own copy buttons.
 *
 * The clipboard is a single OS-wide lock that any running application can be holding, so every call
 * into it can fail for a reason that has nothing to do with this app and is over a moment later.
 * Windows raises `IllegalStateException: cannot open system clipboard` from
 * `WClipboard.openClipboard0` when it cannot take that lock — which reached Sentry as a fatal, and
 * would have taken any of the app's six unguarded copy buttons with it had one been clicked at the
 * wrong moment.
 *
 * So neither call here throws. A copy that could not happen is one the operator repeats; it is not
 * worth a report, and it is certainly not worth ending whatever they were doing.
 */
object SystemClipboard {

    /** Puts [text] on the clipboard. False when the clipboard could not be taken. */
    fun copy(text: String): Boolean = try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    } catch (_: IllegalStateException) {
        false
    } catch (_: HeadlessException) {
        false
    }

    /** The clipboard's text, or null when it holds none or could not be read. */
    fun paste(): String? = try {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (_: Exception) {
        // Also UnsupportedFlavorException (the clipboard holds an image) and IOException, both of
        // which mean the same thing here: there is no text to paste.
        null
    }
}
