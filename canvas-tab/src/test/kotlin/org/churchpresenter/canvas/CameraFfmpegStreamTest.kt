package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading frames off ffmpeg.
 *
 * The webcam half of the cache — the half a DeckLink stub cannot reach. It never ran, because every
 * function took a live `Process`. `Process` is abstract though, so a stand-in can hand over exactly
 * the bytes ffmpeg would, which makes the whole path deterministic: no camera, no binary, no timing.
 *
 * The reassembly is the part that earns a test. A pipe delivers arbitrary chunks, not frames, so
 * `readFullFrame` has to keep reading until it has a whole picture — get that wrong and every frame
 * after the first is torn, which on a canvas looks like a corrupted camera rather than a bug here.
 */
class CameraFfmpegStreamTest {

    /** ffmpeg, minus ffmpeg: hands back the stderr and the frame bytes it is given. */
    private class FakeFfmpeg(
        stderr: String,
        frameBytes: ByteArray,
        private val exitCode: Int = 0,
    ) : Process() {
        private val out = ByteArrayInputStream(frameBytes)
        private val err = ByteArrayInputStream(stderr.toByteArray())
        var destroyed = false
            private set

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = out
        override fun getErrorStream(): InputStream = err
        override fun waitFor(): Int = exitCode
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true
        override fun exitValue(): Int = exitCode
        override fun isAlive(): Boolean = false
        override fun destroy() { destroyed = true }
        override fun destroyForcibly(): Process { destroyed = true; return this }
    }

    /**
     * The line ffmpeg prints when it has worked out the picture size.
     *
     * Two digits minimum per axis, because that is what the parser matches — a real capture is never
     * 4x3, and a fixture that is silently fails to announce anything.
     */
    private fun announces(w: Int, h: Int) =
        "  Stream #0:0: Video: rawvideo (BGRA / 0x41524742), bgra, ${w}x$h, 30 fps, 30 tbr\n"

    /** [count] frames of solid colour, in the BGRA byte order ffmpeg emits. */
    private fun frames(w: Int, h: Int, count: Int): ByteArray {
        val one = ByteArray(w * h * 4)
        for (i in one.indices step 4) {
            one[i] = 0x40; one[i + 1] = 0x80.toByte(); one[i + 2] = 0xC0.toByte(); one[i + 3] = 0xFF.toByte()
        }
        return ByteArray(one.size * count).also { all ->
            repeat(count) { n -> one.copyInto(all, n * one.size) }
        }
    }

    // ── readFullFrame ───────────────────────────────────────────────────────────

    @Test
    fun `a frame arriving in one piece is read whole`() {
        val buf = ByteArray(16)
        val stream = ByteArrayInputStream(ByteArray(16) { it.toByte() })

        assertTrue(SharedCameraFrameCache.readFullFrame(stream, buf, 16))
        assertEquals(15, buf[15].toInt())
    }

