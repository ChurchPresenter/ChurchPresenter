package org.churchpresenter.calendar.model

import java.time.DayOfWeek
import java.time.Month
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The month grid and its names, as the window asks for them: in the machine's own locale. */
class CalendarTimeDefaultsTest {

    private val locale = Locale.getDefault()

    @Test
    fun `the grid starts on the locale's own first day and shows six weeks`() {
        val grid = monthGrid(YearMonth.of(2026, 9))

        assertEquals(firstDayOfWeek(locale), grid.first().dayOfWeek)
        assertEquals(42, grid.size)
        assertEquals(monthGrid(YearMonth.of(2026, 9), firstDayOfWeek(locale)), grid)
    }

    @Test
    fun `the weekday header begins where the grid does`() {
        val order = weekdayOrder()

        assertEquals(firstDayOfWeek(locale), order.first())
        assertEquals(7, order.toSet().size)
    }

    @Test
    fun `month and weekday names default to the machine's locale`() {
        assertEquals(monthName(Month.MARCH, locale), monthName(Month.MARCH))
        assertEquals(weekdayName(DayOfWeek.SUNDAY, locale), weekdayName(DayOfWeek.SUNDAY))
        assertEquals(monthHeading(YearMonth.of(2026, 3), locale), monthHeading(YearMonth.of(2026, 3)))
    }
}
