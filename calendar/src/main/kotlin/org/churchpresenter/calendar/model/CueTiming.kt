package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.time.LocalTime
import java.util.UUID

/** The offsets the cue sheet offers, in minutes from the service start. */
val CUE_OFFSETS: List<Int> = listOf(-30, -15, -10, -5, 0, 30, 65, 90)

/** When [cue] fires on a service that starts at [startTime], or null if neither time parses. */
fun cueFireTime(cue: ServiceCue, startTime: String): LocalTime? {
    if (cue.isPinned()) return parseStoredTime(cue.absoluteTime)
    val start = parseStoredTime(startTime) ?: return null
    return start.plusMinutes(cue.offsetMinutes.toLong())
}

/** [service]'s cues in firing order — pinned and relative cues interleaved by the clock, not the list. */
fun PlannedService.cuesInOrder(): List<ServiceCue> =
    cues.sortedBy { cueFireTime(it, startTime)?.toSecondOfDay() ?: Int.MAX_VALUE }

/** The same service with [cue] added or, if one with its id exists, replaced; kept in firing order. */
fun PlannedService.withCue(cue: ServiceCue): PlannedService {
    val next = cues.filterNot { it.id == cue.id } + cue
    return copy(cues = next).let { it.copy(cues = it.cuesInOrder()) }
}

fun PlannedService.withoutCue(cueId: String): PlannedService = copy(cues = cues.filterNot { it.id == cueId })

/** A fresh-keyed copy of [cues], for a service or template made from another. */
fun copiedCues(cues: List<ServiceCue>): List<ServiceCue> = cues.map { cue ->
    cue.copy(id = UUID.randomUUID().toString(), payload = cue.payload?.withNewId())
}

/**
 * The timer a [CueAction.COUNTDOWN] cue puts on screen: a clock countdown to [startTime].
 *
 * Built at fire time rather than stored, so moving the service moves the countdown's target with
 * it — the one thing a stored payload could not do.
 */
fun countdownItem(startTime: String): ScheduleItem.AnnouncementItem? {
    val start = parseStoredTime(startTime) ?: return null
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

/**
 * Whether the host can put [item] on screen from a cue.
 *
 * Section headings are structure, not content; a lower third and a scene are driven by their own
 * tabs rather than by the projection path a cue uses, so offering them would make a cue that
 * fires and shows nothing.
 */
fun ScheduleItem.isProjectableByCue(): Boolean = when (this) {
    is ScheduleItem.LabelItem, is ScheduleItem.LowerThirdItem, is ScheduleItem.SceneItem -> false
    else -> true
}
