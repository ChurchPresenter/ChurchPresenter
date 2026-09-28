package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Whether the output this content is drawn on hides the mouse pointer.
 *
 * Provided by the output windows alongside [HideOutputWindowCursor], for the one child neither layer
 * reaches: an embedded web page. Chromium's own native window sits inside the output and sets its own
 * cursor, so the arrow showed over a live Web page with the setting on (found on Windows). The page
 * is told instead -- see [outputCursorScript]. False everywhere else, so the Web tab's own browser in
 * the main window keeps its pointer.
 */
val LocalOutputCursorHidden = staticCompositionLocalOf { false }

/** The id of the style element [outputCursorScript] adds, so the same script can take it away. */
private const val HIDE_CURSOR_STYLE_ID = "churchpresenter-hide-cursor"

/**
 * JavaScript that hides the pointer over a web page, or shows it again.
 *
 * A style rule rather than anything done to the window: Chromium then hides the pointer itself, the
 * same way on every platform, and reapplying it is harmless. Run again after every page load, since
 * a new page starts without it.
 */
internal fun outputCursorScript(hide: Boolean): String =
    if (hide) {
        "(function(){var s=document.getElementById('$HIDE_CURSOR_STYLE_ID');" +
            "if(!s){s=document.createElement('style');s.id='$HIDE_CURSOR_STYLE_ID';" +
            "(document.head||document.documentElement).appendChild(s);}" +
            "s.textContent='*,*::before,*::after{cursor:none!important}';})();"
    } else {
        "(function(){var s=document.getElementById('$HIDE_CURSOR_STYLE_ID');if(s){s.remove();}})();"
    }

/** A blank pointer over this content and everything in it, while [hide] is on. */
fun Modifier.hiddenOutputCursor(hide: Boolean): Modifier =
    if (hide) pointerHoverIcon(PointerIcon(blankCursor), overrideDescendants = true) else this
