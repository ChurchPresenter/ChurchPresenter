package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * The planned service [schedule] was loaded from, or null when it holds none.
 *
 * Found by the rows themselves -- a load keeps every row's id -- so it needs no memory of which
 * load happened, and survives a restart. Where rows of more than one service are present, the one
 * with the most of its rows there is the one the Schedule is.
 */
fun CalendarDocument.serviceInSchedule(schedule: List<ScheduleItem>): PlannedService? {
    if (schedule.isEmpty()) return null
    val present = schedule.mapTo(HashSet()) { it.id }
    return services
        .map { service -> service to service.rowsForSchedule().count { it.id in present } }
        .filter { (_, count) -> count > 0 }
        .maxByOrNull { (_, count) -> count }
        ?.first
}

/** Whether every row this service puts into the Schedule is in [schedule]. */
fun PlannedService.isInSchedule(schedule: List<ScheduleItem>): Boolean {
    val rows = rowsForSchedule()
    if (rows.isEmpty()) return false
    val present = schedule.mapTo(HashSet()) { it.id }
    return rows.all { it.id in present }
}

/**
 * This service with its run of show replaced by what [schedule] holds now: rows added there are
 * added, rows removed there are removed, and the order is the Schedule's.
 *
 * What the Schedule cannot show is kept as planned rather than lost:
 * - **Off-screen rows** never go into the Schedule, so each stays just before the row it preceded
 *   on the calendar, or at the end if that row has gone.
 * - **A cue** goes in pinned to the clock; the calendar keeps its own, relative to the start.
 * - **Planned lengths and timing** are kept for every row that is still there. A row new to the
 *   service has none.
 */
fun PlannedService.withScheduleRows(schedule: List<ScheduleItem>): PlannedService {
    val planned = items.associateBy { it.id }
    // Each off-screen row, under the id of the row that followed it on the calendar.
    val offScreenBefore = LinkedHashMap<String?, MutableList<ScheduleItem>>()
    items.forEachIndexed { index, item ->
        if (item !is ScheduleItem.MinistryItem) return@forEachIndexed
        val next = items.drop(index + 1).firstOrNull { it !is ScheduleItem.MinistryItem }?.id
        offScreenBefore.getOrPut(next) { mutableListOf() } += item
    }
    val scheduleIds = schedule.mapTo(HashSet()) { it.id }
    val rows = buildList {
        schedule.forEach { row ->
            offScreenBefore.remove(row.id)?.let(::addAll)
            add(if (row is ScheduleItem.CueItem) planned[row.id] ?: row else row)
        }
        // Whatever preceded a row that has gone, and whatever ended the service.
        offScreenBefore.forEach { (next, offScreen) -> if (next !in scheduleIds) addAll(offScreen) }
    }
    val kept = rows.mapTo(HashSet()) { it.id }
    return copy(
        items = rows,
        plannedSeconds = plannedSeconds.filterKeys { it in kept },
        timing = timing.filterKeys { it in kept },
    )
}
