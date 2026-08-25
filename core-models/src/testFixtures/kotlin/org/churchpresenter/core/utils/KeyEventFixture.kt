package org.churchpresenter.core.utils

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType

/**
 * A key-down event, for testing binding resolution without composing anything.
 *
 * Uses Compose desktop's own `KeyEvent` factory rather than wrapping a `java.awt.event.KeyEvent`:
 * a desktop `KeyEvent` is an `InternalKeyEvent`, not the AWT one, so handing it an AWT event throws
 * `ClassCastException` the moment anything reads `.key` off it.
 *
 * The factory is `@InternalComposeUiApi`, opted into here because there is no public way to build a
 * `KeyEvent` outside a composition and the alternative is to spin up a Compose host for assertions
 * that are pure logic. It is confined to this one test fixture, so a Compose upgrade that changes
 * the signature breaks one file rather than every keyboard test.
 */
@OptIn(InternalComposeUiApi::class)
fun keyDown(
    key: Key,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
    meta: Boolean = false,
): KeyEvent = KeyEvent(
    key = key,
    type = KeyEventType.KeyDown,
    codePoint = 0,
    isCtrlPressed = ctrl,
    isMetaPressed = meta,
    isAltPressed = alt,
    isShiftPressed = shift,
)
