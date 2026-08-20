package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [UpdateCheckInterval.isDueSince] gates the silent background update check. NEVER must be
 * absolute -- a user who turned checks off should never see network traffic.
 */
class UpdateCheckIntervalTest {

    private fun daysAgo(days: Int) = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000

    @Test
    fun `EVERY_LAUNCH is always due`() {
        assertTrue(UpdateCheckInterval.EVERY_LAUNCH.isDueSince(System.currentTimeMillis()))
        assertTrue(UpdateCheckInterval.EVERY_LAUNCH.isDueSince(0L))
    }

    @Test
    fun `NEVER is never due, no matter how long it has been`() {
        assertFalse(UpdateCheckInterval.NEVER.isDueSince(0L), "'never' must mean never, even after decades")
        assertFalse(UpdateCheckInterval.NEVER.isDueSince(daysAgo(10_000)))
    }

    @Test
    fun `a periodic interval is due only once its window has elapsed`() {
        assertFalse(UpdateCheckInterval.WEEKLY.isDueSince(daysAgo(6)))
        assertTrue(UpdateCheckInterval.WEEKLY.isDueSince(daysAgo(8)))

        assertFalse(UpdateCheckInterval.MONTHLY.isDueSince(daysAgo(29)))
        assertTrue(UpdateCheckInterval.MONTHLY.isDueSince(daysAgo(31)))
    }

    @Test
    fun `longer intervals wait longer`() {
        val sixWeeksAgo = daysAgo(42)
        assertTrue(UpdateCheckInterval.MONTHLY.isDueSince(sixWeeksAgo))
        assertFalse(UpdateCheckInterval.EVERY_2_MONTHS.isDueSince(sixWeeksAgo))
        assertFalse(UpdateCheckInterval.EVERY_6_MONTHS.isDueSince(sixWeeksAgo))
    }

    @Test
    fun `a never-checked install reads as due for every periodic interval`() {
        // lastCheckedAtMillis defaults to 0L in AppSettings, i.e. the epoch.
        for (interval in UpdateCheckInterval.entries.filter { it != UpdateCheckInterval.NEVER }) {
            assertTrue(interval.isDueSince(0L), "$interval should be due on a fresh install")
        }
    }
}
