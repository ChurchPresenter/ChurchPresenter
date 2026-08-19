package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

/**
 * The gate on the silent startup check. It is stored in `settings.json` by name, so the entries
 * are part of the file format; what each one means is this.
 */
class UpdateCheckIntervalTest {

    @Test
    fun `every launch is always due, even a moment after the last check`() {
        assertTrue(UpdateCheckInterval.EVERY_LAUNCH.isDueSince(System.currentTimeMillis()))
    }

    @Test
    fun `never is never due, however long ago the last check was`() {
        assertFalse(UpdateCheckInterval.NEVER.isDueSince(0L))
    }

    @Test
    fun `a fixed interval is due only once its own span has elapsed`() {
        val now = System.currentTimeMillis()

        assertFalse(UpdateCheckInterval.WEEKLY.isDueSince(now - 6 * ONE_DAY_MS))
        assertTrue(UpdateCheckInterval.WEEKLY.isDueSince(now - 8 * ONE_DAY_MS))

        assertFalse(UpdateCheckInterval.MONTHLY.isDueSince(now - 29 * ONE_DAY_MS))
        assertTrue(UpdateCheckInterval.MONTHLY.isDueSince(now - 31 * ONE_DAY_MS))
    }

    @Test
    fun `the longer intervals are ordered as their names say`() {
        val now = System.currentTimeMillis()
        val twoMonthsAgo = now - 61 * ONE_DAY_MS

        assertTrue(UpdateCheckInterval.EVERY_2_MONTHS.isDueSince(twoMonthsAgo))
        assertFalse(UpdateCheckInterval.EVERY_3_MONTHS.isDueSince(twoMonthsAgo))
        assertFalse(UpdateCheckInterval.EVERY_6_MONTHS.isDueSince(twoMonthsAgo))
    }

    @Test
    fun `a never-checked install is due on every interval that checks at all`() {
        UpdateCheckInterval.entries.filter { it != UpdateCheckInterval.NEVER }.forEach {
            assertTrue(it.isDueSince(0L), "$it should be due when nothing was ever checked")
        }
    }
}
