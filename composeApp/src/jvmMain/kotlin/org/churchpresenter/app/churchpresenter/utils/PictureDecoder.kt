package org.churchpresenter.app.churchpresenter.utils

import org.jetbrains.skia.Image
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
