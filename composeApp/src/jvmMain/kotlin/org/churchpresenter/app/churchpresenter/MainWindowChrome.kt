package org.churchpresenter.app.churchpresenter

import org.churchpresenter.server.broadcastFreezeChange
import org.churchpresenter.server.broadcastSlideChange
import org.churchpresenter.server.clearPresentationState
import org.churchpresenter.server.getImageFile
import org.churchpresenter.server.updateBible
import org.churchpresenter.server.updateBibleFilePaths
import org.churchpresenter.server.updatePictures
import org.churchpresenter.server.updatePresentation
import org.churchpresenter.server.updateSchedule
import org.churchpresenter.server.updateSecondaryBibleFilePath
import org.churchpresenter.server.updateSongs
import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import org.churchpresenter.sharedui.utils.DevFlags
import kotlinx.coroutines.launch
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.UpdateChecker
import java.io.File
import java.util.Locale
import org.churchpresenter.app.churchpresenter.remote.shouldUseRemoteContent
import org.churchpresenter.sharedui.utils.UrlOpener
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.settings.InstanceLinkSettings
import org.churchpresenter.calendar.ScheduleServiceLink

/** The menu bar. */
@Composable
internal fun MainWindowScope.AppMenuBar() {
    with(root) {
        frame.NavigationTopBar(
            currentTheme = theme,
            hasCustomTheme = appSettings.customThemeAccent.isNotBlank(),
            onCustomizeTheme = { showCustomizeThemeDialog = true },
            onAbout = { showAboutDialog = true },
            onContactUs = { showContactDialog = true },
            onGettingStarted = { showSetupWizard = true },
            onStatistics = { showStatisticsDialog = true },
            onConnectToInstance = { showInstanceLinkDialog = true },
            onDisconnectInstance = { instanceLinkViewModel.disconnect() },
            isInstanceLinkConnected = isInstanceLinkActive(
                instanceLinkViewModel.connectionStatus.collectAsState().value
            ),
            onConverter = {
                converterInitialTab = ConverterTab.BIBLES
                showConverterWindow = true
            },
            onSongLibrary = { showSongLibraryWindow = true },
            onCalendar = { showCalendarWindow = true },
            onHelp = { UrlOpener.open("https://churchpresenter.org/wiki") },
            onHowToBlog = { UrlOpener.open("https://churchpresenter.org/blog") },
            onCheckForUpdates = {
                coroutineScope.launch {
                    pendingUpdateResult = UpdateChecker.checkForUpdate(
                        includePrereleases = appSettings.participateInPrereleases
                    )
                    pendingUpdateCheckWasManual = true
                    appSettings = appSettings.copy(
                        lastUpdateCheckTimestamp = System.currentTimeMillis()
                    )
                    settingsManager.saveSettings(appSettings)
                }
            },
            onKeyboardShortcuts = { showKeyboardShortcutsDialog = true },
            theme = {
                appSettings = appSettings.copy(theme = it.toString())
                theme = it
                settingsManager.saveSettings(appSettings)
            },
            onLanguageChange = { language ->
                currentLanguage = language
                appSettings = appSettings.copy(language = language.code)
                settingsManager.saveSettings(appSettings)
                Locale.setDefault(Locale.forLanguageTag(language.code))
            },
            onSettings = { openOptionsDialog(0) },
            onExit = { exitApplication() },
            onAddToSchedule = { },
            onNewSchedule = { currentScheduleActions.newSchedule() },
            onOpenSchedule = { currentScheduleActions.openSchedule() },
            onSaveSchedule = { currentScheduleActions.saveSchedule() },
            onSaveScheduleAs = { currentScheduleActions.saveScheduleAs() },
            onCloseSchedule = { currentScheduleActions.newSchedule() },
            onRemoveFromSchedule = {
                selectedScheduleItemId?.let {
                    currentScheduleActions.removeSelected()
                    selectedScheduleItemId = null
                }
            },
            onClearSchedule = {
                currentScheduleActions.clearSchedule()
                selectedScheduleItemId = null
            },
            showDeveloperMenu = shouldShowDeveloperMenu(
                BuildConfig.IS_RELEASE, DevFlags.forceDevWindow, developerMenuUnlocked,
            ),
            isPresenterWindowVisible = presenterManager.showPresenterWindow.value,
            onSetPresenterWindowVisible = { presenterManager.setShowPresenterWindow(it) },
            isDevWindowAlwaysOnTop = presenterManager.devWindowAlwaysOnTop.value,
            onSetDevWindowAlwaysOnTop = { presenterManager.setDevWindowAlwaysOnTop(it) },
            onOpenStyleEditor = { showStyleEditorWindow = true },
            onOpenMemoryMonitor = { showMemoryMonitorWindow = true },
            onOpenStoryPrompt = { showStoryPrompt = true },
        )
    }
}

