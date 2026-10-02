package org.churchpresenter.slides.viewmodel

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.core.models.presentation.PresentationLoadError
import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.presentationengine.cache.SlideDiskCache
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.presentationengine.model.DeckLoadError
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.slides.data.HiddenItemsStore
import org.churchpresenter.slides.data.nextVisibleIndex
import java.awt.image.BufferedImage
import java.io.File

/**
 * Orchestrates presentation loading for the Presentation tab. All parsing and rendering lives in
 * the presentation engine ([PresentationLoader]/[DeckRasterizer], the :presentation-engine
 * module); this class owns UI state, the shared slide disk cache and job lifecycle only.
 *
 * The work is split across parts that share one [PresentationState]: hidden slides
 * ([HiddenSlides]), a cue's playback ([SlidePlayback]), moving through the deck ([SlideNavigator])
 * and filling it ([SlideLoader]). Their public functions are this class's own, by delegation.
 */
class PresentationViewModel private constructor(
    private val parts: PresentationParts,
) : SlideNavigation by parts.navigator,
    SlidePlaybackControls by parts.playback {

    constructor(
        appSettings: AppSettings? = null,
        /** Where hidden slides are remembered -- a parameter so a test can keep them in a temp dir. */
        hiddenStore: HiddenItemsStore = HiddenItemsStore(),
    ) : this(PresentationParts(appSettings, hiddenStore, diskCache))

    companion object {
        private val diskCache = SlideDiskCache()

        /**
         * Called on startup: deletes any slide cache folders whose source file is not in
         * [keepPaths] (the union of recent and pinned presentation paths).
         */
        fun cleanupOrphanedCaches(keepPaths: Collection<String>) {
            diskCache.cleanupOrphaned(keepPaths)
        }
    }

    private val state = parts.state
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeLoadJob: Job? = null

    val presentations: List<File> = state.presentations

    val selectedPresentation: File?
        get() = state.selectedPresentation.value

    /** The primary's path, but only while it still describes the current selection. */
    private val remotePathForSelection: String?
        get() = state.selectedPresentation.value?.let { file ->
            state.remotePresentationPath.value?.takeIf { it.first == file }?.second
        }

    /**
     * The full path shown for the current deck: the primary's verbatim when mirrored, the local
     * absolute path otherwise. Null when nothing is selected.
     */
    val selectedPresentationDisplayPath: String?
        get() = remotePathForSelection ?: state.selectedPresentation.value?.absolutePath

    /**
     * The file name shown for the current deck.
     *
     * A mirrored path is split on both separators rather than through [File.getName], which knows
     * only the local one — `File("C:\\Presentations\\Sunday.pptx").name` on a Mac or a Linux box is
     * the whole string, not `Sunday.pptx`. Same idiom as `data/Songs.kt` and the file pickers.
     */
    val selectedPresentationDisplayName: String?
        get() = remotePathForSelection?.substringAfterLast('/')?.substringAfterLast('\\')
            ?: state.selectedPresentation.value?.name

    val slideFiles: SnapshotStateList<File> = state.slideFiles

    val totalSlides: Int get() = state.totalSlides.value

    /** Incremented each time slides finish loading (fresh render or cache hit). Use as LaunchedEffect key. */
    val loadGeneration: Int get() = state.loadGeneration.value

    val slideNotes: List<String> get() = state.slideNotes

    /**
     * The parsed deck of the selected presentation (null while loading, for remote-mirrored
     * slides, and on load failure). Later workstreams read layers/timelines from here for
     * animated playback; the JPEG [slideFiles] remain the static path.
     */
    val deck: Deck? get() = state.deck.value

    val selectedSlideIndex: Int
        get() = state.selectedSlideIndex.value

    /**
     * The slides of the selected presentation the operator has hidden (#676): Next, Previous and the
     * slideshow pass over them, but a click still shows one. Remembered per file.
     */
    val hiddenSlides: Set<Int>
        get() = state.hiddenSlides.value

    val isPlaying: Boolean
        get() = state.isPlaying.value

    val isLoading: Boolean
        get() = state.isLoading.value

    val loadError: PresentationLoadError?
        get() = state.loadError.value

    var autoScrollInterval: Float
        get() = state.autoScrollInterval.value
        set(value) { state.autoScrollInterval.value = value }

    var isLooping: Boolean
        get() = state.isLooping.value
        set(value) { state.isLooping.value = value }

    var transitionDuration: Float
        get() = state.transitionDuration.value
        set(value) { state.transitionDuration.value = value }

    var animationType: AnimationType
        get() = state.animationType.value
        set(value) { state.animationType.value = value }

    /**
     * Parses a deck file. Overridable per-instance so tests can drive the load-failure and
     * warning-degrade branches of the render without a fixture that reproduces each engine
     * error; production always uses the real [PresentationLoader]. An instance seam, not a
     * singleton one — each ViewModel has its own, so nothing leaks between tests.
     */
    internal var loadDeck: (File) -> LoadResult
        get() = parts.loader.loadDeck
        set(value) { parts.loader.loadDeck = value }

    /**
     * Rasterizes one slide to an image — the single step of the render that needs a real
     * graphics pipeline. Overridable per-instance so tests can drive the per-slide-failure and
     * no-slides branches with a real deck and a real rasterizer, failing only at the frame render;
     * production always calls straight through to [DeckRasterizer]. A per-instance seam, so nothing
     * leaks between tests.
     */
    internal var renderSlideFrame: (DeckRasterizer, Int) -> BufferedImage
        get() = parts.loader.renderSlideFrame
        set(value) { parts.loader.renderSlideFrame = value }

    /** The slide loader, for a test that asks it about one deck directly. */
    internal val loading: SlideLoader get() = parts.loader

    /** Hides slide [index] of the selected presentation, or shows it again. */
    fun toggleSlideHidden(index: Int) = parts.hidden.toggle(index)

    /** The slide Next would go to from [index] without wrapping -- what a stage monitor previews. */
    fun nextShownSlideIndex(index: Int = state.selectedSlideIndex.value): Int? =
        nextVisibleIndex(index, 1, state.slideFiles.size, state.hiddenSlides.value, wrap = false)

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadPresentationByPath(filePath: String) = addPresentation(File(filePath))

    /**
     * Loads a presentation from an Instance Link primary when [filePath] doesn't resolve on this
     * machine (e.g. a mirrored schedule item whose file lives on a network drive mounted
     * differently, or not mounted at all, here). Downloads each slide's JPEG bytes via [fetchBytes]
     * into a disk cache keyed by [scheduleItemId] (reusing the same cache-dir idiom local rendering
     * uses) and populates [slideFiles] from the cached files — no new rendering pipeline, just a
     * remote source of already-cached slides. [filePath] is the primary's own path: the synthetic
     * [File] built from it is an identity, so [selectedPresentation] and downstream consumers
     * (recents list, onSlidesLoaded broadcast) keep working the same as the local-file path, while
     * the string itself is kept verbatim for [selectedPresentationDisplayPath] to show. The file is
     * never opened either way.
     */
    fun loadPresentationFromRemote(
        scheduleItemId: String,
        filePath: String,
        slideCount: Int,
        fetchBytes: suspend (index: Int) -> ByteArray?
    ) {
        val syntheticFile = File(filePath)
        val existingFile = state.presentations.find { it.absolutePath == syntheticFile.absolutePath }
        if (existingFile == null) state.presentations.add(syntheticFile)
        state.selectedPresentation.value = existingFile ?: syntheticFile
        state.remotePresentationPath.value = (existingFile ?: syntheticFile) to filePath
        state.selectedSlideIndex.value = 0
        parts.hidden.loadFor(existingFile ?: syntheticFile)
        state.loadError.value = null
        activeLoadJob?.cancel()
        activeLoadJob = scope.launch {
            withContext(Dispatchers.Main) {
                state.clearCurrentSlideState()
                state.totalSlides.value = slideCount
                state.isLoading.value = true
            }
            val cacheDir = File(
                File(System.getProperty("user.home"), ".churchpresenter/slides"),
                "remote_$scheduleItemId"
            )
                .also { it.mkdirs() }
            parts.loader.downloadSlides(cacheDir, slideCount, fetchBytes)
        }
    }

    fun addPresentation(file: File) {
        if (file.exists() && isValidPresentationFile(file)) {
            val existingFile = state.presentations.find { it.absolutePath == file.absolutePath }
            if (existingFile == null) state.presentations.add(file)
            selectPresentation(file)
        }
    }

    /**
     * Removes a presentation from the open list.
     * [isInRecentsOrPinned] — when false the disk cache for that file is also deleted.
     */
    fun removePresentation(file: File, isInRecentsOrPinned: Boolean = true) {
        state.presentations.removeAll { it.absolutePath == file.absolutePath }
        if (!isInRecentsOrPinned) diskCache.invalidate(file)
        if (state.selectedPresentation.value?.absolutePath == file.absolutePath) {
            state.selectedPresentation.value = state.presentations.firstOrNull()
            state.selectedPresentation.value?.let { selectPresentation(it) } ?: run {
                state.remotePresentationPath.value = null
                state.clearCurrentSlideState()
                state.selectedSlideIndex.value = 0
            }
        }
    }

    fun selectPresentation(file: File) {
        val existingFile = state.presentations.find { it.absolutePath == file.absolutePath }
        if (existingFile != null) {
            state.selectedPresentation.value = existingFile
            // Only when the selection actually moves elsewhere: re-selecting the mirrored deck
            // itself must keep showing the primary's path, not fall back to the mangled one.
            if (state.remotePresentationPath.value?.first != existingFile) state.remotePresentationPath.value = null
            state.selectedSlideIndex.value = 0
            parts.hidden.loadFor(existingFile)
            state.loadError.value = null
            activeLoadJob?.cancel()
            activeLoadJob = scope.launch { parts.loader.loadOrCacheSlides(existingFile) }
        }
    }

    fun clearPresentations() {
        activeLoadJob?.cancel()
        state.presentations.clear()
        state.selectedPresentation.value = null
        state.remotePresentationPath.value = null
        state.clearCurrentSlideState()
        state.totalSlides.value = 0
        state.selectedSlideIndex.value = 0
        state.isLoading.value = false
    }

    fun dispose() {
        activeLoadJob?.cancel()
        scope.cancel()
    }
}

