package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.security.MessageDigest
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
     * A songbook name as a record id can carry it -- ASCII id characters only: `Songs of Praise` is
     * `Songs_of_Praise`. A name with letters outside ASCII (Tamil, Cyrillic) also carries a hash of
     * the whole name, or every such book would come out as the same row of underscores.
     */
    private fun catalogKey(songbook: String): String {
        val key = songbook.map { if (it.isAsciiIdChar()) it else '_' }.joinToString("")
        if (songbook.all { it.code < ASCII_END }) return key.take(CATALOG_KEY_CHARS).ifEmpty { "_" }
        val hash = MessageDigest.getInstance("SHA-256").digest(songbook.toByteArray())
            .take(HASH_BYTES).joinToString("") { "%02x".format(it) }
        return key.take(CATALOG_KEY_CHARS - hash.length - 1) + "-" + hash
    }

    private fun Char.isAsciiIdChar(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in ID_PUNCTUATION

    private const val ID_PUNCTUATION = "_-."

    private const val CATALOG_KEY_CHARS = 48

    private const val ASCII_END = 128

    private const val HASH_BYTES = 4

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
