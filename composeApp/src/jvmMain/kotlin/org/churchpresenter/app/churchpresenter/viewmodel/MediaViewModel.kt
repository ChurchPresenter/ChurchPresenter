package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.settings.utils.Constants

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

    private val _currentPosition = mutableStateOf(0L)   // milliseconds
    val currentPosition: Long get() = _currentPosition.value

    private val _duration = mutableStateOf(0L)           // milliseconds
    val duration: Long get() = _duration.value

    /**
     * Incremented every time the user explicitly seeks (seekTo/seekForward/seekBackward).
     * VideoPlayer observes this to avoid a feedback loop with setCurrentPosition().
     */
    private val _seekVersion = mutableIntStateOf(0)
    val seekVersion: Int get() = _seekVersion.intValue

    // Looping
    private val _isLooping = mutableStateOf(false)
    val isLooping: Boolean get() = _isLooping.value

    /** How many times to repeat after the first play. 0 means repeat forever. */
    private val _loopCount = mutableIntStateOf(0)
    val loopCount: Int get() = _loopCount.intValue

    /** Repeats already played back for the current media. */
    private val _loopsPlayed = mutableIntStateOf(0)
    val loopsPlayed: Int get() = _loopsPlayed.intValue

    /**
     * Incremented every time a loop restarts the media. VideoPlayer observes this to re-issue
     * the play command: once VLC has reached the end, seeking alone will not start it again.
     */
    private val _loopRestartVersion = mutableIntStateOf(0)
    val loopRestartVersion: Int get() = _loopRestartVersion.intValue

    /**
     * Bumped whenever playback (re)starts, so that a repeated end-of-file event for one play is
     * told apart from the end of the next one. With looping armed an end spends a repeat, so a
     * doubled event would spend two; SoftwareVideoPlayer's `reportsPlaybackEnd` keeps a mirrored
     * decoder from raising one at all, and this guards the rest.
     */
    private val _playbackGeneration = mutableIntStateOf(0)
    private var finishHandledGeneration = -1


    // Volume: 0.0 – 1.0
    private val _volume = mutableStateOf(1.0f)
    val volume: Float get() = _volume.value

    private val _isMuted = mutableStateOf(false)
    val isMuted: Boolean get() = _isMuted.value

    /** Effective volume sent to the player (0 when muted). */
    val effectiveVolume: Float get() = if (_isMuted.value) 0f else _volume.value

    // Audio file detection
    private val _isAudioFile = mutableStateOf(false)
    val isAudioFile: Boolean get() = _isAudioFile.value

    // Playback finished flag — observed by PresenterWindows to auto-clear the screen
    private val _mediaFinished = mutableStateOf(false)
    val mediaFinished: Boolean get() = _mediaFinished.value

    /**
     * Called by VideoPlayer when the file reaches its end. Restarts it when a loop is still owed,
     * and only otherwise reports the media as finished (which clears the output).
     */
    fun markFinished() {
        // A repeat of the event for a play already dealt with, not the end of the next one.
        if (finishHandledGeneration == _playbackGeneration.intValue) return
        finishHandledGeneration = _playbackGeneration.intValue

        if (_isLooping.value && (_loopCount.intValue == 0 || _loopsPlayed.intValue < _loopCount.intValue)) {
            _loopsPlayed.intValue++
            _currentPosition.value = 0L
            _isPlaying.value = true
            _playbackGeneration.intValue++
            _loopRestartVersion.intValue++
            return
        }
        _mediaFinished.value = true
        _isPlaying.value = false
        _currentPosition.value = 0L
        _loopsPlayed.intValue = 0
        _seekVersion.intValue++
    }
    fun clearFinished() { _mediaFinished.value = false }

    fun toggleLooping() {
        _isLooping.value = !_isLooping.value
        _loopsPlayed.intValue = 0
    }

    fun setLoopCount(count: Int) {
        _loopCount.intValue = count.coerceAtLeast(0)
        _loopsPlayed.intValue = 0
    }


    fun loadMedia(url: String, type: String) {
        _mediaUrl.value = url
        _mediaType.value = type
        _mediaTitle.value = deriveTitleFromUrl(url)
        _isLoaded.value = url.isNotBlank()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _isAudioFile.value = type == Constants.MEDIA_TYPE_AUDIO ||
            url.substringAfterLast('.').lowercase() in Constants.AUDIO_EXTENSIONS
        _loopsPlayed.intValue = 0
        _playbackGeneration.intValue++
    }

    fun loadMediaFromSchedule(url: String, title: String, type: String) {
        _mediaUrl.value = url
        _mediaTitle.value = title
        _mediaType.value = type
        _isLoaded.value = url.isNotBlank()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _isAudioFile.value = type == Constants.MEDIA_TYPE_AUDIO ||
            url.substringAfterLast('.').lowercase() in Constants.AUDIO_EXTENSIONS
        _loopsPlayed.intValue = 0
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
        _currentPosition.value = 0L
        _loopsPlayed.intValue = 0
        _playbackGeneration.intValue++
        _seekVersion.intValue++
    }

    fun unload() {
        _isPlaying.value = false
        _mediaUrl.value = ""
        _mediaTitle.value = ""
        _mediaType.value = Constants.MEDIA_TYPE_LOCAL
        _isLoaded.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _isAudioFile.value = false
        _loopsPlayed.intValue = 0
        _playbackGeneration.intValue++
        _seekVersion.intValue++
    }

    fun seekForward(ms: Long = 10_000L) {
        if (_duration.value > 0) {
            _currentPosition.value = (_currentPosition.value + ms).coerceAtMost(_duration.value)
            _playbackGeneration.intValue++
            _seekVersion.intValue++
        }
    }

    fun seekBackward(ms: Long = 10_000L) {
        _currentPosition.value = (_currentPosition.value - ms).coerceAtLeast(0L)
        _playbackGeneration.intValue++
        _seekVersion.intValue++
    }

    fun seekTo(ms: Long) {
        _currentPosition.value = ms.coerceIn(0L, _duration.value.takeIf { it > 0 } ?: Long.MAX_VALUE)
        _playbackGeneration.intValue++
        _seekVersion.intValue++
    }

    fun setVolume(v: Float) {
        _volume.value = v.coerceIn(0f, 1f)
        if (_isMuted.value && v > 0f) _isMuted.value = false
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    /** Called by VideoPlayer once the media is ready. */
    fun setDuration(ms: Long) {
        _duration.value = ms
    }

    /** Called by VideoPlayer to keep the progress in sync (does NOT bump seekVersion). */
    fun setCurrentPosition(ms: Long) {
        _currentPosition.value = ms
    }

    internal fun deriveTitleFromUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtsp://") ||
                url.startsWith("rtp://") || url.startsWith("mms://") || url.startsWith("udp://") ->
                url.substringAfterLast("/").ifBlank { url }
            else -> {
                val file = java.io.File(url)
                // `File.name` splits on the platform's own separator, so a Windows path that no
                // longer exists still yields "clip.mp4". `substringAfterLast("/")` found no slash
                // in C:\Media\clip.mp4 and handed the whole path back as the title.
                if (file.exists()) file.nameWithoutExtension
                else file.name.ifBlank { url }
            }
        }
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}
