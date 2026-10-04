package org.churchpresenter.app.churchpresenter

import org.churchpresenter.bibletab.selectVerseByBookId
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.models.announcementPresetItem
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.app.churchpresenter.tabs.AppAnnouncementsTab
import org.churchpresenter.canvas.CanvasTab
import org.churchpresenter.dictionary.DictionaryTab
import org.churchpresenter.app.churchpresenter.tabs.AppLowerThirdTab
import org.churchpresenter.media.tabs.MediaTab
import org.churchpresenter.slides.tabs.PicturesTab
import org.churchpresenter.slides.tabs.PresentationTab
import org.churchpresenter.app.churchpresenter.dialogs.PresentationRemoteDialog
import org.churchpresenter.media.composables.isVlcArchMismatch
import org.churchpresenter.media.composables.isVlcAvailable
import org.churchpresenter.media.composables.isVlcLoadFailed
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import java.util.UUID
import org.churchpresenter.app.churchpresenter.tabs.AppWebTab

/*
 * The content tabs as the main screen composes them, one pane each, wired to its state and the
 * Schedule's actions.
 */

@Composable
internal fun MainDesktopScope.PicturesTabPane() {
    PicturesTab(
        modifier = Modifier.fillMaxSize(),
        hostWindow = hostWindow,
        appSettings = appSettings,
        onAddToSchedule = { folderPath, folderName, imageCount ->
            currentScheduleActions.addPicture(folderPath, folderName, imageCount)
        },
        onSavePreset = { folderPath, folderName, imageCount ->
            state.presetToSave = ScheduleItem.PictureItem(
                UUID.randomUUID().toString(), folderPath, folderName, imageCount,
            )
        },
        onInstanceLinkSendProject = link.sendProject,
        onInstanceLinkSendNextPicture = link.sendNextPicture,
        onInstanceLinkSendPreviousPicture = link.sendPreviousPicture,
        instanceLinkFetchPictureImageBytes = link.fetchPictureImageBytes,
        selectedPictureItem = state.selectedPictureItem,
        selectedPictureItemVersion = state.selectedPictureItemVersion,
        presenterManager = presenterManager.slidesOutput,
        onSettingsChange = onSettingsChange,
        viewModel = picturesViewModel
    )
}

@Composable
internal fun MainDesktopScope.PresentationTabPane() {
    PresentationTab(
        modifier = Modifier.fillMaxSize(),
        hostWindow = hostWindow,
        appSettings = appSettings,
        onAddToSchedule = { filePath, fileName, slideCount, fileType ->
            currentScheduleActions.addPresentation(filePath, fileName, slideCount, fileType)
        },
        onSavePreset = { filePath, fileName, slideCount, fileType ->
            state.presetToSave = ScheduleItem.PresentationItem(
                UUID.randomUUID().toString(), filePath, fileName, slideCount, fileType,
            )
        },
        onInstanceLinkSendProject = link.sendProject,
        onInstanceLinkSendNextSlide = link.sendNextSlide,
        onInstanceLinkSendPreviousSlide = link.sendPreviousSlide,
        instanceLinkFetchPresentationSlideBytes = link.fetchPresentationSlideBytes,
        selectedPresentationItem = state.selectedPresentationItem,
        selectedPresentationItemVersion = state.selectedPresentationItemVersion,
        presenterManager = presenterManager.slidesOutput,
        onSlidesLoaded = publish.onPresentationSlidesLoaded,
        onSettingsChange = onSettingsChange,
        viewModel = presentationViewModel,
        remoteDialog = { onDismiss ->
            PresentationRemoteDialog(
                settings = appSettings,
                onSettingsChange = onSettingsChange,
                serverUrl = web.serverUrl,
                apiKeyEnabled = appSettings.serverSettings.apiKeyEnabled,
                apiKey = appSettings.serverSettings.apiKey,
                tunnelStatus = web.tunnelStatus,
                tunnelUrl = web.tunnelUrl,
                presentationDisplayUrl = web.presentationDisplayUrl,
                onPresentationDisplayUrlChanged = web.onPresentationDisplayUrlChanged,
                onStartTunnel = web.onStartTunnel,
                onStopTunnel = web.onStopTunnel,
                onDismiss = onDismiss,
            )
        },
        presentationFrozen = web.presentationFrozen,
        onFreezeToggle = web.onFreezeToggle,
        onClearPresentation = web.onClearPresentation,
        vlcAvailable = isVlcAvailable,
        vlcArchMismatch = isVlcArchMismatch,
        vlcLoadFailed = isVlcLoadFailed,
    )
}

@Composable
internal fun MainDesktopScope.MediaTabPane() {
    MediaTab(
        modifier = Modifier.fillMaxSize(),
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        onAddToSchedule = { mediaUrl, mediaTitle, mediaType, subtitleUrl ->
            currentScheduleActions.addMedia(mediaUrl, mediaTitle, mediaType, subtitleUrl)
        },
        onSavePreset = { mediaUrl, mediaTitle, mediaType ->
            state.presetToSave = ScheduleItem.MediaItem(
                UUID.randomUUID().toString(), mediaUrl, mediaTitle, mediaType,
            )
        },
        selectedMediaItem = state.selectedMediaItem,
        selectedMediaItemVersion = state.selectedMediaItemVersion,
        presenterManager = presenterManager.mediaOutput,
        instanceLinkMediaStreamUrl = link.mediaStreamUrl,
        onInstanceLinkSendProject = link.sendProject,
        previewOutputPicker = { pickerModifier ->
            PreviewOutputPicker(
                appSettings, Constants.PREVIEW_TAB_MEDIA, Presenting.MEDIA, onSettingsChange, pickerModifier,
            )
            rememberPreviewOutput(appSettings, Constants.PREVIEW_TAB_MEDIA, Presenting.MEDIA)
        },
    )
}

