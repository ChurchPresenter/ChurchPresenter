package org.churchpresenter.media.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.diagnostics.Log
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.media.TrackType
import androidx.compose.runtime.MutableState
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import androidx.compose.foundation.Image
import org.churchpresenter.sharedui.utils.DevFlags

/**
 * The software player's VLC event listener. [reportsPlaybackEnd] is what decides whether this
 * decoder is the one that tells the view model the file ended — see the parameter on
 * [SoftwareVideoPlayer].
 */
internal fun softwarePlayerEvents(
    viewModel: MediaViewModel,
    firstFrameCaptured: MutableState<Boolean>,
    gate: PlayerReleaseGate,
    pauseTimer: MutableState<javax.swing.Timer?>,
    reportsPlaybackEnd: Boolean,
) = object : MediaPlayerEventAdapter() {
    override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
        if (newLength > 0) viewModel.position.setDuration(newLength)
    }

    // The tracks are the same for a mirror, so only the decoder that owns the end of the file
    // reports them; the mirror just applies whichever one the view model has selected.
    override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
        if (reportsPlaybackEnd) viewModel.subtitles.setSubtitleTracks(mediaPlayer.subtitleTracks())
    }

    // An external file is added to the player after the media is ready, so it arrives here.
    override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: TrackType, id: Int) {
        if (reportsPlaybackEnd && type == TrackType.TEXT) {
            viewModel.subtitles.setSubtitleTracks(mediaPlayer.subtitleTracks())
        }
    }

    override fun playing(mediaPlayer: MediaPlayer) {
        if (viewModel.isPlaying) return
        if (!firstFrameCaptured.value) {
            // Give VLC up to 200 ms to decode and deliver the first frame to the render callback
            // before pausing. This is critical for portrait/rotated videos (e.g. iPhone MOV) where
            // the decoder may take longer to start. Held so onDispose can stop it — see the
            // embedded player above.
            pauseTimer.value?.stop()
            pauseTimer.value = javax.swing.Timer(POSITION_POLL_MS) {
                gate.ifLive {
                    if (!viewModel.isPlaying) {
                        mediaPlayer.controls().pause()
                        // Rewinds to the start of the file this load-grace decode ran ahead
                        // into, so the first real Go Live resumes from an explicit seek --
                        // exactly what Stop does before a second Play -- rather than from
                        // wherever those ~200ms of decode happened to land. An operator-
                        // initiated pause mid-playback never reaches this branch
                        // (firstFrameCaptured is already true by then), so nothing here
                        // touches a real pause.
                        mediaPlayer.controls().setTime(0)
                    }
                }
            }.also { it.isRepeats = false; it.start() }
        } else {
            SwingUtilities.invokeLater { gate.ifLive { mediaPlayer.controls().pause() } }
        }
    }

    override fun finished(mediaPlayer: MediaPlayer) {
        if (reportsPlaybackEnd) viewModel.markFinished()
    }

    override fun error(mediaPlayer: MediaPlayer) {
        Log.warn("VLCJ (software)", "Playback error for: ${viewModel.mediaUrl.substringBefore('?')}")
        SwingUtilities.invokeLater { viewModel.pause() }
    }
}

/**
 * The media options the software player loads [subtitleUrl] and its audio setting with.
 *
 * [appRendersSubtitles] is true when `MediaViewModel.subtitleCues` parsed something out of
 * [subtitleUrl] -- the app is drawing that file itself (`SubtitleOverlay`), so VLC must not also
 * be handed `:sub-file=`, or the same text would be burned into the frame a second time.
 */
internal fun softwarePlayOptions(
    audioEnabled: Boolean,
    subtitleUrl: String,
    appRendersSubtitles: Boolean = false,
    forceAvcodec: Boolean = DevFlags.forceAvcodec,
): Array<String> {
    val codec = if (forceAvcodec) arrayOf(VLC_OPT_SOFTWARE_CODEC) else emptyArray()
    val base = if (audioEnabled) {
        codec + arrayOf(VLC_OPT_FAST_DECODE, VLC_OPT_TIGHT_CLOCK)
    } else {
        codec + arrayOf(VLC_OPT_FAST_DECODE, VLC_OPT_TIGHT_CLOCK, VLC_OPT_NO_AUDIO)
    }
    return if (appRendersSubtitles) base else base + subtitleMediaOptions(subtitleUrl)
}

private fun MediaPlayer.playSoftware(
    mrl: String,
    audioEnabled: Boolean,
    subtitleUrl: String,
    appRendersSubtitles: Boolean,
) {
    val options = softwarePlayOptions(audioEnabled, subtitleUrl, appRendersSubtitles)
    // media().play takes its options as a vararg; this is the one call that spreads them.
    @Suppress("SpreadOperator")
    media().play(mrl, *options)
}

