package org.churchpresenter.slides.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.presentation.PresentationLoadError
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.presentationengine.cache.SlideCacheSupersededException
import org.churchpresenter.presentationengine.cache.SlideDiskCache
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.presentationengine.model.DeckFormat
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.slides.utils.reportDegradedSlide
import java.awt.image.BufferedImage
import java.io.File

/**
 * Fills [PresentationState] with a deck's slides: from the disk cache, by rendering through the
 * presentation engine, or by downloading an Instance Link primary's already-rendered ones.
 */
internal class SlideLoader(
    private val state: PresentationState,
    private val appSettings: AppSettings?,
    private val diskCache: SlideDiskCache,
    private val hidden: HiddenSlides,
    private val playback: SlidePlayback,
) {
    /** See [PresentationViewModel.loadDeck]. */
    var loadDeck: (File) -> LoadResult = { PresentationLoader.load(it) }

    /** See [PresentationViewModel.renderSlideFrame]. */
    var renderSlideFrame: (DeckRasterizer, Int) -> BufferedImage =
        { rasterizer, index -> rasterizer.renderFinalFrame(index) }

    suspend fun loadOrCacheSlides(file: File) {
        withContext(Dispatchers.Main) { state.loadError.value = null }
        val renderWidth = renderWidth()
        val cached = diskCache.lookup(file, renderWidth)
        if (cached != null) {
            // Parse the deck even on a cache hit — cheap (metadata only), and later workstreams
            // need layers/timelines that are never cached. A parse failure of a previously
            // cached file still shows the cached static slides.
            val parsed = (loadDeck(file) as? LoadResult.Success)?.deck
            withContext(Dispatchers.Main) {
                state.clearCurrentSlideState()
                state.totalSlides.value = cached.slideFiles.size
                state.slideFiles.addAll(cached.slideFiles)
                state.slideNotes.addAll(cached.notes)
                hidden.settleOnShownSlide()
                state.deck.value = exposableDeck(parsed)
                state.loadGeneration.value++
                playback.applyPendingPlayback()
            }
        } else {
            renderSlides(file, renderWidth)
        }
    }

    /**
     * Downloads an Instance Link primary's slides into [cacheDir] and shows them as they land; see
     * [PresentationViewModel.loadPresentationFromRemote].
     */
    suspend fun downloadSlides(
        cacheDir: File,
        slideCount: Int,
        fetchBytes: suspend (index: Int) -> ByteArray?,
    ) {
        // The tab prunes orphaned slide caches on startup, and every `remote_*` entry is
        // orphaned by definition. Without this claim that prune races the download, deletes
        // this directory mid-write, and every renameTo below fails into a vanished parent —
        // a load that lands zero slides and reports RENDER_FAILED.
        SlideDiskCache.claimDir(cacheDir)
        var success = false
        try {
            for (index in 0 until slideCount) {
                val slideFile = cachedSlide(cacheDir, index, fetchBytes)
                if (slideFile != null) {
                    withContext(Dispatchers.Main) {
                        state.slideFiles.add(slideFile)
                        state.slideNotes.add("")
                        hidden.settleOnShownSlide()
                        playback.applyPendingPlayback()
                    }
                }
            }
            if (state.slideFiles.isNotEmpty()) {
                withContext(Dispatchers.Main) { state.loadGeneration.value++ }
                success = true
            } else {
                withContext(Dispatchers.Main) { state.loadError.value = PresentationLoadError.RENDER_FAILED }
            }
        } finally {
            // A superseded load must not delete the directory its successor is already
            // filling: both share `remote_$scheduleItemId`. On cancellation the newer load
            // owns the entry, so leave it — only a load that failed on its own terms cleans up.
            if (!success && currentCoroutineContext().isActive) cacheDir.deleteRecursively()
            SlideDiskCache.releaseDir(cacheDir)
            withContext(Dispatchers.Main) { state.isLoading.value = false }
        }
    }

    /** Slide [index] in [cacheDir], fetched with [fetchBytes] if it is not there yet; null when it cannot be. */
    private suspend fun cachedSlide(
        cacheDir: File,
        index: Int,
        fetchBytes: suspend (index: Int) -> ByteArray?,
    ): File? {
        val slideFile = File(cacheDir, "slide_%04d.jpg".format(index))
        if (slideFile.exists()) return slideFile
        val bytes = fetchBytes(index) ?: return null
        val tmp = File(cacheDir, "${slideFile.name}.tmp")
        tmp.writeBytes(bytes)
        val cached = tmp.renameTo(slideFile)
        if (!cached) tmp.delete()
        return slideFile.takeIf { cached }
    }

    private suspend fun renderSlides(file: File, renderWidth: Int) = CrashReporter.trace(
        operation = "presentation.render",
        name = "Render ${file.extension.lowercase()} slides"
    ) {
        withContext(Dispatchers.Main) {
            state.clearCurrentSlideState()
            state.totalSlides.value = 0
            state.isLoading.value = true
        }
        var writer: SlideDiskCache.Writer? = null
        var success = false
        try {
            val deck = when (val result = loadDeck(file)) {
                is LoadResult.Failure -> {
                    withContext(Dispatchers.Main) { state.loadError.value = result.error.toUiError() }
                    reportLoadFailure(file, result)
                    return@trace
                }
                is LoadResult.Success -> result.deck
            }
            withContext(Dispatchers.Main) {
                state.totalSlides.value = deck.slideCount
                state.deck.value = exposableDeck(deck)
            }
            // Coverage telemetry: each degrade (unknown preset/filter, dropped target, …) leaves
            // one breadcrumb so user reports carry the exact gap to fix in PresetCatalog.
            deck.warnings.forEach { warning ->
                CrashReporter.breadcrumb("Presentation degrade: $warning", category = "presentation")
            }
            val cacheWriter = diskCache.beginWrite(file, deck.format, renderWidth)
            writer = cacheWriter
            var reportedSlideFailure = false
            DeckRasterizer(deck, renderWidth, onDegraded = ::reportDegradedSlide).use { rasterizer ->
                for (slide in deck.slides) {
                    try {
                        val frame = renderSlideFrame(rasterizer, slide.index)
                        val slideFile = cacheWriter.putSlide(
                            index = slide.index,
                            image = frame,
                            note = slide.notes,
                            fidelity = slide.fidelity,
                            hasTimeline = slide.timeline != null
                        )
                        withContext(Dispatchers.Main) {
                            state.slideFiles.add(slideFile)
                            hidden.settleOnShownSlide()
                            playback.applyPendingPlayback()
                            state.slideNotes.add(slide.notes)
                        }
                    } catch (e: CancellationException) {
                        // This render was superseded by a newer selectPresentation. Let it die here
                        // instead of rendering (and reporting) the rest of a deck nobody wants.
                        throw e
                    } catch (_: SlideCacheSupersededException) {
                        // Another render of the same deck — the companion server, or a re-select
                        // whose cancellation we haven't reached yet — owns the cache entry now.
                        // Stop quietly and leave the files to it.
                        return@use
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        // A slide is drawn by POI, PDFBox and AWT, which throw whatever their internals do (NPEs and
                        // class-cast errors from a malformed deck included) -- not a set this code can enumerate.
                        // One bad slide must not kill the deck; the slide is simply skipped. Only
                        // the first failure is reported: whatever breaks one slide (a full or
                        // read-only cache dir) breaks every slide after it, and forty identical
                        // events say nothing the first one didn't.
                        if (!reportedSlideFailure) {
                            reportedSlideFailure = true
                            CrashReporter.reportException(e, "Rendering slide ${slide.index} of ${file.name}")
                        }
                    }
                }
            }
            if (state.slideFiles.isNotEmpty()) {
                cacheWriter.commit()
                withContext(Dispatchers.Main) { state.loadGeneration.value++ }
                success = true
            } else {
                withContext(Dispatchers.Main) { state.loadError.value = PresentationLoadError.RENDER_FAILED }
                CrashReporter.reportWarning(
                    "Presentation: No slides extracted from ${file.extension.lowercase()} file",
                    tags = mapOf(
                        "subsystem" to "presentation",
                        "file.type" to file.extension.lowercase(),
                        "failure.reason" to (state.loadError.value?.name?.lowercase() ?: "unknown")
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SlideCacheSupersededException) {
            // Lost the entry to a newer render of the same deck between the last slide and the
            // commit. That render owns the result; this one is not a failure and shows no error.
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A slide is drawn by POI, PDFBox and AWT, which throw whatever their internals do (NPEs and
            // class-cast errors from a malformed deck included) -- not a set this code can enumerate.
            if (state.loadError.value == null) {
                withContext(Dispatchers.Main) { state.loadError.value = PresentationLoadError.RENDER_FAILED }
            }
            CrashReporter.reportWarning(
                "Presentation: Failed to render ${file.extension.lowercase()} slides",
                throwable = e,
                tags = mapOf("subsystem" to "presentation", "file.type" to file.extension.lowercase())
            )
        } finally {
            if (!success) writer?.abort()
            withContext(Dispatchers.Main) { state.isLoading.value = false }
        }
    }

    private fun reportLoadFailure(file: File, failure: LoadResult.Failure) {
        if (failure.error.isOperatorFile()) return
        CrashReporter.reportWarning(
            "Presentation: Failed to load ${file.extension.lowercase()} file",
            tags = mapOf(
                "subsystem" to "presentation",
                "file.type" to file.extension.lowercase(),
                "failure.reason" to failure.error.name.lowercase()
            )
        )
    }

    private fun renderWidth(): Int =
        appSettings?.projectionSettings?.getAssignment(0)?.targetBoundsW?.takeIf { it > 0 }
            ?: DeckRasterizer.DEFAULT_TARGET_WIDTH_PX

    /**
     * The deck as exposed to playback. Keynote animation rides on a reverse-engineered parser,
     * so the "Animate Keynote" setting can hold .key decks on the static path (deck hidden →
     * PresenterManager never starts the player); static rendering is unaffected.
     */
    fun exposableDeck(deck: Deck?): Deck? = deck?.takeUnless {
        it.format == DeckFormat.KEYNOTE && appSettings?.presentationSettings?.animateKeynote == false
    }
}
