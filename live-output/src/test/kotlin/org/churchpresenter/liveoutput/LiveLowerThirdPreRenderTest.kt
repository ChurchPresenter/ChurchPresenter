package org.churchpresenter.liveoutput

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.churchpresenter.lowerthird.render.LottieRenderCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val WAIT_MS = 10_000L
private const val POLL_MS = 10L

/**
 * A lower third with timing, pre-rendered for real: once the cache has its frames, playback draws
 * them instead of the live renderer, and an output larger than them gets frames of its own size.
 *
 * The fixture is a 64x36 canvas one second long with a solid bar across it, so the render costs a
 * few milliseconds and is not blank — a blank render is discarded and never adopted. Its cache
 * entries land in the fork's own test home, as every render in this suite's do.
 */
class LiveLowerThirdPreRenderTest {

    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    /** [LottieOutputFrames] is driven from the UI thread, as the outputs drive it. */
    private fun <T> onMain(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }

    @Test
    fun `a lower third that renders plays from its frames, and a larger output gets frames its size`() {
        val pm = PresenterManager()
        val frames = pm.lowerThird.outputFrames
        // Twice the canvas each way: past the desktop frames, so this output gets a variant.
        val hold = onMain { frames.hold().also { it.resize(128, 72) } }
        try {
            pm.setLottieContent(BAR, pauseAtFrame = false, pauseFrame = 0f, pauseDurationMs = 0, presetName = "Bar")

            waitFor("the desktop frames") { pm.lottieFrame.value != null }
            assertEquals(30, pm.lottieFrameCount.value)
            assertEquals(LottieRenderCache.PLAYBACK_FPS, pm.lottiePrerenderFps.value)
            // An output larger than the desktop frames, on a machine with the memory for it.
            if (hasMemoryForOutputFrames(physicalMemoryBytes())) {
                waitFor("the 128x72 frames") { onMain { frames.frameFor(128, 72) } != null }
            }
            pm.setLottieCurrentFrameIndex(1)
            assertEquals(1, pm.lottieCurrentFrameIndex.value)

            pm.setLottieContent("", pauseAtFrame = false, pauseFrame = 0f, pauseDurationMs = 0, presetName = "")
            assertNull(pm.lottieFrameCount.value, "new content drops the frames of the last")
            assertNull(pm.lottieFrame.value)
            assertNull(onMain { frames.frameFor(128, 72) })
        } finally {
            onMain { hold.close() }
        }
    }

    private companion object {
        /** A 64x36 canvas, one second at 30 fps, filled by one solid layer. */
        const val BAR =
            """{"v":"5.7.4","fr":30,"ip":0,"op":30,"w":64,"h":36,"assets":[],"layers":[""" +
                """{"ddd":0,"ind":1,"ty":1,"nm":"bar","sr":1,"ks":{"o":{"a":0,"k":100},""" +
                """"p":{"a":0,"k":[32,18,0]},"a":{"a":0,"k":[32,18,0]},"s":{"a":0,"k":[100,100,100]}},""" +
                """"sc":"#0088ff","sw":64,"sh":36,"ip":0,"op":30,"st":0}]}"""
    }
}
