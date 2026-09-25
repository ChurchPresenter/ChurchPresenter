package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.app.churchpresenter.subtitles.SubtitleCue
import org.churchpresenter.app.churchpresenter.subtitles.SubtitleCueParser
import org.churchpresenter.settings.utils.Constants
import java.io.File

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

    /** Sets looping outright — a calendar cue's Once / Loop / N times, rather than the tab's toggle. */
    fun setLooping(looping: Boolean) {
        _isLooping.value = looping
        _loopsPlayed.intValue = 0
    }

    fun setLoopCount(count: Int) {
        _loopCount.intValue = count.coerceAtLeast(0)
        _loopsPlayed.intValue = 0
    }

    // Subtitles
    /** An external subtitle file handed to VLC alongside the media; blank when there is none. */
    private val _subtitleUrl = mutableStateOf("")
    val subtitleUrl: String get() = _subtitleUrl.value

    /**
     * Bumped only when VLC itself has to be re-handed the subtitle file, which costs a reload.
     *
     * Loading a subtitle used to restart the video from the beginning, because `subtitleUrl` was a
     * key on the player's load effect -- and for an SRT/WebVTT that reload achieved nothing at all,
     * since `softwarePlayOptions` omits `:sub-file=` when the app draws the cues itself, so the
     * option array was byte-identical either side of it. Only a format the parser does not read
     * ('.ass', '.ssa', '.sub') genuinely needs the media opened again.
     */
    private val _vlcSubtitleReloadVersion = mutableIntStateOf(0)
    val vlcSubtitleReloadVersion: Int get() = _vlcSubtitleReloadVersion.intValue

    /** The tracks VLC reports for the loaded media. Filled by the player. */
    private val _vlcSubtitleTracks = mutableStateOf<List<SubtitleTrack>>(emptyList())

    /** The embedded tracks VLC found, which it burns into the one shared frame. */
    val subtitleTracks: List<SubtitleTrack> get() = _vlcSubtitleTracks.value

    /**
     * The SRT/WebVTT files the app draws itself, in the order they were loaded.
     *
     * A list rather than one, because these are the only subtitles that *can* differ between
     * outputs: VLC decodes once into a frame every output shares, so an embedded track is
     * necessarily the same everywhere, while these are drawn per output by `SubtitleOverlay`.
     * Two of them routed to one output is how a bilingual screen is built.
     */
    private val _sidecars = mutableStateOf<List<SidecarSubtitle>>(emptyList())
    val sidecarSubtitles: List<SidecarSubtitle> get() = _sidecars.value

    /**
     * The VLC track id being shown, [SUBTITLES_OFF] for none, or [SUBTITLES_UNDECIDED] until the
     * tracks are known. Only ever one of VLC's own -- the app-drawn files are [sidecarSubtitles].
     */
    private val _selectedSubtitleTrack = mutableIntStateOf(SUBTITLES_UNDECIDED)
    val selectedSubtitleTrack: Int get() = _selectedSubtitleTrack.intValue

    /** Whether anything is showing at all, which is what lights the Media tab's Subtitles key. */
    val subtitlesVisible: Boolean
        get() = _selectedSubtitleTrack.intValue >= 0 || _sidecars.value.any { it.enabled }

    /**
     * True when the app is drawing at least one subtitle file itself.
     *
     * This is what tells `VideoPlayer` not to hand VLC `:sub-file=` as well -- it would burn the
     * same text into the frame underneath the one the app draws.
     */
    val appDrawsSubtitles: Boolean get() = _sidecars.value.isNotEmpty()

    /**
     * The cues to draw on the output running [profileId], in the order they should stack.
     *
     * Reads [sidecarSubtitles] and [currentPosition] as Compose state, so an output redraws as
     * playback advances and the moment a track is turned off or re-routed. A track with no routing
     * shows everywhere, which is what a single loaded file has always done.
     */
    fun activeSubtitleCues(profileId: String): List<SubtitleCue> =
        _sidecars.value
            .filter { it.enabled && it.showsOn(profileId) }
            .mapNotNull { SubtitleCueParser.activeCueAt(it.cues, _currentPosition.value) }

    /**
     * Replaces every loaded subtitle with [path]. Blank clears them.
     *
     * A file the app can parse becomes a track drawn on every output at once -- the operator
     * picked it, so they want to see it -- and the media is **not** reloaded: VLC is not handed
     * the file at all in that case. A format the parser does not read is VLC's to burn in, which
     * is the one case that costs a reload ([vlcSubtitleReloadVersion]).
     */
    fun setSubtitleFile(path: String) {
        _sidecars.value = emptyList()
        _vlcSubtitleTracks.value = emptyList()
        _subtitleUrl.value = ""
        _selectedSubtitleTrack.intValue = SUBTITLES_UNDECIDED
        if (path.isNotBlank()) addSubtitleFile(path)
    }

    /**
     * Loads [path] alongside whatever is already loaded, which is how a second language is added.
     *
     * A file already loaded is not loaded twice -- the sibling scan and the operator's own pick
     * can easily name the same one.
     */
    fun addSubtitleFile(path: String) {
        if (path.isBlank() || _sidecars.value.any { it.path == path }) return
        val cues = SubtitleCueParser.parseSubtitleFile(File(path))
        if (cues.isEmpty()) {
            // Nothing this app can draw: hand it to VLC, which costs the one reload that is real.
            _subtitleUrl.value = path
            _vlcSubtitleReloadVersion.intValue++
            return
        }
        _sidecars.value = _sidecars.value + SidecarSubtitle(path = path, name = File(path).name, cues = cues)
        // The app is drawing this one, so VLC must draw none of its own: an embedded track picked
        // earlier would otherwise stay burned into the frame underneath it.
        _selectedSubtitleTrack.intValue = SUBTITLES_OFF
    }

    /** Turns one loaded subtitle file on or off without unloading it. */
    fun setSidecarEnabled(index: Int, enabled: Boolean) {
        updateSidecar(index) { it.copy(enabled = enabled) }
    }

    /**
     * Routes one loaded subtitle file to a set of output profiles; empty means every output.
     *
     * Runtime state, deliberately not persisted: which tracks exist depends entirely on the video
     * that is loaded, so a profile has nothing stable to remember between one and the next.
     */
    fun setSidecarOutputs(index: Int, outputs: Set<String>) {
        updateSidecar(index) { it.copy(outputs = outputs) }
    }

    private fun updateSidecar(index: Int, transform: (SidecarSubtitle) -> SidecarSubtitle) {
        val current = _sidecars.value
        if (index !in current.indices) return
        _sidecars.value = current.mapIndexed { i, track -> if (i == index) transform(track) else track }
    }

    /**
     * Loads every subtitle sitting beside [mediaUrl] that belongs to it.
     *
     * "Beside it" means the video's own name with a subtitle extension -- `sermon.srt` for
     * `sermon.mp4` -- or that name with one more dotted part before the extension, which is how
     * languages are conventionally marked: `sermon.en.srt`, `sermon.es.srt`. Loaded in name order,
     * so the same folder gives the same order on every machine.
     *
     * Silent when there are none, and silent when the path is not a local file at all (a stream
     * has no folder to look in). Nothing is overwritten: the operator can still load any file by
     * hand, and doing so adds to these rather than replacing them.
     */
    private fun loadSiblingSubtitles(mediaUrl: String) {
        if (mediaUrl.isBlank()) return
        val media = runCatching { File(mediaUrl) }.getOrNull() ?: return
        val folder = media.parentFile?.takeIf { it.isDirectory } ?: return
        val stem = media.name.substringBeforeLast('.', media.name)
        val siblings = runCatching { folder.listFiles().orEmpty() }.getOrDefault(emptyArray())
            .filter { it.isFile && it.extension.lowercase() in APP_DRAWN_SUBTITLE_EXTENSIONS }
            .filter { candidate ->
                val name = candidate.name.substringBeforeLast('.', candidate.name)
                // `sermon` exactly, or `sermon.` plus one more part and nothing further.
                name == stem || name.startsWith("$stem.") && !name.removePrefix("$stem.").contains('.')
            }
            .sortedBy { it.name.lowercase() }
        siblings.forEach { addSubtitleFile(it.absolutePath) }
    }

    /** Draws no subtitles at all: every loaded file off, and VLC told to show none of its own. */
    fun turnSubtitlesOff() {
        _sidecars.value = _sidecars.value.map { it.copy(enabled = false) }
        _selectedSubtitleTrack.intValue = SUBTITLES_OFF
    }

    /** Called by the player once VLC has listed the tracks; [SUBTITLES_UNDECIDED] resolves here. */
    fun setSubtitleTracks(tracks: List<SubtitleTrack>) {
        _vlcSubtitleTracks.value = tracks
        val selected = _selectedSubtitleTrack.intValue
        val stillValid = selected == SUBTITLES_OFF || tracks.any { it.id == selected }
        _selectedSubtitleTrack.intValue = when {
            selected != SUBTITLES_UNDECIDED && stillValid -> selected
            // A file the operator handed VLC is the one they want to see; embedded tracks start
            // hidden, and so does everything once the app is drawing a file of its own.
            _subtitleUrl.value.isNotBlank() -> tracks.lastOrNull()?.id ?: SUBTITLES_OFF
            else -> SUBTITLES_OFF
        }
    }

    fun selectSubtitleTrack(id: Int) {
        _selectedSubtitleTrack.intValue = id
    }


    fun loadMedia(url: String, type: String) {
        setSubtitleFile("")
        loadSiblingSubtitles(url)
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

    /**
     * A cue asking this clip to play, kept until the clip it names has finished loading.
     *
     * The load is what the Media tab does when it is handed a row, and it deliberately leaves the
     * clip paused -- an operator going live by hand presses play. Automation has nobody to press
     * it, so a row that fired on its own sat on a blank output. The url is part of the request so
     * it cannot start whatever clip happened to be loaded a moment earlier.
     */
    fun requestPlayback(plays: Int, url: String) {
        pendingPlayUrl = url
        pendingPlays = plays
        applyPendingPlayback()
    }

    private var pendingPlayUrl: String? = null
    private var pendingPlays: Int = 1

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

    private fun applyPendingPlayback() {
        val wanted = pendingPlayUrl ?: return
        if (_mediaUrl.value != wanted || !_isLoaded.value) return
        pendingPlayUrl = null
        // 1 once, 0 for ever, N times -- the same counting the row's `repeats` uses.
        _isLooping.value = pendingPlays != 1
        _loopCount.intValue = if (pendingPlays == 0) 0 else pendingPlays - 1
        _loopsPlayed.intValue = 0
        play()
        onCuePlaybackStarted?.invoke(_mediaUrl.value, _mediaType.value)
    }

    fun loadMediaFromSchedule(url: String, title: String, type: String, subtitleUrl: String = "") {
        setSubtitleFile(subtitleUrl)
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
        // Loaded is the moment a cue's request can be carried out; before it there is nothing to play.
        applyPendingPlayback()
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
        setSubtitleFile("")
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

    companion object {
        const val SUBTITLES_OFF = -1
        const val SUBTITLES_UNDECIDED = -2

        /** The subtitle formats `SubtitleCueParser` reads, and so the ones the app draws itself. */
        private val APP_DRAWN_SUBTITLE_EXTENSIONS = setOf("srt", "vtt")
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
