package lottiegen.editor

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.imageio.ImageIO

/** An image prepared for embedding in a spec (see [lottiegen.spec.ImageElement]). */
data class ImportedImage(val dataUri: String, val width: Int, val height: Int, val encodedBytes: Int)

/**
 * Reads and re-encodes an image for embedding in a style spec: downscaled to
 * [MAX_DIMENSION] on the long edge so the base64 payload stays reasonable, PNG for
 * alpha-capable sources, JPEG kept as JPEG (smaller, no alpha to preserve).
 * PNG/JPEG only — ImageIO cannot read SVG/WebP dimensions reliably.
 */
object ImageImport {
    const val MAX_DIMENSION = 1920

    fun import(file: File): ImportedImage? {
        val source = try {
            ImageIO.read(file)
        } catch (_: IOException) {
            null
        } ?: return null
        if (source.width <= 0 || source.height <= 0) return null

        val scale = MAX_DIMENSION.toDouble() / maxOf(source.width, source.height)
        val jpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
        val targetType = if (jpeg) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
        val image = if (scale >= 1.0 && (!jpeg || source.type == targetType)) {
            source
        } else {
            val w = maxOf(1, (source.width * minOf(scale, 1.0)).toInt())
            val h = maxOf(1, (source.height * minOf(scale, 1.0)).toInt())
            BufferedImage(w, h, targetType).also { scaled ->
                val g = scaled.createGraphics()
                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )
                g.drawImage(source, 0, 0, w, h, null)
                g.dispose()
            }
        }

        val format = if (jpeg) "jpg" else "png"
        val mime = if (jpeg) "image/jpeg" else "image/png"
        val out = ByteArrayOutputStream()
        if (!ImageIO.write(image, format, out)) return null
        val bytes = out.toByteArray()
        return ImportedImage(
            dataUri = "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}",
            width = image.width,
            height = image.height,
            encodedBytes = bytes.size
        )
    }
}
