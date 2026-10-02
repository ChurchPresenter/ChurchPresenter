package org.churchpresenter.media.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.media.subtitles.SubtitleCue
import org.churchpresenter.media.subtitles.SubtitleCueParser
import java.io.File

/** The subtitle formats `SubtitleCueParser` reads, and so the ones the app draws itself. */
private val APP_DRAWN_SUBTITLE_EXTENSIONS = setOf("srt", "vtt")

/**
 * Every subtitle sitting beside [mediaUrl] that belongs to it, in name order.
 *
 * "Beside it" means the video's own name with a subtitle extension -- `sermon.srt` for
 * `sermon.mp4` -- or that name with one more dotted part before the extension, which is how
 * languages are conventionally marked: `sermon.en.srt`, `sermon.es.srt`. Name order, so the same
 * folder gives the same order on every machine. Empty when the path is not a local file at all
 * (a stream has no folder to look in).
 */
internal fun siblingSubtitleFiles(mediaUrl: String): List<File> {
    if (mediaUrl.isBlank()) return emptyList()
    val media = runCatching { File(mediaUrl) }.getOrNull() ?: return emptyList()
    val folder = media.parentFile?.takeIf { it.isDirectory } ?: return emptyList()
    val stem = media.name.substringBeforeLast('.', media.name)
    return runCatching { folder.listFiles().orEmpty() }.getOrDefault(emptyArray())
        .filter { it.isFile && it.extension.lowercase() in APP_DRAWN_SUBTITLE_EXTENSIONS }
        .filter { candidate ->
            val name = candidate.name.substringBeforeLast('.', candidate.name)
            // `sermon` exactly, or `sermon.` plus one more part and nothing further.
            name == stem || name.startsWith("$stem.") && !name.removePrefix("$stem.").contains('.')
        }
        .sortedBy { it.name.lowercase() }
}

/**
 * The loaded clip's subtitles: VLC's own embedded tracks, and the SRT/WebVTT files the app draws.
 *
 * [position] is the playback position, read as Compose state so an output redraws its cues as the
 * clip advances.
 */
class MediaSubtitles(private val position: () -> Long) {

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
     * The VLC track id being shown, [MediaViewModel.SUBTITLES_OFF] for none, or
     * [MediaViewModel.SUBTITLES_UNDECIDED] until the tracks are known. Only ever one of VLC's own --
     * the app-drawn files are [sidecarSubtitles].
     */
    private val _selectedSubtitleTrack = mutableIntStateOf(MediaViewModel.SUBTITLES_UNDECIDED)
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
     * Reads [sidecarSubtitles] and the position as Compose state, so an output redraws as
     * playback advances and the moment a track is turned off or re-routed. A track with no routing
     * shows everywhere, which is what a single loaded file has always done.
     */
    fun activeSubtitleCues(profileId: String): List<SubtitleCue> =
        _sidecars.value
            .filter { it.enabled && it.showsOn(profileId) }
            .mapNotNull { SubtitleCueParser.activeCueAt(it.cues, position()) }

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
        _selectedSubtitleTrack.intValue = MediaViewModel.SUBTITLES_UNDECIDED
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
        _selectedSubtitleTrack.intValue = MediaViewModel.SUBTITLES_OFF
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

    /** Draws no subtitles at all: every loaded file off, and VLC told to show none of its own. */
    fun turnSubtitlesOff() {
        _sidecars.value = _sidecars.value.map { it.copy(enabled = false) }
        _selectedSubtitleTrack.intValue = MediaViewModel.SUBTITLES_OFF
    }

    /**
     * Called by the player once VLC has listed the tracks; [MediaViewModel.SUBTITLES_UNDECIDED]
     * resolves here.
     */
    fun setSubtitleTracks(tracks: List<SubtitleTrack>) {
        _vlcSubtitleTracks.value = tracks
        val selected = _selectedSubtitleTrack.intValue
        val stillValid = selected == MediaViewModel.SUBTITLES_OFF || tracks.any { it.id == selected }
        _selectedSubtitleTrack.intValue = when {
            selected != MediaViewModel.SUBTITLES_UNDECIDED && stillValid -> selected
            // A file the operator handed VLC is the one they want to see; embedded tracks start
            // hidden, and so does everything once the app is drawing a file of its own.
            _subtitleUrl.value.isNotBlank() -> tracks.lastOrNull()?.id ?: MediaViewModel.SUBTITLES_OFF
            else -> MediaViewModel.SUBTITLES_OFF
        }
    }

    fun selectSubtitleTrack(id: Int) {
        _selectedSubtitleTrack.intValue = id
    }
}
