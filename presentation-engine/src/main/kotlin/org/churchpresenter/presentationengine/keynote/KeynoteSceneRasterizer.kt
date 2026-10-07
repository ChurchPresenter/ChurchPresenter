package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RasterLayer
import org.churchpresenter.presentationengine.model.RectPt
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Renders [KeynoteScene] content into ARGB bitmaps: full final frames for the static path and
 * per-layer bitmaps for animated playback. Text is laid out by [KeynoteTextPainter]; slides that
 * need more than this renderer offers were already gated to static by the parser. The deck's
 * images and movies come through [KeynoteDataFiles].
 */
internal class KeynoteSceneRasterizer(private val scene: KeynoteScene) : AutoCloseable {

    private val dataFiles = KeynoteDataFiles(scene.file)

    fun renderFinalFrame(slideIndex: Int, targetWidthPx: Int): BufferedImage {
        val slide = scene.slides[slideIndex]
        val scale = targetWidthPx / scene.slideWidthPt
        return paintSlide(scale) { graphics ->
            drawBackground(graphics, slide)
            for (placed in slide.drawables) drawDrawable(graphics, placed.drawable)
        }
    }

    fun rasterizeLayer(slideIndex: Int, spec: LayerSpec, targetWidthPx: Int): RasterLayer {
        val slide = scene.slides[slideIndex]
        val scale = targetWidthPx / scene.slideWidthPt
        return when (spec) {
            is LayerSpec.Background -> {
                val image = paintSlide(scale) { g ->
                    if (spec.zIndex == 0) drawBackground(g, slide)
                    for (drawableIndex in spec.shapeIndexes) drawDrawable(g, slide.drawables[drawableIndex].drawable)
                }
                RasterLayer(spec, image, 0, 0)
            }
            is LayerSpec.Shape -> paintBounded(spec.boundsPt, scale) { g ->
                drawDrawable(g, slide.drawables[spec.shapeIndex].drawable)
            }.let { RasterLayer(spec, it.image, it.offsetX, it.offsetY) }
            is LayerSpec.Media -> {
                val drawable = slide.drawables[spec.shapeIndex].drawable
                val painted = paintBounded(spec.boundsPt, scale) { g -> drawDrawable(g, drawable) }
                val videoFile = (drawable as? KnDrawable.Movie)?.videoFile?.let { extractDataFile(it) }
                RasterLayer(spec.copy(mediaFile = videoFile), painted.image, painted.offsetX, painted.offsetY)
            }
            is LayerSpec.ParagraphText -> paintBounded(spec.boundsPt, scale) { g ->
                drawParagraphLayer(g, slide.drawables[spec.shapeIndex].drawable as KnDrawable.Text, spec.paragraphIndex)
            }.let { RasterLayer(spec, it.image, it.offsetX, it.offsetY) }
            else -> throw IllegalArgumentException("Layer kind ${spec::class.simpleName} not produced for Keynote")
        }
    }

    override fun close() = dataFiles.close()

