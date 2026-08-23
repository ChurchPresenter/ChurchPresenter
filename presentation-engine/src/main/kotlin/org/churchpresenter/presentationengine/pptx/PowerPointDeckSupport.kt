package org.churchpresenter.presentationengine.pptx

import org.apache.poi.EncryptedDocumentException
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.Slide
import org.apache.poi.sl.usermodel.SlideShow
import org.apache.poi.sl.usermodel.SlideShowFactory
import org.apache.poi.sl.usermodel.TextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.presentationengine.model.DeckLoadError
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.SlideTransitionSpec
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.timeline.TimelineCompiler
import java.io.File

/** Per-slide parse results. */
internal data class PowerPointSlideMeta(
    val notes: String,
    /** Layer decomposition for animated playback; null = single static composite. */
    val layers: List<LayerSpec>?,
    val timeline: Timeline?,
    val transition: SlideTransitionSpec?
)

/** Metadata read from a PPTX/PPT without rendering anything. */
internal data class PowerPointMetadata(
    val slideCount: Int,
    val pageWidthPt: Double,
    val pageHeightPt: Double,
    val slides: List<PowerPointSlideMeta>,
    val warnings: List<String>
)

internal sealed interface PowerPointMetadataResult {
    data class Success(val metadata: PowerPointMetadata) : PowerPointMetadataResult
    data class Failure(val error: DeckLoadError, val detail: String?) : PowerPointMetadataResult
}

internal object PowerPointDeckSupport {

    /** Opens a slide show with format auto-detection (works for both .pptx and .ppt). */
    fun open(file: File): SlideShow<*, *> = SlideShowFactory.create(file, null, true)
        .also { registerEmbeddedFonts(it) }
        .also { show -> (show as? XMLSlideShow)?.let { stripUnsupportedHighlights(it) } }

