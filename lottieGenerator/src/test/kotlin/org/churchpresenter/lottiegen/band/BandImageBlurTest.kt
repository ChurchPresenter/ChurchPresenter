package org.churchpresenter.lottiegen.band

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BandImageBlurTest {

    /** Vertical black and white stripes, ten pixels wide: any blur at all greys the edges. */
    private fun stripes(format: String): BandImage {
        val image = BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 120) for (y in 0 until 40) image.setRGB(x, y, if ((x / 10) % 2 == 0) 0xFFFFFF else 0x000000)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
        val mime = if (format == "jpg") "image/jpeg" else "image/png"
        return BandImage("data:$mime;base64," + Base64.getEncoder().encodeToString(bytes), 120, 40, "s.$format")
    }

    private fun decode(image: BandImage): BufferedImage =
        ImageIO.read(Base64.getDecoder().decode(image.data.substringAfter(",")).inputStream())

    @Test
    fun `a blur greys the stripe edges and keeps the picture's size and kind`() {
        val source = stripes("png")
        val blurred = BandImageBlur.blurred(source, 6.0)
        assertNotEquals(source.data, blurred.data)
        assertEquals(120 to 40, blurred.width to blurred.height)
        assertEquals("s.png", blurred.name)
        assertTrue(blurred.data.startsWith("data:image/png;base64,"), "a PNG stays a PNG")
        val edge = decode(blurred).getRGB(10, 20) and 0xFF
        assertTrue(edge in 1..254, "the edge between white and black is grey now, got $edge")
        val jpeg = BandImageBlur.blurred(stripes("jpg"), 6.0)
        assertTrue(jpeg.data.startsWith("data:image/jpeg;base64,"), "a JPEG stays a JPEG")
    }

    @Test
    fun `no blur, an unusable picture and unreadable bytes all come back untouched`() {
        val source = stripes("png")
        assertSame(source, BandImageBlur.blurred(source, 0.0))
        assertSame(source, BandImageBlur.blurred(source, 0.9), "under a pixel rounds to none")
        val empty = BandImage("data:image/png;base64,", 0, 0, "e.png")
        assertSame(empty, BandImageBlur.blurred(empty, 4.0))
        val garbage = BandImage("data:image/png;base64,AAAA", 4, 4, "g.png")
        assertSame(garbage, BandImageBlur.blurred(garbage, 4.0), "bytes Skia cannot read are left for the player")
        val notBase64 = BandImage("data:image/png;base64,***", 4, 4, "n.png")
        assertSame(notBase64, BandImageBlur.blurred(notBase64, 4.0))
    }

    @Test
    fun `the same picture at the same radius is blurred once`() {
        val source = stripes("png")
        val first = BandImageBlur.blurred(source, 5.7)
        val again = BandImageBlur.blurred(source, 5.2)
        assertSame(first, again, "5.7 and 5.2 both round to 5 and hit the cache")
        assertNotEquals(first.data, BandImageBlur.blurred(source, 12.0).data, "a different radius, a different picture")
    }
}
