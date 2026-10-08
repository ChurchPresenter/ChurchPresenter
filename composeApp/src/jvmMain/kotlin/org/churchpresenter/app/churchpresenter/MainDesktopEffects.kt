package org.churchpresenter.app.churchpresenter

import org.churchpresenter.songs.ScheduleSongAction
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.media.composables.SoftwareVideoPlayer
import org.churchpresenter.media.composables.VideoPlayer
import org.churchpresenter.media.composables.isVlcAvailable
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.diagnostics.CrashReporter
import java.awt.GraphicsEnvironment

/**
 * The effects that come first on the main screen: the tab clamp, the STT address it remembers, and
 * the hidden players that keep media running while the Media tab is not showing.
 */
@Composable
internal fun MainDesktopScope.LeadingEffects() {
    // Persist the clamped value back into state after composition.
    LaunchedEffect(effectiveTabIndex) {
        if (state.selectedTabIndex != effectiveTabIndex) state.selectedTabIndex = effectiveTabIndex
    }

    // Remember the URL of the first successful STT connection so the Bible-tab connect
    // button stays visible across restarts (and hides again if the URL later changes).
    val sttConnected = sttManager?.connected?.value == true
    LaunchedEffect(sttConnected) {
        val urlToPersist = sttUrlToPersist(appSettings, sttConnected)
        if (urlToPersist != null) {
            onSettingsChange { s -> withSttLastConnectedUrl(s, urlToPersist) }
        }
    }

    // Hidden VLCJ player for audio: keeps audio playing when user switches away from Media tab.
    // Only composed when NOT on the Media tab (the tab has its own VideoPlayer).
    if (mediaViewModel != null &&
        shouldHostBackgroundAudio(mediaViewModel.isAudioFile, mediaViewModel.isPlaying, currentTab)
    ) {
        VideoPlayer(
            viewModel = mediaViewModel,
            modifier = Modifier.size(0.dp)
        )
    }
    // Master video decoder for video files when away from Media tab.
    // When on Media tab, MediaTab hosts its own SoftwareVideoPlayer (the master decoder).
    // Both are mutually exclusive so only one decoder runs at a time.
    if (mediaViewModel != null &&
        shouldHostBackgroundVideo(mediaViewModel.isAudioFile, mediaViewModel.isLoaded, currentTab)
    ) {
        SoftwareVideoPlayer(
            viewModel = mediaViewModel,
            modifier = Modifier.size(0.dp),
            // This decoder only exists to keep rendering a frame while off the Media tab.
            // The only control that can set isPlaying = true lives on the Media tab itself,
            // so if this mounts paused it stays paused for its whole lifetime here — safe to
            // disable its audio track outright rather than rely on a volume of 0.
            audioEnabled = mediaViewModel.isPlaying
        )
    }
}

/**
 * The effects that come last: the schedule and media hooks, the remote commands, and the focus and
 * diagnostics work that follows a tab change or a closed dialog.
 */
@Composable
internal fun MainDesktopScope.TrailingEffects() {
    LaunchedEffect(scheduleViewModel) {
        scheduleViewModel.onItemPresented = live.onRowWentLive
    }

    // A clip a cue started belongs on the live output: being handed the row cleared it, and
    // nothing else will push it back. See MediaViewModel.onCuePlaybackStarted.
    LaunchedEffect(mediaViewModel, presenterManager) {
        mediaViewModel?.onCuePlaybackStarted = { url, type ->
            presenterManager.setCurrentMedia(url, type)
            presenterManager.setPresentingMode(Presenting.MEDIA)
            presenterManager.setShowPresenterWindow(true)
        }
    }

    RemoteCommandEffects(
        appSettings = appSettings,
        picturesViewModel = picturesViewModel,
        presentationViewModel = presentationViewModel,
        bibleViewModel = bibleViewModel,
        presenterManager = presenterManager,
        onSongItemVersionBump = { state.selectedSongItemVersion++ },
        resolveImageFile = flows.resolveImageFile,
        onSettingsChange = onSettingsChange,
        onSongItemSelected = {
            state.selectedSongItem = it
            state.selectedSongItemAction = ScheduleSongAction.PUSH
        },
        onPictureItemSelected = { state.select(it) },
        onPresentationItemSelected = { state.select(it) },
        onMediaItemSelected = { state.select(it) },
        onSelectTab = this::selectTab,
        pushCurrentSlideIfLive = this::pushCurrentSlideIfLive,
        remotePresentationPlayPauseFlow = flows.remotePresentationPlayPauseFlow,
        remotePresentationLoopToggleFlow = flows.remotePresentationLoopToggleFlow,
        remotePresentationGotoFlow = flows.remotePresentationGotoFlow,
        selectPictureImageFlow = flows.selectPictureImageFlow,
        nextPictureFlow = flows.nextPictureFlow,
        previousPictureFlow = flows.previousPictureFlow,
        nextSlideFlow = flows.nextSlideFlow,
        previousSlideFlow = flows.previousSlideFlow,
        selectSlideFlow = flows.selectSlideFlow,
        selectBibleVerseFlow = flows.selectBibleVerseFlow,
        remoteSelectSongFlow = flows.remoteSelectSongFlow,
        remoteSelectPictureFlow = flows.remoteSelectPictureFlow,
        remoteSelectPresentationFlow = flows.remoteSelectPresentationFlow,
        remoteSelectMediaFlow = flows.remoteSelectMediaFlow,
        uploadPresentationFlow = flows.uploadPresentationFlow,
        statisticsManager = statisticsManager,
    )

    LaunchedEffect(state.selectedTabIndex) {
        publish.onTabChange(state.selectedTabIndex)
        visibleTabs.getOrNull(effectiveTabIndex)?.name?.let { tabName ->
            CrashReporter.setTag("active_tab", tabName)
            CrashReporter.breadcrumb("Tab: $tabName", category = "navigation")
        }
        // Re-request focus so F-key shortcuts keep working after the new tab's children steal focus
        mainFocusRequester.requestFocus()
    }
    // Restore focus whenever a dialog closes (DialogWindow steals OS focus; without this, arrow
    // keys and other shortcuts stop working until the user clicks back on the main window).
    LaunchedEffect(dialogDismissSignal) {
        if (dialogDismissSignal > 0) mainFocusRequester.requestFocus()
    }

    // One-time startup snapshot of the configuration as searchable Sentry tags, so errors can
    // be filtered by setup (screen/output count, integrations, VLC availability). Off the main
    // thread because the VLC probe can block.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val screenCount = try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
            } catch (_: Exception) { 0 }
            CrashReporter.setConfigTags(mapOf(
                "vlc.available" to isVlcAvailable.toString(),
                "screen.count" to screenCount.toString(),
                "output.count" to appSettings.projectionSettings.screenAssignments.size.toString(),
                "atem.enabled" to appSettings.atemSettings.host.isNotBlank().toString(),
                "obs.enabled" to appSettings.obsSettings.enabled.toString(),
                "server.enabled" to appSettings.serverSettings.enabled.toString()
            ))
        }
    }
}
