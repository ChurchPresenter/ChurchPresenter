package org.churchpresenter.media.presenter

import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.media.composables.SoftwareVlc
import uk.co.caprica.vlcj.player.base.MediaPlayer
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

class EmbeddedVideoDecoderFramesTest {

    private fun decoder() = EmbeddedVideoDecoder(
        File("clip.mp4"),
        BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB),
        Rectangle(0, 0, 4, 4),
    )

    private fun poster() = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)

    private fun pixels(vararg argb: Int): ByteBuffer =
        ByteBuffer.allocate(argb.size * 4).order(ByteOrder.BIG_ENDIAN).apply {
            argb.forEach { putInt(it) }
            rewind()
        }

    @Test
    fun `the buffer is sized to what VLC reports`() {
        val frames = DecodedFrames()
        val format = frames.bufferFormatCallback.getBufferFormat(2, 1)
        assertEquals(2, format.width)
        assertEquals(1, format.height)
        assertEquals(2, frames.frame?.width)
    }

    @Test
    fun `a frame before the buffer is sized is dropped`() {
        val frames = DecodedFrames()
        frames.renderCallback.display(mockk(relaxed = true), arrayOf(pixels(1)), mockk(relaxed = true))
        assertEquals(0L, frames.version)
    }

    @Test
    fun `a frame with no native buffer is dropped`() {
        val frames = DecodedFrames()
        frames.bufferFormatCallback.getBufferFormat(1, 1)
        frames.renderCallback.display(mockk(relaxed = true), emptyArray(), mockk(relaxed = true))
        assertEquals(0L, frames.version)
    }

    @Test
    fun `a delivered frame is copied and counted`() {
        val frames = DecodedFrames()
        frames.bufferFormatCallback.getBufferFormat(2, 1)
        val delivered = arrayOf(pixels(0x00112233, 0x00445566))
        frames.renderCallback.display(mockk(relaxed = true), delivered, mockk(relaxed = true))
        assertEquals(1L, frames.version)
        assertEquals(0x445566, frames.frame!!.getRGB(1, 0) and 0xFFFFFF)
    }

    @Test
    fun `a buffer that is not plain ints is dropped`() {
        val frames = DecodedFrames()
        frames.frame = BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)
        frames.renderCallback.display(mockk(relaxed = true), arrayOf(pixels(1)), mockk(relaxed = true))
        assertEquals(0L, frames.version)
    }

    @Test
    fun `the player's events confirm playing and paused`() {
        val target = decoder()
        val mp = mockk<MediaPlayer>(relaxed = true)
        target.events.playing(mp)
        assertTrue(target.confirmedPlaying)
        target.events.paused(mp)
        assertTrue(target.confirmedPaused)
        assertFalse(target.confirmedPlaying)
        target.events.error(mp)
    }

    @Test
    fun `beginning opens the video silently and composites what arrives`() {
        val target = decoder()
        val mp = mockk<MediaPlayer>(relaxed = true)
        target.begin(mp)
        verify { mp.events().addMediaPlayerEventListener(target.events) }
        verify { mp.audio().setVolume(0) }
        val path = File("clip.mp4").absolutePath
        verify { mp.media().play(path, ":codec=avcodec", ":avcodec-fast", ":clock-jitter=0") }
        assertNull(target.latestFrame)
        target.frames.bufferFormatCallback.getBufferFormat(1, 1)
        val delivered = arrayOf(pixels(0x00FF0000))
        target.frames.renderCallback.display(mockk(relaxed = true), delivered, mockk(relaxed = true))
        val deadline = System.nanoTime() + 5_000_000_000L
        while (target.latestFrame == null && System.nanoTime() < deadline) Thread.onSpinWait()
        assertNotNull(target.latestFrame)
        target.close()
    }

    @Test
    fun `resume and pause before anything has started do nothing`() {
        val target = decoder()
        target.resume()
        target.pause()
        assertFalse(target.confirmedPlaying)
    }

    @Test
    fun `starting with no VLC to open leaves the poster alone`() {
        val target = EmbeddedVideoDecoder(File("clip.mp4"), poster(), Rectangle(0, 0, 4, 4)) { null }
        target.start()
        target.resume()
        assertNull(target.latestFrame)
        target.close()
    }

    @Test
    fun `starting opens VLC onto this decoder's frames, and closing lets it go`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        var attached = false
        var released = 0
        val vlc = SoftwareVlc(mp, { format, render ->
            attached = true
            format.getBufferFormat(2, 2)
            render.display(mp, arrayOf(pixels(1, 2, 3, 4)), mockk(relaxed = true))
        }, { released++ })
        val target = EmbeddedVideoDecoder(File("clip.mp4"), poster(), Rectangle(0, 0, 4, 4)) { vlc }
        target.start()
        assertTrue(attached)
        assertEquals(1L, target.frames.version)
        verify { mp.audio().setVolume(0) }
        target.close()
        assertEquals(1, released)
        verify { mp.controls().stop() }
    }

    @Test
    fun `a closed decoder does not open VLC again`() {
        var opened = 0
        val target = EmbeddedVideoDecoder(File("clip.mp4"), poster(), Rectangle(0, 0, 4, 4)) {
            opened++
            null
        }
        target.close()
        target.start()
        assertEquals(0, opened)
    }
}
