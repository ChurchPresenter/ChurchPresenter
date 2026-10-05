package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.announcements.AnnouncementsOutput
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.liveshow.LiveShow
import org.churchpresenter.media.MediaOutput
import org.churchpresenter.qa.QAOutput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.web.WebOutput

/**
 * What every output is showing, and the one channel between the operator's tabs and the outputs.
 *
 * Its parts are classes of their own, one per kind of content -- [OutputLocks], [LiveBible],
 * [LiveSongs], [LivePictures], [LiveSlides], [PresentationPlayback], [LiveLowerThird],
 * [LiveAnnouncements], [LiveWeb] and [LiveScreens] -- and this class answers for all of them by
 * delegation, so a caller still reads and writes everything through one manager. What stays here is
 * what cuts across them: the live mode, clearing the display, the presenter window, the settings
 * preview and the snapshot a preview restores.
 */
class PresenterManager private constructor(
    showPresenterWindowInitially: Boolean,
    private val context: PresenterContext,
    internal val locks: OutputLocksState = OutputLocksState(context),
    internal val bible: LiveBibleState = LiveBibleState(context),
    internal val songs: LiveSongsState = LiveSongsState(context),
    internal val pictures: LivePicturesState = LivePicturesState(context),
    internal val slides: LiveSlidesState = LiveSlidesState(context),
    internal val playback: PresentationPlaybackState = PresentationPlaybackState(context),
    internal val lowerThird: LiveLowerThirdState = LiveLowerThirdState(context),
    internal val announcements: LiveAnnouncementsState = LiveAnnouncementsState(context),
    internal val web: LiveWebState = LiveWebState(context),
    internal val screens: LiveScreensState = LiveScreensState(context),
    internal val overlayLayers: LiveOverlaysState = LiveOverlaysState(context),
) : OutputLocks by locks,
    LiveBible by bible,
    LiveSongs by songs,
    LivePictures by pictures,
    LiveSlides by slides,
    PresentationPlayback by playback,
    LiveLowerThird by lowerThird,
    LiveAnnouncements by announcements,
    LiveWeb by web,
    LiveScreens by screens,
    LiveOverlays by overlayLayers {

    constructor(showPresenterWindowInitially: Boolean = true) :
        this(showPresenterWindowInitially, PresenterContext())

    init {
        context.notify = { source -> onLiveStateChanged?.invoke(this, source) }
        context.setPresentingMode = ::setPresentingMode
        context.requestClearDisplay = ::requestClearDisplay
    }

    /** What is cued, not yet on air, while preview mode is on -- see [PreviewBus]. */
    val previewBus: PreviewBus by lazy { PreviewBus(this) }

    /** This manager as the Pictures and Presentation tabs see it -- see [PresenterSlidesOutput]. */
    val slidesOutput: SlidesOutput by lazy { PresenterSlidesOutput(this) }
    val mediaOutput: MediaOutput by lazy { PresenterMediaOutput(this) }

    /** This manager as the Web tab sees it -- see [PresenterWebOutput]. */
    val webOutput: WebOutput by lazy { PresenterWebOutput(this) }

    /** This manager as the Q&A tab sees it -- see [PresenterQAOutput]. */
    val qaOutput: QAOutput by lazy { PresenterQAOutput(this) }

    /** This manager as the Announcements tab sees it -- see [PresenterAnnouncementsOutput]. */
    val announcementsOutput: AnnouncementsOutput by lazy { PresenterAnnouncementsOutput(this) }

    /**
     * The layers whose cue is held whole rather than derived from one of the parts -- messages and
     * props (`docs/SHOW_CONTROL.md`). The content layers stay derived until their parts write cues.
     */
    internal val liveShow = LiveShow()

    /**
     * What is on air, as the layer model sees it: the slide's content, each of [overlays], and the
     * content they name ([legacyProgram]), with [liveShow]'s own layers over them. Everything asking
     * what is on screen reads it, through [liveContent], [slideContent] and [isLive]. An output with
     * a screen lock draws its own mode's program instead (`OutputLayers`).
     */
    val program: State<Map<Layer, Cue>> = derivedStateOf {
        val derived = overlays.value.fold(legacyProgram(context.slideMode.value, this)) { layers, overlay ->
            layers + legacyProgram(overlay, this)
        }
        derived + liveShow.program.value
    }

    /** The content types [program] has on air: the slide's first, then each overlay in the order it went up. */
    val liveContent: State<Set<Presenting>> = derivedStateOf { contentOnAir(program.value) }

    /**
     * The content on air under the overlays: Bible, songs, pictures, a presentation, media, a web
     * page, a canvas scene, Q&A or the dictionary, or [Presenting.NONE]. An overlay going live leaves
     * it alone -- see [overlays].
     */
    val slideContent: State<Presenting> = derivedStateOf {
        liveContent.value.firstOrNull { !it.isOverlay } ?: Presenting.NONE
    }

    /** Whether anything at all is on screen. */
    val anythingLive: Boolean get() = liveContent.value.isNotEmpty()

    /** Whether [mode] is on screen, on the slide layers or as an overlay. */
    fun isLive(mode: Presenting): Boolean = mode in liveContent.value

    /** Notified whenever live-content state changes (mode, verse, lyric section, picture, media,
     *  announcement, website, scene, Q&A, dictionary) — wired in main.kt to broadcast an
     *  InstanceLink live-state update via CompanionServer.updateLiveState(). Excludes purely
     *  visual transition/animation state (alphas, offsets) so it doesn't fire on every frame.
     *
     *  The second parameter is the content type THIS specific change belongs to — e.g. setSelectedVerse
     *  always reports [Presenting.BIBLE], regardless of what [slideContent] currently holds. Content
     *  setters and [setPresentingMode] are independent calls from application code, so deriving the
     *  reported type from the live (possibly not-yet-updated) [slideContent] value instead would let
     *  a broadcast pair the wrong mode with fresh content, or the right mode with stale content,
     *  whichever setter happened to run first. */
    var onLiveStateChanged: ((PresenterManager, Presenting) -> Unit)? = null

    /** Raised to fade the outputs out before the display is cleared -- see [requestClearDisplay]. */
    val clearDisplayRequested: State<Boolean> = context.clearDisplayRequested

    private val _showPresenterWindow = mutableStateOf(showPresenterWindowInitially)
    val showPresenterWindow: State<Boolean> = _showPresenterWindow

    private val _devWindowAlwaysOnTop = mutableStateOf(false)
    val devWindowAlwaysOnTop: State<Boolean> = _devWindowAlwaysOnTop

    /**
     * Settings the outputs should render *instead of* the saved ones, while a preview is running.
     *
     * The settings dialog edits a draft copy that reaches the app only on Apply or OK, and the
     * output windows are fed the saved `appSettings` -- so an on-screen preview that pushed only
     * content would style it with the styling being replaced, and show nothing of the edit. This is
     * the channel for the draft: main.kt folds it over `appSettings` when it is non-null, and the
     * dialog clears it on the way out.
     *
     * Deliberately here and not a callback threaded through the dialog: this class is already the
     * one channel between what the operator is doing and what the outputs draw, and a second
     * parallel one would be a second thing to remember to clear.
     */
    private val _previewSettingsOverride = mutableStateOf<AppSettings?>(null)
    val previewSettingsOverride: State<AppSettings?> = _previewSettingsOverride

    fun setPresentingMode(mode: Presenting) {
        if (mode.isOverlay) {
            overlayLayers.showOverlay(mode)
            return
        }
        // Slide content replaces the overlays over it; clearing takes everything down.
        context.overlays.value = emptySet()
        // Clearing takes the whole layers down too; slide content takes a message down.
        if (mode == Presenting.NONE) liveShow.clearAll() else liveShow.clear(Layer.MESSAGES)
        putSlide(mode, lastLive = mode)
    }

    /** Puts [mode] on the slide layers, leaving the overlays as they are, and records [lastLive]. */
    internal fun putSlide(mode: Presenting, lastLive: Presenting) {
        context.lastLive.value = lastLive
        if (context.slideMode.value != mode) {
            CrashReporter.setTag("presenting", mode.name)
            CrashReporter.breadcrumb("Presenting: ${mode.name}", category = "presenter")
        }
        context.slideMode.value = mode
        if (mode != Presenting.NONE) {
            context.clearDisplayRequested.value = false
            // Reset transition alphas so presenters are visible when going live
            // (fade-in inside the presenter handles the actual animation)
            setBibleTransitionAlpha(1f)
            setSongTransitionAlpha(1f)
        }
        if (mode != Presenting.PRESENTATION) {
            // Leaving presentation mode releases the animated player and its layer bitmaps.
            clearPresentationPlayback()
            slides.clearLiveSlide()
        }
        context.notify(mode)
    }

    /** Request a fade-out before clearing the display. The LaunchedEffect in main.kt
     *  watches this flag, animates bibleTransitionAlpha/songTransitionAlpha to 0,
     *  then clears the display. */
    fun requestClearDisplay() {
        if (anythingLive) {
            context.clearDisplayRequested.value = true
        }
    }

    fun togglePresenterWindow() {
        _showPresenterWindow.value = !_showPresenterWindow.value
    }

    fun setShowPresenterWindow(show: Boolean) {
        _showPresenterWindow.value = show
    }

    fun setPreviewSettingsOverride(settings: AppSettings?) {
        _previewSettingsOverride.value = settings
    }

    fun setDevWindowAlwaysOnTop(alwaysOnTop: Boolean) {
        _devWindowAlwaysOnTop.value = alwaysOnTop
    }

    /**
     * Everything a temporary takeover of the outputs has to put back.
     *
     * Bible and song content only, because that is what can be taken over: the settings dialog's
     * on-screen preview is the one caller, and it draws a verse or a slide. The transition alphas
     * are deliberately absent -- [setPresentingMode] resets them to 1f on the way back in, which is
     * the state a restored slide wants anyway, and capturing a value mid-fade would restore a
     * half-faded screen.
     */
    data class LiveStateSnapshot(
        val mode: Presenting,
        val selectedVerse: SelectedVerse,
        val selectedVerses: List<SelectedVerse>,
        val displayedVerses: List<SelectedVerse>,
        val lyricSection: LyricSection,
        val displayedLyricSection: LyricSection,
        val allLyricSections: List<LyricSection>,
        val songDisplaySectionIndex: Int,
        val songDisplayLineIndex: Int,
        val showPresenterWindow: Boolean,
    )

    /**
     * What is live right now, so it can be handed back by [restoreLiveState].
     *
     * Here rather than in the caller because this is the one class that knows the full set: a
     * dialog reaching into ten `mutableState`s of its own would silently miss the next field
     * somebody adds, and would have to be a friend of internals it has no other business with.
     */
    fun snapshotLiveState(): LiveStateSnapshot = LiveStateSnapshot(
        mode = slideContent.value,
        selectedVerse = selectedVerse.value,
        selectedVerses = selectedVerses.value,
        displayedVerses = displayedVerses.value,
        lyricSection = lyricSection.value,
        displayedLyricSection = displayedLyricSection.value,
        allLyricSections = allLyricSections.value,
        songDisplaySectionIndex = songDisplaySectionIndex.value,
        songDisplayLineIndex = songDisplayLineIndex.value,
        showPresenterWindow = _showPresenterWindow.value,
    )

    /**
     * Puts back what [snapshotLiveState] took, content first and the mode last.
     *
     * Order matters: [setPresentingMode] broadcasts, so setting it before the content is back would
     * announce the restored mode paired with the preview's content. The content is put back with
     * plain field writes rather than the notifying setters for the same reason -- one broadcast, at
     * the end, describing a state that is actually true.
     *
     * Not [requestClearDisplay]: that only raises a flag for main.kt to fade out on, is a no-op when
     * the mode is already NONE, and would blank a screen that was showing something before the
     * preview started.
     */
    fun restoreLiveState(snapshot: LiveStateSnapshot) {
        bible.restore(snapshot)
        songs.restore(snapshot)
        _showPresenterWindow.value = snapshot.showPresenterWindow
        setPresentingMode(snapshot.mode)
    }
}
