@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.WindowLayoutSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import org.churchpresenter.ui.assertColorFieldShows
import org.churchpresenter.ui.assertNumberFieldShows
import org.churchpresenter.ui.confirmColorDialogWith
import org.churchpresenter.ui.retypeNumberField
import org.churchpresenter.ui.styleButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The controls down the left of the tab — colour, alignment, type, shadow — and the three on the
 * timer row that nothing else drives: Reset, the AM/PM toggle and the speed slider.
 *
 * Every fixture gives the control under test a colour or a number no other control on the tab is
 * holding, because that is how the shared `:ui-components` helpers address them.
 */
class AnnouncementsTabControlsTest {

    /**
     * The shared `recolor` helper scrolls the field into view first; nothing on this tab is inside a
     * scrollable, so it is opened directly instead. The round trip is otherwise the same.
     */
    private fun ComposeUiTest.recolorHere(fromHex: String, toHex: String) {
        onAllNodes(hasClickAction() and hasText(fromHex, ignoreCase = true))[0].performClick()
        waitForIdle()
        confirmColorDialogWith(toHex)
        assertColorFieldShows(toHex, "the colour field just edited")
    }

    // ── Colour ──────────────────────────────────────────────────────────────────

    @Test
    fun `the text colour is recoloured through its own field`() =
        announcementsTab(initial = AnnouncementsSettings(textColor = "#AABBCC")) { _, reports ->
            recolorHere("#AABBCC", "#112233")

            assertEquals("#112233", reports.settings?.textColor)
        }

    @Test
    fun `the background colour is recoloured through its own field`() =
        announcementsTab(initial = AnnouncementsSettings(backgroundColor = "#123456")) { _, reports ->
            recolorHere("#123456", "#654321")

            assertEquals("#654321", reports.settings?.backgroundColor)
        }

    // ── Alignment ───────────────────────────────────────────────────────────────

