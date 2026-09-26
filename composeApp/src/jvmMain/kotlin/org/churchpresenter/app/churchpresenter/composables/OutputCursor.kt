package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.Window as AwtWindow

// Hiding the mouse pointer over an output window takes two layers, because one is not enough.
// Compose sets the pointer it draws over its own content on every hover and falls back to the
// arrow, so the content carries a blank pointer icon that overrides whatever its children ask for
// ([hiddenOutputCursor]). Heavyweight views embedded in the window -- video and web pages -- are not
// Compose's to draw over; they take the window's own cursor, so that is set too
// ([HideOutputWindowCursor]), and put back when the setting is turned off.

/**
 * A cursor with nothing in it -- one fully transparent pixel.
 *
 * Built on first use rather than at class load: `createCustomCursor` throws `HeadlessException` on a
 * machine with no display, which is every test run, and only an output window ever asks for it.
 */
private val blankCursor: Cursor by lazy {
    Toolkit.getDefaultToolkit().createCustomCursor(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        Point(0, 0),
        "blank",
    )
}

/** Sets [window]'s own cursor to nothing while [hide] is on, and back to the arrow when it is not. */
@Composable
fun HideOutputWindowCursor(window: AwtWindow, hide: Boolean) {
    DisposableEffect(window, hide) {
        window.cursor = if (hide) blankCursor else Cursor.getDefaultCursor()
        onDispose { }
    }
}

/** A blank pointer over this content and everything in it, while [hide] is on. */
fun Modifier.hiddenOutputCursor(hide: Boolean): Modifier =
    if (hide) pointerHoverIcon(PointerIcon(blankCursor), overrideDescendants = true) else this
