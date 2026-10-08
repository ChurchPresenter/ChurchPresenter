package org.churchpresenter.app.churchpresenter

import org.churchpresenter.server.preloadData
import org.churchpresenter.server.updateApiKey
import org.churchpresenter.server.updateAtemConfig
import org.churchpresenter.server.updateFileUploadEnabled
import org.churchpresenter.server.updateMaxMediaUploadMb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.statistics.asDurationRow
import org.churchpresenter.app.churchpresenter.dialogs.AboutDialog
import org.churchpresenter.serverui.InstanceLinkToastHost
import org.churchpresenter.app.churchpresenter.dialogs.CONTACT_TYPE_TESTIMONIAL
import org.churchpresenter.app.churchpresenter.dialogs.ContactUsDialog
import org.churchpresenter.app.churchpresenter.dialogs.ShareYourStoryDialog
import org.churchpresenter.app.churchpresenter.dialogs.ConverterWindow
import org.churchpresenter.app.churchpresenter.dialogs.SongLibraryWindow
import org.churchpresenter.strings.generated.resources.bible_font
import org.churchpresenter.app.churchpresenter.dialogs.LottieGenWindow
import org.churchpresenter.profiles.hostFontPicker
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.dialogs.StyleEditorWindow
import org.churchpresenter.app.churchpresenter.dialogs.MemoryMonitorWindow
import org.churchpresenter.app.churchpresenter.dialogs.CustomizeThemeDialog
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityToastHost
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventDialog
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.ui.theme.themeChoiceFrom
import org.churchpresenter.app.churchpresenter.ui.theme.themeCustomizationFrom
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.serverui.InstanceLinkDialog
import org.churchpresenter.sharedui.utils.presenterScreenBounds
import org.churchpresenter.updater.UpdateChecker
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.statistics.CCLIReportDialog
import org.churchpresenter.updater.UpdateAvailableDialog
import org.churchpresenter.settings.answered
import org.jetbrains.compose.resources.stringResource

/** The settings, keyboard-shortcut and theme dialogs. */
@Composable
internal fun MainWindowScope.SettingsDialogs() {
    with(root) {
        OptionsDialog(
            isVisible = showOptionsDialog,
            devMode = devMode,
            initialTab = optionsDialogInitialTab,
            initialSettings = appSettings,
            theme = theme,
            settingsManager = settingsManager,
            companionServer = companionServer,
            remoteClientManager = remoteClientManager,
            calendarSync = calendarSync,
            onDismiss = { showOptionsDialog = false; dialogDismissSignal++ },
            onSave = { updated ->
                appSettings = updated
                settingsManager.saveSettings(updated)
                companionServer.preloadData(
                    songStorageDir = updated.songSettings.storageDirectory,
                    bibleStorageDir = updated.bibleSettings.storageDirectory,
                    primaryBibleFileName = updated.bibleSettings.primaryBible
                )
                companionServer.updateApiKey(
                    enabled = updated.serverSettings.apiKeyEnabled,
                    key = updated.serverSettings.apiKey
                )
                companionServer.updateFileUploadEnabled(updated.serverSettings.fileUploadEnabled)
                companionServer.updateMaxMediaUploadMb(updated.serverSettings.maxMediaUploadMb)
                companionServer.updateAtemConfig(
                    updated.atemSettings,
                    updated.streamingSettings.lowerThirdFolder
                )
            },
            onIdentifyScreen = {
                identifyingScreen = true
                coroutineScope.launch {
                    delay(UPDATE_CHECK_DELAY_MS)
                    identifyingScreen = false
                }
            },
            onIdentifyBrowserSource = { index ->
                presenterManager.identifyBrowserSourceOutput(index)
            },
            onIdentifyNdi = { index ->
                presenterManager.identifyNdiOutput(index)
            },
            onIdentifyOmt = { index ->
                presenterManager.identifyOmtOutput(index)
            },
            obsManager = obsManager,
            companionSatelliteViewModel = companionSatelliteViewModel
        )
        KeyboardShortcutsDialog(
            isVisible = showKeyboardShortcutsDialog,
            settings = appSettings,
            devMode = devMode,
            onSave = { updated ->
                appSettings = updated
                settingsManager.saveSettings(updated)
            },
            onDismiss = { showKeyboardShortcutsDialog = false; dialogDismissSignal++ }
        )
        CustomizeThemeDialog(
            isVisible = showCustomizeThemeDialog,
            currentTheme = theme,
            initial = themeChoiceFrom(
                appSettings,
                // On unless a custom look exists and a preset has been picked
                // since: then opening this is more likely about font or size.
                useCustomColors = customColorsByDefault(theme, appSettings.customThemeAccent),
            ),
            onApply = { choice ->
                if (choice.useCustomColors) theme = ThemeMode.CUSTOM
                appSettings = appSettings.copy(
                    theme = theme.toString(),
                    customThemeAccent = choice.accentHex,
                    customThemeDark = choice.dark,
                    customThemeColors = choice.colors,
                    uiFontFamily = choice.fontFamily,
                    uiFontScale = choice.fontScale,
                    uiRowSpacing = choice.rowSpacing,
                )
                settingsManager.saveSettings(appSettings)
                onThemeCustomizationChange(themeCustomizationFrom(appSettings))
            },
            onDismiss = { showCustomizeThemeDialog = false; dialogDismissSignal++ }
        )
    }
}

