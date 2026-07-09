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
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdOffscreenRenderer
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.LowerThirdDebugLog
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.server.AtemRenderCache

class PresenterManager {

    companion object {
        const val PRERENDER_FPS = 30

        // Held simultaneously as raw ARGB frames for the lifetime of the lower third —
        // must stay well under the heap ceiling alongside everything else the app needs.
        private const val MAX_PRERENDER_BYTES = 1_200_000_000L

        // Output windows render pre-rendered frames with ContentScale.Fit, so a canvas
        // larger than the app's default render resolution wastes memory with no visual benefit.
        private const val MAX_CANVAS_DIMENSION = 1920

        // Floors tried, in order, before giving up on pre-rendering a clip that doesn't fit
        // MAX_PRERENDER_BYTES at full quality. Resolution is spent first since it has no effect
        // on motion smoothness for a lower-third overlay; frame rate is the last resort since
        // that's what "smooth" actually depends on — pre-rendering exists specifically because
        // live/GPU-driven playback was unreliable on some hardware, so a clip should almost never
        // fall back to that path just for being long or large.
        private const val MIN_CANVAS_DIMENSION = 720
        private const val MIN_PRERENDER_FPS = 15
    }

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

    // Pre-rendered ARGB frames for display — populated in background when content is set
    private val _lottieRawFrames = mutableStateOf<List<IntArray>?>(null)
    val lottieRawFrames: State<List<IntArray>?> = _lottieRawFrames

    private val _lottieRawFrameSize = mutableStateOf<Pair<Int, Int>?>(null)
    val lottieRawFrameSize: State<Pair<Int, Int>?> = _lottieRawFrameSize

    // The frame rate the current _lottieRawFrames were actually rendered at — may be below
    // PRERENDER_FPS when a clip was degraded to fit MAX_PRERENDER_BYTES. main.kt's playback loop
    // must pace itself off this, not the PRERENDER_FPS constant, or a degraded clip plays too fast.
    private val _lottiePrerenderFps = mutableStateOf(PRERENDER_FPS)
    val lottiePrerenderFps: State<Int> = _lottiePrerenderFps

    private val _lottieCurrentFrameIndex = mutableStateOf(0)
    val lottieCurrentFrameIndex: State<Int> = _lottieCurrentFrameIndex

