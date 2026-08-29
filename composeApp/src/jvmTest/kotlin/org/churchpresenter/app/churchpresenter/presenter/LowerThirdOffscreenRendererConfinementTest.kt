package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Collections
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The off-screen renderer builds, renders and closes its scene on one thread, and that thread is
 * the event queue.
 *
 * Not a style preference. `ComposeScene.render` advances the global snapshot, and
 * `advanceGlobalSnapshot` runs *every* registered apply observer — this scene's and the on-screen
 * AWT scene's alike. Two threads doing that concurrently take those observers' locks in opposite
 * orders, and on 2026-08-29 that deadlocked CI (run 33269248282): a fork was halted after 159s with
 * an explicit two-thread cycle, `AWT-EventQueue-0` inside a desktop scrollbar's derived state
 * against this renderer's worker inside `sendApplyNotifications`, each holding the lock the other
 * wanted. The same cycle can freeze the running app, since `LottieRenderCache` pre-renders for the
 * ATEM media pool on `Dispatchers.Default` while the UI is live.
 *
 * The deadlock itself cannot be tested — it needs two scenes and a lost race. What can be tested is
 * the property that removes it, which is what this pins: nothing that touches the scene runs
 * anywhere but the event queue. A future change that moves any of it back onto a worker fails here
 * rather than three weeks later in someone else's suite.
 */
class LowerThirdOffscreenRendererConfinementTest {

    private val lottieJson =
        """{"v":"5.5.2","fr":30,"ip":0,"op":30,"w":10,"h":10,"nm":"test","ddd":0,"assets":[],"layers":[]}"""

    /**
     * Delegates to [Dispatchers.Main] and records the thread each dispatched block actually ran on.
     *
     * The thread is read from *inside* the block, so this records where the work happened rather
     * than that a dispatcher was asked to do it.
     */
    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val threads: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val allOnEventQueue: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context) {
                threads.add(Thread.currentThread().name)
                allOnEventQueue.add(SwingUtilities.isEventDispatchThread())
                block.run()
            }
        }
    }

    @Test
    fun `every scene operation runs on the event queue`() = runBlocking {
        val recorder = RecordingDispatcher(Dispatchers.Main)

        val pixels = LowerThirdOffscreenRenderer(10, 10, sceneDispatcher = recorder)
            .renderStill(lottieJson, progress = 0.5f)

        // The render really happened — a confinement assertion over a no-op would pass just as well.
        assertEquals(100, pixels.size)

        assertTrue(recorder.threads.isNotEmpty(), "the scene work went through the scene dispatcher")
        assertTrue(
            recorder.allOnEventQueue.all { it },
            "every scene operation must run on the event queue, but some ran on " +
                recorder.threads.distinct().joinToString(),
        )
    }

    @Test
    fun `the caller keeps its own thread while the scene is confined`() = runBlocking {
        // The point of confining only the scene half: the pixel work must stay off the event queue,
        // or a background pre-render would stall the UI for the length of a clip.
        val recorder = RecordingDispatcher(Dispatchers.Main)
        var blockThreadWasEventQueue = true

        LowerThirdOffscreenRenderer(10, 10, sceneDispatcher = recorder)
            .withSession(lottieJson, initialProgress = 0f) { renderFrame ->
                renderFrame(0f)
                blockThreadWasEventQueue = SwingUtilities.isEventDispatchThread()
            }

        assertTrue(recorder.allOnEventQueue.all { it }, "the scene stayed on the event queue")
        assertEquals(false, blockThreadWasEventQueue, "but the frame consumer did not")
    }
}
