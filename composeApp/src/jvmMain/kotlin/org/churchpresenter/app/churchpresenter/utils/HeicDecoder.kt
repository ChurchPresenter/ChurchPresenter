package org.churchpresenter.app.churchpresenter.utils

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import org.churchpresenter.diagnostics.CrashReporter

/**
 * Decodes HEIC/HEIF image files into JPEG bytes.
 *
 * Three converters, tried in order of how likely each is to be there:
 *
 *  * **`sips`** on macOS — built into the OS, so it is tried first and usually settles it. Not
 *    always: a third of the reported decode failures came from Macs, so it falls through too.
 *  * **ImageIO** everywhere — in-process and cheap, but it needs a HEIF reader plugin, and no
 *    stock JDK ships one. It succeeds only where the deployment has added one.
 *  * **ffmpeg** everywhere — the optional external tool the camera source already depends on.
 *
 * Off macOS the first two both miss on an ordinary install, which is why the ffmpeg leg exists: for
 * every Windows and Linux user, HEIC previously reached `ImageIO.read`, got null because nothing
 * claimed the format, and failed. Phone photos are HEIC by default, so a folder straight off an
 * iPhone showed a grid of failed tiles. That leg backs up a failed `sips` on macOS as well.
 */
object HeicDecoder {

    private val isMacOs = System.getProperty("os.name").lowercase().contains("mac")

    /** How long ffmpeg gets to convert one still before it is killed. */
    private const val FFMPEG_TIMEOUT_SECONDS = 20L

    /**
     * Converts [heicFile] to JPEG bytes, or returns null if every converter available here failed.
     *
     * The converters are parameters so the order can be tested without a HEIC file, a Mac, or an
     * ffmpeg install; nothing but a test passes them.
     */
    internal fun toJpegBytes(
        heicFile: File,
        onMac: Boolean = isMacOs,
        sips: (File) -> ByteArray? = ::convertWithSips,
        imageIo: (File) -> ByteArray? = ::convertWithImageIO,
        ffmpeg: (File) -> ByteArray? = ::convertWithFfmpeg,
    ): ByteArray? {
        if (onMac) sips(heicFile)?.let { return it }
        return imageIo(heicFile) ?: ffmpeg(heicFile)
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
            CrashReporter.reportException(e, "Converting HEIC to JPEG")
            null
        } finally {
            tempFile.delete()
        }
    }

    // ── Fallback: ImageIO ────────────────────────────────────────────────────

    internal fun convertWithImageIO(heicFile: File): ByteArray? {
        return try {
            val bufferedImage = ImageIO.read(heicFile) ?: return null
            val out = ByteArrayOutputStream()
            ImageIO.write(bufferedImage, "jpg", out)
            out.toByteArray()
        } catch (e: Exception) {
            CrashReporter.reportException(e, "Decoding HEIC image")
            null
        }
    }

    // ── Fallback: ffmpeg ─────────────────────────────────────────────────────

    /**
     * Converts [heicFile] through the ffmpeg CLI, or returns null when it is absent or failed.
     *
     * A missing ffmpeg is the ordinary case rather than an error — it is an optional tool — so this
     * reports nothing and lets the caller record one decode failure for the file.
     */
    internal fun convertWithFfmpeg(heicFile: File): ByteArray? {
        val tempFile = Files.createTempFile("heic_ffmpeg_", ".jpg").toFile()
        return try {
            // Output goes to the file, not a pipe: draining a pipe while also waiting with a
            // timeout needs a second thread, and there is nothing here worth reading.
            val process = ProcessBuilder(ffmpegHeicCommand(heicFile, tempFile))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() == 0 && tempFile.length() > 0) tempFile.readBytes() else null
        } catch (_: IOException) {
            // ffmpeg is not on PATH. Expected on a plain Windows or Linux install.
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } finally {
            tempFile.delete()
        }
    }

    /**
     * The ffmpeg invocation that turns [input] into a single JPEG at [output].
     *
     * `-frames:v 1` matters: a HEIC can hold a burst or a Live Photo's video track, and without it
     * ffmpeg writes a numbered sequence and leaves [output] empty.
     */
    internal fun ffmpegHeicCommand(input: File, output: File): List<String> = listOf(
        "ffmpeg", "-y", "-loglevel", "error",
        "-i", input.absolutePath,
        "-frames:v", "1",
        output.absolutePath
    )
}

