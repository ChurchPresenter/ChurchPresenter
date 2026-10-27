package org.churchpresenter.signage.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTest {
    private val zone = ZoneId.of("America/Chicago")

    // 2026-10-18 is a Sunday.
    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text)
    private fun instant(text: String): Instant = at(text).atZone(zone).toInstant()

    private fun block(id: String, days: Set<Day>, start: String, end: String, action: BlockAction = BlockAction.TvOff) =
        ScheduleBlock(id, days, ClockTime.parse(start), ClockTime.parse(end), action)

    @Test
    fun aDaytimeBlockCoversFromItsStartUpToItsEnd() {
        val sunday = block("s", setOf(Day.SUN), "09:30", "12:30")
        assertFalse(sunday.covers(at("2026-10-18T09:29")))
        assertTrue(sunday.covers(at("2026-10-18T09:30")))
        assertTrue(sunday.covers(at("2026-10-18T12:29")))
        assertFalse(sunday.covers(at("2026-10-18T12:30")))
        assertFalse(sunday.covers(at("2026-10-19T10:00")))
    }

    @Test
    fun anOvernightBlockBelongsToTheDayItStarts() {
        val fridayNight = block("n", setOf(Day.FRI), "22:00", "06:00")
        assertTrue(fridayNight.crossesMidnight)
        assertTrue(fridayNight.covers(at("2026-10-16T23:00")))
        assertTrue(fridayNight.covers(at("2026-10-17T05:59")))
        assertFalse(fridayNight.covers(at("2026-10-17T06:00")))
        assertFalse(fridayNight.covers(at("2026-10-17T23:00")))
        assertFalse(fridayNight.covers(at("2026-10-16T05:00")))
    }

    @Test
    fun equalStartAndEndIsTheWholeDay() {
        val allSunday = block("a", setOf(Day.SUN), "00:00", "00:00")
        assertTrue(allSunday.covers(at("2026-10-18T00:00")))
        assertTrue(allSunday.covers(at("2026-10-18T23:59")))
        assertFalse(allSunday.covers(at("2026-10-19T00:00")))
        assertEquals(ClockTime.MINUTES_PER_DAY, allSunday.minutesOfWeek().size)
    }

    @Test
    fun theFirstListedBlockRunningNowIsActive() {
        val schedule = listOf(
            block("a", setOf(Day.SUN), "09:00", "10:00"),
            block("b", setOf(Day.SUN), "09:30", "11:00"),
        )
        assertEquals("a", schedule.activeAt(instant("2026-10-18T09:45"), zone)?.id)
        assertEquals("b", schedule.activeAt(instant("2026-10-18T10:15"), zone)?.id)
        assertNull(schedule.activeAt(instant("2026-10-18T11:00"), zone))
    }

    @Test
    fun theNextChangeIsTheNearestStartOrEnd() {
        val schedule = listOf(
            block("sun", setOf(Day.SUN), "09:30", "12:30"),
            block("wed", setOf(Day.WED), "17:00", "20:00"),
        )
        assertEquals(instant("2026-10-18T09:30"), schedule.nextChangeAfter(instant("2026-10-18T08:00"), zone))
        assertEquals(instant("2026-10-18T12:30"), schedule.nextChangeAfter(instant("2026-10-18T09:30"), zone))
        assertEquals(instant("2026-10-21T17:00"), schedule.nextChangeAfter(instant("2026-10-18T12:30"), zone))
        assertEquals(instant("2026-10-25T09:30"), schedule.nextChangeAfter(instant("2026-10-21T20:00"), zone))
        assertNull(emptyList<ScheduleBlock>().nextChangeAfter(instant("2026-10-18T08:00"), zone))
    }

    @Test
    fun anOvernightBlocksEndIsFoundOnTheNextDay() {
        val schedule = listOf(block("n", setOf(Day.SAT), "22:00", "06:00"))
        assertEquals(instant("2026-10-18T06:00"), schedule.nextChangeAfter(instant("2026-10-18T01:00"), zone))
    }

    @Test
    fun aTimeInTheSpringForwardGapMovesForward() {
        // 2027-03-14 02:00 does not exist in Chicago; the clock jumps to 03:00.
        val schedule = listOf(block("x", setOf(Day.SUN), "02:30", "04:00"))
        val change = schedule.nextChangeAfter(instant("2027-03-14T01:00"), zone)
        assertEquals(LocalDateTime.parse("2027-03-14T03:30").atZone(zone).toInstant(), change)
    }

    @Test
    fun overlapsAreFoundAcrossMidnightAndTheWeekEnd() {
        val night = block("night", setOf(Day.SUN), "22:00", "06:00")
        val earlyMonday = block("mon", setOf(Day.MON), "05:00", "07:00")
        val sundayMorning = block("sun", setOf(Day.SUN), "09:00", "10:00")
        val touching = block("touch", setOf(Day.MON), "06:00", "07:00")
        assertEquals(listOf("night" to "mon"), listOf(night, earlyMonday, sundayMorning).overlaps())
        assertEquals(emptyList(), listOf(night, touching, sundayMorning).overlaps())
    }
}
