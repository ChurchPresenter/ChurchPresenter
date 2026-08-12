@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The slideshow loop toggle's tooltip, which is the only thing on screen that says whether looping
 * is on.
 *
 * The button is an icon with no label, and its two states differ only by container colour — so the
 * tooltip is not a convenience here, it is the readout. One stuck on `Loop On` would tell the
 * operator the opposite of the truth, and the failure is silent until a slideshow dead-ends at the
 * last picture mid-service.
 *
 * The button now carries the same `Loop On`/`Loop Off` string as its content description, so it is
 * addressable by name. It used to be addressed as the only clickable node carrying neither text nor
 * a description — a selector that worked, but one that selected on the *absence* of a label and so
 * broke the moment the button was given one.
 *
 * The tooltip is still what this tests: [countOf] matches `Text` semantics only, which the content
 * description does not contribute to, so hovering must still raise the count by one.
 */
class PicturesTabLoopTooltipTest {

    /** The loop toggle, by the name it now carries in whichever state it is in. */
    private fun ComposeUiTest.loopButton(): SemanticsNodeInteraction {
        val matches = onAllNodes(
            hasClickAction() and
                (hasContentDescription("Loop On") or hasContentDescription("Loop Off"))
        )
        assertEquals(
            1, matches.fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "the loop toggle is addressed by its content description",
        )
        return matches[0]
    }

    private fun ComposeUiTest.countOf(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).size

    private fun ComposeUiTest.hoverLoop() {
        loopButton().performMouseInput { moveTo(center) }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    @Test
    fun `with looping on the tooltip says so`() = picturesTab { vm, _ ->
        // Looping is the default, so this is what an operator sees without touching anything.
        assertEquals(true, vm.isLooping)
        val before = countOf("Loop On")

        hoverLoop()

        assertEquals(before + 1, countOf("Loop On"))
    }

    @Test
    fun `with looping off it says the other thing`() {
        // The readout has to follow the state. The two buttons differ only by container colour, so
        // a tooltip stuck on one string is invisible until the slideshow dead-ends at the last
        // picture — and by then the operator is mid-service wondering why it stopped.
        picturesTab(
            settings = { it.copy(pictureSettings = it.pictureSettings.copy(isLooping = false)) },
        ) { vm, _ ->
            assertEquals(false, vm.isLooping)
            val beforeOff = countOf("Loop Off")
            val beforeOn = countOf("Loop On")

            hoverLoop()

            assertEquals(beforeOff + 1, countOf("Loop Off"))
            assertEquals(beforeOn, countOf("Loop On"), "and must not still claim looping is on")
        }
    }
}
