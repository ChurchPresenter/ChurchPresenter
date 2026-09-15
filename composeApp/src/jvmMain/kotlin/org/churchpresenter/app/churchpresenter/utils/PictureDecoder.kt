package org.churchpresenter.app.churchpresenter.utils

import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Decodes a picture file into a Skia [Image], falling back when Skia's own decoders reject it.
 *
 * `Image.makeFromEncoded` covers the common web formats and nothing else, and it fails with the
 * same opaque `Failed to Image::makeFromEncoded` whatever the reason — so before this existed a
 * folder holding an ordinary photo could show a permanently failed tile. Three cases reach it in
 * practice:
 *
 * - **A JPEG Skia will not read.** CMYK/YCCK JPEGs out of print workflows are the usual one; the
 *   TwelveMonkeys `imageio-jpeg` reader on the classpath reads them.
 * - **A HEIC/HEIF carrying a `.jpg`/`.jpeg` name.** Phone exports and chat apps rename freely, so
 *   the HEIC path is chosen by sniffing the `ftyp` box rather than by extension.
 * - **A Photoshop file carrying a `.jpg` name.** Reported from the field as an undecodable
 *   thumbnail whose leading bytes were `8BPS`; the `imageio-psd` reader on the classpath reads it.
 * - **A format only ImageIO knows**, TIFF being the one that turns up in picture folders.
 *
 * The fallbacks re-encode, so they cost a full extra decode — they run only after Skia has already
 * refused the file.
 */
object PictureDecoder {

    /** Decodes [file], or throws with the reason Skia gave. */
    fun decode(file: File): Image {
        val bytes = file.readBytes()

        val skiaError = try {
            return Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            e
        }

        if (isHeif(bytes) || file.extension.lowercase() in HEIF_EXTENSIONS) {
            HeicDecoder.toJpegBytes(file)?.let { jpeg ->
                runCatching { return Image.makeFromEncoded(jpeg) }
            }
        }

        transcodeWithImageIO(bytes)?.let { png ->
            runCatching { return Image.makeFromEncoded(png) }
        }

        throw IOException("Failed to decode image ${file.name}: ${skiaError.message}", skiaError)
    }

    /**
     * A one-line technical description of [file], for the report written when nothing could decode
     * it.
     *
     * Skia fails every unreadable file with the same `Failed to Image::makeFromEncoded` whatever
     * the reason, so a report carrying only that message says nothing beyond "a picture did not
     * load" — an empty placeholder still syncing from iCloud, a truncated copy, a CMYK JPEG and a
     * file that is not an image at all are indistinguishable in it. Size, the leading magic bytes
     * and whether ImageIO recognises the format at all separate those four without carrying any of
     * the file's content, and the name is deliberately left out: picture file names are the user's.
     */
    fun diagnose(file: File): String = try {
        val size = file.length()
        val head = readHead(file)
        val magic = head.joinToString("") { byte -> "%02X".format(byte) }.ifEmpty { "none" }
        "ext=${file.extension.lowercase()} size=$size magic=$magic imageio=${imageIoFormat(file) ?: "none"}"
    } catch (e: IOException) {
        "diagnostics unavailable: ${e.message}"
    } catch (e: SecurityException) {
        "diagnostics unavailable: ${e.message}"
    }

    /**
     * The first bytes of [file], short enough to be a format signature and nothing more, or none
     * when the file has been deleted between the failed decode and this call.
     */
    private fun readHead(file: File): ByteArray {
        if (!file.isFile) return ByteArray(0)
        return file.inputStream().use { stream ->
            val buffer = ByteArray(MAGIC_LENGTH)
            val read = stream.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
    }

    /**
     * The format name of the first ImageIO reader that claims [file], or null when none does.
     *
     * "no reader claimed it" and "a reader claimed it and then failed" are different bugs — the
     * first is a file we do not support, the second is one we should have read.
     */
    private fun imageIoFormat(file: File): String? = try {
        ImageIO.createImageInputStream(file)?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (readers.hasNext()) readers.next().formatName else null
        }
    } catch (_: IOException) {
        null
    }

