package org.churchpresenter.calendar

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.CueAction
import org.churchpresenter.calendar.model.DueCue
import org.churchpresenter.calendar.model.countdownItem
import org.churchpresenter.calendar.model.dueCues
import org.churchpresenter.calendar.model.isProjectableByCue
import org.churchpresenter.calendar.model.storedDate
import java.time.LocalDateTime

/**
 * Fires each service's cues at their time — the automation engine.
 *
 * Lives beside the store rather than inside the window, because the window is opened mid-week to
 * plan and closed again; a cue set for Sunday 09:45 has to fire whether or not anybody has the
 * Calendar Manager open at 09:45. The app launches [run] once at startup and it stays up for the
 * session.
 *
 * It re-reads `calendar.json` on every tick rather than sharing state with the window. The file
 * is a few kilobytes, the window writes it on every change, and reading it is what guarantees the
 * engine fires what was planned rather than what was loaded an hour ago.
 */
class CueRunner(
    private val store: CalendarStore,
    private val host: CalendarHost,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val fired = HashSet<String>()
    private var firedDate = ""

    /** Checks and fires forever, every [tickMillis]. Cancel the coroutine to stop. */
    suspend fun run(tickMillis: Long = TICK_MILLIS, io: CoroutineDispatcher = Dispatchers.IO) {
        while (true) {
            val document = withContext(io) { runCatching { store.load().document }.getOrNull() }
            if (document != null) tick(document)
            delay(tickMillis)
        }
    }

    /** One pass over [document] at the current time. Public so a test can drive it without the loop. */
    fun tick(document: CalendarDocument) {
        val at = now()
        val today = storedDate(at.toLocalDate())
        // The fired set is per day, or a weekly cue would be remembered as fired for ever.
        if (today != firedDate) {
            fired.clear()
            firedDate = today
        }
        dueCues(document, at, fired).forEach { due ->
            fired += due.key
            runCatching { fire(due) }
        }
    }

    private fun fire(due: DueCue) {
        val service = due.service
        when (due.cue.action) {
            CueAction.COUNTDOWN -> countdownItem(service.startTime)?.let(host.projectItem)
            CueAction.GO_LIVE -> {
                host.loadIntoSchedule(service.items, true)
                service.items.firstOrNull { it.isProjectableByCue() }?.let(host.projectItem)
            }
            CueAction.PROJECT -> due.cue.payload?.takeIf { it.isProjectableByCue() }?.let(host.projectItem)
            CueAction.BLANK -> host.blankOutputs()
            else -> Unit
        }
    }
}

/** Ten seconds: close enough that a cue lands within its minute, cheap enough to run all day. */
private const val TICK_MILLIS = 10_000L
