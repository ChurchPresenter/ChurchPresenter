package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import io.mockk.mockk
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two plain (non-composable) callback objects VLC's render thread drives directly:
 * [rv32BufferFormatCallback] on a size change, [frameRenderCallback] on every decoded frame. Both
 * are pure JVM objects -- no display, no libvlc handle -- so they're driven directly rather than
 * through `VideoPlayer`/`SoftwareVideoPlayer`, which need a real player to compose at all.
 */
class RenderCallbacksTest {

    // ── rv32BufferFormatCallback ────────────────────────────────────────────────────────────

    @Test
    fun `getBufferFormat allocates a ping-pong pair at the given size`() {
        val pingPong = mutableStateOf<FramePingPong?>(null)
        val callback = rv32BufferFormatCallback(pingPong)

        callback.getBufferFormat(640, 360)

        val pp = assertNotNull(pingPong.value)
        assertEquals(640 to 360, pp.writeTarget.width to pp.writeTarget.height)
    }

    @Test
    fun `getBufferFormat coerces a non-positive size up to 1`() {
        val pingPong = mutableStateOf<FramePingPong?>(null)
        val callback = rv32BufferFormatCallback(pingPong)

        callback.getBufferFormat(0, -5)

        val pp = assertNotNull(pingPong.value)
        assertEquals(1 to 1, pp.writeTarget.width to pp.writeTarget.height)
    }

    @Test
    fun `allocatedBuffers does not throw`() {
        val callback = rv32BufferFormatCallback(mutableStateOf(null))
        callback.allocatedBuffers(arrayOf())
    }

    // ── frameRenderCallback ─────────────────────────────────────────────────────────────────

    private fun nativeBuffer(pixels: IntArray): ByteBuffer =
        ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.nativeOrder()).also {
            it.asIntBuffer().put(pixels)
        }

    @Test
    fun `a real frame is copied into the write target and published`() {
        val pingPong = mutableStateOf<FramePingPong?>(FramePingPong(2, 1))
        val displayHolder = mutableStateOf<BufferedImage?>(null)
        val firstFrameCaptured = mutableStateOf(false)
        val frameVersion = mutableLongStateOf(0L)
        val target = frameRenderCallback(pingPong, displayHolder, firstFrameCaptured, frameVersion)
        val writtenTo = pingPong.value!!.writeTarget

        target.display(mockk<MediaPlayer>(relaxed = true), arrayOf(nativeBuffer(intArrayOf(0x11223344, 0x55))), null)

        assertEquals(displayHolder.value, writtenTo, "the buffer that was written to is the one published")
        assertTrue(firstFrameCaptured.value)
        assertEquals(1L, frameVersion.longValue)
        val pixelData = (writtenTo.raster.dataBuffer as DataBufferInt).data
        assertEquals(0x11223344, pixelData[0])
    }

    @Test
    fun `a missing ping-pong pair is a no-op`() {
        val pingPong = mutableStateOf<FramePingPong?>(null)
        val displayHolder = mutableStateOf<BufferedImage?>(null)
        val firstFrameCaptured = mutableStateOf(false)
        val frameVersion = mutableLongStateOf(0L)
        val target = frameRenderCallback(pingPong, displayHolder, firstFrameCaptured, frameVersion)

        target.display(mockk<MediaPlayer>(relaxed = true), arrayOf(nativeBuffer(intArrayOf(1))), null)

        assertNull(displayHolder.value)
        assertFalse(firstFrameCaptured.value)
        assertEquals(0L, frameVersion.longValue)
    }

    private fun callbackWithFreshBuffers(displayHolder: androidx.compose.runtime.MutableState<BufferedImage?>) =
        frameRenderCallback(
            mutableStateOf<FramePingPong?>(FramePingPong(2, 1)),
            displayHolder,
            mutableStateOf(false),
            mutableLongStateOf(0L),
        )

    @Test
    fun `null native buffers is a no-op`() {
        val displayHolder = mutableStateOf<BufferedImage?>(null)
        val target = callbackWithFreshBuffers(displayHolder)

        target.display(mockk<MediaPlayer>(relaxed = true), null, null)

        assertNull(displayHolder.value)
    }

    @Test
    fun `empty native buffers is a no-op`() {
        val displayHolder = mutableStateOf<BufferedImage?>(null)
        val target = callbackWithFreshBuffers(displayHolder)

        target.display(mockk<MediaPlayer>(relaxed = true), arrayOf(), null)

        assertNull(displayHolder.value)
    }

    @Test
    fun `a null first buffer is a no-op rather than a crash`() {
        val displayHolder = mutableStateOf<BufferedImage?>(null)
        val target = callbackWithFreshBuffers(displayHolder)

        target.display(mockk<MediaPlayer>(relaxed = true), arrayOf(null), null)

        assertNull(displayHolder.value)
    }

    @Test
    fun `successive frames alternate which buffer is published`() {
        val pingPong = mutableStateOf<FramePingPong?>(FramePingPong(1, 1))
        val displayHolder = mutableStateOf<BufferedImage?>(null)
        val target = frameRenderCallback(pingPong, displayHolder, mutableStateOf(false), mutableLongStateOf(0L))

        target.display(mockk<MediaPlayer>(relaxed = true), arrayOf(nativeBuffer(intArrayOf(1))), null)
        val first = displayHolder.value
        target.display(mockk<MediaPlayer>(relaxed = true), arrayOf(nativeBuffer(intArrayOf(2))), null)
        val second = displayHolder.value

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first !== second, "the two frames must land in different buffers")
    }
}
