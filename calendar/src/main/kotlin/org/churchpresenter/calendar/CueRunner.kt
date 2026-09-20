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
    /**
     * True while the outputs show something the operator put there by hand -- a row clicked in
     * the Schedule, a song sent from the Songs tab -- rather than something this engine fired.
     *
     * The engine yields to a hand on the controls: a cue that is due while the operator is live
     * with something else is **skipped**, not fired over them, and is posted to [CueFeed] as
     * skipped so it can be fired by hand if it should still go. Blank outputs, or a picture this
     * engine put up itself, are not in the way.
     */
    private val operatorLive: () -> Boolean = { false },
) {
    private val fired = HashSet<String>()
    private var firedDate = ""

    /** End actions waiting for their moment: the row's id, when its run is over, and what then. */
    private val pendingEnds = ArrayList<PendingEnd>()

    /**
     * The row the engine last put on screen.
     *
     * An end action is booked minutes ahead, and by the time it comes the service may have moved
     * on -- a later pinned row fired, or the operator went live with something else. Running it
     * then projects "the row after" something nobody is watching any more, over whatever *is* on
     * screen: a timer's `→ Next`, booked at 07:06 for 07:11, put the pre-service slideshow back up
     * on top of the video that had just started. So an end only runs while its own row is still
     * the live one.
     */
    private var liveRow = ""

    /**
     * A row whose length is its own -- `Runs: own length` -- waiting for the item to finish.
     *
     * There is no number to count, so the end action can only be driven by the thing playing it;
     * [liveItemFinished] is how the app reports that the video reached its last frame.
     */
    private var awaitingItemEnd: Pair<String, RowTiming>? = null

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
        // Read once per tick: what is on screen is a fact about this moment, and a cue and the
        // row after it should be judged against the same one.
        val yielding = operatorLive()
        dueRows(rows, timings, isArmed, at, fired).forEach { row ->
            fired += row.id
            if (yielding) {
                CueFeed.post(FiredCue(row, at.toLocalTime(), skipped = true))
                return@forEach
            }
            val plan = timings[row.id] ?: RowTiming.DEFAULT
            liveRow = row.id
            runCatching { host.projectItem(row, plan.repeats) }
            CueFeed.post(FiredCue(row, at.toLocalTime()))
            scheduleEnd(row, plan, at)
        }
        dueCues(rows, isArmed, at, fired).forEach { cue ->
            fired += cue.id
            if (yielding) {
                CueFeed.post(FiredCue(cue, at.toLocalTime(), skipped = true))
                return@forEach
            }
            // The rows are the live schedule already, so a go-live cue has nothing to load.
            runCatching { fireCue(host, rows, cue, at.toLocalTime(), loadRows = false) }
        }
        runEnds(rows, at)
    }

    /** Books the row's end action: on the clock when it has a length, on the item when it does not. */
    private fun scheduleEnd(row: ScheduleItem, plan: RowTiming, startedAt: LocalDateTime) {
        awaitingItemEnd = null
        if (plan.atEnd == RowEnd.HOLD) return
        val seconds = plan.runSeconds
        if (seconds == null) {
            // `Runs: own length`: the item decides when it is over -- see [liveItemFinished].
            awaitingItemEnd = row.id to plan
            return
        }
        // A row played N times runs N times as long; a loop runs its stated length.
        val total = seconds.toLong() * plan.repeats.coerceAtLeast(1)
        pendingEnds += PendingEnd(row.id, startedAt.plusSeconds(total), plan.atEnd)
    }

    /**
     * The item on screen played itself out -- run the end action of the row that put it there.
     *
     * Only for the row that is still live, and only while it was waiting on its own length: a
     * video finishing says nothing about a row that was replaced two minutes ago.
     */
    fun liveItemFinished() {
        val (rowId, plan) = awaitingItemEnd ?: return
        if (rowId != liveRow) return
        awaitingItemEnd = null
        runEnd(items(), rowId, plan.atEnd, now())
    }

    private fun runEnds(rows: List<ScheduleItem>, at: LocalDateTime) {
        val due = pendingEnds.filter { !it.at.isAfter(at) }
        if (due.isEmpty()) return
        pendingEnds.removeAll(due)
        due.forEach { end ->
            // Stale: something else is on screen now, and "the row after this one" is not what
            // anybody is waiting for. See [liveRow].
            if (end.rowId != liveRow) return@forEach
            runEnd(rows, end.rowId, end.action, at)
        }
    }

    /** Carries out one row's end action: the next content row goes live, or the outputs blank. */
    private fun runEnd(rows: List<ScheduleItem>, rowId: String, action: String, at: LocalDateTime) {
        runCatching {
            when (action) {
                RowEnd.BLANK -> {
                    liveRow = ""
                    host.blankOutputs()
                }
                RowEnd.NEXT -> rows.nextContentRow(rowId)?.let { next ->
                    val plan = timing()[next.id] ?: RowTiming.DEFAULT
                    liveRow = next.id
                    host.projectItem(next, plan.repeats)
                    CueFeed.post(FiredCue(next, at.toLocalTime()))
                    scheduleEnd(next, plan, at)
                }
                else -> Unit
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
            if (loadRows) host.loadIntoSchedule(rows, emptyMap(), true, true, startTime)
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

/**
 * One row that went off on its own — a cue, or a row that starts by itself — and when.
 *
 * [skipped] is a row that was due but held back, because the operator was live with something
 * else at the time -- see `CueRunner.operatorLive`. It is in the feed so the window can say so,
 * and so the Schedule can mark it, rather than the cue silently never happening.
 */
data class FiredCue(val row: ScheduleItem, val at: LocalTime, val skipped: Boolean = false) {
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
