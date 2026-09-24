@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun leftPanel(widthDp: Int): (AppSettings) -> AppSettings = { s ->
        s.copy(
            maximizedLayout = s.maximizedLayout.copy(announcementsLeftPanelWidthDp = widthDp),
            windowedLayout = s.windowedLayout.copy(announcementsLeftPanelWidthDp = widthDp),
        )
    }

    private fun ComposeUiTest.transparentButtonBesideField(): Boolean {
        val field = onNodeWithText("BACKGROUND COLOR", substring = true).fetchSemanticsNode().boundsInRoot
        val button = onNodeWithText(AnnouncementLabel.TRANSPARENT).fetchSemanticsNode().boundsInRoot
        return button.center.y in field.top..field.bottom && button.left > field.left
    }

    @Test
    fun `the transparent button sits beside the colour when there is room`() =
        announcementsTab(settings = leftPanel(400)) { _, _ ->
            assertTrue(transparentButtonBesideField())
        }

    @Test
    fun `the transparent button moves under the colour when the panel is too narrow for both`() =
        announcementsTab(settings = leftPanel(220)) { _, _ ->
            assertFalse(transparentButtonBesideField())
            val field = onNodeWithText("BACKGROUND COLOR", substring = true).fetchSemanticsNode().boundsInRoot
            val button = onNodeWithText(AnnouncementLabel.TRANSPARENT).fetchSemanticsNode().boundsInRoot
            assertTrue(button.top >= field.bottom, "under it: $field vs $button")
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
        assertEquals(
            Constants.ANIMATION_SLIDE_FROM_BOTTOM,
            reports.settings?.animationType,
            "back to the shipped default",
        )
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

    /**
     * Fade/None take a different preview branch than the slide-from animations, with its own
     * position-to-alignment mapping — these walk every position under that branch.
     *
     * **Split by row rather than run as one loop**, and the reason is the clock. That branch draws
     * an `AnimatedContent` keyed on the position, so *changing* the position runs a full fade of the
     * announcement's own display duration, and `waitForIdle` waits for it. Nine of them in one body
     * cost ~9s here and blew `runTest`'s 60s budget on a loaded CI runner — which fails as
     * `UncompletedCoroutinesError`, naming neither the test's own assertion nor the wait. The total
     * is unchanged; what changes is that no single body is anywhere near the limit.
     *
     * The fade is production behaviour and correct, so it is not injectable and not worth making so.
     */
    @Test
    fun `the top row of positions is honored by the static preview`() = walkPositions(
        "Top Left", "Top Center", "Top Right",
    )

    @Test
    fun `the middle row of positions is honored by the static preview`() = walkPositions(
        "Center Left", AnnouncementLabel.CENTER, "Center Right",
    )

    @Test
    fun `the bottom row of positions is honored by the static preview`() = walkPositions(
        "Bottom Left", "Bottom Center", "Bottom Right",
    )

    private fun walkPositions(vararg labels: String) =
        announcementsTab(initial = AnnouncementsSettings(animationType = Constants.ANIMATION_FADE)) { _, reports ->
            for (label in labels) {
                clickPosition(label)
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

    // ── Backdrop ──────────────────────────────────────────────────────────────────

    @Test
    fun `the preview paints the backdrop behind the announcement`() {
        fun magentaPixels(backdrop: TextBackdrop): Int {
            var found = 0
            announcementsTab(
                initial = AnnouncementsSettings(
                    text = "Welcome",
                    animationType = Constants.ANIMATION_NONE,
                    backdrop = backdrop,
                ),
            ) { _, _ ->
                waitForIdle()
                val map = onRoot().captureToImage().toPixelMap()
                for (y in 0 until map.height) for (x in 0 until map.width) {
                    val p = map[x, y]
                    // Near-transparent pixels are shadow fringe over the empty root, not paint.
                    val opaque = p.alpha > 0.5f
                    val magenta = p.red > 0.6f && p.blue > 0.6f && p.green < 0.35f
                    if (opaque && magenta) found++
                }
            }
            return found
        }

        assertEquals(0, magentaPixels(TextBackdrop()), "nothing is painted while the backdrop is off")
        val band = TextBackdrop(
            lineBackground = true,
            lineBackgroundColor = "#FF00FF",
            lineBackgroundOpacity = 100,
        )
        assertTrue(magentaPixels(band) > 0, "the preview must paint the band the presenter will draw")
    }
}
