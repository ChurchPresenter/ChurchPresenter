package org.churchpresenter.app.churchpresenter.remote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.server.DrawingFrame
import org.jetbrains.skia.Image

/*
 * A page drawn on a phone or tablet's canvas, as the app puts it on the outputs. The server holds the
 * pages (DrawingDesk); this turns one into something an output can draw, and names it for the
 * operator's prompt.
 */

/** A decoded page and the paper around it, ready for `PresenterManager.showDrawingFrame`. */
internal class DrawingPage(val bitmap: ImageBitmap, val paper: Color)

/**
 * [frame] decoded, or null when its bytes are not a picture after all — the server only checks the
 * PNG signature. **Blocking**: call it off the event thread.
 */
internal fun decodeDrawingPage(frame: DrawingFrame): DrawingPage? = runCatching {
    DrawingPage(Image.makeFromEncoded(frame.png).toComposeImageBitmap(), paperColour(frame.paper))
}.onFailure { Log.warn("RemoteDrawing", "a drawing page from ${frame.clientId} could not be decoded: ${it.message}") }
    .getOrNull()

/** The page's `#RRGGBB` background, or black — every other output's empty screen — when there is none. */
internal fun paperColour(hex: String?): Color {
    val rgb = hex?.removePrefix("#")?.takeIf { it.length == RGB_DIGITS }?.toLongOrNull(HEX) ?: return Color.Black
    return Color(OPAQUE or rgb)
}

private const val RGB_DIGITS = 6
private const val HEX = 16
private const val OPAQUE = 0xFF000000

/**
 * The (title, detail) the operator's Go Live prompt and toast show for a drawing — in the shape
 * [remoteEventLabel] gives every other item: what it is called, and what kind of thing it is.
 */
internal fun drawingEventLabel(title: String): Pair<String, String> =
    title.trim().ifEmpty { DRAWING_KIND } to DRAWING_KIND

private const val DRAWING_KIND = "Drawing"
