package org.churchpresenter.settings

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarSyncRotationTest {

    private val now = Instant.parse("2026-09-24T12:00:00Z")

    @Test
    fun `an instance that has never been rotated may be`() {
        assertNull(CalendarSyncSettings().nextRotationAt(now))
    }

    @Test
    fun `within a week of the last rotation the next one waits for the week to end`() {
        val settings = CalendarSyncSettings(rotatedAt = "2026-09-21T12:00:00Z")

        assertEquals(Instant.parse("2026-09-28T12:00:00Z"), settings.nextRotationAt(now))
    }

    @Test
    fun `a week after the last rotation the next one may happen`() {
        assertNull(CalendarSyncSettings(rotatedAt = "2026-09-17T12:00:00Z").nextRotationAt(now))
    }

    @Test
    fun `a stamp that does not parse limits nothing`() {
        assertNull(CalendarSyncSettings(rotatedAt = "yesterday").nextRotationAt(now))
    }
}
