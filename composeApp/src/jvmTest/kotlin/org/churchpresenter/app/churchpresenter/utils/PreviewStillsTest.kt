package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.camera.CameraDeviceRef
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.churchpresenter.media.utils.VideoFirstFrame

class PreviewStillsTest {

    private val dir: File = Files.createTempDirectory("cp-preview-stills").toFile()
    private val seededFrames = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
        seededFrames.forEach { it.delete() }
    }

    private fun png(name: String, width: Int = 400, height: Int = 200): File =
        File(dir, name).also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }

    @Test
    fun `a picture is decoded to fit the preview, keeping its shape`() {
        val still = assertNotNull(PreviewStills.picture(png("wide.png"), maxWidth = 100, maxHeight = 100))

        assertTrue(still.width <= 100 && still.height <= 100, "decoded at ${still.width}x${still.height}")
        assertEquals(2.0, still.width.toDouble() / still.height, 0.05)
    }

    @Test
    fun `the same picture at the same size is decoded once`() {
        val file = png("once.png")

        assertSame(PreviewStills.picture(file, 120, 80), PreviewStills.picture(file, 120, 80))
    }

    @Test
    fun `a picture changed on disk is decoded again`() {
        val file = png("changing.png")
        val before = PreviewStills.picture(file, 120, 80)
        ImageIO.write(BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB), "png", file)
        file.setLastModified(file.lastModified() + 60_000)

        assertNotSame(before, PreviewStills.picture(file, 120, 80))
    }

    @Test
    fun `nothing is drawn for a missing or unreadable picture`() {
        assertNull(PreviewStills.picture(File(dir, "missing.png"), 100, 100))
        assertNull(PreviewStills.picture(File(dir, "junk.png").also { it.writeText("not a picture") }, 100, 100))
    }

    @Test
    fun `a clip previews as its already-extracted first frame`() {
        val clip = File(dir, "clip.mp4").also { it.writeBytes(ByteArray(8)) }
        // The frame ffmpeg would have written, seeded where VideoFirstFrame looks for it.
        VideoFirstFrame.cacheFileFor(clip).also { frame ->
            frame.parentFile.mkdirs()
            ImageIO.write(BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB), "png", frame)
            seededFrames += frame
        }

        assertNotNull(PreviewStills.videoFrame(clip, 80, 80))
    }

    @Test
    fun `a camera that is not set up has no snapshot`() {
        assertNull(runBlocking { PreviewStills.cameraSnapshot(CameraDeviceRef()) })
    }
}
