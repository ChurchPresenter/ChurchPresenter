@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.WindowLayoutSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The timer's pickers as the operator drives them: the + and - keys, the digits typed into the
 * wells, AM/PM, and the save-preset buttons beside each card. Also the divider between the columns.
 */
class AnnouncementsTabSteppersTest {

    private companion object {
        const val HR = "hr"
        const val MIN = "min"
        const val SEC = "sec"
        const val SAVE_PRESET = "Save preset"
    }

    private fun ComposeUiTest.step(label: String, up: Boolean) {
        onNodeWithTag(timerStepTag(label, up)).performClick()
        waitForIdle()
    }

    private fun AnnouncementReports.target() =
        settings!!.let { Triple(it.targetHour, it.targetMinute, it.targetSecond) }

    private fun ComposeUiTest.type(label: String, digits: String) {
        onNodeWithTag(timerFieldTag(label)).performTextReplacement(digits)
        waitForIdle()
    }

    @Test
    fun `the duration keys step hours, minutes and seconds up and down`() =
        announcementsTab { _, reports ->
            step(HR, up = true)
            step(MIN, up = true)
            step(SEC, up = true)
            val up = reports.settings!!
            assertEquals(Triple(1, 1, 5), Triple(up.timerHours, up.timerMinutes, up.timerSeconds))

            step(SEC, up = false)
            step(MIN, up = false)
            step(HR, up = false)
            val down = reports.settings!!
            assertEquals(Triple(0, 0, 0), Triple(down.timerHours, down.timerMinutes, down.timerSeconds))
        }

    @Test
    fun `digits typed into the duration wells set it, seconds no further than 59`() =
        announcementsTab { _, reports ->
            type(HR, "2")
            type(MIN, "07")
            type(SEC, "75")

            val s = reports.settings!!
            assertEquals(Triple(2, 7, 59), Triple(s.timerHours, s.timerMinutes, s.timerSeconds))
        }

    @Test
    fun `the target keys step a specific time, carrying across the hour`() =
        announcementsTab(
            initial = AnnouncementsSettings(
                timerMode = Constants.TIMER_MODE_CLOCK, targetHour = 9, targetMinute = 59, targetSecond = 55,
            ),
        ) { _, reports ->
            step(SEC, up = true)
            assertEquals(Triple(10, 0, 0), reports.target())

            step(SEC, up = false)
            step(MIN, up = false)
            step(HR, up = false)
            step(MIN, up = true)
            step(HR, up = true)
            // Back across the hour to 9:59:55, then down and up a minute and an hour each.
            assertEquals(Triple(9, 59, 55), reports.target())
        }