@Composable
internal fun MainDesktopScope.LowerThirdTabPane() {
    AppLowerThirdTab(
        modifier = Modifier.fillMaxSize(),
        appSettings = appSettings,
        selectedLowerThirdItem = state.selectedLowerThirdItem,
        selectedLowerThirdItemVersion = state.selectedLowerThirdItemVersion,
        onSettingsChange = onSettingsChange,
        onAddToSchedule = { presetId, presetLabel, pauseAtFrame, pauseDurationMs ->
            state.scheduleActions.addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs)
        },
        onGoLive = { json, pauseAtFrame, pauseFrame, pauseDurationMs, presetName ->
            presenterManager.setLottieContent(json, pauseAtFrame, pauseFrame, pauseDurationMs, presetName)
            presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
            presenterManager.setShowPresenterWindow(true)
        },
        onOpenLottieGen = { outputDir, onSaved -> onOpenLottieGen(outputDir, onSaved) }
    )
}

@Composable
internal fun MainDesktopScope.AnnouncementsTabPane() {
    AppAnnouncementsTab(
        modifier = Modifier.fillMaxSize(),
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        presenterManager = presenterManager,
        onAddToSchedule = { settings ->
            val isTimer = settings.timerMode != Constants.TIMER_MODE_DURATION ||
                settings.timerHours > 0 || settings.timerMinutes > 0 || settings.timerSeconds > 0
            currentScheduleActions.addAnnouncement(
                settings.text,
                settings.textColor,
                settings.backgroundColor,
                settings.fontSize,
                settings.fontType,
                settings.bold,
                settings.italic,
                settings.underline,
                settings.shadow,
                settings.shadowColor,
                settings.shadowSize,
                settings.shadowOpacity,
                settings.horizontalAlignment,
                settings.position,
                settings.animationType,
                settings.animationDuration,
                settings.loopCount,
                isTimer,
                settings.timerHours,
                settings.timerMinutes,
                settings.timerSeconds,
                settings.timerTextColor,
                settings.timerExpiredText,
                settings.timerMode,
                settings.targetHour,
                settings.targetMinute,
                settings.targetSecond,
                settings.liveClockFormat,
                settings.backdrop,
                settings.outline,
            )
        },
        onSavePreset = { settings -> state.presetToSave = announcementPresetItem(settings) }
    )
}

@Composable
internal fun MainDesktopScope.WebTabPane() {
    AppWebTab(
        modifier = Modifier.fillMaxSize(),
        presenterManager = presenterManager,
        selectedWebsiteItem = state.selectedWebsiteItem,
        selectedWebsiteItemVersion = state.selectedWebsiteItemVersion,
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        onAddToSchedule = { url, title ->
            currentScheduleActions.addWebsite(url, title)
        },
        onUpdateScheduleTitle = { url, title ->
            currentScheduleActions.updateWebsiteTitle(url, title)
        }
    )
}

@Composable
internal fun MainDesktopScope.CanvasTabPane() {
    CanvasTab(
        modifier = Modifier.fillMaxSize(),
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        onPresentScene = { scene ->
            presenterManager.setActiveScene(scene)
            presenterManager.setPresentingMode(Presenting.CANVAS)
            presenterManager.setShowPresenterWindow(true)
        },
        sceneViewModel = sceneViewModel,
        onAddToSchedule = { sceneId, sceneName ->
            currentScheduleActions.addScene(sceneId, sceneName)
        },
        onSavePreset = { sceneId, sceneName ->
            state.presetToSave = ScheduleItem.SceneItem(
                id = UUID.randomUUID().toString(),
                sceneId = sceneId,
                sceneName = sceneName,
            )
        },
        dialogDismissSignal = dialogDismissSignal
    )
}

@Composable
internal fun MainDesktopScope.DictionaryTabPane() {
    DictionaryTab(
        modifier = Modifier.fillMaxSize(),
        viewModel = dictionaryViewModel,
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        onAddToSchedule = { number, word, transliteration, definition ->
            state.scheduleActions.addDictionary(number, word, transliteration, definition)
        },
        onGoLive = { entry ->
            presenterManager.setDisplayedDictionaryEntry(entry)
            presenterManager.setShowPresenterWindow(true)
            live.presenting(Presenting.DICTIONARY)
        },
        getVerseText = { bookId, chapter, verse ->
            val bible = dictionaryViewModel.dictBible ?: bibleViewModel.primaryBible.value
            bible?.getVerseDetails(bookId, chapter, verse)?.second
        },
        getBookName = { bookId ->
            val bible = dictionaryViewModel.dictBible ?: bibleViewModel.primaryBible.value
            bible?.getBookName(bookId)
        },
        onWordClick = { strongsNumber ->
            UsageEvents.record(UsageEvent.STRONGS_LOOKUP)
            dictionaryViewModel.selectByNumber(strongsNumber)
        },
        onVerseClick = { bookId, chapter, verseNumber ->
            selectTab(Tabs.BIBLE)
            bibleViewModel.selectVerseByBookId(bookId, chapter, verseNumber)
        },
    )
}
