package presentation.engine

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.sl.usermodel.SlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import presentation.engine.fonts.SlideFontRegistry
import presentation.engine.keynote.KeynoteSceneRasterizer
import presentation.engine.keynote.KeynoteStaticSupport
import presentation.engine.model.Fidelity
import presentation.engine.model.Deck
import presentation.engine.model.DeckSource
import presentation.engine.model.KeynoteStaticStrategy
import presentation.engine.model.LayerSpec
import presentation.engine.model.RasterLayer
import presentation.engine.model.RectPt
import presentation.engine.pptx.PowerPointDeckSupport
import presentation.engine.pptx.PptxSlideRasterizer
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Produces pixels for a [Deck]. Holds the source document open across calls (one open per deck,
 * regardless of slide count) — always use inside `use { }` or call [close].
 *
 * [targetWidthPx] controls render resolution for vector sources (PDF, PowerPoint); raster
 * sources (Keynote thumbnails) come back at their stored size.
 */
class DeckRasterizer(
    private val deck: Deck,
    private val targetWidthPx: Int = DEFAULT_TARGET_WIDTH_PX
) : AutoCloseable {

    companion object {
        const val DEFAULT_TARGET_WIDTH_PX = 1920

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
                return slideSpec.layers.map { PptxSlideRasterizer.rasterizeLayer(slide, it, scale) }
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
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    private fun renderPdfPage(file: File, pageIndex: Int): BufferedImage {
        val renderer = pdfRenderer ?: run {
            val document = PDDocument.load(file)
            pdfDocument = document
            PDFRenderer(document).also { pdfRenderer = it }
        }
        val pageWidthPt = pdfDocument!!.getPage(pageIndex).mediaBox.width
        val scale = targetWidthPx / pageWidthPt
        return renderer.renderImage(pageIndex, scale, ImageType.RGB)
    }

    // ── PowerPoint ────────────────────────────────────────────────────────────

    private fun renderPowerPointSlide(file: File, slideIndex: Int): BufferedImage {
        val show = slideShow ?: PowerPointDeckSupport.open(file).also { slideShow = it }
        val slide = show.slides[slideIndex]
        val pageSize = show.pageSize
        val scale = targetWidthPx.toDouble() / pageSize.width
        val width = (pageSize.width * scale).toInt().coerceAtLeast(1)
        val height = (pageSize.height * scale).toInt().coerceAtLeast(1)
        // ARGB: a slide without an opaque background keeps its transparency instead of the old
        // pipeline's forced white fill (the slide's own background — from slide, layout or
        // master — is painted by POI's DrawSlide).
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(Drawable.FONT_HANDLER, SlideFontRegistry.drawFontManager)
            graphics.scale(scale, scale)
            DrawFactory.getInstance(graphics).getDrawable(slide).draw(graphics)
        } finally {
            graphics.dispose()
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
        val pageWidthPt = pdfDocument!!.getPage(pageIndex).mediaBox.width
        val scale = targetWidthPx / pageWidthPt
        return renderer.renderImage(pageIndex, scale, ImageType.RGB)
    }

    private fun renderKeynoteThumbnail(source: DeckSource.KeynoteStatic, slideIndex: Int): BufferedImage {
        val entry = source.orderedThumbnailEntries[slideIndex]
        val bytes = KeynoteStaticSupport.readThumbnailBytes(source.file, entry)
            ?: throw IllegalStateException("Keynote thumbnail vanished: $entry")
        return ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalStateException("Undecodable Keynote thumbnail: $entry")
    }
}
