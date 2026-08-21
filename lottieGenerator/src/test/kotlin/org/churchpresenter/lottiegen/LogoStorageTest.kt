package org.churchpresenter.lottiegen

import org.churchpresenter.lottiegen.persistence.LogoStorage
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

class LogoStorageTest {

    private lateinit var temp: File
    private lateinit var savedHome: String

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("lottiegen-logo-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    private fun pngFile(name: String, w: Int = 8, h: Int = 4): File {
        val file = File(temp, name)
        ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", file)
        return file
    }

    @Test
    fun `an empty logo folder lists nothing`() {
        assertEquals(emptyList(), LogoStorage.listLogos())
    }

    @Test
    fun `only image files are listed, sorted by name`() {
        LogoStorage.importLogo(pngFile("beta.png"))
        LogoStorage.importLogo(pngFile("alpha.png"))
        LogoStorage.getLogoFile("notes.txt").writeText("not a logo")

        assertEquals(listOf("alpha.png", "beta.png"), LogoStorage.listLogos())
    }

    @Test
    fun `every supported extension is recognised regardless of case`() {
        listOf("a.PNG", "b.jpg", "c.JPEG", "d.svg", "e.webp").forEach {
            LogoStorage.getLogoFile(it).writeText("x")
        }
        assertEquals(5, LogoStorage.listLogos().size)
    }

    @Test
    fun `a missing file loads as null rather than throwing`() {
        assertNull(LogoStorage.loadLogoData(File(temp, "absent.png")))
    }

    @Test
    fun `a png loads as a base64 data URL carrying its real dimensions`() {
        val data = assertNotNull(LogoStorage.loadLogoData(pngFile("logo.png", w = 12, h = 5)))

        assertTrue(data.dataUrl.startsWith("data:image/png;base64,"))
        assertEquals(12, data.width)
        assertEquals(5, data.height)
    }

    @Test
    fun `the mime type follows the file extension`() {
        val cases = mapOf(
            "logo.jpg" to "image/jpeg",
            "logo.jpeg" to "image/jpeg",
            "logo.svg" to "image/svg+xml",
            "logo.webp" to "image/webp",
            "logo.bin" to "image/png",
        )
        cases.forEach { (name, mime) ->
            val file = File(temp, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val data = assertNotNull(LogoStorage.loadLogoData(file), name)
            assertTrue(data.dataUrl.startsWith("data:$mime;base64,"), "$name -> ${data.dataUrl.take(32)}")
        }
    }

    @Test
    fun `a file ImageIO cannot decode still loads, at a fallback size`() {
        val svg = File(temp, "logo.svg").apply { writeText("<svg/>") }
        val data = assertNotNull(LogoStorage.loadLogoData(svg))

        assertEquals(100, data.width)
        assertEquals(100, data.height)
    }

    @Test
    fun `importing copies the file into the logo folder under its own name`() {
        val dest = assertNotNull(LogoStorage.importLogo(pngFile("brand.png")))

        assertEquals("brand.png", dest.name)
        assertTrue(dest.isFile)
        assertEquals(listOf("brand.png"), LogoStorage.listLogos())
    }

    @Test
    fun `importing the same name twice overwrites rather than failing`() {
        LogoStorage.importLogo(pngFile("brand.png", w = 8))
        val second = assertNotNull(LogoStorage.importLogo(pngFile("brand.png", w = 20)))

        assertEquals(1, LogoStorage.listLogos().size)
        assertEquals(20, assertNotNull(LogoStorage.loadLogoData(second)).width)
    }

    @Test
    fun `importing a missing source reports null rather than throwing`() {
        assertNull(LogoStorage.importLogo(File(temp, "nope.png")))
    }
}
