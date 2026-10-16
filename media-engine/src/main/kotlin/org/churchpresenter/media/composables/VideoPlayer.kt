package org.churchpresenter.media.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.media.viewmodel.SubtitleTrack
import org.churchpresenter.diagnostics.Log
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Component
import java.util.Locale

internal const val POSITION_POLL_MS = 200
internal const val VOLUME_PERCENT_SCALE = 100
internal const val STATE_SETTLE_MS = 250L
internal const val FRAME_INTERVAL_MS = 16L

/** RV32 is four bytes to the pixel, which is how many pixels a native buffer holds. */
internal const val RV32_BYTES_PER_PIXEL = 4

// libvlc media options, passed to play() one argument at a time rather than as an array: play() is
// a Java vararg, so handing it an array makes the compiler copy that array at every call -- which
// is exactly what detekt's SpreadOperator rule is there to stop.
internal const val VLC_OPT_SOFTWARE_CODEC = ":codec=avcodec"
internal const val VLC_OPT_FAST_DECODE = ":avcodec-fast"
internal const val VLC_OPT_TIGHT_CLOCK = ":clock-jitter=0"
internal const val VLC_OPT_NO_AUDIO = ":no-audio"
internal const val VLC_OPT_SUB_FILE = ":sub-file="

/** The media option that hands VLC an external subtitle file, or nothing when there is none. */
internal fun subtitleMediaOptions(subtitleUrl: String): Array<String> =
    if (subtitleUrl.isBlank()) emptyArray() else arrayOf(VLC_OPT_SUB_FILE + subtitleUrl)

/** VLC's own "draw no subtitles" track. Its list carries it as an entry; we filter it out below. */
internal const val VLC_SUBTITLE_TRACK_DISABLED = -1

/** The subtitle tracks VLC lists for the playing media, without its own "Disable" entry (id -1). */
internal fun MediaPlayer.subtitleTracks(): List<SubtitleTrack> =
    subpictures().trackDescriptions()
        .filter { it.id() >= 0 }
        .map { SubtitleTrack(it.id(), it.description()) }

internal fun isMacOS(): Boolean {
    val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
    return "mac" in os || "darwin" in os
}

/**
 * Creates a platform-appropriate VLCJ component.
 * macOS requires CallbackMediaPlayerComponent; Linux/Windows use EmbeddedMediaPlayerComponent.
 * See https://github.com/caprica/vlcj/issues/887#issuecomment-503288294
 */
internal fun createMediaPlayerComponent(): Component? {
    return try {
        if (isMacOS()) CallbackMediaPlayerComponent()
        else EmbeddedMediaPlayerComponent()
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        // The component loads libvlc: a missing or wrong-architecture library is an Error.
        val msg = e.message ?: e.toString()
        vlcUnavailableReason = msg
        Log.warn("VLCJ", "Could not initialise. Is VLC installed? $msg")
        // Not reported to CrashReporter: this is a known, expected condition (missing VLC,
        // arch mismatch, or a missing Windows dependency like the VC++ Redistributable) with
        // its own dedicated UI messaging (isVlcArchMismatch / isVlcLoadFailed) — it's already
        // "handled" and surfaced to the user, not an unexpected app crash worth telemetry noise.
        null
    }
}

/** Extracts the EmbeddedMediaPlayer from either component type. */
fun Component.mediaPlayer(): EmbeddedMediaPlayer = when (this) {
    is CallbackMediaPlayerComponent -> mediaPlayer()
    is EmbeddedMediaPlayerComponent -> mediaPlayer()
    else -> error("Unexpected component type")
}

/** Releases the component. */
internal fun Component.releasePlayer() = when (this) {
    is CallbackMediaPlayerComponent -> release()
    is EmbeddedMediaPlayerComponent -> release()
    else -> {}
}

/**
 * Embeds a VLCJ media player for local video/audio files.
 * Requires VLC to be installed on the system.
 * Works cross-platform: Linux, Windows, and macOS.
 */
@Composable
fun VideoPlayer(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier,
    audioEnabled: Boolean = true,
    audioDeviceId: String = ""
) {
    val component = remember { createMediaPlayerComponent() } ?: return
    EmbeddedPlayback(
        viewModel, component.mediaPlayer(), audioEnabled, audioDeviceId, release = { component.releasePlayer() },
    )
    SwingPanel(factory = { component }, modifier = modifier)
}