    /** Decodes [file], or returns null if none of the decoders could read it. */
    fun decodeOrNull(file: File): Image? = try {
        decode(file)
    } catch (_: Exception) {
        null
    }

    /**
     * Decodes [file] and scales the result down to fit within [maxWidth]×[maxHeight], never
     * upscaling — an operator's own photo has no size cap the way a stock/Pexels download does, and
     * decoding one at its native resolution (often 4000px+, tens of megabytes) once per place it is
     * drawn is what turned "add a background" into a multi-second freeze (#549). The caller decides
     * what "fits" means: a presenter output passes its own pixel size, a grid thumbnail passes its
     * tile size.
     *
     * The downscale itself is cheap relative to [decode] — decoding the full file already paid the
     * real cost — so this exists to bound what gets *held*, not to speed up the read.
     */
    fun decodeScaled(file: File, maxWidth: Int, maxHeight: Int): Image {
        val original = decode(file)
        val scale = minOf(
            maxWidth.toFloat() / original.width,
            maxHeight.toFloat() / original.height,
            1f,
        )
        if (scale >= 1f) return original

        val newWidth = (original.width * scale).toInt()
        val newHeight = (original.height * scale).toInt()
        val surface = Surface.makeRasterN32Premul(newWidth, newHeight)
        val srcRect = Rect.makeWH(original.width.toFloat(), original.height.toFloat())
        val dstRect = Rect.makeWH(newWidth.toFloat(), newHeight.toFloat())
        // Mitchell over the default (nearest-neighbour) sampling — a straight downscale without it
        // aliases badly enough to be visible on a full-frame background.
        surface.canvas.drawImageRect(original, srcRect, dstRect, SamplingMode.MITCHELL, Paint(), true)
        return surface.makeImageSnapshot()
    }

    /** [decodeScaled], or null if [file] could not be decoded at all. */
    fun decodeScaledOrNull(file: File, maxWidth: Int, maxHeight: Int): Image? = try {
        decodeScaled(file, maxWidth, maxHeight)
    } catch (_: Exception) {
        null
    }

    /**
     * Re-encodes [bytes] as PNG through ImageIO, or returns null if ImageIO cannot read it either.
     *
     * The intermediate is drawn into an ARGB image rather than written straight out: ImageIO hands
     * back whatever colour model the file used — CMYK rasters and indexed TIFFs among them — and
     * the PNG writer refuses several of those.
     */
    internal fun transcodeWithImageIO(bytes: ByteArray): ByteArray? = try {
        ImageIO.read(ByteArrayInputStream(bytes))?.let { source ->
            val argb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = argb.createGraphics()
            try {
                graphics.drawImage(source, 0, 0, null)
            } finally {
                graphics.dispose()
            }
            ByteArrayOutputStream().also { ImageIO.write(argb, "png", it) }.toByteArray()
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Whether [bytes] is an ISO-BMFF file holding HEIF imagery, read from its `ftyp` box.
     *
     * The extension is not enough — a HEIC arriving as `photo.jpeg` is routine — and the brand is
     * what distinguishes one from the MP4s that share the container.
     */
    internal fun isHeif(bytes: ByteArray): Boolean {
        if (bytes.size < BRAND_OFFSET + FOUR_CC_LENGTH) return false
        val box = String(bytes, BOX_TYPE_OFFSET, FOUR_CC_LENGTH, Charsets.US_ASCII)
        if (box != "ftyp") return false
        return String(bytes, BRAND_OFFSET, FOUR_CC_LENGTH, Charsets.US_ASCII).lowercase() in HEIF_BRANDS
    }

    /** An ISO-BMFF box opens with a four-byte length, then its four-character type, then its brand. */
    private const val FOUR_CC_LENGTH = 4
    private const val BOX_TYPE_OFFSET = 4
    private const val BRAND_OFFSET = 8

    /** Enough leading bytes to name a format — JPEG needs three, PNG eight. */
    private const val MAGIC_LENGTH = 8

    private val HEIF_EXTENSIONS = setOf("heic", "heif")

    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
}
