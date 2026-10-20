package org.churchpresenter.app.churchpresenter

import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * Whether [event] is the character a Ctrl shortcut leaves behind. On X11, Ctrl and = arrives as the
 * key press the menu's Zoom In takes, and then as a typed "=" a focused text field would insert as
 * well. Ctrl with a letter types a control character, which no field inserts, so only printable
 * characters are caught; Ctrl with Alt is AltGr on Windows, how some layouts type @ or €.
 */
internal fun isShortcutResidue(event: KeyEvent): Boolean =
    event.id == KeyEvent.KEY_TYPED &&
        event.isControlDown && !event.isAltDown && !event.isAltGraphDown && !event.isMetaDown &&
        !event.keyChar.isISOControl()

/**
 * Drops [isShortcutResidue]'s characters before any window sees them, on Windows and Linux. macOS
 * is left alone: its shortcuts are on Command, which types nothing.
 */
internal fun installShortcutResidueFilter(
    osName: String = System.getProperty("os.name", ""),
    addDispatcher: (KeyEventDispatcher) -> Unit = {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(it)
    },
) {
    if (osName.lowercase().contains("mac")) return
    addDispatcher(KeyEventDispatcher(::isShortcutResidue))
}
