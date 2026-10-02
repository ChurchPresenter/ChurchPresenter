package org.churchpresenter.media.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.media.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import javax.swing.SwingUtilities

/** The embedded player's VLC event listener. */
internal fun embeddedPlayerEvents(
    viewModel: MediaViewModel,
    firstFrameCaptured: MutableState<Boolean>,
    gate: PlayerReleaseGate,
    pauseTimer: MutableState<javax.swing.Timer?>,
) = object : MediaPlayerEventAdapter() {
    override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
        if (newLength > 0) viewModel.position.setDuration(newLength)
    }
    // The embedded tracks were listed only by the software player, so a file opened
    // through this one offered an empty Subtitles menu however many tracks it carried.
    override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
        viewModel.subtitles.setSubtitleTracks(mediaPlayer.subtitleTracks())
    }
    override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: TrackType, id: Int) {
        if (type == TrackType.TEXT) viewModel.subtitles.setSubtitleTracks(mediaPlayer.subtitleTracks())
    }
    override fun playing(mediaPlayer: MediaPlayer) {
        if (!viewModel.isPlaying) {
            if (!firstFrameCaptured.value) {
                // Delay pause by 200 ms so VLC can decode and render the first frame
                // before being paused. Without this, portrait/MOV videos stay black.
                // The timer is held so onDispose can stop it: switching media disposes
                // this composable well inside those 200 ms, and the callback would
                // otherwise pause a player that has already been released.
                pauseTimer.value?.stop()
                pauseTimer.value = javax.swing.Timer(POSITION_POLL_MS) {
                    gate.ifLive {
                        if (!viewModel.isPlaying) {
                            mediaPlayer.controls().pause()
                            // Rewinds to the start of the file this load-grace decode
                            // ran ahead into, so the first real Go Live resumes from an
                            // explicit seek -- exactly what Stop does before a second
                            // Play -- rather than from wherever those ~200ms of decode
                            // happened to land. An operator-initiated pause mid-playback
                            // never reaches this branch (firstFrameCaptured is already
                            // true by then), so nothing here touches a real pause.
                            mediaPlayer.controls().setTime(0)
                        }
                    }
                }.also { it.isRepeats = false; it.start() }
            } else {
                SwingUtilities.invokeLater { gate.ifLive { mediaPlayer.controls().pause() } }
            }
        }
    }
    override fun finished(mediaPlayer: MediaPlayer) {
        viewModel.markFinished()
    }
    override fun error(mediaPlayer: MediaPlayer) {
        Log.warn("VLCJ", "Playback error for: ${viewModel.mediaUrl}")
        SwingUtilities.invokeLater { viewModel.pause() }
    }
    override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) {
        // VLC confirmed a video output is present — mark first frame as captured
        // so subsequent play/pause cycles don't delay.
        if (newCount > 0) firstFrameCaptured.value = true
    }
}

/**
 * Opens the view model's media in [mp], paused.
 *
 * Stays muted until playback is actually requested. Loading always briefly starts the VLC
 * pipeline to capture a first frame (see `playing()` above), and without this guard that grace
 * window would be audible even though the file is meant to load paused.
 *
 * Muted, not silenced by volume: the real volume is set here too, so libVLC's audio output device
 * is asked to exist during this load grace window rather than for the first time at Go Live.
 *
 * When the caller has determined this instance must never produce audio (e.g. a background
 * decoder mounted only to keep rendering a paused frame), the audio track is disabled outright
 * with `:no-audio`.
 */
internal fun loadEmbedded(mp: MediaPlayer, viewModel: MediaViewModel, audioEnabled: Boolean) {
    val url = viewModel.mediaUrl
    mp.controls().stop()
    if (url.isBlank()) return
    val mrl = mediaResourceLocator(url)
    if (audioEnabled) mp.applyVolume(viewModel.effectiveVolume)
    mp.audio().setMute(!audioEnabled || !viewModel.isPlaying)
    if (!audioEnabled) mp.media().play(mrl, VLC_OPT_NO_AUDIO)
    else mp.media().play(mrl)  // VideoPlayer is audio-only; no codec override needed
    // Auto-pause is handled by the playing() event listener above.
}

/**
 * Everything [VideoPlayer] does with its player once it has one: the event listener, the load,
 * and keeping play/pause, volume, seeks and the position in step with [viewModel]. [release]
 * frees whatever owns the native player, after it has been stopped.
 */
@Composable
internal fun EmbeddedPlayback(
    viewModel: MediaViewModel,
    mp: MediaPlayer,
    audioEnabled: Boolean = true,
    audioDeviceId: String = "",
    release: () -> Unit = {},
) {
    // True once VLC has delivered at least one frame. Used to give a 200 ms grace window
    // before auto-pausing on first load, so portrait/rotated videos have time to render
    // their first frame before the player is paused.
    val firstFrameCaptured = remember { mutableStateOf(false) }

    // Native-handle lifetime guard and the deferred pause it protects — see PlayerReleaseGate.
    val gate = remember { PlayerReleaseGate() }
    val pauseTimer = remember { mutableStateOf<javax.swing.Timer?>(null) }

    DisposableEffect(Unit) {
        mp.events().addMediaPlayerEventListener(embeddedPlayerEvents(viewModel, firstFrameCaptured, gate, pauseTimer))
        onDispose { stopAndRelease(gate, pauseTimer, mp, release) }
    }

    AudioOutputDevice(mp, audioDeviceId)

    // Load media when the URL changes, and again on every loop restart: once VLC has reached the
    // end of a file it is in the Ended state, where setTime()/play() alone will not start it over.
    LaunchedEffect(viewModel.mediaUrl, viewModel.loopRestartVersion) {
        firstFrameCaptured.value = false  // reset grace window for each new file
        loadEmbedded(mp, viewModel, audioEnabled)
    }

    PlaybackSync(viewModel, mp, gate, audioEnabled)
}
