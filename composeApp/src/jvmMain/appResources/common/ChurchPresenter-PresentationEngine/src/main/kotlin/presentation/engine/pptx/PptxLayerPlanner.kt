package presentation.engine.pptx

import org.apache.poi.sl.usermodel.PlaceableShape
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import presentation.engine.model.LayerSpec
import presentation.engine.model.RectPt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Splits a slide into renderable layers for animated playback.
 *
 * Z-band flattening: maximal runs of consecutive never-animated shapes collapse into one
 * [LayerSpec.Background] band (the bottom band also carries the slide background and
 * master/layout graphics). Each animated shape becomes its own [LayerSpec.Shape]; a text shape
 * built paragraph-by-paragraph becomes one [LayerSpec.ParagraphText] per paragraph.
 *
 * A shape whose animated target is a *descendant* (inside a group) animates as the whole group —
 * documented degrade; per-child animation inside groups would need manual group-transform
 * plumbing that POI's per-shape drawing does not provide.
 */
internal object PptxLayerPlanner {

    /** Padding around a shape's rotated bounds for shadows/glows, in points. */
    private const val EFFECT_PADDING_PT = 20.0

    /** Extra margin as a fraction of the larger dimension (soft effects, minor overdraw). */
    private const val MARGIN_FRACTION = 0.25

    /**
     * Returns the layer decomposition for [slide], or null when the slide has no animation
     * targets and should stay a single static composite.
     */
    fun plan(slide: XSLFSlide, targets: AnimationTargetScanner.Targets): List<LayerSpec>? {
        if (targets.isEmpty) return null
        val slideBounds = RectPt(
            0.0, 0.0,
            slide.slideShow.pageSize.getWidth(),
            slide.slideShow.pageSize.getHeight()
        )

        val layers = mutableListOf<LayerSpec>()
        var band = mutableListOf<Int>()
        var z = 0

        fun flushBand(force: Boolean = false) {
            // The bottom band always exists (it carries background + master graphics) even when
            // no un-animated shape precedes the first animated one.
            if (band.isEmpty() && !force && z != 0) return
            layers.add(
                LayerSpec.Background(
                    id = "band-$z",
                    zIndex = z,
                    boundsPt = slideBounds,
                    shapeIndexes = band.toList()
                )
            )
            band = mutableListOf()
            z++
        }

        slide.shapes.forEachIndexed { shapeIndex, shape ->
            val isParagraphBuilt = shape is XSLFTextShape &&
                shape.shapeId.toLong() in targets.paragraphBuiltShapeIds
            val isAnimated = shapeOrDescendantTargeted(shape, targets.shapeIds)
            when {
                isParagraphBuilt -> {
                    flushBand(force = z == 0)
                    val bounds = paddedBounds(shape)
                    val paragraphCount = shape.textParagraphs.size.coerceAtLeast(1)
                    repeat(paragraphCount) { paragraphIndex ->
                        layers.add(
                            LayerSpec.ParagraphText(
                                id = "shape-$shapeIndex-para-$paragraphIndex",
                                zIndex = z++,
                                boundsPt = bounds,
                                shapeIndex = shapeIndex,
                                paragraphIndex = paragraphIndex,
                                initiallyVisible = true
                            )
                        )
                    }
                }
                isAnimated -> {
                    flushBand(force = z == 0)
                    layers.add(
                        LayerSpec.Shape(
                            id = "shape-$shapeIndex",
                            zIndex = z++,
                            boundsPt = paddedBounds(shape),
                            shapeIndex = shapeIndex,
                            initiallyVisible = true
                        )
                    )
                }
                else -> band.add(shapeIndex)
            }
        }
        // Trailing band, and the degenerate case of no animated shapes reaching here.
        if (band.isNotEmpty() || z == 0) flushBand(force = true)
        return layers
    }

    private fun shapeOrDescendantTargeted(shape: XSLFShape, targetIds: Set<Long>): Boolean {
        if (shape.shapeId.toLong() in targetIds) return true
        if (shape is XSLFGroupShape) {
            return shape.shapes.any { shapeOrDescendantTargeted(it, targetIds) }
        }
        return false
    }

    /** Axis-aligned bounds of the shape's rotated anchor, padded for effects, in points. */
    private fun paddedBounds(shape: XSLFShape): RectPt {
        val anchor = shape.anchor
        val rotation = Math.toRadians((shape as? PlaceableShape<*, *>)?.rotation ?: 0.0)
        val cx = anchor.centerX
        val cy = anchor.centerY
        val halfW = anchor.width / 2
        val halfH = anchor.height / 2
        val cosR = abs(cos(rotation))
        val sinR = abs(sin(rotation))
        val rotHalfW = halfW * cosR + halfH * sinR
        val rotHalfH = halfW * sinR + halfH * cosR
        val pad = EFFECT_PADDING_PT + MARGIN_FRACTION * max(anchor.width, anchor.height)
        return RectPt(
            x = cx - rotHalfW - pad,
            y = cy - rotHalfH - pad,
            w = (rotHalfW + pad) * 2,
            h = (rotHalfH + pad) * 2
        )
    }
}
