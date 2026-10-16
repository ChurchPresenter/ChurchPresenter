package org.churchpresenter.core.models.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

/** How a timer's seconds read on the Announcements tab and on the output. */
class TimerTextTest {

    @Test
    fun `a timer under an hour is shown as minutes and seconds`() {
        assertEquals("00:00", formatTimer(0))
        assertEquals("00:09", formatTimer(9))
        assertEquals("00:59", formatTimer(59))
        assertEquals("01:00", formatTimer(60))
        assertEquals("01:05", formatTimer(65))
        assertEquals("05:30", formatTimer(330))
        assertEquals("59:59", formatTimer(3599))
    }

    @Test
    fun `an hour or more gains an hours field, unpadded`() {
        assertEquals("1:00:00", formatTimer(3600))
        assertEquals("1:01:01", formatTimer(3661))
        assertEquals("10:00:00", formatTimer(36_000))
        assertEquals("23:59:59", formatTimer(86_399))
    }
}
