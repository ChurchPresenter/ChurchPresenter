package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.withPreviewMode
import org.churchpresenter.server.broadcastFreezeChange
import org.churchpresenter.server.clearPresentationState
import org.churchpresenter.server.updateAutoScrollInterval
import org.churchpresenter.server.updateLoopingState
import org.churchpresenter.server.updatePresentationRemoteSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.statistics.asDurationRow
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.InstanceLinkLogSide
import org.churchpresenter.server.InstanceLinkLogger
import org.churchpresenter.app.churchpresenter.remote.applyRemoteLiveState
import org.churchpresenter.app.churchpresenter.remote.downloadMirroredBackgroundSettings
import org.churchpresenter.app.churchpresenter.remote.instanceLinkBackgroundCacheDir
import org.churchpresenter.app.churchpresenter.remote.shouldMirrorRemoteBackgrounds
import org.churchpresenter.app.churchpresenter.remote.shouldMirrorRemoteOutput
import org.churchpresenter.server.TunnelStatus

/** Effects driven straight off the settings: the ATEM render, the STT dev mode and Companion Satellite. */
@Composable
internal fun AppRootState.SettingsDrivenEffects() {
    LaunchedEffect(appSettings.atemSettings) {
        presenterManager.setAtemRenderSettings(appSettings.atemSettings)
    }
    LaunchedEffect(appSettings.bibleEngineSettings.helpDevMode) {
        sttManager.helpDevModeEnabled = appSettings.bibleEngineSettings.helpDevMode
    }
    LaunchedEffect(appSettings.companionSatelliteConnections.map { it.id }) {
        for (connection in appSettings.companionSatelliteConnections) {
            if (connection.autoConnect && autoConnectedIds.add(connection.id)) {
                val effective = if (needsGeneratedDeviceId(connection)) {
                    val generated = java.util.UUID.randomUUID().toString()
                    appSettings = appSettings.copy(
                        companionSatelliteConnections = withGeneratedDeviceId(
                            appSettings.companionSatelliteConnections, connection.id, generated,
                        )
                    )
                    settingsManager.saveSettings(appSettings)
                    connection.copy(deviceId = generated)
                } else connection
                companionSatelliteViewModel.connectAll(effective)
            }
        }
    }
    CompanionSatelliteWiring(appSettings, companionSatelliteViewModel, lastReconciled)
}

/**
 * This instance as an Instance Link follower: connecting when the settings say so, and mirroring the
 * primary's live output, its presentation slide and its clears onto this one's outputs.
 */
