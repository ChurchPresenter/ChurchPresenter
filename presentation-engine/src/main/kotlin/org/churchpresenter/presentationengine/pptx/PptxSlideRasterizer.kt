package org.churchpresenter.presentationengine.pptx

import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.drawingml.x2006.main.CTColor
import org.openxmlformats.schemas.drawingml.x2006.main.CTEffectList
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextField
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextLineBreak
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RasterLayer
import org.churchpresenter.presentationengine.model.RectPt
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import org.churchpresenter.presentationengine.DeckRasterizer

/**
 * Renders the [PptxLayerPlanner]'s layer decomposition into transparent ARGB bitmaps.
 *
 * Per-paragraph layers use transparent-run mutation: every *other* paragraph's runs (and bullet)
 * get a fully transparent font color, the whole shape is drawn — so text layout, autofit and
 * wrapping stay byte-identical — and the paragraph XML is restored from snapshots afterwards.
 * The rejected alternative (clipping per-paragraph Y-bands out of POI's text layout) would need
 * re-derivation of internal line-break positions and fails on rotated/vertical text.
 */
internal object PptxSlideRasterizer {

    /**
     * [extractedTempFiles] caches embedded video files extracted from the pptx zip, keyed by
     * relationship id — owned by the caller ([DeckRasterizer], which already
     * owns the rest of this deck's per-open lifecycle) so temp files are extracted once and
     * deleted when the deck closes rather than relying on `deleteOnExit()` alone.
     */
    fun rasterizeLayer(
        slide: XSLFSlide,
        spec: LayerSpec,
        scale: Double,
        extractedTempFiles: MutableMap<String, File?> = mutableMapOf()
    ): RasterLayer {
        return when (spec) {
            is LayerSpec.Background -> RasterLayer(
                spec = spec,
                image = renderBand(slide, spec, scale),
                offsetXPx = 0,
                offsetYPx = 0
            )
            is LayerSpec.Shape -> renderCropped(slide, spec, spec.boundsPt, scale) { graphics ->
                drawShape(graphics, slide.shapes[spec.shapeIndex])
            }
            is LayerSpec.ParagraphText -> renderCropped(slide, spec, spec.boundsPt, scale) { graphics ->
                val shape = slide.shapes[spec.shapeIndex] as XSLFTextShape
                withOnlyParagraphVisible(shape, spec.paragraphIndex) {
                    drawShape(graphics, shape)
                }
            }
            is LayerSpec.Media -> {
                // The shape's own picture data is the poster/first frame — draw it exactly like
                // an ordinary picture, no video-specific drawing needed.
                val raster = renderCropped(slide, spec, spec.boundsPt, scale) { graphics ->
                    drawShape(graphics, slide.shapes[spec.shapeIndex])
                }
                val videoShape = slide.shapes[spec.shapeIndex] as? XSLFPictureShape
                val videoFile = videoShape?.let { extractVideoFile(slide, it, extractedTempFiles) }
                RasterLayer(spec.copy(mediaFile = videoFile), raster.image, raster.offsetXPx, raster.offsetYPx)
            }
            is LayerSpec.StaticComposite ->
                throw IllegalArgumentException("Layer kind ${spec::class.simpleName} is not rasterized per-shape")
        }
    }