/** The main screen, wired to the app: the live output, the calendar, the server and Instance Link. */
@Composable
internal fun MainWindowScope.MainDesktopHost() {
    with(root) {
        val upcomingServiceLoad by serviceAutoLoader.upcoming.collectAsState()
        val scheduleService by serviceAutoLoader.scheduleService.collectAsState()
        val instanceLinkStatus = instanceLinkViewModel.connectionStatus.collectAsState().value
        val instanceLinkIsControllerConnected =
            isControllerConnected(instanceLinkStatus, appSettings.instanceLink.role)
        val instanceLinkUsesRemoteContent =
            shouldUseRemoteContent(instanceLinkStatus, appSettings.instanceLink.role)
        MainDesktop(
            hostWindow = window,
            appSettings = appSettings,
            livePreviewAppSettings = effectiveAppSettings,
            activeQuickBackground = activeQuickBackground,
            onQuickBackgroundPicked = { activeQuickBackground = it },
            presenterManager = presenterManager,
            statisticsManager = statisticsManager,
            verseSequenceLog = verseSequenceLog,
            onShowSettings = { openOptionsDialog(0) },
            onShowBackgroundSettings = { openOptionsDialog(OPTIONS_TAB_BACKGROUND) },
            onSettingsChange = { updateFn ->
                appSettings = updateFn(appSettings)
                settingsManager.saveSettings(appSettings)
            },
            theme = theme,
            qaManager = qaManager,
            onOpenLottieGen = { outputDir, onSaved ->
                if (isUsableOutputDir(outputDir)) {
                    lottieGenOutputDir = File(outputDir)
                    lottieGenOnFileSaved = onSaved
                    showLottieGenWindow = true
                } else {
                    javax.swing.JOptionPane.showMessageDialog(
                        null,
                        "Please set a Lower Third folder in Settings first.",
                        "No Folder Configured",
                        javax.swing.JOptionPane.WARNING_MESSAGE
                    )
                }
            },
            sttManager = sttManager,
            dialogDismissSignal = dialogDismissSignal,
            companionSatelliteViewModel = companionSatelliteViewModel,
            onRequestDeveloperMenuUnlock = { developerMenuUnlocked = true },
            live = liveOutputCallbacks(),
            service = servicePlanLink(upcomingServiceLoad, scheduleService),
            publish = mainDesktopPublishers(),
            flows = remoteControlFlows(),
            link = instanceLinkBridge(instanceLinkIsControllerConnected, instanceLinkUsesRemoteContent),
            web = webAccessState(),
        )
    }
}

