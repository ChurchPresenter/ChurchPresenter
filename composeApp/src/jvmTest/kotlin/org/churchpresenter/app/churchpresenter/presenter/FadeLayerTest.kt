package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [rememberFadeLayers], the crossfade the song and Bible outputs share, driven by the test clock.
 *
 * Pages are strings; two pages are "the same page" when they share a first letter, so `A1` and
 * `A2` stand for one slide restated -- a line stepped, or the tuning changed -- and `B` for another.
 */
@OptIn(ExperimentalTestApi::class)
class FadeLayerTest {

    private var target by mutableStateOf("A")
    private var layers: List<FadeLayer<String>> = emptyList()

    private fun ComposeUiTest.host(crossfade: Boolean = true) {
        setContent {
            layers = rememberFadeLayers(
                target = target,
                crossfade = crossfade,
                durationMs = FADE_MS,
                samePage = { shown, next -> shown.first() == next.first() },
            )
        }
        waitForIdle()
    }

    /** Steps frames until [condition] holds, failing after a fade's worth and then some. */
    private fun ComposeUiTest.stepUntil(what: String, condition: () -> Boolean) {
        repeat(FADE_MS / FRAME_MS * 2) {
            if (condition()) return
            mainClock.advanceTimeByFrame()
        }
        assertTrue(condition(), "never reached: $what")
    }

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(FADE_MS * 3L)
        waitForIdle()
    }

    @Test
    fun `the outgoing page keeps its own layer at full alpha when the next one arrives`() = runComposeUiTest {
        mainClock.autoAdvance = false
        host()
        val outgoing = layers.single()

        target = "B"
        stepUntil("the incoming layer") { layers.size == 2 }

        // The crossfade used to move the outgoing page into a new "previous" slot and drop the old
        // slot's alpha to 0 in the same write, which drew one blank frame. It must keep its layer.
        assertSame(outgoing, layers.first(), "the outgoing page stays in the layer it was drawn in")
        assertEquals("A", outgoing.page)
        assertTrue(outgoing.alpha > 0.9f, "the outgoing page starts the fade fully drawn, was ${outgoing.alpha}")
        assertEquals("B", layers.last().page)
        assertTrue(layers.last().alpha < 0.1f, "the incoming page starts the fade undrawn")
    }

    @Test
    fun `a crossfade ends on the incoming page alone, fully drawn`() = runComposeUiTest {
        mainClock.autoAdvance = false
        host()
        target = "B"
        settle()

        assertEquals("B", layers.single().page)
        assertEquals(1f, layers.single().alpha)
    }

    @Test
    fun `going to a page and straight back during a crossfade ends on the page gone back to`() = runComposeUiTest {
        mainClock.autoAdvance = false
        host()
        target = "B"
        stepUntil("the crossfade to B") { layers.size == 2 }
        target = "C"
        mainClock.advanceTimeByFrame()
        target = "B"
        settle()

        assertEquals("B", layers.single().page, "C was queued and then left, so it must not play")
    }

    @Test
    fun `a restated page is drawn in place without a fade`() = runComposeUiTest {
        mainClock.autoAdvance = false
        host()
        val shown = layers.single()

        target = "A2"
        repeat(FADE_MS / FRAME_MS) {
            mainClock.advanceTimeByFrame()
            assertEquals(1, layers.size, "a restatement never adds a layer")
        }

        assertSame(shown, layers.single())
        assertEquals("A2", shown.page)
        assertEquals(1f, shown.alpha)
    }

    @Test
    fun `without crossfade the next page replaces the shown one in its layer`() = runComposeUiTest {
        mainClock.autoAdvance = false
        host(crossfade = false)
        val shown = layers.single()

        target = "B"
        stepUntil("the swap") { shown.page == "B" }

        assertSame(shown, layers.single())
        assertEquals(1f, shown.alpha)
    }

    private companion object {
        const val FADE_MS = 320
        const val FRAME_MS = 16
    }
}