    /**
     * Resolves an embedded video shape's data to a real filesystem [File] a decoder can open
     * directly. POI has no typed relation for the `video`/media2007 relationship types, so this
     * always resolves to a generic `POIXMLDocumentPart` — the raw zip-entry bytes behind it are
     * extracted to a temp file once (cached in [cache] by relationship id) and deleted by the
     * caller's `close()`, mirroring `KeynoteSceneRasterizer.extractDataFile`'s temp-file lifecycle.
     */
    private fun extractVideoFile(
        slide: XSLFSlide,
        shape: XSLFPictureShape,
        cache: MutableMap<String, File?>
    ): File? {
        if (!shape.isVideoFile) return null
        val relId = shape.videoFileLink ?: return null
        return cache.getOrPut(relId) {
            try {
                val part = slide.getRelationById(relId) ?: return@getOrPut null
                val packagePart = part.packagePart
                // Preserve the real extension (e.g. .mov/.mp4) from the zip entry's own part name
                // (".../ppt/media/media1.mov") — some decoders sniff container format from it.
                val ext = packagePart.partName.name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
                val temp = File.createTempFile("pptx_media_", if (ext != null) ".$ext" else ".bin")
                packagePart.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                temp
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * A full-slide band: the bottom band (zIndex 0) draws the slide background and master/layout
     * graphics exactly like POI's DrawSlide does, then the band's own shapes.
     */
    private fun renderBand(slide: XSLFSlide, spec: LayerSpec.Background, scale: Double): BufferedImage {
        val pageSize = slide.slideShow.pageSize
        val width = (pageSize.getWidth() * scale).toInt().coerceAtLeast(1)
        val height = (pageSize.getHeight() * scale).toInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            applyHints(graphics, slide)
            graphics.scale(scale, scale)
            if (spec.zIndex == 0) drawBackdrop(graphics, slide)
            for (shapeIndex in spec.shapeIndexes) {
                drawShape(graphics, slide.shapes[shapeIndex])
            }
        } finally {
            graphics.dispose()
        }
        return image
    }

    /** The slide's background, and its master's graphics when it follows them; either may fail alone. */
    private fun drawBackdrop(graphics: Graphics2D, slide: XSLFSlide) {
        val factory = DrawFactory.getInstance(graphics)
        runCatching { slide.background?.let { factory.getDrawable(it).draw(graphics) } }
        if (slide.followMasterGraphics) {
            runCatching { slide.masterSheet?.let { factory.getDrawable(it).draw(graphics) } }
        }
    }

    private fun renderCropped(
        slide: XSLFSlide,
        spec: LayerSpec,
        boundsPt: RectPt,
        scale: Double,
        draw: (Graphics2D) -> Unit
    ): RasterLayer {
        val offsetX = floor(boundsPt.x * scale).toInt()
        val offsetY = floor(boundsPt.y * scale).toInt()
        val width = (ceil((boundsPt.x + boundsPt.w) * scale).toInt() - offsetX).coerceAtLeast(1)
        val height = (ceil((boundsPt.y + boundsPt.h) * scale).toInt() - offsetY).coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            applyHints(graphics, slide)
            graphics.translate(-offsetX, -offsetY)
            graphics.scale(scale, scale)
            draw(graphics)
        } finally {
            graphics.dispose()
        }
        return RasterLayer(spec, image, offsetX, offsetY)
    }

    /**
     * Draws one shape, skipping it if it throws.
     *
     * This is the layered playback path, where an unguarded throw costs the caller its whole band
     * (and through `DeckRasterizer.rasterizeSlideLayers`, the slide's entire layer list) for one
     * bad element. The same swallow-and-degrade choice the file already makes in
     * `extractVideoFile`.
     *
     * The shape's own rotation and flips are applied first, as POI's sheet drawing does for each
     * shape before it draws it: `draw` alone assumes they are already on the graphics, so without
     * them a picture turned upside down in PowerPoint came out upright on any slide with a build.
     */
    private fun drawShape(graphics: Graphics2D, shape: XSLFShape) {
        val before = graphics.transform
        try {
            graphics.setRenderingHint(Drawable.GROUP_TRANSFORM, AffineTransform())
            val drawable = DrawFactory.getInstance(graphics).getDrawable(shape)
            drawable.applyTransform(graphics)
            drawable.draw(graphics)
        } catch (_: Throwable) {
            // Left out of this layer; the rest of the band still draws.
        } finally {
            graphics.transform = before
        }
    }

    private fun applyHints(graphics: Graphics2D, slide: XSLFSlide) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(Drawable.FONT_HANDLER, SlideFontRegistry.drawFontManager)
        graphics.setRenderingHint(Drawable.DRAW_FACTORY, SlideDrawFactory)
        // DrawSlide sets this before drawing; per-shape drawing needs it too (placeholder
        // visibility checks in DrawSimpleShape dereference the current slide).
        graphics.setRenderingHint(Drawable.CURRENT_SLIDE, slide)
    }

