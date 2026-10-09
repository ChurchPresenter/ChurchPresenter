package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.launch
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.helperText
import org.churchpresenter.liveoutput.shouldShowPresenterWindowFor
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done_announcement
import org.churchpresenter.strings.generated.resources.helper_done_announcement_cued
import org.churchpresenter.strings.generated.resources.helper_done_countdown
import org.churchpresenter.strings.generated.resources.timer_expired
import org.jetbrains.compose.resources.getString
import java.util.UUID

// The helper putting an announcement or a countdown on screen, as a Schedule row would.

private const val MINUTES_PER_HOUR = 60

/** Puts [text] — or, for a timer, a countdown of [minutes] — on screen as an announcement. */
internal fun AppRootState.helperAnnounce(text: String, minutes: Int? = null): ActionOutcome {
    val item = ScheduleItem.AnnouncementItem(
        id = UUID.randomUUID().toString(),
        text = text,
        isTimer = minutes != null,
        timerHours = (minutes ?: 0) / MINUTES_PER_HOUR,
        timerMinutes = (minutes ?: 0) % MINUTES_PER_HOUR,
        timerMode = TimerModes.DURATION,
    )
    showAnnouncement(item)
    return when {
        minutes != null -> ActionOutcome.Done(helperText(Res.string.helper_done_countdown, minutes))
        appSettings.projectionSettings.previewModeEnabled ->
            ActionOutcome.Done(helperText(Res.string.helper_done_announcement_cued))
        else -> ActionOutcome.Done(helperText(Res.string.helper_done_announcement))
    }
}

/** The way a Schedule announcement row goes up, so the Announcements tab shows the same thing. */
internal fun AppRootState.showAnnouncement(item: ScheduleItem.AnnouncementItem) {
    coroutineScope.launch {
        presentAnnouncementItem(
            item,
            timerExpiredDefaultLabel = getString(Res.string.timer_expired),
            presenterManager = presenterManager,
            onSettingsChange = { edit ->
                appSettings = edit(appSettings)
                settingsManager.saveSettings(appSettings)
            },
            presenting = { mode ->
                presenterManager.previewBus.present(mode)
                if (shouldShowPresenterWindowFor(mode)) presenterManager.setShowPresenterWindow(true)
            },
        )
    }
}
