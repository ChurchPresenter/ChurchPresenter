package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.nio.file.Files

/**
 * Decodes HEIC/HEIF image files into JPEG bytes.
 *
 * On macOS, uses the built-in `sips` CLI tool for reliable native decoding.
 * On other platforms, attempts ImageIO (requires a compatible ImageIO plugin).
 */
object HeicDecoder {

    private val isMacOs = System.getProperty("os.name").lowercase().contains("mac")

    /**
     * Converts [heicFile] to JPEG bytes, or returns null if conversion fails.
     */
    fun toJpegBytes(heicFile: File): ByteArray? {
        return if (isMacOs) convertWithSips(heicFile) else convertWithImageIO(heicFile)
    }

    // ── macOS: sips ──────────────────────────────────────────────────────────

    private fun convertWithSips(heicFile: File): ByteArray? {
        val tempFile = Files.createTempFile("heic_convert_", ".jpg").toFile()
        return try {
            val process = ProcessBuilder(
                "sips", "-s", "format", "jpeg",
                heicFile.absolutePath,
                "--out", tempFile.absolutePath
            )
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            if (exitCode == 0 && tempFile.exists() && tempFile.length() > 0) {
                tempFile.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempFile.delete()
        }
    }

    // ── Fallback: ImageIO ────────────────────────────────────────────────────

    private fun convertWithImageIO(heicFile: File): ByteArray? {
        return try {
            val bufferedImage = javax.imageio.ImageIO.read(heicFile) ?: return null
            val out = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(bufferedImage, "jpg", out)
            out.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

