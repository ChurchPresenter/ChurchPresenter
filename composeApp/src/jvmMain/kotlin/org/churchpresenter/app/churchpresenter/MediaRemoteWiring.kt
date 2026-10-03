package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.server.broadcastMediaState
import org.churchpresenter.app.churchpresenter.server.MediaPlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

private const val SEEK_SETTLE_MS = 500L

/**
 * Wires a companion device's Media tab to the desktop's own player: what a phone's transport
 * controls do here, and what the desktop reports back as the media state changes.
 *
 * Lifted out of `main()` as a composable. These are ordinary `LaunchedEffect` collectors with no
 * window attached, so unlike the rest of main.kt they can be composed — and therefore tested —
 * on their own.
 */
@Composable
internal fun MediaRemoteWiring(
    companionServer: CompanionServer,
    mediaViewModel: MediaViewModel,
    presenterManager: PresenterManager,
) {
    LaunchedEffect(Unit) {
        var wasLoaded = false
        while (true) {
            val loaded = mediaViewModel.isLoaded
            if (loaded) {
                companionServer.broadcastMediaState(
                    MediaPlaybackState(
                        isLive = isMediaLive(presenterManager.presentingMode.value),
                        isLoaded = true,
                        isPlaying = mediaViewModel.isPlaying,
                        title = mediaViewModel.mediaTitle,
                        positionMs = mediaViewModel.currentPosition,
                        durationMs = mediaViewModel.duration,
                        volume = mediaViewModel.volume,
                        muted = mediaViewModel.isMuted,
                        mediaType = mediaViewModel.mediaType,
                        source = mediaViewModel.mediaUrl,
                    ),
                )
                wasLoaded = true
            } else if (shouldBroadcastMediaCleared(loaded, wasLoaded)) {
                // One final "not loaded" so the mobile clears its now-playing view.
                companionServer.broadcastMediaState(
                    MediaPlaybackState(
                        isLive = false, isLoaded = false, isPlaying = false,
                        title = "", positionMs = 0L, durationMs = 0L,
                        volume = mediaViewModel.volume, muted = mediaViewModel.isMuted,
                        mediaType = mediaViewModel.mediaType, source = "",
                    ),
                )
                wasLoaded = false
            }
            delay(SEEK_SETTLE_MS)
        }
    }
    // Media transport controls from a companion remote (mobile Media tab).
    LaunchedEffect(Unit) { companionServer.onMediaPlayPause.collect { mediaViewModel.togglePlayPause() } }
    LaunchedEffect(Unit) { companionServer.onMediaStop.collect { mediaViewModel.stop() } }
    LaunchedEffect(Unit) { companionServer.onMediaSeekForward.collect { mediaViewModel.position.seekForward() } }
    LaunchedEffect(Unit) { companionServer.onMediaSeekBackward.collect { mediaViewModel.position.seekBackward() } }
    LaunchedEffect(Unit) { companionServer.onMediaSeekTo.collect { mediaViewModel.position.seekTo(it) } }
    LaunchedEffect(Unit) { companionServer.onMediaSetVolume.collect { mediaViewModel.audio.setVolume(it) } }
    LaunchedEffect(Unit) { companionServer.onMediaMuteToggle.collect { mediaViewModel.audio.toggleMute() } }
}
