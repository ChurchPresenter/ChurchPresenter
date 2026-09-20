package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowTiming
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanDriftTest {

    private val clocks = mapOf("a" to RowClock(LocalTime.of(10, 0), exact = true))
    private val timing = mapOf("a" to RowTiming(runSeconds = 300))

    private fun drift(since: LocalTime, now: LocalTime, plan: Map<String, RowTiming> = timing) =
        planDrift("a", since, now, clocks, plan)

    @Test
    fun `a row started late is behind by that much for as long as it runs`() {
        val started = LocalTime.of(10, 2)
        assertEquals(120, drift(started, LocalTime.of(10, 2))?.seconds)
        assertEquals(120, drift(started, LocalTime.of(10, 6))?.seconds)
        assertTrue(drift(started, LocalTime.of(10, 6))!!.isBehind())
    }

    @Test
    fun `once the row overruns its length the drift grows with the clock`() {
        val started = LocalTime.of(10, 2)
        // Planned to end at 10:05; at 10:07 the row has been up two minutes past that.
        assertEquals(120, drift(started, LocalTime.of(10, 7))?.seconds)
        assertEquals(180, drift(started, LocalTime.of(10, 8))?.seconds)
    }

    @Test
    fun `the two reckonings agree at the boundary, so the number never jumps`() {
        val started = LocalTime.of(10, 2)
        val end = started.plusSeconds(300)
        assertEquals(drift(started, end.minusSeconds(1))?.seconds, drift(started, end)?.seconds)
    }

    @Test
    fun `a row started early is ahead until its planned end passes`() {
        val started = LocalTime.of(9, 58)
        val early = drift(started, LocalTime.of(10, 1))!!
        assertEquals(-120, early.seconds)
        assertTrue(early.isAhead())
        assertEquals(60, drift(started, LocalTime.of(10, 6))?.seconds)
    }

    @Test
    fun `a row of unknown length can only say how late it started`() {
        val started = LocalTime.of(10, 1)
        assertEquals(60, drift(started, LocalTime.of(11, 0), plan = emptyMap())?.seconds)
    }

    @Test
    fun `a row played twice runs twice as long before it overruns`() {
        val twice = mapOf("a" to RowTiming(runSeconds = 300, repeats = 2))
        assertEquals(0, drift(LocalTime.of(10, 0), LocalTime.of(10, 9), twice)?.seconds)
        assertEquals(60, drift(LocalTime.of(10, 0), LocalTime.of(10, 11), twice)?.seconds)
    }

    @Test
    fun `nothing to say when the plan has no time for the row`() {
        assertNull(planDrift("b", LocalTime.of(10, 0), LocalTime.of(10, 1), clocks, timing))
        assertNull(planDrift("a", LocalTime.of(10, 0), LocalTime.of(10, 1), emptyMap(), timing))
    }

    @Test
    fun `an estimated clock makes an estimated drift`() {
        val soft = mapOf("a" to RowClock(LocalTime.of(10, 0), exact = false))
        assertFalse(planDrift("a", LocalTime.of(10, 0), LocalTime.of(10, 1), soft, timing)!!.exact)
    }

    @Test
    fun `midnight is crossed the short way round`() {
        assertEquals(120, secondsBetween(LocalTime.of(23, 59), LocalTime.of(0, 1)))
        assertEquals(-120, secondsBetween(LocalTime.of(0, 1), LocalTime.of(23, 59)))
        assertEquals(3600, secondsBetween(LocalTime.of(9, 0), LocalTime.of(10, 0)))
    }
}
