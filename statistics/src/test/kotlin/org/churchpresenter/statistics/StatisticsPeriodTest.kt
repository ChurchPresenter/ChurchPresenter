package org.churchpresenter.statistics

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.core.models.statistics.StatisticsPeriod

/**
 * The reporting periods offered by the statistics window, resolved against a fixed `today` so the
 * assertions never depend on the clock.
 */
class StatisticsPeriodTest {

    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun `a finished calendar year spans that year and nothing of its neighbours`() {
        val range = StatisticsPeriod.Year(2025).resolve(today, earliestMs = null)

        assertEquals(LocalDate.of(2025, 1, 1).startOfDayMs(), range.fromMs, "starts at midnight on January 1")
        assertEquals(LocalDate.of(2025, 12, 31).endOfDayMs(), range.toMs, "ends at 23:59:59 on December 31")
        assertTrue(LocalDate.of(2024, 12, 31).endOfDayMs() < range.fromMs, "the last moment of 2024 is outside")
        assertTrue(LocalDate.of(2026, 1, 1).startOfDayMs() > range.toMs, "the first moment of 2026 is outside")
    }

    @Test
    fun `the current year stops at today rather than running into a December that has not happened`() {
        val range = StatisticsPeriod.Year(today.year).resolve(today, earliestMs = null)

        assertEquals(LocalDate.of(today.year, 1, 1).startOfDayMs(), range.fromMs)
        assertEquals(today.endOfDayMs(), range.toMs, "the range ends tonight, not on December 31")
    }

    @Test
    fun `a rolling window counts back whole months from today and ends tonight`() {
        for (months in ROLLING_MONTHS) {
            val range = StatisticsPeriod.LastMonths(months).resolve(today, earliestMs = null)

            assertEquals(
                today.minusMonths(months.toLong()).startOfDayMs(),
                range.fromMs,
                "the $months-month window starts that many months back",
            )
            assertEquals(today.endOfDayMs(), range.toMs, "and runs to the end of today")
        }
    }

    @Test
    fun `all time starts at the oldest event when there is one`() {
        val earliest = LocalDate.of(2024, 7, 4).startOfDayMs()

        val range = StatisticsPeriod.AllTime.resolve(today, earliestMs = earliest)

        assertEquals(earliest, range.fromMs)
        assertEquals(today.endOfDayMs(), range.toMs)
    }

    @Test
    fun `all time with an empty log falls back to the current year`() {
        val range = StatisticsPeriod.AllTime.resolve(today, earliestMs = null)

        // There is nothing recorded to be excluded, and the From picker has to show *some* date —
        // January 1 is the one the report's year list starts at.
        assertEquals(LocalDate.of(today.year, 1, 1).startOfDayMs(), range.fromMs)
        assertEquals(today.endOfDayMs(), range.toMs)
    }

    @Test
    fun `all time never opens after the oldest event it has to include`() {
        val earliest = LocalDate.of(2024, 7, 4).startOfDayMs()

        val range = StatisticsPeriod.AllTime.resolve(today, earliestMs = earliest)

        assertTrue(range.fromMs <= earliest, "the oldest event must fall inside all time")
    }

    @Test
    fun `the offered years run from the oldest event to this one, newest first`() {
        val earliest = LocalDate.of(2024, 7, 4).startOfDayMs()

        assertEquals(listOf(2026, 2025, 2024), availableYears(today, earliest))
    }

    @Test
    fun `an empty log offers only the current year`() {
        assertEquals(listOf(2026), availableYears(today, earliestMs = null))
    }

    @Test
    fun `an event stamped in the future does not invert the year list`() {
        val future = LocalDate.of(2030, 1, 1).startOfDayMs()

        assertEquals(listOf(2026), availableYears(today, future), "the list never runs backwards")
    }
}