    fun setLottieCurrentFrameIndex(index: Int) {
        _lottieCurrentFrameIndex.value = index
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
            // DIAGNOSTIC (temporary — see AGENT.md "lower third disappears mid-playback")
            if (_presentingMode.value == Presenting.LOWER_THIRD || mode == Presenting.LOWER_THIRD) {
                LowerThirdDebugLog.log("setPresentingMode: ${_presentingMode.value} -> $mode")
            }
        }
        _presentingMode.value = mode
        if (mode != Presenting.NONE) {
            _clearDisplayRequested.value = false
            // Reset transition alphas so presenters are visible when going live
            // (fade-in inside the presenter handles the actual animation)
            _bibleTransitionAlpha.value = 1f
            _songTransitionAlpha.value = 1f
        }
        notifyLiveStateChanged(mode)
    }

    /** Request a fade-out before clearing the display. The LaunchedEffect in main.kt
     *  watches this flag, animates bibleTransitionAlpha/songTransitionAlpha to 0,
     *  then sets presentingMode to NONE. */
    fun requestClearDisplay() {
        if (_presentingMode.value != Presenting.NONE) {
            // DIAGNOSTIC (temporary — see AGENT.md "lower third disappears mid-playback")
            if (_presentingMode.value == Presenting.LOWER_THIRD) {
                val caller = Thread.currentThread().stackTrace
                    .firstOrNull { it.className.startsWith("org.churchpresenter") && !it.methodName.contains("requestClearDisplay") }
                LowerThirdDebugLog.log("requestClearDisplay() called while presenting LOWER_THIRD, caller=${caller?.className}.${caller?.methodName}:${caller?.lineNumber}")
            }
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
        _lottieRawFrames.value = null
        _lottieRawFrameSize.value = null
        _lottiePrerenderFps.value = PRERENDER_FPS
        _lottieCurrentFrameIndex.value = 0
        preRenderJob?.cancel()

        if (json.isNotBlank()) {
            preRenderJob = preRenderScope.launch {
                try {
                    val (rawW, rawH) = AtemRenderCache.lottieCanvasSize(json) ?: (1920 to 1080)
                    val (clampedW, clampedH) = clampCanvasSize(rawW, rawH)
                    var w = clampedW
                    var h = clampedH
                    var fps = PRERENDER_FPS
                    var frameCount = AtemRenderCache.clipFrameCount(json, fps.toDouble()) ?: return@launch
                    var estimatedBytes = frameCount.toLong() * w.toLong() * h.toLong() * 4L

                    // 1. Shrink resolution first — it's free quality to spend, since it has no
                    // effect on motion smoothness for a lower-third overlay.
                    while (estimatedBytes > MAX_PRERENDER_BYTES && maxOf(w, h) > MIN_CANVAS_DIMENSION) {
                        w = (w * 0.85).toInt().coerceAtLeast(1)
                        h = (h * 0.85).toInt().coerceAtLeast(1)
                        estimatedBytes = frameCount.toLong() * w.toLong() * h.toLong() * 4L
                    }

                    // 2. Only reduce frame rate if the resolution floor alone wasn't enough — fps
                    // is what "smooth" actually depends on, so it's the last resort before giving up.
                    if (estimatedBytes > MAX_PRERENDER_BYTES && fps > MIN_PRERENDER_FPS) {
                        fps = MIN_PRERENDER_FPS
                        frameCount = AtemRenderCache.clipFrameCount(json, fps.toDouble()) ?: return@launch
                        estimatedBytes = frameCount.toLong() * w.toLong() * h.toLong() * 4L
                    }

                    if (estimatedBytes > MAX_PRERENDER_BYTES) {
                        // Still too big even at minimum quality (an extreme, e.g. multi-minute,
                        // clip) — fall back to live rendering, same as before this change.
                        CrashReporter.reportWarning(
                            "Lottie pre-render skipped even at minimum quality: estimated size exceeds budget",
                            tags = mapOf(
                                "subsystem" to "lower_third",
                                "estimatedBytes" to estimatedBytes.toString(),
                                "frameCount" to frameCount.toString(),
                                "width" to w.toString(),
                                "height" to h.toString(),
                                "fps" to fps.toString()
                            )
                        )
                        return@launch
                    }

                    if (w != clampedW || h != clampedH || fps != PRERENDER_FPS) {
                        CrashReporter.breadcrumb(
                            "Lottie pre-render degraded to fit budget: ${w}x$h @ ${fps}fps",
                            category = "lower_third"
                        )
                    }

                    // DIAGNOSTIC (temporary — see AGENT.md "lower third disappears mid-playback"):
                    // this is where a large, long clip materializes as a single JVM-heap-resident
                    // List<IntArray> — log the size decision and heap before/after to check whether
                    // this allocation is approaching the -Xmx ceiling.
                    LowerThirdDebugLog.log(
                        "Lottie pre-render starting: ${frameCount} frames @ ${w}x$h @ ${fps}fps, " +
                            "estimated ${estimatedBytes / 1_000_000}MB, ${LowerThirdDebugLog.heapStats()}"
                    )
                    val renderer = LowerThirdOffscreenRenderer(w, h)
                    val frames = renderer.renderAllFrames(json, frameCount)
                    LowerThirdDebugLog.log("Lottie pre-render finished: ${frames.size} frames, ${LowerThirdDebugLog.heapStats()}")

                    // Discard a pre-render that silently rendered (near-)blank frames instead of
                    // publishing it — better to keep using the live renderer for the whole clip
                    // than to switch playback over to bitmaps of nothing.
                    val sampleIndices = listOf(frames.size / 4, frames.size / 2, frames.size * 3 / 4, frames.size - 1)
                        .map { it.coerceIn(0, frames.size - 1) }
                        .distinct()
                    val blankCount = sampleIndices.count { isFrameBlank(frames[it]) }
                    if (blankCount > sampleIndices.size / 2) {
                        LowerThirdDebugLog.log(
                            "Lottie pre-render discarded: $blankCount/${sampleIndices.size} sampled frames blank"
                        )
                        CrashReporter.reportWarning(
                            "Lottie pre-render produced blank frames, discarded",
                            tags = mapOf(
                                "subsystem" to "lower_third",
                                "blankCount" to blankCount.toString(),
                                "sampled" to sampleIndices.size.toString()
                            )
                        )
                    } else {
                        withContext(Dispatchers.Main) {
                            _lottieRawFrameSize.value = w to h
                            _lottiePrerenderFps.value = fps
                            _lottieRawFrames.value = frames
                            _lottieCurrentFrameIndex.value = 0
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    System.err.println("[PresenterManager] Lottie pre-render failed: ${e.message}")
                    LowerThirdDebugLog.logException("Lottie pre-render (${LowerThirdDebugLog.heapStats()})", e)
                    CrashReporter.reportException(e, "Lottie pre-render")
                }
            }
        }
    }

    /**
     * Roughly estimates whether a rendered frame is meaningfully blank (near-fully transparent) —
     * used to detect a pre-render that silently produced nothing instead of the intended content.
     * Samples pixels at a stride rather than scanning the whole frame to stay cheap.
     */
    private fun isFrameBlank(frame: IntArray): Boolean {
        if (frame.isEmpty()) return true
        val sampleStep = maxOf(1, frame.size / 500)
        var sampled = 0
        var opaqueCount = 0
        var i = 0
        while (i < frame.size) {
            sampled++
            if ((frame[i] ushr 24) and 0xFF > 8) opaqueCount++
            i += sampleStep
        }
        return opaqueCount.toFloat() / sampled < 0.01f
    }

    /** Scales (w, h) down proportionally so neither side exceeds [MAX_CANVAS_DIMENSION]. */
    private fun clampCanvasSize(w: Int, h: Int): Pair<Int, Int> {
        val longestSide = maxOf(w, h)
        if (longestSide <= MAX_CANVAS_DIMENSION) return w to h
        val scale = MAX_CANVAS_DIMENSION.toDouble() / longestSide
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
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
        _announcementTimerExpired.value = false
        if (remainingSeconds != null) _timerRemainingSeconds.value = remainingSeconds
    }

    private fun pushAnnouncementTextIfLive(text: String) {
        val anyScreenOnAnnouncements = _presentingMode.value == Presenting.ANNOUNCEMENTS ||
            _screenLocks.value.values.any { it == Presenting.ANNOUNCEMENTS }
        if (anyScreenOnAnnouncements) setAnnouncementText(text)
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
