package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.viewmodel.clearLayer
import org.churchpresenter.app.churchpresenter.viewmodel.layerForName
import org.churchpresenter.server.broadcastDisplayCleared
import org.churchpresenter.server.broadcastSongSectionSelected
import org.churchpresenter.server.updateBrowserSourceTranspose
import org.churchpresenter.app.churchpresenter.dialogs.text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.tooltip_clear_display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.server.RemoteActivityNotification
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.lowerthird.LowerThirdSequencer
import org.churchpresenter.calendar.CalendarFileWatcher
import org.churchpresenter.calendar.seedCalendarFolder
import org.churchpresenter.calendar.CueRunner
import org.churchpresenter.settings.calendarFolder
import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.jetbrains.compose.resources.stringResource

/** The cue engine and the service auto-loader, running for the whole session. */
@Composable
internal fun MainWindowScope.CalendarAutomationWiring() {
    with(root) {
        // The automation engine, up for the whole session: it watches the live
        // Schedule and fires its cue rows at their time. See CueRunner.
        val cueRunner = remember {
            CueRunner(
                items = { currentScheduleItems },
                timing = { currentScheduleActions.currentTiming() },
                armed = { automationArmed },
                host = cueHost.copy(
                    projectItem = { item, plays ->
                        UsageEvents.recordOncePerRun(UsageEvent.CALENDAR_CUE_FIRED)
                        cueHost.projectItem(item, plays)
                    },
                    blankOutputs = {
                        UsageEvents.recordOncePerRun(UsageEvent.CALENDAR_CUE_FIRED)
                        cueHost.blankOutputs()
                    },
                ),
                operatorLive = {
                    isOperatorLive(
                        presenterManager.lastLive.value,
                        engineLiveItem?.let { liveDurationLog.showing(it) },
                    )
                },
            )
        }
        LaunchedEffect(Unit) { cueRunner.run() }

        // Nothing is on screen any more, so whatever was is no longer being
        // timed -- see LiveDurationLog.
        val liveMode = presenterManager.slideContent.value
        LaunchedEffect(liveMode) {
            if (liveMode == Presenting.NONE) {
                liveDurationLog.wentBlank()
                engineLiveItem = null
            }
        }

        // A row set to run for its *own* length has no number for the engine to
        // count -- the video itself decides when it is over. MediaViewModel
        // already reports that (it is what clears the output), so the row's
        // "at end" action hangs off the same signal.
        LaunchedEffect(mediaViewModel.mediaFinished) {
            if (mediaViewModel.mediaFinished) cueRunner.liveItemFinished()
        }
        // A row added, removed or cleared changes what the notice and the save
        // button say at once, not at the loader's next minute.
        LaunchedEffect(serviceAutoLoader, currentScheduleItems) { serviceAutoLoader.refresh() }
        // And a service added or edited in the Calendar Manager -- or synced
        // from a phone -- changes them as soon as it is saved.
        LaunchedEffect(serviceAutoLoader, calendarFolder) {
            CalendarFileWatcher(calendarFolder).run(
                onChanged = { serviceAutoLoader.refresh(reread = true) },
            )
        }
        var calendarSyncWasOn by remember { mutableStateOf(appSettings.calendarSync.enabled) }
        LaunchedEffect(appSettings.calendarSync.enabled) {
            val on = appSettings.calendarSync.enabled
            if (on && !calendarSyncWasOn) UsageEvents.record(UsageEvent.CALENDAR_SYNC_ENABLED)
            calendarSyncWasOn = on
        }
        LaunchedEffect(calendarFolder, appSettings.calendarSync.enabled) {
            // A folder chosen in Settings starts from what the app data folder
            // holds, once, so the calendar does not vanish on the switch.
            withContext(Dispatchers.IO) { seedCalendarFolder(AppDataDir.resolve(), calendarFolder) }
            // Phones plan all week; their edits are pulled and merged before the
            // auto-loader looks, so it loads this week's plan.
            calendarSync.syncOnStartup()
            launch { calendarSync.run() }
            serviceAutoLoader.run()
        }
    }
}

