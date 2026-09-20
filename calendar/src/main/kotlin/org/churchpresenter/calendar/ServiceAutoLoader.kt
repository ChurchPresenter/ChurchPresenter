package org.churchpresenter.calendar

import kotlinx.coroutines.delay
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.holdsOnlyPlannedRows
import org.churchpresenter.calendar.model.rowsForSchedule
import org.churchpresenter.calendar.model.serviceToAutoLoad
import org.churchpresenter.calendar.model.storedDate
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.model.timingForSchedule
import java.time.LocalDateTime

/**
 * Puts the service that is about to start into the Schedule tab, without anyone asking.
 *
 * Off unless `CalendarPreferences.autoLoadService` is on. It reads `calendar.json` rather than the
 * planner's state, so it works with the Calendar Manager closed -- which is the point: the machine
 * is switched on, the operator opens the app, and the morning's run of show is already there.
 *
 * **It clears the Schedule and puts the service in its place -- when what is there came from the
 * calendar.** That is deliberate and was asked for: the tab is meant to hold the service that is
 * about to run, so whatever is left over from last week goes. Rows built by hand in the Schedule
 * tab are another matter: a load that wiped an operator's morning work five minutes before the
 * service would be the opposite of helping, so with any of those present the service is
 * **appended** instead -- see `holdsOnlyPlannedRows`. It happens **once** per plan, either way:
 * once the rows have been seen in the Schedule, an operator who then clears it meant to, and
 * putting the service back a minute later would be no better. Editing the plan makes it a
 * different plan -- see [loadKey] -- and that one loads.
 */
class ServiceAutoLoader(
    private val document: suspend () -> CalendarDocument,
    private val host: CalendarHost,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    /**
     * Every plan whose rows have been seen in the Schedule today -- see [loadKey].
     *
     * A set rather than "the last one", because a day can hold several services and their windows
     * can overlap: a long morning service is still inside its own window when the evening one
     * loads, and would otherwise be due again -- and reload itself -- the moment the evening
     * window closed.
     *
     * Kept per day, and only for plans that actually *arrived*. A load asked for before the
     * Schedule tab has published the actions it goes through lands nowhere and must be tried
     * again; one that landed and was then cleared by the operator must not.
     */
    private val landed = HashSet<String>()
    private var landedDate = ""

    /** Checks and loads forever, every [tickMillis]. Cancel the coroutine to stop. */
    suspend fun run(tickMillis: Long = TICK_MILLIS, startupMillis: Long = STARTUP_MILLIS) {
        // The Schedule tab publishes the actions a load goes through when it first composes, and
        // this loop starts in the same instant: a tick before that lands nowhere, and the service
        // then waits a whole tick for the retry -- long enough for a row pinned in the gap to fall
        // outside the engine's grace window. The lead is minutes; a moment here costs nothing.
        delay(startupMillis)
        while (true) {
            // A tick that throws must not end the loop: the next service of the day still has to
            // load. Same reasoning as CueRunner's runCatching around each host call.
            runCatching { tick() }
            delay(tickMillis)
        }
    }

    /** One pass at the current time. Public so a test can drive it without the loop. */
    suspend fun tick() {
        val calendar = document()
        val at = now()
        if (!calendar.preferences.autoLoadService) return
        val today = storedDate(at.toLocalDate())
        if (today != landedDate) {
            landed.clear()
            landedDate = today
        }
        val service = calendar.serviceToAutoLoad(at, calendar.preferences.autoLoadLead()) ?: return
        val key = service.loadKey()
        val current = host.currentSchedule()
        when {
            key in landed -> Unit
            // Its rows are in the Schedule, so the load arrived: record it and leave the Schedule
            // alone from here, whatever the operator does to it. Asking whether *these* rows are
            // there, rather than whether the Schedule holds anything, is what tells a load that
            // arrived from one that called into a no-op while last week's schedule sat there.
            service.isInSchedule(current) -> landed += key
            else -> {
                // Last week's service goes; the operator's own rows stay, and this goes under them.
                val replace = calendar.holdsOnlyPlannedRows(current)
                host.loadIntoSchedule(
                    service.rowsForSchedule(), service.timingForSchedule(), replace, service.armed, service.startTime,
                )
            }
        }
    }
}

/**
 * What makes one loading of a service distinct from another: the day, the service, its start, and
 * the run of show itself.
 *
 * All of it is deliberate. Moving a service is a decision about when it runs; editing its rows is
 * a decision about what runs. Without the rows in the key, a plan edited after the Schedule had
 * already been loaded stayed behind the one that was: rows added to the calendar were simply not
 * there when the automation looked for the next item, and the hand-off found nothing.
 */
/**
 * Whether [rows] hold this plan -- every one of its rows, by id.
 *
 * Every row and not just the first: a plan edited after it was loaded usually keeps its opening
 * row, so asking about that one alone reported an edited plan as already loaded and it never
 * reached the Schedule. Rows *beside* the plan's are ignored -- projecting a picture folder or a
 * clip appends one, and that must not make the plan look absent.
 */
private fun PlannedService.isInSchedule(rows: List<ScheduleItem>): Boolean {
    if (items.isEmpty()) return false
    val present = rows.mapTo(HashSet()) { it.id }
    return items.all { it.id in present }
}

private fun PlannedService.loadKey(): String =
    "$date|$id|$startTime|" + items.joinToString(",") { it.id } + "|" + timing.hashCode()

/**
 * A minute, and no more often: the lead is minutes and never less than one, so a minute's
 * granularity costs nothing worth having, and each tick reads and parses `calendar.json`.
 */
private const val TICK_MILLIS = 60_000L

/** Long enough for the first composition to have published the Schedule's actions. */
private const val STARTUP_MILLIS = 5_000L
