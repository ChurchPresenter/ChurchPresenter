package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Duration
import java.time.LocalTime

/** Where one cue stands against the clock: already fired, the next to fire, or neither. */
data class CueStatus(val fired: Boolean, val isNext: Boolean, val minutesUntil: Long)

/**
 * Each cue row's [CueStatus] at [now], keyed by row id -- empty when there is no clock to stand
 * against (a service on another day). A cue that is skipped, or disarmed with the rest, is never
 * fired and never next. [startTime] resolves the rows that are not pinned.
 */
fun cueStatuses(
    items: List<ScheduleItem>,
    startTime: String?,
    armed: Boolean,
    now: LocalTime?,
): Map<String, CueStatus> {
    if (now == null || !armed) return emptyMap()
    val live = items.filterIsInstance<ScheduleItem.CueItem>()
        .filter { it.enabled }
        .mapNotNull { cue -> cueFireTime(cue, startTime)?.let { cue to it } }
    val upcoming = live.filter { (_, at) -> at.isAfter(now) }.minByOrNull { (_, at) -> at }?.first
    return live.associate { (cue, at) ->
        cue.id to CueStatus(
            fired = !at.isAfter(now),
            isNext = cue.id == upcoming?.id,
            minutesUntil = Duration.between(now, at).toMinutes(),
        )
    }
}

fun PlannedService.cueStatuses(now: LocalTime?): Map<String, CueStatus> = cueStatuses(items, startTime, armed, now)

/**
 * The rows as they go into the live schedule: the same list, with every cue's time written down
 * against this service's start. Relative on the calendar, so moving the service moves them; fixed
 * once live, because the Schedule has no start time to be relative to.
 */
fun PlannedService.rowsForSchedule(): List<ScheduleItem> =
    items.map { if (it is ScheduleItem.CueItem) it.pinnedTo(startTime) else it }

/**
 * Each row's timing as it goes into the live schedule: the planned length written in as the run
 * length, so the Schedule -- which has no estimates of its own -- knows how long a row that starts
 * on its own runs before its end action.
 */
fun PlannedService.timingForSchedule(): Map<String, RowTiming> = items.mapNotNull { item ->
    val planned = timingOf(item.id)
    val timing = if (planned.runSeconds == null) planned.copy(runSeconds = plannedSeconds[item.id]) else planned
    if (timing.isDefault()) null else item.id to timing
}.toMap()

/** How many of the service's cues will fire on their own -- ticked, and the service armed. */
fun PlannedService.activeCueCount(): Int = if (armed) cueRows().count { it.enabled } else 0

/**
 * Where a stepped preview clock starts from: a little before the service, so the pre-service cues
 * are the first thing it walks through -- or from the clock already showing, so stepping a live
 * service moves on from now rather than back to the start.
 */
fun previewClockStart(startTime: String, showing: LocalTime?, fallback: LocalTime = LocalTime.now()): LocalTime =
    showing ?: parseStoredTime(startTime)?.minusMinutes(PREVIEW_LEAD_MINUTES) ?: fallback

/** How far before the service a preview clock starts. */
const val PREVIEW_LEAD_MINUTES: Long = 20L

/**
 * The same document with every service's and template's old-style cues folded into its rows.
 *
 * Applied once on load, beside `withUniqueRowIds`: a file from before cues were rows still opens,
 * its cues take their place in the list by the clock, and the next save writes them there.
 */
fun CalendarDocument.withCuesAsRows(): CalendarDocument = copy(
    services = services.map { service ->
        if (service.cues.isEmpty()) service
        else service.cues.fold(service) { acc, cue -> acc.withCue(cue.asRow()) }.copy(cues = emptyList())
    },
    templates = templates.map { template ->
        if (template.cues.isEmpty()) template
        else {
            // A template has no date, but it has a start time, which is all placing by the clock needs.
            val placed = template.cues.fold(
                PlannedService(
                    id = template.id,
                    date = "",
                    name = template.name,
                    startTime = template.startTime,
                    items = template.items,
                ),
            ) { acc, cue -> acc.withCue(cue.asRow()) }
            template.copy(items = placed.items, cues = emptyList())
        }
    },
)
