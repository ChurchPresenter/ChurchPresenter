package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDate

/**
 * What this desktop tells the relay: each service with its rows reduced to what a phone needs
 * to show and reorder them. The projection is one-way by design — see [RemoteRow].
 */
object Projection {

    /** The services the relay keeps: from [WireLimits.RETENTION_DAYS] ago onward, newest first, capped. */
    fun services(document: CalendarDocument, today: LocalDate): List<RemoteService> {
        val earliest = today.minusDays(WireLimits.RETENTION_DAYS).toString()
        return document.services
            .filter { it.date >= earliest }
            .sortedWith(compareByDescending<PlannedService> { it.date }.thenBy { it.startTime })
            .take(WireLimits.SERVICES_PER_PUSH)
            .map(::service)
    }

    fun tombstones(document: CalendarDocument): List<RemoteTombstone> =
        document.deletedServices.map { (id, at) -> RemoteTombstone(id, at) }

    /** A preset by name and kind only; the item it holds never leaves this machine. */
    fun presets(presets: List<ItemPreset>): List<RemotePreset> =
        presets.map { RemotePreset(id = it.id, name = it.name, kind = kindOf(it.item)) }

    fun service(service: PlannedService): RemoteService = RemoteService(
        id = service.id,
        date = service.date,
        startTime = service.startTime,
        name = service.name,
        kind = service.kind,
        armed = service.armed,
        seriesId = service.seriesId,
        rows = service.items.take(WireLimits.ROWS_PER_SERVICE).map(::row),
        plannedSeconds = service.plannedSeconds.filterKeys { id -> service.items.any { it.id == id } },
        timing = service.timing.filterKeys { id -> service.items.any { it.id == id } },
        updatedAt = service.updatedAt,
        updatedBy = DESKTOP,
    )

    /**
     * A row as a phone may see it. The kinds a phone can author round-trip with their content;
     * everything else is a [RemoteRow.Ref] with a kind and a title.
     */
    fun row(item: ScheduleItem): RemoteRow = when (item) {
        is ScheduleItem.LabelItem -> RemoteRow.Section(item.id, item.text, item.backgroundColor)
        is ScheduleItem.SongItem -> RemoteRow.Song(
            id = item.id,
            title = item.title,
            songId = item.songId,
            songbook = item.songbook,
            number = if (item.songNumber > 0) item.songNumber.toString() else "",
        )
        is ScheduleItem.BibleVerseItem -> RemoteRow.Bible(item.id, item.displayText)
        is ScheduleItem.MinistryItem -> RemoteRow.Ministry(item.id, item.title, item.detail)
        else -> RemoteRow.Ref(id = item.id, title = item.displayText, kind = kindOf(item))
    }

    fun kindOf(item: ScheduleItem): String = when (item) {
        is ScheduleItem.LabelItem -> RemoteKind.SECTION
        is ScheduleItem.SongItem -> RemoteKind.SONG
        is ScheduleItem.BibleVerseItem -> RemoteKind.BIBLE
        is ScheduleItem.MinistryItem -> RemoteKind.MINISTRY
        is ScheduleItem.PictureItem -> RemoteKind.PICTURES
        is ScheduleItem.PresentationItem -> RemoteKind.PRESENTATION
        is ScheduleItem.MediaItem -> RemoteKind.MEDIA
        is ScheduleItem.LowerThirdItem -> RemoteKind.LOWER_THIRD
        is ScheduleItem.AnnouncementItem -> if (item.isTimer) RemoteKind.TIMER else RemoteKind.ANNOUNCEMENT
        is ScheduleItem.WebsiteItem -> RemoteKind.WEBSITE
        is ScheduleItem.SceneItem -> RemoteKind.SCENE
        is ScheduleItem.DictionaryItem -> RemoteKind.DICTIONARY
        is ScheduleItem.CueItem -> RemoteKind.CUE
    }

    const val DESKTOP = "desktop"
}
