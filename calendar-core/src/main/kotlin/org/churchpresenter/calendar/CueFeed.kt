package org.churchpresenter.calendar

import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.churchpresenter.core.models.schedule.ScheduleItem

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

    /**
     * Forgets everything. For a test or a screenshot that posts to this process-wide feed: the
     * marks and the toast read from it, so a firing left behind would show up in the next one.
     */
    fun clear() {
        log.value = emptyList()
    }
}

private const val LOG_LIMIT = 12
