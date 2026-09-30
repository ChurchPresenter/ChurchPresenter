package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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
}
