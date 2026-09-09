package org.churchpresenter.app.churchpresenter.server

import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.presentationengine.cache.SlideCacheSupersededException
import org.churchpresenter.presentationengine.cache.SlideDiskCache
import org.churchpresenter.app.churchpresenter.utils.reportDegradedSlide
import org.churchpresenter.settings.utils.Constants

/**
 * Presentations as the companion API serves them: the catalogue, the per-slide JPEG cache and the
 * background render that fills it.
 *
 * Owns that state rather than leaving it spread across `CompanionServer`. Member names are
 * unchanged from where this code used to live so the bodies moved verbatim. [broadcast] is passed
 * in because the render finishes asynchronously and has to tell clients itself -- everything else
 * this needs is its own.
 */
internal class PresentationStore(
    private val json: Json,
    private val scope: CoroutineScope,
    private val broadcast: (WebSocketMessage) -> Unit,
) {

    /**
     * Serializes background presentation renders for the companion API. A schedule with several
     * presentations would otherwise render every deck concurrently (one IO coroutine each), and
     * each deck holds a full POI [SlideShow] plus a 1920px frame buffer — a few heavy decks at
     * once exhaust the heap (OutOfMemoryError). Rendering one deck at a time caps peak memory to a
     * single deck's footprint; the renders just queue.
     */
    internal val presentationRenderMutex = Mutex()
    // Presentation catalog — metadata only; raw JPEG bytes stored per-slide in _slideBytes
    internal val _presentationCatalog = MutableStateFlow(PresentationCatalogResponse(emptyList(), 0))
    /** presentationId → list of JPEG-encoded slide bytes (index = slide number). Max 5 cached. */
    internal val _slideBytes = ConcurrentHashMap<String, List<ByteArray>>()
    internal val _slideBytesOrder = java.util.concurrent.ConcurrentLinkedDeque<String>()
    internal val MAX_CACHED_PRESENTATIONS = 5
    /** presentationId (file hash) → PresentationDto — covers tab-loaded and background-rendered items */
    internal val _presentationCatalogs = ConcurrentHashMap<String, PresentationDto>()
    /** presentationId (file hash) → absolute file path — populated by updatePresentation and updateSchedule */
    internal val _presentationFilePaths = ConcurrentHashMap<String, String>()
    /** presentationId (file hash) → per-slide presenter notes (index = slide number) */
    internal val _presentationNotes = ConcurrentHashMap<String, List<String>>()
    /** schedule item UUID → presentation file hash — populated when schedule is updated */
    internal val _scheduleItemToPresentationId = ConcurrentHashMap<String, String>()
    /** Set of presentation IDs currently being background-rendered (avoids duplicate renders) */
    internal val _renderingPresentations = ConcurrentHashMap<String, Unit>()
    /** Cancels previous updatePresentation encode job when a new presentation is loaded */
    internal var _activeUpdateJob: Job? = null
    /** Shared slide disk cache — same directory PresentationViewModel renders into (one render, both consumers). */
    internal val slideDiskCache = SlideDiskCache()
    /**
     * ID of the most recently device-uploaded presentation file.
     * Cleared from [_presentationCatalogs], [_slideBytes], and [_presentationFilePaths] when a new
     * upload replaces it, so the mobile's presentation list never accumulates stale entries.
     */
    @Volatile internal var _lastDeviceUploadedPresentationId: String? = null
    private fun cacheSlideBytes(id: String, slides: List<ByteArray>) {
        _slideBytes[id] = slides
        _slideBytesOrder.remove(id)
        _slideBytesOrder.addFirst(id)
        while (_slideBytesOrder.size > MAX_CACHED_PRESENTATIONS) {
            val evicted = _slideBytesOrder.pollLast()
            if (evicted != null) {
                _slideBytes.remove(evicted)
                _presentationNotes.remove(evicted)
            }
        }
    }

    /**
     * Feed a loaded presentation (id, fileName, fileType and already-encoded JPEG slide files).
     * Reads bytes from disk on the IO thread; no re-encoding needed since files are already JPEG.
     */
    internal fun updatePresentation(
        id: String,
        filePath: String,
        fileName: String,
        fileType: String,
        slideFiles: List<File>,
        slideNotes: List<String> = emptyList()
    ) {
        if (filePath.isNotBlank()) {
            _presentationFilePaths[id] = filePath
        }
        _presentationNotes[id] = slideNotes
        _activeUpdateJob?.cancel()
        _activeUpdateJob = scope.launch {
            // slideFiles can be deleted out from under this coroutine (e.g. removePresentation()
            // invalidating the shared disk cache) while it's queued on Dispatchers.IO — treat a
            // vanished cache the same way renderPresentationForServer does: skip, don't crash.
            try {
                val jpegSlides = slideFiles.map { it.readBytes() }
                cacheSlideBytes(id, jpegSlides)

                val catalog = buildPresentationCatalog(id, fileName, fileType, jpegSlides.size)
                _presentationCatalogs[id] = catalog.presentations.first()
                _presentationCatalog.value = catalog
                broadcast(WebSocketMessage(
                    type = Constants.WS_EVENT_PRESENTATION_UPDATED,
                    payload = json.encodeToString(PresentationCatalogResponse.serializer(), catalog)
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Renders a schedule presentation for the mobile companion API using the shared presentation
     * engine. When the Presentation tab already rendered this file into the shared disk cache the
     * JPEGs are reused directly (any resolution); otherwise the deck is rendered here — into the
     * same shared cache, so a later tab open at the default width hits it too.
     */
    private fun slidesForServer(file: File): Pair<List<ByteArray>, List<String>>? =
        slideDiskCache.lookup(file, renderWidthPx = null)
            ?.let { cached -> cached.slideFiles.map { it.readBytes() } to cached.notes }
            ?: renderSlidesForServer(file)

    private fun renderSlidesForServer(file: File): Pair<List<ByteArray>, List<String>>? {
        // The Presentation tab is rendering this very deck into the shared entry. Starting
        // a second render here would take the entry away from it mid-deck, so leave it be:
        // the client's 404-retry path comes back once the tab's render has committed.
        if (slideDiskCache.isWriting(file)) return null
        val deck = when (val result = PresentationLoader.load(file)) {
            is LoadResult.Failure -> {
                // The detail is the message the loader caught, and without it every distinct
                // cause files as the same unactionable "parse_failed". It goes in extras and not
                // in the context sentence: the sentence is what Sentry groups on, and an
                // exception message can carry a file path.
                CrashReporter.reportWarning(
                    "Presentation: No slides extracted from ${file.extension.lowercase()} file (server)",
                    tags = mapOf(
                        "subsystem" to "presentation",
                        "file.type" to file.extension.lowercase(),
                        "failure.reason" to result.error.name.lowercase()
                    ),
                    extras = mapOf(
                        "detail" to (result.detail ?: "none"),
                        "file.size" to file.length().toString(),
                    )
                )
                return null
            }
            is LoadResult.Success -> result.deck
        }
        val writer = slideDiskCache.beginWrite(file, deck.format, DeckRasterizer.DEFAULT_TARGET_WIDTH_PX)
        var committed = false
        return try {
            val jpegSlides = CrashReporter.trace("server.render", "Server render presentation") {
                DeckRasterizer(deck, onDegraded = ::reportDegradedSlide).use { rasterizer ->
                    deck.slides.map { slide ->
                        val slideFile = writer.putSlide(
                            index = slide.index,
                            image = rasterizer.renderFinalFrame(slide.index),
                            note = slide.notes,
                            fidelity = slide.fidelity,
                            hasTimeline = slide.timeline != null
                        )
                        slideFile.readBytes()
                    }
                }
            }
            writer.commit()
            committed = true
            jpegSlides to deck.slides.map { it.notes }
        } catch (_: SlideCacheSupersededException) {
            // A tab render took the entry over after this one started; it finishes the job.
            null
        } finally {
            if (!committed) writer.abort()
        }
    }

    internal fun renderPresentationForServer(presentationId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val (jpegSlides, notes) = slidesForServer(file) ?: return
            if (jpegSlides.isEmpty()) return
            cacheSlideBytes(presentationId, jpegSlides)
            _presentationFilePaths[presentationId] = filePath
            _presentationNotes[presentationId] = notes
            val slideDtos = jpegSlides.indices.map { i ->
                SlideDto(slideIndex = i, thumbnailUrl = "${Constants.ENDPOINT_PRESENTATIONS}/$presentationId/slides/$i")
            }
            _presentationCatalogs[presentationId] = PresentationDto(
                id         = presentationId,
                fileName   = file.nameWithoutExtension,
                fileType   = file.extension.lowercase(),
                slideTotal = jpegSlides.size,
                slides     = slideDtos
            )
        } catch (oom: OutOfMemoryError) {
            // A background companion-API render must never take down the live app. OOM is an
            // Error, not an Exception, so the catch below wouldn't stop it escaping the coroutine
            // as an uncaught crash. Degrade to a warning and drop this presentation's render — the
            // client falls back to the 404-retry path (rendering still pending) just as it would
            // for any other render failure.
            CrashReporter.reportWarning(
                "Presentation: Out of memory rendering ${file.extension.lowercase()} for companion API (server)",
                throwable = oom,
                tags = mapOf(
                    "subsystem" to "presentation",
                    "file.type" to file.extension.lowercase()
                )
            )
        } catch (e: Exception) {
            CrashReporter.reportException(e, "Storing presentation slide")
        }
    }
}
