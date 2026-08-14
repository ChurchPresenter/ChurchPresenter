@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The preview's background and entrance animation — how the announcement itself looks, as opposed
 * to what it says. See `AnnouncementsTabTestSupport.kt` for the harness.
 */
class AnnouncementsTabAppearanceTest {

    // ── Background ──────────────────────────────────────────────────────────────

    @Test
    fun `an explicitly transparent background offers to give it a real color`() =
        announcementsTab(initial = AnnouncementsSettings(backgroundColor = "transparent")) { _, reports ->
            assertTrue(showsContainingText(AnnouncementLabel.TRANSPARENT))

            onNodeWithText(AnnouncementLabel.TRANSPARENT).performClick()
            waitForIdle()

            assertEquals("#000000", reports.settings?.backgroundColor, "clicking it gives a real starting color")
        }

    // ── Animation type ────────────────────────────────────────────────────────────

    @Test
    fun `every animation option can be chosen and is remembered`() = announcementsTab { _, reports ->
        fun select(label: String) {
            onNodeWithText("ANIMATION", substring = true).performClick()
            waitForIdle()
            onNodeWithText(label).performClick()
            waitForIdle()
        }

        select("Slide From Top")
        assertEquals(Constants.ANIMATION_SLIDE_FROM_TOP, reports.settings?.animationType)

        select("Slide From Left")
        assertEquals(Constants.ANIMATION_SLIDE_FROM_LEFT, reports.settings?.animationType)

        select("Slide From Right")
        assertEquals(Constants.ANIMATION_SLIDE_FROM_RIGHT, reports.settings?.animationType)

        select("Fade")
        assertEquals(Constants.ANIMATION_FADE, reports.settings?.animationType)

        select("None")
        assertEquals(Constants.ANIMATION_NONE, reports.settings?.animationType)

        select("Slide From Bottom")
        assertEquals(Constants.ANIMATION_SLIDE_FROM_BOTTOM, reports.settings?.animationType, "back to the shipped default")
    }

    @Test
    fun `each slide-from option renders as its own label`() {
        for ((constant, label) in listOf(
            Constants.ANIMATION_SLIDE_FROM_LEFT to "Slide From Left",
            Constants.ANIMATION_SLIDE_FROM_RIGHT to "Slide From Right",
            Constants.ANIMATION_SLIDE_FROM_TOP to "Slide From Top",
            Constants.ANIMATION_FADE to "Fade",
        )) {
            announcementsTab(initial = AnnouncementsSettings(animationType = constant)) { _, _ ->
                assertTrue(showsContainingText(label), "$constant should render as \"$label\": ${renderedText()}")
            }
        }
    }

    @Test
    fun `every screen position is honored by the static preview too`() =
        announcementsTab(initial = AnnouncementsSettings(animationType = Constants.ANIMATION_FADE)) { _, reports ->
            // Fade/None take a different preview branch than the slide-from animations, with its
            // own position-to-alignment mapping — this walks every position under that branch.
            for (label in listOf(
                "Top Left", "Top Center", "Top Right",
                "Center Left", "Center Right",
                "Bottom Left", "Bottom Center", "Bottom Right",
                AnnouncementLabel.CENTER,
            )) {
                clickLabel(label)
                assertEquals(label, reports.settings?.position)
            }
        }

    // ── Live clock format ─────────────────────────────────────────────────────────

    @Test
    fun `the live clock format can be changed`() = announcementsTab { _, reports ->
        clickLabel(AnnouncementLabel.CLOCK_DISPLAY_MODE)

        onNodeWithText("FORMAT", substring = true).performClick()
        waitForIdle()
        onAllNodesWithText("24-Hour (HH:mm)")[0].performClick()
        waitForIdle()

        assertEquals("HH:mm", reports.settings?.liveClockFormat)
    }

    // ── Loop count ────────────────────────────────────────────────────────────────

    @Test
    fun `the loop count can be edited directly`() = announcementsTab { _, reports ->
        loopCountIncrement().performClick()
        waitForIdle()

        assertEquals(1, reports.settings?.loopCount)
    }
}
