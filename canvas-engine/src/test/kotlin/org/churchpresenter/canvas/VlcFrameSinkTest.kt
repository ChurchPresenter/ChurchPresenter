package org.churchpresenter.canvas

import uk.co.caprica.vlcj.media.TrackType
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlcFrameSinkTest {

    private fun pixels(vararg argb: Int): ByteBuffer =
        ByteBuffer.allocate(argb.size * 4).order(ByteOrder.BIG_ENDIAN).apply { argb.forEach { putInt(it) } }

    @Test
    fun `a picture before any size is announced is dropped`() {
        val sink = VlcFrameSink()

        sink.render(arrayOf(pixels(1, 2)))

        assertEquals(0, sink.frameVersion)
        assertNull(sink.frame)
    }

    @Test
    fun `a decoded picture is copied into the frame and bumps its version`() {
        val sink = VlcFrameSink()
        val format = sink.format(2, 1)

        sink.render(arrayOf(pixels(0x11223344, 0x55667788)))

        assertEquals(2, format.width)
        assertEquals(1, sink.frameVersion)
        val data = (sink.frame!!.raster.dataBuffer as DataBufferInt).data
        assertEquals(listOf(0x11223344, 0x55667788), data.toList())
    }

    @Test
    fun `a short buffer fills what it can`() {
        val sink = VlcFrameSink()
        sink.format(3, 1)

        sink.render(arrayOf(pixels(7)))

        val data = (sink.frame!!.raster.dataBuffer as DataBufferInt).data
        assertEquals(listOf(7, 0, 0), data.toList())
    }

    @Test
    fun `a callback with no buffers changes nothing`() {
        val sink = VlcFrameSink()
        sink.format(1, 1)

        sink.render(null)
        sink.render(emptyArray())
        sink.render(arrayOf(null))

        assertEquals(0, sink.frameVersion)
    }

    @Test
    fun `a new size replaces the picture being written into`() {
        val sink = VlcFrameSink()
        sink.format(1, 1)

        sink.format(4, 2)

        assertEquals(4, sink.frame!!.width)
    }

    @Test
    fun `the volume asked for is applied, and zero is passed through as a mute`() {
        val applied = mutableListOf<Int>()
        val volume = VlcVolume { applied += it }

        volume.want(40)
        volume.want(0)

        assertEquals(listOf(40, 0), applied)
    }

    @Test
    fun `the volume is applied again when the audio track arrives, not for video`() {
        val applied = mutableListOf<Int>()
        val volume = VlcVolume { applied += it }

        volume.onStreamSelected(TrackType.VIDEO)
        volume.onStreamSelected(TrackType.AUDIO)

        assertEquals(listOf(100), applied)
    }

    @Test
    fun `only the first clock tick applies the volume`() {
        val applied = mutableListOf<Int>()
        val volume = VlcVolume { applied += it }

        volume.onTimeChanged()
        volume.onTimeChanged()
        volume.applyNow()

        assertEquals(listOf(100, 100), applied)
    }
}
