package org.churchpresenter.canvas

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether VLC is usable, as the canvas needs to know it.
 *
 * A video scene source is decoded by VLC straight into a pixel buffer. Whether the native library
 * actually loaded is something the app works out once at startup — ten files there ask about it —
 * so the answer is handed in rather than probed again here.
 *
 * [Unavailable] is the default: a scene composed with no VLC draws the "video unavailable" placeholder
 * instead of a black rectangle, which is also exactly what a test wants.
 */
data class CanvasVideoSupport(
    /** The native library loaded and a player can be built. */
    val available: Boolean = false,
    /** Loading was attempted and failed — worth telling the operator, unlike simply not installed. */
    val loadFailed: Boolean = false,
) {
    companion object {
        val Unavailable = CanvasVideoSupport()
    }
}

/** The VLC availability the canvas draws against. */
val LocalCanvasVideoSupport = staticCompositionLocalOf { CanvasVideoSupport.Unavailable }