    /**
     * Registers fonts embedded in the document (the fntdata parts under ppt/fonts — plain TTF
     * payloads) into the JVM so slides render with the deck's own typefaces even when they
     * aren't installed. Obfuscated or embed-restricted payloads simply fail registration and
     * fall back to the substitution table.
     */
    private fun registerEmbeddedFonts(show: SlideShow<*, *>) {
        val pkg = (show as? XMLSlideShow)?.`package` ?: return
        try {
            for (part in pkg.parts) {
                if (part.contentType.contains("font", ignoreCase = true)) {
                    try {
                        SlideFontRegistry.registerFontStream(part.inputStream)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun readMetadata(file: File): PowerPointMetadataResult {
        return try {
            open(file).use { show ->
                val slides = show.slides
                if (slides.isEmpty()) {
                    return PowerPointMetadataResult.Failure(
                        DeckLoadError.EMPTY_DOCUMENT,
                        emptyDocumentDetail(file, show)
                    )
                }
                val pageSize = show.pageSize
                val warnings = mutableListOf<String>()
                PowerPointMetadataResult.Success(
                    PowerPointMetadata(
                        slideCount = slides.size,
                        pageWidthPt = pageSize.getWidth(),
                        pageHeightPt = pageSize.getHeight(),
                        slides = slides.mapIndexed { index, slide ->
                            slideMeta(slide, index, pageSize.getWidth(), pageSize.getHeight(), warnings)
                        },
                        warnings = warnings
                    )
                )
            }
        } catch (e: EncryptedDocumentException) {
            PowerPointMetadataResult.Failure(DeckLoadError.PASSWORD_PROTECTED, e.message)
        } catch (e: Exception) {
            PowerPointMetadataResult.Failure(DeckLoadError.PARSE_FAILED, e.message)
        }
    }

    /**
     * What an empty deck looked like, for the diagnostic that reports it.
     *
     * POI opens a malformed legacy `.ppt` without complaint and simply hands back no slides, so
     * this branch is reached with nothing else to go on — the reason tag alone can't tell a
     * genuinely empty deck from a corrupt one. Naming the implementation (HSLF vs XSLF), the
     * master count and the file size makes the next report classifiable without the file itself.
     */
    private fun emptyDocumentDetail(file: File, show: SlideShow<*, *>): String {
        val masters = try {
            show.slideMasters.size.toString()
        } catch (_: Exception) {
            "?"
        }
        return "impl=${show.javaClass.simpleName} masters=$masters bytes=${file.length()}"
    }

    private fun slideMeta(
        slide: Slide<*, *>,
        slideIndex: Int,
        pageWidthPt: Double,
        pageHeightPt: Double,
        warnings: MutableList<String>
    ): PowerPointSlideMeta {
        val notes = extractNotes(slide)
        if (slide !is XSLFSlide) {
            // Legacy .ppt: static forever (the binary format exposes no usable animation data).
            return PowerPointSlideMeta(notes, layers = null, timeline = null, transition = null)
        }
        var layers: List<LayerSpec>? = null
        var timeline: Timeline? = null
        try {
            layers = PptxLayerPlanner.plan(slide, AnimationTargetScanner.scan(slide))
            if (layers != null) {
                val compiled = TimelineCompiler(
                    slideWidthPt = pageWidthPt,
                    slideHeightPt = pageHeightPt,
                    resolveLayers = layerResolver(slide, layers)
                ).compile(TimingParser.parse(slide))
                if (compiled == null) {
                    // Nothing usable compiled. A video layer still needs its own identity even
                    // with no animation driving it — the app finds it there to drive playback —
                    // so only fall back to the flattened static composite when there isn't one.
                    // (KeynoteDeckSupport's native path makes the same exception, for the same
                    // reason; without this a slide holding only an unanimated video loses the
                    // layer the planner deliberately created for it.)
                    if (layers.none { it is LayerSpec.Media }) layers = null
                } else {
                    timeline = compiled.timeline
                    compiled.warnings.forEach { warnings.add("Slide ${slideIndex + 1}: $it") }
                    layers = layers.map { spec ->
                        if (spec.id in compiled.initiallyHiddenLayerIds) spec.withInitiallyVisible(false) else spec
                    }
                }
            }
        } catch (e: Exception) {
            warnings.add("Slide ${slideIndex + 1}: animation parse failed (${e.message}) — static")
            layers = null
            timeline = null
        }
        return PowerPointSlideMeta(
            notes = notes,
            layers = layers,
            timeline = timeline,
            transition = TransitionParser.parse(slide)
        )
    }

    /**
     * Maps a behavior target (shape id + optional paragraph) to the planner's layer ids.
     * A target inside a group resolves to the enclosing top-level group layer; a whole-shape
     * target on a paragraph-built shape fans out to every paragraph layer.
     */
    private fun layerResolver(
        slide: XSLFSlide,
        layers: List<LayerSpec>
    ): (Long, Int?) -> List<TimelineCompiler.LayerIdWithBounds> {
        val topIndexByShapeId = mutableMapOf<Long, Int>()
        slide.shapes.forEachIndexed { index, shape ->
            collectShapeIds(shape).forEach { id -> topIndexByShapeId.putIfAbsent(id, index) }
        }
        val shapeLayers = layers.filterIsInstance<LayerSpec.Shape>().associateBy { it.shapeIndex }
        val paragraphLayers = layers.filterIsInstance<LayerSpec.ParagraphText>().groupBy { it.shapeIndex }
        val mediaLayers = layers.filterIsInstance<LayerSpec.Media>().associateBy { it.shapeIndex }

        return fun(shapeId: Long, paragraphIndex: Int?): List<TimelineCompiler.LayerIdWithBounds> {
            val topIndex = topIndexByShapeId[shapeId] ?: return emptyList()
            paragraphLayers[topIndex]?.let { paras ->
                return if (paragraphIndex != null) {
                    paras.filter { it.paragraphIndex == paragraphIndex }
                        .map { TimelineCompiler.LayerIdWithBounds(it.id, it.boundsPt) }
                } else {
                    paras.map { TimelineCompiler.LayerIdWithBounds(it.id, it.boundsPt) }
                }
            }
            shapeLayers[topIndex]?.let {
                return listOf(TimelineCompiler.LayerIdWithBounds(it.id, it.boundsPt))
            }
            mediaLayers[topIndex]?.let {
                return listOf(TimelineCompiler.LayerIdWithBounds(it.id, it.boundsPt))
            }
            return emptyList()
        }
    }

    private fun collectShapeIds(shape: XSLFShape): List<Long> {
        val ids = mutableListOf(shape.shapeId.toLong())
        if (shape is XSLFGroupShape) {
            shape.shapes.forEach { ids.addAll(collectShapeIds(it)) }
        }
        return ids
    }

    private fun LayerSpec.withInitiallyVisible(visible: Boolean): LayerSpec = when (this) {
        is LayerSpec.Shape -> copy(initiallyVisible = visible)
        is LayerSpec.ParagraphText -> copy(initiallyVisible = visible)
        is LayerSpec.Background -> copy(initiallyVisible = visible)
        is LayerSpec.StaticComposite -> copy(initiallyVisible = visible)
        is LayerSpec.Media -> copy(initiallyVisible = visible)
    }

    private fun extractNotes(slide: Slide<*, *>): String {
        return try {
            val notesSheet = slide.notes ?: return ""
            // Speaker notes live in the BODY placeholder; the other text shapes on a notes
            // sheet are master boilerplate (date, slide number, header/footer).
            notesSheet.shapes
                .filterIsInstance<TextShape<*, *>>()
                .filter { it.placeholder == Placeholder.BODY }
                .mapNotNull { shape -> shape.text?.trim()?.takeIf { it.isNotBlank() } }
                .joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }
}
