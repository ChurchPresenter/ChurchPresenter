package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.calendar.CalendarUsage
import org.churchpresenter.converter.ui.BIBLE_CONVERSION
import org.churchpresenter.converter.ui.SongSources
import org.churchpresenter.songlibrary.SongLibraryUsage

internal fun calendarUsageEvent(usage: CalendarUsage): UsageEvent = when (usage) {
    CalendarUsage.SERVICE_ADDED -> UsageEvent.CALENDAR_SERVICE_ADDED
    CalendarUsage.SERVICE_COPIED -> UsageEvent.CALENDAR_SERVICE_COPIED
    CalendarUsage.TEMPLATE_SAVED -> UsageEvent.CALENDAR_TEMPLATE_SAVED
    CalendarUsage.MISSING_FILE_FIXED -> UsageEvent.CALENDAR_MISSING_FILE_FIXED
    CalendarUsage.EXPORTED -> UsageEvent.CALENDAR_EXPORTED
}

internal fun songLibraryUsageEvent(usage: SongLibraryUsage): UsageEvent = when (usage) {
    SongLibraryUsage.SONGS_SAVED -> UsageEvent.SONG_EDITED
    SongLibraryUsage.BULK_EDIT -> UsageEvent.SONG_LIBRARY_BULK_EDIT
    SongLibraryUsage.SONGBOOK_CREATED -> UsageEvent.SONG_LIBRARY_SONGBOOK_CREATED
    SongLibraryUsage.SONGS_DELETED -> UsageEvent.SONG_LIBRARY_SONGS_DELETED
}

internal fun converterEvent(sourceId: String): UsageEvent? = when (sourceId) {
    BIBLE_CONVERSION -> UsageEvent.CONVERTED_BIBLE
    SongSources.EASYSLIDES -> UsageEvent.CONVERTED_EASYSLIDES
    SongSources.EASYWORSHIP -> UsageEvent.CONVERTED_EASYWORSHIP
    SongSources.FREESHOW -> UsageEvent.CONVERTED_FREESHOW
    SongSources.FREEWORSHIP -> UsageEvent.CONVERTED_FREEWORSHIP
    SongSources.MEDIASHOUT -> UsageEvent.CONVERTED_MEDIASHOUT
    SongSources.OPENLP -> UsageEvent.CONVERTED_OPENLP
    SongSources.OPENSONG -> UsageEvent.CONVERTED_OPENSONG
    SongSources.PROPRESENTER -> UsageEvent.CONVERTED_PROPRESENTER
    SongSources.QUELEA -> UsageEvent.CONVERTED_QUELEA
    SongSources.SOFTPROJECTOR -> UsageEvent.CONVERTED_SOFTPROJECTOR
    SongSources.SONGBEAMER -> UsageEvent.CONVERTED_SONGBEAMER
    SongSources.VIDEOPSALM -> UsageEvent.CONVERTED_VIDEOPSALM
    SongSources.DOCUMENTS -> UsageEvent.CONVERTED_DOCUMENTS
    else -> null
}
