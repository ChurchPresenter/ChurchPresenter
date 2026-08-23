@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.assertColorFieldShows
import org.churchpresenter.ui.recolor

/**
 * Drives the Card Background and Transitions sections — the tab's two sliders and its two transition
 * switches.
 *
 * `SlimSlider` publishes no semantics for its track, so it is clicked through the gap between its
 * caption and its readout (see [clickSlider]). Both ends of both ranges are reached exactly, because
 * the slider coerces the tapped fraction into 0..1; the interior of the range is asserted by
 * direction and by the readout following the stored value rather than by an exact pixel-to-value
 * mapping, which would differ across platforms.
 */
class DictionarySettingsTabCardAndTransitionsTest {

    // ── Card background colour ──────────────────────────────────────────────────────────────────

    @Test
    fun `the card background colour field stores the confirmed hex`() {
        dictionaryTab(initial = dictionarySettings { copy(cardBackgroundColor = "#2B2D42") }) { get ->
            recolor(fromHex = "#2B2D42", toHex = "#8D99AE")
            assertTrue(
                get().dictionarySettings.cardBackgroundColor.equals("#8D99AE", ignoreCase = true),
                "the confirmed hex must become the card background colour",
            )
            assertColorFieldShows("#8D99AE", "the card background colour field")
            assertEquals("#FFFFFF", get().dictionarySettings.wordColor, "the word colour must be untouched")
        }
    }

    // ── Card background opacity ─────────────────────────────────────────────────────────────────

    @Test
    fun `the opacity readout starts on the stored value`() = dictionaryTab { get ->
        assertEquals(0.92f, get().dictionarySettings.cardBackgroundOpacity, "0.92 out of the box")
        assertOpacityReads(0.92f)
    }

    @Test
    fun `dragging the opacity slider left of its end lowers the stored opacity`() = dictionaryTab { get ->
        clickSlider("Opacity", "%", fraction = 0.4f)
        val stored = get().dictionarySettings.cardBackgroundOpacity
        assertTrue(stored < 0.92f, "clicking left of the stored point must lower the opacity, was $stored")
        assertBetweenFloat("the card opacity", stored, 0f, 1f)
        assertOpacityReads(stored)
    }

    @Test
    fun `the opacity slider reaches both ends of its range`() = dictionaryTab { get ->
        dragSliderToEnd("Opacity", "%", toRight = false)
        assertEquals(0f, get().dictionarySettings.cardBackgroundOpacity, "the left end is fully transparent")
        assertOpacityReads(0f)

        dragSliderToEnd("Opacity", "%", toRight = true)
        assertEquals(1f, get().dictionarySettings.cardBackgroundOpacity, "the right end is fully opaque")
        assertOpacityReads(1f)
    }

    @Test
    fun `the opacity slider is monotonic across its track`() = dictionaryTab { get ->
        clickSlider("Opacity", "%", fraction = 0.25f)
        val low = get().dictionarySettings.cardBackgroundOpacity
        clickSlider("Opacity", "%", fraction = 0.75f)
        val high = get().dictionarySettings.cardBackgroundOpacity
        assertTrue(low < high, "further right must mean more opaque, was $low then $high")
    }

    /**
     * Asserts the drag landed as well as that it stayed put. "The duration did not change" holds
     * just as well when nothing happened at all, so on its own it would pass against a tab wired to
     * nothing — the moved value is what makes the isolation claim mean something.
     */
    @Test
    fun `the opacity slider leaves the transition duration alone`() = dictionaryTab { get ->
        clickSlider("Opacity", "%", fraction = 0.3f)
        assertTrue(
            get().dictionarySettings.cardBackgroundOpacity != 0.92f,
            "the opacity must actually have moved, or this proves nothing about the duration",
        )
        assertEquals(500f, get().dictionarySettings.transitionDuration, "the duration must be untouched")
    }

    // ── Fade switches ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the fade in switch turns off and back on`() = dictionaryTab { get ->
        switch(Switches.FADE_IN).assertIsOn()

        switch(Switches.FADE_IN).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.fadeIn, "switching off must be stored")
        switch(Switches.FADE_IN).assertIsOff()
        assertTrue(get().dictionarySettings.fadeOut, "fade out must be untouched")

        switch(Switches.FADE_IN).performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.fadeIn, "switching back on must be stored too")
        switch(Switches.FADE_IN).assertIsOn()
    }

    @Test
    fun `the fade out switch turns off and back on`() = dictionaryTab { get ->
        switch(Switches.FADE_OUT).assertIsOn()

        switch(Switches.FADE_OUT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.fadeOut, "switching off must be stored")
        switch(Switches.FADE_OUT).assertIsOff()
        assertTrue(get().dictionarySettings.fadeIn, "fade in must be untouched")

        switch(Switches.FADE_OUT).performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.fadeOut, "switching back on must be stored too")
        switch(Switches.FADE_OUT).assertIsOn()
    }

    // ── Transition duration ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the duration readout starts on the stored value`() = dictionaryTab { get ->
        assertEquals(500f, get().dictionarySettings.transitionDuration, "500ms out of the box")
        assertDurationReads(500f)
    }

    @Test
    fun `the duration slider reaches both ends of its range`() = dictionaryTab { get ->
        dragSliderToEnd("Transition Duration", "ms", toRight = false)
        assertEquals(100f, get().dictionarySettings.transitionDuration, "the range starts at 100ms")
        assertDurationReads(100f)

        dragSliderToEnd("Transition Duration", "ms", toRight = true)
        assertEquals(2000f, get().dictionarySettings.transitionDuration, "and ends at 2000ms")
        assertDurationReads(2000f)
    }

    @Test
    fun `dragging the duration slider right of its start raises the stored duration`() = dictionaryTab { get ->
        clickSlider("Transition Duration", "ms", fraction = 0.8f)
        val stored = get().dictionarySettings.transitionDuration
        assertTrue(stored > 500f, "clicking right of the stored point must raise the duration, was $stored")
        assertBetweenFloat("the transition duration", stored, 100f, 2000f)
        assertDurationReads(stored)
    }

    /** As above: the moved value is what gives "the opacity is untouched" any force. */
    @Test
    fun `the duration slider leaves the card opacity alone`() = dictionaryTab { get ->
        clickSlider("Transition Duration", "ms", fraction = 0.6f)
        assertTrue(
            get().dictionarySettings.transitionDuration != 500f,
            "the duration must actually have moved, or this proves nothing about the opacity",
        )
        assertEquals(0.92f, get().dictionarySettings.cardBackgroundOpacity, "the opacity must be untouched")
    }
}
