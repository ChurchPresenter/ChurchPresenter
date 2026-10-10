package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.liveoutput.LiveLowerThird
import org.churchpresenter.liveoutput.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LottiePlaybackEffectTest {

    private fun liveManager() = PresenterManager().apply { setPresentingMode(Presenting.LOWER_THIRD) }

    private fun ComposeUiTest.play(
        manager: PresenterManager,
        durationFrames: Float? = 6f,
        frameRate: Float? = 60f,
        pauseAtFrame: Boolean = false,
        pauseFrame: Float = -1f,
        pauseDurationMs: Long = 0L,
        trigger: Int = 0,
    ) {
        setContent {
            LottiePlaybackEffect(
                presenterManager = manager,
                durationFrames = durationFrames,
                frameRate = frameRate,
                pauseAtFrame = pauseAtFrame,
                pauseFrame = pauseFrame,
                pauseDurationMs = pauseDurationMs,
                trigger = trigger,
            )
        }
    }

    private class Prerendered(
        manager: PresenterManager,
        frames: Int?,
        private val failOnProgress: Boolean = false,
    ) : LiveLowerThird by manager {
        override val lottieFrameCount = mutableStateOf(frames)
        override val lottiePrerenderFps = mutableStateOf(60)
        val frameIndices = mutableListOf<Int>()
        var progressCalls = 0

        override fun setLottieCurrentFrameIndex(index: Int) {
            frameIndices += index
        }

        override fun setLottieProgress(progress: Float) {
            check(!failOnProgress) { "progress refused" }
            progressCalls++
        }
    }

    private fun ComposeUiTest.playOn(
        manager: PresenterManager,
        lowerThird: LiveLowerThird,
        durationFrames: Float?,
        frameRate: Float?,
        shown: () -> Boolean = { true },
    ) {
        setContent {
            if (shown()) {
                LottiePlaybackEffect(
                    presenterManager = manager,
                    durationFrames = durationFrames,
                    frameRate = frameRate,
                    pauseAtFrame = false,
                    pauseFrame = -1f,
                    pauseDurationMs = 0L,
                    trigger = 0,
                    lowerThird = lowerThird,
                )
            }
        }
    }

    @Test
    fun `pre-rendered frames are stepped through by index and end on the last one`() = runComposeUiTest {
        val manager = liveManager()
        val frames = Prerendered(manager, frames = 4)

        playOn(manager, frames, durationFrames = null, frameRate = null)

        waitUntil("the clip finished") { manager.clearDisplayRequested.value }
        assertEquals(3, frames.frameIndices.last())
        assertEquals(0, frames.progressCalls)
    }

    @Test
    fun `taking the clip off before it ends stops it without finishing`() = runComposeUiTest {
        val manager = liveManager()
        val lowerThird = Prerendered(manager, frames = null)
        var shown by mutableStateOf(true)

        mainClock.autoAdvance = false
        playOn(manager, lowerThird, durationFrames = 60_000f, frameRate = 60f) { shown }
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertTrue(lowerThird.progressCalls >= 2, "the clip is playing")
        shown = false
        mainClock.advanceTimeByFrame()
        val callsWhenRemoved = lowerThird.progressCalls
        repeat(3) { mainClock.advanceTimeByFrame() }

        assertEquals(callsWhenRemoved, lowerThird.progressCalls, "playback stops with the effect")
        assertTrue(!manager.clearDisplayRequested.value, "a clip cut short must not end the overlay")
    }

    @Test
    fun `a failure inside playback is reported and thrown on`() {
        assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                val manager = liveManager()
                playOn(manager, Prerendered(manager, frames = null, failOnProgress = true), 6f, 60f)
                waitForIdle()
            }
        }
    }

    @Test
    fun `a clip runs to the end and asks for the display to be cleared`() = runComposeUiTest {
        val manager = liveManager()

        play(manager)

        waitUntil("the clip finished") { manager.clearDisplayRequested.value }
        assertEquals(1f, manager.lottieProgress.value, "the last frame must be left on screen, not a partial one")
    }

    @Test
    fun `a clip that holds on a frame still finishes`() = runComposeUiTest {
        val manager = liveManager()

        play(manager, pauseAtFrame = true, pauseFrame = 0.5f, pauseDurationMs = 40L)

        waitUntil("the clip finished") { manager.clearDisplayRequested.value }
        assertEquals(1f, manager.lottieProgress.value)
    }

    @Test
    fun `a hold with no frame configured is not a hold`() = runComposeUiTest {
        val manager = liveManager()

        play(manager, pauseAtFrame = true, pauseFrame = -1f, pauseDurationMs = 5_000L)

        waitUntil("the clip finished") { manager.clearDisplayRequested.value }
        assertEquals(1f, manager.lottieProgress.value)
    }

    @Test
    fun `nothing to play leaves the output alone`() = runComposeUiTest {
        val manager = liveManager()

        play(manager, durationFrames = null, frameRate = null)

        waitForIdle()
        assertTrue(!manager.clearDisplayRequested.value, "there is no clip, so nothing ends")
        assertEquals(0f, manager.lottieProgress.value)
    }

    @Test
    fun `a composition with no frame rate is not played`() = runComposeUiTest {
        val manager = liveManager()

        play(manager, durationFrames = 6f, frameRate = null)

        waitForIdle()
        assertTrue(!manager.clearDisplayRequested.value)
    }

    @Test
    fun `retriggering the same clip plays it again`() = runComposeUiTest {
        val manager = liveManager()
        val trigger = mutableStateOf(0)
        setContent {
            LottiePlaybackEffect(
                presenterManager = manager,
                durationFrames = 6f,
                frameRate = 60f,
                pauseAtFrame = false,
                pauseFrame = -1f,
                pauseDurationMs = 0L,
                trigger = trigger.value,
            )
        }
        waitUntil("the first pass finished") { manager.clearDisplayRequested.value }

        manager.setPresentingMode(Presenting.NONE)
        manager.setPresentingMode(Presenting.LOWER_THIRD)
        trigger.value = 1

        waitUntil("the second pass finished") { manager.clearDisplayRequested.value }
        assertEquals(1f, manager.lottieProgress.value)
    }
}
