package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeAnimatedRect
import org.churchpresenter.lottiegen.lottie.makeAnimatedStroke
import org.churchpresenter.lottiegen.lottie.makeCurvedPath
import org.churchpresenter.lottiegen.lottie.makeEllipse
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGradientFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeRepeater
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTrimPath
import kotlin.math.max
import kotlin.math.min

/** The four shape attributes that travel together from an element to [SpecShapes.buildShape]. */
internal class ShapeAttrs(
    val size: SizeSpec,
    val corner: CornerSpec,
    val growFrom: GrowOrigin,
    val repeat: RepeatSpec?,
)

/** Builds the shape elements of a spec: rects, ellipses, polygons and paths. */
internal class SpecShapes(
    private val builder: LottieBuilder,
    private val layout: SpecLayoutContext,
    private val tracks: SpecTracks,
) {

    fun buildShape(element: ElementSpec, attrs: ShapeAttrs, paint: PaintSpec) {
        val size = attrs.size
        val (w, h) = layout.sizeOf(size)
        val rest = layout.resolve(element.placement).let { point ->
            // Full-canvas bands are always horizontally centered on the canvas.
            if (size is SizeSpec.CanvasWidth) SpecPoint(layout.canvasW / 2, point.y) else point
        }
        val cornerPx = layout.cornerPx(attrs.corner)
        val sizeTrack = tracks.trackFor(element, AnimProperty.RECT_SIZE)

        val shapeItem = if (element is EllipseElement) {
            makeEllipse(w, h)
        } else if (sizeTrack != null) {
            val sizeKFs = tracks.compileTrack(sizeTrack) { values ->
                jsonArrayOf(values.getOrElse(0) { 1.0 } * w, values.getOrElse(1) { 1.0 } * h)
            }
            makeAnimatedRect(sizeKFs, cornerPx)
        } else {
            makeRect(w, h, cornerPx)
        }

        val items = mutableListOf(shapeItem)
        items.addAll(paintItems(element, paint))
        trimItem(element)?.let { items.add(it) }
        attrs.repeat?.let { items.add(repeaterItem(element, it)) }

        val edgeGrow = sizeTrack != null && attrs.growFrom == GrowOrigin.ALIGN_EDGE && !layout.isCenter &&
            tracks.trackFor(element, AnimProperty.POSITION_OFFSET) == null
        val transform = if (edgeGrow) {
            // Keep the alignment-side edge fixed while the rect grows inward: synthesize
            // position keyframes matching the size track's timing (pivot not applicable).
            val inward = layout.flowSign(element.placement)
            val edgeX = rest.x - inward * w / 2
            val posKFs = tracks.compileTrack(sizeTrack) { values ->
                val wNow = values.getOrElse(0) { 1.0 } * w
                jsonArrayOf(edgeX + inward * wNow / 2, rest.y, 0.0)
            }
            LottieBuilder.defaultTransform(
                opacity = tracks.opacityProp(element, restOpacity = 100.0),
                rotation = tracks.rotationProp(element),
                position = LottieBuilder.animatedProp(posKFs),
                scale = tracks.scaleProp(element, baseScale = 100.0)
            )
        } else {
            shapeTransform(element, rest, w, h)
        }

        builder.addShapeLayer(element.name, buildJsonArray { add(makeGroup(items)) }, transform)
    }


    fun buildPolygon(
        element: PolygonElement
    ) {
        val flow = layout.flowSign(element.placement)
        val naturalXs = element.verticesEm.map { layout.em(it.getOrElse(0) { 0.0 }) }
        val naturalW = if (naturalXs.isEmpty()) 0.0 else naturalXs.max() - naturalXs.min()
        val fit = layout.fitFactor(element.fitWidthTo, naturalW)
        val vertices = element.verticesEm.map { v ->
            listOf(layout.em(v.getOrElse(0) { 0.0 }) * flow * fit, layout.em(v.getOrElse(1) { 0.0 }))
        }
        val (w, h) = layout.resolveSize(element)
        val rest = layout.resolve(element.placement)

        val items = mutableListOf(makePath(vertices, element.closed))
        items.addAll(paintItems(element, element.paint))
        trimItem(element)?.let { items.add(it) }
        element.repeat?.let { items.add(repeaterItem(element, it)) }

        builder.addShapeLayer(
            element.name,
            buildJsonArray { add(makeGroup(items)) },
            shapeTransform(element, rest, w, h)
        )
    }


    fun buildPath(
        element: PathElement
    ) {
        // The flow sign flips vertex AND tangent x-components, so a curve keeps its
        // handedness mirrored on right alignment; the fit factor stretches both so a
        // fitted curve keeps its shape proportions.
        val naturalXs = element.verticesEm.map { layout.em(it.x) }
        val naturalW = if (naturalXs.isEmpty()) 0.0 else naturalXs.max() - naturalXs.min()
        val fx = layout.flowSign(element.placement) * layout.fitFactor(element.fitWidthTo, naturalW)
        val vertices = element.verticesEm.map { listOf(layout.em(it.x) * fx, layout.em(it.y)) }
        val inTangents = element.verticesEm.map { listOf(layout.em(it.inX) * fx, layout.em(it.inY)) }
        val outTangents = element.verticesEm.map { listOf(layout.em(it.outX) * fx, layout.em(it.outY)) }
        val (w, h) = layout.resolveSize(element)
        val rest = layout.resolve(element.placement)

        val items = mutableListOf(makeCurvedPath(vertices, inTangents, outTangents, element.closed))
        items.addAll(paintItems(element, element.paint))
        trimItem(element)?.let { items.add(it) }
        element.repeat?.let { items.add(repeaterItem(element, it)) }

        builder.addShapeLayer(
            element.name,
            buildJsonArray { add(makeGroup(items)) },
            shapeTransform(element, rest, w, h)
        )
    }

    /**
     * Common shape-layer transform. A non-zero pivot moves the layer anchor (and shifts
     * the position identically, so the rest pose is unchanged) — rotation and scale
     * tracks then orbit the pivot instead of the element center.
     */

    fun shapeTransform(
        element: ElementSpec,
        rest: SpecPoint,
        w: Double,
        h: Double
    ): JsonObject {
        val flow = layout.flowSign(element.placement)
        val pivotX = layout.em(element.placement.pivotXEm) * flow
        val pivotY = layout.em(element.placement.pivotYEm)
        val hasPivot = pivotX != 0.0 || pivotY != 0.0
        val shiftedRest = if (hasPivot) SpecPoint(rest.x + pivotX, rest.y + pivotY) else rest
        return LottieBuilder.defaultTransform(
            opacity = tracks.opacityProp(element, restOpacity = 100.0),
            rotation = tracks.rotationProp(element),
            position = tracks.positionProp(element, shiftedRest, w, h),
            anchor = if (hasPivot) LottieBuilder.staticPropArray(pivotX, pivotY, 0.0) else null,
            scale = tracks.scaleProp(element, baseScale = 100.0)
        )
    }

    /** Compiles a TRIM track into a Trim Paths shape item (null when the element has none). */

    fun trimItem(element: ElementSpec): JsonObject? {
        val track = tracks.trackFor(element, AnimProperty.TRIM) ?: return null
        val startKFs = tracks.compileTrack(track) { values ->
            jsonArrayOf(values.getOrElse(0) { 0.0 } * 100.0)
        }
        val endKFs = tracks.compileTrack(track) { values ->
            jsonArrayOf(values.getOrElse(1) { 1.0 } * 100.0)
        }
        return makeTrimPath(startKFs = startKFs, endKFs = endKFs)
    }


    fun repeaterItem(element: ElementSpec, repeat: RepeatSpec): JsonObject {
        val flow = layout.flowSign(element.placement)
        val basis = repeat.fitWidthTo
        val offsetXPx = if (basis != null && repeat.copies > 1) {
            val sign = if (repeat.offsetXEm < 0) -1.0 else 1.0
            sign * layout.basisWidthPx(basis) / (repeat.copies - 1)
        } else {
            layout.em(repeat.offsetXEm)
        }
        return makeRepeater(
            copies = repeat.copies,
            offsetPx = listOf(offsetXPx * flow, layout.em(repeat.offsetYEm)),
            rotationDeg = repeat.rotationDeg,
            scalePct = repeat.scalePct,
            endOpacity = if (repeat.fadeOut) 0.0 else 100.0
        )
    }


    fun paintItems(element: ElementSpec, paint: PaintSpec): List<JsonObject> =
        listOfNotNull(
            paint.fill?.let { fillItem(element, it) },
            paint.stroke?.let { strokeItem(element, it) },
        )

    /** A flat fill, or a linear gradient when the spec asks for one. */
    private fun fillItem(element: ElementSpec, fill: FillSpec): JsonObject {
        val color = layout.roleColor(fill.role)
        val alpha = layout.roleAlpha(fill.role) * fill.alphaFactor
        val gradient = fill.gradient ?: return makeFill(color, alpha)
        val flow = layout.flowSign(element.placement)
        return makeGradientFill(
            color, alpha,
            listOf(layout.em(gradient.startXEm) * flow, layout.em(gradient.startYEm)),
            listOf(layout.em(gradient.endXEm) * flow, layout.em(gradient.endYEm)),
        )
    }

    /** A stroke, animated when the element has a STROKE_WIDTH track. Null when it is hairline. */
    private fun strokeItem(element: ElementSpec, stroke: StrokeSpec): JsonObject? {
        val widthPx = layout.strokeWidthPx(stroke.width)
        if (widthPx <= 0) return null
        val color = layout.roleColor(stroke.role)
        val alpha = layout.roleAlpha(stroke.role) * stroke.alphaFactor
        val dashPx = layout.em(stroke.dashEm)
        val widthTrack = tracks.trackFor(element, AnimProperty.STROKE_WIDTH)
            ?: return makeStroke(color, widthPx, alpha, dashPx)
        val widthKFs = tracks.compileTrack(widthTrack) { values ->
            jsonArrayOf(values.getOrElse(0) { 1.0 } * widthPx)
        }
        return makeAnimatedStroke(color, widthKFs, alpha, dashPx)
    }

    // ------------------------------------------------------- track compilation

}
