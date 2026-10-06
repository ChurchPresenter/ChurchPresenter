package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.liveoutput.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
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
