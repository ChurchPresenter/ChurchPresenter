package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_blank
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_countdown
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_go_live
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_project
import org.churchpresenter.calendar.generated.resources.calendar_cue_action_scene
import org.churchpresenter.calendar.generated.resources.calendar_cue_badge_go_live
import org.churchpresenter.calendar.generated.resources.calendar_cue_badge_timer
import org.churchpresenter.calendar.generated.resources.calendar_cue_item_missing
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
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

/** The clock time a cue fires on a service starting at [startTime], as `09:45`; blank if it cannot. */
@Composable
fun cueTimeText(cue: ScheduleItem.CueItem, startTime: String?): String =
    cueFireTime(cue, startTime)?.let { clockText(it, LocalUse24HourClock.current) }.orEmpty()
