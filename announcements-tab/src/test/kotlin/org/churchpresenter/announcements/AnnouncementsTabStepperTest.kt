@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The six numeric columns — hr/min/sec for a duration, and hr/min/sec for a target time — driven
 * through their own arrows and their own field.
 *
 * The arrows carry a content description naming the field ("hr Increment"), which is what tells the
 * six identically-drawn columns apart both here and to a screen reader.
 */
class AnnouncementsTabStepperTest {

    private fun ComposeUiTest.step(field: String, up: Boolean) {
        onNodeWithContentDescription("$field ${if (up) "Increment" else "Decrement"}").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.type(shown: String, replacement: String) {
        onNodeWithText(shown).performTextReplacement(replacement)
        waitForIdle()
    }

    // ── Duration: hr / min / sec ────────────────────────────────────────────────

    private fun duration(h: Int = 1, m: Int = 30, s: Int = 45) = AnnouncementsSettings(
        timerMode = Constants.TIMER_MODE_DURATION,
        timerHours = h,
        timerMinutes = m,
        timerSeconds = s,
    )

    @Test
    fun `the duration arrows move each field by one`() = announcementsTab(initial = duration()) { _, reports ->
        step("hr", up = true)
        assertEquals(2, reports.settings?.timerHours)

        step("min", up = true)
        assertEquals(31, reports.settings?.timerMinutes)

        step("sec", up = true)
        assertEquals(50, reports.settings?.timerSeconds, "seconds step to the next multiple of five")

        step("hr", up = false)
        assertEquals(1, reports.settings?.timerHours)

        step("min", up = false)
        assertEquals(30, reports.settings?.timerMinutes)

        step("sec", up = false)
        assertEquals(45, reports.settings?.timerSeconds)
    }

    @Test
    fun `a duration typed into each field is stored`() = announcementsTab(initial = duration()) { _, reports ->
        type("01", "7")
        assertEquals(7, reports.settings?.timerHours)

        type("30", "12")
        assertEquals(12, reports.settings?.timerMinutes)

        type("45", "9")
        assertEquals(9, reports.settings?.timerSeconds)
    }

    @Test
    fun `letters and extra digits typed into a duration field are dropped`() =
        announcementsTab(initial = duration()) { _, reports ->
            type("30", "4m2x")

            assertEquals(42, reports.settings?.timerMinutes, "only the first two digits are read")
        }

    @Test
    fun `a duration second past fifty-nine is clamped`() = announcementsTab(initial = duration()) { _, reports ->
        type("45", "88")

        assertEquals(59, reports.settings?.timerSeconds)
    }

    // ── Target time: hr / min / sec ─────────────────────────────────────────────

    private fun target() = AnnouncementsSettings(
        timerMode = Constants.TIMER_MODE_CLOCK,
        targetHour = 10,
        targetMinute = 20,
        targetSecond = 30,
    )

    @Test
    fun `the target time arrows move each field by one`() = announcementsTab(initial = target()) { _, reports ->
        step("min", up = true)
        assertEquals(21, reports.settings?.targetMinute)

        step("sec", up = true)
        assertEquals(35, reports.settings?.targetSecond, "seconds step to the next multiple of five")

        step("min", up = false)
        assertEquals(20, reports.settings?.targetMinute)

        step("sec", up = false)
        assertEquals(30, reports.settings?.targetSecond)
    }

    @Test
    fun `the target hour arrows move it by one`() = announcementsTab(initial = target()) { _, reports ->
        step("hr", up = true)
        assertEquals(11, reports.settings?.targetHour)

        step("hr", up = false)
        assertEquals(10, reports.settings?.targetHour)
    }

    @Test
    fun `a target time typed into the minute and second fields is stored`() =
        announcementsTab(initial = target()) { _, reports ->
            type("20", "5")
            assertEquals(5, reports.settings?.targetMinute)

            type("30", "44")
            assertEquals(44, reports.settings?.targetSecond)
        }

    @Test
    fun `a target second past fifty-nine is clamped`() = announcementsTab(initial = target()) { _, reports ->
        type("30", "99")

        assertEquals(59, reports.settings?.targetSecond)
    }
}
