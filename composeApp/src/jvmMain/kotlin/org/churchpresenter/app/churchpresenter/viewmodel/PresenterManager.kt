package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.cef.browser.CefBrowser
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import androidx.compose.runtime.withFrameNanos
import org.churchpresenter.app.churchpresenter.presenter.LottieFrame
import org.churchpresenter.app.churchpresenter.presenter.LottieFrameStream
import org.churchpresenter.app.churchpresenter.presenter.PresentationFrame
import org.churchpresenter.app.churchpresenter.presenter.PresentationPlayer
import presentation.engine.model.Deck
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.server.LottieRenderCache

class PresenterManager {

    private val preRenderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var preRenderJob: Job? = null
    private val _presentingMode = mutableStateOf(Presenting.NONE)
    val presentingMode: State<Presenting> = _presentingMode

    /** Notified whenever live-content state changes (mode, verse, lyric section, picture, media,
     *  announcement, website, scene, Q&A, dictionary) — wired in main.kt to broadcast an
     *  InstanceLink live-state update via CompanionServer.updateLiveState(). Excludes purely
     *  visual transition/animation state (alphas, offsets) so it doesn't fire on every frame.
     *
     *  The second parameter is the content type THIS specific change belongs to — e.g. setSelectedVerse
     *  always reports [Presenting.BIBLE], regardless of what [presentingMode] currently holds. Content
     *  setters and [setPresentingMode] are independent calls from application code, so deriving the
     *  reported type from the live (possibly not-yet-updated) [presentingMode] value instead would let
     *  a broadcast pair the wrong mode with fresh content, or the right mode with stale content,
     *  whichever setter happened to run first. */
    var onLiveStateChanged: ((PresenterManager, Presenting) -> Unit)? = null
    private fun notifyLiveStateChanged(source: Presenting) {
        onLiveStateChanged?.invoke(this, source)
    }

    // Flag to request fade-out before clearing display
    private val _clearDisplayRequested = mutableStateOf(false)
    val clearDisplayRequested: State<Boolean> = _clearDisplayRequested

    private val _selectedVerse = mutableStateOf(SelectedVerse())
    val selectedVerse: State<SelectedVerse> = _selectedVerse

    private val _selectedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val selectedVerses: State<List<SelectedVerse>> = _selectedVerses

    // Shared Bible transition state — driven by a single animation so all windows stay in sync
    private val _displayedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val displayedVerses: State<List<SelectedVerse>> = _displayedVerses

    // Next Bible verse after whatever is currently displayed — for Stage Monitor's "Next" zone.
    // Distinct from displayedVerses, which pairs primary+secondary *language* of the same verse.
    private val _nextVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val nextVerses: State<List<SelectedVerse>> = _nextVerses

    private val _bibleTransitionAlpha = mutableStateOf(1f)
    val bibleTransitionAlpha: State<Float> = _bibleTransitionAlpha

    // Previous content for crossfade (both old and new visible simultaneously)
    private val _previousDisplayedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val previousDisplayedVerses: State<List<SelectedVerse>> = _previousDisplayedVerses
    private val _previousBibleAlpha = mutableStateOf(0f)
    val previousBibleAlpha: State<Float> = _previousBibleAlpha

    // Hold mode — when active, verse selection changes are staged but not sent to display
    private val _bibleHold = mutableStateOf(false)
    val bibleHold: State<Boolean> = _bibleHold

    // Per-screen lock: maps screen slot index -> locked Presenting mode.
    // Null entry means the screen follows the global presentingMode.
    private val _screenLocks = mutableStateOf<Map<Int, Presenting>>(emptyMap())
    val screenLocks: State<Map<Int, Presenting>> = _screenLocks

    fun setScreenLock(screenIndex: Int, mode: Presenting?) {
        val updated = _screenLocks.value.toMutableMap()
        if (mode == null) updated.remove(screenIndex) else updated[screenIndex] = mode
        _screenLocks.value = updated
    }

    // Per-Browser-Source-output lock: separate index space from _screenLocks since
    // ProjectionSettings.browserSourceOutputs has its own independent 0-based indices.
    private val _browserSourceLocks = mutableStateOf<Map<Int, Presenting>>(emptyMap())
    val browserSourceLocks: State<Map<Int, Presenting>> = _browserSourceLocks

    fun setBrowserSourceLock(index: Int, mode: Presenting?) {
        val updated = _browserSourceLocks.value.toMutableMap()
        if (mode == null) updated.remove(index) else updated[index] = mode
        _browserSourceLocks.value = updated
    }

