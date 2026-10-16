package org.churchpresenter.media.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.churchpresenter.media.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.io.File
import javax.swing.SwingUtilities

/** What VLC is asked to open: a file that exists by its absolute path, anything else as given. */
internal fun mediaResourceLocator(url: String): String = try {
    val f = File(url)
    if (f.exists()) f.absolutePath else url
} catch (_: Exception) { url }

/** The view model's volume, as the percentage VLC takes. */
internal fun MediaPlayer.applyVolume(volume: Float) {
    audio().setVolume((volume * VOLUME_PERCENT_SCALE).toInt())
}

/**
 * One play or pause command.
 *
 * Playing lifts the mute the load set for the first-frame grace window — the audio device itself
 * has been live since load, so this is not the first time it's asked to exist.
 */
internal fun MediaPlayer.applyPlaying(playing: Boolean, audioEnabled: Boolean) {
    if (playing) {
        if (audioEnabled) audio().setMute(false)
        controls().play()
    } else {
        if (audioEnabled) audio().setMute(true)
        controls().pause()
    }
}

/** One poll of the position and, as a fallback, the length (in case lengthChanged fired too early). */
internal fun pollPlayback(mp: MediaPlayer, viewModel: MediaViewModel) {
    if (!mp.status().isPlaying) return
    viewModel.position.setCurrentPosition(mp.status().time())
    if (viewModel.duration == 0L) {
        val len = mp.status().length()
        if (len > 0) viewModel.position.setDuration(len)
    }
}

/**
 * Stops the player and lets it go: the gate first, so anything already queued sees the player as
 * gone, then the grace timer, then [release] for whatever owns the native handle.
 */
internal fun stopAndRelease(
    gate: PlayerReleaseGate,
    pauseTimer: MutableState<javax.swing.Timer?>,
    mp: MediaPlayer,
    release: () -> Unit,
) {
    gate.release()
    pauseTimer.value?.stop()
    try {
        mp.controls().stop()
        release()
    } catch (_: Throwable) { }
}

/** Sends the sound to [audioDeviceId]; blank leaves VLC on the system default. */
@Composable
internal fun AudioOutputDevice(mp: MediaPlayer, audioDeviceId: String) {
    LaunchedEffect(audioDeviceId) {
        if (audioDeviceId.isNotBlank()) {
            mp.audio().setOutputDevice(null, audioDeviceId)
        }
    }
}

/**
 * Keeps the player in step with the view model: play/pause, volume, seeks, and the position the
 * transport shows.
 */
@Composable
internal fun PlaybackSync(viewModel: MediaViewModel, mp: MediaPlayer, gate: PlayerReleaseGate, audioEnabled: Boolean) {
    // We always send the command unconditionally: mp.status().isPlaying() can return a stale
    // value on the EDT (VLC may be buffering/transitioning) and guard-skipping the call is what
    // causes the button to get stuck. libvlc play/pause on an already-playing/paused player
    // is a documented no-op, so this is safe.
    // Behind the gate: the queued call can run after this composable was disposed and the player
    // released, and pausing a released player is a native invalid memory access.
    LaunchedEffect(viewModel.isPlaying) {
        SwingUtilities.invokeLater {
            gate.ifLive { mp.applyPlaying(viewModel.isPlaying, audioEnabled) }
        }
    }

    LaunchedEffect(viewModel.effectiveVolume) {
        if (audioEnabled) mp.applyVolume(viewModel.effectiveVolume)
    }

    LaunchedEffect(viewModel.seekVersion) {
        if (viewModel.currentPosition >= 0) {
            mp.controls().setTime(viewModel.currentPosition)
        }
    }

    if (audioEnabled) {
        LaunchedEffect(viewModel.mediaUrl) {
            while (isActive) {
                delay(STATE_SETTLE_MS)
                pollPlayback(mp, viewModel)
            }
        }
    }
}
