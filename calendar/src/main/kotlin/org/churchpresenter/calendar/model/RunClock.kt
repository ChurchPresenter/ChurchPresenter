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
 * divider, not something that takes time — and a cue row takes none either: it fires at its own
 * time, beside the rows, rather than holding the service up.
 *
 * A row that starts on its own sits at its own time, exactly, and the rows after it flow on from
 * there; the others accumulate from the service's start. A row that plays N times takes N times
 * its length; one that loops takes its stated length.
 *
 * Times wrap at midnight, which is what a service running past it actually does.
 */
fun runClocks(service: PlannedService): Map<String, RowClock> {
    val start = parseStoredTime(service.startTime) ?: return emptyMap()
    var clock = start
    var exact = true
    return service.items.associate { item ->
        val timing = service.timingOf(item.id)
        val pinned = timing.startAt.takeIf { it.isNotEmpty() }?.let(::parseStoredTime)
        if (pinned != null) {
            clock = pinned
            exact = true
        }
        val rowClock = RowClock(clock, exact)
        if (item !is ScheduleItem.LabelItem && item !is ScheduleItem.CueItem) {
            val planned = service.plannedSeconds[item.id]
            if (planned == null) {
                exact = false
            } else {
                clock = clock.plusSeconds(planned.toLong() * timing.repeats.coerceAtLeast(1))
            }
        }
        item.id to rowClock
    }
}
