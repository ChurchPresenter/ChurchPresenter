package org.churchpresenter.app.churchpresenter.composables

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

private const val WAIT_MS = 2_000L
private const val POLL_MS = 5L
private const val NANOS_PER_MS = 1_000_000L

/**
 * That one screen-capture configuration costs one grab loop, however many layers draw it.
 *
 * The point of the cache: a screen-capture layer is composed in the canvas editor, in every sidebar
 * live preview and on every presenter output, and each of those used to run its own
 * `Robot.createScreenCapture` at up to 30fps over the same pixels — work the window server does, so
 * it lands on the compositor rather than on the app.
 *
 * `Robot` itself is never constructed here. The cache takes its grab as a constructor parameter and
 * these tests supply one that counts calls and returns a constructed image, so the suite measures
 * the sharing rather than the platform — and runs headless, where a real `Robot` throws.
 */
class ScreenCaptureCacheTest {

    /** Ends on the condition itself; the deadline only fails the test. */
    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * NANOS_PER_MS
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    private fun source(
        id: String = "cap",
        mode: String = "region",
        x: Int = 0,
        y: Int = 0,
        width: Int = 320,
        height: Int = 240,
        interval: Int = 33,
    ) = SceneSource.ScreenCaptureSource(
        id = id,
        name = "Screen Capture",
        captureMode = mode,
        captureX = x,
        captureY = y,
        captureWidth = width,
        captureHeight = height,
        captureInterval = interval,
    )

    private fun image() = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun `two layers of one configuration share a single grab loop`() {
        val opened = AtomicInteger()
        val cache = ScreenCaptureCache { opened.incrementAndGet(); image() }

        val editor = cache.acquire(source(id = "editor"))
        val output = cache.acquire(source(id = "output"))

        assertEquals(1, cache.liveCaptureCount, "one configuration is one grab loop")
        assertSame(editor, output, "and both layers draw from the same flow")
    }

    @Test
    fun `different regions are different captures`() {
        val cache = ScreenCaptureCache { image() }

        cache.acquire(source(id = "a", x = 0))
        cache.acquire(source(id = "b", x = 400))

        assertEquals(2, cache.liveCaptureCount, "folding these together would show one the other's pixels")
    }

    @Test
    fun `the loop stops only when the last layer lets go`() {
        val cache = ScreenCaptureCache { image() }
        val first = source(id = "editor")
        val second = source(id = "output")

        cache.acquire(first)
        cache.acquire(second)
        cache.release(first)
        assertEquals(1, cache.liveCaptureCount, "one layer leaving must not blank the other")

        cache.release(second)
        assertEquals(0, cache.liveCaptureCount, "the last one out stops the grab")
    }

    @Test
    fun `releasing something never acquired changes nothing`() {
        val cache = ScreenCaptureCache { image() }
        cache.release(source())
        assertEquals(0, cache.liveCaptureCount)
    }

    @Test
    fun `a grab that returns a frame reaches the flow`() {
        val cache = ScreenCaptureCache { image() }
        val frames = cache.acquire(source())
        waitFor("a frame") { frames.value != null }
        assertNotNull(frames.value, "the layer must be given the picture the grab produced")
    }

    @Test
    fun `a configuration whose grab yields nothing still holds one entry`() {
        val cache = ScreenCaptureCache { null }
        cache.acquire(source())
        assertEquals(1, cache.liveCaptureCount, "a screen that cannot be grabbed is still one subscriber")
    }
}
