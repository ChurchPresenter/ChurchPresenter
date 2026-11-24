package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimerModelTest {

    private fun t(h: Int, m: Int, s: Int) = h * 3600 + m * 60 + s

    @Test
    fun `each unit reads its own two digits`() {
        val total = t(1, 2, 5)
        assertEquals(1, unitValue(total, TimerUnit.Hours))
        assertEquals(2, unitValue(total, TimerUnit.Minutes))
        assertEquals(5, unitValue(total, TimerUnit.Seconds))
        assertEquals("01", unitText(total, TimerUnit.Hours))
        assertEquals("05", unitText(total, TimerUnit.Seconds))
        assertEquals("99", unitText(MAX_TIMER_SECONDS, TimerUnit.Hours))
    }

    @Test
    fun `stepping carries and borrows across units`() {
        assertEquals(t(0, 1, 0), stepTimer(t(0, 0, 59), TimerUnit.Seconds, up = true))
        assertEquals(t(0, 0, 59), stepTimer(t(0, 1, 0), TimerUnit.Seconds, up = false))
        assertEquals(t(1, 0, 30), stepTimer(t(0, 59, 30), TimerUnit.Minutes, up = true))
        assertEquals(t(2, 0, 0), stepTimer(t(1, 0, 0), TimerUnit.Hours, up = true))
    }

    @Test
    fun `held seconds jump by five, other units do not`() {
        assertEquals(t(0, 0, 15), stepTimer(t(0, 0, 10), TimerUnit.Seconds, up = true, fast = true))
        assertEquals(t(0, 11, 0), stepTimer(t(0, 10, 0), TimerUnit.Minutes, up = true, fast = true))
    }

    @Test
    fun `the timer stops at zero and at 99 59 59`() {
        assertFalse(canStep(0, TimerUnit.Seconds, up = false))
        assertTrue(canStep(t(0, 5, 0), TimerUnit.Seconds, up = false))
        assertFalse(canStep(t(0, 5, 0), TimerUnit.Hours, up = false))
        assertFalse(canStep(MAX_TIMER_SECONDS, TimerUnit.Seconds, up = true))
        assertTrue(canStep(MAX_TIMER_SECONDS - 1, TimerUnit.Seconds, up = true))
        assertEquals(0, stepTimer(t(0, 0, 3), TimerUnit.Seconds, up = false, fast = true).coerceAtLeast(0))
        assertEquals(MAX_TIMER_SECONDS, stepTimer(MAX_TIMER_SECONDS, TimerUnit.Hours, up = true))
    }

    @Test
    fun `typing a value replaces one unit and caps it`() {
        assertEquals(t(1, 45, 5), setUnit(t(1, 2, 5), TimerUnit.Minutes, 45))
        assertEquals(t(1, 59, 5), setUnit(t(1, 2, 5), TimerUnit.Minutes, 75))
        assertEquals(t(1, 2, 59), setUnit(t(1, 2, 5), TimerUnit.Seconds, 99))
        assertEquals(t(99, 2, 5), setUnit(t(1, 2, 5), TimerUnit.Hours, 99))
    }

    @Test
    fun `typed text keeps at most two digits`() {
        assertEquals(45, parseUnit("45"))
        assertEquals(12, parseUnit("1a2b3"))
        assertNull(parseUnit(""))
        assertNull(parseUnit("ab"))
    }
}
