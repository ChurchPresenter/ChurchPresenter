package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.SECTION_SWATCHES
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.parseReference
import org.churchpresenter.calendar.model.sectionItem
import org.churchpresenter.calendar.model.toScheduleItem
import org.churchpresenter.calendar.model.withId
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A service from the relay as this desktop will keep it, and what did not survive the trip. */
class ResolvedService(
    val service: PlannedService,
    /** Rows kept but not matched to anything here, as row id → why. Drawn flagged in the run of show. */
    val unresolved: Map<String, String>,
    /** Rows discarded outright: a ref to a row this desktop never had, a kind it does not accept. */
    val dropped: Int,
)

/**
 * Turns what the relay holds into rows for `calendar.json`: each row is rebuilt from the wire
 * kinds against the library, the presets and the local copy of the service; a row that resolves
 * to nothing is kept flagged, or dropped.
 */
class Resolver(
    private val songs: List<SongItem>,
    private val presets: List<ItemPreset>,
    private val today: LocalDate,
) {
    private val songsById = songs.associateBy { it.songId }
    private val songsByTitle = songs.groupBy { it.title.trim().lowercase() }
    private val presetsById = presets.associateBy { it.id }

    /** Null when the header itself is not a service this desktop will keep. */
    fun resolve(remote: RemoteService, local: PlannedService?): ResolvedService? {
        if (!Sanitize.isId(remote.id)) return null
        val date = Sanitize.storedDate(remote.date, today) ?: return null
        val time = Sanitize.storedTime(remote.startTime) ?: return null
        val name = Sanitize.cleanText(remote.name, WireLimits.NAME_CHARS).ifEmpty { local?.name ?: DEFAULT_NAME }

        val localRows = local?.items?.associateBy { it.id }.orEmpty()
        val unresolved = LinkedHashMap<String, String>()
        val seen = HashSet<String>()
        var dropped = 0
        val items = ArrayList<ScheduleItem>()
        for (row in remote.rows.take(WireLimits.ROWS_PER_SERVICE)) {
            if (!Sanitize.isId(row.id) || !seen.add(row.id)) {
                dropped++
                continue
            }
            val item = resolveRow(row, localRows, unresolved)
            if (item == null) dropped++ else items += item
        }
        dropped += (remote.rows.size - WireLimits.ROWS_PER_SERVICE).coerceAtLeast(0)
        val kept = items.mapTo(HashSet()) { it.id }

        val service = PlannedService(
            id = remote.id,
            date = date.toString(),
            name = name,
            startTime = "%02d:%02d".format(time.hour, time.minute),
            kind = ServiceKind.from(remote.kind).id,
            items = items,
            plannedSeconds = remote.plannedSeconds
                .filterKeys { it in kept }
                .mapValues { (_, seconds) -> seconds.coerceIn(0, WireLimits.MAX_PLANNED_SECONDS) },
            timing = remote.timing.filterKeys { it in kept }.mapValues { (_, timing) -> cleanTiming(timing) }
                .filterValues { !it.isDefault() },
            cues = local?.cues.orEmpty(),
            armed = remote.armed,
            seriesId = remote.seriesId.takeIf(Sanitize::isId).orEmpty(),
            repeat = local?.repeat.orEmpty(),
            updatedAt = cleanInstant(remote.updatedAt),
            unresolvedRows = unresolved,
        )
        return ResolvedService(service, unresolved, dropped)
    }

    private fun resolveRow(
        row: RemoteRow,
        localRows: Map<String, ScheduleItem>,
        unresolved: MutableMap<String, String>,
    ): ScheduleItem? = when (row) {
        is RemoteRow.Section -> sectionItem(
            text = Sanitize.cleanText(row.title, WireLimits.TITLE_CHARS).ifEmpty { DEFAULT_SECTION },
            colorHex = Sanitize.hexColor(row.color, SECTION_SWATCHES.first()),
        ).withId(row.id)
        is RemoteRow.Song -> resolveSong(row, unresolved)
        is RemoteRow.Bible -> resolveBible(row, unresolved)
        is RemoteRow.Ministry -> ScheduleItem.MinistryItem(
            id = row.id,
            title = Sanitize.cleanText(row.title, WireLimits.TITLE_CHARS).ifEmpty { DEFAULT_MINISTRY },
            detail = Sanitize.cleanText(row.detail, WireLimits.DETAIL_CHARS),
        )
        is RemoteRow.Preset -> resolvePreset(row, unresolved)
        is RemoteRow.Ref -> localRows[row.id]
    }

    private fun resolveSong(row: RemoteRow.Song, unresolved: MutableMap<String, String>): ScheduleItem {
        val title = Sanitize.cleanText(row.title, WireLimits.TITLE_CHARS)
        val match = songsById[row.songId]
            ?: songsByTitle[title.lowercase()]?.singleOrNull()
            ?: songsByTitle[titleWithoutNumber(title).lowercase()]?.singleOrNull()
        if (match != null) {
            return ScheduleItem.SongItem(
                id = row.id,
                songNumber = match.number.toIntOrNull() ?: 0,
                title = match.title,
                songbook = match.songbook,
                songId = match.songId,
            )
        }
        unresolved[row.id] = UNRESOLVED_SONG
        return ScheduleItem.SongItem(
            id = row.id,
            songNumber = 0,
            title = title.ifEmpty { DEFAULT_SONG },
            songbook = Sanitize.cleanText(row.songbook, WireLimits.TITLE_CHARS),
            songId = "",
        )
    }

    private fun resolveBible(row: RemoteRow.Bible, unresolved: MutableMap<String, String>): ScheduleItem {
        val text = Sanitize.cleanText(row.title, WireLimits.TITLE_CHARS)
        val parsed = parseReference(text)
        if (parsed != null) {
            // A book number travels with the name when the phone knew it, so the reference resolves
            // whatever language either side's Bible is in; the name alone is matched by text later.
            val bookId = row.bookId.takeIf { it in 1..BOOKS_IN_BIBLE } ?: 0
            return parsed.toScheduleItem().copy(bookId = bookId).withId(row.id)
        }
        unresolved[row.id] = UNRESOLVED_REFERENCE
        // Kept as something that never goes on screen, so the planner sees what was meant.
        return ScheduleItem.MinistryItem(id = row.id, title = text.ifEmpty { DEFAULT_REFERENCE })
    }

    private fun resolvePreset(row: RemoteRow.Preset, unresolved: MutableMap<String, String>): ScheduleItem {
        val preset = presetsById[row.presetId]
        if (preset != null) return preset.item.withId(row.id)
        unresolved[row.id] = UNRESOLVED_PRESET
        val title = Sanitize.cleanText(row.title, WireLimits.TITLE_CHARS).ifEmpty { DEFAULT_PRESET }
        return ScheduleItem.MinistryItem(id = row.id, title = title)
    }

    private fun cleanTiming(timing: RowTiming): RowTiming = RowTiming(
        startAt = timing.startAt.takeIf { it.isEmpty() || Sanitize.storedTime(it) != null }.orEmpty(),
        followsPrevious = timing.followsPrevious && timing.startAt.isEmpty(),
        runSeconds = timing.runSeconds?.coerceIn(0, WireLimits.MAX_PLANNED_SECONDS),
        repeats = timing.repeats.coerceIn(0, WireLimits.MAX_REPEATS),
        atEnd = timing.atEnd.takeIf { it in ROW_ENDS } ?: RowEnd.HOLD,
        leadSeconds = timing.leadSeconds.coerceIn(0, WireLimits.MAX_PLANNED_SECONDS),
    )

    /** An instant as the file stores it, or empty. */
    private fun cleanInstant(text: String): String = try {
        Instant.parse(text).toString()
    } catch (_: DateTimeParseException) {
        ""
    }

    /** `42 - Here I Am to Worship` → `Here I Am to Worship`, the form a phone shows a song in. */
    private fun titleWithoutNumber(title: String): String = title.substringAfter(" - ", title)

    companion object {
        private val ROW_ENDS = setOf(RowEnd.HOLD, RowEnd.NEXT, RowEnd.BLANK)
        const val UNRESOLVED_SONG = "song"
        const val UNRESOLVED_REFERENCE = "reference"
        const val UNRESOLVED_PRESET = "preset"
        private const val BOOKS_IN_BIBLE = 66
        private const val DEFAULT_NAME = "Service"
        private const val DEFAULT_SECTION = "Section"
        private const val DEFAULT_SONG = "Song"
        private const val DEFAULT_MINISTRY = "Ministry"
        private const val DEFAULT_REFERENCE = "Reference"
        private const val DEFAULT_PRESET = "Preset"
    }
}
