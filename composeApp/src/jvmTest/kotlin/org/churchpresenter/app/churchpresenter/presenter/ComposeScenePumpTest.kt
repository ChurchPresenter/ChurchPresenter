package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val W = 8
private const val H = 4
private const val POLL_MS = 2L
private const val WAIT_MS = 4_000L

/**
 * The frame pump both off-screen outputs run on.
 *
 * It had no coverage at all while it was inline in [BrowserSourceVideoRenderer] — reaching it meant
 * standing up the whole render loop — which is the other half of why it was worth extracting.
 *
 * Every wait here ends on a positive signal (a frame arriving, a count rising) rather than on a
 * fixed pause, so nothing in this class asserts on timing.
 */
class ComposeScenePumpTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    /**
     * Waits until [condition] holds, failing rather than continuing if it never does.
     *
     * Polls with a short [delay] rather than [yield]: yielding in a `runBlocking` loop is a
     * busy-wait that pins a core for the whole wait, and this suite runs on four parallel forks —
     * so a dozen of these spinning at once starves the *other* forks. That is a plausible cause of
     * a loopback websocket handshake in an unrelated suite missing its 15s deadline on CI.
     *
     * Still ends on the positive signal, never on the timeout: the deadline only fails the test.
     */
    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    private fun redPump(
        shouldRender: () -> Boolean = { true },
        onPark: () -> Unit = {},
    ) = ComposeScenePump(width = W, height = H, fps = 60, shouldRender = shouldRender, onPark = onPark) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Red))
    }

    @Test
    fun `the pump renders what it was given, at the size it was given`() {
        val pump = redPump()
        val seen = AtomicReference<Triple<Int, Int, Int>?>(null)
        pump.start(scope) { argb, w, h, _ -> seen.compareAndSet(null, Triple(argb[0], w, h)) }
        waitFor("the first frame") { seen.get() != null }
        pump.stop()

        val (pixel, w, h) = seen.get()!!
        assertEquals(W, w)
        assertEquals(H, h)
        assertEquals(0xFFFF0000.toInt(), pixel, "the composable painted red, so the frame is red")
    }

    @Test
    fun `the buffer handed to the callback is the whole frame`() {
        val pump = redPump()
        val size = AtomicInteger(0)
        pump.start(scope) { argb, _, _, _ -> size.compareAndSet(0, argb.size) }
        waitFor("a frame") { size.get() > 0 }
        pump.stop()

        assertEquals(W * H, size.get())
    }

    @Test
    fun `the elapsed clock advances with the frames rather than standing still`() {
        val pump = redPump()
        val first = AtomicReference<Long?>(null)
        val later = AtomicReference<Long?>(null)
        pump.start(scope) { _, _, _, elapsed ->
            if (!first.compareAndSet(null, elapsed) && elapsed > first.get()!!) later.compareAndSet(null, elapsed)
        }
        waitFor("a second, later frame") { later.get() != null }
        pump.stop()

        assertTrue(later.get()!! > first.get()!!, "an animation driven off this clock has to move")
    }

    @Test
    fun `a parked pump renders nothing`() {
        val frames = AtomicInteger(0)
        val parks = AtomicInteger(0)
        val pump = redPump(shouldRender = { false }, onPark = { parks.incrementAndGet() })
        pump.start(scope) { _, _, _, _ -> frames.incrementAndGet() }
        // Wait on the positive signal that the loop reached its parking decision, not on a pause.
        waitFor("the loop to park") { parks.get() > 0 }
        pump.stop()

        assertEquals(0, frames.get(), "the whole point of parking is that the render is not paid")
    }

    @Test
    fun `onPark fires once per park, not once per idle poll`() {
        val parks = AtomicInteger(0)
        val polls = AtomicInteger(0)
        val pump = ComposeScenePump(
            width = W,
            height = H,
            fps = 60,
            shouldRender = { polls.incrementAndGet(); false },
            onPark = { parks.incrementAndGet() },
        ) { Box(modifier = Modifier.fillMaxSize()) }
        pump.start(scope) { _, _, _, _ -> }
        waitFor("several idle polls") { polls.get() >= 3 }
        pump.stop()

        assertEquals(1, parks.get(), "a park is an edge, not a state — the callback resets a baseline")
    }

    @Test
    fun `a pump that wakes up renders again`() {
        var allowed = false
        val frames = AtomicInteger(0)
        val parks = AtomicInteger(0)
        val pump = redPump(shouldRender = { allowed }, onPark = { parks.incrementAndGet() })
        pump.start(scope) { _, _, _, _ -> frames.incrementAndGet() }
        waitFor("the loop to park") { parks.get() > 0 }
        allowed = true
        waitFor("a frame after waking") { frames.get() > 0 }
        pump.stop()
    }

    @Test
    fun `parking again after a render fires onPark a second time`() {
        var allowed = true
        val frames = AtomicInteger(0)
        val parks = AtomicInteger(0)
        val pump = redPump(shouldRender = { allowed }, onPark = { parks.incrementAndGet() })
        pump.start(scope) { _, _, _, _ -> frames.incrementAndGet() }
        waitFor("a frame") { frames.get() > 0 }
        allowed = false
        waitFor("the first park") { parks.get() == 1 }
        allowed = true
        waitFor("a frame after waking") { frames.get() > 1 }
        allowed = false
        waitFor("the second park") { parks.get() == 2 }
        pump.stop()
    }

    @Test
    fun `isRunning tracks start and stop`() {
        val pump = redPump()
        assertFalse(pump.isRunning)
        pump.start(scope) { _, _, _, _ -> }
        assertTrue(pump.isRunning)
        pump.stop()
        assertFalse(pump.isRunning)
    }

    @Test
    fun `starting twice does not start a second loop`() {
        val pump = redPump()
        val starts = AtomicInteger(0)
        pump.start(scope) { _, _, _, _ -> starts.incrementAndGet() }
        pump.start(scope) { _, _, _, _ -> starts.incrementAndGet() }
        waitFor("a frame") { starts.get() > 0 }
        pump.stop()
        // One stop has to be enough; a second loop would keep counting after it.
        val afterStop = starts.get()
        waitFor("the loop to be quiet") { starts.get() == afterStop }
    }

    @Test
    fun `stopping a pump that was never started is harmless`() {
        redPump().stop()
    }

    @Test
    fun `the tick delay comes from the fps and is clamped at both ends`() {
        assertEquals(1000L / 30, ComposeScenePump(W, H, 30) {}.tickDelayMs)
        assertEquals(1000L / 60, ComposeScenePump(W, H, 60) {}.tickDelayMs)
        // A rate of zero is not a rate; a rate above 60 is beyond what an off-screen render keeps up
        // with, and both used to be a division by zero or an unbounded loop.
        assertEquals(1000L / 1, ComposeScenePump(W, H, 0) {}.tickDelayMs)
        assertEquals(1000L / 1, ComposeScenePump(W, H, -5) {}.tickDelayMs)
        assertEquals(1000L / 60, ComposeScenePump(W, H, 240) {}.tickDelayMs)
    }

    @Test
    fun `many pumps starting at once all reach a frame`() {
        // ImageComposeScene's constructor touches Compose state that is global to the JVM, and
        // there is a pump per Browser Source output and per NDI output, every one of them started
        // from its own LaunchedEffect on the multi-threaded default dispatcher. Building two at
        // the same time raced a shared map inside the constructor and threw
        // ArrayIndexOutOfBoundsException out of its own resizeStorage, killing the app for an
        // operator with two outputs configured. Construction is serialised now; this is the shape
        // that used to break, driven wider than any real configuration.
        val pumps = List(8) { redPump() }
        val firstFrames = AtomicInteger(0)
        pumps.forEach { pump -> pump.start(scope) { _, _, _, _ -> firstFrames.incrementAndGet() } }

        waitFor("every pump's first frame") { firstFrames.get() >= pumps.size }
        pumps.forEach { it.stop() }
    }

    @Test
    fun `a pump whose content cannot compose does not take the app down, and can start again`() {
        val explode = AtomicBoolean(true)
        val pump = ComposeScenePump(width = W, height = H, fps = 60) {
            if (explode.get()) error("no scene for you")
            Box(modifier = Modifier.fillMaxSize().background(Color.Red))
        }

        pump.start(scope) { _, _, _, _ -> }
        // The failure clears the job rather than leaving the pump wedged for the rest of the
        // session, which is what "can start again" below actually proves.
        waitFor("the failed start to release the pump") { !pump.isRunning }

        explode.set(false)
        val frames = AtomicInteger(0)
        pump.start(scope) { _, _, _, _ -> frames.incrementAndGet() }
        waitFor("a frame from the restarted pump") { frames.get() > 0 }
        pump.stop()
    }

    @Test
    fun `the idle poll is slower than the fastest tick`() {
        // Otherwise parking would cost more wake-ups than rendering does.
        assertTrue(ComposeScenePump.IDLE_POLL_MS > ComposeScenePump(W, H, 60) {}.tickDelayMs)
    }
}
