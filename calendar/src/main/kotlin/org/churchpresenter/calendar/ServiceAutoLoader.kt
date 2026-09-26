package org.churchpresenter.calendar

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.calendar.model.holdsOnlyPlannedRows
import org.churchpresenter.calendar.model.isInSchedule
import org.churchpresenter.calendar.model.nextAutoLoad
import org.churchpresenter.calendar.model.serviceInSchedule
import org.churchpresenter.calendar.model.withScheduleRows
import org.churchpresenter.calendar.model.rowsForSchedule
import org.churchpresenter.calendar.model.serviceToAutoLoad
import org.churchpresenter.calendar.model.storedDate
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
 *
 * It also says what it is going to do: [upcoming] is the next planned service still to load, for
 * the Schedule tab to announce, and [loadNow] loads it early -- days early, to rehearse it.
 *
 * And it closes the loop the other way: [scheduleService] is the service the Schedule holds, and
 * whether the Schedule has since been changed, and [saveScheduleToService] writes those changes
 * back into the plan through [save] -- only when asked, so what the Schedule picks up while a
 * service is being presented never becomes part of the plan by itself.
 */
class ServiceAutoLoader(
    private val document: suspend () -> CalendarDocument,
    private val host: CalendarHost,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val save: suspend (CalendarDocument) -> Unit = {},
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

    /** A tick and a [loadNow] both read and then write [landed]; never both at once. */
    private val lock = Mutex()

    private val _upcoming = MutableStateFlow<UpcomingLoad?>(null)

    /**
     * The next planned service still to load itself, or null -- none ahead, or auto-load off.
     * A service whose rows are in the Schedule now is passed over; clear them, and it is back.
     */
    val upcoming: StateFlow<UpcomingLoad?> = _upcoming.asStateFlow()

    private val _scheduleService = MutableStateFlow<ScheduleServiceLink?>(null)

    /** The planned service the Schedule holds, or null when it holds none. */
    val scheduleService: StateFlow<ScheduleServiceLink?> = _scheduleService.asStateFlow()

    /** What was last read, so a change to the Schedule is answered without reading the file again. */
    private var lastDocument: CalendarDocument? = null

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
    suspend fun tick() = lock.withLock {
        val calendar = document().also { lastDocument = it }
        val at = now()
        try {
            loadIfDue(calendar, at)
        } finally {
            announce(calendar, at)
        }
    }

    /**
     * Loads the service [serviceId] now, ahead of its window. [replace] is the operator's answer --
     * clear the Schedule first, or add to the end of it -- rather than the rule a load nobody is
     * watching goes by. While its rows stay in the Schedule its window does not load it again;
     * cleared before then, it loads on time, as the notice goes back to saying.
     */
    suspend fun loadNow(serviceId: String, replace: Boolean) = lock.withLock {
        val calendar = document().also { lastDocument = it }
        calendar.services.firstOrNull { it.id == serviceId }?.let { load(it, replace) }
        announce(calendar, now())
    }

    /**
     * Answers a change to the Schedule -- or, with [reread], to the calendar file: what the
     * Schedule holds now, and what is still to load. A Schedule change reuses what was last read.
     */
    suspend fun refresh(reread: Boolean = false) = lock.withLock {
        val calendar = lastDocument.takeUnless { reread } ?: document().also { lastDocument = it }
        announce(calendar, now())
    }

    /**
     * Writes the Schedule's rows back into the service it holds -- see [withScheduleRows] -- and
     * saves the calendar. Read fresh rather than from [lastDocument], so an edit made in the
     * Calendar Manager a moment ago is not written over.
     */
    suspend fun saveScheduleToService() = lock.withLock {
        val calendar = document()
        val schedule = host.currentSchedule()
        val service = calendar.serviceInSchedule(schedule) ?: return@withLock
        val updated = calendar.copy(
            services = calendar.services.map { if (it.id == service.id) it.withScheduleRows(schedule) else it },
        )
        save(updated)
        lastDocument = updated
        announce(updated, now())
    }

    private fun startDay(at: LocalDateTime) {
        val today = storedDate(at.toLocalDate())
        if (today != landedDate) {
            landed.clear()
            landedDate = today
        }
    }

    private fun announce(calendar: CalendarDocument, at: LocalDateTime) {
        val schedule = host.currentSchedule()
        _upcoming.value = if (calendar.preferences.autoLoadService) {
            calendar.nextAutoLoad(at, calendar.preferences.autoLoadLead()) { it.isInSchedule(schedule) }
        } else {
            null
        }
        _scheduleService.value = calendar.serviceInSchedule(schedule)?.let { service ->
            val saved = service.items.map { it.id }
            val now = service.withScheduleRows(schedule).items.map { it.id }
            ScheduleServiceLink(service.id, service.name, hasChanges = now != saved)
        }
    }

    private fun load(service: PlannedService, replace: Boolean) {
        host.loadIntoSchedule(
            service.rowsForSchedule(), service.timingForSchedule(), replace, service.armed, service.startTime,
        )
    }

    private fun loadIfDue(calendar: CalendarDocument, at: LocalDateTime) {
        if (!calendar.preferences.autoLoadService) return
        startDay(at)
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
            // Last week's service goes; the operator's own rows stay, and this goes under them.
            else -> load(service, replace = calendar.holdsOnlyPlannedRows(current))
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
private fun PlannedService.loadKey(): String =
    "$date|$id|$startTime|" + items.joinToString(",") { it.id } + "|" + timing.hashCode()

/**
 * A minute, and no more often: the lead is minutes and never less than one, so a minute's
 * granularity costs nothing worth having, and each tick reads and parses `calendar.json`.
 */
private const val TICK_MILLIS = 60_000L

/** Long enough for the first composition to have published the Schedule's actions. */
private const val STARTUP_MILLIS = 5_000L

/**
 * The service the Schedule holds, by [serviceId] and [serviceName], and whether rows have been
 * added, removed or moved there since -- what the Schedule tab offers to save back.
 */
data class ScheduleServiceLink(val serviceId: String, val serviceName: String, val hasChanges: Boolean)