/** Statistics, Instance Link, About, Contact and the story prompt. */
@Composable
internal fun MainWindowScope.InfoDialogs() {
    with(root) {
        CCLIReportDialog(
            isVisible = showStatisticsDialog,
            theme = theme,
            statisticsManager = statisticsManager,
            onDismiss = { showStatisticsDialog = false; dialogDismissSignal++ }
        )
        InstanceLinkDialog(
            isVisible = showInstanceLinkDialog,
            settings = appSettings.instanceLink,
            connectionStatus = instanceLinkViewModel.connectionStatus.collectAsState().value,
            remoteLiveState = instanceLinkViewModel.remoteLiveState.collectAsState().value,
            remoteScheduleCount = instanceLinkViewModel.remoteSchedule.collectAsState().value.size,
            lastMessageAtMs = instanceLinkViewModel.lastMessageAtMs.collectAsState().value,
            onConnect = { edited ->
                val link = edited.copy(enabled = true)
                appSettings = appSettings.copy(instanceLink = link)
                settingsManager.saveSettings(appSettings)
                instanceLinkViewModel.connect(
                    link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                    link.reconnectDelayMs.toLong()
                )
            },
            onSave = { edited ->
                appSettings = appSettings.copy(instanceLink = edited)
                settingsManager.saveSettings(appSettings)
            },
            onDisconnect = {
                setInstanceLinkEnabled(false)
                instanceLinkViewModel.disconnect()
            },
            onDismiss = { showInstanceLinkDialog = false; dialogDismissSignal++ }
        )
        AboutDialog(
            isVisible = showAboutDialog,
            onDismiss = { showAboutDialog = false; dialogDismissSignal++ },
            appSettings = appSettings,
            theme = theme
        )
        ContactUsDialog(
            isVisible = showContactDialog,
            onDismiss = {
                showContactDialog = false
                contactDialogInitialType = null
                dialogDismissSignal++
            },
            initialTypeKey = contactDialogInitialType
        )
        ShareYourStoryDialog(
            isVisible = showStoryPrompt,
            onShare = {
                appSettings = appSettings.copy(storyPrompt = appSettings.storyPrompt.answered())
                settingsManager.saveSettings(appSettings)
                showStoryPrompt = false
                contactDialogInitialType = CONTACT_TYPE_TESTIMONIAL
                showContactDialog = true
            },
            onDismiss = { showStoryPrompt = false; dialogDismissSignal++ }
        )
    }
}

/** The converter and the Song Library Manager. */
@Composable
internal fun MainWindowScope.ToolWindows() {
    with(root) {
        if (showConverterWindow) {
            ConverterWindow(
                theme = theme,
                initialTab = converterInitialTab,
                onClose = { showConverterWindow = false }
            )
        }
        if (showSongLibraryWindow) {
            SongLibraryWindow(
                theme = theme,
                songStorageDirectory = appSettings.songSettings.storageDirectory,
                typicalSongSeconds = { song -> liveDurationLog.median(song.asDurationRow()) },
                // What it writes lands in the songs folder, which SongsViewModel
                // already watches -- so the list behind this window reloads on
                // its own rather than on close.
                onClose = { showSongLibraryWindow = false }
            )
        }
    }
}

/** The lower-third generator, the developer windows and the update notice. */
@Composable
internal fun MainWindowScope.MoreToolWindows() {
    with(root) {
        if (showLottieGenWindow) {
            val screenBounds = presenterScreenBounds()
            val lottieGenFonts = rememberSystemFonts()
            val lottieGenFontLabel = stringResource(Res.string.bible_font)
            LottieGenWindow(
                theme = theme,
                outputDir = lottieGenOutputDir,
                onClose = { showLottieGenWindow = false },
                onFileSaved = {
                    UsageEvents.record(UsageEvent.LOWER_THIRD_GENERATED)
                    lottieGenOnFileSaved?.invoke()
                },
                canvasWidth = screenBounds.width,
                canvasHeight = screenBounds.height,
                fontPicker = hostFontPicker(lottieGenFonts, lottieGenFontLabel),
            )
        }
        MemoryMonitorWindow(
            isVisible = showMemoryMonitorWindow,
            theme = theme,
            onClose = { showMemoryMonitorWindow = false }
        )
        if (showStyleEditorWindow) {
            StyleEditorWindow(
                theme = theme,
                onClose = { showStyleEditorWindow = false }
            )
        }
        UpdateAvailableDialog(
            result = pendingUpdateResult,
            isManualCheck = pendingUpdateCheckWasManual,
            participateInPrereleases = appSettings.participateInPrereleases,
            onParticipateInPrereleasesChange = { enabled ->
                appSettings = appSettings.copy(participateInPrereleases = enabled)
                settingsManager.saveSettings(appSettings)
                coroutineScope.launch {
                    pendingUpdateResult = UpdateChecker.checkForUpdate(includePrereleases = enabled)
                }
            },
            updateCheckInterval = appSettings.updateCheckInterval,
            onUpdateCheckIntervalChange = { interval ->
                appSettings = appSettings.copy(updateCheckInterval = interval)
                settingsManager.saveSettings(appSettings)
            },
            onDismiss = { pendingUpdateResult = null }
        )
    }
}

