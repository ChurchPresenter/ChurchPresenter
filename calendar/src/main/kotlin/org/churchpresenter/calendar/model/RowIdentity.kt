package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.util.UUID

/**
 * The same row with a fresh id.
 *
 * Copying out of the Schedule tab has to re-key, or the planned row and the live row share an id —
 * and the planned service's own `plannedSeconds` map is keyed by it, so two services copied from
 * the same schedule would share each other's estimates.
 */
fun ScheduleItem.withNewId(): ScheduleItem {
    val fresh = UUID.randomUUID().toString()
    return when (this) {
        is ScheduleItem.SongItem -> copy(id = fresh)
        is ScheduleItem.BibleVerseItem -> copy(id = fresh)
        is ScheduleItem.LabelItem -> copy(id = fresh)
        is ScheduleItem.PictureItem -> copy(id = fresh)
        is ScheduleItem.PresentationItem -> copy(id = fresh)
        is ScheduleItem.MediaItem -> copy(id = fresh)
        is ScheduleItem.LowerThirdItem -> copy(id = fresh)
        is ScheduleItem.AnnouncementItem -> copy(id = fresh)
        is ScheduleItem.WebsiteItem -> copy(id = fresh)
        is ScheduleItem.SceneItem -> copy(id = fresh)
        is ScheduleItem.DictionaryItem -> copy(id = fresh)
        is ScheduleItem.CueItem -> copy(id = fresh, payload = payload?.withNewId())
    }
}

/** A run of show, its estimates and its timing, re-keyed together. */
data class CopiedRows(
    val items: List<ScheduleItem>,
    val plannedSeconds: Map<String, Int>,
    val timing: Map<String, RowTiming> = emptyMap(),
)

/**
 * A fresh-keyed copy of [items] with [plannedSeconds] and [timing] following each row to its new id.
 *
 * Every copy a service makes of another's rows — from a template, from last week, into the next
 * occurrence of a series — goes through here, or the two services share row ids and editing one
 * estimate moves the other's too.
 */
fun copiedRows(
    items: List<ScheduleItem>,
    plannedSeconds: Map<String, Int>,
    timing: Map<String, RowTiming> = emptyMap(),
): CopiedRows {
    val copied = items.map { it.withNewId() }
    val seconds = items.indices.mapNotNull { index ->
        plannedSeconds[items[index].id]?.let { copied[index].id to it }
    }
    val timings = items.indices.mapNotNull { index ->
        timing[items[index].id]?.let { copied[index].id to it }
    }
    return CopiedRows(copied, seconds.toMap(), timings.toMap())
}

/**
 * The same document with every service's row ids made unique.
 *
 * Applied once on load, and the reason drag-and-drop can key rows by id at all: a `LazyColumn` key
 * must be unique or it throws, and reordering needs the key to be *stable per row*, so the index
 * cannot be part of it. Those two demands can only both be met if ids are genuinely unique — which
 * a file on disk cannot promise. `calendar.json` is hand-editable, a run of show can be pasted in
 * from an old `.schedule`, and a bug anywhere upstream would otherwise surface as a crash in the
 * list rather than as the data problem it is.
 *
 * [PlannedService.plannedSeconds] is re-keyed with the rows, so an estimate follows its row.
 */
fun CalendarDocument.withUniqueRowIds(): CalendarDocument =
    copy(services = services.map { it.withUniqueRowIds() })

private fun PlannedService.withUniqueRowIds(): PlannedService {
    val seen = HashSet<String>(items.size)
    var changed = false
    val remapped = HashMap<String, String>()
    val fixed = items.map { item ->
        if (seen.add(item.id)) {
            item
        } else {
            changed = true
            item.withNewId().also { remapped[item.id] = it.id }
        }
    }
    if (!changed) return this
    // A duplicated id can only carry one estimate between them, so the first row keeps it and each
    // later copy takes the same value rather than losing it.
    val seconds = plannedSeconds.toMutableMap()
    val timings = timing.toMutableMap()
    remapped.forEach { (original, fresh) ->
        plannedSeconds[original]?.let { seconds[fresh] = it }
        timing[original]?.let { timings[fresh] = it }
    }
    return copy(items = fixed, plannedSeconds = seconds, timing = timings)
}

/** The section heading a name and a color make, as an ordinary run-of-show row. */
fun sectionItem(text: String, colorHex: String): ScheduleItem.LabelItem = ScheduleItem.LabelItem(
    id = UUID.randomUUID().toString(),
    text = text,
    textColor = SECTION_TEXT_COLOR,
    backgroundColor = colorHex,
)

/** Section headings are drawn from their own color, so the stored text color is only a fallback. */
private const val SECTION_TEXT_COLOR = "#FFFFFF"
