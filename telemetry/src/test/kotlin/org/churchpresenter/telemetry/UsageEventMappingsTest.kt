package org.churchpresenter.telemetry

import org.churchpresenter.calendar.CalendarUsage
import org.churchpresenter.converter.ui.BIBLE_CONVERSION
import org.churchpresenter.converter.ui.SongSources
import org.churchpresenter.songlibrary.SongLibraryUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.churchpresenter.sharedui.utils.UsageEvent

/** The modules report usage in their own vocabulary; these are the app's names for it. */
class UsageEventMappingsTest {

    @Test
    fun `every calendar action is counted as its own event`() {
        val events = CalendarUsage.entries.map(::calendarUsageEvent)

        assertEquals(CalendarUsage.entries.size, events.toSet().size, "two calendar actions share $events")
        assertEquals(UsageEvent.CALENDAR_EXPORTED, calendarUsageEvent(CalendarUsage.EXPORTED))
    }

    @Test
    fun `every song library action is counted as its own event`() {
        val events = SongLibraryUsage.entries.map(::songLibraryUsageEvent)

        assertEquals(SongLibraryUsage.entries.size, events.toSet().size, "two library actions share $events")
        // Saving in the library is the same act as saving in the editor, so it is counted as one.
        assertEquals(UsageEvent.SONG_EDITED, songLibraryUsageEvent(SongLibraryUsage.SONGS_SAVED))
    }

    @Test
    fun `every converter source has an event of its own, so a new source cannot go uncounted`() {
        val events = SongSources.all.map { source ->
            assertNotNull(converterEvent(source.id), "the ${source.name} converter is counted as nothing")
        }

        assertEquals(SongSources.all.size, events.toSet().size, "two converter sources share an event")
    }

    @Test
    fun `a Bible conversion is counted, and an id nobody sends is not`() {
        assertEquals(UsageEvent.CONVERTED_BIBLE, converterEvent(BIBLE_CONVERSION))
        assertNull(converterEvent("no-such-source"))
    }
}
