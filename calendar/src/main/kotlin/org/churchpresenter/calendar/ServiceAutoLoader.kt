package org.churchpresenter.calendar

import kotlinx.coroutines.delay
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.rowsForSchedule
import org.churchpresenter.calendar.model.serviceToAutoLoad
import org.churchpresenter.calendar.model.timingForSchedule
import java.time.LocalDateTime

/**
 * Puts the service that is about to start into the Schedule tab, without anyone asking.
 *
 * Off unless `CalendarPreferences.autoLoadService` is on. It reads `calendar.json` rather than the
 * planner's state, so it works with the Calendar Manager closed -- which is the point: the machine
 * is switched on, the operator opens the app, and the morning's run of show is already there.
 *
 * **It clears the Schedule and puts the service in its place.** That is deliberate and was asked
 * for: the tab is meant to hold the service that is about to run, so whatever is left over from
 * last week goes. It happens once per service per day, so work done in the Schedule after the load
 * is never wiped by a later tick.
 */
class ServiceAutoLoader(
    private val document: suspend () -> CalendarDocument,
    private val host: CalendarHost,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    /** What was last loaded -- see [loadKey] -- so a service loads once rather than every tick. */
    private var loaded = ""

    /** Checks and loads forever, every [tickMillis]. Cancel the coroutine to stop. */
    suspend fun run(tickMillis: Long = TICK_MILLIS, startupMillis: Long = STARTUP_MILLIS) {
        // The Schedule tab publishes the actions a load goes through when it first composes, and
        // this loop starts in the same instant: a tick before that lands nowhere, and the service
        // then waits a whole tick for the retry -- long enough for a row pinned in the gap to fall
        // outside the engine's grace window. The lead is five minutes; a moment here costs nothing.
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
        val service = calendar.serviceToAutoLoad(at) ?: return
        val key = service.loadKey()
        // "Already loaded" is only believed while the Schedule actually holds something.
        //
        // The load goes through the Schedule tab's own actions, and those do not exist until that
        // tab has first composed -- a tick in that gap (the app is started seconds before a
        // service, which is exactly when this feature is wanted) calls into a no-op and loads
        // nothing. Recording it as done there would lose the service for the rest of the day, so
        // an empty Schedule means it is tried again on the next tick instead.
        if (key == loaded && host.currentSchedule().isNotEmpty()) return
        loaded = key
        host.loadIntoSchedule(service.rowsForSchedule(), service.timingForSchedule(), true, service.armed)
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
private fun PlannedService.loadKey(): String =
    "$date|$id|$startTime|" + items.joinToString(",") { it.id } + "|" + timing.hashCode()

/**
 * A minute, and no more often: the lead is five minutes, so a minute's granularity costs nothing
 * worth having, and each tick reads and parses `calendar.json`.
 */
private const val TICK_MILLIS = 60_000L

/** Long enough for the first composition to have published the Schedule's actions. */
private const val STARTUP_MILLIS = 5_000L