    // Indices of Browser Source outputs currently showing the "Identify" overlay
    // (their output number, briefly flashed) — same idea as identifyingScreen for
    // physical displays, but per-output since there's no window to flash instead.
    private val _browserSourceIdentifying = mutableStateOf<Set<Int>>(emptySet())
    val browserSourceIdentifying: State<Set<Int>> = _browserSourceIdentifying

    fun identifyBrowserSourceOutput(index: Int) {
        _browserSourceIdentifying.value = _browserSourceIdentifying.value + index
        preRenderScope.launch {
            delay(5_000L)
            _browserSourceIdentifying.value = _browserSourceIdentifying.value - index
        }
    }

    private val _lyricSection = mutableStateOf(LyricSection())
    val lyricSection: State<LyricSection> = _lyricSection

    // Monotonic counter incremented on every setLyricSection call.
    // Ensures Compose always sees a state change even when the LyricSection
    // content is structurally identical (same song/section clicked twice).
    private val _lyricSectionVersion = mutableStateOf(0)
    val lyricSectionVersion: State<Int> = _lyricSectionVersion

    // Shared Song transition state
    private val _displayedLyricSection = mutableStateOf(LyricSection())
    val displayedLyricSection: State<LyricSection> = _displayedLyricSection

    private val _songTransitionAlpha = mutableStateOf(1f)
    val songTransitionAlpha: State<Float> = _songTransitionAlpha

    // Previous content for song crossfade
    private val _previousDisplayedLyricSection = mutableStateOf(LyricSection())
    val previousDisplayedLyricSection: State<LyricSection> = _previousDisplayedLyricSection
    private val _previousSongAlpha = mutableStateOf(0f)
    val previousSongAlpha: State<Float> = _previousSongAlpha

    private val _songDisplayLineIndex = mutableStateOf(-1)
    val songDisplayLineIndex: State<Int> = _songDisplayLineIndex

    private val _songDisplaySectionIndex = mutableStateOf(-1)
    val songDisplaySectionIndex: State<Int> = _songDisplaySectionIndex

    // All sections of the currently selected song — used for auto-fit font sizing
    private val _allLyricSections = mutableStateOf<List<LyricSection>>(emptyList())
    val allLyricSections: State<List<LyricSection>> = _allLyricSections

    private val _selectedImagePath = mutableStateOf<String?>(null)
    val selectedImagePath: State<String?> = _selectedImagePath

    // Shared Picture transition state
    private val _displayedImagePath = mutableStateOf<String?>(null)
    val displayedImagePath: State<String?> = _displayedImagePath

    private val _nextImagePath = mutableStateOf<String?>(null)
    val nextImagePath: State<String?> = _nextImagePath

    private val _pictureTransitionAlpha = mutableStateOf(1f)
    val pictureTransitionAlpha: State<Float> = _pictureTransitionAlpha

    private val _previousDisplayedImagePath = mutableStateOf<String?>(null)
    val previousDisplayedImagePath: State<String?> = _previousDisplayedImagePath

    private val _pictureSlideOffset = mutableStateOf(1f)
    val pictureSlideOffset: State<Float> = _pictureSlideOffset

    private val _animationType = mutableStateOf(AnimationType.CROSSFADE)
    val animationType: State<AnimationType> = _animationType

    private val _transitionDuration = mutableStateOf(500)
    val transitionDuration: State<Int> = _transitionDuration

    private val _showPresenterWindow = mutableStateOf(true)
    val showPresenterWindow: State<Boolean> = _showPresenterWindow

    private val _selectedSlide = mutableStateOf<ImageBitmap?>(null)
    val selectedSlide: State<ImageBitmap?> = _selectedSlide

    // Shared Slide transition state
    private val _displayedSlide = mutableStateOf<ImageBitmap?>(null)
    val displayedSlide: State<ImageBitmap?> = _displayedSlide

    private val _nextSlide = mutableStateOf<ImageBitmap?>(null)
    val nextSlide: State<ImageBitmap?> = _nextSlide

    // Presenter notes for the current slide (from PowerPoint/Keynote)
    private val _presenterNotes = mutableStateOf("")
    val presenterNotes: State<String> = _presenterNotes

    private val _slideTransitionAlpha = mutableStateOf(1f)
    val slideTransitionAlpha: State<Float> = _slideTransitionAlpha

    private val _previousDisplayedSlide = mutableStateOf<ImageBitmap?>(null)
    val previousDisplayedSlide: State<ImageBitmap?> = _previousDisplayedSlide