private fun MainWindowScope.liveOutputCallbacks(): LiveOutputCallbacks = with(root) {
    LiveOutputCallbacks(
        onRowWentLive = { item -> liveDurationLog.wentLive(item) },
        onVerseSelected = { verses -> presenterManager.previewBus.forVerses(verses).setSelectedVerses(verses) },
        // Line mode used to push the section straight to the outputs from
        // here. That put the words on screen behind the transition driver's
        // back, so the Lottie band animated a swap for text that had
        // already changed. Every mode now goes through the driver.
        // Through the Preview bus: the song on air is stepped, another one cued -- see forSong.
        onSongItemSelected = { section -> presenterManager.previewBus.forSong(section).setLyricSection(section) },
        onAllSectionsChanged = { presenterManager.previewBus.forSong(it.firstOrNull()).setAllLyricSections(it) },
        onSectionIndexChanged = { presenterManager.previewBus.songStepTarget.setSongDisplaySectionIndex(it) },
        onLineIndexChanged = { presenterManager.previewBus.songStepTarget.setSongDisplayLineIndex(it) },
        presenting = { mode ->
            presenterManager.previewBus.present(mode)
            if (shouldShowPresenterWindowFor(mode)) {
                presenterManager.setShowPresenterWindow(true)
            }
        },
    )
}

private fun MainWindowScope.servicePlanLink(
    upcomingServiceLoad: UpcomingLoad?,
    scheduleService: ScheduleServiceLink?,
): ServicePlanLink = with(root) {
    ServicePlanLink(
        onPresentCue = fireScheduleCue,
        upcomingServiceLoad = upcomingServiceLoad,
        onLoadServiceNow = { replace ->
            upcomingServiceLoad?.let { upcoming ->
                coroutineScope.launch {
                    serviceAutoLoader.loadNow(upcoming.serviceId, replace)
                }
            }
        },
        scheduleService = scheduleService,
        onSaveScheduleToCalendar = {
            coroutineScope.launch { serviceAutoLoader.saveScheduleToService() }
        },
        onAddScheduleToCalendar = {
            calendarNewServiceFromSchedule++
            showCalendarWindow = true
        },
        typicalSongSeconds = { song ->
            liveDurationLog.median(
                ScheduleItem.SongItem(
                    id = song.songId,
                    songNumber = song.number.toIntOrNull() ?: 0,
                    title = song.title,
                    songbook = song.songbook,
                    songId = song.songId,
                )
            )
        },
    )
}

private fun MainWindowScope.mainDesktopPublishers(): MainDesktopPublishers = with(root) {
    MainDesktopPublishers(
        onScheduleActionsReady = { scheduleActions = it },
        onScheduleItemSelected = { itemId -> selectedScheduleItemId = itemId },
        onSongsLoaded = { songs -> companionServer.updateSongs(songs) },
        onScenesChanged = { scenes -> scenesForInstanceLink = scenes },
        onBibleLoaded = { bible, translation ->
            primaryBibleForInstanceLink = bible
            companionServer.updateBible(
                bible,
                translation,
                filePath = bibleFilePath(
                    appSettings.bibleSettings.storageDirectory,
                    translation
                )
            )
        },
        onScheduleChanged = { items ->
            currentScheduleItems = items
            companionServer.updateSchedule(items)
        },
        onPresentationSlidesLoaded = { id, filePath, fileName, fileType, slides, notes ->
            companionServer.updatePresentation(
                id, filePath, fileName, fileType, slides, notes,
            )
        },
        onPicturesLoaded = { folderId, folderName, folderPath, imageFiles ->
            companionServer.updatePictures(folderId, folderName, folderPath, imageFiles)
        },
        onSlideChanged = { id, index, total, isPlaying ->
            companionServer.broadcastSlideChange(id, index, total, isPlaying)
        },
    )
}

