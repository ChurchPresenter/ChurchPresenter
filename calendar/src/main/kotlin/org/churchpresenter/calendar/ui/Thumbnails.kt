package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Image as AwtImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The longest side a preview thumbnail is scaled to, in pixels. */
private const val THUMB_PIXELS = 192

/** [file] decoded and scaled to thumbnail size, or null when it is not an image after all. */
internal fun thumbnailOf(file: File): ImageBitmap? {
    val source = runCatching { ImageIO.read(file) }.getOrNull() ?: return null
    val scale = THUMB_PIXELS.toDouble() / maxOf(source.width, source.height).coerceAtLeast(1)
    if (scale >= 1.0) return source.toComposeImageBitmap()
    val width = (source.width * scale).toInt().coerceAtLeast(1)
    val height = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaled.createGraphics()
    graphics.drawImage(source.getScaledInstance(width, height, AwtImage.SCALE_SMOOTH), 0, 0, null)
    graphics.dispose()
    return scaled.toComposeImageBitmap()
}

/**
 * Where the window reads files it only shows — `CalendarApp`'s `io`, provided once at its root.
 *
 * A composition local so a sheet deep in the window can decode a picture off the event thread
 * without every dialog between here and the root taking the dispatcher as a parameter.
 */
internal val LocalCalendarIo: ProvidableCompositionLocal<CoroutineDispatcher> =
    staticCompositionLocalOf { Dispatchers.IO }

/**
 * [path]'s thumbnail, decoded on [LocalCalendarIo] rather than while composing: null until it is
 * read, and null for a blank path or a file that is not an image.
 */
@Composable
internal fun rememberThumbnail(path: String): State<ImageBitmap?> {
    val io = LocalCalendarIo.current
    return produceState<ImageBitmap?>(initialValue = null, path, io) {
        value = if (path.isBlank()) null else withContext(io) { thumbnailOf(File(path)) }
    }
}