    private val _slideSlideOffset = mutableStateOf(1f)
    val slideSlideOffset: State<Float> = _slideSlideOffset

    private val _slideFrozen = mutableStateOf(false)
    val slideFrozen: State<Boolean> = _slideFrozen
    fun setSlideFrozen(frozen: Boolean) { _slideFrozen.value = frozen }

    // Shared Announcements transition state
    private val _displayedAnnouncementText = mutableStateOf("")
    val displayedAnnouncementText: State<String> = _displayedAnnouncementText

    private val _announcementTransitionAlpha = mutableStateOf(1f)
    val announcementTransitionAlpha: State<Float> = _announcementTransitionAlpha

    // Shared Lottie (lower third) animation progress — driven centrally
    private val _lottieProgress = mutableStateOf(0f)
    val lottieProgress: State<Float> = _lottieProgress

    // Pre-rendered playback: once the LottieRenderCache entry for the current content is
    // ready, a LottieFrameStream serves decoded frames here and lottieFrameCount flips
    // non-null — main.kt's playback clock switches from lottieProgress to frame indices.
    private val _lottieFrame = mutableStateOf<LottieFrame?>(null)
    val lottieFrame: State<LottieFrame?> = _lottieFrame

    private val _lottieFrameCount = mutableStateOf<Int?>(null)
    val lottieFrameCount: State<Int?> = _lottieFrameCount

    // The frame rate the current cache entry was rendered at — main.kt's playback loop
    // paces itself off this.
    private val _lottiePrerenderFps = mutableStateOf(LottieRenderCache.PLAYBACK_FPS)
    val lottiePrerenderFps: State<Int> = _lottiePrerenderFps

    private val _lottieCurrentFrameIndex = mutableStateOf(0)
    val lottieCurrentFrameIndex: State<Int> = _lottieCurrentFrameIndex

    // Read from the decode worker thread (frame-publish identity guard) — volatile so a
    // Main-thread clear is seen there promptly.
    @Volatile
    private var lottieFrameStream: LottieFrameStream? = null

    // ATEM settings snapshot (kept current from main.kt) so pre-renders pick the shared
    // render size and playback + ATEM uploads hit one cache entry.
    private var atemRenderSettings: AtemSettings? = null

    fun setAtemRenderSettings(atem: AtemSettings?) {
        atemRenderSettings = atem
    }

    fun setLottieCurrentFrameIndex(index: Int) {
        _lottieCurrentFrameIndex.value = index
        lottieFrameStream?.requestFrame(index)
    }

    // Shared Media transition alpha
    private val _mediaTransitionAlpha = mutableStateOf(1f)
    val mediaTransitionAlpha: State<Float> = _mediaTransitionAlpha

    // Currently playing media — not driven by MediaViewModel's own state, so it's tracked here
    // purely to report "what's live" (e.g. to an InstanceLink follower).
    private val _currentMediaUrl = mutableStateOf("")
    val currentMediaUrl: State<String> = _currentMediaUrl

    private val _currentMediaType = mutableStateOf("")
    val currentMediaType: State<String> = _currentMediaType

    fun setCurrentMedia(url: String, type: String) {
        _currentMediaUrl.value = url
        _currentMediaType.value = type
        notifyLiveStateChanged(Presenting.MEDIA)
    }

    fun setPresentingMode(mode: Presenting) {
        if (_presentingMode.value != mode) {
            CrashReporter.setTag("presenting", mode.name)
            CrashReporter.breadcrumb("Presenting: ${mode.name}", category = "presenter")
        }
        _presentingMode.value = mode
        if (mode != Presenting.NONE) {
            _clearDisplayRequested.value = false
            // Reset transition alphas so presenters are visible when going live
            // (fade-in inside the presenter handles the actual animation)
            _bibleTransitionAlpha.value = 1f
            _songTransitionAlpha.value = 1f
        }
        if (mode != Presenting.PRESENTATION) {
            // Leaving presentation mode releases the animated player and its layer bitmaps.
            clearPresentationPlayback()
        }
        notifyLiveStateChanged(mode)
    }

    /** Request a fade-out before clearing the display. The LaunchedEffect in main.kt
     *  watches this flag, animates bibleTransitionAlpha/songTransitionAlpha to 0,
     *  then sets presentingMode to NONE. */
    fun requestClearDisplay() {
        if (_presentingMode.value != Presenting.NONE) {
            _clearDisplayRequested.value = true
        }
    }