    @Test
    fun `a frame split across several chunks is reassembled`() {
        // A pipe hands over whatever it has; the reader has to keep going until the frame is whole.
        val dribbling = object : InputStream() {
            private val data = ByteArray(16) { it.toByte() }
            private var pos = 0
            override fun read(): Int = if (pos < data.size) data[pos++].toInt() else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= data.size) return -1
                val n = minOf(3, len, data.size - pos)   // never more than three bytes at a time
                System.arraycopy(data, pos, b, off, n); pos += n
                return n
            }
        }
        val buf = ByteArray(16)

        assertTrue(SharedCameraFrameCache.readFullFrame(dribbling, buf, 16))
        assertEquals(15, buf[15].toInt(), "the tail of the frame is missing")
    }

    @Test
    fun `a stream that ends mid-frame is reported as incomplete`() {
        val buf = ByteArray(16)
        val short = ByteArrayInputStream(ByteArray(9))

        // Half a picture is not a picture — drawing it would tear.
        assertTrue(!SharedCameraFrameCache.readFullFrame(short, buf, 16))
    }

    @Test
    fun `a stream that throws is reported as incomplete rather than propagating`() {
        val broken = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("pipe closed")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw java.io.IOException("pipe closed")
        }

        assertTrue(!SharedCameraFrameCache.readFullFrame(broken, ByteArray(16), 16))
    }

    // ── readFramesInto ──────────────────────────────────────────────────────────

    @Test
    fun `every whole frame in the stream reaches the entry`() = runBlocking {
        val entry = SharedCameraFrameCache.CacheEntry()
        val process = FakeFfmpeg(announces(16, 12), frames(16, 12, count = 3))

        val count = SharedCameraFrameCache.readFramesInto(process, entry, 16, 12)

        assertEquals(3, count)
        assertNotNull(entry.frame.value)
        assertEquals(16, entry.frame.value?.width)
        assertEquals(12, entry.frame.value?.height)
    }

    @Test
    fun `a stream carrying nothing yields no frames`() = runBlocking {
        val entry = SharedCameraFrameCache.CacheEntry()
        val process = FakeFfmpeg(announces(16, 12), ByteArray(0))

        assertEquals(0, SharedCameraFrameCache.readFramesInto(process, entry, 16, 12))
        assertNull(entry.frame.value)
    }

    @Test
    fun `a trailing partial frame is dropped rather than drawn`() = runBlocking {
        val entry = SharedCameraFrameCache.CacheEntry()
        val oneAndABit = frames(16, 12, count = 1) + ByteArray(7)
        val process = FakeFfmpeg(announces(16, 12), oneAndABit)

        assertEquals(1, SharedCameraFrameCache.readFramesInto(process, entry, 16, 12))
    }

    // ── streamFrames ────────────────────────────────────────────────────────────

    @Test
    fun `a capture that announces a size and delivers reports frames`() = runBlocking {
        val entry = SharedCameraFrameCache.CacheEntry()
        val process = FakeFfmpeg(announces(16, 12), frames(16, 12, count = 2))

        assertEquals(CaptureOutcome.FRAMES, SharedCameraFrameCache.streamFrames(process, entry))
        assertNotNull(entry.frame.value)
    }

    @Test
    fun `a capture that never announces a size says so`() = runBlocking {
        val entry = SharedCameraFrameCache.CacheEntry()
        // ffmpeg chatter with no stream line at all — what a bad device path produces.
        val process = FakeFfmpeg("ffmpeg version 6.0\n  Could not find video device\n", ByteArray(0))

        assertEquals(CaptureOutcome.NO_DIMENSIONS, SharedCameraFrameCache.streamFrames(process, entry))
    }

    @Test
    fun `a capture that announces a size then sends nothing is distinguished from one that never starts`() =
        runBlocking {
            val entry = SharedCameraFrameCache.CacheEntry()
            val process = FakeFfmpeg(announces(32, 32), ByteArray(0))

            // The two failures need different wording for the operator, so they are different values.
            assertEquals(CaptureOutcome.NO_FRAMES, SharedCameraFrameCache.streamFrames(process, entry))
        }

    @Test
    fun `the process reference is let go once the capture ends`() = runBlocking {
        val entry = SharedCameraFrameCache.CacheEntry()
        val process = FakeFfmpeg(announces(16, 12), frames(16, 12, count = 1))

        SharedCameraFrameCache.streamFrames(process, entry)

        // Held while streaming so `release` can kill it, dropped afterwards so a later release does
        // not go hunting a process that has already gone.
        assertNull(entry.ffmpegProcess)
    }

    @Test
    fun `the reference is let go even when the capture failed`() = runBlocking {
        val entry = SharedCameraFrameCache.CacheEntry()
        val process = FakeFfmpeg("ffmpeg version 6.0\n  Could not find video device\n", ByteArray(0))

        SharedCameraFrameCache.streamFrames(process, entry)

        assertNull(entry.ffmpegProcess)
    }

    // ── bgraBytesToArgbPixels ───────────────────────────────────────────────────

    @Test
    fun `bgra bytes become argb pixels in the right order`() {
        val src = byteArrayOf(0x40, 0x80.toByte(), 0xC0.toByte(), 0xFF.toByte())
        val out = IntArray(1)

        bgraBytesToArgbPixels(src, out)

        // ffmpeg gives blue, green, red, alpha; Compose wants alpha, red, green, blue.
        assertEquals(0xFFC08040.toInt(), out[0])
    }

    @Test
    fun `every pixel of a larger frame is converted`() {
        val src = ByteArray(4 * 4).also { for (i in it.indices step 4) {
            it[i] = 0x11; it[i + 1] = 0x22; it[i + 2] = 0x33; it[i + 3] = 0xFF.toByte()
        } }
        val out = IntArray(4)

        bgraBytesToArgbPixels(src, out)

        assertTrue(out.all { it == 0xFF332211.toInt() }, out.joinToString { Integer.toHexString(it) })
    }
}
