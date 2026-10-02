package org.churchpresenter.media.utils

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cache and the command. Running ffmpeg itself is left out: whether a bundled one exists
 * depends on the machine, and a test that passes only where it does is not one to keep.
 */
class VideoFirstFrameTest {

    private val dir: File = Files.createTempDirectory("cp-first-frame").toFile()
    private val cache = File(dir, "cache")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun video(name: String = "clip.mp4", bytes: Int = 16): File =
        File(dir, name).also { it.writeBytes(ByteArray(bytes)) }

    @Test
    fun `a clip is cached under a name that changes when the clip does`() {
        val clip = video()
        val first = VideoFirstFrame.cacheFileFor(clip, cache)

        assertEquals(first, VideoFirstFrame.cacheFileFor(clip, cache), "the same clip must hit the same file")
        assertEquals(cache, first.parentFile)

        clip.writeBytes(ByteArray(32))
        assertNotEquals(first, VideoFirstFrame.cacheFileFor(clip, cache), "a replaced clip must be extracted again")
    }

    @Test
    fun `two clips never share a cached frame`() {
        assertNotEquals(
            VideoFirstFrame.cacheFileFor(video("a.mp4"), cache),
            VideoFirstFrame.cacheFileFor(video("b.mp4"), cache),
        )
    }

    @Test
    fun `ffmpeg is asked for exactly one frame of the clip, written over whatever is there`() {
        val input = File(dir, "in.mp4")
        val output = File(dir, "out.jpg")

        assertEquals(
            listOf(
                "ffmpeg", "-y", "-loglevel", "error",
                "-i", input.absolutePath,
                "-frames:v", "1",
                output.absolutePath,
            ),
            VideoFirstFrame.ffmpegFirstFrameCommand(input, output, executable = "ffmpeg"),
        )
    }

    @Test
    fun `a frame already extracted is returned without running anything`() {
        val clip = video()
        val cached = VideoFirstFrame.cacheFileFor(clip, cache).also {
            it.parentFile.mkdirs()
            it.writeBytes(byteArrayOf(1, 2, 3))
        }

        assertEquals(cached, VideoFirstFrame.extract(clip, cache))
    }

    @Test
    fun `there is no frame for a clip that is not there`() {
        assertNull(VideoFirstFrame.extract(File(dir, "missing.mp4"), cache))
    }

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private fun stub(name: String, script: String) = File(dir, name).apply {
        writeText("#!/bin/sh\n$script\n")
        setExecutable(true)
    }

    @Test
    fun `with no ffmpeg there is no frame`() {
        assertNull(VideoFirstFrame.extract(video(), cache, ffmpeg = null))
    }

    @Test
    fun `the frame ffmpeg writes is kept under the clip's cache name`() {
        assumeTrue(!isWindows, "needs a shell script to stand in for ffmpeg")
        val clip = video()
        // The output path is the last argument ffmpeg is given.
        val ffmpeg = stub("ffmpeg-ok", "for last; do :; done; printf 'jpeg' > \"${'$'}last\"")
        val frame = VideoFirstFrame.extract(clip, cache, ffmpeg = ffmpeg.path)
        assertEquals(VideoFirstFrame.cacheFileFor(clip, cache), frame)
        assertEquals("jpeg", frame?.readText())
    }

    @Test
    fun `a clip ffmpeg cannot read leaves no frame behind`() {
        assumeTrue(!isWindows, "needs a shell script to stand in for ffmpeg")
        val clip = video()
        val ffmpeg = stub("ffmpeg-fails", "exit 1")
        assertNull(VideoFirstFrame.extract(clip, cache, ffmpeg = ffmpeg.path))
        assertTrue(cache.listFiles().orEmpty().isEmpty(), "no partial frame may be left")
    }

    @Test
    fun `an ffmpeg that cannot be started is no frame, not an error`() {
        assertNull(VideoFirstFrame.extract(video(), cache, ffmpeg = File(dir, "no-such-ffmpeg").path))
    }

    @Test
    fun `frames are kept in the app's own cache, run through the bundled ffmpeg`() {
        val clip = video()
        val home = File(System.getProperty("user.home"))
        assertEquals(File(home, ".churchpresenter/cache/preview-frames"), VideoFirstFrame.cacheDir())
        assertEquals(VideoFirstFrame.cacheDir(), VideoFirstFrame.cacheFileFor(clip).parentFile)
        val command = VideoFirstFrame.ffmpegFirstFrameCommand(clip, File(dir, "out.jpg"))
        assertEquals(org.churchpresenter.sharedui.utils.FfmpegBinary.path, command.first())
    }
}