private fun MainWindowScope.remoteControlFlows(): RemoteControlFlows = with(root) {
    RemoteControlFlows(
        selectPictureImageFlow = kotlinx.coroutines.flow.flow {
            companionServer.onSelectPicture.collect { req ->
                emit(req.folderId to req.index)
            }
        },
        resolveImageFile = { folderId, index ->
            companionServer.getImageFile(folderId, index)
        },
        selectSlideFlow = kotlinx.coroutines.flow.flow {
            companionServer.onSelectSlide.collect { req ->
                emit(req.id to req.index)
            }
        },
        selectBibleVerseFlow = kotlinx.coroutines.flow.flow {
            companionServer.onSelectBibleVerse.collect { req ->
                emit(req)
            }
        },
        remoteSelectSongFlow = remoteSelectSongFlow,
        remoteSelectPictureFlow = remoteSelectPictureFlow,
        remoteSelectPresentationFlow = remoteSelectPresentationFlow,
        remoteSelectMediaFlow = remoteSelectMediaFlow,
        nextPictureFlow = kotlinx.coroutines.flow.flow {
            companionServer.onNextPicture.collect { emit(Unit) }
        },
        previousPictureFlow = kotlinx.coroutines.flow.flow {
            companionServer.onPreviousPicture.collect { emit(Unit) }
        },
        nextSlideFlow = kotlinx.coroutines.flow.flow {
            companionServer.onNextSlide.collect { emit(Unit) }
        },
        previousSlideFlow = kotlinx.coroutines.flow.flow {
            companionServer.onPreviousSlide.collect { emit(Unit) }
        },
        uploadPresentationFlow = kotlinx.coroutines.flow.flow {
            companionServer.onPresentationUploaded.collect { file ->
                emit(file)
            }
        },
        remotePresentationPlayPauseFlow = companionServer.onPresentationPlayPause,
        remotePresentationLoopToggleFlow = companionServer.onPresentationLoopToggle,
        remotePresentationGotoFlow = companionServer.onPresentationGoto,
    )
}

