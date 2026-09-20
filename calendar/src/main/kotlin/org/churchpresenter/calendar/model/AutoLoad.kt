package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem

import java.time.LocalDateTime
import java.time.LocalTime

/** Five minutes: long enough to have the run of show up before anything starts, short enough not to sit there. */
const val AUTO_LOAD_LEAD_MINUTES: Int = 5

/**
 * How far ahead the lead may be set, in minutes.
 *
 * A minute at the bottom because zero would mean "load it as it starts", which is a load nobody
 * has time to look at; two hours at the top because past that the window's own end -- the service
 * is assumed over -- starts before the lead does, and the service would never load at all.
 */
const val AUTO_LOAD_LEAD_MIN: Int = 1
const val AUTO_LOAD_LEAD_MAX: Int = 120

/**
 * The minutes typed into the settings' lead field, or null when it is not a number in range.
 *
 * A bare number, because the unit is in the row's subtitle: a field that draws `5 min` can only be
 * typed back in if the parser knows the word, and that word differs in each locale this ships in.
 */
fun parseLeadMinutes(text: String): Int? = text.trim()
    .toIntOrNull()
    ?.takeIf { it in AUTO_LOAD_LEAD_MIN..AUTO_LOAD_LEAD_MAX }

/**
 * Which planned service, if any, belongs in the Schedule tab at [at].
 *
 * A service is due from [AUTO_LOAD_LEAD_MINUTES] before **its first row's start time** until it
 * should be over -- its planned total, or [ASSUMED_RUN_MINUTES] when nothing has been estimated.
 * The window has an end because "it is Sunday and there was a service this morning" is not a
 * reason to load one at nine at night.
 *
 * Only today's services are considered, and where two overlap the one that starts latest wins: at
 * a midweek evening service, the morning's is over.
 */
fun CalendarDocument.serviceToAutoLoad(at: LocalDateTime, lead: Int = AUTO_LOAD_LEAD_MINUTES): PlannedService? {
    val today = storedDate(at.toLocalDate())
    return services
        .filter { it.date == today }
        .sortedBy { it.firstRowStart()?.toSecondOfDay() ?: 0 }
        .lastOrNull { it.isDueToLoad(at, lead) }
}

/**
 * When the service actually begins: its first content row's clock time.
 *
 * Not the service's own start time, which is where that clock *starts* -- a run of show whose
 * first row is pinned to 10:15 begins at 10:15, whatever the service is filed under. Falls back to
 * the service's start when there is no row to ask, and is null only when neither time parses.
 */
fun PlannedService.firstRowStart(): LocalTime? {
    val start = parseStoredTime(startTime)
    val first = contentItems().firstOrNull() ?: return start
    return runClocks(this)[first.id]?.time ?: start
}

/** Whether [at] falls in this service's loading window -- see [serviceToAutoLoad]. */
fun PlannedService.isDueToLoad(at: LocalDateTime, lead: Int = AUTO_LOAD_LEAD_MINUTES): Boolean {
    val start = firstRowStart() ?: return false
    val now = at.toLocalTime().toSecondOfDay()
    val from = start.toSecondOfDay() - lead.coerceAtLeast(0) * SECONDS_PER_MINUTE
    val runs = plannedTotalSeconds().takeIf { it > 0 } ?: (ASSUMED_RUN_MINUTES * SECONDS_PER_MINUTE)
    return now >= from && now <= start.toSecondOfDay() + runs
}

/** How long a service with nothing estimated is assumed to run, for the end of the window above. */
private const val ASSUMED_RUN_MINUTES = 120
private const val SECONDS_PER_MINUTE = 60

/**
 * Whether [schedule] holds nothing but rows that came from this calendar -- a planned service
 * loaded earlier, still sitting there -- or nothing at all.
 *
 * What decides whether an automatic load may *replace* the Schedule. Rows loaded from a plan keep
 * their ids, so a leftover service is recognisable by them and can go; a row with an id this
 * calendar has never seen was built by hand in the Schedule tab, and a load that wiped it five
 * minutes before the service would be doing the opposite of helping -- that load appends.
 */
fun CalendarDocument.holdsOnlyPlannedRows(schedule: List<ScheduleItem>): Boolean {
    if (schedule.isEmpty()) return true
    val planned = HashSet<String>()
    services.forEach { service -> service.items.mapTo(planned) { it.id } }
    templates.forEach { template -> template.items.mapTo(planned) { it.id } }
    return schedule.all { it.id in planned }
}
