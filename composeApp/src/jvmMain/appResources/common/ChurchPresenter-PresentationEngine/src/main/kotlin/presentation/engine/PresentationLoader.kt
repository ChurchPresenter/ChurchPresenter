package presentation.engine

import presentation.engine.keynote.KeynoteDeckParser
import presentation.engine.keynote.KeynoteLayerPlanner
import presentation.engine.keynote.KeynoteScene
import presentation.engine.keynote.KeynoteStaticSupport
import presentation.engine.model.Deck
import presentation.engine.model.DeckFormat
import presentation.engine.model.DeckLoadError
import presentation.engine.model.DeckSource
import presentation.engine.model.Fidelity
import presentation.engine.model.KeynoteStaticStrategy
import presentation.engine.model.LayerSpec
import presentation.engine.model.RectPt
import presentation.engine.model.Slide
import presentation.engine.model.Timeline
import presentation.engine.pdf.PdfDeckSupport
import presentation.engine.pdf.PdfMetadataResult
import presentation.engine.pptx.PowerPointDeckSupport
import presentation.engine.pptx.PowerPointMetadataResult
import java.io.File

sealed interface LoadResult {
    data class Success(val deck: Deck) : LoadResult
    data class Failure(val error: DeckLoadError, val detail: String? = null) : LoadResult
}

/**
 * Parses a presentation file into a [Deck]. Never throws — every failure comes back as
 * [LoadResult.Failure]. Parsing is metadata-only (slide count, notes, geometry, and — in later
 * workstreams — layers and timelines); pixels are produced by [DeckRasterizer].
 */
object PresentationLoader {

    val SUPPORTED_EXTENSIONS = setOf("pdf", "pptx", "ppt", "key")

    fun load(file: File): LoadResult {
        if (!file.exists()) return LoadResult.Failure(DeckLoadError.PARSE_FAILED, "File not found: ${file.path}")
        return try {
            when (file.extension.lowercase()) {
                "pdf" -> loadPdf(file)
                "pptx" -> loadPowerPoint(file, isPptx = true)
                "ppt" -> loadPowerPoint(file, isPptx = false)
                "key" -> loadKeynote(file)
                else -> LoadResult.Failure(DeckLoadError.UNSUPPORTED_FORMAT, file.extension)
            }
        } catch (e: Exception) {
            LoadResult.Failure(DeckLoadError.PARSE_FAILED, e.message)
        }
    }

    private fun loadPdf(file: File): LoadResult {
        return when (val result = PdfDeckSupport.readMetadata(file)) {
            is PdfMetadataResult.Failure -> LoadResult.Failure(result.error, result.detail)
            is PdfMetadataResult.Success -> {
                val meta = result.metadata
                LoadResult.Success(
                    Deck(
                        sourceFile = file,
                        format = DeckFormat.PDF,
                        slideWidthPt = meta.pageWidthPt,
                        slideHeightPt = meta.pageHeightPt,
                        slides = staticSlides(meta.pageCount, meta.pageWidthPt, meta.pageHeightPt,
                            notes = emptyList(), fidelity = Fidelity.NATIVE),
                        source = DeckSource.Pdf(file)
                    )
                )
            }
        }
    }

    private fun loadPowerPoint(file: File, isPptx: Boolean): LoadResult {
        return when (val result = PowerPointDeckSupport.readMetadata(file)) {
            is PowerPointMetadataResult.Failure -> LoadResult.Failure(result.error, result.detail)
            is PowerPointMetadataResult.Success -> {
                val meta = result.metadata
                val fullBounds = RectPt(0.0, 0.0, meta.pageWidthPt, meta.pageHeightPt)
                val slides = meta.slides.mapIndexed { index, slideMeta ->
                    Slide(
                        index = index,
                        notes = slideMeta.notes,
                        transition = slideMeta.transition,
                        layers = slideMeta.layers?.takeIf { it.isNotEmpty() }
                            ?: listOf(LayerSpec.StaticComposite(id = "slide-$index", zIndex = 0, boundsPt = fullBounds)),
                        timeline = slideMeta.timeline,
                        fidelity = Fidelity.NATIVE
                    )
                }
                LoadResult.Success(
                    Deck(
                        sourceFile = file,
                        format = if (isPptx) DeckFormat.PPTX else DeckFormat.PPT,
                        slideWidthPt = meta.pageWidthPt,
                        slideHeightPt = meta.pageHeightPt,
                        slides = slides,
                        warnings = meta.warnings,
                        source = DeckSource.PowerPoint(file, isPptx)
                    )
                )
            }
        }
    }

    /**
     * Keynote: first the native reverse-engineered IWA parse (renders and animates whitelisted
     * content; slides beyond the whitelist gate per-slide to the static fallback). When the
     * whole document can't be parsed natively, the static cascade takes over: embedded
     * QuickLook preview PDF, else per-slide `st-` thumbnails. Pure in-JVM throughout.
     */
    private fun loadKeynote(file: File): LoadResult {
        val analysis = KeynoteStaticSupport.analyze(file)
        val scene = try {
            KeynoteDeckParser.parse(file)
        } catch (_: Exception) {
            null
        }
        if (scene != null && scene.slides.isNotEmpty()) {
            buildNativeKeynoteDeck(file, scene, analysis)?.let { return LoadResult.Success(it) }
        }
        return loadKeynoteStatic(file, analysis)
    }

