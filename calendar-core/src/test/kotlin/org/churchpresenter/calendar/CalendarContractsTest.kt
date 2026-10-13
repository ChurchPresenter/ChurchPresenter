package org.churchpresenter.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The small types the Schedule tab and the picker read off the calendar. */
class CalendarContractsTest {

    @Test
    fun `the Schedule's link names the service it holds and whether it has changed`() {
        val link = ScheduleServiceLink(serviceId = "svc", serviceName = "Sunday", hasChanges = false)

        assertEquals("svc", link.serviceId)
        assertEquals("Sunday", link.serviceName)
        assertFalse(link.hasChanges)
    }

    @Test
    fun `a Bible book is short-named after its own name unless the host says otherwise`() {
        val book = CalendarBibleBook(bookId = 23, name = "Книга пророка Исаии", verseCounts = listOf(31, 22))

        assertEquals("Книга пророка Исаии", book.shortName)
        assertEquals(2, book.verseCounts.size, "one entry per chapter")
        assertEquals("Исаия", book.copy(shortName = "Исаия").shortName)
    }
}
