package org.churchpresenter.app.churchpresenter.presenter

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.ui.HeicDecoder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PicturePresenterDownscaleTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-picture-downscale").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun pngOf(width: Int, height: Int, name: String = "img.png"): File =
        File(dir, name).apply { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", this) }

    @Test
    fun `a missing file yields null`() {
        assertNull(loadAndDownscaleImage(File(dir, "does-not-exist.png").absolutePath))
    }

    @Test
    fun `undecodable bytes with a non-HEIC extension yield null`() {
        val file = File(dir, "not-an-image.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        assertNull(loadAndDownscaleImage(file.absolutePath))
    }

    @Test
    fun `an image already within bounds is returned at its original size`() {
        val file = pngOf(100, 100)
        val bitmap = loadAndDownscaleImage(file.absolutePath, maxWidth = 1920, maxHeight = 1080)
        assertEquals(100, bitmap?.width)
        assertEquals(100, bitmap?.height)
    }

    @Test
    fun `an image larger than the bounds is downscaled to fit, never upscaled`() {
        val file = pngOf(4000, 3000)
        val bitmap = loadAndDownscaleImage(file.absolutePath, maxWidth = 800, maxHeight = 600)
        assertEquals(800, bitmap?.width)
        assertEquals(600, bitmap?.height)
    }

    @Test
    fun `downscaling is capped by the more restrictive dimension, preserving aspect`() {
        val file = pngOf(2000, 1000)
        val bitmap = loadAndDownscaleImage(file.absolutePath, maxWidth = 800, maxHeight = 800)
        assertEquals(800, bitmap?.width)
        assertEquals(400, bitmap?.height)
    }

    @Test
    fun `a path that cannot be read as bytes yields null`() {
        assertNull(loadAndDownscaleImage(dir.absolutePath))
    }

    @Test
    fun `undecodable bytes with a HEIC extension attempt the HEIC fallback and still yield null`() {
        val file = File(dir, "not-a-real-photo.heic").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        assertNull(loadAndDownscaleImage(file.absolutePath))
    }

    @Test
    fun `a HEIC file HeicDecoder can convert decodes via the converted JPEG bytes`() {
        val jpegBytes = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB), "jpg", it)
        }.toByteArray()
        val file = File(dir, "photo.heic").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        mockkObject(HeicDecoder)
        try {
            every { HeicDecoder.toJpegBytes(any()) } returns jpegBytes
            val bitmap = loadAndDownscaleImage(file.absolutePath, maxWidth = 1920, maxHeight = 1080)
            assertEquals(50, bitmap?.width)
            assertEquals(40, bitmap?.height)
        } finally {
            unmockkObject(HeicDecoder)
        }
    }
}