/**
 * Applies the view model's chosen subtitle track live, without reloading the media.
 *
 * VLC only ever hears about its own tracks. The app-drawn sidecar file
 * (`MediaViewModel.SUBTITLES_SIDECAR`) has no VLC track behind it, so choosing it -- or turning
 * subtitles off -- is sent to VLC as its "Disable" track, and `MediaPresenter` does the rest by
 * reading `activeSubtitleCue`. Without that, picking an embedded track and then the sidecar file
 * left VLC still burning the embedded one into the frame underneath.
 */
@Composable
internal fun SubtitleTrackSync(viewModel: MediaViewModel, mp: MediaPlayer, gate: PlayerReleaseGate) {
    // Keyed on the resolved selection alone, not on `subtitleTracks` too: that list is reassigned
    // once per embedded track VLC reports (`elementaryStreamAdded` fires per track), and keying on
    // it re-sent the *same* selection to VLC on every one of those -- `setTrack` is not a no-op
    // when the id is unchanged, so a file with several embedded tracks flashed the subtitle
    // renderer once per track while it was still being discovered. Nothing here needs the list
    // itself: `selectedSubtitleTrack` already flips from `SUBTITLES_UNDECIDED` to a resolved value
    // (see `setSubtitleTracks`) the moment there's something to resolve it with.
    LaunchedEffect(viewModel.selectedSubtitleTrack) {
        val track = viewModel.selectedSubtitleTrack
        if (track == MediaViewModel.SUBTITLES_UNDECIDED) return@LaunchedEffect
        // Anything that is not one of VLC's own tracks means "draw nothing", which for VLC is its
        // "Disable" entry, id -1 -- and that is exactly what SUBTITLES_OFF already is.
        val vlcTrack = if (track >= 0) track else VLC_SUBTITLE_TRACK_DISABLED
        SwingUtilities.invokeLater { gate.ifLive { mp.subpictures().setTrack(vlcTrack) } }
    }
}

/**
 * Software-rendering video player that works in any context (including offscreen DeckLink).
 * Uses VLCJ's CallbackVideoSurface to capture frames as BufferedImage → Compose Image.
 * Integrates with MediaViewModel for full playback control.
 */
@Composable
fun SoftwareVideoPlayer(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier,
    audioEnabled: Boolean = true,
    audioDeviceId: String = "",
    // Exactly one decoder may report the end of the file: with looping armed, the end is what
    // spends a repeat, so a mirror mounted on the same view model reporting it too would spend
    // two. The Media tab's decoder and MainDesktop's are already mutually exclusive; a mirror
    // (the stage monitor) passes false.
    reportsPlaybackEnd: Boolean = true,
) = SoftwareVideo(viewModel, modifier, audioEnabled, audioDeviceId, reportsPlaybackEnd, ::openSoftwareVlc)

