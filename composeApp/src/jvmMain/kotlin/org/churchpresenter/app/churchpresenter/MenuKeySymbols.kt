package org.churchpresenter.app.churchpresenter

import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/** The key an item's accelerator stands for, once [showKeySymbol] has swapped it for a symbol. */
private const val KEY_PROPERTY = "churchpresenter.acceleratorKey"

/** The keys Swing names in words ("Ctrl+Equals"), and the symbol each is shown as instead. */
private val KEY_SYMBOLS = mapOf(
    KeyEvent.VK_EQUALS to '=',
    KeyEvent.VK_MINUS to '-',
    KeyEvent.VK_PLUS to '+',
    KeyEvent.VK_COMMA to ',',
    KeyEvent.VK_PERIOD to '.',
    KeyEvent.VK_SLASH to '/',
    KeyEvent.VK_BACK_SLASH to '\\',
    KeyEvent.VK_SEMICOLON to ';',
    KeyEvent.VK_QUOTE to '\'',
    KeyEvent.VK_BACK_QUOTE to '`',
    KeyEvent.VK_OPEN_BRACKET to '[',
    KeyEvent.VK_CLOSE_BRACKET to ']',
)

/**
 * Shows [item]'s accelerator as the symbol on the key — "Ctrl+=" — where Swing would spell the key's
 * name out, as "Ctrl+Equals". The label is Swing's own, built from the accelerator, so the item is
 * given a typed-character accelerator that reads right, and the key it stands for stays bound in
 * its place; the typed one is unbound, as that character can also arrive as a typed key.
 * Idempotent, and run again after the item's look and feel is replaced, which binds its accelerator
 * afresh.
 */
internal fun showKeySymbol(item: JMenuItem) {
    val shown = item.accelerator ?: return
    val saved = item.getClientProperty(KEY_PROPERTY) as? KeyStroke
    val key = saved?.takeIf { shown == symbolStroke(it) } ?: shown
    val symbolStroke = symbolStroke(key) ?: return
    item.putClientProperty(KEY_PROPERTY, key)
    item.accelerator = symbolStroke
    val keys = SwingUtilities.getUIInputMap(item, JComponent.WHEN_IN_FOCUSED_WINDOW) ?: return
    val action = keys.get(symbolStroke) ?: keys.get(key) ?: return
    keys.remove(symbolStroke)
    keys.put(key, action)
}

/** The typed-character stroke that labels [key] with its symbol, or null for a key Swing names well. */
private fun symbolStroke(key: KeyStroke): KeyStroke? {
    if (key.keyEventType != KeyEvent.KEY_PRESSED) return null
    val symbol = KEY_SYMBOLS[key.keyCode] ?: return null
    return KeyStroke.getKeyStroke(symbol, key.modifiers)
}
