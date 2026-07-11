package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.PresentationLoadError
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import presentation.engine.DeckRasterizer
import presentation.engine.LoadResult
import presentation.engine.PresentationLoader
import presentation.engine.cache.SlideDiskCache
import presentation.engine.model.Deck
import presentation.engine.model.DeckFormat
import presentation.engine.model.DeckLoadError
import java.io.File

/**
 * Orchestrates presentation loading for the Presentation tab. All parsing and rendering lives in
 * the presentation engine ([PresentationLoader]/[DeckRasterizer], see
 * ChurchPresenter-PresentationEngine); this class owns UI state, the shared slide disk cache and
 * job lifecycle only.
 */
class PresentationViewModel(private val appSettings: AppSettings? = null) {

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

    private val _presentations = mutableStateListOf<File>()
    val presentations: List<File> = _presentations

    private val _selectedPresentation = mutableStateOf<File?>(null)
    val selectedPresentation: File?
        get() = _selectedPresentation.value

    private val _slideFiles = mutableStateListOf<File>()
    val slideFiles: SnapshotStateList<File> = _slideFiles

    private val _totalSlides = mutableStateOf(0)
    val totalSlides: Int get() = _totalSlides.value

    /** Incremented each time slides finish loading (fresh render or cache hit). Use as LaunchedEffect key. */
    private val _loadGeneration = mutableStateOf(0)
    val loadGeneration: Int get() = _loadGeneration.value

    private val _slideNotes = mutableStateListOf<String>()
    val slideNotes: List<String> get() = _slideNotes

    /**
     * The parsed deck of the selected presentation (null while loading, for remote-mirrored
     * slides, and on load failure). Later workstreams read layers/timelines from here for
     * animated playback; the JPEG [slideFiles] remain the static path.
     */
    private val _deck = mutableStateOf<Deck?>(null)
    val deck: Deck? get() = _deck.value

    private val _selectedSlideIndex = mutableStateOf(0)
    val selectedSlideIndex: Int
        get() = _selectedSlideIndex.value

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: Boolean
        get() = _isPlaying.value

    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean
        get() = _isLoading.value

    private val _loadError = mutableStateOf<PresentationLoadError?>(null)
    val loadError: PresentationLoadError?
        get() = _loadError.value

    private val _autoScrollInterval = mutableStateOf(appSettings?.presentationSettings?.autoScrollInterval ?: 5f)
    var autoScrollInterval: Float
        get() = _autoScrollInterval.value
        set(value) { _autoScrollInterval.value = value }

    private val _isLooping = mutableStateOf(appSettings?.presentationSettings?.isLooping ?: true)
    var isLooping: Boolean
        get() = _isLooping.value
        set(value) { _isLooping.value = value }

    private val _transitionDuration = mutableStateOf(appSettings?.presentationSettings?.transitionDuration ?: 500f)
    var transitionDuration: Float
        get() = _transitionDuration.value
        set(value) { _transitionDuration.value = value }

    private val _animationType = mutableStateOf(
        when (appSettings?.presentationSettings?.animationType) {
            Constants.ANIMATION_FADE -> AnimationType.FADE
            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
            Constants.ANIMATION_NONE -> AnimationType.NONE
            else -> AnimationType.CROSSFADE
        }
    )
    var animationType: AnimationType
        get() = _animationType.value
        set(value) { _animationType.value = value }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeLoadJob: Job? = null

    private fun clearCurrentSlideState() {
        _slideFiles.clear()
        _slideNotes.clear()
        _deck.value = null
    }

    private fun renderWidth(): Int =
        appSettings?.projectionSettings?.getAssignment(0)?.targetBoundsW?.takeIf { it > 0 }
            ?: DeckRasterizer.DEFAULT_TARGET_WIDTH_PX

