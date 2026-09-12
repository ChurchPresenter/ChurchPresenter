package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val W = 8
private const val H = 6
private const val WAIT_MS = 10_000L
private const val POLL_MS = 20L

/**
 * Where [ComposeScenePump]'s scene work runs — issue #498.
 *
 * `ComposeScene.render` advances the global snapshot and `advanceGlobalSnapshot` runs *every*
 * registered apply observer, this scene's and the on-screen AWT scene's alike, so two threads doing
 * it at once take those two observers' locks in opposite orders. That was a real deadlock, proved
 * with lock ownership on 2026-08-29 (CI run 33269248282): `AWT-EventQueue-0` inside a desktop
 * scrollbar's derived state against a worker inside `sendApplyNotifications`, each holding the lock
 * the other wanted. Confining the Compose half to one thread removes the second lock order, and
 * there is no way to opt a scene out of the global observer list.
 *
 * The deadlock itself cannot be tested — it needs two scenes and a lost race. What can be tested is
 * the property that removes it, which is what this pins: nothing that touches the scene runs
 * anywhere but the event queue. A change that moves any of it back onto a worker fails here rather
 * than as an unreproducible freeze in the middle of somebody's service.
 *
 * Deliberately *not* asserted: that `readInto` and `onFrame` stay off the event queue. They are the
 * expensive half and touch no Compose state, so putting them on it would cost the frame budget for
 * nothing — but they run on the pump's own coroutine rather than through [sceneDispatcher], so this
 * dispatcher never sees them and their absence here is the evidence.
 *
 * Mirrors [LowerThirdOffscreenRendererConfinementTest], which pins the same property for the other
 * off-screen renderer.
 */
class ComposeScenePumpConfinementTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    /**
     * Delegates to [Dispatchers.Main] and records the thread each dispatched block actually ran on.
     *
     * Read from *inside* the block, so it records where the work happened rather than merely that a
     * dispatcher was asked to do it.
     */
    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val onEventQueue: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())
        val threads: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context) {
                onEventQueue.add(SwingUtilities.isEventDispatchThread())
                threads.add(Thread.currentThread().name)
                block.run()
            }
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    @Test
    fun `building, rendering and closing the scene all run on the event queue`() {
        val recorder = RecordingDispatcher(Dispatchers.Main)
        val frames = AtomicInteger(0)
        val pump = ComposeScenePump(
            width = W,
            height = H,
            fps = 60,
            sceneDispatcher = recorder,
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Red))
        }

        pump.start(scope) { _, _, _, _ -> frames.incrementAndGet() }
        // Several frames, not one: construction and the first render could both be confined while a
        // later tick quietly was not.
        waitFor("a few rendered frames") { frames.get() >= 3 }
        pump.stop()
        // The close is dispatched from a `finally` on a cancelled coroutine, so it lands after stop
        // returns; without waiting the last recorded block would be the render, not the close.
        waitFor("the scene close to be dispatched") { recorder.onEventQueue.size >= frames.get() }

        assertTrue(recorder.onEventQueue.isNotEmpty(), "no scene work was dispatched at all")
        assertTrue(
            recorder.onEventQueue.all { it },
            "every scene operation must run on the event queue, but some ran elsewhere: " +
                recorder.threads.distinct(),
        )
    }

    @Test
    fun `the scene is still closed when the pump is stopped mid-render`() {
        // The close runs inside `withContext(NonCancellable + sceneDispatcher)`. Without
        // NonCancellable that `withContext` throws on the already-cancelled coroutine stopping the
        // pump is, and the scene leaks for the life of the process -- one per output, every restart.
        val recorder = RecordingDispatcher(Dispatchers.Main)
        val frames = AtomicInteger(0)
        val pump = ComposeScenePump(W, H, fps = 60, sceneDispatcher = recorder) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Red))
        }

        pump.start(scope) { _, _, _, _ -> frames.incrementAndGet() }
        waitFor("the pump to be rendering") { frames.get() >= 2 }
        val beforeStop = recorder.onEventQueue.size
        pump.stop()

        waitFor("the close to run despite cancellation") { recorder.onEventQueue.size > beforeStop }
        assertTrue(
            recorder.onEventQueue.all { it },
            "the close must also land on the event queue: " + recorder.threads.distinct(),
        )
    }
}
