package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_sum_loops_for
import org.churchpresenter.calendar.generated.resources.calendar_sum_loops_until
import org.churchpresenter.calendar.generated.resources.calendar_sum_own_length
import org.churchpresenter.calendar.generated.resources.calendar_sum_plays_times
import org.churchpresenter.calendar.generated.resources.calendar_sum_runs
import org.churchpresenter.calendar.generated.resources.calendar_sum_starts_at
import org.churchpresenter.calendar.generated.resources.calendar_sum_starts_cued
import org.churchpresenter.calendar.generated.resources.calendar_sum_then_blank
import org.churchpresenter.calendar.generated.resources.calendar_sum_then_hold
import org.churchpresenter.calendar.generated.resources.calendar_sum_then_next
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.calendar.model.parseClockText
import org.churchpresenter.calendar.model.parseDuration
import org.churchpresenter.calendar.model.storedTime
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import java.time.LocalTime

/**
 * What the picker's timing panel is editing: the four things [RowTiming] holds plus the run
 * length, as the text the operator typed where a field is involved -- half of `9:45` is not a
 * time, and the panel must be able to hold it while it is being typed.
 */
data class TimingDraft(
    /** As typed, or the picked chip's time in the current clock format. Empty is "cued". */
    val startText: String = "",
    /** The `After previous` chip: this row's turn comes when the one before it ends. */
    val followsPrevious: Boolean = false,
    val durationText: String = "",
    val repeats: Int = 1,
    /** The `N` field's own text; the chips write it, and typing it sets [repeats]. */
    val repeatsText: String = "",
    val atEnd: String = RowEnd.HOLD,
) {
    fun startTime(): LocalTime? = startText.takeIf { it.isNotBlank() }?.let { parseClockText(it) }
    fun runSeconds(): Int? = parseDuration(durationText)

    /** The timing this draft describes; run length is carried separately as the planned length. */
    fun toTiming(): RowTiming = RowTiming(
        startAt = startTime()?.let(::storedTime).orEmpty(),
        followsPrevious = followsPrevious && startText.isBlank(),
        repeats = repeats,
        atEnd = atEnd,
    )

    companion object {
        fun of(timing: RowTiming, plannedSeconds: Int?, use24Hour: Boolean): TimingDraft = TimingDraft(
            startText = timing.startAt.takeIf { it.isNotEmpty() }?.let { clockText(it, use24Hour) }.orEmpty(),
            followsPrevious = timing.followsPrevious,
            durationText = plannedSeconds?.let(::formatDuration).orEmpty(),
            repeats = timing.repeats,
            repeatsText = if (timing.repeats > 1) timing.repeats.toString() else "",
            atEnd = timing.atEnd,
        )
    }
}

/** `Starts on its own at 9:45 AM · loops for 15:00 · then advances` -- the panel read back as one line. */
@Composable
fun timingSummary(draft: TimingDraft): String {
    val use24Hour = LocalUse24HourClock.current
    val runs = draft.runSeconds()
    val starts = draft.startTime()?.let { stringResource(Res.string.calendar_sum_starts_at, clockText(it, use24Hour)) }
        ?: stringResource(Res.string.calendar_sum_starts_cued)
    val length = when {
        draft.repeats == 0 && runs != null -> stringResource(Res.string.calendar_sum_loops_for, formatDuration(runs))
        draft.repeats == 0 -> stringResource(Res.string.calendar_sum_loops_until)
        draft.repeats > 1 -> stringResource(
            Res.string.calendar_sum_plays_times,
            draft.repeats,
            runs?.let { formatDuration(it * draft.repeats) } ?: stringResource(Res.string.calendar_sum_own_length),
        )
        runs != null -> stringResource(Res.string.calendar_sum_runs, formatDuration(runs))
        else -> stringResource(Res.string.calendar_sum_own_length)
    }
    val then = stringResource(
        when (draft.atEnd) {
            RowEnd.NEXT -> Res.string.calendar_sum_then_next
            RowEnd.BLANK -> Res.string.calendar_sum_then_blank
            else -> Res.string.calendar_sum_then_hold
        }
    )
    return "$starts · $length · $then"
}
