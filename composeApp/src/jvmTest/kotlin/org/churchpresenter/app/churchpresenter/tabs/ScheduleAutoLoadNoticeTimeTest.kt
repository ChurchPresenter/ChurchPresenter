package org.churchpresenter.app.churchpresenter.tabs

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** [minutesUntil]: the time left the Schedule tab's auto-load notice shows. */
class ScheduleAutoLoadNoticeTimeTest {
    private val loadAt = LocalDateTime.of(2026, 9, 20, 9, 55)

    @Test
    fun `whole minutes are exact`() {
        assertEquals(80, minutesUntil(loadAt, loadAt.minusMinutes(80)))
    }

    @Test
    fun `part of a minute rounds up, so the last seconds never read zero`() {
        assertEquals(1, minutesUntil(loadAt, loadAt.minusSeconds(30)))
        assertEquals(2, minutesUntil(loadAt, loadAt.minusSeconds(61)))
    }

    @Test
    fun `nothing left once the moment has come or passed`() {
        assertEquals(0, minutesUntil(loadAt, loadAt))
        assertEquals(0, minutesUntil(loadAt, loadAt.plusMinutes(3)))
    }
}
