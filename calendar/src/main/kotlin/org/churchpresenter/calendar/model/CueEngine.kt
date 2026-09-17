package org.churchpresenter.calendar.model

import java.time.Duration
import java.time.LocalDateTime

/** One cue that is due now, with the service it belongs to and the key that marks it fired. */
data class DueCue(val service: PlannedService, val cue: ServiceCue) {
    /** Unique per cue per day, so a cue fires once however often the engine looks. */
    val key: String get() = service.date + "|" + service.id + "|" + cue.id
}

/**
 * The cues in [document] that should fire at [now] and have not — the whole decision of the
 * automation engine, as one pure function.
 *
 * A cue is due when its service is on today's date and armed, the cue is enabled, its time has
 * passed, and it passed within [grace]. The grace window is what stops an app started at 11:40
 * firing the 10:00 countdown, the 10:00 go-live and the 11:05 lower third one after another; it
 * fires what was meant for the last couple of minutes and lets the rest go.
 */
fun dueCues(
    document: CalendarDocument,
    now: LocalDateTime,
    fired: Set<String>,
    grace: Duration = DEFAULT_GRACE,
): List<DueCue> {
    val today = storedDate(now.toLocalDate())
    return document.services
        .filter { it.date == today && it.armed }
        .flatMap { service ->
            service.cues.filter { it.enabled }.mapNotNull { cue ->
                val at = cueFireTime(cue, service.startTime)?.atDate(now.toLocalDate()) ?: return@mapNotNull null
                val elapsed = Duration.between(at, now)
                if (elapsed.isNegative || elapsed > grace) return@mapNotNull null
                DueCue(service, cue).takeUnless { it.key in fired }
            }
        }
        .sortedBy { cueFireTime(it.cue, it.service.startTime) }
}

/** How long after its time a cue is still fired. */
val DEFAULT_GRACE: Duration = Duration.ofMinutes(2)