@Composable
internal fun AppRootState.InstanceLinkFollowerWiring() {
    LaunchedEffect(
        appSettings.instanceLink.enabled,
        appSettings.instanceLink.autoConnect,
        appSettings.instanceLink.primaryHost,
        appSettings.instanceLink.primaryPort,
        appSettings.instanceLink.apiKey,
        appSettings.instanceLink.reconnectDelayMs
    ) {
        val link = appSettings.instanceLink
        if (shouldAutoConnectInstanceLink(link)) {
            instanceLinkViewModel.connect(
                link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                link.reconnectDelayMs.toLong()
            )
        } else if (shouldDisconnectInstanceLink(link)) {
            instanceLinkViewModel.disconnect()
        }
    }
    LaunchedEffect(instanceLinkViewModel, appSettings.instanceLink.role) {
        if (!shouldMirrorRemoteOutput(appSettings.instanceLink.role)) return@LaunchedEffect
        instanceLinkViewModel.remoteLiveState.collectLatest { state ->
            if (state == null) return@collectLatest
            applyRemoteLiveState(
                state, presenterManager, instanceLinkViewModel,
                bibleSyncMode = appSettings.instanceLink.bibleSyncMode,
                localPrimaryBible = primaryBibleForInstanceLink,
                localScenes = scenesForInstanceLink,
                onPlayRemoteMedia = { url, type ->
                    mediaViewModel.loadMedia(url, type)
                    mediaViewModel.play()
                },
                followedLayers = appSettings.instanceLink.followedLayers,
            )
        }
    }
    LaunchedEffect(instanceLinkViewModel, appSettings.instanceLink.role) {
        if (!shouldMirrorRemoteOutput(appSettings.instanceLink.role)) return@LaunchedEffect
        instanceLinkViewModel.remotePresentationSlide.collectLatest { slide ->
            if (slide == null) return@collectLatest
            if (!hasFetchableSlide(slide.id)) return@collectLatest
            val bytes = instanceLinkViewModel.fetchPresentationSlideBytes(slide.id, slide.index)
            if (bytes == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "PRESENTATION", "resolved" to false, "reason" to "fetch_failed")
                )
                return@collectLatest
            }
            val bitmap = runCatching {
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
            if (bitmap == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "PRESENTATION", "resolved" to false, "reason" to "decode_failed")
                )
                return@collectLatest
            }
            presenterManager.setSelectedSlide(bitmap)
            if (slide.isLive) {
                presenterManager.setPresentingMode(Presenting.PRESENTATION)
                presenterManager.setShowPresenterWindow(true)
            }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "PRESENTATION", "resolved" to true, "isLive" to slide.isLive)
            )
        }
    }
    LaunchedEffect(instanceLinkViewModel, appSettings.instanceLink.role) {
        if (!shouldMirrorRemoteOutput(appSettings.instanceLink.role)) return@LaunchedEffect
        var lastSeen = instanceLinkViewModel.displayClearedSignal.value
        instanceLinkViewModel.displayClearedSignal.collect { signal ->
            if (!isFreshClearSignal(signal, lastSeen)) return@collect
            lastSeen = signal
            presenterManager.requestClearDisplay()
        }
    }
    InstanceLinkFailureWiring(instanceLinkViewModel, instanceLinkCommandFailures)
}

/** The primary's backgrounds, fetched and cached while this instance mirrors them over Instance Link. */
@Composable
internal fun AppRootState.MirroredBackgroundsWiring() {
    val instanceLinkConnectionStatusForBackgrounds by instanceLinkViewModel.connectionStatus.collectAsState()
    val instanceLinkBackgroundsSignal by instanceLinkViewModel.backgroundsUpdatedSignal.collectAsState()
    LaunchedEffect(
        instanceLinkConnectionStatusForBackgrounds,
        appSettings.instanceLink.mirrorBackgrounds,
        appSettings.instanceLink.role,
        instanceLinkBackgroundsSignal
    ) {
        if (!shouldMirrorRemoteBackgrounds(
                status = instanceLinkConnectionStatusForBackgrounds,
                role = appSettings.instanceLink.role,
                mirrorBackgrounds = appSettings.instanceLink.mirrorBackgrounds
            )
        ) {
            mirroredBackgroundSettings = null
            return@LaunchedEffect
        }
        if (shouldInvalidateBackgroundCache(instanceLinkBackgroundsSignal)) {
            withContext(Dispatchers.IO) {
                instanceLinkBackgroundCacheDir.listFiles()?.forEach { it.delete() }
            }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "cache_invalidated",
                mapOf("kind" to "backgrounds", "trigger" to "backgrounds_updated")
            )
        }
        val remote = instanceLinkViewModel.fetchBackgroundSettings() ?: return@LaunchedEffect
        mirroredBackgroundSettings = downloadMirroredBackgroundSettings(remote, instanceLinkViewModel)
    }
}

/**
 * The settings the outputs actually render with: the settings dialog's live draft, the primary's
 * mirrored backgrounds and the quick tray's pick, layered over the saved settings.
 */