/** Commands from the Companion server and the lower-third sequencer, applied to the outputs. */
@Composable
internal fun MainWindowScope.ServerCommandWiring() {
    with(root) {
        LaunchedEffect(Unit) {
            companionServer.onSelectSongSection.collect { req ->
                val sections = presenterManager.allLyricSections.value
                val section = sections.getOrNull(req.section) ?: return@collect
                // Positions first: setLyricSection reports the change, and reads them.
                presenterManager.setSongDisplaySectionIndex(req.section)
                presenterManager.setSongDisplayLineIndex(remoteSongLineIndex(req.lineIndex))
                presenterManager.setLyricSection(section)
                if (shouldSwitchToLyrics(presenterManager.slideContent.value)) {
                    presenterManager.setPresentingMode(Presenting.LYRICS)
                    presenterManager.setShowPresenterWindow(true)
                }
            }
        }

        LaunchedEffect(Unit) {
            companionServer.onClear.collect {
                mediaViewModel.pause()
                presenterManager.requestClearDisplay()
            }
        }
        LaunchedEffect(Unit) {
            companionServer.onClearLayer.collect { name ->
                layerForName(name)?.let(presenterManager::clearLayer)
            }
        }
        LaunchedEffect(Unit) {
            companionServer.onTake.collect { presenterManager.previewBus.take() }
        }

        LaunchedEffect(Unit) {
            LowerThirdSequencer.onShow.collect { req ->
                presenterManager.setLottieContent(
                    req.json, req.pauseAtFrame, req.pauseFrame, req.pauseDurationMs, req.name
                )
                presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
                presenterManager.setShowPresenterWindow(true)
            }
        }
        val overlayEndClearsDisplay by rememberUpdatedState(appSettings.projectionSettings.overlayEndClearsDisplay)
        LaunchedEffect(Unit) {
            // Only while the lower third is still up: the sequence runs on its own clock, and by the
            // time it ends the operator may have moved on.
            LowerThirdSequencer.onClear.collect {
                presenterManager.overlayFinished(Presenting.LOWER_THIRD, overlayEndClearsDisplay)
            }
        }
        LaunchedEffect(Unit) {
            companionServer.onQADisplay.collect { question ->
                if (question != null) {
                    presenterManager.setDisplayedQuestion(question)
                    presenterManager.setShowQRCodeOnDisplay(false)
                    presenterManager.setPresentingMode(Presenting.QA)
                } else {
                    presenterManager.setDisplayedQuestion(null)
                    presenterManager.setPresentingMode(Presenting.NONE)
                }
            }
        }
    }
}

/** Transpose, Bible hold and what the outputs show, sent back out to remote clients. */
@Composable
internal fun MainWindowScope.ServerBroadcastWiring() {
    with(root) {
        // A musician view's transpose presses, and the state they produce sent
        // back to every musician view: the app is the one owner of the offset,
        // so a press on the desktop tile reaches the tablets the same way.
        LaunchedEffect(Unit) {
            companionServer.onBrowserSourceTranspose.collect { command ->
                if (command.reset) {
                    presenterManager.setBrowserSourceTranspose(command.index, 0)
                } else {
                    presenterManager.stepBrowserSourceTranspose(command.index, command.delta)
                }
            }
        }
        LaunchedEffect(Unit) {
            presenterManager.browserSourceTranspose.collect { transposes ->
                companionServer.updateBrowserSourceTranspose(transposes)
            }
        }

        LaunchedEffect(Unit) {
            companionServer.onBibleHold.collect { hold ->
                presenterManager.setBibleHold(hold)
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { presenterManager.lastLive.value }
                .collect { mode ->
                    if (shouldBroadcastDisplayCleared(mode)) {
                        companionServer.broadcastDisplayCleared()
                    }
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { presenterManager.songDisplaySectionIndex.value }
                .collect { index ->
                    if (shouldBroadcastSongSection(presenterManager.slideContent.value)) {
                        companionServer.broadcastSongSectionSelected(index)
                    }
                }
        }

        val clearDisplayTitle by rememberUpdatedState(
            stringResource(Res.string.tooltip_clear_display)
        )
        LaunchedEffect(Unit) {
            companionServer.onInstantAction.collect { action ->
                val type = remoteActionType(action.actionType)
                remoteActivityNotifications.add(
                    RemoteActivityNotification(
                        type = type,
                        title = remoteActivityTitle(type, clearDisplayTitle, action.title.text()),
                        detail = action.detail.text(),
                        clientId = action.clientId,
                        clientLabel = remoteClientManager.getLabel(action.clientId)
                    )
                )
            }
        }
    }
}

