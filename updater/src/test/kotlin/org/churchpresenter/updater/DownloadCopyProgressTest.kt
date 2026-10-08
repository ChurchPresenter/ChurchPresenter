package org.churchpresenter.updater

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The copy loop behind the in-app update download.
 *
 * It used to sit inline inside the `DialogWindow` that shows the update, where nothing could run it.
 * Two things it has to get right, and both fail late and confusingly:
 *
 *  * **Every byte arrives.** A short copy produces an installer that fails to run — minutes after
 *    the operator started an update, with the app already asking to restart.
 *  * **Progress is reported as it goes**, not once at the end. The bar is the only sign the download
 *    is alive over what may be a 100 MB file on church wifi.
 *
 * `downloadProgressFraction` already had its own coverage; this is the loop around it.
 */
class DownloadCopyProgressTest {

    private fun copy(
        bytes: ByteArray,
        contentLength: Long = bytes.size.toLong(),
        input: InputStream = ByteArrayInputStream(bytes),
    ): Triple<Long, ByteArray, List<Float>> {
        val out = ByteArrayOutputStream()
        val seen = mutableListOf<Float>()
        val copied = runBlocking { copyReportingProgress(input, out, contentLength) { seen += it } }
        return Triple(copied, out.toByteArray(), seen)
    }

    /** Larger than the 8 KB buffer, so the loop runs several times rather than once. */
    private fun payload(size: Int) = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun `every byte reaches the other side`() {
        val bytes = payload(20_000)

        val (copied, written, _) = copy(bytes)

        assertEquals(bytes.size.toLong(), copied)
        assertContentEquals(bytes, written, "a short copy is an installer that will not run")
    }

    @Test
    fun `progress is reported during the copy, not once at the end`() {
        val (_, _, seen) = copy(payload(20_000))

        // Deliberately not asserting the exact number of reports: that follows the buffer size,
        // which anyone may tune without changing behaviour. What has to hold is that the bar moves
        // *while* a large file downloads and ends at full.
        assertTrue(seen.size > 1, "the bar has to move during the download, not just at the end")
        assertTrue(seen.first() < 1f, "the first report must come before the copy finishes: $seen")
        assertEquals(1f, seen.last(), "and it must finish at full")
    }

    @Test
    fun `progress only ever climbs`() {
        val (_, _, seen) = copy(payload(40_000))

        assertEquals(seen.sorted(), seen, "a bar that jumps backwards reads as a stall: $seen")
        assertTrue(seen.all { it in 0f..1f }, "and stays in range: $seen")
    }

    @Test
    fun `an empty body copies nothing and reports nothing`() {
        // A zero-length response is a server or proxy problem, not a crash: the caller sees zero
        // bytes and can say the download failed rather than launching an empty installer.
        val (copied, written, seen) = copy(ByteArray(0))

        assertEquals(0L, copied)
        assertEquals(0, written.size)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `an unknown content length still copies, reporting the indeterminate fraction`() {
        // Servers that stream without a Content-Length give -1, which is the signal for an
        // indeterminate bar. The copy itself must not depend on knowing the size.
        val bytes = payload(12_000)

        val (copied, written, seen) = copy(bytes, contentLength = -1)

        assertEquals(bytes.size.toLong(), copied)
        assertContentEquals(bytes, written)
        assertTrue(seen.all { it == -1f }, "unknown length means an indeterminate bar throughout: $seen")
    }

    @Test
    fun `a stream that dies mid-download propagates rather than truncating silently`() {
        // The caller turns this into a visible error state. Swallowing it would leave a half-written
        // temp file being handed to the installer.
        val failing = object : InputStream() {
            private var served = 0
            override fun read() = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served > 0) throw IOException("connection reset")
                served++
                return len.coerceAtMost(4_096).also { b.fill(1, off, off + it) }
            }
        }

        assertFailsWith<IOException> { copy(ByteArray(0), contentLength = 9_000, input = failing) }
    }
}
