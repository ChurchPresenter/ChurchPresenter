package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.previewClockStart
import org.churchpresenter.calendar.model.storedDate
import java.time.LocalDate
import java.time.LocalTime

/**
 * The clock the run of show judges its cues against.
 *
 * [now] is the wall clock, kept to the minute, when the open service is today; a preview clock
 * once [step] has been pressed, which walks any service's timeline in advance; and null otherwise,
 * because a service on another day has no "fired" or "next" to speak of. The preview is per
 * service -- switching services drops it.
 */
internal class RunClockState(val now: LocalTime?, val step: () -> Unit, val reset: () -> Unit)

@Composable
internal fun rememberRunClock(service: PlannedService?, today: LocalDate): RunClockState {
    var wallClock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_TICK_MILLIS)
            wallClock = LocalTime.now()
        }
    }
    var preview by remember(service?.id) { mutableStateOf<LocalTime?>(null) }
    val now = preview ?: wallClock.takeIf { service?.date == storedDate(today) }
    return RunClockState(
        now = now,
        step = {
            if (service != null) {
                preview = (preview ?: previewClockStart(service.startTime, now)).plusMinutes(CLOCK_STEP_MINUTES)
            }
        },
        reset = { preview = null },
    )
}

private const val CLOCK_TICK_MILLIS = 15_000L
private const val CLOCK_STEP_MINUTES = 5L
