package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_blank
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_countdown
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_go_live
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_project
import org.churchpresenter.calendar.generated.resources.calendar_cue_after
import org.churchpresenter.calendar.generated.resources.calendar_cue_at_start
import org.churchpresenter.calendar.generated.resources.calendar_cue_badge_go_live
import org.churchpresenter.calendar.generated.resources.calendar_cue_badge_timer
import org.churchpresenter.calendar.generated.resources.calendar_cue_before
import org.churchpresenter.calendar.generated.resources.calendar_cue_item_missing
import org.churchpresenter.calendar.generated.resources.calendar_cue_pinned_at
import org.churchpresenter.calendar.generated.resources.calendar_cues_count_one
import org.churchpresenter.calendar.generated.resources.calendar_cues_count_other
import org.churchpresenter.calendar.model.CueAction
import org.churchpresenter.calendar.model.ServiceCue
import org.churchpresenter.calendar.model.cueFireTime
import org.churchpresenter.calendar.model.storedTime
import org.jetbrains.compose.resources.stringResource

/** The words for a cue, shared by the pane, the settings tab and the sheet so they cannot drift. */

@Composable
fun cueActionLabel(action: String): String = stringResource(
    when (action) {
        CueAction.COUNTDOWN -> Res.string.calendar_cue_action_countdown
        CueAction.GO_LIVE -> Res.string.calendar_cue_action_go_live
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
fun cueSubtitle(cue: ServiceCue): String {
    val action = cueActionLabel(cue.action)
    if (cue.action != CueAction.PROJECT) return action
    val payload = cue.payload ?: return action + " · " + stringResource(Res.string.calendar_cue_item_missing)
    return action + " · " + payload.displayText
}

/** `At start`, `15 min before`, `30 min in`, or `at 11:00` when pinned. */
@Composable
fun cueWhenLabel(cue: ServiceCue): String = when {
    cue.isPinned() -> stringResource(Res.string.calendar_cue_pinned_at, cue.absoluteTime)
    else -> offsetLabel(cue.offsetMinutes)
}

@Composable
fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(Res.string.calendar_cue_at_start)
    minutes < 0 -> stringResource(Res.string.calendar_cue_before, -minutes)
    else -> stringResource(Res.string.calendar_cue_after, minutes)
}

/** The clock time a cue fires on a service starting at [startTime], as `09:45`; blank if it cannot. */
fun cueTimeText(cue: ServiceCue, startTime: String): String =
    cueFireTime(cue, startTime)?.let(::storedTime).orEmpty()

@Composable
fun cueCountLabel(count: Int): String = if (count == 1) {
    stringResource(Res.string.calendar_cues_count_one, count)
} else {
    stringResource(Res.string.calendar_cues_count_other, count)
}