    fun setSelectedVerse(verse: SelectedVerse) {
        _selectedVerse.value = verse
        notifyLiveStateChanged(Presenting.BIBLE)
    }

    fun setSelectedVerses(verses: List<SelectedVerse>) {
        _selectedVerses.value = verses
        if (verses.isNotEmpty()) {
            _selectedVerse.value = verses.first()
        }
        notifyLiveStateChanged(Presenting.BIBLE)
    }

    fun setDisplayedVerses(verses: List<SelectedVerse>) {
        _displayedVerses.value = verses
    }

    fun setNextVerses(verses: List<SelectedVerse>) {
        _nextVerses.value = verses
    }

    fun setBibleTransitionAlpha(alpha: Float) {
        _bibleTransitionAlpha.value = alpha
    }

    fun setPreviousDisplayedVerses(verses: List<SelectedVerse>) {
        _previousDisplayedVerses.value = verses
    }

    fun setPreviousBibleAlpha(alpha: Float) {
        _previousBibleAlpha.value = alpha
    }

    fun setBibleHold(hold: Boolean) {
        _bibleHold.value = hold
    }

    fun setLyricSection(section: LyricSection) {
        _lyricSection.value = section
        _lyricSectionVersion.value++
        notifyLiveStateChanged(Presenting.LYRICS)
    }

    fun setDisplayedLyricSection(section: LyricSection) {
        _displayedLyricSection.value = section
    }

    fun setSongTransitionAlpha(alpha: Float) {
        _songTransitionAlpha.value = alpha
    }

    fun setPreviousDisplayedLyricSection(section: LyricSection) {
        _previousDisplayedLyricSection.value = section
    }

    fun setPreviousSongAlpha(alpha: Float) {
        _previousSongAlpha.value = alpha
    }

    fun setSongDisplayLineIndex(index: Int) {
        _songDisplayLineIndex.value = index
    }

    fun setSongDisplaySectionIndex(index: Int) {
        _songDisplaySectionIndex.value = index
    }

    fun setAllLyricSections(sections: List<LyricSection>) {
        _allLyricSections.value = sections
    }

    fun setSelectedImagePath(imagePath: String?) {
        _selectedImagePath.value = imagePath
        notifyLiveStateChanged(Presenting.PICTURES)
    }

    fun setDisplayedImagePath(path: String?) {
        _displayedImagePath.value = path
    }

    fun setNextImagePath(path: String?) {
        _nextImagePath.value = path
    }

    fun setPictureTransitionAlpha(alpha: Float) {
        _pictureTransitionAlpha.value = alpha
    }

    fun setPreviousDisplayedImagePath(path: String?) {
        _previousDisplayedImagePath.value = path
    }

    fun setPictureSlideOffset(offset: Float) {
        _pictureSlideOffset.value = offset
    }

    fun setAnimationType(type: AnimationType) {
        _animationType.value = type
    }

    fun setTransitionDuration(duration: Int) {
        _transitionDuration.value = duration
    }

    fun togglePresenterWindow() {
        _showPresenterWindow.value = !_showPresenterWindow.value
    }

    fun setShowPresenterWindow(show: Boolean) {
        _showPresenterWindow.value = show
    }

    fun setSelectedSlide(slide: ImageBitmap?) {
        _selectedSlide.value = slide
    }

    // ── Animated presentation playback ───────────────────────────────────────
    // The player is a rendering bridge like LottieFrameStream: one evaluation per display
    // frame, published here, drawn by every output window's PresentationPresenter.

    private val _presentationFrame = mutableStateOf<PresentationFrame?>(null)
    val presentationFrame: State<PresentationFrame?> = _presentationFrame

    private var presentationPlayer: PresentationPlayer? = null

