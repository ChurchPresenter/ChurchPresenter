package org.churchpresenter.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeicDecoderTest {

    @Test
    fun `a missing file yields null instead of throwing`() {
        assertNull(HeicDecoder.toJpegBytes(File("/no/such/path/photo.heic")))
    }

    @Test
    fun `a file that is not an image yields null`() {
        val notAnImage = Files.createTempFile("cp-heic-test", ".heic").toFile()
        try {
            notAnImage.writeText("this is plain text, not a HEIC container")
            assertNull(HeicDecoder.toJpegBytes(notAnImage))
        } finally {
            notAnImage.delete()
        }
    }

    @Test
    fun `an empty file yields null`() {
        val empty = Files.createTempFile("cp-heic-empty", ".heic").toFile()
        try {
            assertNull(HeicDecoder.toJpegBytes(empty))
        } finally {
            empty.delete()
        }
    }

    @Test
    fun `a directory passed instead of a file yields null`() {
        val dir = Files.createTempDirectory("cp-heic-dir").toFile()
        try {
            assertNull(HeicDecoder.toJpegBytes(dir))
        } finally {
            dir.delete()
        }
    }

    // The ImageIO fallback is the non-macOS decode path, so `sips`-based toJpegBytes never reaches
    // it on the dev/CI mac; call it directly to exercise the real decode on any platform.

    @Test
    fun `the ImageIO fallback turns a readable image into valid JPEG bytes`() {
        val png = Files.createTempFile("cp-heic-io", ".png").toFile()
        try {
            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", png)
            val jpeg = HeicDecoder.convertWithImageIO(png)
            assertNotNull(jpeg, "a readable image should convert")
            assertTrue(jpeg.isNotEmpty(), "converted JPEG must not be empty")
            assertNotNull(ImageIO.read(ByteArrayInputStream(jpeg)), "output must itself be a decodable image")
        } finally {
            png.delete()
        }
    }

    @Test
    fun `the ImageIO fallback yields null for a file it cannot decode`() {
        val notAnImage = Files.createTempFile("cp-heic-io-bad", ".png").toFile()
        try {
            notAnImage.writeText("not image bytes")
            assertNull(HeicDecoder.convertWithImageIO(notAnImage))
        } finally {
            notAnImage.delete()
        }
    }
}
