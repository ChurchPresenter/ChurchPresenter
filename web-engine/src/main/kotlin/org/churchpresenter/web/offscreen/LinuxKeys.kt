package org.churchpresenter.web.offscreen

import java.awt.event.KeyEvent

/**
 * [e] as JCEF's native Linux key path needs it.
 *
 * That path takes a pressed or released key's X keysym from its `keyChar`, and maps only Backspace,
 * Delete, the arrows, Enter, Escape and Tab from the key code. A key with no character (Home, End,
 * Page Up, a function key, a bare modifier) carries `CHAR_UNDEFINED`, 0xFFFF, which is the keysym of
 * Delete, so it deletes. A Ctrl chord carries a control character and a Cyrillic letter its Unicode
 * value, neither a keysym, so the page gets key code 0 and Ctrl+A, C and V do nothing. So a press or
 * a release is re-made with the keysym of its key in place of its character. Typed characters, which
 * insert the text, go through untouched, and Latin characters are their own keysyms already, except
 * Enter: AWT types it as a line feed, which the page gets as charCode 10 and ignores, so it goes as
 * Return's keysym, which JCEF turns into the carriage return the page expects.
 *
 * Chromium also reads the keysym as a hardware key code for the event's `code`, so `code` is still
 * wrong on Linux; `key`, `keyCode` and shortcuts are right.
 */
internal fun linuxKeyEvent(e: KeyEvent): KeyEvent {
    if (e.id == KeyEvent.KEY_TYPED) return if (e.keyChar == '\n') e.withChar(XK_RETURN) else e
    val char = e.keyChar.code
    val latin = char in PRINTABLE_ASCII || char in PRINTABLE_LATIN1
    val ctrl = e.modifiersEx and KeyEvent.CTRL_DOWN_MASK != 0
    val keysym = if (latin && !ctrl) char else keysymFor(e.keyCode, e.keyLocation) ?: 0
    return if (keysym == char) e else e.withChar(keysym)
}

private fun KeyEvent.withChar(keysym: Int) =
    KeyEvent(component, id, `when`, modifiersEx, keyCode, keysym.toChar(), keyLocation)

/** The X keysym of AWT key [code] on a US layout, or null for a key with none here. */
internal fun keysymFor(code: Int, location: Int): Int? {
    val right = location == KeyEvent.KEY_LOCATION_RIGHT
    return when (code) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> code + ('a' - 'A')
        in KeyEvent.VK_0..KeyEvent.VK_9 -> code
        in KeyEvent.VK_F1..KeyEvent.VK_F12 -> XK_F1 + (code - KeyEvent.VK_F1)
        in KeyEvent.VK_F13..KeyEvent.VK_F24 -> XK_F13 + (code - KeyEvent.VK_F13)
        in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9 -> XK_KP_0 + (code - KeyEvent.VK_NUMPAD0)
        else -> SIDED[code]?.let { (left, rightSym) -> if (right) rightSym else left } ?: FIXED[code]
    }
}

private const val XK_F1 = 0xffbe
private const val XK_F13 = 0xffca
private const val XK_KP_0 = 0xffb0
private const val XK_RETURN = 0xff0d

/** Characters that are their own keysyms: printable ASCII and printable Latin-1. */
private val PRINTABLE_ASCII = 0x20..0x7e
private val PRINTABLE_LATIN1 = 0xa0..0xff

/** The left and right keysyms of the modifier keys. */
private val SIDED = mapOf(
    KeyEvent.VK_SHIFT to (0xffe1 to 0xffe2),
    KeyEvent.VK_CONTROL to (0xffe3 to 0xffe4),
    KeyEvent.VK_ALT to (0xffe9 to 0xffea),
    KeyEvent.VK_META to (0xffe7 to 0xffe8),
    KeyEvent.VK_WINDOWS to (0xffeb to 0xffec),
)

/** Keysyms for keys that have one regardless of side. */
private val FIXED = mapOf(
    KeyEvent.VK_HOME to 0xff50,
    KeyEvent.VK_LEFT to 0xff51,
    KeyEvent.VK_UP to 0xff52,
    KeyEvent.VK_RIGHT to 0xff53,
    KeyEvent.VK_DOWN to 0xff54,
    KeyEvent.VK_PAGE_UP to 0xff55,
    KeyEvent.VK_PAGE_DOWN to 0xff56,
    KeyEvent.VK_END to 0xff57,
    KeyEvent.VK_INSERT to 0xff63,
    KeyEvent.VK_DELETE to 0xffff,
    KeyEvent.VK_BACK_SPACE to 0xff08,
    KeyEvent.VK_TAB to 0xff09,
    KeyEvent.VK_ENTER to 0xff0d,
    KeyEvent.VK_ESCAPE to 0xff1b,
    KeyEvent.VK_PAUSE to 0xff13,
    KeyEvent.VK_SCROLL_LOCK to 0xff14,
    KeyEvent.VK_PRINTSCREEN to 0xff61,
    KeyEvent.VK_CONTEXT_MENU to 0xff67,
    KeyEvent.VK_NUM_LOCK to 0xff7f,
    KeyEvent.VK_CAPS_LOCK to 0xffe5,
    KeyEvent.VK_ALT_GRAPH to 0xfe03,
    KeyEvent.VK_MULTIPLY to 0xffaa,
    KeyEvent.VK_ADD to 0xffab,
    KeyEvent.VK_SEPARATOR to 0xffac,
    KeyEvent.VK_SUBTRACT to 0xffad,
    KeyEvent.VK_DECIMAL to 0xffae,
    KeyEvent.VK_DIVIDE to 0xffaf,
    KeyEvent.VK_SPACE to ' '.code,
    KeyEvent.VK_MINUS to '-'.code,
    KeyEvent.VK_EQUALS to '='.code,
    KeyEvent.VK_OPEN_BRACKET to '['.code,
    KeyEvent.VK_CLOSE_BRACKET to ']'.code,
    KeyEvent.VK_BACK_SLASH to '\\'.code,
    KeyEvent.VK_SEMICOLON to ';'.code,
    KeyEvent.VK_QUOTE to '\''.code,
    KeyEvent.VK_BACK_QUOTE to '`'.code,
    KeyEvent.VK_COMMA to ','.code,
    KeyEvent.VK_PERIOD to '.'.code,
    KeyEvent.VK_SLASH to '/'.code,
)
