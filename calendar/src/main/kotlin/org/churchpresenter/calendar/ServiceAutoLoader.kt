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
    suspend fun run(tickMillis: Long = TICK_MILLIS) {
        trace("started, every ${tickMillis}ms")
        while (true) {
            runCatching { tick() }.onFailure { trace("tick threw: $it") }
            delay(tickMillis)
        }
    }

    /** One pass at the current time. Public so a test can drive it without the loop. */
    suspend fun tick() {
        val calendar = document()
        val at = now()
        val service = calendar.serviceToAutoLoad(at)
        trace(
            "at=$at on=${calendar.preferences.autoLoadService} " +
                "services=${calendar.services.size} due=${service?.name} loaded=$loaded"
        )
        if (!calendar.preferences.autoLoadService) return
        service ?: return
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
        trace("loading ${service.name} (${service.items.size} rows)")
        host.loadIntoSchedule(service.rowsForSchedule(), service.timingForSchedule(), true, service.armed)
    }
}

/**
 * TEMPORARY while this is being confirmed on a real machine -- stderr *and* a file beside
 * `calendar.json`, because the app is usually started from something whose console nobody reads.
 * Remove both this and its callers once the feature is confirmed working.
 */
private fun trace(message: String) {
    val line = java.time.LocalTime.now().withNano(0).toString() + " [AutoLoad] " + message
    System.err.println(line)
    runCatching {
        java.io.File(System.getProperty("user.home"), ".churchpresenter/autoload-debug.log")
            .appendText(line + "\n")
    }
}

/**
 * What makes one loading of a service distinct from another: the day, the service, and its start.
 *
 * The start time is in it deliberately. Moving a service is a decision about when it runs, so the
 * moved service is due again -- without that, correcting a start time after the run of show had
 * already gone into the Schedule would silently do nothing.
 */
private fun PlannedService.loadKey(): String = "$date|$id|$startTime"

/**
 * A minute, and no more often: the lead is five minutes, so a minute's granularity costs nothing
 * worth having, and each tick reads and parses `calendar.json`.
 */
private const val TICK_MILLIS = 60_000L
