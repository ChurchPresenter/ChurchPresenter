package org.churchpresenter.presentationengine

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.sl.usermodel.Slide
import org.apache.poi.sl.usermodel.SlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.presentationengine.keynote.KeynoteSceneRasterizer
import org.churchpresenter.presentationengine.keynote.KeynoteStaticSupport
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.presentationengine.model.DeckSource
import org.churchpresenter.presentationengine.model.KeynoteStaticStrategy
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RasterLayer
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.pptx.PowerPointDeckSupport
import org.churchpresenter.presentationengine.pptx.PptxSlideRasterizer
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt

/**
 * Produces pixels for a [Deck]. Holds the source document open across calls (one open per deck,
 * regardless of slide count) — always use inside `use { }` or call [close].
 *
 * [targetWidthPx] controls render resolution for vector sources (PDF, PowerPoint); raster
 * sources (Keynote thumbnails) come back at their stored size.
 */
class DeckRasterizer(
    private val deck: Deck,
    private val targetWidthPx: Int = DEFAULT_TARGET_WIDTH_PX,
    /**
     * Called for the **first** slide of this deck that could only be rendered with elements left
     * out, and not again for the rest of the render.
     *
     * A callback rather than a report from in here: this module has no dependency on the crash
     * reporter and should not gain one, and the caller is what knows whether this render is a
     * thumbnail, the companion API or the live output. First-only because the cause is nearly
     * always one asset the whole deck reuses — a hundred-slide deck would otherwise file a
     * hundred identical events for a single bad picture.
     */
    private val onDegraded: (SlideRenderDegradation) -> Unit = {},
) : AutoCloseable {

    companion object {
        const val DEFAULT_TARGET_WIDTH_PX = 1920

        /**
         * Upper bound on one rendered slide raster. Rendering fixes the width to the target and
         * lets the height follow the page aspect ratio — a pathological page (a very tall banner /
         * "infinite canvas" deck) would otherwise produce a multi-hundred-MB image and OOM, made
         * worse by [flattenToRgb]'s second full-size copy. Normal 16:9 decks render at ~1920×1080
         * (~2M px), far under these caps, so their output is unchanged.
         */
        const val MAX_RENDER_PIXELS = 8_000_000
        const val MAX_RENDER_DIMENSION = 8192

        /**
         * Scale that renders a [pageWidthPt]×[pageHeightPt] page at [targetWidthPx] wide, reduced
         * proportionally (aspect ratio preserved) if that would exceed [MAX_RENDER_DIMENSION] on
         * either axis or [MAX_RENDER_PIXELS] total. A degraded page renders at lower resolution
         * instead of exhausting the heap.
         */
        fun boundedRenderScale(pageWidthPt: Double, pageHeightPt: Double, targetWidthPx: Int): Double {
            if (pageWidthPt <= 0.0 || pageHeightPt <= 0.0) return 1.0
            val byWidth = targetWidthPx / pageWidthPt
            val byDimension = MAX_RENDER_DIMENSION / maxOf(pageWidthPt, pageHeightPt)
            val byArea = sqrt(MAX_RENDER_PIXELS / (pageWidthPt * pageHeightPt))
            return minOf(byWidth, byDimension, byArea).takeIf { it > 0.0 } ?: 1.0
        }

        /**
         * Flattens any translucency onto [background] — required before JPEG encoding
         * (ImageIO's JPEG writer corrupts colors on ARGB input).
         */
        fun flattenToRgb(image: BufferedImage, background: Color = Color.WHITE): BufferedImage {
            if (image.type == BufferedImage.TYPE_INT_RGB) return image
            val flat = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g = flat.createGraphics()
            g.color = background
            g.fillRect(0, 0, flat.width, flat.height)
            g.drawImage(image, 0, 0, null)
            g.dispose()
            return flat
        }
    }

    private var pdfDocument: PDDocument? = null
    private var pdfRenderer: PDFRenderer? = null
    private var slideShow: SlideShow<*, *>? = null
    private var keynoteTempPdf: File? = null
    private var keynoteSceneRasterizer: KeynoteSceneRasterizer? = null

    /** The pixel size a slide renders at, and the scale that gets it there from page points. */
    private data class SlideCanvas(val width: Int, val height: Int, val scale: Double)

    /** Whether [onDegraded] has already fired for this deck; see its own doc for why once. */
    private var degradationReported = false

    /** Embedded pptx video files extracted to temp files, keyed by relationship id — see
     *  [PptxSlideRasterizer.rasterizeLayer]; deleted in [close], not just `deleteOnExit()`. */
    private val pptxExtractedTempFiles = HashMap<String, File?>()

    /**
     * Renders the slide with every build complete — the image used for thumbnails, the static
     * output path, the companion API and the disk cache. ARGB when the source can carry
     * transparency; flatten with [flattenToRgb] before JPEG encoding.
     */
    fun renderFinalFrame(slideIndex: Int): BufferedImage {
        require(slideIndex in deck.slides.indices) { "Slide $slideIndex out of range 0..${deck.slides.size - 1}" }
        return when (val source = deck.source) {
            is DeckSource.Pdf -> renderPdfPage(source.file, slideIndex)
            is DeckSource.PowerPoint -> renderPowerPointSlide(source.file, slideIndex)
            is DeckSource.KeynoteStatic -> renderKeynoteStatic(source, slideIndex)
            is DeckSource.KeynoteNative -> {
                val gated = deck.slides[slideIndex].fidelity == Fidelity.STATIC_FALLBACK
                if (gated && source.staticFallback != null) {
                    renderKeynoteStatic(source.staticFallback, slideIndex)
                } else {
                    // Native render — for a gated slide without an aligned static source this
                    // still shows the parseable subset (partial beats blank).
                    keynoteRasterizer(source).renderFinalFrame(slideIndex, targetWidthPx)
                }
            }
        }
    }

    private fun renderKeynoteStatic(source: DeckSource.KeynoteStatic, slideIndex: Int): BufferedImage =
        when (source.strategy) {
            KeynoteStaticStrategy.PREVIEW_PDF -> renderKeynotePreviewPage(source.file, slideIndex)
            KeynoteStaticStrategy.THUMBNAILS -> renderKeynoteThumbnail(source, slideIndex)
        }

    private fun keynoteRasterizer(source: DeckSource.KeynoteNative): KeynoteSceneRasterizer =
        keynoteSceneRasterizer ?: KeynoteSceneRasterizer(source.scene).also { keynoteSceneRasterizer = it }

    /**
     * Rasterizes the slide's layer decomposition for animated playback. Slides without a layer
     * plan (static composites, non-PPTX formats) come back as a single full-frame layer.
     * Compositing the result in order at each layer's offset reproduces [renderFinalFrame].
     */
    fun rasterizeSlideLayers(slideIndex: Int): List<RasterLayer> {
        require(slideIndex in deck.slides.indices) { "Slide $slideIndex out of range 0..${deck.slides.size - 1}" }
        val slideSpec = deck.slides[slideIndex]
        val source = deck.source
        val isLayered = slideSpec.layers.any { it !is LayerSpec.StaticComposite }
        if (isLayered && source is DeckSource.PowerPoint && source.isPptx) {
            val show = slideShow ?: PowerPointDeckSupport.open(source.file).also { slideShow = it }
            val xslfShow = show as? XMLSlideShow
            if (xslfShow != null) {
                val slide = xslfShow.slides[slideIndex]
                val scale = targetWidthPx.toDouble() / show.pageSize.width
                return slideSpec.layers.map {
                    PptxSlideRasterizer.rasterizeLayer(slide, it, scale, pptxExtractedTempFiles)
                }
            }
        }
        if (isLayered && source is DeckSource.KeynoteNative &&
            slideSpec.fidelity == Fidelity.NATIVE
        ) {
            val rasterizer = keynoteRasterizer(source)
            return slideSpec.layers.map { rasterizer.rasterizeLayer(slideIndex, it, targetWidthPx) }
        }
        val fallbackSpec = slideSpec.layers.firstOrNull()
            ?: LayerSpec.StaticComposite(
                id = "slide-$slideIndex",
                zIndex = 0,
                boundsPt = RectPt(0.0, 0.0, deck.slideWidthPt, deck.slideHeightPt)
            )
        return listOf(RasterLayer(fallbackSpec, renderFinalFrame(slideIndex), 0, 0))
    }

    override fun close() {
        try {
            pdfDocument?.close()
        } catch (_: Exception) {
        }
        pdfDocument = null
        pdfRenderer = null
        try {
            slideShow?.close()
        } catch (_: Exception) {
        }
        slideShow = null
        keynoteTempPdf?.delete()
        keynoteTempPdf = null
        try {
            keynoteSceneRasterizer?.close()
        } catch (_: Exception) {
        }
        keynoteSceneRasterizer = null
        pptxExtractedTempFiles.values.forEach { it?.delete() }
        pptxExtractedTempFiles.clear()
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    private fun renderPdfPage(file: File, pageIndex: Int): BufferedImage {
        val renderer = pdfRenderer ?: run {
            val document = PDDocument.load(file)
            pdfDocument = document
            PDFRenderer(document).also { pdfRenderer = it }
        }
        val mediaBox = pdfDocument!!.getPage(pageIndex).mediaBox
        val scale = boundedRenderScale(mediaBox.width.toDouble(), mediaBox.height.toDouble(), targetWidthPx).toFloat()
        return renderer.renderImage(pageIndex, scale, ImageType.RGB)
    }

    // ── PowerPoint ────────────────────────────────────────────────────────────

    // Throwable is the point: one shape's failure must not cost the slide, and the drawing path
    // raises Errors (OutOfMemoryError on an absurd declared length) as well as exceptions.
    @Suppress("TooGenericExceptionCaught")
    private fun renderPowerPointSlide(file: File, slideIndex: Int): BufferedImage {
        val show = slideShow ?: PowerPointDeckSupport.open(file).also { slideShow = it }
        val slide = show.slides[slideIndex]
        val pageSize = show.pageSize
        val scale = boundedRenderScale(pageSize.width.toDouble(), pageSize.height.toDouble(), targetWidthPx)
        val canvas = SlideCanvas(
            width = (pageSize.width * scale).toInt().coerceAtLeast(1),
            height = (pageSize.height * scale).toInt().coerceAtLeast(1),
            scale = scale,
        )
        return try {
            slideImage(canvas) { graphics ->
                DrawFactory.getInstance(graphics).getDrawable(slide).draw(graphics)
            }
        } catch (t: Throwable) {
            // POI's DrawSlide walks every shape itself, so anything one of them throws — an
            // oversized embedded picture is the one seen in the field — comes out here having
            // abandoned the rest of the slide. A blank slide mid-service is far worse than a
            // slide missing one picture, so draw it again the forgiving way. Nothing is retained
            // from the failed attempt: it is a fresh image, because the first pass may have
            // painted part of the slide before it threw.
            renderPowerPointSlideDegraded(slide, slideIndex, canvas, t)
        }
    }

    /**
     * A slide-sized ARGB image with [draw] painted onto it at [scale].
     *
     * ARGB: a slide without an opaque background keeps its transparency instead of the old
     * pipeline's forced white fill (the slide's own background — from slide, layout or master —
     * is painted by POI's DrawSlide).
     */
    private fun slideImage(canvas: SlideCanvas, draw: (Graphics2D) -> Unit): BufferedImage {
        val image = BufferedImage(canvas.width, canvas.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(Drawable.FONT_HANDLER, SlideFontRegistry.drawFontManager)
            graphics.scale(canvas.scale, canvas.scale)
            draw(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }

    /**
     * Re-renders the slide one element at a time, keeping everything that draws.
     *
     * This reproduces what `DrawSlide.draw` does — background, then the master sheet when the
     * slide follows it, then the shapes — with each step on its own, so a failing element costs
     * only itself. It is not a substitute for the single call: it is slower and it re-implements
     * an ordering POI owns, which is why it runs only after that call has already failed.
     */
    private fun renderPowerPointSlideDegraded(
        slide: Slide<*, *>,
        slideIndex: Int,
        canvas: SlideCanvas,
        cause: Throwable,
    ): BufferedImage {
        val shapes = slide.shapes.toList()
        var skipped = 0
        val image = slideImage(canvas) { graphics ->
            val factory = DrawFactory.getInstance(graphics)
            runCatching { slide.background?.let { factory.getDrawable(it).draw(graphics) } }
            if (slide.followMasterGraphics) {
                runCatching { slide.masterSheet?.let { factory.getDrawable(it).draw(graphics) } }
            }
            skipped = drawEachSkippingFailures(shapes) { factory.getDrawable(it).draw(graphics) }
        }
        if (!degradationReported) {
            degradationReported = true
            onDegraded(
                SlideRenderDegradation(
                    slideIndex = slideIndex,
                    shapesTotal = shapes.size,
                    shapesSkipped = skipped,
                    cause = cause.javaClass.simpleName,
                )
            )
        }
        return image
    }

    // ── Keynote (static) ──────────────────────────────────────────────────────

    private fun renderKeynotePreviewPage(keyFile: File, pageIndex: Int): BufferedImage {
        val renderer = pdfRenderer ?: run {
            val temp = File.createTempFile("keynote_preview_", ".pdf")
            check(KeynoteStaticSupport.extractPreviewPdf(keyFile, temp)) {
                "Embedded Keynote preview PDF disappeared from ${keyFile.name}"
            }
            keynoteTempPdf = temp
            val document = PDDocument.load(temp)
            pdfDocument = document
            PDFRenderer(document).also { pdfRenderer = it }
        }
        val mediaBox = pdfDocument!!.getPage(pageIndex).mediaBox
        val scale = boundedRenderScale(mediaBox.width.toDouble(), mediaBox.height.toDouble(), targetWidthPx).toFloat()
        return renderer.renderImage(pageIndex, scale, ImageType.RGB)
    }

    private fun renderKeynoteThumbnail(source: DeckSource.KeynoteStatic, slideIndex: Int): BufferedImage {
        val entry = source.orderedThumbnailEntries[slideIndex]
        val bytes = KeynoteStaticSupport.readThumbnailBytes(source.file, entry)
            ?: error("Keynote thumbnail vanished: $entry")
        return ImageIO.read(ByteArrayInputStream(bytes))
            ?: error("Undecodable Keynote thumbnail: $entry")
    }
}
