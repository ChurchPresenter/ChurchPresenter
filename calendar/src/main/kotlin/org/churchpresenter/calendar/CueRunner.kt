package org.churchpresenter.calendar

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.churchpresenter.calendar.model.countdownItem
import org.churchpresenter.calendar.model.dueCues
import org.churchpresenter.calendar.model.dueRows
import org.churchpresenter.calendar.model.isProjectableByCue
import org.churchpresenter.calendar.model.nextContentRow
import org.churchpresenter.calendar.model.storedDate
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Runs the live schedule's automation — the engine.
 *
 * It watches **the schedule that is loaded**, through [items], [timing] and [armed], and nothing
 * else: the Schedule tab is what runs, so what it shows armed is exactly what will fire. A service
 * planned on the calendar fires only once it has been loaded, which is also what makes the
 * Schedule the one place to look to know what is about to happen.
 *
 * Two kinds of thing fire. A **row that starts on its own** (`RowTiming.startAt`) is put on screen
 * at its time, playing its repeats; when its run length is up, its end action runs — the next
 * content row goes live, or the outputs blank. A **cue row** does its action at its time. The
 * app starts [run] once and it stays up for the session.
 */
class CueRunner(
    private val items: () -> List<ScheduleItem>,
    private val armed: () -> Boolean,
    private val host: CalendarHost,
    private val timing: () -> Map<String, RowTiming> = { emptyMap() },
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val fired = HashSet<String>()
    private var firedDate = ""

    /** End actions waiting for their moment: the row's id, when its run is over, and what then. */
    private val pendingEnds = ArrayList<PendingEnd>()

    /** Checks and fires forever, every [tickMillis]. Cancel the coroutine to stop. */
    suspend fun run(tickMillis: Long = TICK_MILLIS) {
        while (true) {
            tick()
            delay(tickMillis)
        }
    }

    /** One pass at the current time. Public so a test can drive it without the loop. */
    fun tick() {
        val at = now()
        val today = storedDate(at.toLocalDate())
        // The fired set is per day, or a row still in the schedule tomorrow would never fire again.
        if (today != firedDate) {
            fired.clear()
            pendingEnds.clear()
            firedDate = today
        }
        val rows = items()
        val timings = timing()
        val isArmed = armed()
        dueRows(rows, timings, isArmed, at, fired).forEach { row ->
            fired += row.id
            val plan = timings[row.id] ?: RowTiming.DEFAULT
            runCatching { host.projectItem(row, plan.repeats) }
            CueFeed.post(FiredCue(row, at.toLocalTime()))
            scheduleEnd(row, plan, at)
        }
        dueCues(rows, isArmed, at, fired).forEach { cue ->
            fired += cue.id
            // The rows are the live schedule already, so a go-live cue has nothing to load.
            runCatching { fireCue(host, rows, cue, at.toLocalTime(), loadRows = false) }
        }
        runEnds(rows, at)
    }

    /** Books the row's end action, if it has one and a length to measure it from. */
    private fun scheduleEnd(row: ScheduleItem, plan: RowTiming, startedAt: LocalDateTime) {
        if (plan.atEnd == RowEnd.HOLD) return
        val seconds = plan.runSeconds ?: return
        // A row played N times runs N times as long; a loop runs its stated length.
        val total = seconds.toLong() * plan.repeats.coerceAtLeast(1)
        pendingEnds += PendingEnd(row.id, startedAt.plusSeconds(total), plan.atEnd)
    }

    private fun runEnds(rows: List<ScheduleItem>, at: LocalDateTime) {
        val due = pendingEnds.filter { !it.at.isAfter(at) }
        if (due.isEmpty()) return
        pendingEnds.removeAll(due)
        due.forEach { end ->
            runCatching {
                when (end.action) {
                    RowEnd.BLANK -> host.blankOutputs()
                    RowEnd.NEXT -> rows.nextContentRow(end.rowId)?.let { next ->
                        val plan = timing()[next.id] ?: RowTiming.DEFAULT
                        host.projectItem(next, plan.repeats)
                        CueFeed.post(FiredCue(next, at.toLocalTime()))
                        scheduleEnd(next, plan, at)
                    }
                    else -> Unit
                }
            }
        }
    }

    private data class PendingEnd(val rowId: String, val at: LocalDateTime, val action: String)
}

/**
 * Carries out [cue] through [host], now, and tells [CueFeed] it happened.
 *
 * The one place a cue's action becomes host calls — the engine fires through it on the clock, the
 * Schedule tab's go-live and the calendar's ▶ fire through it by hand, so the three cannot drift.
 * [rows] is the list the cue sits in. A go-live cue fired from the calendar loads them into the
 * Schedule first ([loadRows]); fired from the Schedule they are already there. [startTime] is
 * where a countdown with no timer of its own finds the service's start.
 */
fun fireCue(
    host: CalendarHost,
    rows: List<ScheduleItem>,
    cue: ScheduleItem.CueItem,
    at: LocalTime = LocalTime.now(),
    loadRows: Boolean = true,
    startTime: String? = null,
) {
    when (cue.action) {
        CueAction.COUNTDOWN -> (cue.payload ?: countdownItem(startTime))?.let { host.projectItem(it, ONCE) }
        CueAction.GO_LIVE -> {
            if (loadRows) host.loadIntoSchedule(rows, emptyMap(), true, true)
            val first = cue.payload ?: rows.firstOrNull { it.isProjectableByCue() }
            first?.takeIf { it.isProjectableByCue() }?.let { host.projectItem(it, cue.plays) }
        }
        CueAction.PROJECT, CueAction.SCENE -> cue.payload?.takeIf { it.isProjectableByCue() }?.let {
            host.projectItem(it, cue.plays)
        }
        CueAction.BLANK -> host.blankOutputs()
        else -> Unit
    }
    CueFeed.post(FiredCue(cue, at))
}

/** One row that went off on its own — a cue, or a row that starts by itself — and when. */
data class FiredCue(val row: ScheduleItem, val at: LocalTime) {
    /** Distinct per firing, so the window can tell a repeat of the same row from the one it dismissed. */
    val key: String get() = row.id + "|" + at.toSecondOfDay()
}

/**
 * What has fired this session, newest first, for the window to show as a toast.
 *
 * A process-wide object rather than something passed in: the engine runs for the whole session
 * whether or not the window is open, and the window is opened and closed around it. The list is
 * capped, so a Sunday's worth of cues does not grow it without bound.
 */
object CueFeed {
    private val log = MutableStateFlow<List<FiredCue>>(emptyList())

    val fired: StateFlow<List<FiredCue>> = log.asStateFlow()

    fun post(event: FiredCue) {
        log.update { (listOf(event) + it).take(LOG_LIMIT) }
    }
}

/** Ten seconds: close enough that a cue lands within its minute, cheap enough to run all day. */
private const val TICK_MILLIS = 10_000L
private const val ONCE = 1
private const val LOG_LIMIT = 12