/** [SoftwareVideoPlayer], with how VLC is opened handed in. */
@Composable
internal fun SoftwareVideo(
    viewModel: MediaViewModel,
    modifier: Modifier,
    audioEnabled: Boolean,
    audioDeviceId: String,
    reportsPlaybackEnd: Boolean,
    openVlc: () -> SoftwareVlc?,
) {
    val vlc = remember { openVlc() } ?: return

    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }
    val frameVersion = remember { mutableStateOf(0L) }
    // The last COMPLETE frame -- what ConvertFramesOffRenderThread reads. The pair it was copied
    // from is FramePingPong's own concern; nothing here reads a buffer VLC might still be writing.
    val bufferedImageHolder = remember { mutableStateOf<BufferedImage?>(null) }
    val framePingPong = remember { mutableStateOf<FramePingPong?>(null) }

    // True once the render callback has delivered at least one frame for the current URL.
    // Used to give VLC a brief window (200 ms) before auto-pausing on first load so that
    // even slow-starting or portrait/rotated videos have time to deliver their first frame.
    val firstFrameCaptured = remember { mutableStateOf(false) }

    ConvertFramesOffRenderThread(frameVersion, bufferedImageHolder, currentFrame)

    // Set up callback video surface for software rendering. Released with the player, by
    // SoftwarePlayback.
    DisposableEffect(Unit) {
        vlc.attachSurface(
            rv32BufferFormatCallback(framePingPong),
            frameRenderCallback(framePingPong, bufferedImageHolder, firstFrameCaptured, frameVersion),
        )
        onDispose { }
    }

    SoftwarePlayback(
        viewModel = viewModel,
        mp = vlc.mp,
        firstFrameCaptured = firstFrameCaptured,
        audioEnabled = audioEnabled,
        audioDeviceId = audioDeviceId,
        reportsPlaybackEnd = reportsPlaybackEnd,
        release = vlc.release,
    )

    // Render current frame as Compose Image
    currentFrame.value?.let { frame ->
        Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/**
 * Everything [SoftwareVideoPlayer] does with its player once it has one: the event listener, the
 * load, the subtitle track, and keeping play/pause, volume, seeks and the position in step with
 * [viewModel]. [release] frees whatever owns the native player, after it has been stopped.
 */
@Composable
internal fun SoftwarePlayback(
    viewModel: MediaViewModel,
    mp: MediaPlayer,
    firstFrameCaptured: MutableState<Boolean>,
    audioEnabled: Boolean = true,
    audioDeviceId: String = "",
    reportsPlaybackEnd: Boolean = true,
    release: () -> Unit = {},
) {
    // What the load effect below last opened it for, so it can tell a genuinely new file or a loop
    // restart (both begin at zero) from the same file being re-opened only because a subtitle VLC
    // has to burn in itself was chosen (resume where the operator was).
    val loadedUrl = remember { mutableStateOf("") }
    val loadedLoopVersion = remember { mutableIntStateOf(-1) }

    // Native-handle lifetime guard and the deferred pause it protects — see PlayerReleaseGate.
    val gate = remember { PlayerReleaseGate() }
    val pauseTimer = remember { mutableStateOf<javax.swing.Timer?>(null) }

    DisposableEffect(Unit) {
        mp.events().addMediaPlayerEventListener(
            softwarePlayerEvents(viewModel, firstFrameCaptured, gate, pauseTimer, reportsPlaybackEnd)
        )
        onDispose { stopAndRelease(gate, pauseTimer, mp, release) }
    }

    AudioOutputDevice(mp, audioDeviceId)

    SoftwareMediaLoad(viewModel, mp, audioEnabled, firstFrameCaptured, loadedUrl, loadedLoopVersion)

    SubtitleTrackSync(viewModel, mp, gate)

    PlaybackSync(viewModel, mp, gate, audioEnabled)
}

/**
 * Opens the media in VLC, and opens it again only when it genuinely must be reopened.
 *
 * Loads when the URL changes, and again on every loop restart: once VLC has reached the end of a
 * file it is in the Ended state, where setTime()/play() alone will not start it over.
 *
 * Keyed on `vlcSubtitleReloadVersion` and NOT on `subtitleUrl`: choosing a subtitle used to
 * restart the video from zero and blank every output, and for an SRT/WebVTT -- the common case,
 * which the app draws itself -- that reload changed nothing about this call. Only a format the
 * parser does not read needs the media opened again, and that is the one thing that bumps the
 * version. Where a reload is unavoidable, `resumeAtMs` below puts playback back where it was.
 */
@Composable
private fun SoftwareMediaLoad(
    viewModel: MediaViewModel,
    mp: MediaPlayer,
    audioEnabled: Boolean,
    firstFrameCaptured: MutableState<Boolean>,
    loadedUrl: MutableState<String>,
    loadedLoopVersion: MutableIntState,
) {
    LaunchedEffect(viewModel.mediaUrl, viewModel.loopRestartVersion, viewModel.vlcSubtitleReloadVersion) {
        val url = viewModel.mediaUrl
        // A reload forced by a subtitle format VLC has to burn in itself must not lose the
        // operator's place mid-service; a new file and a loop restart both begin at zero.
        val sameLoad = url == loadedUrl.value && viewModel.loopRestartVersion == loadedLoopVersion.intValue
        val resumeAtMs = if (sameLoad) viewModel.currentPosition.coerceAtLeast(0L) else 0L
        loadedUrl.value = url
        loadedLoopVersion.intValue = viewModel.loopRestartVersion
        firstFrameCaptured.value = false  // reset so next file gets the 200 ms grace window
        SharedVideoOutput.frame.value = null  // clear stale frame while new media loads
        mp.controls().stop()
        if (url.isBlank()) return@LaunchedEffect

        val mrl = mediaResourceLocator(url)

        // Stay muted until playback is actually requested. Loading always briefly starts the
        // VLC pipeline to capture a first frame (see playing() below), and without this guard
        // that grace window would be audible even though the video is meant to load paused.
        //
        // Muted, not silenced by volume: the real volume is set here too, so libVLC's audio
        // output device is asked to exist -- and negotiate with the OS -- during this load
        // grace window rather than for the first time at Go Live. That negotiation is the
        // asynchronous part the comment below already flags; setting volume 0 at load and only
        // setting the real volume once Go Live is pressed meant Go Live was the first moment
        // that device was ever actually needed, which is a plausible stall of its own layered
        // on top of decode ramping up -- the isPlaying effect below only ever lifts a mute now,
        // it does not ask for a device for the first time.
        if (audioEnabled) mp.applyVolume(viewModel.effectiveVolume)
        mp.audio().setMute(!audioEnabled || !viewModel.isPlaying)

        // :codec=avcodec forces FFmpeg software decoding, bypassing VideoToolbox.
        // Required for Dolby Vision HEVC / 10-bit files where VideoToolbox outputs zero-copy
        // GPU CVPX buffers that the callback video surface cannot read (black frame).
        // :avcodec-fast reduces per-frame overhead; :clock-jitter=0 tightens frame scheduling.
        // When the caller has determined this instance must never produce audio (e.g. a
        // background decoder mounted only to keep rendering a paused frame), :no-audio
        // disables the audio track outright.
        mp.playSoftware(mrl, audioEnabled, viewModel.subtitleUrl, viewModel.appDrawsSubtitles)
        // Auto-pause is handled by the playing() event listener above.
        if (resumeAtMs > 0) mp.controls().setTime(resumeAtMs)
    }
}