    /** A whole-slide bitmap at [scale] -- its size truncated, as the full frame always was -- drawn by [draw]. */
    private fun paintSlide(scale: Double, draw: (Graphics2D) -> Unit): BufferedImage {
        val width = (scene.slideWidthPt * scale).toInt().coerceAtLeast(1)
        val height = (scene.slideHeightPt * scale).toInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            applyHints(graphics)
            graphics.scale(scale, scale)
            draw(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }

    /** See [KeynoteDataFiles.extract]. */
    fun extractDataFile(fileName: String): File? = dataFiles.extract(fileName)

    // ── Drawing ───────────────────────────────────────────────────────────────

    /** One paragraph of a text box, in the box's own geometry; paragraph 0 also carries its fill. */
    private fun drawParagraphLayer(graphics: Graphics2D, drawable: KnDrawable.Text, paragraphIndex: Int) {
        val saved = graphics.transform
        try {
            applyGeometry(graphics, drawable.geometry)
            // The box's own fill/stroke is static across all its paragraph layers —
            // paint it once, with paragraph 0, rather than once per layer.
            if (paragraphIndex == 0) drawable.shape?.let { drawShapeContent(graphics, it) }
            KeynoteTextPainter.drawParagraphs(graphics, drawable, scene.slideWidthPt, onlyIndex = paragraphIndex)
        } finally {
            graphics.transform = saved
        }
    }

    private fun drawBackground(graphics: Graphics2D, slide: KnSlide) {
        val background = slide.background ?: return
        background.color?.let {
            graphics.color = it
            graphics.fill(Rectangle2D.Double(0.0, 0.0, scene.slideWidthPt, scene.slideHeightPt))
        }
        background.imageFile?.let { fileName ->
            dataFiles.image(fileName)?.let { image ->
                graphics.drawImage(image, 0, 0, scene.slideWidthPt.toInt(), scene.slideHeightPt.toInt(), null)
            }
        }
    }

    private fun drawDrawable(graphics: Graphics2D, drawable: KnDrawable) {
        val saved = graphics.transform
        try {
            applyGeometry(graphics, drawable.geometry)
            when (drawable) {
                is KnDrawable.Image -> drawPicture(graphics, drawable.dataFile, drawable.geometry)
                is KnDrawable.Shape -> drawShapeContent(graphics, drawable)
                is KnDrawable.Text -> {
                    drawable.shape?.let { drawShapeContent(graphics, it) }
                    KeynoteTextPainter.drawParagraphs(graphics, drawable, scene.slideWidthPt)
                }
                is KnDrawable.Group -> {
                    for (child in drawable.children) drawDrawable(graphics, child.drawable)
                }
                // Static poster frame — live playback (once decoded) replaces this bitmap app-side.
                is KnDrawable.Movie -> drawPicture(graphics, drawable.posterFile, drawable.geometry)
            }
        } finally {
            graphics.transform = saved
        }
    }

    /** [fileName] stretched over the drawable's box; nothing when there is no such image. */
    private fun drawPicture(graphics: Graphics2D, fileName: String?, g: KnGeometry) {
        val image = fileName?.let { dataFiles.image(it) } ?: return
        graphics.drawImage(image, 0, 0, g.w.toInt().coerceAtLeast(1), g.h.toInt().coerceAtLeast(1), null)
    }

    private fun drawShapeContent(graphics: Graphics2D, shape: KnDrawable.Shape) {
        val g = shape.geometry
        if (g.w <= 0 || g.h <= 0) return
        val outline = shape.path?.let { normalized ->
            val scaled = AffineTransform.getScaleInstance(g.w, g.h)
            scaled.createTransformedShape(normalized)
        } ?: Rectangle2D.Double(0.0, 0.0, g.w, g.h)

        val alpha = shape.opacity.coerceIn(0.0, 1.0).toFloat()
        val originalComposite = graphics.composite
        if (alpha < 1f) {
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        }
        try {
            shape.fill?.color?.let {
                graphics.color = it
                graphics.fill(outline)
            }
            shape.fill?.imageFile?.let { dataFiles.image(it) }?.let { image ->
                val clip = graphics.clip
                graphics.clip(outline)
                graphics.drawImage(image, 0, 0, g.w.toInt().coerceAtLeast(1), g.h.toInt().coerceAtLeast(1), null)
                graphics.clip = clip
            }
            if (shape.strokeColor != null && shape.strokeWidthPt > 0) {
                graphics.color = shape.strokeColor
                graphics.stroke = BasicStroke(shape.strokeWidthPt.toFloat())
                graphics.draw(outline)
            }
        } finally {
            graphics.composite = originalComposite
        }
    }
}

/** A bitmap of one layer and where its top-left corner sits on the slide, in pixels. */
private class Painted(val image: BufferedImage, val offsetX: Int, val offsetY: Int)

/**
 * A bitmap covering [bounds] (slide points) at [scale], with [draw] called in slide coordinates.
 * The bitmap starts at the pixel the bounds start in and ends at the one they end in, so a layer
 * composited at its offset lands exactly where the full frame would draw it.
 */
private fun paintBounded(bounds: RectPt, scale: Double, draw: (Graphics2D) -> Unit): Painted {
    val offsetX = floor(bounds.x * scale).toInt()
    val offsetY = floor(bounds.y * scale).toInt()
    val width = (ceil((bounds.x + bounds.w) * scale).toInt() - offsetX).coerceAtLeast(1)
    val height = (ceil((bounds.y + bounds.h) * scale).toInt() - offsetY).coerceAtLeast(1)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        applyHints(graphics)
        graphics.translate(-offsetX, -offsetY)
        graphics.scale(scale, scale)
        draw(graphics)
    } finally {
        graphics.dispose()
    }
    return Painted(image, offsetX, offsetY)
}

private fun applyHints(graphics: Graphics2D) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
}

/** Translate to the drawable's origin, rotating/flipping about its center. */
private fun applyGeometry(graphics: Graphics2D, geometry: KnGeometry) {
    graphics.translate(geometry.x, geometry.y)
    if (geometry.angle != 0.0 || geometry.hFlip || geometry.vFlip) {
        val cx = geometry.w / 2
        val cy = geometry.h / 2
        val transform = AffineTransform()
        transform.translate(cx, cy)
        if (geometry.angle != 0.0) transform.rotate(geometry.angle)
        transform.scale(if (geometry.hFlip) -1.0 else 1.0, if (geometry.vFlip) -1.0 else 1.0)
        transform.translate(-cx, -cy)
        graphics.transform(transform)
    }
}
