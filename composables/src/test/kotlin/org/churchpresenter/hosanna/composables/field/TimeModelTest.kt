package org.churchpresenter.hosanna.composables.field

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeModelTest {

    private val labels = TimeFieldLabels("Hour", "Minute", "AM/PM", "AM", "PM")
    private fun at(hour: Int, minute: Int) = hour * 60 + minute

    @Test
    fun `hours read as 12-hour or 24-hour`() {
        assertEquals("12", hourText(at(0, 5), use24Hour = false))
        assertEquals("8", hourText(at(8, 0), use24Hour = false))
        assertEquals("12", hourText(at(12, 0), use24Hour = false))
        assertEquals("9", hourText(at(21, 30), use24Hour = false))
        assertEquals("00", hourText(at(0, 5), use24Hour = true))
        assertEquals("21", hourText(at(21, 30), use24Hour = true))
        assertEquals("05", minuteText(at(7, 5)))
        assertFalse(isPm(at(11, 59)))
        assertTrue(isPm(at(12, 0)))
    }

    @Test
    fun `a 24-hour field has no period part`() {
        assertEquals(listOf(TimePart.Hour, TimePart.Minute), timeParts(use24Hour = true))
        assertEquals(TimePart.entries, timeParts(use24Hour = false))
    }

    @Test
    fun `stepping wraps around the day, minutes by five within the hour`() {
        assertEquals(at(0, 30), stepTime(at(23, 30), TimePart.Hour, up = true))
        assertEquals(at(23, 30), stepTime(at(0, 30), TimePart.Hour, up = false))
        assertEquals(at(8, 5), stepTime(at(8, 0), TimePart.Minute, up = true))
        assertEquals(at(8, 10), stepTime(at(8, 7), TimePart.Minute, up = true))
        assertEquals(at(8, 5), stepTime(at(8, 7), TimePart.Minute, up = false))
        assertEquals(at(8, 0), stepTime(at(8, 55), TimePart.Minute, up = true))
        assertEquals(at(8, 55), stepTime(at(8, 0), TimePart.Minute, up = false))
        assertEquals(at(20, 0), stepTime(at(8, 0), TimePart.Period, up = true))
        assertEquals(at(8, 0), stepTime(at(20, 0), TimePart.Period, up = false))
        assertEquals(at(0, 0), wrapDay(at(24, 0)))
        assertEquals(at(23, 0), wrapDay(-60))
    }

    @Test
    fun `A and P set the period`() {
        assertEquals(at(20, 0), setPeriod(at(8, 0), pm = true))
        assertEquals(at(20, 0), setPeriod(at(20, 0), pm = true))
        assertEquals(at(8, 0), setPeriod(at(20, 0), pm = false))
        assertEquals(at(8, 0), setPeriod(at(8, 0), pm = false))
    }

    @Test
    fun `typing a 12-hour hour`() {
        assertEquals(TypedTime(at(9, 15), null, true), typeDigit(at(3, 15), TimePart.Hour, null, 9, use24Hour = false))
        assertEquals(TypedTime(at(1, 15), 1, false), typeDigit(at(3, 15), TimePart.Hour, null, 1, use24Hour = false))
        assertEquals(TypedTime(at(0, 15), null, true), typeDigit(at(1, 15), TimePart.Hour, 1, 2, use24Hour = false))
        assertEquals(TypedTime(at(5, 15), null, true), typeDigit(at(1, 15), TimePart.Hour, 1, 5, use24Hour = false))
        assertEquals(TypedTime(at(12, 0), 0, false), typeDigit(at(20, 0), TimePart.Hour, null, 0, use24Hour = false))
        assertEquals(TypedTime(at(21, 0), null, true), typeDigit(at(20, 0), TimePart.Hour, null, 9, use24Hour = false))
    }

    @Test
    fun `typing a 24-hour hour`() {
        assertEquals(TypedTime(at(2, 0), 2, false), typeDigit(at(8, 0), TimePart.Hour, null, 2, use24Hour = true))
        assertEquals(TypedTime(at(21, 0), null, true), typeDigit(at(2, 0), TimePart.Hour, 2, 1, use24Hour = true))
        assertEquals(TypedTime(at(5, 0), null, true), typeDigit(at(2, 0), TimePart.Hour, 2, 5, use24Hour = true))
        assertEquals(TypedTime(at(7, 0), null, true), typeDigit(at(8, 0), TimePart.Hour, null, 7, use24Hour = true))
    }

    @Test
    fun `typing minutes, and digits on the period do nothing`() {
        assertEquals(TypedTime(at(9, 3), 3, false), typeDigit(at(9, 0), TimePart.Minute, null, 3, use24Hour = false))
        assertEquals(TypedTime(at(9, 30), null, true), typeDigit(at(9, 3), TimePart.Minute, 3, 0, use24Hour = false))
        assertEquals(TypedTime(at(9, 7), null, true), typeDigit(at(9, 0), TimePart.Minute, null, 7, use24Hour = false))
        assertEquals(TypedTime(at(9, 0), null, false), typeDigit(at(9, 0), TimePart.Period, null, 4, use24Hour = false))
    }

    @Test
    fun `moving between parts stops at the ends`() {
        val parts = timeParts(use24Hour = false)
        assertEquals(TimePart.Minute, nextPart(parts, TimePart.Hour, 1))
        assertEquals(TimePart.Period, nextPart(parts, TimePart.Period, 1))
        assertEquals(TimePart.Hour, nextPart(parts, TimePart.Hour, -1))
    }

    @Test
    fun `the spinner shows the values around the current one`() {
        val hour = spinValues(at(8, 0), TimePart.Hour, use24Hour = false, labels)
        assertEquals(listOf("7", "8", "9"), listOf(hour.previous, hour.current, hour.next))
        val minute = spinValues(at(8, 0), TimePart.Minute, use24Hour = false, labels)
        assertEquals(listOf("55", "00", "05"), listOf(minute.previous, minute.current, minute.next))
        val am = spinValues(at(8, 0), TimePart.Period, use24Hour = false, labels)
        assertEquals(listOf("", "AM", "PM"), listOf(am.previous, am.current, am.next))
        val pm = spinValues(at(20, 0), TimePart.Period, use24Hour = false, labels)
        assertEquals(listOf("AM", "PM", ""), listOf(pm.previous, pm.current, pm.next))
    }
}
