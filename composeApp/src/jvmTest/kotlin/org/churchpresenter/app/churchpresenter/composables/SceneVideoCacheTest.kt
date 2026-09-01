package org.churchpresenter.app.churchpresenter.composables

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * That one video file costs one decode, however many layers draw it.
 *
 * The most expensive of the three shared caches. A video layer is composed in the canvas editor, in
 * every sidebar live preview and on every presenter output, and each instance used to build its own
 * VLC factory, its own player and its own conversion loop — the same file decoded at full source
 * resolution that many times, its audio played that many times over itself, and every frame
 * converted to an `ImageBitmap` per instance at 60fps.
 *
 * libvlc is never loaded here: the cache takes its player as a constructor parameter and these
 * tests supply a stand-in that hands back frames on demand. So this measures the sharing, the
 * release and the volume path, not the decoder.
 */
class SceneVideoCacheTest {

    /** Ends on the condition itself; the deadline only fails the test. */
    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + VIDEO_WAIT_MS * VIDEO_NANOS_PER_MS
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(VIDEO_POLL_MS)
        }
    }

    /** A player that produces one frame and records what was asked of it. */
    private class FakePlayer : SceneVideoHandle {
        val volumes = mutableListOf<Int>()
        var closed = false
        override var frameVersion = 1L
        override val frame: BufferedImage? = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        override fun setVolume(percent: Int) { volumes += percent }
        override fun close() { closed = true }
    }

    private fun spec(path: String = "/videos/loop.mp4", loop: Boolean = true) = SceneVideoSpec(path, loop)

    @Test
    fun `two layers of one file share a single decode`() {
        val opened = AtomicInteger()
        val cache = SceneVideoCache { opened.incrementAndGet(); FakePlayer() }

        val editor = cache.acquire(spec(), volume = 1f)
        val output = cache.acquire(spec(), volume = 1f)

        assertEquals(1, cache.liveDecodeCount, "one file is one decode")
        assertSame(editor, output, "and both layers draw from the same flow")
        waitFor("the decode to open") { opened.get() == 1 }
        assertEquals(1, opened.get(), "the player must be built once, not once per layer")
    }

    @Test
    fun `the same file looped and unlooped are separate decodes`() {
        val cache = SceneVideoCache { FakePlayer() }

        cache.acquire(spec(loop = true), volume = 1f)
        cache.acquire(spec(loop = false), volume = 1f)

        assertEquals(2, cache.liveDecodeCount, "looping changes what is played, so it cannot be shared")
    }

    @Test
    fun `volume is not part of the key, so two volumes are still one decode`() {
        val opened = AtomicInteger()
        val cache = SceneVideoCache { opened.incrementAndGet(); FakePlayer() }

        cache.acquire(spec(), volume = 1f)
        cache.acquire(spec(), volume = 0.2f)

        assertEquals(1, cache.liveDecodeCount, "the picture is identical; only the audio differs")
    }

    @Test
    fun `the volume asked for while the player was opening is applied when it arrives`() {
        val player = FakePlayer()
        val cache = SceneVideoCache { player }

        // Opening is asynchronous, so this names a volume before there is anything to set it on.
        cache.acquire(spec(), volume = 0.4f)

        waitFor("the opening volume to be applied") { player.volumes.contains(40) }
        assertTrue(
            player.volumes.contains(40),
            "the source's own volume must reach the decoder, not be dropped for its default",
        )
    }

    @Test
    fun `a volume change reaches the running player without restarting it`() {
        val opened = AtomicInteger()
        val player = FakePlayer()
        val cache = SceneVideoCache { opened.incrementAndGet(); player }

        cache.acquire(spec(), volume = 1f)
        waitFor("the decode to open") { player.volumes.isNotEmpty() }
        cache.setVolume(spec(), 0.5f)

        waitFor("the volume change to be applied") { player.volumes.contains(50) }
        assertEquals(1, opened.get(), "a slider drag must not rebuild the decoder")
    }

    @Test
    fun `the decode stops only when the last layer lets go`() {
        val player = FakePlayer()
        val cache = SceneVideoCache { player }

        cache.acquire(spec(), volume = 1f)
        cache.acquire(spec(), volume = 1f)
        // Wait for the player to exist before letting go of it: releasing before the decode has
        // opened is a real case, but it is the *no player was ever built* one, and it is not what
        // this test is about.
        waitFor("the decode to open") { player.volumes.isNotEmpty() }

        cache.release(spec())
        assertEquals(1, cache.liveDecodeCount, "one layer leaving must not blank the other")

        cache.release(spec())
        assertEquals(0, cache.liveDecodeCount, "the last one out stops the decode")
        waitFor("the player to be closed") { player.closed }
        assertTrue(player.closed, "the native decoder must be released, not left running")
    }

    @Test
    fun `a decoded frame reaches the flow`() {
        val cache = SceneVideoCache { FakePlayer() }
        val frames = cache.acquire(spec(), volume = 1f)
        waitFor("a frame") { frames.value != null }
        assertNotNull(frames.value, "the layer must be given the decoded picture")
    }

    @Test
    fun `a file whose player cannot be built holds an entry but no frames`() {
        val cache = SceneVideoCache { null }
        val frames = cache.acquire(spec(), volume = 1f)
        assertEquals(1, cache.liveDecodeCount, "a video that will not open is still one subscriber")
        assertEquals(null, frames.value)
    }
}

private const val VIDEO_WAIT_MS = 2_000L
private const val VIDEO_POLL_MS = 5L
private const val VIDEO_NANOS_PER_MS = 1_000_000L
