package org.churchpresenter.liveoutput

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a countdown and a clock prop say, as text. */
class PropsTextTest {

    @Test
    fun `a countdown shows what is left, then nothing below zero`() {
        val now = LocalTime.of(9, 30, 0)
        assertEquals("15:00", countdownText(now, "09:45"))
        assertEquals("1:05:30", countdownText(LocalTime.of(9, 0, 0), "10:05:30"))
        assertEquals("0:00", countdownText(now, "09:00"))
        assertEquals("0:00", countdownText(now, "soon"))
    }

    @Test
    fun `the clock follows its pattern, and a broken pattern falls back to hours and minutes`() {
        val now = LocalTime.of(14, 5, 9)
        assertEquals("14:05:09", clockText(now, "HH:mm:ss"))
        assertEquals("14:05", clockText(now, "HH:mm:ss'"))
    }
}