    /**
     * Runs [block] with every paragraph except [visibleIndex] made invisible via transparent
     * font/bullet colors (layout-preserving — hidden paragraphs still occupy their lines), then
     * reverts every mutation. Mutations are surgical — only fill elements inside each run's
     * `rPr` (and the paragraph's `buClr`) are touched, never whole paragraph nodes: replacing a
     * paragraph's XML wholesale disconnects POI's cached run wrappers
     * (XmlValueDisconnectedException on the next draw). A run that cannot be hidden simply
     * appears in every layer of the shape (builds still work, that text just shows early).
     */
    private fun withOnlyParagraphVisible(shape: XSLFTextShape, visibleIndex: Int, block: () -> Unit) {
        val guards = mutableListOf<RunFillGuard>()
        try {
            shape.textParagraphs.forEachIndexed { index, paragraph ->
                if (index == visibleIndex) return@forEachIndexed
                for (run in paragraph.textRuns) {
                    try {
                        RunFillGuard.hide(run.xmlObject)?.let { guards.add(it) }
                    } catch (_: Exception) {
                    }
                }
                try {
                    paragraphHasVisibleBullet(paragraph.xmlObject.pPr)?.let { guards.add(it) }
                } catch (_: Exception) {
                }
            }
            block()
        } finally {
            for (guard in guards.asReversed()) {
                try {
                    guard.restore()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun paragraphHasVisibleBullet(pPr: CTTextParagraphProperties?): RunFillGuard? {
        // Only paragraphs with an explicit bullet glyph need hiding; an inherited bullet with no
        // color of its own takes its first run's, which is already transparent, and one whose list
        // style colors it is acceptable overdraw (rare, and only until WS6 polish).
        if (pPr == null || (!pPr.isSetBuChar && !pPr.isSetBuAutoNum)) return null
        return RunFillGuard.hideBullet(pPr)
    }

    /**
     * Reversible "make this run invisible" mutation: swaps the run's fill for a fully
     * transparent solid fill and puts the original back on [restore].
     */
    internal class RunFillGuard private constructor(private val restoreAction: () -> Unit) {

        fun restore() = restoreAction()

        companion object {
            fun hide(runXml: XmlObject): RunFillGuard? {
                val (hadRPr, rPr) = when (runXml) {
                    is CTRegularTextRun -> runXml.isSetRPr to (runXml.rPr ?: runXml.addNewRPr())
                    is CTTextField -> runXml.isSetRPr to (runXml.rPr ?: runXml.addNewRPr())
                    is CTTextLineBreak -> return null // line breaks draw nothing
                    else -> return null
                }
                val originalSolid =
                    if (rPr.isSetSolidFill) rPr.solidFill.copy() as CTSolidColorFillProperties else null
                if (rPr.isSetSolidFill) rPr.unsetSolidFill()
                applyTransparentFill(rPr)
                val restoreDecorations = hideDecorations(rPr)
                return RunFillGuard {
                    if (rPr.isSetSolidFill) rPr.unsetSolidFill()
                    restoreDecorations()
                    when {
                        originalSolid != null -> rPr.solidFill = originalSolid
                        !hadRPr -> when (runXml) {
                            is CTRegularTextRun -> if (runXml.isSetRPr) runXml.unsetRPr()
                            is CTTextField -> if (runXml.isSetRPr) runXml.unsetRPr()
                        }
                    }
                }
            }

            /**
             * Takes away what a run draws in colors of its own — its outline, its glow and shadow,
             * its highlight — which a transparent fill leaves standing, and returns what puts them
             * back. Left in, every paragraph's effects showed in every other paragraph's layer.
             */
            private fun hideDecorations(rPr: CTTextCharacterProperties): () -> Unit {
                val outline = if (rPr.isSetLn) rPr.ln.copy() as CTLineProperties else null
                val effects = if (rPr.isSetEffectLst) rPr.effectLst.copy() as CTEffectList else null
                val highlight = if (rPr.isSetHighlight) rPr.highlight.copy() as CTColor else null
                if (outline != null) rPr.unsetLn()
                if (effects != null) rPr.unsetEffectLst()
                if (highlight != null) rPr.unsetHighlight()
                return {
                    if (outline != null) rPr.ln = outline
                    if (effects != null) rPr.effectLst = effects
                    if (highlight != null) rPr.highlight = highlight
                }
            }

            fun hideBullet(pPr: CTTextParagraphProperties): RunFillGuard {
                val originalBuClr = if (pPr.isSetBuClr) pPr.buClr.copy() as CTColor else null
                if (pPr.isSetBuClr) pPr.unsetBuClr()
                val buClr = pPr.addNewBuClr()
                val srgb = buClr.addNewSrgbClr()
                srgb.`val` = byteArrayOf(0, 0, 0)
                srgb.addNewAlpha().`val` = 0
                return RunFillGuard {
                    if (pPr.isSetBuClr) pPr.unsetBuClr()
                    if (originalBuClr != null) pPr.buClr = originalBuClr
                }
            }

            /**
             * A gradient/pattern/picture fill on the run stays in place — the transparent solid
             * fill added here wins only when it is the run's sole fill, so those exotic runs are
             * left visible (documented overdraw) rather than corrupted.
             */
            private fun applyTransparentFill(rPr: CTTextCharacterProperties) {
                if (rPr.isSetGradFill || rPr.isSetPattFill || rPr.isSetBlipFill) return
                val solid = rPr.addNewSolidFill()
                val srgb = solid.addNewSrgbClr()
                srgb.`val` = byteArrayOf(0, 0, 0)
                srgb.addNewAlpha().`val` = 0
            }
        }
    }
}
