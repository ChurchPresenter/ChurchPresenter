package org.churchpresenter.lottiegen.band

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import java.util.Base64

/**
 * Blurs a band picture's pixels for the overlay, keeping its size so the layout built on it
 * stands. Decoding and blurring a photo costs a noticeable fraction of a second, and the
 * generator regenerates on every slider tick, so the last few results are kept: dragging any
 * other knob reuses the picture already blurred at this radius.
 */
internal object BandImageBlur {
    private const val CACHE_SIZE = 8
    private const val JPEG_QUALITY = 90
    private const val DATA_URL_SEPARATOR = ","

    private val cache = object : LinkedHashMap<Pair<BandImage, Int>, BandImage>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<BandImage, Int>, BandImage>): Boolean =
            size > CACHE_SIZE
    }

    /**
     * [image] blurred by [sigma] of its own pixels, or [image] itself when there is nothing to
     * blur or its bytes cannot be decoded — an undecodable picture is left for the player, which
     * may well draw it.
     */
    fun blurred(image: BandImage, sigma: Double): BandImage {
        val rounded = sigma.toInt()
        if (rounded <= 0 || !image.isUsable) return image
        val key = image to rounded
        synchronized(cache) { cache[key] }?.let { return it }
        val result = render(image, rounded.toFloat()) ?: image
        synchronized(cache) { cache[key] = result }
        return result
    }

    private fun render(image: BandImage, sigma: Float): BandImage? {
        val decoded = decode(image.data) ?: return null
        val format = if (image.data.startsWith("data:image/jpeg")) EncodedImageFormat.JPEG else EncodedImageFormat.PNG
        return decoded.use { source ->
            Surface.makeRasterN32Premul(source.width, source.height).use { surface ->
                // Clamped edges: the picture's own border pixels spread inwards rather than
                // transparency bleeding in, so the band's edge stays as solid as it was.
                val paint = Paint().apply { imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP) }
                surface.canvas.drawImage(source, 0f, 0f, paint)
                val encoded = surface.makeImageSnapshot().use { it.encodeToData(format, JPEG_QUALITY) }
                val mime = if (format == EncodedImageFormat.JPEG) "image/jpeg" else "image/png"
                encoded?.let { image.copy(data = "data:$mime;base64,${Base64.getEncoder().encodeToString(it.bytes)}") }
            }
        }
    }

    /** The picture behind a data URL, or null when the URL or the bytes are not one Skia can read. */
    private fun decode(dataUrl: String): Image? = try {
        Image.makeFromEncoded(Base64.getDecoder().decode(dataUrl.substringAfter(DATA_URL_SEPARATOR)))
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val LOAD_FACTOR = 0.75f
}
