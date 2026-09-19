package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_blank
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_countdown
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_go_live
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_project
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_scene
import org.churchpresenter.calendar.generated.resources.calendar_cue_after
import org.churchpresenter.calendar.generated.resources.calendar_cue_at_start
import org.churchpresenter.calendar.generated.resources.calendar_cue_badge_go_live
import org.churchpresenter.calendar.generated.resources.calendar_cue_badge_timer
import org.churchpresenter.calendar.generated.resources.calendar_cue_before
import org.churchpresenter.calendar.generated.resources.calendar_cue_item_missing
import org.churchpresenter.calendar.generated.resources.calendar_cue_loops
import org.churchpresenter.calendar.generated.resources.calendar_cue_times
import org.churchpresenter.calendar.generated.resources.calendar_cue_pinned_at
import org.churchpresenter.calendar.generated.resources.calendar_cues_count_one
import org.churchpresenter.calendar.generated.resources.calendar_cues_count_other
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.model.canPlayRepeatedly
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.cueFireTime
import org.jetbrains.compose.resources.stringResource

/** The words for a cue, shared by the pane, the settings tab and the sheet so they cannot drift. */

@Composable
fun cueActionLabel(action: String): String = stringResource(
    when (action) {
        CueAction.COUNTDOWN -> Res.string.calendar_cue_action_countdown
        CueAction.GO_LIVE -> Res.string.calendar_cue_action_go_live
        CueAction.SCENE -> Res.string.calendar_cue_action_scene
        CueAction.BLANK -> Res.string.calendar_cue_action_blank
        else -> Res.string.calendar_cue_action_project
    }
)

/** The small badge a cue row carries, or null for the actions that have none. */
@Composable
fun cueBadge(action: String): String? = when (action) {
    CueAction.COUNTDOWN -> stringResource(Res.string.calendar_cue_badge_timer)
    CueAction.GO_LIVE -> stringResource(Res.string.calendar_cue_badge_go_live)
    else -> null
}

/** What the cue does, under its label — the action and, for a shown item, which. */
@Composable
fun cueSubtitle(cue: ScheduleItem.CueItem): String {
    val action = cueActionLabel(cue.action)
    if (cue.action != CueAction.PROJECT) return action
    val payload = cue.payload ?: return action + " · " + stringResource(Res.string.calendar_cue_item_missing)
    return action + " · " + payload.displayText
}

/** `At start`, `15 min before`, `30 min in`, or `at 11:00` when pinned. */
@Composable
fun cueWhenLabel(cue: ScheduleItem.CueItem): String = when {
    cue.isPinned() ->
        stringResource(Res.string.calendar_cue_pinned_at, clockText(cue.absoluteTime, LocalUse24HourClock.current))
    else -> offsetLabel(cue.offsetMinutes)
}

@Composable
fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(Res.string.calendar_cue_at_start)
    minutes < 0 -> stringResource(Res.string.calendar_cue_before, -minutes)
    else -> stringResource(Res.string.calendar_cue_after, minutes)
}

/** The clock time a cue fires on a service starting at [startTime], as `09:45`; blank if it cannot. */
@Composable
fun cueTimeText(cue: ScheduleItem.CueItem, startTime: String?): String =
    cueFireTime(cue, startTime)?.let { clockText(it, LocalUse24HourClock.current) }.orEmpty()

@Composable
fun cueCountLabel(count: Int): String = if (count == 1) {
    stringResource(Res.string.calendar_cues_count_one, count)
} else {
    stringResource(Res.string.calendar_cues_count_other, count)
}