/** The prompt for the first queued remote request, and the choices it offers. */
@Composable
internal fun MainWindowScope.RemoteApprovalDialog() {
    with(root) {
        val currentRemote = remoteEventQueue.firstOrNull()
        val currentClientId = currentRemote?.first?.clientId ?: ""
        RemoteEventDialog(
            event = currentRemote?.first,
            queueSize = remoteEventQueue.size,
            isClientKnownAllowed = remoteClientManager.isAllowed(currentClientId),
            isClientKnownBlocked = remoteClientManager.isBlocked(currentClientId),
            isInstanceLinkFollower = isInstanceLinkFollowerClient(
                currentClientId,
                companionServer.connectedInstanceLinkFollowers.collectAsState().value,
            ),
            onAllow = {
                if (currentRemote != null) UsageEvents.record(UsageEvent.REMOTE_APPROVED)
                currentRemote?.second?.invoke()
                if (remoteEventQueue.isNotEmpty()) remoteEventQueue.removeAt(0)
            },
            onAllowForSession = {
                if (shouldRecordSessionClient(currentClientId, sessionAllowedClients)) {
                    sessionAllowedClients.add(currentClientId)
                }
                val clientToAllow = currentClientId
                val toApprove = remoteEventsSettledBy(remoteEventQueue, clientToAllow)
                UsageEvents.record(UsageEvent.REMOTE_APPROVED, toApprove.size)
                toApprove.forEach { it.second.invoke() }
                remoteEventQueue.removeAll(toApprove)
            },
            onAllowPermanently = {
                remoteClientManager.allowPermanently(currentClientId)
                val clientToAllow = currentClientId
                val toApprove = remoteEventsSettledBy(remoteEventQueue, clientToAllow)
                UsageEvents.record(UsageEvent.REMOTE_APPROVED, toApprove.size)
                toApprove.forEach { it.second.invoke() }
                remoteEventQueue.removeAll(toApprove)
            },
            onBlockForSession = {
                if (shouldRecordSessionClient(currentClientId, sessionBlockedClients)) {
                    sessionBlockedClients.add(currentClientId)
                }
                val clientToBlock = currentClientId
                val toRemove = remoteEventsSettledBy(remoteEventQueue, clientToBlock)
                UsageEvents.record(UsageEvent.REMOTE_DENIED, toRemove.size)
                toRemove.forEach { it.third.invoke() }
                remoteEventQueue.removeAll(toRemove)
            },
            onBlockPermanently = {
                remoteClientManager.blockPermanently(currentClientId)
                val clientToBlock = currentClientId
                val toRemove = remoteEventsSettledBy(remoteEventQueue, clientToBlock)
                UsageEvents.record(UsageEvent.REMOTE_DENIED, toRemove.size)
                toRemove.forEach { it.third.invoke() }
                remoteEventQueue.removeAll(toRemove)
            },
            onDeny = {
                if (currentRemote != null) UsageEvents.record(UsageEvent.REMOTE_DENIED)
                currentRemote?.third?.invoke()
                if (remoteEventQueue.isNotEmpty()) remoteEventQueue.removeAt(0)
            }
        )
    }
}

/** The Instance Link failure toasts and the remote activity toasts. */
@Composable
internal fun MainWindowScope.ActivityToasts() {
    with(root) {
        InstanceLinkToastHost(
            failures = instanceLinkCommandFailures,
            onDismiss = { failure -> instanceLinkCommandFailures.remove(failure) }
        )
        RemoteActivityToastHost(
            notifications = remoteActivityNotifications,
            connectedInstanceLinkFollowers =
                companionServer.connectedInstanceLinkFollowers.collectAsState().value,
            onDismiss = { n -> remoteActivityNotifications.remove(n) },
            onDismissAll = { remoteActivityNotifications.clear() },
            onBlockForSession = { n ->
                val cid = n.clientId
                if (shouldRecordSessionClient(cid, sessionBlockedClients)) {
                    sessionBlockedClients.add(cid)
                    sessionAllowedClients.remove(cid)
                }
                remoteActivityNotifications.removeAll { it.clientId == cid }
            }
        )
    }
}

