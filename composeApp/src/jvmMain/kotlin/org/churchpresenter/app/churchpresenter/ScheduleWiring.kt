package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.schedule.ScheduleTab
import org.churchpresenter.schedule.ScheduleToolbarIconSize
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.timer_expired
import org.jetbrains.compose.resources.stringResource

/*
 * The Schedule tab as the main screen wires it: to its view model, to the tabs a row opens in, and to
 * the live output a row is put on. The sidebar it sits in only places it.
 */

/** The Schedule rows' go-live handlers over this screen's state, tabs and live output. */
internal fun MainDesktopScope.scheduleRowHandlers() = ScheduleRowHandlers(
    state = state,
    selectTab = ::selectTab,
    presenting = live.presenting,
    presenterManager = presenterManager,
    onSettingsChange = onSettingsChange,
    lowerThirdFolder = { appSettings.streamingSettings.lowerThirdFolder },
)

@Composable
internal fun MainDesktopScope.ScheduleTabPane() {
    // Keep a stable reference to onScheduleActionsReady so the onActionsReady lambda always
    // hands the menu the latest callback.
    val currentOnScheduleActionsReady by rememberUpdatedState(publish.onScheduleActionsReady)
    val timerExpiredDefaultLabel = stringResource(Res.string.timer_expired)
    val rows = remember(this) { scheduleRowHandlers() }
    ScheduleTab(
        scheduleViewModel = scheduleViewModel,
        onPresenting = live.presenting,
        onAddLabel = { state.showAddLabelDialog = true },
        upcomingServiceLoad = service.upcomingServiceLoad,
        onLoadServiceNow = service.onLoadServiceNow,
        scheduleService = service.scheduleService,
        onSaveScheduleToCalendar = service.onSaveScheduleToCalendar,
        onAddScheduleToCalendar = service.onAddScheduleToCalendar,
        onPresentBible = rows::presentBible,
        onPresentSong = rows::presentSong,
        onPresentPresentation = rows::presentPresentation,
        onPresentPictures = rows::presentPictures,
        onPresentMedia = rows::presentMedia,
        onPresentAnnouncement = { item -> rows.presentAnnouncement(item, timerExpiredDefaultLabel) },
        onPresentLowerThird = rows::presentLowerThird,
        onPresentWebsite = rows::presentWebsite,
        onPresentDictionary = rows::presentDictionary,
        onPresentCue = service.onPresentCue,
        onPresentScene = { item -> presentScene(item.sceneId) },
        onItemClick = this::openScheduleItem,
        onEditLabel = { labelItem ->
            state.editingLabelItem = labelItem
            state.showAddLabelDialog = true
        },
        onActionsReady = { actions ->
            state.scheduleActions = actions
            currentOnScheduleActionsReady(
                scheduleActionsFrom(actions, presentScene = this::presentScene, playSlideshow = this::playSlideshow)
            )
        },
        onSelectedItemChanged = { id ->
            publish.onScheduleItemSelected(id)
        },
        onScheduleChanged = publish.onScheduleChanged,
        itemZoomPercent = appSettings.scheduleItemZoomPercent,
        onItemZoomChange = { percent ->
            onSettingsChange { settings -> settings.copy(scheduleItemZoomPercent = percent) }
        },
        legacyRowActions = appSettings.scheduleLegacyRowActions,
        onLegacyRowActionsChange = { legacy ->
            onSettingsChange { settings -> settings.copy(scheduleLegacyRowActions = legacy) }
        },
        toolbarIconSize = ScheduleToolbarIconSize.fromName(appSettings.scheduleToolbarIconSize),
        onToolbarIconSizeChange = { size ->
            onSettingsChange { settings -> settings.copy(scheduleToolbarIconSize = size.name) }
        },
        hiddenToolbarButtons = appSettings.hiddenScheduleButtons,
        onToggleToolbarButton = { button ->
            onSettingsChange { settings ->
                settings.copy(
                    hiddenScheduleButtons =
                        toggleHiddenScheduleButton(settings.hiddenScheduleButtons, button)
                )
            }
        },
        planningCenterImport = { isVisible, onDismiss ->
            PlanningCenterImport(isVisible = isVisible, onDismiss = onDismiss)
        }
    )
}

/** Puts a Canvas scene live and shows it in its tab — from a Schedule row or the menu. */
internal fun MainDesktopScope.presentScene(sceneId: String) {
    sceneViewModel.selectScene(sceneId)
    presenterManager.setActiveScene(sceneViewModel.scenes.find { it.id == sceneId })
    selectTab(Tabs.CANVAS)
    live.presenting(Presenting.CANVAS)
}

/** Starts a Schedule row's slideshow in the player that shows it, for [plays] passes. */
private fun MainDesktopScope.playSlideshow(item: ScheduleItem, plays: Int) {
    when (item) {
        is ScheduleItem.MediaItem ->
            mediaViewModel?.cue?.requestPlayback(plays, item.mediaUrl)
        is ScheduleItem.PictureItem ->
            picturesViewModel.requestPlayback(plays, item.folderPath)
        is ScheduleItem.PresentationItem ->
            presentationViewModel.requestPlayback(plays, item.filePath)
        else -> Unit
    }
}

/** A row clicked in the Schedule: opens it in the tab it belongs to, without going live. */
internal fun MainDesktopScope.openScheduleItem(item: ScheduleItem) {
    tabForScheduleItem(item)?.let { selectTab(it) }
    if (state.select(item)) return
    when (item) {
        is ScheduleItem.LabelItem -> {
            state.editingLabelItem = item
            state.showAddLabelDialog = true
        }
        is ScheduleItem.AnnouncementItem -> {
            onSettingsChange { settings -> withAnnouncementFrom(settings, item) }
        }
        is ScheduleItem.SceneItem -> {
            sceneViewModel.selectScene(item.sceneId)
        }
        is ScheduleItem.DictionaryItem -> {
            dictionaryViewModel.selectByNumber(item.number)
        }
        // Planned time that never goes on screen; nothing to open.
        else -> Unit
    }
}