    /**
     * Points playback at [deck]/[slideIndex]. Decks with no timing at all (plain PDFs, static
     * exports) clear the player — the static bitmap path with the app's configured slide
     * transition then renders as before. A deck with any timeline or deck-defined transition
     * plays entirely through the player so its own transitions win consistently.
     *
     * [enterAtLastStep] is true only for genuine backward navigation (the operator stepping to
     * the *previous* slide) — real PowerPoint/Keynote show that slide fully built, as the
     * audience last saw it, rather than resetting it to the pre-click state.
     */
    fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean = false) {
        val deckIsAnimated = deck.slides.any { it.timeline != null || it.transition != null }
        if (!deckIsAnimated) {
            clearPresentationPlayback()
            return
        }
        // Same-slide idempotence: when the player is already showing exactly this slide AND it
        // is visible on the output, keep its build state untouched — re-selecting the live
        // slide's thumbnail, focus-recovery paths, or a repeated Go Live must never restart the
        // slide's animations mid-service. Slide changes, cleared displays and different decks
        // fall through to the full (re)show, where starting from the pre-click state is correct.
        if (steppablePlayer(deck, slideIndex) != null) return
        val player = presentationPlayer?.takeIf { it.deck === deck }
            ?: PresentationPlayer(deck).also { fresh ->
                presentationPlayer?.close()
                presentationPlayer = fresh
            }
        player.showSlide(slideIndex, enterAtLastStep)
        _presentationFrame.value = null // published by the clock once layers are rasterized
    }

    /**
     * Advances one build step of the live animated slide. Identity-guarded: acts only when the
     * player is showing exactly [deck]/[slideIndex] AND that content is actually visible —
     * otherwise the keypress must fall through to plain slide navigation instead of being
     * silently eaten by a player the operator can't see (cleared display, different deck, or a
     * player created ahead of the grid selection).
     * False = caller changes the slide instead.
     */
    fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean =
        steppablePlayer(deck, slideIndex)?.advance(System.nanoTime()) ?: false

    /** Steps one build back. Same identity guard; false = caller changes the slide instead. */
    fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean =
        steppablePlayer(deck, slideIndex)?.rewind() ?: false

    private fun steppablePlayer(deck: Deck, slideIndex: Int): PresentationPlayer? {
        val player = presentationPlayer ?: return null
        if (player.deck !== deck || player.currentSlideIndex != slideIndex) return null
        // A cleared display blanks the mode-driven output — steps would be invisible there.
        // (Screen-lock visibility is the caller's gate, as with all lock-aware behavior.)
        val visibleViaMode = _presentingMode.value == Presenting.PRESENTATION && !_clearDisplayRequested.value
        val visibleViaLock = _screenLocks.value.values.any { it == Presenting.PRESENTATION }
        return if (visibleViaMode || visibleViaLock) player else null
    }

    fun clearPresentationPlayback() {
        presentationPlayer?.close()
        presentationPlayer = null
        _presentationFrame.value = null
    }

    /**
     * Frame clock body, driven from main.kt (same pattern as the Lottie clock): publishes the
     * evaluated frame on every display frame while a step animates, and idles cheaply when
     * settled or when no animated presentation is live.
     */
    suspend fun runPresentationClock() {
        while (true) {
            val player = presentationPlayer
            if (player == null) {
                delay(100)
                continue
            }
            withFrameNanos { now ->
                if (presentationPlayer === player) {
                    _presentationFrame.value = player.frame(now)
                }
            }
            if (!player.isAnimating(System.nanoTime())) {
                // Settled: keep the last frame, poll for the next advance/slide change.
                delay(33)
            }
        }
    }

    fun setDisplayedSlide(slide: ImageBitmap?) {
        _displayedSlide.value = slide
    }

    fun setNextSlide(slide: ImageBitmap?) {
        _nextSlide.value = slide
    }

    fun setPresenterNotes(notes: String) {
        _presenterNotes.value = notes
    }

    fun setSlideTransitionAlpha(alpha: Float) {
        _slideTransitionAlpha.value = alpha
    }

    fun setPreviousDisplayedSlide(slide: ImageBitmap?) {
        _previousDisplayedSlide.value = slide
    }

    fun setSlideSlideOffset(offset: Float) {
        _slideSlideOffset.value = offset
    }

    fun setDisplayedAnnouncementText(text: String) {
        _displayedAnnouncementText.value = text
    }

    fun setAnnouncementTransitionAlpha(alpha: Float) {
        _announcementTransitionAlpha.value = alpha
    }

    fun setLottieProgress(progress: Float) {
        _lottieProgress.value = progress
    }

    fun setMediaTransitionAlpha(alpha: Float) {
        _mediaTransitionAlpha.value = alpha
    }

    private val _lottieJsonContent = mutableStateOf("")
    val lottieJsonContent: State<String> = _lottieJsonContent

    private val _lottiePauseAtFrame = mutableStateOf(false)
    val lottiePauseAtFrame: State<Boolean> = _lottiePauseAtFrame

    private val _lottiePauseDurationMs = mutableStateOf(2000L)
    val lottiePauseDurationMs: State<Long> = _lottiePauseDurationMs

    private val _lottieTrigger = mutableStateOf(0)
    val lottieTrigger: State<Int> = _lottieTrigger

    // Preset filename (no extension) the current Lottie content came from — PresenterManager only
    // ever held the rendered JSON itself before, with nothing identifying which preset it was, so
    // there was nothing for InstanceLink's live-state broadcast to report. Purely informational
    // (rendering only ever uses lottieJsonContent); defaults to "" for content set some other way.
    private val _currentLowerThirdName = mutableStateOf("")
    val currentLowerThirdName: State<String> = _currentLowerThirdName

    fun setLottieContent(json: String, pauseAtFrame: Boolean, pauseFrame: Float, pauseDurationMs: Long, presetName: String = "") {
        _lottieJsonContent.value = json
        _lottiePauseAtFrame.value = pauseAtFrame
        _lottiePauseFrame.value = pauseFrame
        _lottiePauseDurationMs.value = pauseDurationMs
        _lottieTrigger.value++
        _currentLowerThirdName.value = presetName
        notifyLiveStateChanged(Presenting.LOWER_THIRD)

        // Clear stale frames immediately so the presenter falls back to compottie
        preRenderJob?.cancel()
        _lottieFrameCount.value = null
        _lottieFrame.value = null
        _lottiePrerenderFps.value = LottieRenderCache.PLAYBACK_FPS
        _lottieCurrentFrameIndex.value = 0
        lottieFrameStream?.close()
        lottieFrameStream = null

        if (json.isNotBlank()) {
            preRenderJob = preRenderScope.launch {
                var stream: LottieFrameStream? = null
                var published = false
                try {
                    val variant = LottieRenderCache.desktopVariant(json, atemRenderSettings)
                        ?: return@launch // JSON has no timing — stay on the live renderer
                    // Instant on a cache hit; renders in the background otherwise
                    val cached = LottieRenderCache.prepare(json, variant).await()
                    stream = LottieFrameStream(file = cached, scope = preRenderScope) { frame ->
                        // Identity guard: a decode already in flight when newer content replaced
                        // this stream must not publish into the new content's cleared state (the
                        // stale bitmap would be closed by this stream's teardown while displayed).
                        if (lottieFrameStream === stream) _lottieFrame.value = frame
                    }
                    if (!stream.open()) {
                        // A silently-blank off-screen render: discard the cache file (so a later
                        // play re-renders instead of re-reading the blank entry) and keep using
                        // the live renderer — better than switching to bitmaps of nothing.
                        stream.close()
                        cached.delete()
                        CrashReporter.reportWarning(
                            "Lottie pre-render produced blank frames, discarded",
                            tags = mapOf("subsystem" to "lower_third")
                        )
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        // Do NOT reset _lottieCurrentFrameIndex here — main.kt's playback loop
                        // may already be partway through this play (it switches to raw frames
                        // the instant they're ready, not just at play start) and derives the
                        // correct index from real elapsed time on its very next tick. Resetting
                        // to 0 here raced that loop and caused a visible flash back to frame 0
                        // right at the switchover moment.
                        lottieFrameStream = stream
                        _lottiePrerenderFps.value = variant.fps.toInt()
                        _lottieFrameCount.value = stream.frameCount
                        published = true
                    }
                    // Serve the current playhead position immediately so the switchover from the
                    // live renderer has a frame to draw on its very first tick.
                    stream.requestFrame(_lottieCurrentFrameIndex.value)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    System.err.println("[PresenterManager] Lottie pre-render failed: ${e.message}")
                    CrashReporter.reportException(e, "Lottie pre-render")
                } finally {
                    // Newer content cancelled this job after the stream was opened but before
                    // it was adopted — release it, it will never be drawn.
                    if (!published) stream?.close()
                }
            }
        }
    }

    private val _lottiePauseFrame = mutableStateOf(-1f)
    val lottiePauseFrame: State<Float> = _lottiePauseFrame

    private val _announcementText = mutableStateOf("")
    val announcementText: State<String> = _announcementText

    fun setAnnouncementText(text: String) {
        _announcementText.value = text
        notifyLiveStateChanged(Presenting.ANNOUNCEMENTS)
    }

    private val _websiteUrl = mutableStateOf("")
    val websiteUrl: State<String> = _websiteUrl

    fun setWebsiteUrl(url: String) {
        _websiteUrl.value = url
        notifyLiveStateChanged(Presenting.WEBSITE)
    }

    private val _webPageTitle = mutableStateOf("")
    val webPageTitle: State<String> = _webPageTitle

    fun setWebPageTitle(title: String) {
        _webPageTitle.value = title
        notifyLiveStateChanged(Presenting.WEBSITE)
    }

    // Periodically-updated screenshot of the live WebView — used by LivePreviewPanel
    // to mirror the exact visible content without needing a second WebView instance.
    private val _webSnapshot = mutableStateOf<ImageBitmap?>(null)
    val webSnapshot: State<ImageBitmap?> = _webSnapshot

    fun setWebSnapshot(bitmap: ImageBitmap?) {
        _webSnapshot.value = bitmap
    }

    // Reference to the presenter's live CefBrowser — used by WebTab to forward input events
    private val _liveBrowser = mutableStateOf<CefBrowser?>(null)
    val liveBrowser: State<CefBrowser?> = _liveBrowser

    fun setLiveBrowser(browser: CefBrowser?) {
        _liveBrowser.value = browser
    }

    // Scene compositor state
    private val _activeScene = mutableStateOf<Scene?>(null)
    val activeScene: State<Scene?> = _activeScene

    fun setActiveScene(scene: Scene?) {
        _activeScene.value = scene
        notifyLiveStateChanged(Presenting.CANVAS)
    }

    // ── Announcements timer/clock ───────────────────────────────────────────
    // Ticks here — not in the Announcements tab's ViewModel — so switching to another tab (e.g.
    // to present a Bible verse) doesn't kill the coroutine and freeze the countdown. Stage
    // Monitor and the live output both observe timerRemainingSeconds/timerRunning directly.
    private val _timerRemainingSeconds = mutableStateOf(0)
    val timerRemainingSeconds: State<Int> = _timerRemainingSeconds

    private val _timerRunning = mutableStateOf(false)
    val timerRunning: State<Boolean> = _timerRunning

    // Unlike [_timerRunning] (which is only ever true for Duration/Count-Up, the two modes with a
    // pause concept), this is true whenever ANY of the four ticker modes below is actively pushing
    // its value into the shared announcementText slot every second — including Specific Time and
    // Clock Display, which are otherwise always-on. Switching to plain announcement text must check
    // this, not [_timerRunning], or the still-running ticker silently overwrites the text a second later.
    private val _announcementTickerActive = mutableStateOf(false)
    val announcementTickerActive: State<Boolean> = _announcementTickerActive

    // True only once the operator has explicitly pushed the ticker's content live via Go Live or
    // Send to Stage Monitor — NOT merely because the ticker is running. This is what the play/pause
    // button in the Announcements tab must gate on so pressing it while only previewing (never gone
    // live) can never touch the presenter screen; only Go Live/Send to Stage Monitor may set this
    // true. Deliberately NOT cleared by pauseAnnouncementTimer() itself — once live, pausing the
    // ticker freezes the on-screen value (last push stands) and pressing play again resumes
    // pushing live, so a live countdown can be paused/resumed with the same button without a
    // separate "stop timer on presenter" control.
    private val _announcementTickerLive = mutableStateOf(false)
    val announcementTickerLive: State<Boolean> = _announcementTickerLive

    fun setAnnouncementTickerLive(live: Boolean) {
        _announcementTickerLive.value = live
    }

    // True once a Duration countdown has run out — the authoritative signal the Announcements tab
    // uses to show the expired message/color, since it (unlike this manager) may have been
    // recreated since the countdown actually finished.
    private val _announcementTimerExpired = mutableStateOf(false)
    val announcementTimerExpired: State<Boolean> = _announcementTimerExpired

    private var announcementTickerJob: Job? = null

    /** Duration/Countdown — ticks down from [remainingSeconds] and shows [expiredText] on reaching zero. */
    fun startAnnouncementCountdown(remainingSeconds: Int, expiredText: String) {
        announcementTickerJob?.cancel()
        if (remainingSeconds <= 0) return
        val endEpochSecond = java.time.Instant.now().epochSecond + remainingSeconds
        _timerRemainingSeconds.value = remainingSeconds
        _timerRunning.value = true
        _announcementTickerActive.value = true
        _announcementTimerExpired.value = false
        announcementTickerJob = preRenderScope.launch {
            while (true) {
                val remaining = (endEpochSecond - java.time.Instant.now().epochSecond).toInt()
                if (remaining <= 0) break
                _timerRemainingSeconds.value = remaining
                pushAnnouncementTextIfLive(AnnouncementsViewModel.formatTimer(remaining))
                delay(1000L)
            }
            _timerRemainingSeconds.value = 0
            _timerRunning.value = false
            _announcementTickerActive.value = false
            _announcementTimerExpired.value = true
            pushAnnouncementTextIfLive(expiredText)
            setPresentingMode(Presenting.ANNOUNCEMENTS)
        }
    }

    /** Count-up — an open-ended stopwatch starting from [initialElapsedSeconds]. */
    fun startAnnouncementCountUp(initialElapsedSeconds: Int) {
        announcementTickerJob?.cancel()
        val startEpochSecond = java.time.Instant.now().epochSecond - initialElapsedSeconds
        _timerRemainingSeconds.value = initialElapsedSeconds
        _timerRunning.value = true
        _announcementTickerActive.value = true
        _announcementTimerExpired.value = false
        announcementTickerJob = preRenderScope.launch {
            while (true) {
                val elapsed = (java.time.Instant.now().epochSecond - startEpochSecond).toInt().coerceAtLeast(0)
                _timerRemainingSeconds.value = elapsed
                pushAnnouncementTextIfLive(AnnouncementsViewModel.formatTimer(elapsed))
                delay(1000L)
            }
        }
    }

    /** Specific Time — always-on countdown to the next occurrence of [targetHour]:[targetMinute]:[targetSecond]. */
    fun startAnnouncementSpecificTime(targetHour: Int, targetMinute: Int, targetSecond: Int) {
        announcementTickerJob?.cancel()
        _timerRunning.value = false
        _announcementTickerActive.value = true
        announcementTickerJob = preRenderScope.launch {
            while (true) {
                val nowSec = java.time.LocalTime.now().toSecondOfDay()
                val targetSec = targetHour * 3600 + targetMinute * 60 + targetSecond
                val diff = targetSec - nowSec
                val remaining = if (diff > 0) diff else diff + 86400
                _timerRemainingSeconds.value = remaining
                pushAnnouncementTextIfLive(AnnouncementsViewModel.formatTimer(remaining))
                delay(1000L)
            }
        }
    }

    /** Clock Display — always-on live wall clock formatted with [formatPattern]. */
    fun startAnnouncementClockDisplay(formatPattern: String) {
        announcementTickerJob?.cancel()
        _timerRunning.value = false
        _announcementTickerActive.value = true
        announcementTickerJob = preRenderScope.launch {
            while (true) {
                val text = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(formatPattern))
                pushAnnouncementTextIfLive(text)
                delay(1000L)
            }
        }
    }

    /** Pauses/stops whichever announcement ticker is active, optionally pinning the mirrored remaining value (e.g. on Reset). */
    fun pauseAnnouncementTimer(remainingSeconds: Int? = null) {
        announcementTickerJob?.cancel()
        _timerRunning.value = false
        _announcementTickerActive.value = false
        _announcementTimerExpired.value = false
        if (remainingSeconds != null) _timerRemainingSeconds.value = remainingSeconds
    }

    private fun pushAnnouncementTextIfLive(text: String) {
        val anyScreenOnAnnouncements = _presentingMode.value == Presenting.ANNOUNCEMENTS ||
            _screenLocks.value.values.any { it == Presenting.ANNOUNCEMENTS }
        if (anyScreenOnAnnouncements && _announcementTickerLive.value) setAnnouncementText(text)
    }

    // Q&A display state
    private val _displayedQuestion = mutableStateOf<Question?>(null)
    val displayedQuestion: State<Question?> = _displayedQuestion

    private val _qaTransitionAlpha = mutableStateOf(1f)
    val qaTransitionAlpha: State<Float> = _qaTransitionAlpha

    private val _showQRCodeOnDisplay = mutableStateOf(false)
    val showQRCodeOnDisplay: State<Boolean> = _showQRCodeOnDisplay

    fun setDisplayedQuestion(question: Question?) {
        _displayedQuestion.value = question
        notifyLiveStateChanged(Presenting.QA)
    }

    fun setQaTransitionAlpha(alpha: Float) {
        _qaTransitionAlpha.value = alpha
    }

    fun setShowQRCodeOnDisplay(show: Boolean) {
        _showQRCodeOnDisplay.value = show
    }

    // Dictionary display state
    private val _displayedDictionaryEntry = mutableStateOf<StrongsEntry?>(null)
    val displayedDictionaryEntry: State<StrongsEntry?> = _displayedDictionaryEntry

    fun setDisplayedDictionaryEntry(entry: StrongsEntry?) {
        _displayedDictionaryEntry.value = entry
        notifyLiveStateChanged(Presenting.DICTIONARY)
    }
}
