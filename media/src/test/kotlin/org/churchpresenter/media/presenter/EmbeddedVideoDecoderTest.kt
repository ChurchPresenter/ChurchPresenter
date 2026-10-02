package org.churchpresenter.media.presenter

import androidx.compose.ui.graphics.toAwtImage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import uk.co.caprica.vlcj.player.base.AudioApi
import uk.co.caprica.vlcj.player.base.ControlsApi
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmbeddedVideoDecoderTest {

    private fun solidImage(width: Int, height: Int, rgb: Int): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(rgb)
        g.fillRect(0, 0, width, height)
        g.dispose()
        return img
    }

    private fun decoder(
        poster: BufferedImage = solidImage(10, 10, 0xFF0000),
        contentRect: Rectangle = Rectangle(2, 3, 4, 4),
    ) = EmbeddedVideoDecoder(File("does-not-need-to-exist.mp4"), poster, contentRect)

    @Test
    fun `composite blits the decoded frame into the poster at the content rect`() {
        val poster = solidImage(10, 10, 0xFF0000)
        val content = solidImage(4, 4, 0x0000FF)
        val target = decoder(poster, Rectangle(2, 3, 4, 4))
        target.decodedFrame = content

        target.composite()

        val frame = assertNotNull(target.latestFrame)
        val awt = frame.toAwtImage()
        assertEquals(10, awt.width)
        assertEquals(10, awt.height)
        assertEquals(0xFF0000FF.toInt(), awt.getRGB(3, 4))
        assertEquals(0xFFFF0000.toInt(), awt.getRGB(0, 0))
    }

    @Test
    fun `composite is a no-op without a decoded frame`() {
        val target = decoder()

        target.composite()

        assertNull(target.latestFrame)
    }

    @Test
    fun `resume unmutes once and reissues play until confirmed`() {
        val mp = mockk<EmbeddedMediaPlayer>(relaxed = true)
        val controls = mockk<ControlsApi>(relaxed = true)
        val audio = mockk<AudioApi>(relaxed = true)
        every { mp.controls() } returns controls
        every { mp.audio() } returns audio
        val target = decoder()
        target.mp = mp

        target.resume()
        verify(exactly = 1) { audio.setVolume(100) }
        verify(exactly = 1) { controls.play() }

        target.resume()
        verify(exactly = 1) { audio.setVolume(100) }
        verify(exactly = 2) { controls.play() }

        target.onPlayingConfirmed()
        target.resume()
        verify(exactly = 2) { controls.play() }
    }

    @Test
    fun `pause reissues pause until confirmed`() {
        val mp = mockk<EmbeddedMediaPlayer>(relaxed = true)
        val controls = mockk<ControlsApi>(relaxed = true)
        every { mp.controls() } returns controls
        val target = decoder()
        target.mp = mp

        target.pause()
        verify(exactly = 1) { controls.pause() }

        target.pause()
        verify(exactly = 2) { controls.pause() }

        target.onPausedConfirmed()
        target.pause()
        verify(exactly = 2) { controls.pause() }
    }

    @Test
    fun `onErrorEvent does not throw`() {
        val target = decoder()

        target.onErrorEvent()
    }

    @Test
    fun `onPlayingConfirmed and onPausedConfirmed are mutually exclusive`() {
        val target = decoder()

        target.onPlayingConfirmed()
        assertTrue(target.confirmedPlaying)
        assertFalse(target.confirmedPaused)

        target.onPausedConfirmed()
        assertFalse(target.confirmedPlaying)
        assertTrue(target.confirmedPaused)
    }

    @Test
    fun `allocateDecodedFrame clamps zero or negative VLC-reported dimensions to 1`() {
        val target = decoder()

        assertEquals(1 to 1, target.allocateDecodedFrame(0, 0).let { it.width to it.height })
        assertEquals(1 to 1, target.allocateDecodedFrame(-5, -5).let { it.width to it.height })
        assertEquals(640 to 360, target.allocateDecodedFrame(640, 360).let { it.width to it.height })
    }

    @Test
    fun `copyFrameBytes copies whole pixels bounded by the native buffer's actual size`() {
        val target = decoder()
        val pixelData = IntArray(4)
        val buf = ByteBuffer.allocate(4 * 4).order(ByteOrder.BIG_ENDIAN)
        buf.asIntBuffer().put(intArrayOf(0x11, 0x22, 0x33, 0x44))

        val copied = target.copyFrameBytes(buf, pixelData)

        assertEquals(4, copied)
        assertEquals(listOf(0x11, 0x22, 0x33, 0x44), pixelData.toList())
    }

    @Test
    fun `copyFrameBytes stops at the buffer's boundary instead of overrunning it`() {
        val target = decoder()
        val pixelData = IntArray(4)
        val buf = ByteBuffer.allocate(2 * 4).order(ByteOrder.BIG_ENDIAN)
        buf.asIntBuffer().put(intArrayOf(0x11, 0x22))

        val copied = target.copyFrameBytes(buf, pixelData)

        assertEquals(2, copied)
        assertEquals(listOf(0x11, 0x22, 0, 0), pixelData.toList())
    }

    @Test
    fun `pausing resets the one-time volume gate so the next resume unmutes again`() {
        val mp = mockk<EmbeddedMediaPlayer>(relaxed = true)
        val controls = mockk<ControlsApi>(relaxed = true)
        val audio = mockk<AudioApi>(relaxed = true)
        every { mp.controls() } returns controls
        every { mp.audio() } returns audio
        val target = decoder()
        target.mp = mp

        target.resume()
        target.pause()
        target.resume()

        verify(exactly = 2) { audio.setVolume(100) }
    }

    @Test
    fun `resume and pause after close do nothing`() {
        val mp = mockk<EmbeddedMediaPlayer>(relaxed = true)
        val controls = mockk<ControlsApi>(relaxed = true)
        every { mp.controls() } returns controls
        val target = decoder()
        target.mp = mp

        target.close()
        target.resume()
        target.pause()

        verify(exactly = 0) { controls.play() }
        verify(exactly = 0) { controls.pause() }
    }

    @Test
    fun `close stops the player`() {
        val mp = mockk<EmbeddedMediaPlayer>(relaxed = true)
        val controls = mockk<ControlsApi>(relaxed = true)
        every { mp.controls() } returns controls
        val target = decoder()
        target.mp = mp

        target.close()

        verify(exactly = 1) { controls.stop() }
    }

    @Test
    fun `close does not throw when nothing was ever started`() {
        val target = decoder()

        target.close()
        target.close()
    }
}