@Composable
internal fun AppRootState.rememberEffectiveAppSettings(): AppSettings {
    // The settings dialog's on-screen preview edits a draft copy that does not reach `appSettings`
    // until Apply or OK, so while it is running the outputs render that draft instead -- otherwise
    // the preview would show the sample in the styling the operator is in the middle of replacing.
    // Null whenever no preview is running, which is every other moment of the app's life.
    val previewSettingsOverride by presenterManager.previewSettingsOverride
    val devMode = devMode
    val effectiveAppSettings = remember(
        appSettings,
        mirroredBackgroundSettings,
        previewSettingsOverride,
        activeQuickBackground,
        devMode,
    ) {
        // Three layers, innermost first: the settings dialog's draft stands in for the saved
        // settings while a preview is running, Instance Link swaps the backgrounds inside whichever
        // of the two that is, and the quick tray's pick goes in front of the lot — it is what an
        // operator reaches for mid-service to override what is on screen right now.
        // Preview mode is dev mode only: outside it a saved switch is read as off.
        withQuickBackground(
            withMirroredBackgrounds(previewSettingsOverride ?: appSettings, mirroredBackgroundSettings),
            activeQuickBackground,
        ).let { if (devMode) it else it.withPreviewMode(false) }
    }
    // A tile removed from the tray, or a whole settings import, must not leave a stale override live.
    LaunchedEffect(appSettings.quickBackgrounds) {
        val stillThere = activeQuickBackground?.id?.let { id -> appSettings.quickBackgrounds.any { it.id == id } }
        if (stillThere == false) activeQuickBackground = null
    }
    return effectiveAppSettings
}

/** The Companion server kept in step with the app: live state, OBS, the tunnel and the presentation remote. */
@Composable
internal fun AppRootState.CompanionServerWiring(tunnelStatus: TunnelStatus) {
    LiveStateBroadcastWiring(
        appSettings = { appSettings },
        primaryBible = { primaryBibleForInstanceLink },
        presenterManager = presenterManager,
        companionServer = companionServer,
        screenCountForUsage = screenCountForUsage,
        deckLinkCountForUsage = deckLinkCountForUsage,
    )
    ObsSceneWiring(appSettings, companionServer, obsManager, presenterManager)
    LaunchedEffect(tunnelStatus) {
        val isConnected = isTunnelConnected(tunnelStatus)
        if (tunnelJustDropped(prevTunnelWasConnected.value, isConnected)) {
            companionServer.clearPresentationState()
            qaDisplayUrl = ""
            presentationDisplayUrl = ""
        }
        prevTunnelWasConnected.value = isConnected
    }
    LaunchedEffect(
        appSettings.presentationRemoteSettings.remoteControlEnabled,
        appSettings.serverSettings.apiKeyEnabled,
        appSettings.serverSettings.apiKey
    ) {
        val activeApiKey = activeApiKey(appSettings.serverSettings)
        companionServer.updatePresentationRemoteSettings(appSettings.presentationRemoteSettings, activeApiKey)
    }
    LaunchedEffect(appSettings.presentationSettings.autoScrollInterval) {
        companionServer.updateAutoScrollInterval(appSettings.presentationSettings.autoScrollInterval.toInt())
    }
    LaunchedEffect(appSettings.presentationSettings.isLooping) {
        companionServer.updateLoopingState(appSettings.presentationSettings.isLooping)
    }
    LaunchedEffect(Unit) {
        companionServer.onPresentationFreezeToggle.collect {
            presentationFrozen = !presentationFrozen
            companionServer.broadcastFreezeChange(presentationFrozen)
            presenterManager.setSlideFrozen(presentationFrozen)
        }
    }
    MediaRemoteWiring(companionServer, mediaViewModel, presenterManager)
    val slideContentValue = presenterManager.slideContent.value
    LiveStatusWiring(appSettings, companionServer, slideContentValue)
    LaunchedEffect(Unit) {
        companionServer.onPresentationGoLive.collect {
            presenterManager.setPresentingMode(Presenting.PRESENTATION)
            presenterManager.setShowPresenterWindow(true)
        }
    }
    // A phone planning a service asks how long each song usually runs here; the log is the answer.
    LaunchedEffect(liveDurationLog) {
        companionServer.typicalSeconds = { song -> liveDurationLog.median(song.asDurationRow()) }
    }
}