    /**
     * The three alignment buttons, left to right as drawn.
     *
     * They carry neither text nor a content description, so they are addressed by shape: 28dp
     * squares are what `HorizontalAlignmentButtons` draws and nothing else on this tab does.
     */
    private fun ComposeUiTest.alignmentButtons() =
        onAllNodes(hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .filter { it.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() }
            .filter { it.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() }
            .filter { it.size.width == it.size.height && it.size.width in 26..30 }
            .sortedBy { it.boundsInRoot.left }

    @Test
    fun `the alignment buttons are wired right, centre, left`() = announcementsTab { _, reports ->
        val expected = listOf(Constants.RIGHT, Constants.CENTER, Constants.LEFT)
        val picked = expected.indices.map { i ->
            val button = alignmentButtons().getOrNull(i)
                ?: error("only ${alignmentButtons().size} alignment buttons are on screen")
            onRoot().performMouseInput { click(button.boundsInRoot.center) }
            waitForIdle()
            reports.settings?.horizontalAlignment
        }

        // Not a formality: the tab passes leftValue/centerValue/rightValue by name into a row that
        // draws them in the opposite order, so a mix-up would align text the wrong way with nothing
        // on screen to show for it.
        assertEquals(expected, picked, "the row is drawn right-to-left")
    }

    // ── Type ────────────────────────────────────────────────────────────────────

    @Test
    fun `a font size typed into its field is stored`() =
        announcementsTab(initial = AnnouncementsSettings(fontSize = 48)) { _, reports ->
            retypeNumberField(showing = 48, to = 72)

            assertEquals(72, reports.settings?.fontSize)
        }

    @Test
    fun `a font size outside the allowed range is shown but not stored`() =
        announcementsTab(initial = AnnouncementsSettings(fontSize = 48)) { _, reports ->
            retypeNumberField(showing = 48, to = 900)

            assertEquals(0, reports.settingsChanges, "8..200 is the range the field accepts")
        }

    // ── Shadow ──────────────────────────────────────────────────────────────────

    @Test
    fun `turning the shadow on reveals its own colour, size and opacity`() =
        announcementsTab(
            initial = AnnouncementsSettings(shadowColor = "#654321", shadowSize = 123, shadowOpacity = 45),
        ) { _, reports ->
            styleButton(group = 0, label = AnnouncementLabel.SHADOW).performClick()
            waitForIdle()

            assertEquals(true, reports.settings?.shadow, "the S button turns the shadow on")
            assertNumberFieldShows(123, "the shadow size")
            assertNumberFieldShows(45, "the shadow opacity")

            recolorHere("#654321", "#0F0F0F")
            assertEquals("#0F0F0F", reports.settings?.shadowColor)

            retypeNumberField(showing = 123, to = 200)
            assertEquals(200, reports.settings?.shadowSize)

            retypeNumberField(showing = 45, to = 60)
            assertEquals(60, reports.settings?.shadowOpacity)
        }

    // ── The timer row ───────────────────────────────────────────────────────────

    @Test
    fun `Reset stops a running duration and puts it back where it started`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerMode = Constants.TIMER_MODE_DURATION, timerMinutes = 3),
        ) { output, _ ->
            annButton(AnnouncementLabel.START).performClick()
            waitForIdle()
            assertTrue(output.tickerActive, "the timer must be running before Reset means anything")

            timerButton(AnnouncementLabel.RESET).performClick()
            waitForIdle()

            assertEquals(false, output.tickerActive, "Reset stops the ticker")
            assertEquals(3 * SECONDS_PER_MINUTE, output.pausedAt, "and hands back the full duration")
        }

    @Test
    fun `the AM PM toggle moves the target hour by twelve`() {
        // Under a 24-hour locale the tab does not draw the toggle at all.
        if (isSystemUsing24HourFormat()) return
        announcementsTab(
            initial = AnnouncementsSettings(
                timerMode = Constants.TIMER_MODE_CLOCK,
                targetHour = 9,
                targetMinute = 41,
                targetSecond = 17,
            ),
            // Three digit columns plus the toggle do not fit the 300dp the panel opens at, and a
            // Row gives its last child nothing rather than shrinking the rest — so at the shipped
            // width the toggle is measured to zero and cannot be clicked.
            settings = ::withARoomyLeftPanel,
        ) { _, reports ->
            val toggle = clickableAround("AM")
            onRoot().performMouseInput { click(toggle) }
            waitForIdle()
            assertEquals(21, reports.settings?.targetHour, "9am becomes 9pm")

            onRoot().performMouseInput { click(clickableAround("PM")) }
            waitForIdle()
            assertEquals(9, reports.settings?.targetHour, "and back again")
        }
    }

    /** The centre of the clickable box wrapping the text [label], which is not itself clickable. */
    private fun ComposeUiTest.clickableAround(label: String): Offset {
        val text = onAllNodes(hasText(label)).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .firstOrNull { it.boundsInRoot.width > 0f } ?: error("\"$label\" is not on screen")
        val box = onAllNodes(hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .firstOrNull { it.boundsInRoot.contains(text.boundsInRoot.center) }
            ?: error("\"$label\" sits in nothing clickable")
        return box.boundsInRoot.center
    }

    // ── Speed ───────────────────────────────────────────────────────────────────

    @Test
    fun `clicking further left along the speed slider lengthens the animation`() =
        announcementsTab(width = 1200.dp) { _, reports ->
            // The slider publishes no semantics of its own — it is a Canvas behind a tap detector —
            // so it is found through the readout beside it, which sits 10.dp off its right edge.
            val seconds = Regex("""^\d+\.\ds$""")
            val readout = onAllNodes(hasText("s", substring = true))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .first { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { seconds.matches(it.text) } == true
                }
            val trackEnd = readout.boundsInRoot.left - 10f
            val y = readout.boundsInRoot.center.y

            onRoot().performMouseInput { click(Offset(trackEnd - 4f, y)) }
            waitForIdle()
            val nearTheRightEnd = reports.settings?.animationDuration
                ?: error("clicking the track must set a duration")

            onRoot().performMouseInput { click(Offset(trackEnd - 200f, y)) }
            waitForIdle()
            val furtherLeft = reports.settings?.animationDuration ?: error("the second click set nothing")

            // The slider is inverted — it reads as a speed, so its value counts down to the right.
            assertTrue(
                furtherLeft > nearTheRightEnd,
                "further left must be a longer animation ($furtherLeft vs $nearTheRightEnd)",
            )
        }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val ROOMY_LEFT_PANEL_DP = 460

        fun withARoomyLeftPanel(settings: AppSettings) = settings.copy(
            maximizedLayout = WindowLayoutSettings(announcementsLeftPanelWidthDp = ROOMY_LEFT_PANEL_DP),
        )
    }
}
