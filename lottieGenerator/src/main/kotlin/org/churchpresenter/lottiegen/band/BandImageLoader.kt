package org.churchpresenter.lottiegen.band

import org.churchpresenter.lottiegen.persistence.LogoData
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Reads a picture for the band: the bytes as a data URL the Lottie player decodes itself, and the
 * pixel size from the file's header. The size is read through an `ImageReader` rather than by
 * decoding the picture, because `ImageIO.read` refuses JPEGs it cannot colour-convert (CMYK, some
 * progressive ones) while the player, which uses Skia, draws them fine.
 */
internal object BandImageLoader {
    private val mimeTypes = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
    )

    fun load(file: File): LogoData? {
        if (!file.isFile) return null
        val mime = mimeTypes[file.extension.lowercase()] ?: return null
        val bytes = try {
            file.readBytes()
        } catch (_: IOException) {
            return null
        }
        val (w, h) = readSize(file) ?: return null
        return LogoData("data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}", w, h)
    }

    private fun readSize(file: File): Pair<Int, Int>? = try {
        ImageIO.createImageInputStream(file)?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.input = stream
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    } catch (_: IOException) {
        null
    }
}
