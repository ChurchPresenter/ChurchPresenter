package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Duration
import java.time.LocalDateTime

/**
 * The cue rows of [items] that should fire at [now] and have not — the whole decision of the
 * automation engine, as one pure function over the live schedule.
 *
 * A cue is due when the schedule is [armed], the row is ticked, its time has passed, and it passed
 * within [grace]. The grace window is what stops a service loaded at 11:40 firing the 10:00
 * countdown, the 10:00 go-live and the 11:05 lower third one after another; it fires what was
 * meant for the last couple of minutes and lets the rest go. [fired] holds the keys of what has
 * gone off already today, so a cue fires once however often the engine looks.
 *
 * Only pinned rows have a time here: a row is pinned as it is loaded into the schedule, and one
 * that somehow is not has nothing to fire at.
 */
fun dueCues(
    items: List<ScheduleItem>,
    armed: Boolean,
    now: LocalDateTime,
    fired: Set<String>,
    grace: Duration = DEFAULT_GRACE,
): List<ScheduleItem.CueItem> {
    if (!armed) return emptyList()
    return items.filterIsInstance<ScheduleItem.CueItem>()
        .filter { it.enabled && it.id !in fired }
        .mapNotNull { cue ->
            val at = cueFireTime(cue, null)?.atDate(now.toLocalDate()) ?: return@mapNotNull null
            val elapsed = Duration.between(at, now)
            if (elapsed.isNegative || elapsed > grace) null else cue to at
        }
        .sortedBy { (_, at) -> at }
        .map { (cue, _) -> cue }
}

/** How long after its time a cue is still fired. */
val DEFAULT_GRACE: Duration = Duration.ofMinutes(2)

/**
 * The rows of [items] that start on their own and should now -- same rule as [dueCues], read off
 * each row's [RowTiming.startAt] instead of a cue's time.
 */
fun dueRows(
    items: List<ScheduleItem>,
    timing: Map<String, RowTiming>,
    armed: Boolean,
    now: LocalDateTime,
    fired: Set<String>,
    grace: Duration = DEFAULT_GRACE,
): List<ScheduleItem> {
    if (!armed) return emptyList()
    return items
        .filter { it !is ScheduleItem.CueItem && it !is ScheduleItem.LabelItem && it.id !in fired }
        .mapNotNull { row ->
            val startAt = timing[row.id]?.startAt?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val at = parseStoredTime(startAt)?.atDate(now.toLocalDate()) ?: return@mapNotNull null
            val elapsed = Duration.between(at, now)
            if (elapsed.isNegative || elapsed > grace) null else row to at
        }
        .sortedBy { (_, at) -> at }
        .map { (row, _) -> row }
}

/** The first content row after [rowId] -- what "at end: next item" goes on to. */
fun List<ScheduleItem>.nextContentRow(rowId: String): ScheduleItem? {
    val index = indexOfFirst { it.id == rowId }
    if (index < 0) return null
    return drop(index + 1).firstOrNull { it !is ScheduleItem.LabelItem && it !is ScheduleItem.CueItem }
}
