package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.time.LocalDate

/**
 * What this desktop tells the relay: each service with its rows reduced to what a phone needs
 * to show and reorder them. The projection is one-way by design — see [RemoteRow].
 */
object Projection {

    /**
     * The library as catalog records, keyed by record id: one per songbook, split into parts past
     * [WireLimits.CATALOG_PART_SONGS]. [seconds] is each song's usual length, or null. Sorted so the
     * same library always produces the same records -- the desktop pushes a record only when its
     * bytes changed.
     */
    fun catalog(songs: List<SongItem>, seconds: (SongItem) -> Int?): Map<String, CatalogRecord> {
        val byBook = songs.take(WireLimits.CATALOG_SONGS_MAX).groupBy { it.songbook }.toSortedMap()
        val records = LinkedHashMap<String, CatalogRecord>()
        for ((book, list) in byBook) {
            val entries = list
                .sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }, { it.title }))
                .map { song ->
                    val second = song.secondaryTitle.ifBlank { null }
                    CatalogSong(n = song.number, t = song.title, s = seconds(song), t2 = second)
                }
            entries.chunked(WireLimits.CATALOG_PART_SONGS).forEachIndexed { part, chunk ->
                records[catalogRecordId(book, part)] = CatalogRecord(songbook = book, part = part, songs = chunk)
            }
        }
        return records
    }

    /** `catalog:<songbook>` for the first part, `catalog:<songbook>:<part>` after. */
    fun catalogRecordId(songbook: String, part: Int): String =
        if (part == 0) "$CATALOG_PREFIX${catalogKey(songbook)}" else "$CATALOG_PREFIX${catalogKey(songbook)}:$part"

    /**
     * A songbook name as a record id can carry it: a readable ASCII slug -- `Songs of Praise` is
     * `Songs_of_Praise` -- plus a short hash of the real name, so a book named in another script
     * (`Гимны`, whose slug is nothing) still has an id of its own, and two such books never share one.
     * The phone builds the same id for a book it receives over the LAN; keep the two in step.
     */
    fun catalogKey(songbook: String): String {
        val slug = songbook.map { if (isSlugChar(it)) it else '_' }.joinToString("").trim('_').take(CATALOG_SLUG_CHARS)
        return (if (slug.isEmpty()) "" else "$slug-") + fnv1a(songbook)
    }

    private const val CATALOG_SLUG_CHARS = 32

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
        is ScheduleItem.BibleVerseItem -> RemoteRow.Bible(item.id, item.displayText, bookId = item.bookId)
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

private fun isSlugChar(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in ID_PUNCTUATION

/** FNV-1a over the UTF-8 bytes, as eight hex characters -- stable, tiny, and the same on every platform. */
private fun fnv1a(text: String): String {
    var hash = FNV_OFFSET
    for (byte in text.toByteArray()) hash = ((hash xor (byte.toLong() and BYTE_MASK)) * FNV_PRIME) and UINT_MASK
    return (hash and UINT_MASK).toString(HEX).padStart(HEX_CHARS, '0')
}

private const val FNV_OFFSET = 0x811c9dc5L
private const val FNV_PRIME = 0x01000193L
private const val BYTE_MASK = 0xffL
private const val UINT_MASK = 0xffffffffL
private const val HEX = 16
private const val HEX_CHARS = 8
private const val ID_PUNCTUATION = "_-."
