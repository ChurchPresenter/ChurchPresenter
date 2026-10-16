package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.time.LocalTime
import java.util.UUID

/**
 * When [cue] fires on a service that starts at [startTime], or null if neither time parses.
 *
 * A pinned cue -- and every cue once it is in the live schedule -- carries its own clock time and
 * needs no start. [startTime] is null where there is none to give, and a relative cue then has no
 * time at all rather than a wrong one.
 */
fun cueFireTime(cue: ScheduleItem.CueItem, startTime: String?): LocalTime? {
    if (cue.isPinned()) return parseStoredTime(cue.absoluteTime)
    val start = startTime?.let(::parseStoredTime) ?: return null
    return start.plusMinutes(cue.offsetMinutes.toLong())
}

/** [cue] with its time written down as a clock time, so it fires the same wherever the row goes. */
fun ScheduleItem.CueItem.pinnedTo(startTime: String): ScheduleItem.CueItem =
    cueFireTime(this, startTime)?.let { copy(absoluteTime = storedTime(it)) } ?: this

/**
 * The same service with [cue] added or, if a row with its id exists, replaced -- put in the list
 * where it fires: before the first row whose clock is not earlier than the cue's, or at the end.
 * A cue's *time* is what fires it; its place in the list is so the list reads as the service will
 * run.
 */
fun PlannedService.withCue(cue: ScheduleItem.CueItem): PlannedService {
    val without = items.filterNot { it.id == cue.id }
    val at = cueFireTime(cue, startTime)
    val clocks = runClocks(copy(items = without))
    val index = if (at == null) {
        without.size
    } else {
        without.indexOfFirst { row ->
            val rowTime = when (row) {
                is ScheduleItem.CueItem -> cueFireTime(row, startTime)
                is ScheduleItem.LabelItem -> null
                else -> clocks[row.id]?.time
            }
            rowTime != null && !rowTime.isBefore(at)
        }.let { if (it < 0) without.size else it }
    }
    return copy(items = without.toMutableList().also { it.add(index, cue) })
}

/**
 * The timer a [CueAction.COUNTDOWN] cue puts on screen: a clock countdown to [startTime].
 *
 * Built at fire time rather than stored, so moving the service moves the countdown's target with
 * it — the one thing a stored payload could not do.
 */
fun countdownItem(startTime: String?): ScheduleItem.AnnouncementItem? {
    val start = startTime?.let(::parseStoredTime) ?: return null
    return ScheduleItem.AnnouncementItem(
        id = UUID.randomUUID().toString(),
        text = "",
        isTimer = true,
        timerMode = TimerModes.CLOCK,
        targetHour = start.hour,
        targetMinute = start.minute,
        targetSecond = 0,
    )
}

/** Whether [ScheduleItem.CueItem.plays] means anything for this item — it has a run to play through. */
fun ScheduleItem.canPlayRepeatedly(): Boolean = when (this) {
    is ScheduleItem.PictureItem, is ScheduleItem.PresentationItem, is ScheduleItem.MediaItem -> true
    is ScheduleItem.AnnouncementItem -> !isTimer
    else -> false
}

/**
 * Whether the host can put [item] on screen from a cue.
 *
 * Section headings and cues are structure, not content, and a lower third is driven by its own tab
 * rather than by the projection path a cue uses, so offering any would make a cue that fires and
 * shows nothing.
 */
fun ScheduleItem.isProjectableByCue(): Boolean = when (this) {
    is ScheduleItem.LabelItem, is ScheduleItem.LowerThirdItem, is ScheduleItem.CueItem,
    is ScheduleItem.MinistryItem,
    -> false
    else -> true
}
