@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the two tabs' controls say before anything is clicked: the animation chosen last time, named
 * on its selector, and the tooltips that are the only readout of the play and loop buttons' state.
 */
class SlidesAnimationLabelTest {

    private val saved = mapOf(
        Constants.ANIMATION_FADE to "Fade",
        Constants.ANIMATION_SLIDE_LEFT to "Slide Left",
        Constants.ANIMATION_SLIDE_RIGHT to "Slide Right",
        Constants.ANIMATION_NONE to "None",
    )

    private fun ComposeUiTest.countOf(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    @Test
    fun `the presentation tab names the animation it was left on`() {
        for ((type, label) in saved) {
            val withType: (AppSettings) -> AppSettings =
                { it.copy(presentationSettings = it.presentationSettings.copy(animationType = type)) }
            presentationTab(settings = withType) { _, _ ->
                assertTrue(countOf(label) >= 1, "$type is shown as $label")
                assertEquals(0, countOf("Crossfade"), "$type is not shown as the default")
            }
        }
    }

    @Test
    fun `the pictures tab names the animation it was left on`() {
        for ((type, label) in saved) {
            val withType: (AppSettings) -> AppSettings =
                { it.copy(pictureSettings = it.pictureSettings.copy(animationType = type)) }
            picturesTab(settings = withType) { _, _ ->
                assertTrue(countOf(label) >= 1, "$type is shown as $label")
                assertEquals(0, countOf("Crossfade"), "$type is not shown as the default")
            }
        }
    }

    /** Hovers the button named [description] long enough for its tooltip, and returns how many [text]s it added. */
    private fun ComposeUiTest.tooltipAdds(description: String, text: String): Int {
        val before = countOf(text)
        onNode(hasClickAction() and hasContentDescription(description)).performMouseInput { moveTo(center) }
        waitForIdle()
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()
        return countOf(text) - before
    }

    @Test
    fun `the presentation tab's play and loop buttons say what they will do`() = presentationTab { _, _ ->
        assertEquals(1, tooltipAdds("Play", "Play"))
        assertEquals(1, tooltipAdds("Loop On", "Loop On"))
    }

    private companion object {
        const val TOOLTIP_WAIT_MS = 2_000L
    }
}
