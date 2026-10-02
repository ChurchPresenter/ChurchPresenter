package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.sharedui.utils.FfmpegBinary
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.churchpresenter.sharedui.utils.HeicDecoder

/**
 * A video's first frame as a JPEG on disk, for a preview that must show what a clip looks like
 * without playing it.
 *
 * Written by the bundled ffmpeg (`-frames:v 1`, the same invocation [HeicDecoder] uses for a single
 * picture) and kept under `~/.churchpresenter/cache/preview-frames/`, keyed by the clip's path, its
 * modification time and its length -- so a clip replaced under the same name is extracted again,
 * and an unchanged one is extracted once per machine rather than once per dialog.
 *
 * Starting VLC for this would hold a decoder open for as long as the preview is on screen, for a
 * picture a few hundred dp wide; one ffmpeg run of well under a second is the whole cost here.
 */
internal object VideoFirstFrame {

    /** A clip ffmpeg has not finished with by then is not one worth waiting on for a preview. */
    private const val FFMPEG_TIMEOUT_SECONDS = 10L

    /** Where the extracted frames are kept. */
    fun cacheDir(): File = File(System.getProperty("user.home"), ".churchpresenter/cache/preview-frames")

    /** The file [video]'s frame is cached as in [dir]: its path, mtime and length hashed together. */
    fun cacheFileFor(video: File, dir: File = cacheDir()): File {
        val key = "${video.absolutePath}:${video.lastModified()}:${video.length()}"
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".jpg")
    }

    /** The ffmpeg invocation that writes [input]'s first frame to [output] as one JPEG. */
    fun ffmpegFirstFrameCommand(
        input: File,
        output: File,
        executable: String = FfmpegBinary.path,
    ): List<String> = listOf(
        executable, "-y", "-loglevel", "error",
        "-i", input.absolutePath,
        "-frames:v", "1",
        output.absolutePath,
    )

    /**
     * [video]'s first frame, extracting it if it is not cached yet, or null when there is no such
     * clip, no ffmpeg, or ffmpeg could not read it. Blocks: call it off the UI thread.
     */
    fun extract(video: File, dir: File = cacheDir()): File? {
        if (!video.isFile) return null
        val cached = cacheFileFor(video, dir)
        if (cached.isFile && cached.length() > 0) return cached
        if (!FfmpegBinary.isAvailable) return null
        dir.mkdirs()
        // Written beside the final name and renamed into place, so a run cut short never leaves a
        // truncated JPEG that every later preview would read as the frame.
        val partial = File(dir, cached.nameWithoutExtension + ".partial.jpg")
        return try {
            val process = ProcessBuilder(ffmpegFirstFrameCommand(video, partial))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0 && partial.length() > 0 && partial.renameTo(cached)) {
                cached
            } else {
                null
            }
        } catch (_: IOException) {
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } finally {
            partial.delete()
        }
    }
}
