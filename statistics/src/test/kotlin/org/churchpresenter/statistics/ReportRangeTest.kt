package org.churchpresenter.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportRangeTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 20)

    @Test
    fun `typing a To date drops the quick period and ends the range at the end of that day`() {
        val range = ReportRange(today, zone)
        range.apply(StatisticsPeriod.LastMonths(3), today, earliestEvent = null)

        range.setTo(2026, 2, 31)

        assertNull(range.activePeriod)
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(range.toMs()), zone)
        assertEquals(LocalDate.of(2026, 2, 28), end.toLocalDate(), "a day past the month's end is its last day")
        assertEquals(23, end.hour)
    }
}
