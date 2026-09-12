package org.churchpresenter.lottiegen.band

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BandImageLoaderTest {

    private lateinit var temp: File

    @BeforeTest
    fun setUp() {
        temp = Files.createTempDirectory("band-image-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    private fun write(name: String, format: String, w: Int = 12, h: Int = 8): File {
        val file = File(temp, name)
        ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, file)
        return file
    }

    @Test
    fun `png and jpeg load with their size and mime type`() {
        val png = assertNotNull(BandImageLoader.load(write("a.png", "png", 20, 10)))
        assertEquals(20, png.width)
        assertEquals(10, png.height)
        assertTrue(png.dataUrl.startsWith("data:image/png;base64,"))
        val jpg = assertNotNull(BandImageLoader.load(write("b.JPG", "jpeg", 6, 4)))
        assertEquals(6 to 4, jpg.width to jpg.height)
        assertTrue(jpg.dataUrl.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `an unknown extension, a missing file and a file that is not an image are all null`() {
        assertNull(BandImageLoader.load(File(temp, "missing.png")))
        assertNull(BandImageLoader.load(File(temp, "notes.txt").apply { writeText("hello") }))
        assertNull(BandImageLoader.load(File(temp, "broken.png").apply { writeText("not a png") }))
    }
}
