@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementsTabTargetTimeTest {

    private val use24Hour = isSystemUsing24HourFormat()

    private fun clockSettings(hour: Int) = AnnouncementsSettings(
        timerMode = Constants.TIMER_MODE_CLOCK,
        targetHour = hour,
        targetMinute = 41,
        targetSecond = 17,
    )

    private fun ComposeUiTest.typeHour(shown: String, replacement: String) {
        onNodeWithText(shown).performTextReplacement(replacement)
        waitForIdle()
    }

    @Test
    fun `a morning hour is stored as it was typed`() =
        announcementsTab(initial = clockSettings(9)) { _, reports ->
            typeHour("09", "8")

            assertEquals(8, reports.settings?.targetHour)
        }

    @Test
    fun `letters typed into the hour are ignored`() =
        announcementsTab(initial = clockSettings(9)) { _, reports ->
            typeHour("09", "7pm")

            assertEquals(7, reports.settings?.targetHour, "only the digits are read")
        }

    @Test
    fun `more than two digits are cut to two`() =
        announcementsTab(initial = clockSettings(9)) { _, reports ->
            typeHour("09", "115")

            assertEquals(11, reports.settings?.targetHour)
        }

    @Test
    fun `midnight is stored as hour zero rather than twelve`() =
        announcementsTab(initial = clockSettings(9)) { _, reports ->
            typeHour("09", "12")

            val expected = if (use24Hour) 12 else 0
            assertEquals(
                expected,
                reports.settings?.targetHour,
                "12 in the morning is midnight, not noon",
            )
        }

    @Test
    fun `an afternoon hour keeps its afternoon meaning`() {
        val startHour = 15
        announcementsTab(initial = clockSettings(startHour)) { _, reports ->
            val shown = if (use24Hour) "15" else "03"

            typeHour(shown, "4")

            val expected = if (use24Hour) 4 else 16
            assertEquals(expected, reports.settings?.targetHour)
        }
    }

    @Test
    fun `noon stays noon in the afternoon half of the day`() {
        val startHour = 15
        announcementsTab(initial = clockSettings(startHour)) { _, reports ->
            val shown = if (use24Hour) "15" else "03"

            typeHour(shown, "12")

            assertEquals(12, reports.settings?.targetHour, "12 in the afternoon is noon")
        }
    }

    @Test
    fun `an hour beyond the clock is pulled back onto it`() =
        announcementsTab(initial = clockSettings(9)) { _, reports ->
            typeHour("09", "99")

            val stored = reports.settings?.targetHour
            assertEquals(true, stored != null && stored in 0..23, "stored $stored is not a real hour")
        }

    @Test
    fun `clearing the hour leaves the stored value alone`() =
        announcementsTab(initial = clockSettings(9)) { _, reports ->
            typeHour("09", "")

            assertEquals(
                9,
                reports.settings?.targetHour ?: 9,
                "an empty box is mid-edit, not a request to jump to midnight",
            )
        }
}