    private fun buildNativeKeynoteDeck(
        file: File,
        scene: KeynoteScene,
        analysis: KeynoteStaticSupport.Analysis
    ): Deck? {
        val warnings = mutableListOf<String>()

        // Static source for gated slides — only usable when its page count aligns 1:1.
        val fallback: DeckSource.KeynoteStatic? = when {
            analysis.hasPreviewPdf && previewPdfPageCount(file) == scene.slides.size ->
                DeckSource.KeynoteStatic(file, KeynoteStaticStrategy.PREVIEW_PDF)
            analysis.orderedThumbnailEntries.size == scene.slides.size ->
                DeckSource.KeynoteStatic(file, KeynoteStaticStrategy.THUMBNAILS, analysis.orderedThumbnailEntries)
            else -> null
        }

        val fullBounds = RectPt(0.0, 0.0, scene.slideWidthPt, scene.slideHeightPt)
        val slides = scene.slides.map { knSlide ->
            val gated = knSlide.gateReason != null
            if (gated) {
                warnings.add("Slide ${knSlide.index + 1}: ${knSlide.gateReason} — static fallback")
            }
            var layers: List<LayerSpec>? = null
            var timeline: Timeline? = null
            if (!gated) {
                layers = KeynoteLayerPlanner.plan(knSlide, scene.slideWidthPt, scene.slideHeightPt)
                if (layers != null) {
                    val remapped = KeynoteLayerPlanner.remapTimeline(knSlide, layers)
                    if (remapped == null) {
                        layers = null
                    } else {
                        timeline = remapped.first
                        layers = layers.map { spec ->
                            if (spec.id in remapped.second && spec is LayerSpec.Shape) {
                                spec.copy(initiallyVisible = false)
                            } else spec
                        }
                    }
                }
            }
            Slide(
                index = knSlide.index,
                notes = knSlide.notes,
                transition = if (gated) null else knSlide.transition,
                layers = layers
                    ?: listOf(LayerSpec.StaticComposite("slide-${knSlide.index}", 0, fullBounds)),
                timeline = timeline,
                fidelity = if (gated) Fidelity.STATIC_FALLBACK else Fidelity.NATIVE
            )
        }
        // A fully-gated deck with no usable native content renders better through the plain
        // static cascade (full-page previews instead of partial native renders).
        if (slides.all { it.fidelity == Fidelity.STATIC_FALLBACK } && fallback == null) return null

        return Deck(
            sourceFile = file,
            format = DeckFormat.KEYNOTE,
            slideWidthPt = scene.slideWidthPt,
            slideHeightPt = scene.slideHeightPt,
            slides = slides,
            warnings = warnings,
            source = DeckSource.KeynoteNative(scene, fallback)
        )
    }

    private fun previewPdfPageCount(file: File): Int? {
        val tempPdf = File.createTempFile("keynote_preview_", ".pdf")
        return try {
            if (!KeynoteStaticSupport.extractPreviewPdf(file, tempPdf)) return null
            (PdfDeckSupport.readMetadata(tempPdf) as? PdfMetadataResult.Success)?.metadata?.pageCount
        } finally {
            tempPdf.delete()
        }
    }

    private fun loadKeynoteStatic(file: File, analysis: KeynoteStaticSupport.Analysis): LoadResult {
        val warnings = mutableListOf<String>()

        if (analysis.hasPreviewPdf) {
            val tempPdf = File.createTempFile("keynote_preview_", ".pdf")
            try {
                if (KeynoteStaticSupport.extractPreviewPdf(file, tempPdf)) {
                    val meta = PdfDeckSupport.readMetadata(tempPdf)
                    if (meta is PdfMetadataResult.Success) {
                        return LoadResult.Success(
                            Deck(
                                sourceFile = file,
                                format = DeckFormat.KEYNOTE,
                                slideWidthPt = meta.metadata.pageWidthPt,
                                slideHeightPt = meta.metadata.pageHeightPt,
                                slides = staticSlides(
                                    meta.metadata.pageCount,
                                    meta.metadata.pageWidthPt, meta.metadata.pageHeightPt,
                                    notes = analysis.notes,
                                    fidelity = Fidelity.STATIC_FALLBACK
                                ),
                                warnings = warnings,
                                source = DeckSource.KeynoteStatic(file, KeynoteStaticStrategy.PREVIEW_PDF)
                            )
                        )
                    }
                    warnings.add("Embedded preview PDF unreadable, using thumbnails")
                }
            } finally {
                tempPdf.delete()
            }
        }

        if (analysis.orderedThumbnailEntries.isEmpty()) {
            return LoadResult.Failure(DeckLoadError.EMPTY_DOCUMENT, "No preview PDF or slide thumbnails in .key")
        }
        // Thumbnail geometry is unknown until decode; 16:9 slide points are the safe default.
        return LoadResult.Success(
            Deck(
                sourceFile = file,
                format = DeckFormat.KEYNOTE,
                slideWidthPt = 720.0,
                slideHeightPt = 405.0,
                slides = staticSlides(
                    analysis.orderedThumbnailEntries.size, 720.0, 405.0,
                    notes = analysis.notes,
                    fidelity = Fidelity.STATIC_FALLBACK
                ),
                warnings = warnings,
                source = DeckSource.KeynoteStatic(
                    file, KeynoteStaticStrategy.THUMBNAILS, analysis.orderedThumbnailEntries
                )
            )
        )
    }

    private fun staticSlides(
        count: Int,
        widthPt: Double,
        heightPt: Double,
        notes: List<String>,
        fidelity: Fidelity
    ): List<Slide> {
        val bounds = RectPt(0.0, 0.0, widthPt, heightPt)
        return (0 until count).map { index ->
            Slide(
                index = index,
                notes = notes.getOrElse(index) { "" },
                transition = null,
                layers = listOf(LayerSpec.StaticComposite(id = "slide-$index", zIndex = 0, boundsPt = bounds)),
                timeline = null,
                fidelity = fidelity
            )
        }
    }
}
