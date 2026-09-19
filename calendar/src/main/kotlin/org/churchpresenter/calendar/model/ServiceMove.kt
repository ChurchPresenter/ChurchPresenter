package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * The same service with everything pinned to the clock moved by however far its start moved.
 *
 * A pinned time is stored absolutely -- `06:17` -- but it was almost always *chosen* as an offset:
 * the timing panel offers `−20`, `−15`, `−5`, and a pre-service sequence is three rows pinned that
 * way. Moving the service to a different hour and leaving those where they were would turn a
 * 20-minute countdown into a 50-minute one silently, so the whole plan travels with the start.
 *
 * [oldStart] is what the service started at before the edit; this service already carries the new
 * one. Unreadable times, or no change at all, leave everything alone. Times wrap at midnight, as a
 * service moved past it does.
 */
fun PlannedService.withStartMovedFrom(oldStart: String): PlannedService {
    val from = parseStoredTime(oldStart) ?: return this
    val to = parseStoredTime(startTime) ?: return this
    if (from == to) return this
    val shift = (to.toSecondOfDay() - from.toSecondOfDay()).toLong()
    fun moved(stored: String): String =
        parseStoredTime(stored)?.let { storedTime(it.plusSeconds(shift)) } ?: stored
    return copy(
        timing = timing.mapValues { (_, row) ->
            if (row.startAt.isEmpty()) row else row.copy(startAt = moved(row.startAt))
        },
        items = items.map { item ->
            if (item is ScheduleItem.CueItem && item.absoluteTime.isNotEmpty()) {
                item.copy(absoluteTime = moved(item.absoluteTime))
            } else {
                item
            }
        },
    )
}
