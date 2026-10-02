package org.churchpresenter.sharedui.utils

import io.mockk.every
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [HeicDecoder] tries `sips` on macOS, then ImageIO, then ffmpeg. Its contract is "returns JPEG
 * bytes, or null" — never an exception, because it runs while loading a user's picture folder where
 * a single bad file must not take the slideshow down.
 *
 * The order is the behaviour worth pinning. Off macOS the only converter ever tried was ImageIO,
 * and no stock JDK ships a HEIF reader for it to use, so every HEIC on Windows and Linux failed —
 * which is most phone photos. The converters are injectable so that order can be exercised without
 * a Mac, an ffmpeg install, or a real HEIC file.
 */
class HeicDecoderTest {

    private fun stubBytes(marker: Byte) = ByteArray(4) { marker }

    private val neverCalled: (File) -> ByteArray? = { error("this converter should not have run") }
    private val cannotDecode: (File) -> ByteArray? = { null }

    @Test
    fun `sips wins on macOS and nothing else runs`() {
        val out = HeicDecoder.toJpegBytes(
            File("photo.heic"), onMac = true,
            sips = { stubBytes(1) }, imageIo = neverCalled, ffmpeg = neverCalled
        )

        assertContentEquals(stubBytes(1), out)
    }

    @Test
    fun `sips is never asked off macOS`() {
        val out = HeicDecoder.toJpegBytes(
            File("photo.heic"), onMac = false,
            sips = neverCalled, imageIo = { stubBytes(2) }, ffmpeg = neverCalled
        )

        assertContentEquals(stubBytes(2), out)
    }

    @Test
    fun `ffmpeg picks up what ImageIO cannot read`() {
        // The case every Windows and Linux user was in: nothing on the classpath claims HEIF, so
        // ImageIO returns null — and before this there was nothing after it.
        val out = HeicDecoder.toJpegBytes(
            File("photo.heic"), onMac = false,
            sips = neverCalled, imageIo = cannotDecode, ffmpeg = { stubBytes(3) }
        )

        assertContentEquals(stubBytes(3), out)
    }

    @Test
    fun `a Mac whose sips failed still falls through to the rest`() {
        val out = HeicDecoder.toJpegBytes(
            File("photo.heic"), onMac = true,
            sips = cannotDecode, imageIo = cannotDecode, ffmpeg = { stubBytes(4) }
        )

        assertContentEquals(stubBytes(4), out)
    }

    @Test
    fun `null when every converter available here failed`() {
        val out = HeicDecoder.toJpegBytes(
            File("photo.heic"), onMac = true,
            sips = cannotDecode, imageIo = cannotDecode, ffmpeg = cannotDecode
        )

        assertNull(out)
    }

    @Test
    fun `the ffmpeg command asks for exactly one frame`() {
        // A HEIC can carry a burst or a Live Photo's video track. Without -frames:v 1 ffmpeg writes
        // a numbered sequence and leaves the single output file this code then reads empty.
        val input = File("in.heic")
        val output = File("out.jpg")

        val command = HeicDecoder.ffmpegHeicCommand(input, output, executable = "ffmpeg")

        assertEquals("ffmpeg", command.first())
        assertEquals(output.absolutePath, command.last())
        assertTrue(command.windowed(2).contains(listOf("-frames:v", "1")), command.toString())
        assertTrue(command.windowed(2).contains(listOf("-i", input.absolutePath)), command.toString())
        // -y, or ffmpeg blocks on a prompt about the temp file createTempFile has already made.
        assertTrue("-y" in command, command.toString())
    }

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
