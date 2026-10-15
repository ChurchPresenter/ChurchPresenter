package org.churchpresenter.announcements

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals

/** The announcements port's one default: a pause that is not told where the timer stood. */
class AnnouncementsOutputTest {

    private class Recording : AnnouncementsOutput {
        val pauses = mutableListOf<Int?>()
        override val announcementsLive = false
        override val screenLocks: State<Map<Int, Presenting>> = mutableStateOf(emptyMap())
        override val timerRemainingSeconds: State<Int> = mutableStateOf(0)
        override val timerRunning: State<Boolean> = mutableStateOf(false)
        override val announcementTickerActive: State<Boolean> = mutableStateOf(false)
        override val announcementTimerExpired: State<Boolean> = mutableStateOf(false)
        override fun setPresentingMode(mode: Presenting) = Unit
        override fun requestClearDisplay() = Unit
        override fun setScreenLock(screenIndex: Int, mode: Presenting?) = Unit
        override fun setAnnouncementText(text: String) = Unit
        override fun setAnnouncementTickerLive(live: Boolean) = Unit
        override fun startAnnouncementCountdown(remainingSeconds: Int, expiredText: String) = Unit
        override fun startAnnouncementCountUp(initialElapsedSeconds: Int) = Unit
        override fun startAnnouncementSpecificTime(targetHour: Int, targetMinute: Int, targetSecond: Int) = Unit
        override fun startAnnouncementClockDisplay(formatPattern: String) = Unit
        override fun pauseAnnouncementTimer(remainingSeconds: Int?) {
            pauses += remainingSeconds
        }
    }

    @Test
    fun `a pause with no time given leaves the timer where the output has it`() {
        val output = Recording()

        output.pauseAnnouncementTimer()
        output.pauseAnnouncementTimer(42)

        assertEquals(listOf(null, 42), output.pauses)
    }
}
