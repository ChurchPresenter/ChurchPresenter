package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key

internal enum class OutputShortcut { NEXT, PREVIOUS, FULLSCREEN, HIDE, ON_TOP, BLACKOUT }

internal fun outputShortcut(key: Key): OutputShortcut? = when (key) {
    Key.DirectionRight, Key.DirectionDown, Key.PageDown, Key.Spacebar -> OutputShortcut.NEXT
    Key.DirectionLeft, Key.DirectionUp, Key.PageUp, Key.Backspace -> OutputShortcut.PREVIOUS
    Key.F11 -> OutputShortcut.FULLSCREEN
    Key.Escape -> OutputShortcut.HIDE
    Key.T -> OutputShortcut.ON_TOP
    Key.B -> OutputShortcut.BLACKOUT
    else -> null
}