    @Test
    fun `digits typed into the target wells set it, on the half of the day shown`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK, targetHour = 14),
        ) { _, reports ->
            type(HR, "3")
            type(MIN, "45")
            type(SEC, "99")

            val s = reports.settings!!
            assertEquals(Triple(15, 45, 59), Triple(s.targetHour, s.targetMinute, s.targetSecond), "3 PM")
        }

    @Test
    fun `AM and PM swap the target between halves of the day`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK, targetHour = 9),
        ) { _, reports ->
            onNodeWithText("AM").performClick()
            waitForIdle()
            assertEquals(21, reports.settings!!.targetHour)

            onNodeWithText("PM").performClick()
            waitForIdle()
            assertEquals(9, reports.settings!!.targetHour)
        }

    @Test
    fun `a 24-hour clock has no AM or PM, and takes the hour as typed`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK, targetHour = 9),
            use24HourClock = true,
        ) { _, reports ->
            assertTrue(!showsExactly("AM") && !showsExactly("PM"))

            type(HR, "18")
            assertEquals(18, reports.settings!!.targetHour)
        }

    @Test
    fun `the text card and the timer card each save a preset of what they hold`() =
        announcementsTab(
            initial = AnnouncementsSettings(text = "Welcome", timerMinutes = 3),
            withOnSavePreset = true,
        ) { _, reports ->
            annButton(SAVE_PRESET).performClick()
            waitForIdle()
            timerButton(SAVE_PRESET).performClick()
            waitForIdle()

            assertEquals(listOf("Welcome", "Welcome"), reports.presets.map { it.text })
            assertEquals(3, reports.presets.last().timerMinutes)
        }

    @Test
    fun `dragging the column divider widens the left column and remembers it`() =
        announcementsTab { _, reports ->
            onNodeWithTag(ANNOUNCEMENTS_SPLIT_DIVIDER_TAG).performTouchInput {
                down(center)
                moveBy(Offset(DRAG_PX, 0f))
                up()
            }
            waitForIdle()

            val app = reports.appSettings!!
            assertTrue(
                app.maximizedLayout.announcementsLeftPanelWidthDp > DEFAULT_LEFT_DP,
                "the width saved is the dragged one: ${app.maximizedLayout.announcementsLeftPanelWidthDp}",
            )
        }

    @Test
    fun `an expired countdown shows its message, or the default one`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerMinutes = 1, timerExpiredText = "We are starting"),
        ) { presenter, _ ->
            presenter.announcementTimerExpired.value = true
            waitForIdle()
            assertTrue(showsContainingText("We are starting"))
        }

    @Test
    fun `an expired countdown with no message says time is up`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 1)) { presenter, _ ->
            presenter.announcementTimerExpired.value = true
            waitForIdle()
            assertTrue(showsContainingText("Time's up!"))
        }

    @Test
    fun `a running countdown previews the output's time, not the configured one`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 10)) { presenter, _ ->
            presenter.announcementTickerActive.value = true
            presenter.timerRemainingSeconds.value = 125
            waitForIdle()
            assertTrue(showsExactly("02:05"))
        }

    @Test
    fun `in a floating window the divider saves to the windowed layout`() =
        announcementsTab(floatingWindow = true) { _, reports ->
            onNodeWithTag(ANNOUNCEMENTS_SPLIT_DIVIDER_TAG).performTouchInput {
                down(center)
                moveBy(Offset(DRAG_PX, 0f))
                up()
            }
            waitForIdle()

            val app = reports.appSettings!!
            assertTrue(app.windowedLayout.announcementsLeftPanelWidthDp > DEFAULT_LEFT_DP)
            assertEquals(
                DEFAULT_LEFT_DP,
                app.maximizedLayout.announcementsLeftPanelWidthDp,
                "the maximised one is left",
            )
        }

    @Test
    fun `the tab draws with only its settings, as a preview composes it`() = runDesktopComposeUiTest(1024, 1200) {
        val settings = AppSettings(announcementsSettings = AnnouncementsSettings("Hello"))
        setContent { AnnouncementsTab(appSettings = settings) }
        waitForIdle()

        assertTrue(showsContainingText("Hello"))
    }

    @Test
    fun `a transparent background can be given a colour again`() =
        announcementsTab(
            initial = AnnouncementsSettings(text = "Notices", backgroundColor = Constants.COLOR_VALUE_TRANSPARENT),
        ) { _, reports ->
            onNodeWithText("Transparent (Default)").performClick()
            waitForIdle()

            assertEquals("#000000", reports.settings?.backgroundColor)
        }

    @Test
    fun `every screen position can be chosen, and the preview follows it`() =
        announcementsTab(initial = AnnouncementsSettings(text = "Notices")) { _, reports ->
            listOf(
                Constants.TOP_LEFT, Constants.TOP_CENTER, Constants.TOP_RIGHT,
                Constants.CENTER_LEFT, Constants.CENTER, Constants.CENTER_RIGHT,
                Constants.BOTTOM_LEFT, Constants.BOTTOM_CENTER, Constants.BOTTOM_RIGHT,
            ).forEach { position ->
                clickPosition(position)
                assertEquals(position, reports.settings?.position)
                assertTrue(showsContainingText("Notices"), "the preview still shows the text at $position")
            }
        }
}

private const val DRAG_PX = 80f

/** `WindowLayoutSettings.announcementsLeftPanelWidthDp`'s default. */
private val DEFAULT_LEFT_DP = WindowLayoutSettings().announcementsLeftPanelWidthDp
