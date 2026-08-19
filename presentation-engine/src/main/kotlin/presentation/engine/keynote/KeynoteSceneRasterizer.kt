package presentation.engine.keynote

import presentation.engine.fonts.SlideFontRegistry
import presentation.engine.model.LayerSpec
import presentation.engine.model.RasterLayer
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.text.AttributedString
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Renders [KeynoteScene] content into ARGB bitmaps: full final frames for the static path and
 * per-layer bitmaps for animated playback. Text is laid out by the engine itself
 * (LineBreakMeasurer over [SlideFontRegistry]-resolved fonts) — the acknowledged fidelity risk
 * of the native Keynote path; slides that need more than this renderer offers were already
 * gated to static by the parser.
 */
internal class KeynoteSceneRasterizer(private val scene: KeynoteScene) : AutoCloseable {

    private var zipFile: ZipFile? = null
    private val imageCache = HashMap<String, BufferedImage?>()
    private val extractedTempFiles = HashMap<String, File?>()

    fun renderFinalFrame(slideIndex: Int, targetWidthPx: Int): BufferedImage {
        val slide = scene.slides[slideIndex]
        val scale = targetWidthPx / scene.slideWidthPt
        val width = (scene.slideWidthPt * scale).toInt().coerceAtLeast(1)
        val height = (scene.slideHeightPt * scale).toInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            applyHints(graphics)
            graphics.scale(scale, scale)
            drawBackground(graphics, slide)
            for (placed in slide.drawables) drawDrawable(graphics, placed.drawable)
        } finally {
            graphics.dispose()
        }
        return image
    }

    fun rasterizeLayer(slideIndex: Int, spec: LayerSpec, targetWidthPx: Int): RasterLayer {
        val slide = scene.slides[slideIndex]
        val scale = targetWidthPx / scene.slideWidthPt
        return when (spec) {
            is LayerSpec.Background -> {
                val width = (scene.slideWidthPt * scale).toInt().coerceAtLeast(1)
                val height = (scene.slideHeightPt * scale).toInt().coerceAtLeast(1)
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    applyHints(graphics)
                    graphics.scale(scale, scale)
                    if (spec.zIndex == 0) drawBackground(graphics, slide)
                    for (drawableIndex in spec.shapeIndexes) {
                        drawDrawable(graphics, slide.drawables[drawableIndex].drawable)
                    }
                } finally {
                    graphics.dispose()
                }
                RasterLayer(spec, image, 0, 0)
            }
            is LayerSpec.Shape -> {
                val offsetX = floor(spec.boundsPt.x * scale).toInt()
                val offsetY = floor(spec.boundsPt.y * scale).toInt()
                val width = (ceil((spec.boundsPt.x + spec.boundsPt.w) * scale).toInt() - offsetX).coerceAtLeast(1)
                val height = (ceil((spec.boundsPt.y + spec.boundsPt.h) * scale).toInt() - offsetY).coerceAtLeast(1)
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    applyHints(graphics)
                    graphics.translate(-offsetX, -offsetY)
                    graphics.scale(scale, scale)
                    drawDrawable(graphics, slide.drawables[spec.shapeIndex].drawable)
                } finally {
                    graphics.dispose()
                }
                RasterLayer(spec, image, offsetX, offsetY)
            }
            is LayerSpec.Media -> {
                val offsetX = floor(spec.boundsPt.x * scale).toInt()
                val offsetY = floor(spec.boundsPt.y * scale).toInt()
                val width = (ceil((spec.boundsPt.x + spec.boundsPt.w) * scale).toInt() - offsetX).coerceAtLeast(1)
                val height = (ceil((spec.boundsPt.y + spec.boundsPt.h) * scale).toInt() - offsetY).coerceAtLeast(1)
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val drawable = slide.drawables[spec.shapeIndex].drawable
                val graphics = image.createGraphics()
                try {
                    applyHints(graphics)
                    graphics.translate(-offsetX, -offsetY)
                    graphics.scale(scale, scale)
                    drawDrawable(graphics, drawable)
                } finally {
                    graphics.dispose()
                }
                val videoFile = (drawable as? KnDrawable.Movie)?.videoFile?.let { extractDataFile(it) }
                RasterLayer(spec.copy(mediaFile = videoFile), image, offsetX, offsetY)
            }
            is LayerSpec.ParagraphText -> {
                val offsetX = floor(spec.boundsPt.x * scale).toInt()
                val offsetY = floor(spec.boundsPt.y * scale).toInt()
                val width = (ceil((spec.boundsPt.x + spec.boundsPt.w) * scale).toInt() - offsetX).coerceAtLeast(1)
                val height = (ceil((spec.boundsPt.y + spec.boundsPt.h) * scale).toInt() - offsetY).coerceAtLeast(1)
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    applyHints(graphics)
                    graphics.translate(-offsetX, -offsetY)
                    graphics.scale(scale, scale)
                    val drawable = slide.drawables[spec.shapeIndex].drawable as KnDrawable.Text
                    val saved = graphics.transform
                    try {
                        applyGeometry(graphics, drawable.geometry)
                        // The box's own fill/stroke is static across all its paragraph layers —
                        // paint it once, with paragraph 0, rather than once per layer.
                        if (spec.paragraphIndex == 0) drawable.shape?.let { drawShapeContent(graphics, it) }
                        drawParagraphs(graphics, drawable, onlyIndex = spec.paragraphIndex)
                    } finally {
                        graphics.transform = saved
                    }
                } finally {
                    graphics.dispose()
                }
                RasterLayer(spec, image, offsetX, offsetY)
            }
            else -> throw IllegalArgumentException("Layer kind ${spec::class.simpleName} not produced for Keynote")
        }
    }

    override fun close() {
        try {
            zipFile?.close()
        } catch (_: Exception) {
        }
        zipFile = null
        imageCache.clear()
        extractedTempFiles.values.forEach { it?.delete() }
        extractedTempFiles.clear()
    }

    /**
     * Resolves a Data/ file to a real filesystem [File] VLC can open directly. Directory-bundle
     * `.key` files already have one; zip-bundle files are extracted once to a temp file, cached
     * for the life of this rasterizer and deleted in [close] (not left to `deleteOnExit()`,
     * which would otherwise leak large `.mov` temp files for the whole app run).
     */
    fun extractDataFile(fileName: String): File? = extractedTempFiles.getOrPut(fileName) {
        val file = scene.file
        if (file.isDirectory) {
            File(File(file, "Data"), fileName).takeIf { it.isFile }
        } else {
            try {
                val zip = zipFile ?: ZipFile(file).also { zipFile = it }
                val entry = zip.getEntry("Data/$fileName") ?: zip.getEntry(fileName) ?: return@getOrPut null
                val temp = File.createTempFile("kn_media_", "_${fileName.substringAfterLast('/')}")
                zip.getInputStream(entry).use { input -> temp.outputStream().use { input.copyTo(it) } }
                temp
            } catch (_: Exception) {
                null
            }
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private fun applyHints(graphics: Graphics2D) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    private fun drawBackground(graphics: Graphics2D, slide: KnSlide) {
        val background = slide.background ?: return
        background.color?.let {
            graphics.color = it
            graphics.fill(Rectangle2D.Double(0.0, 0.0, scene.slideWidthPt, scene.slideHeightPt))
        }
        background.imageFile?.let { fileName ->
            loadImage(fileName)?.let { image ->
                graphics.drawImage(image, 0, 0, scene.slideWidthPt.toInt(), scene.slideHeightPt.toInt(), null)
            }
        }
    }

    private fun drawDrawable(graphics: Graphics2D, drawable: KnDrawable) {
        val saved = graphics.transform
        try {
            applyGeometry(graphics, drawable.geometry)
            when (drawable) {
                is KnDrawable.Image -> drawImage(graphics, drawable)
                is KnDrawable.Shape -> drawShape(graphics, drawable)
                is KnDrawable.Text -> {
                    drawable.shape?.let { drawShapeContent(graphics, it) }
                    drawParagraphs(graphics, drawable)
                }
                is KnDrawable.Group -> {
                    for (child in drawable.children) drawDrawable(graphics, child.drawable)
                }
                is KnDrawable.Movie -> drawMovie(graphics, drawable)
            }
        } finally {
            graphics.transform = saved
        }
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

    private fun drawImage(graphics: Graphics2D, drawable: KnDrawable.Image) {
        val image = loadImage(drawable.dataFile) ?: return
        val g = drawable.geometry
        graphics.drawImage(image, 0, 0, g.w.toInt().coerceAtLeast(1), g.h.toInt().coerceAtLeast(1), null)
    }

    /** Static poster frame — live playback (once decoded) replaces this bitmap app-side. */
    private fun drawMovie(graphics: Graphics2D, drawable: KnDrawable.Movie) {
        val poster = drawable.posterFile?.let { loadImage(it) } ?: return
        val g = drawable.geometry
        graphics.drawImage(poster, 0, 0, g.w.toInt().coerceAtLeast(1), g.h.toInt().coerceAtLeast(1), null)
    }

    private fun drawShape(graphics: Graphics2D, drawable: KnDrawable.Shape) = drawShapeContent(graphics, drawable)

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
            shape.fill?.imageFile?.let { fileName ->
                loadImage(fileName)?.let { image ->
                    val clip = graphics.clip
                    graphics.clip(outline)
                    graphics.drawImage(image, 0, 0, g.w.toInt().coerceAtLeast(1), g.h.toInt().coerceAtLeast(1), null)
                    graphics.clip = clip
                }
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

    /**
     * @param onlyIndex when non-null, only that paragraph is actually painted — every paragraph
     *   is still measured and advances `y`, so later paragraphs (and the stop-early check below)
     *   land at the same vertical position they would in the full render. This is the per-layer
     *   path for By-Paragraph/By-Bullet Keynote builds: unlike PPTX (which must mutate run XML
     *   through opaque POI drawing code to isolate one paragraph), Keynote already lays out text
     *   itself, so isolating one paragraph is just "skip painting, still advance" — the exact
     *   same loop drives both the whole-object render and the per-paragraph one, so they can't
     *   drift out of sync.
     */
    private fun drawParagraphs(graphics: Graphics2D, drawable: KnDrawable.Text, onlyIndex: Int? = null) {
        val g = drawable.geometry
        // Auto-sized text boxes persist size (0,0): lay out unwrapped from the anchor instead
        // (alignment offsets need a real box width, so they only apply to sized boxes).
        val autoSized = g.w <= 1.0
        val width = if (autoSized) (scene.slideWidthPt - g.x).toFloat().coerceAtLeast(10f) else g.w.toFloat()
        val frc: FontRenderContext = graphics.fontRenderContext
        var y = 0f
        for ((index, paragraph) in drawable.paragraphs.withIndex()) {
            if (paragraph.text.isBlank()) {
                y += (paragraph.fontSizePt * 1.2).toFloat()
                if (onlyIndex != null && index >= onlyIndex) break
                continue
            }
            y = layOutParagraph(graphics, paragraph, width, autoSized, frc, y, paint = onlyIndex == null || onlyIndex == index)
            if (onlyIndex != null && index >= onlyIndex) break
        }
    }

    /** Measures and (optionally) paints one paragraph, returning the advanced `y`. */
    private fun layOutParagraph(
        graphics: Graphics2D,
        paragraph: KnParagraph,
        width: Float,
        autoSized: Boolean,
        frc: FontRenderContext,
        startY: Float,
        paint: Boolean
    ): Float {
        var y = startY
        val family = paragraph.fontFamily?.let { SlideFontRegistry.resolveFamily(it) } ?: Font.SANS_SERIF
        var style = Font.PLAIN
        if (paragraph.bold) style = style or Font.BOLD
        if (paragraph.italic) style = style or Font.ITALIC
        val font = Font(family, style, 12).deriveFont(paragraph.fontSizePt.toFloat())

        val attributed = AttributedString(paragraph.text)
        attributed.addAttribute(TextAttribute.FONT, font)
        if (isRtl(paragraph.text)) {
            attributed.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL)
        }
        val measurer = LineBreakMeasurer(attributed.iterator, frc)
        if (paint) graphics.color = paragraph.color
        while (measurer.position < paragraph.text.length) {
            val layout = measurer.nextLayout(width) ?: break
            y += layout.ascent
            val lineX = when {
                autoSized -> 0f
                paragraph.alignment == 1 -> width - layout.advance      // right
                paragraph.alignment == 2 -> (width - layout.advance) / 2f // center
                else -> 0f                                              // left / justified
            }
            if (paint) layout.draw(graphics, lineX, y)
            y += layout.descent + layout.leading
        }
        return y
    }

    /** True when the paragraph's first strong-directional character is right-to-left. */
    private fun isRtl(text: String): Boolean {
        for (char in text) {
            when (Character.getDirectionality(char)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                else -> {}
            }
        }
        return false
    }

    private fun loadImage(fileName: String): BufferedImage? = imageCache.getOrPut(fileName) {
        try {
            val bytes = readDataFile(fileName) ?: return@getOrPut null
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            null
        }
    }

    private fun readDataFile(fileName: String): ByteArray? {
        val file = scene.file
        return if (file.isDirectory) {
            File(File(file, "Data"), fileName).takeIf { it.isFile }?.readBytes()
        } else {
            val zip = zipFile ?: ZipFile(file).also { zipFile = it }
            (zip.getEntry("Data/$fileName") ?: zip.getEntry(fileName))
                ?.let { zip.getInputStream(it).readBytes() }
        }
    }
}