@Composable
private fun MainWindowScope.instanceLinkBridge(
    instanceLinkIsControllerConnected: Boolean,
    instanceLinkUsesRemoteContent: Boolean,
): InstanceLinkBridge = with(root) {
    InstanceLinkBridge(
        connectionStatus = instanceLinkViewModel.connectionStatus.collectAsState().value,
        nextRetryAtMs = instanceLinkViewModel.nextRetryAtMs.collectAsState().value,
        bibleUpdatedSignal =
            instanceLinkViewModel.bibleUpdatedSignal.collectAsState().value,
        secondaryBibleUpdatedSignal =
            instanceLinkViewModel.secondaryBibleUpdatedSignal.collectAsState().value,
        followingHost = appSettings.instanceLink.primaryHost,
        followerCount =
            companionServer.connectedInstanceLinkFollowers.collectAsState().value.size,
        onConnect = {
            val link = appSettings.instanceLink
            setInstanceLinkEnabled(true)
            instanceLinkViewModel.connect(
                link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                link.reconnectDelayMs.toLong()
            )
        },
        onDisconnect = {
            setInstanceLinkEnabled(false)
            instanceLinkViewModel.disconnect()
        },
        remoteSchedule = instanceLinkViewModel.remoteSchedule.collectAsState().value,
        remoteSongCatalog = instanceLinkViewModel.remoteSongCatalog.collectAsState().value,
        fetchSongDetail = { number, songbook ->
            instanceLinkViewModel.fetchSongDetail(number, songbook)
        },
        fetchBibleFile = { instanceLinkViewModel.fetchBibleFile() },
        bibleSyncMode = appSettings.instanceLink.bibleSyncMode,
        fetchSecondaryBibleFile = {
            instanceLinkViewModel.fetchSecondaryBibleFile()
        },
        fetchBibleTranslations = { instanceLinkViewModel.fetchBibleTranslations() },
        onSecondaryBibleFilePathChanged = { path ->
            companionServer.updateSecondaryBibleFilePath(path)
        },
        onBibleFilePathsChanged = { paths ->
            companionServer.updateBibleFilePaths(paths)
        },
        sendAddToSchedule = if (canPushToSchedule(appSettings.instanceLink)) {
            { item -> instanceLinkViewModel.sendAddToSchedule(item) }
        } else null,
        sendRemoveFromSchedule = if (canPushToSchedule(appSettings.instanceLink)) {
            { id -> instanceLinkViewModel.sendRemoveFromSchedule(id) }
        } else null,
        role = appSettings.instanceLink.role,
        sendProject = if (instanceLinkIsControllerConnected) {
            { item -> instanceLinkViewModel.sendProject(item) }
        } else null,
        sendVerse = if (instanceLinkIsControllerConnected) {
            { bookName, chapter, verseNumber, verseText, verseRange ->
                instanceLinkViewModel.sendSelectBibleVerse(bookName, chapter, verseNumber, verseText, verseRange)
            }
        } else null,
        sendSongSection = if (instanceLinkIsControllerConnected) {
            { number, section, lineIndex ->
                instanceLinkViewModel.sendSelectSongSection(number, section, lineIndex)
            }
        } else null,
        sendClear = if (instanceLinkIsControllerConnected) {
            { instanceLinkViewModel.sendClear() }
        } else null,
        sendBibleHold = if (instanceLinkIsControllerConnected) {
            { hold -> instanceLinkViewModel.sendBibleHold(hold) }
        } else null,
        sendNextPicture = if (instanceLinkIsControllerConnected) {
            { instanceLinkViewModel.sendNextPicture() }
        } else null,
        sendPreviousPicture = if (instanceLinkIsControllerConnected) {
            { instanceLinkViewModel.sendPreviousPicture() }
        } else null,
        sendNextSlide = if (instanceLinkIsControllerConnected) {
            { instanceLinkViewModel.sendNextSlide() }
        } else null,
        sendPreviousSlide = if (instanceLinkIsControllerConnected) {
            { instanceLinkViewModel.sendPreviousSlide() }
        } else null,
        fetchPictureImageBytes = if (instanceLinkUsesRemoteContent) {
            { folderId, index ->
                instanceLinkViewModel.fetchPictureImageBytes(folderId, index)
            }
        } else null,
        fetchPresentationSlideBytes = if (instanceLinkUsesRemoteContent) {
            { id, index -> instanceLinkViewModel.fetchPresentationSlideBytes(id, index) }
        } else null,
        mediaStreamUrl = remoteMediaStreamUrl(appSettings.instanceLink, instanceLinkUsesRemoteContent),
    )
}

/** Where a mirrored video streams from on the primary, or null while this instance plays its own. */
private fun remoteMediaStreamUrl(link: InstanceLinkSettings, usesRemoteContent: Boolean): ((String) -> String)? =
    if (usesRemoteContent) {
        { itemId: String ->
            instanceLinkMediaStreamUrl(
                link.primaryHost, link.primaryPort, link.apiKey, itemId,
            )
        }
    } else null

@Composable
private fun MainWindowScope.webAccessState(): WebAccessState = with(root) {
    WebAccessState(
        serverUrl = companionServer.serverUrl.collectAsState().value,
        tunnelStatus = tunnelStatus,
        tunnelUrl = tunnelUrl ?: "",
        onStartTunnel = {
            companionServer.tunnelManager.start(appSettings.serverSettings.port)
        },
        onStopTunnel = { companionServer.tunnelManager.stop() },
        qaDisplayUrl = qaDisplayUrl,
        onQaDisplayUrlChanged = { qaDisplayUrl = it },
        presentationDisplayUrl = presentationDisplayUrl,
        onPresentationDisplayUrlChanged = { presentationDisplayUrl = it },
        presentationFrozen = presentationFrozen,
        onFreezeToggle = {
            presentationFrozen = !presentationFrozen
            companionServer.broadcastFreezeChange(presentationFrozen)
            presenterManager.setSlideFrozen(presentationFrozen)
        },
        onClearPresentation = {
            companionServer.clearPresentationState()
            presenterManager.requestClearDisplay()
        },
    )
}


