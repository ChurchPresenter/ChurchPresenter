package org.churchpresenter.canvas

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a camera frame is read off ffmpeg's pipe. On Windows a read asking for a whole 1080p frame
 * (8 MB) ran at a fortieth of the speed of 256 KB reads, and the camera fell eleven times behind.
 */
class CameraFrameReadTest {

    /** Hands out what it holds, and remembers the largest amount any one read asked for. */
    private class RecordingStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var largestRequest = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            largestRequest = maxOf(largestRequest, len)
            return super.read(b, off, len)
        }
    }

    private val frameBytes = 1920 * 1080 * 4

    @Test
    fun `a 1080p frame is read whole, in chunks no larger than the cap`() {
        val source = ByteArray(frameBytes) { it.toByte() }
        val stream = RecordingStream(source)
        val frame = ByteArray(frameBytes)
        assertTrue(SharedCameraFrameCache.readFullFrame(stream, frame, frameBytes))
        assertContentEquals(source, frame)
        assertTrue(stream.largestRequest <= READ_CHUNK_BYTES, "largest read was ${stream.largestRequest} bytes")
    }

    @Test
    fun `a stream that ends partway through a frame is reported, not returned as a frame`() {
        val stream = RecordingStream(ByteArray(frameBytes / 2))
        assertFalse(SharedCameraFrameCache.readFullFrame(stream, ByteArray(frameBytes), frameBytes))
    }
}
