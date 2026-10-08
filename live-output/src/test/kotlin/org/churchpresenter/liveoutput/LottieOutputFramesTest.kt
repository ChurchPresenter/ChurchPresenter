package org.churchpresenter.liveoutput

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lowerthird.render.LottieRenderCache
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which outputs get lower-third frames of their own size, which share, when a size's stream goes,
 * and what an output draws before its frames are ready.
 *
 * The render cache is stood in for by [prepare]: each variant asked for gets a deferred the test
 * completes with a tiny cache file of its own, so readiness is the test's to decide.
 */
class LottieOutputFramesTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val dir = Files.createTempDirectory("lottie-output-frames").toFile()
    private val asked = mutableListOf<LottieRenderCache.Variant>()
    private val pending = HashMap<LottieRenderCache.Variant, CompletableDeferred<File>>()

    private val desktop =
        LottieRenderCache.Variant(clip = true, width = 1920, height = 1080, fps = 30.0, frameCount = 2)
    private val json = """{"w":1920,"h":1080}"""

    private val frames = LottieOutputFrames(scope, currentFrameIndex = { 1 }) { _, variant ->
        synchronized(asked) { asked += variant }
        synchronized(pending) { pending.getOrPut(variant) { CompletableDeferred() } }
    }

    @AfterTest
    fun tearDown() {
        frames.setContent(null, null)
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun sizes() = frames.variants.map { IntSize(it.width, it.height) }.toSet()

    /** A two-frame, 2x2 opaque cache file: what the stream reads is the file, whatever the variant says. */
    private fun cacheFile(): File {
        val file = File.createTempFile("variant", ".lrcc", dir)
        RandomAccessFile(file, "rw").use { raf ->
            raf.writeBytes("LRCC")
            raf.writeByte(LottieRenderCache.VERSION)
            raf.writeByte(0)
            raf.writeInt(2)
            raf.writeInt(2)
            raf.writeInt(3000)
            raf.writeInt(2)
            val offsets = LongArray(2) { i ->
                raf.filePointer.also {
                    raf.writeInt(8)
                    raf.writeInt(4) // a run of all four pixels
                    raf.writeInt(0xFF00FF00.toInt() + i)
                }
            }
            val footer = raf.filePointer
            offsets.forEach(raf::writeLong)
            raf.writeLong(footer)
        }
        return file
    }

    private fun ready(width: Int, height: Int) {
        val variant = frames.variants.single { it.width == width && it.height == height }
        synchronized(pending) { pending.getValue(variant) }.complete(cacheFile())
    }

    private fun awaitFrame(width: Int, height: Int): LottieFrame {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (true) {
            frames.frameFor(width, height)?.let { return it }
            check(System.currentTimeMillis() < deadline) { "no frame was published for ${width}x$height" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `an output larger than the desktop frames gets a variant of its own size`() {
        frames.setContent(json, desktop)
        frames.hold().resize(3840, 2160)

        assertEquals(setOf(IntSize(3840, 2160)), sizes())
        assertEquals(listOf(desktop.copy(width = 3840, height = 2160)), asked, "same rate and length, larger")
    }

    @Test
    fun `on a machine short of memory a large output draws the desktop frames scaled`() {
        val small = LottieOutputFrames(scope, currentFrameIndex = { 1 }, enabled = false) { _, variant ->
            synchronized(asked) { asked += variant }
            CompletableDeferred()
        }
        small.setContent(json, desktop)
        small.hold().resize(3840, 2160)

        assertTrue(small.variants.isEmpty(), "no 4K stream")
        assertTrue(asked.isEmpty(), "nothing is pre-rendered for it")
        assertNull(small.frameFor(3840, 2160), "so the output falls back to the desktop frames")
        small.setContent(null, null)
    }

    @Test
    fun `an 8 GB machine counts as enough memory, a 4 GB one does not, and an unknown one does`() {
        val gib = 1024L * 1024 * 1024
        assertTrue(hasMemoryForOutputFrames(8 * gib))
        // What Windows reports for 8 GB of RAM once the firmware and the GPU have kept theirs.
        assertTrue(hasMemoryForOutputFrames(7 * gib + 900L * 1024 * 1024))
        assertFalse(hasMemoryForOutputFrames(4 * gib))
        assertFalse(hasMemoryForOutputFrames(MIN_MEMORY_FOR_OUTPUT_FRAMES - 1))
        assertTrue(hasMemoryForOutputFrames(null), "a JVM that cannot say keeps the larger frames")
        assertTrue(physicalMemoryBytes()?.let { it > 0 } ?: true, "read from the platform, positive when known")
    }

    @Test
    fun `outputs the desktop frames already cover take no variant of their own`() {
        frames.setContent(json, desktop)
        frames.hold().resize(1920, 1080)
        frames.hold().resize(1280, 720)
        frames.hold().resize(320, 180)

        assertTrue(frames.variants.isEmpty())
        assertTrue(asked.isEmpty(), "nothing is rendered for them")
        assertNull(frames.frameFor(1280, 720))
    }

    @Test
    fun `outputs of one size share one stream, and so do sizes that fit to the same frames`() {
        frames.setContent(json, desktop)
        frames.hold().resize(3840, 2160)
        frames.hold().resize(3840, 2160)
        // A taller output letterboxes the same 3840x2160 frames.
        frames.hold().resize(3840, 2400)

        assertEquals(setOf(IntSize(3840, 2160)), sizes())
        assertEquals(1, asked.size)
        ready(3840, 2160)
        assertTrue(awaitFrame(3840, 2400) === awaitFrame(3840, 2160), "both draw the one published frame")
    }

    @Test
    fun `a size's stream closes once the last output of that size lets go`() {
        frames.setContent(json, desktop)
        val first = frames.hold().apply { resize(3840, 2160) }
        val second = frames.hold().apply { resize(3840, 2160) }

        first.close()
        assertEquals(setOf(IntSize(3840, 2160)), sizes(), "one output of that size is still live")

        second.close()
        assertTrue(frames.variants.isEmpty())
        assertTrue(frames.heldSizes.isEmpty())
    }

    @Test
    fun `an output resized to a size with the same frames keeps its stream`() {
        frames.setContent(json, desktop)
        val hold = frames.hold().apply { resize(3840, 2160) }

        hold.resize(3840, 2200)

        assertEquals(setOf(IntSize(3840, 2160)), sizes())
        assertEquals(1, asked.size, "the stream was not closed and rendered again")
        assertEquals(setOf(IntSize(3840, 2200)), frames.heldSizes)
    }

    @Test
    fun `at most two sizes get frames of their own, the largest first`() {
        frames.setContent(json, desktop)
        frames.hold().resize(2560, 1440)
        frames.hold().resize(3840, 2160)
        frames.hold().resize(3200, 1800)

        assertEquals(setOf(IntSize(3840, 2160), IntSize(3200, 1800)), sizes())
        assertNull(frames.frameFor(2560, 1440), "the third size draws the desktop frames")
    }

    @Test
    fun `until its frames are ready an output draws the desktop frames`() {
        frames.setContent(json, desktop)
        frames.hold().resize(3840, 2160)

        assertNull(frames.frameFor(3840, 2160), "rendering: nothing of its own yet")

        ready(3840, 2160)
        val frame = awaitFrame(3840, 2160)
        assertEquals(1, frame.index, "the stream starts at the playhead, not at frame zero")
    }

    @Test
    fun `an output held before the content is known gets its variant once it is`() {
        frames.hold().resize(3840, 2160)
        assertTrue(frames.variants.isEmpty())

        frames.setContent(json, desktop)

        assertEquals(setOf(IntSize(3840, 2160)), sizes())
    }

    @Test
    fun `new content drops every output's frames until its own are ready`() {
        frames.setContent(json, desktop)
        frames.hold().resize(3840, 2160)
        ready(3840, 2160)
        awaitFrame(3840, 2160)

        frames.setContent(null, null)

        assertNull(frames.frameFor(3840, 2160))
        assertTrue(frames.variants.isEmpty())
    }

    @Test
    fun `requested frames reach every output-sized stream`() {
        frames.setContent(json, desktop)
        frames.hold().resize(3840, 2160)
        ready(3840, 2160)
        awaitFrame(3840, 2160)

        frames.requestFrame(0)

        val deadline = System.currentTimeMillis() + WAIT_MS
        while (frames.frameFor(3840, 2160)?.index != 0) {
            check(System.currentTimeMillis() < deadline) { "frame 0 was never published" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `a blank render is discarded and the output stays on the desktop frames`() {
        frames.setContent(json, desktop)
        frames.hold().resize(3840, 2160)
        val variant = frames.variants.single()
        val blank = cacheFile().also { file ->
            // Rewrite both pixel runs as transparent.
            RandomAccessFile(file, "rw").use { raf ->
                listOf(30L, 42L).forEach { raf.seek(it); raf.writeInt(0) }
            }
        }
        val job = synchronized(pending) { pending.getValue(variant) }
        job.complete(blank)

        val deadline = System.currentTimeMillis() + WAIT_MS
        while (blank.exists()) {
            check(System.currentTimeMillis() < deadline) { "the blank render was kept" }
            Thread.onSpinWait()
        }
        assertNull(frames.frameFor(3840, 2160))
    }

    @Test
    fun `an output not yet laid out holds no size`() {
        frames.setContent(json, desktop)
        frames.hold().resize(0, 0)

        assertTrue(frames.heldSizes.isEmpty())
        assertTrue(frames.variants.isEmpty())
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
