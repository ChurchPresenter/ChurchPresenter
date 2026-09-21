package org.churchpresenter.calendar.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Image as AwtImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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
