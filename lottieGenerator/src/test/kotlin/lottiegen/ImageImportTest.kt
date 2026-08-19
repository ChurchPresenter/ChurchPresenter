package lottiegen

import lottiegen.editor.ImageImport
import lottiegen.model.AnimationStyle
import lottiegen.model.LottieAlignment
import lottiegen.model.LottieFont
import lottiegen.model.TextTransform
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Preparing an image for embedding in a style spec, and the little enum lookup tables.
 *
 * Embedding means base64 inside a JSON document that ships with the app, so the two properties
 * that matter are that a large source is downscaled (an un-resized 4K photo would balloon the
 * spec) and that a source with transparency keeps it — a logo flattened onto black is worse than
 * no logo.
 */
class ImageImportTest {

    private val temp: File = Files.createTempDirectory("lottiegen-image-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun image(name: String, w: Int, h: Int, type: Int = BufferedImage.TYPE_INT_ARGB): File {
        val img = BufferedImage(w, h, type)
        val g = img.createGraphics()
        g.color = Color(200, 30, 30)
        g.fillRect(0, 0, w / 2, h)
        g.dispose()
        val file = File(temp, name)
        ImageIO.write(img, if (name.endsWith(".jpg")) "jpg" else "png", file)
        return file
    }

    @Test
    fun `a small PNG is embedded at its own size`() {
        val result = ImageImport.import(image("small.png", 64, 32))!!
        assertEquals(64, result.width)
        assertEquals(32, result.height)
        assertTrue(result.dataUri.startsWith("data:image/png;base64,"))
        assertTrue(result.encodedBytes > 0)
    }

    @Test
    fun `an oversized image is downscaled to the long-edge limit`() {
        // Without this an imported photo balloons the spec that ships with the app.
        val result = ImageImport.import(image("huge.png", 4000, 2000))!!
        assertEquals(ImageImport.MAX_DIMENSION, result.width, "the long edge is capped")
        assertEquals(ImageImport.MAX_DIMENSION / 2, result.height, "and the aspect ratio is kept")
    }

    @Test
    fun `a tall image is capped on its own long edge`() {
        val result = ImageImport.import(image("tall.png", 1000, 4000))!!
        assertEquals(ImageImport.MAX_DIMENSION, result.height)
        assertEquals(ImageImport.MAX_DIMENSION / 4, result.width)
    }

    @Test
    fun `an image already within the limit is not upscaled`() {
        val result = ImageImport.import(image("exact.png", 800, 600))!!
        assertEquals(800, result.width, "no enlargement, which would only add bytes")
        assertEquals(600, result.height)
    }

    @Test
    fun `a JPEG stays a JPEG`() {
        // JPEG has no alpha to preserve and is smaller, so re-encoding it as PNG would only bloat.
        val result = ImageImport.import(image("photo.jpg", 200, 100, BufferedImage.TYPE_INT_RGB))!!
        assertTrue(result.dataUri.startsWith("data:image/jpeg;base64,"), "got ${result.dataUri.take(32)}")
    }

    @Test
    fun `transparency survives the round trip`() {
        val src = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
        src.setRGB(0, 0, 0x00000000)          // fully transparent
        src.setRGB(19, 19, 0xFFFF0000.toInt()) // opaque red
        val file = File(temp, "alpha.png").also { ImageIO.write(src, "png", it) }

        val result = ImageImport.import(file)!!
        val decoded = ImageIO.read(
            java.io.ByteArrayInputStream(
                java.util.Base64.getDecoder().decode(result.dataUri.substringAfter("base64,"))
            )
        )
        assertEquals(0, decoded.getRGB(0, 0) ushr 24, "the transparent pixel is still transparent")
        assertEquals(255, decoded.getRGB(19, 19) ushr 24, "and the opaque one still opaque")
    }

    @Test
    fun `something that is not an image is refused`() {
        val notAnImage = File(temp, "notes.txt").apply { writeText("plain text") }
        assertNull(ImageImport.import(notAnImage))
        assertNull(ImageImport.import(File(temp, "missing.png")))
    }

    // ── Enum lookups ──────────────────────────────────────────────────────────

    @Test
    fun `every animation style round-trips through its id`() {
        for (style in AnimationStyle.entries) {
            assertEquals(style, AnimationStyle.fromId(style.id), "${style.name} round-trips")
            assertTrue(style.labelKey.isNotBlank(), "${style.name} has a label key")
        }
        assertEquals(
            AnimationStyle.entries.size,
            AnimationStyle.entries.map { it.id }.distinct().size,
            "ids are unique, so a saved preset resolves to one style",
        )
    }

    @Test
    fun `every alignment and transform round-trips through its id`() {
        for (a in LottieAlignment.entries) assertEquals(a, LottieAlignment.fromId(a.id))
        for (t in TextTransform.entries) assertEquals(t, TextTransform.fromId(t.id))
    }

    @Test
    fun `every font declares a family name and at least one weight`() {
        for (font in LottieFont.entries) {
            assertTrue(font.familyName.isNotBlank(), "${font.name} names a family")
            assertTrue(font.hasRegular || font.hasBold, "${font.name} ships something usable")
        }
    }
}
