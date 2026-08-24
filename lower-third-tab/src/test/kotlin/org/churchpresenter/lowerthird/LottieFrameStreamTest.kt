package org.churchpresenter.lowerthird

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LottieFrameStreamTest {

    private val scopes = mutableListOf<CoroutineScope>()

    private fun newScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scopes += it }

    @AfterTest
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
    }

    private val opaqueRed = 0xFFFF0000.toInt()
    private val transparent = 0x00000000

    private fun solidFrame(width: Int, height: Int, argb: Int) = IntArray(width * height) { argb }

    private fun encodeLiteral(pixels: IntArray): ByteArray {
        val buf = ByteBuffer.allocate(4 + pixels.size * 4)
        buf.putInt(-pixels.size)
        pixels.forEach { buf.putInt(it) }
        return buf.array()
    }

    private fun encodeTruncated(pixels: IntArray): ByteArray {
        val full = encodeLiteral(pixels)
        return full.copyOf(full.size - 4)
    }

    private fun writeCacheFile(width: Int, height: Int, framePayloads: List<ByteArray>): java.io.File {
        val file = Files.createTempFile("lottie-frame-stream-test", ".lrcc").toFile()
        file.deleteOnExit()
        RandomAccessFile(file, "rw").use { raf ->
            raf.writeBytes("LRCC")
            raf.writeByte(1)
            raf.writeByte(0)
            raf.writeInt(width)
            raf.writeInt(height)
            raf.writeInt(3000)
            raf.writeInt(framePayloads.size)
            val offsets = LongArray(framePayloads.size)
            framePayloads.forEachIndexed { i, payload ->
                offsets[i] = raf.filePointer
                raf.writeInt(payload.size)
                raf.write(payload)
            }
            val footerStart = raf.filePointer
            offsets.forEach { raf.writeLong(it) }
            raf.writeLong(footerStart)
        }
        return file
    }

    private fun pollUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(5)
        }
        return condition()
    }

    @Test
    fun `an empty frame is considered blank`() {
        val stream = LottieFrameStream(writeCacheFile(1, 1, emptyList()), newScope()) {}
        assertTrue(stream.isFrameBlank(IntArray(0)))
    }

    @Test
    fun `a fully transparent frame is blank`() {
        val stream = LottieFrameStream(writeCacheFile(1, 1, emptyList()), newScope()) {}
        assertTrue(stream.isFrameBlank(solidFrame(10, 10, transparent)))
    }

    @Test
    fun `a fully opaque frame is not blank`() {
        val stream = LottieFrameStream(writeCacheFile(1, 1, emptyList()), newScope()) {}
        assertFalse(stream.isFrameBlank(solidFrame(10, 10, opaqueRed)))
    }

    @Test
    fun `exactly the 1 percent opaque threshold does not count as blank`() {
        val stream = LottieFrameStream(writeCacheFile(1, 1, emptyList()), newScope()) {}
        val frame = IntArray(100) { transparent }
        frame[0] = opaqueRed
        assertFalse(stream.isFrameBlank(frame))
    }

    @Test
    fun `just under the threshold is blank`() {
        val stream = LottieFrameStream(writeCacheFile(1, 1, emptyList()), newScope()) {}
        assertTrue(stream.isFrameBlank(IntArray(100) { transparent }))
    }

    @Test
    fun `open returns true and reports the frame count for a non-blank cache`() {
        val file = writeCacheFile(2, 2, List(3) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val stream = LottieFrameStream(file, newScope()) {}

        assertTrue(runBlocking { stream.open() })
        assertEquals(3, stream.frameCount)
    }

    @Test
    fun `open returns false for a mostly blank cache but still reports the frame count`() {
        val file = writeCacheFile(2, 2, List(4) { encodeLiteral(solidFrame(2, 2, transparent)) })
        val stream = LottieFrameStream(file, newScope()) {}

        assertFalse(runBlocking { stream.open() })
        assertEquals(4, stream.frameCount)
    }

    @Test
    fun `requestFrame decodes and publishes via the background worker`() {
        val file = writeCacheFile(2, 2, List(3) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val received = LinkedBlockingQueue<LottieFrame>()
        val stream = LottieFrameStream(file, newScope()) { frame -> frame?.let(received::add) }
        assertTrue(runBlocking { stream.open() })

        stream.requestFrame(1)

        val frame = received.poll(2, TimeUnit.SECONDS)
        assertNotNull(frame)
        assertEquals(1, frame.index)
        assertEquals(2, frame.imageBitmap.width)
        assertEquals(2, frame.imageBitmap.height)
    }

    @Test
    fun `decodeAndPublish publishes the decoded frame`() {
        val file = writeCacheFile(4, 4, List(2) { encodeLiteral(solidFrame(4, 4, opaqueRed)) })
        val received = mutableListOf<LottieFrame>()
        val stream = LottieFrameStream(file, newScope()) { it?.let(received::add) }
        assertTrue(runBlocking { stream.open() })

        stream.decodeAndPublish(0)

        assertEquals(1, received.size)
        assertEquals(0, received[0].index)
        assertEquals(4, received[0].imageBitmap.width)
        assertFalse(received[0].skiaBitmap.isClosed)
    }

    @Test
    fun `a corrupted frame is skipped without publishing or crashing the pipeline`() {
        val file = writeCacheFile(
            2, 2,
            listOf(
                encodeTruncated(solidFrame(2, 2, opaqueRed)),
                encodeLiteral(solidFrame(2, 2, opaqueRed)),
                encodeLiteral(solidFrame(2, 2, opaqueRed)),
                encodeLiteral(solidFrame(2, 2, opaqueRed)),
                encodeLiteral(solidFrame(2, 2, opaqueRed)),
            )
        )
        val received = mutableListOf<LottieFrame>()
        val stream = LottieFrameStream(file, newScope()) { it?.let(received::add) }
        assertTrue(runBlocking { stream.open() })

        stream.decodeAndPublish(0)
        assertTrue(received.isEmpty())

        stream.decodeAndPublish(1)
        assertEquals(1, received.size)
        assertEquals(1, received[0].index)
    }

    @Test
    fun `more than RETAIN_FRAMES live bitmaps evicts the oldest first`() {
        val file = writeCacheFile(2, 2, List(7) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val received = mutableListOf<LottieFrame>()
        val stream = LottieFrameStream(file, newScope()) { it?.let(received::add) }
        assertTrue(runBlocking { stream.open() })

        (0..6).forEach { stream.decodeAndPublish(it) }

        assertEquals(7, received.size)
        assertTrue(received[0].skiaBitmap.isClosed)
        assertTrue(received[1].skiaBitmap.isClosed)
        assertTrue(received[2].skiaBitmap.isClosed)
        assertTrue(received[3].skiaBitmap.isClosed)
        assertFalse(received[4].skiaBitmap.isClosed)
        assertFalse(received[5].skiaBitmap.isClosed)
        assertFalse(received[6].skiaBitmap.isClosed)
    }

    @Test
    fun `decodeAndPublish before open is a no-op`() {
        val file = writeCacheFile(2, 2, List(2) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val received = mutableListOf<LottieFrame>()
        val stream = LottieFrameStream(file, newScope()) { it?.let(received::add) }

        stream.decodeAndPublish(0)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `close before open does not throw`() {
        val file = writeCacheFile(2, 2, List(2) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val stream = LottieFrameStream(file, newScope()) {}

        stream.close()
    }

    @Test
    fun `close is idempotent`() {
        val file = writeCacheFile(2, 2, List(2) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val stream = LottieFrameStream(file, newScope()) {}
        assertTrue(runBlocking { stream.open() })

        stream.close()
        stream.close()
    }

    @Test
    fun `requestFrame after close does not throw`() {
        val file = writeCacheFile(2, 2, List(2) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val stream = LottieFrameStream(file, newScope()) {}
        assertTrue(runBlocking { stream.open() })

        stream.close()
        stream.requestFrame(0)
    }

    @Test
    fun `close releases every published bitmap after the linger`() {
        val file = writeCacheFile(2, 2, List(2) { encodeLiteral(solidFrame(2, 2, opaqueRed)) })
        val received = mutableListOf<LottieFrame>()
        val stream = LottieFrameStream(file, newScope()) { it?.let(received::add) }
        assertTrue(runBlocking { stream.open() })
        stream.decodeAndPublish(0)
        val frame = received.single()
        assertFalse(frame.skiaBitmap.isClosed)

        stream.close()

        assertTrue(pollUntil(timeoutMs = 1000) { frame.skiaBitmap.isClosed })
    }
}