/** The parts of one [PresentationViewModel], built together so they share one state. */
internal class PresentationParts(
    appSettings: AppSettings?,
    hiddenStore: HiddenItemsStore,
    diskCache: SlideDiskCache,
) {
    val state = PresentationState(appSettings)
    val hidden = HiddenSlides(state, hiddenStore)
    val playback = SlidePlayback(state)
    val navigator = SlideNavigator(state, playback)
    val loader = SlideLoader(state, appSettings, diskCache, hidden, playback)
}

internal fun DeckLoadError.toUiError(): PresentationLoadError = when (this) {
    DeckLoadError.PASSWORD_PROTECTED -> PresentationLoadError.PASSWORD_PROTECTED
    DeckLoadError.EMPTY_DOCUMENT -> PresentationLoadError.EMPTY_DOCUMENT
    DeckLoadError.UNSUPPORTED_FORMAT, DeckLoadError.PARSE_FAILED -> PresentationLoadError.RENDER_FAILED
}

private fun isValidPresentationFile(file: File): Boolean =
    file.extension.lowercase() in PresentationLoader.SUPPORTED_EXTENSIONS

/**
 * Which load failures are a fact about the operator's file rather than about this code.
 *
 * A deck the app cannot open because it is locked, or because it has no slides in it, is
 * answered by the dialog — [DeckLoadError.PASSWORD_PROTECTED] and [DeckLoadError.EMPTY_DOCUMENT]
 * both have their own message on screen — and there is nothing here to fix. Reported anyway,
 * they were indistinguishable from a parse regression: one issue, five churches, and no way to
 * tell "someone opened a protected deck" from "the parser broke". The same reasoning, and the
 * same classify-by-type shape, as the Bible installer's `isOperatorEnvironment`.
 */
internal fun DeckLoadError.isOperatorFile(): Boolean =
    this == DeckLoadError.PASSWORD_PROTECTED || this == DeckLoadError.EMPTY_DOCUMENT
