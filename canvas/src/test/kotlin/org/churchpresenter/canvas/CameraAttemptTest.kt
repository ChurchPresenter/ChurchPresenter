package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraAttemptTest {

    /** A process that has already written everything it ever will. */
    private class FakeProcess(
        stdout: ByteArray = ByteArray(0),
        stderr: String = "",
        private val exitsWithinWindow: Boolean = false,
        private val exitCode: Int = 0,
    ) : Process() {
        private val out = ByteArrayInputStream(stdout)
        private val err = ByteArrayInputStream(stderr.toByteArray())
        var destroyed = false

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = out
        override fun getErrorStream(): InputStream = err
        override fun waitFor(): Int = exitCode
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exitsWithinWindow || destroyed
        override fun exitValue(): Int = exitCode
        override fun destroy() {
            destroyed = true
        }
        override fun isAlive(): Boolean = !destroyed && !exitsWithinWindow
    }

    private fun attempt(process: Process, entry: CacheEntry = CacheEntry()): FfmpegAttempt? = runBlocking {
        SharedCameraFrameCache.attemptCapture(listOf("ffmpeg"), entry) { process }
    }

    /** Two 16x16 BGRA frames: all blue, then all red. */
    private val twoFrames = ByteArray(2 * FRAME_BYTES) { i ->
        val inSecond = i >= FRAME_BYTES
        when (i % 4) {
            0 -> if (inSecond) 0 else -1
            2 -> if (inSecond) -1 else 0
            3 -> -1
            else -> 0
        }
    }

    private val announce = "Stream #0:0: Video: rawvideo (BGRA / 0x41524742), bgra, 16x16, 30 fps\n"

    @Test
    fun `a process that cannot be started is no attempt at all`() {
        val result = runBlocking {
            SharedCameraFrameCache.attemptCapture(listOf("ffmpeg"), CacheEntry()) { throw IOException("no such file") }
        }

        assertNull(result)
    }

    @Test
    fun `a process that exits at once carries its exit code and the reason it gave`() {
        val result = attempt(FakeProcess(stderr = "Device or resource busy\n", exitsWithinWindow = true, exitCode = 1))

        val failed = assertNotNull(result)
        assertTrue(failed.exitedImmediately)
        assertFalse(failed.framesProduced)
        assertEquals(1, failed.exitCode)
    }

    @Test
    fun `a stream's frames are read onto the screen until it ends`() {
        val entry = CacheEntry()

        val result = attempt(FakeProcess(stdout = twoFrames, stderr = announce, exitCode = 0), entry)

        val ran = assertNotNull(result)
        assertTrue(ran.framesProduced)
        val shown = assertNotNull(entry.frame.value)
        assertEquals(16, shown.width)
        assertNull(entry.ffmpegProcess, "the finished process is let go of")
    }

    @Test
    fun `a stream that ends before its first whole frame produced nothing`() {
        val result = attempt(FakeProcess(stdout = ByteArray(10), stderr = announce, exitCode = 1))

        val ran = assertNotNull(result)
        assertFalse(ran.framesProduced)
        assertTrue(ran.stderrTail.any { "bgra" in it }, "what ffmpeg said is kept for the report")
    }

    @Test
    fun `a successful exit inside the window is not mistaken for a failure`() {
        val result = attempt(FakeProcess(stdout = twoFrames, stderr = announce, exitsWithinWindow = true, exitCode = 0))

        val ran = assertNotNull(result)
        assertFalse(ran.exitedImmediately)
    }

    private companion object {
        const val FRAME_BYTES = 16 * 16 * 4
    }
}
