package org.churchpresenter.media.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.media.subtitles.SubtitleCue
import org.churchpresenter.settings.utils.Constants

/** One subtitle track VLC found in the loaded media: [id] is VLC's own, [name] is what it calls it. */
data class SubtitleTrack(val id: Int, val name: String)

/**
 * One SRT/WebVTT file the app draws itself, and where it draws it.
 *
 * [outputs] holds output profile ids; **empty means every output**, which is what a single loaded
 * file has always done and what the sibling scan produces. Naming profiles here is how one screen
 * gets the English and another the Spanish, and how one screen gets both at once.
 */
data class SidecarSubtitle(
    val path: String,
    val name: String,
    val cues: List<SubtitleCue>,
    val enabled: Boolean = true,
    val outputs: Set<String> = emptySet(),
) {
    /** Whether this track is drawn on the output running [profileId]. */
    fun showsOn(profileId: String): Boolean = outputs.isEmpty() || profileId in outputs
}

class MediaViewModel {

    // Media source
    private val _mediaUrl = mutableStateOf("")
    val mediaUrl: String get() = _mediaUrl.value

    private val _mediaTitle = mutableStateOf("")
    val mediaTitle: String get() = _mediaTitle.value

    private val _mediaType = mutableStateOf(Constants.MEDIA_TYPE_LOCAL)
    val mediaType: String get() = _mediaType.value

    private val _isLoaded = mutableStateOf(false)
    val isLoaded: Boolean get() = _isLoaded.value

    // Playback state
    private val _isPlaying = mutableStateOf(false)
    val isPlaying: Boolean get() = _isPlaying.value

    /**
     * Bumped whenever playback (re)starts, so that a repeated end-of-file event for one play is
     * told apart from the end of the next one. With looping armed an end spends a repeat, so a
     * doubled event would spend two; SoftwareVideoPlayer's `reportsPlaybackEnd` keeps a mirrored
     * decoder from raising one at all, and this guards the rest.
     */
    private val _playbackGeneration = mutableIntStateOf(0)
    private var finishHandledGeneration = -1

    /** Seeking, the position and the length. */
    val position = MediaPosition(onSeek = { _playbackGeneration.intValue++ })
    val currentPosition: Long get() = position.currentPosition
    val duration: Long get() = position.duration
    val seekVersion: Int get() = position.seekVersion

    /** Repeating the clip. */
    val looping = MediaLooping()
    val isLooping: Boolean get() = looping.isLooping
    val loopCount: Int get() = looping.loopCount
    val loopsPlayed: Int get() = looping.loopsPlayed
    val loopRestartVersion: Int get() = looping.loopRestartVersion

    /** Volume and mute. */
    val audio = MediaAudio()
    val volume: Float get() = audio.volume
    val isMuted: Boolean get() = audio.isMuted
    val effectiveVolume: Float get() = audio.effectiveVolume

    /** The embedded tracks VLC found and the subtitle files the app draws. */
    val subtitles = MediaSubtitles(position = { position.currentPosition })
    val subtitleUrl: String get() = subtitles.subtitleUrl
    val vlcSubtitleReloadVersion: Int get() = subtitles.vlcSubtitleReloadVersion
    val subtitleTracks: List<SubtitleTrack> get() = subtitles.subtitleTracks
    val sidecarSubtitles: List<SidecarSubtitle> get() = subtitles.sidecarSubtitles
    val selectedSubtitleTrack: Int get() = subtitles.selectedSubtitleTrack
    val subtitlesVisible: Boolean get() = subtitles.subtitlesVisible
    val appDrawsSubtitles: Boolean get() = subtitles.appDrawsSubtitles

    // Audio file detection
    private val _isAudioFile = mutableStateOf(false)
    val isAudioFile: Boolean get() = _isAudioFile.value

    // Playback finished flag — observed by PresenterWindows to auto-clear the screen
    private val _mediaFinished = mutableStateOf(false)
    val mediaFinished: Boolean get() = _mediaFinished.value

    /**
     * Told when a cue's clip actually starts, so the app can put it back on the live output.
     *
     * A lambda rather than a reference to the presenter: this class must not hold one (see
     * `AGENT.md` on passing view models around). It is needed because being handed a row *clears*
     * the live output -- the Media tab asks for that, so the previous clip fades rather than cuts
     * -- and going live by hand undoes it by pushing the new clip. A cue has nobody to push, so
     * without this the clip played in the preview over a black output.
     */
    var onCuePlaybackStarted: ((url: String, type: String) -> Unit)? = null

    /** A cue's request to play a clip once it has loaded. */
    val cue = CueRequest(
        isReady = { url -> _mediaUrl.value == url && _isLoaded.value },
        start = { plays ->
            looping.setPlays(plays)
            play()
            onCuePlaybackStarted?.invoke(_mediaUrl.value, _mediaType.value)
        },
    )

    /**
     * Called by VideoPlayer when the file reaches its end. Restarts it when a loop is still owed,
     * and only otherwise reports the media as finished (which clears the output).
     */
    fun markFinished() {
        // A repeat of the event for a play already dealt with, not the end of the next one.
        if (finishHandledGeneration == _playbackGeneration.intValue) return
        finishHandledGeneration = _playbackGeneration.intValue

        if (looping.spendRepeat()) {
            position.setCurrentPosition(0L)
            _isPlaying.value = true
            _playbackGeneration.intValue++
            return
        }
        _mediaFinished.value = true
        _isPlaying.value = false
        looping.restart()
        position.rewind()
    }
    fun clearFinished() { _mediaFinished.value = false }

    fun loadMedia(url: String, type: String) {
        subtitles.setSubtitleFile("")
        siblingSubtitleFiles(url).forEach { subtitles.addSubtitleFile(it.absolutePath) }
        restart(url, mediaTitleFromUrl(url), type)
    }

    fun loadMediaFromSchedule(url: String, title: String, type: String, subtitleUrl: String = "") {
        subtitles.setSubtitleFile(subtitleUrl)
        restart(url, title, type)
        // Loaded is the moment a cue's request can be carried out; before it there is nothing to play.
        cue.applyIfReady()
    }

    /** A clip from the top: loaded (or not, for a blank [url]), paused, at no position. */
    private fun restart(url: String, title: String, type: String) {
        _mediaUrl.value = url
        _mediaTitle.value = title
        _mediaType.value = type
        _isLoaded.value = url.isNotBlank()
        _isPlaying.value = false
        position.clear()
        _isAudioFile.value = type == Constants.MEDIA_TYPE_AUDIO ||
            url.substringAfterLast('.').lowercase() in Constants.AUDIO_EXTENSIONS
        looping.restart()
        _playbackGeneration.intValue++
    }

    fun togglePlayPause() {
        if (!_isLoaded.value) return
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) _playbackGeneration.intValue++
    }

    fun play() {
        if (_isLoaded.value) {
            _isPlaying.value = true
            _playbackGeneration.intValue++
        }
    }

    fun pause() {
        _isPlaying.value = false
    }

    fun stop() {
        _isPlaying.value = false
        looping.restart()
        _playbackGeneration.intValue++
        position.rewind()
    }

    fun unload() {
        subtitles.setSubtitleFile("")
        restart("", "", Constants.MEDIA_TYPE_LOCAL)
        position.rewind()
    }

    companion object {
        const val SUBTITLES_OFF = -1
        const val SUBTITLES_UNDECIDED = -2
    }
}
