package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem

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

/**
 * Every row pinned to the time the plan says it runs: the first row keeps its own time, and each
 * row after it starts when the one before it ends.
 *
 * What "lay out the times" does. A run of show built with both pins and `→ Next` asks for two
 * different things -- the pins say when, the lengths say how long, and they contradict each other
 * the moment one row runs longer than the gap to the next pin. This resolves it the way a plan is
 * actually read: in order, from the top.
 *
 * It stops at the first row with no planned length, leaving that row and everything after it
 * untouched: a row of unknown length makes every time after it a guess, and a plan that quietly
 * invents times is worse than one that visibly stops.
 */
fun PlannedService.withTimesLaidOut(): PlannedService {
    val rows = items.filter { it !is ScheduleItem.LabelItem && it !is ScheduleItem.CueItem }
    val anchor = rows.firstNotNullOfOrNull { parseStoredTime(timingOf(it.id).startAt) }
        ?: parseStoredTime(startTime)
        ?: return this
    var clock = anchor
    val laid = timing.toMutableMap()
    for (row in rows) {
        val timing = timingOf(row.id)
        // A row that waits its turn keeps waiting: pinning it would take the hand-off away, which
        // is the one thing it was set to do.
        laid[row.id] = if (timing.followsPrevious) timing else timing.copy(startAt = storedTime(clock))
        val planned = plannedSeconds[row.id] ?: break
        clock = clock.plusSeconds(planned.toLong() * timing.repeats.coerceAtLeast(1))
    }
    return copy(timing = laid)
}

/**
 * The rows set to follow the previous one that nothing ever hands to.
 *
 * A row waits its turn, but a turn only comes if the row before it is set to advance -- and the
 * first row of a service has nothing before it at all. Both are easy to create and impossible to
 * see: the row sits there looking scheduled and never goes live. The run of show marks these.
 */
fun PlannedService.followsWithoutHandoff(): Set<String> {
    val rows = items.filter { it !is ScheduleItem.LabelItem && it !is ScheduleItem.CueItem }
    return rows.withIndex()
        .filter { (index, row) ->
            val timing = timingOf(row.id)
            if (!timing.followsPrevious || timing.startsOnItsOwn()) return@filter false
            val previous = rows.getOrNull(index - 1) ?: return@filter true
            timingOf(previous.id).atEnd != RowEnd.NEXT
        }
        .map { (_, row) -> row.id }
        .toSet()
}

/**
 * Each row's projected clock time over a **loaded schedule** -- the same reckoning as [runClocks],
 * read off the timings the rows carry rather than a service's planned lengths.
 *
 * The Schedule tab is where a service actually runs, and until this it could only show a time on a
 * row that carried a pin: everything between two pinned rows sat blank, though the plan says
 * exactly when each one lands. Anchored on the first pinned row, else on [startTime] -- the start
 * of the service the schedule was loaded from, which is what a plan with no pins at all runs
 * from. With neither there is nothing to reckon from and the map is empty.
 *
 * [RowClock.exact] goes false once a row of unknown length has been passed -- every time after it
 * is a guess, and the caller draws it as one.
 */
fun scheduleClocks(
    items: List<ScheduleItem>,
    timing: Map<String, RowTiming>,
    startTime: String? = null,
): Map<String, RowClock> {
    val rows = items.filter { it !is ScheduleItem.LabelItem && it !is ScheduleItem.CueItem }
    var clock = rows.firstNotNullOfOrNull { parseStoredTime(timing[it.id]?.startAt.orEmpty()) }
        ?: parseStoredTime(startTime.orEmpty())
        ?: return emptyMap()
    var exact = true
    return rows.associate { row ->
        val plan = timing[row.id] ?: RowTiming.DEFAULT
        // Off-screen time before the row -- see RowTiming.leadSeconds -- unless the row is pinned,
        // in which case the pin says when.
        clock = clock.plusSeconds(plan.leadSeconds.toLong())
        parseStoredTime(plan.startAt)?.let {
            clock = it
            exact = true
        }
        val rowClock = RowClock(clock, exact)
        val runs = plan.runSeconds
        if (runs == null) exact = false else clock = clock.plusSeconds(runs.toLong() * plan.repeats.coerceAtLeast(1))
        row.id to rowClock
    }
}