    /**
     * The deck as exposed to playback. Keynote animation rides on a reverse-engineered parser,
     * so the "Animate Keynote" setting can hold .key decks on the static path (deck hidden →
     * PresenterManager never starts the player); static rendering is unaffected.
     */
    private fun exposableDeck(deck: Deck?): Deck? = deck?.takeUnless {
        it.format == DeckFormat.KEYNOTE && appSettings?.presentationSettings?.animateKeynote == false
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadPresentationByPath(filePath: String) {
        val file = File(filePath)
        if (file.exists() && isValidPresentationFile(file)) {
            val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
            if (existingFile == null) _presentations.add(file)
            selectPresentation(file)
        }
    }

    /**
     * Loads a presentation from an Instance Link primary when [filePath] doesn't resolve on this
     * machine (e.g. a mirrored schedule item whose file lives on a network drive mounted
     * differently, or not mounted at all, here). Downloads each slide's JPEG bytes via [fetchBytes]
     * into a disk cache keyed by [scheduleItemId] (reusing the same cache-dir idiom local rendering
     * uses) and populates [_slideFiles] from the cached files — no new rendering pipeline, just a
     * remote source of already-cached slides. [filePath] is only used to build a synthetic [File]
     * so [selectedPresentation] and downstream consumers (recents list, onSlidesLoaded broadcast)
     * keep working the same as the local-file path — the file itself is never opened.
     */
    fun loadPresentationFromRemote(
        scheduleItemId: String,
        filePath: String,
        slideCount: Int,
        fetchBytes: suspend (index: Int) -> ByteArray?
    ) {
        val syntheticFile = File(filePath)
        val existingFile = _presentations.find { it.absolutePath == syntheticFile.absolutePath }
        if (existingFile == null) _presentations.add(syntheticFile)
        _selectedPresentation.value = existingFile ?: syntheticFile
        _selectedSlideIndex.value = 0
        _loadError.value = null
        activeLoadJob?.cancel()
        activeLoadJob = scope.launch {
            withContext(Dispatchers.Main) {
                clearCurrentSlideState()
                _totalSlides.value = slideCount
                _isLoading.value = true
            }
            val cacheDir = File(File(System.getProperty("user.home"), ".churchpresenter/slides"), "remote_$scheduleItemId")
                .also { it.mkdirs() }
            var success = false
            try {
                for (index in 0 until slideCount) {
                    val slideFile = File(cacheDir, "slide_%04d.jpg".format(index))
                    if (!slideFile.exists()) {
                        val bytes = fetchBytes(index) ?: continue
                        val tmp = File(cacheDir, "${slideFile.name}.tmp")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(slideFile)) { tmp.delete(); continue }
                    }
                    withContext(Dispatchers.Main) { _slideFiles.add(slideFile); _slideNotes.add("") }
                }
                if (_slideFiles.isNotEmpty()) {
                    withContext(Dispatchers.Main) { _loadGeneration.value++ }
                    success = true
                } else {
                    withContext(Dispatchers.Main) { _loadError.value = PresentationLoadError.RENDER_FAILED }
                }
            } finally {
                if (!success) cacheDir.deleteRecursively()
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    fun addPresentation(file: File) {
        if (file.exists() && isValidPresentationFile(file)) {
            val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
            if (existingFile == null) _presentations.add(file)
            selectPresentation(file)
        }
    }

    /**
     * Removes a presentation from the open list.
     * [isInRecentsOrPinned] — when false the disk cache for that file is also deleted.
     */
    fun removePresentation(file: File, isInRecentsOrPinned: Boolean = true) {
        _presentations.removeAll { it.absolutePath == file.absolutePath }
        if (!isInRecentsOrPinned) diskCache.invalidate(file)
        if (_selectedPresentation.value?.absolutePath == file.absolutePath) {
            _selectedPresentation.value = _presentations.firstOrNull()
            _selectedPresentation.value?.let { selectPresentation(it) } ?: run {
                clearCurrentSlideState()
                _selectedSlideIndex.value = 0
            }
        }
    }

    fun selectPresentation(file: File) {
        val existingFile = _presentations.find { it.absolutePath == file.absolutePath }
        if (existingFile != null) {
            _selectedPresentation.value = existingFile
            _selectedSlideIndex.value = 0
            _loadError.value = null
            activeLoadJob?.cancel()
            activeLoadJob = scope.launch { loadOrCacheSlides(existingFile) }
        }
    }

    /** [onInstanceLinkSendNext] — Instance Link Controller mode, non-null only when connected and
     *  controlling. Invoked unconditionally (even when this Controller's own [_slideFiles] is empty,
     *  which is the normal case — Controller mode doesn't mirror the primary's content) so next/prev
     *  still reaches the primary's own currently-live presentation. See Constants.WS_CMD_NEXT_SLIDE. */
    fun nextSlide(onInstanceLinkSendNext: (() -> Unit)? = null) {
        if (_selectedSlideIndex.value < _slideFiles.size - 1) {
            _selectedSlideIndex.value++
        } else if (_isLooping.value && _slideFiles.isNotEmpty()) {
            _selectedSlideIndex.value = 0
        } else {
            _isPlaying.value = false
        }
        onInstanceLinkSendNext?.invoke()
    }

    fun previousSlide(onInstanceLinkSendPrevious: (() -> Unit)? = null) {
        if (_selectedSlideIndex.value > 0) {
            _selectedSlideIndex.value--
        } else if (_isLooping.value && _slideFiles.isNotEmpty()) {
            _selectedSlideIndex.value = _slideFiles.size - 1
        }
        onInstanceLinkSendPrevious?.invoke()
    }

    fun selectSlide(index: Int) {
        if (index in _slideFiles.indices) {
            _selectedSlideIndex.value = index
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun clearPresentations() {
        activeLoadJob?.cancel()
        _presentations.clear()
        _selectedPresentation.value = null
        clearCurrentSlideState()
        _totalSlides.value = 0
        _selectedSlideIndex.value = 0
        _isLoading.value = false
    }

    fun dispose() {
        activeLoadJob?.cancel()
        scope.cancel()
    }

    // ── Load / cache orchestration ────────────────────────────────────────────

    private suspend fun loadOrCacheSlides(file: File) {
        withContext(Dispatchers.Main) { _loadError.value = null }
        val renderWidth = renderWidth()
        val cached = diskCache.lookup(file, renderWidth)
        if (cached != null) {
            // Parse the deck even on a cache hit — cheap (metadata only), and later workstreams
            // need layers/timelines that are never cached. A parse failure of a previously
            // cached file still shows the cached static slides.
            val parsed = (PresentationLoader.load(file) as? LoadResult.Success)?.deck
            withContext(Dispatchers.Main) {
                clearCurrentSlideState()
                _totalSlides.value = cached.slideFiles.size
                _slideFiles.addAll(cached.slideFiles)
                _slideNotes.addAll(cached.notes)
                _deck.value = exposableDeck(parsed)
                _loadGeneration.value++
            }
        } else {
            renderSlides(file, renderWidth)
        }
    }

    private suspend fun renderSlides(file: File, renderWidth: Int) = CrashReporter.trace(
        operation = "presentation.render",
        name = "Render ${file.extension.lowercase()} slides"
    ) {
        withContext(Dispatchers.Main) {
            clearCurrentSlideState()
            _totalSlides.value = 0
            _isLoading.value = true
        }
        var writer: SlideDiskCache.Writer? = null
        var success = false
        try {
            val deck = when (val result = PresentationLoader.load(file)) {
                is LoadResult.Failure -> {
                    withContext(Dispatchers.Main) { _loadError.value = result.error.toUiError() }
                    reportLoadFailure(file, result)
                    return@trace
                }
                is LoadResult.Success -> result.deck
            }
            withContext(Dispatchers.Main) {
                _totalSlides.value = deck.slideCount
                _deck.value = exposableDeck(deck)
            }
            // Coverage telemetry: each degrade (unknown preset/filter, dropped target, …) leaves
            // one breadcrumb so user reports carry the exact gap to fix in PresetCatalog.
            deck.warnings.forEach { warning ->
                CrashReporter.breadcrumb("Presentation degrade: $warning", category = "presentation")
            }
            val cacheWriter = diskCache.beginWrite(file, deck.format, renderWidth)
            writer = cacheWriter
            DeckRasterizer(deck, renderWidth).use { rasterizer ->
                for (slide in deck.slides) {
                    try {
                        val frame = rasterizer.renderFinalFrame(slide.index)
                        val slideFile = cacheWriter.putSlide(
                            index = slide.index,
                            image = frame,
                            note = slide.notes,
                            fidelity = slide.fidelity,
                            hasTimeline = slide.timeline != null
                        )
                        withContext(Dispatchers.Main) {
                            _slideFiles.add(slideFile)
                            _slideNotes.add(slide.notes)
                        }
                    } catch (e: Exception) {
                        // One bad slide must not kill the deck; the slide is simply skipped.
                        CrashReporter.reportException(e, "Rendering slide ${slide.index} of ${file.name}")
                    }
                }
            }
            if (_slideFiles.isNotEmpty()) {
                cacheWriter.commit()
                withContext(Dispatchers.Main) { _loadGeneration.value++ }
                success = true
            } else {
                withContext(Dispatchers.Main) { _loadError.value = PresentationLoadError.RENDER_FAILED }
                CrashReporter.reportWarning(
                    "Presentation: No slides extracted from ${file.extension.lowercase()} file",
                    tags = mapOf(
                        "subsystem" to "presentation",
                        "file.type" to file.extension.lowercase(),
                        "failure.reason" to (_loadError.value?.name?.lowercase() ?: "unknown")
                    )
                )
            }
        } catch (e: Exception) {
            if (_loadError.value == null) {
                withContext(Dispatchers.Main) { _loadError.value = PresentationLoadError.RENDER_FAILED }
            }
            CrashReporter.reportWarning(
                "Presentation: Failed to render ${file.extension.lowercase()} slides",
                throwable = e,
                tags = mapOf("subsystem" to "presentation", "file.type" to file.extension.lowercase())
            )
        } finally {
            if (!success) writer?.abort()
            withContext(Dispatchers.Main) { _isLoading.value = false }
        }
    }

    private fun reportLoadFailure(file: File, failure: LoadResult.Failure) {
        CrashReporter.reportWarning(
            "Presentation: Failed to load ${file.extension.lowercase()} file",
            tags = mapOf(
                "subsystem" to "presentation",
                "file.type" to file.extension.lowercase(),
                "failure.reason" to failure.error.name.lowercase()
            )
        )
    }

    private fun DeckLoadError.toUiError(): PresentationLoadError = when (this) {
        DeckLoadError.PASSWORD_PROTECTED -> PresentationLoadError.PASSWORD_PROTECTED
        DeckLoadError.EMPTY_DOCUMENT -> PresentationLoadError.EMPTY_DOCUMENT
        DeckLoadError.UNSUPPORTED_FORMAT, DeckLoadError.PARSE_FAILED -> PresentationLoadError.RENDER_FAILED
    }

    private fun isValidPresentationFile(file: File): Boolean =
        file.extension.lowercase() in PresentationLoader.SUPPORTED_EXTENSIONS
}
