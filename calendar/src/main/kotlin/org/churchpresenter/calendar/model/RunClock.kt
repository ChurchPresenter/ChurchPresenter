package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalTime

/** A run-of-show row's clock time, and whether it can be trusted. */
data class RowClock(
    /** The wall-clock time; the caller formats it, as the clock format is a preference. */
    val time: LocalTime,
    /**
     * False once some earlier row has no planned length.
     *
     * The clock still advances — a plan with three of its ten rows estimated is still worth showing
     * times for — but the caller draws an approximate time dimmed, so nobody reads it as a promise.
     */
    val exact: Boolean,
)

/**
 * What time each row of [service] is expected to start, keyed by row id.
 *
 * Empty when the service's own start time is unreadable, which is the one case where no row has a
 * meaningful clock. A section heading takes the clock of the row it introduces — a heading is a
 * divider, not something that takes time.
 *
 * Times wrap at midnight, which is what a service running past it actually does.
 */
fun runClocks(service: PlannedService): Map<String, RowClock> {
    val start = parseStoredTime(service.startTime) ?: return emptyMap()
    var offset = 0L
    var exact = true
    return service.items.associate { item ->
        val clock = RowClock(start.plusSeconds(offset), exact)
        if (item !is ScheduleItem.LabelItem) {
            val planned = service.plannedSeconds[item.id]
            if (planned == null) exact = false else offset += planned
        }
        item.id to clock
    }
}
