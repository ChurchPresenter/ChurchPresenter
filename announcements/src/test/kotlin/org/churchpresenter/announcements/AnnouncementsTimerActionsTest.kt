package org.churchpresenter.announcements

import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What Start/Pause and Reset ask of the output in each timer mode. The output does the ticking, so
 * these assert which timer was started with what, and what a pause or a reset leaves showing.
 */
class AnnouncementsTimerActionsTest {

    private val created = mutableListOf<AnnouncementsViewModel>()

    @AfterTest
    fun cleanUp() = created.forEach { it.dispose() }

    private fun viewModel(settings: AnnouncementsSettings) =
        AnnouncementsViewModel().also { created += it; it.syncFromSettings(settings) }

    @Test
    fun `a countdown starts from its configured length`() {
        val vm = viewModel(AnnouncementsSettings(timerMinutes = 5))
        val output = FakeAnnouncementsOutput()

        vm.startPauseTimer(output)

        assertEquals(listOf("countdown 300"), output.started)
        assertTrue(output.timerRunning.value)
    }

    @Test
    fun `a paused countdown resumes from where it stopped, not from the top`() {
        val vm = viewModel(AnnouncementsSettings(timerMinutes = 5))
        val output = FakeAnnouncementsOutput()
        output.timerRemainingSeconds.value = 42

        vm.startPauseTimer(output)

        assertEquals(listOf("countdown 42"), output.started)
    }

    @Test
    fun `a running countdown pauses, keeping what is left`() {
        val vm = viewModel(AnnouncementsSettings(timerMinutes = 5))
        val output = FakeAnnouncementsOutput()
        vm.startPauseTimer(output)
        output.timerRemainingSeconds.value = 120

        vm.startPauseTimer(output)

        assertFalse(output.timerRunning.value)
        assertEquals(120, output.timerRemainingSeconds.value)
    }

    @Test
    fun `a countdown of nothing does not start`() {
        val output = FakeAnnouncementsOutput()

        viewModel(AnnouncementsSettings()).startPauseTimer(output)

        assertTrue(output.started.isEmpty())
    }

    @Test
    fun `a count-up starts from what it had counted, and pauses again`() {
        val vm = viewModel(AnnouncementsSettings(timerMode = Constants.TIMER_MODE_COUNT_UP))
        val output = FakeAnnouncementsOutput()
        output.timerRemainingSeconds.value = 7

        vm.startPauseTimer(output)
        assertEquals(listOf("countUp 7"), output.started)

        vm.startPauseTimer(output)
        assertFalse(output.timerRunning.value)
    }

    @Test
    fun `a specific time counts down to its target, and stops again`() {
        val vm = viewModel(
            AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK, targetHour = 9, targetMinute = 30)
        )
        val output = FakeAnnouncementsOutput()

        vm.startPauseTimer(output)
        assertEquals(listOf("specificTime 9:30:0"), output.started)
        assertTrue(output.announcementTickerActive.value)

        vm.startPauseTimer(output)
        assertFalse(output.announcementTickerActive.value)
    }

    @Test
    fun `a clock shows the time in its format, and stops again`() {
        val vm = viewModel(
            AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK_DISPLAY, liveClockFormat = "HH:mm")
        )
        val output = FakeAnnouncementsOutput()

        vm.startPauseTimer(output)
        assertEquals(listOf("clock HH:mm"), output.started)

        vm.startPauseTimer(output)
        assertFalse(output.announcementTickerActive.value)
    }

    @Test
    fun `with no output there is nothing to start, pause or reset`() {
        val modes = listOf(Constants.TIMER_MODE_DURATION, Constants.TIMER_MODE_COUNT_UP, Constants.TIMER_MODE_CLOCK)
        modes.forEach { mode ->
            val vm = viewModel(AnnouncementsSettings(timerMinutes = 1, timerMode = mode))
            vm.startPauseTimer(null)
            vm.pauseTimer(null)
            vm.resetTimer(null)
            assertEquals(1, vm.timerMinutes, "the configured timer is left as it was in $mode")
        }
    }

    @Test
    fun `reset puts a countdown back to its full length`() {
        val vm = viewModel(AnnouncementsSettings(timerMinutes = 2))
        val output = FakeAnnouncementsOutput()
        output.timerRemainingSeconds.value = 30

        vm.resetTimer(output)

        assertEquals(120, output.timerRemainingSeconds.value)
    }

    @Test
    fun `reset puts a count-up back to zero`() {
        val vm = viewModel(AnnouncementsSettings(timerMode = Constants.TIMER_MODE_COUNT_UP))
        val output = FakeAnnouncementsOutput()
        output.timerRemainingSeconds.value = 30

        vm.resetTimer(output)

        assertEquals(0, output.timerRemainingSeconds.value)
    }

    @Test
    fun `reset leaves a specific time alone -- it always follows the wall clock`() {
        val vm = viewModel(AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK))
        val output = FakeAnnouncementsOutput()
        output.timerRemainingSeconds.value = 30
        output.announcementTickerActive.value = true

        vm.resetTimer(output)

        assertEquals(30, output.timerRemainingSeconds.value)
        assertTrue(output.announcementTickerActive.value)
    }

    @Test
    fun `pausing for text takes the timer off the live slot`() {
        val vm = viewModel(AnnouncementsSettings(timerMinutes = 1))
        val output = FakeAnnouncementsOutput()
        output.announcementTickerLive.value = true

        vm.pauseTimer(output)

        assertFalse(output.announcementTickerLive.value)
    }

    @Test
    fun `a typed hour reads as 24-hour, or as 12-hour in the half of the day shown`() {
        assertEquals(15, enteredHour24(15, use24Hour = true, isPm = false))
        assertEquals(9, enteredHour24(9, use24Hour = false, isPm = false))
        assertEquals(21, enteredHour24(9, use24Hour = false, isPm = true))
        assertEquals(0, enteredHour24(12, use24Hour = false, isPm = false), "12 AM is midnight")
        assertEquals(12, enteredHour24(12, use24Hour = false, isPm = true), "12 PM is noon")
        assertEquals(1, enteredHour24(0, use24Hour = false, isPm = false), "a 12-hour clock has no hour 0")
        assertEquals(12, enteredHour24(40, use24Hour = false, isPm = true), "nor anything past 12")
    }
}
