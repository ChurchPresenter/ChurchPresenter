package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.settings.shown
import org.churchpresenter.app.churchpresenter.server.emitRemoteTabSelection
import org.churchpresenter.app.churchpresenter.server.executeProjectItem
import org.churchpresenter.app.churchpresenter.server.withAnnouncement

/*
 * What the calendar and its cue automation do to the live app. Plain functions over the app's
 * state: the engine, the auto-loader and the Calendar Manager all fire through them.
 */

/**
 * What a fired calendar cue does — the same path a phone's "project" takes once approved, minus the
 * approval: the operator planned it. [plays] is the cue's Once / Loop / N times: 1, 0, or N. Each
 * kind of item already has its own notion of a repeat, so it is mapped onto that rather than timed
 * from here.
 */
internal fun AppRootState.projectFromCalendar(item: ScheduleItem, plays: Int) {
    // Select it as a click would, so the Schedule shows what is live.
    currentScheduleActions.selectItem(item.id)
    liveDurationLog.wentLive(item)
    engineLiveItem = item
    when (item) {
        // Scenes are driven by MainDesktop's own ViewModel; the bridge is
        // the one way there. Everything else is what a phone can project.
        is ScheduleItem.SceneItem -> currentScheduleActions.presentScene(item.sceneId)
        else -> {
            val shown = calendarShownItem(item, plays)
            if (shown is ScheduleItem.AnnouncementItem) {
                appSettings = appSettings.withAnnouncement(shown)
            }
            executeProjectItem(
                shown,
                currentScheduleActions,
                presenterManager,
                statisticsManager,
            )
            if (shown is ScheduleItem.MediaItem) {
                mediaViewModel.setLooping(plays != 1)
                mediaViewModel.setLoopCount(calendarMediaLoopCount(plays))
            }
            currentScheduleActions.playSlideshow(shown, plays)
            coroutineScope.launch {
                emitRemoteTabSelection(
                    shown, remoteSelectSongFlow,
                    remoteSelectPictureFlow, remoteSelectPresentationFlow,
                    remoteSelectMediaFlow,
                )
            }
        }
    }
}

/** Loads a planned service's rows into the Schedule, with their timing and the service's start. */
internal fun AppRootState.loadFromCalendar(
    items: List<ScheduleItem>,
    timing: Map<String, RowTiming>,
    replace: Boolean,
    armed: Boolean,
    startTime: String?,
) {
    UsageEvents.record(UsageEvent.CALENDAR_LOADED)
    // The service's start anchors the Schedule's clock column where
    // no row is pinned. Appending to a schedule keeps the start it
    // already runs from; appending to an empty one adopts this one's.
    val wasEmpty = currentScheduleItems.isEmpty()
    if (replace) currentScheduleActions.clearSchedule()
    if (calendarSetsServiceStart(replace, wasEmpty)) currentScheduleActions.setServiceStart(startTime)
    // Rows go in whole, ids and all: a plan's headings, lower thirds,
    // scenes and cues survive the trip, and each row's timing lands on it.
    items.forEach { item ->
        currentScheduleActions.addRow(item, timing[item.id])
    }
    // The service's own switch comes with it: what was armed on the
    // calendar is armed here, where the cues actually fire from.
    automationArmed = armed
}

/**
 * What a cue does when it goes off -- the engine, the Schedule tab's go-live on a cue row and the
 * Calendar Manager's ▶ all fire through it.
 */
internal fun AppRootState.calendarCueHost(): CalendarHost = CalendarHost(
    loadIntoSchedule = this::loadFromCalendar,
    // What the auto-loader asks before it loads: without it every
    // Schedule looks empty, so a service loads again each minute of
    // its window and rows built by hand are cleared with it.
    currentSchedule = { currentScheduleItems },
    projectItem = this::projectFromCalendar,
    blankOutputs = {
        presenterManager.requestClearDisplay()
        liveDurationLog.wentBlank()
        engineLiveItem = null
    },
)

