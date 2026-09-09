package org.churchpresenter.app.churchpresenter.utils

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fallbacks behind [PictureDecoder].
 *
 * Skia reads the web formats and refuses everything else with one opaque message, which is what put
 * a `.jpeg` out of a chat app on a permanently failed tile in the Pictures grid. The cases below
 * cover a file Skia cannot read but ImageIO can, that a readable file never pays for the fallback,
 * and that an unreadable one still fails with Skia's own reason rather than a fallback's.
 *
 * **Not covered: the CMYK JPEG** that the report came from. Reading one needs the TwelveMonkeys
 * plugin, which is on the classpath — but *writing* one to build the fixture does not, and a
 * committed binary fixture is not worth it when TIFF exercises the identical branch.
 */
class PictureDecoderTest {

    private lateinit var folder: File

    @BeforeTest
    fun createFolder() {
        folder = Files.createTempDirectory("cp-picture-decoder").toFile()
    }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun image(): BufferedImage =
        BufferedImage(12, 9, BufferedImage.TYPE_INT_RGB).also {
            it.createGraphics().apply {
                color = Color.RED
                fillRect(0, 0, 12, 9)
                dispose()
            }
        }

    private fun write(name: String, format: String): File =
        File(folder, name).also { assertTrue(ImageIO.write(image(), format, it), "no $format writer") }

    @Test
    fun `decodes a format Skia reads on its own`() {
        val decoded = PictureDecoder.decode(write("plain.png", "png"))

        assertEquals(12, decoded.width)
        assertEquals(9, decoded.height)
    }

    @Test
    fun `decodes a format only ImageIO reads`() {
        // Skia has no TIFF decoder; the JDK's ImageIO does, so the fallback is what answers here.
        val decoded = PictureDecoder.decode(write("scan.tiff", "tiff"))

        assertEquals(12, decoded.width)
        assertEquals(9, decoded.height)
    }

    @Test
    fun `a file no decoder can read fails with Skia's reason`() {
        val broken = File(folder, "truncated.jpeg").also { it.writeText("this is not a JPEG") }

        val failure = assertFailsWith<Exception> { PictureDecoder.decode(broken) }

        assertContains(failure.message ?: "", "truncated.jpeg")
        assertNotNull(failure.cause, "Skia's own failure is kept as the cause")
        assertNull(PictureDecoder.decodeOrNull(broken))
    }

    /**
     * The smallest valid Photoshop document: a 1x1 8-bit RGB image, uncompressed.
     *
     * Hand-built because `imageio-psd` reads PSD and does not write it, so ImageIO cannot produce
     * this fixture. The layout is the PSD file header (signature, version, channel count, height,
     * width, depth, colour mode), three empty length-prefixed sections, then the raw image data
     * preceded by its compression word.
     */
    private fun minimalPsdBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(out).use { data ->
            data.writeBytes("8BPS")
            data.writeShort(1)                  // version
            repeat(6) { data.writeByte(0) }     // reserved
            data.writeShort(3)                  // channels: R, G, B
            data.writeInt(1)                    // height
            data.writeInt(1)                    // width
            data.writeShort(8)                  // bits per channel
            data.writeShort(3)                  // colour mode: RGB
            data.writeInt(0)                    // colour mode data
            data.writeInt(0)                    // image resources
            data.writeInt(0)                    // layer and mask info
            data.writeShort(0)                  // compression: raw
            data.write(byteArrayOf(0x7F, 0x00, 0x00))  // one pixel, one byte per channel
        }
        return out.toByteArray()
    }

    @Test
    fun `decodes a Photoshop file carrying a jpg name`() {
        // The field report: an undecodable thumbnail whose leading bytes were 8BPS and whose name
        // ended .jpg. Skia refuses it and the extension is a lie, so only the ImageIO fallback --
        // and only with imageio-psd on the classpath -- can read it.
        val file = File(folder, "renamed-photoshop.jpg")
        file.writeBytes(minimalPsdBytes())

        assertNotNull(PictureDecoder.decodeOrNull(file), "imageio-psd must be on the classpath")
    }

    @Test
    fun `a Photoshop file is claimed by an ImageIO reader`() {
        // The same file's diagnosis line said "imageio=none" before the reader was added, which is
        // what made the report look like an unsupported format rather than a missing plugin.
        val file = File(folder, "photoshop.psd")
        file.writeBytes(minimalPsdBytes())

        assertContains(PictureDecoder.diagnose(file).lowercase(), "imageio=psd")
    }

    @Test
    fun `transcode returns null for bytes ImageIO cannot read`() {
        assertNull(PictureDecoder.transcodeWithImageIO("not an image".toByteArray()))
    }

    @Test
    fun `HEIF is recognised by its ftyp brand, not its extension`() {
        assertTrue(PictureDecoder.isHeif(ftyp("heic")))
        assertTrue(PictureDecoder.isHeif(ftyp("mif1")))
        // The same container carrying video is not a picture and must not reach the HEIC decoder.
        assertTrue(!PictureDecoder.isHeif(ftyp("isom")))
        assertTrue(!PictureDecoder.isHeif(ByteArray(4)))
        assertTrue(!PictureDecoder.isHeif(image().let { ImageIO.write(
            it,
            "png",
            File(folder, "p.png"),
        ); File(folder, "p.png").readBytes() }))
    }

    @Test
    fun `diagnosing a readable file names the format ImageIO claims it with`() {
        val line = PictureDecoder.diagnose(write("photo.png", "png"))

        assertContains(line, "ext=png")
        // The PNG signature, so a file renamed to the wrong extension is visible in the report.
        assertContains(line, "magic=89504E470D0A1A0A")
        assertContains(line, "imageio=png")
        assertTrue("size=0" !in line, "a written file has bytes: $line")
    }

    @Test
    fun `diagnosing a file no reader claims says so, and never carries the name`() {
        val broken = File(folder, "holiday snap.jpg").also { it.writeText("this is not a JPEG") }

        val line = PictureDecoder.diagnose(broken)

        assertContains(line, "ext=jpg")
        assertContains(line, "imageio=none")
        assertTrue("holiday" !in line, "picture names are the user's and stay local: $line")
    }

    @Test
    fun `diagnosing an empty file reports no bytes rather than an unreadable format`() {
        val placeholder = File(folder, "still-syncing.jpg").also { it.createNewFile() }

        val line = PictureDecoder.diagnose(placeholder)

        assertContains(line, "size=0")
        assertContains(line, "magic=none")
        assertContains(line, "imageio=none")
    }

    @Test
    fun `diagnosing a file deleted since the decode returns a line instead of throwing`() {
        // The failed decode and the report are not one step, so the file can be gone by now.
        val line = PictureDecoder.diagnose(File(folder, "gone.jpg"))

        assertContains(line, "size=0")
        assertContains(line, "magic=none")
    }

    private fun ftyp(brand: String): ByteArray =
        byteArrayOf(0, 0, 0, 0x18) + "ftyp".toByteArray() + brand.toByteArray() + ByteArray(4)
}
