package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeAnimatedRect
import org.churchpresenter.lottiegen.lottie.makeAnimatedStroke
import org.churchpresenter.lottiegen.lottie.makeCurvedPath
import org.churchpresenter.lottiegen.lottie.makeEllipse
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGradientFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeRandomFadeAnimator
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeRepeater
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.makeTextDataWithAnimators
import org.churchpresenter.lottiegen.lottie.makeTextRevealAnimator
import org.churchpresenter.lottiegen.lottie.makeTrimPath
import org.churchpresenter.lottiegen.lottie.styles.StyleGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a [StyleSpec] through the exact same LottieBuilder/buildKeyframes pipeline
 * the hand-written styles use. One instance serves both the developer Style Editor's
 * live preview and — for shipped spec-based styles — the user-facing generator.
 */
class SpecStyleGenerator(private val spec: StyleSpec) : StyleGenerator {

    /** Warnings from the most recent [generate] call (editor status display). */
    var lastWarnings: List<String> = emptyList()
        private set

    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val layout = SpecLayoutContext(spec, cfg)
        for (element in spec.elements) {
            if (!layout.visible(element.visibleWhen)) continue
            if (element.placement.alignOverrides[cfg.align]?.hidden == true) continue
            when (element) {
                is LogoElement -> buildLogo(builder, cfg, layout, element)
                is ImageElement -> buildImage(builder, cfg, layout, element)
                is TextElement -> buildText(builder, cfg, layout, element)
                is RectElement -> buildShape(
                    builder, cfg, layout, element,
                    element.size, element.paint, element.corner, element.growFrom, element.repeat
                )
                is BackgroundElement -> buildShape(
                    builder, cfg, layout, element,
                    element.size, backgroundPaint(layout, element), element.corner, element.growFrom, null
                )
                is EllipseElement -> buildShape(
                    builder, cfg, layout, element,
                    element.size, element.paint, CornerSpec.None, GrowOrigin.CENTER, element.repeat
                )
                is PolygonElement -> buildPolygon(builder, cfg, layout, element)
                is PathElement -> buildPath(builder, cfg, layout, element)
            }
        }
        lastWarnings = layout.warnings.toList()
    }

    // ------------------------------------------------------------------ logo

    private fun buildLogo(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
        element: LogoElement
    ) {
        val logoData = cfg.logoData ?: return
        if (cfg.logoW <= 0 || cfg.logoH <= 0) return
        val assetId = "logo_${element.id}"
        builder.addImageAsset(assetId, logoData, cfg.logoW, cfg.logoH)

        val (w, h) = layout.resolveSize(element)
        val logoSizeEm = element.sizeEm ?: cfg.logoSize.toDouble()
        val baseScale = layout.em(logoSizeEm) / max(cfg.logoW, cfg.logoH).toDouble() * 100
        val rest = layout.resolve(element.placement)

        builder.addImageLayer(
            element.name, assetId,
            LottieBuilder.defaultTransform(
                opacity = opacityProp(builder, cfg, element, restOpacity = 100.0),
                rotation = rotationProp(builder, cfg, element),
                position = positionProp(builder, cfg, layout, element, rest, w, h),
                anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                scale = scaleProp(builder, cfg, element, baseScale)
            )
        )
    }

    // ------------------------------------------------------------------ image

    /**
     * A designer-baked embedded image (see [ImageElement]): one image asset + a ty=2
     * layer scaled from its natural dims onto the resolved box. COVER and rounded
     * corners clip via the same td/tt matte pattern as [buildTextMask]; the matte
     * shares the image's position/rotation props so an animated image stays clipped.
     */
    private fun buildImage(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
        element: ImageElement
    ) {
        if (element.dataUri.isEmpty() || element.naturalW <= 0 || element.naturalH <= 0) return
        val (w, h) = layout.resolveSize(element)
        if (w <= 0.0 || h <= 0.0) return
        val assetId = "img_${element.id}"
        builder.addImageAsset(assetId, element.dataUri, element.naturalW, element.naturalH)

        val fitX = w / element.naturalW
        val fitY = h / element.naturalH
        val (baseX, baseY) = when (element.scaleMode) {
            ImageScaleMode.FIT -> min(fitX, fitY).let { it to it }
            ImageScaleMode.STRETCH -> fitX to fitY
            ImageScaleMode.COVER -> max(fitX, fitY).let { it to it }
        }
        val rest = layout.resolve(element.placement)
        val matted = element.corner != CornerSpec.None || element.scaleMode == ImageScaleMode.COVER
        if (matted) {
            builder.addShapeLayer(
                "${element.name} Mask",
                buildJsonArray {
                    add(
                        makeGroup(
                            listOf(
                                makeRect(w, h, layout.cornerPx(element.corner)),
                                makeFill(listOf(1.0, 1.0, 1.0))
                            )
                        )
                    )
                },
                LottieBuilder.defaultTransform(
                    rotation = rotationProp(builder, cfg, element),
                    position = positionProp(builder, cfg, layout, element, rest, w, h)
                ),
                td = 1
            )
        }
        builder.addImageLayer(
            element.name, assetId,
            LottieBuilder.defaultTransform(
                opacity = opacityProp(builder, cfg, element, restOpacity = 100.0 * element.alphaFactor),
                rotation = rotationProp(builder, cfg, element),
                position = positionProp(builder, cfg, layout, element, rest, w, h),
                anchor = LottieBuilder.staticPropArray(element.naturalW / 2.0, element.naturalH / 2.0, 0.0),
                scale = scalePropXY(builder, cfg, element, baseX * 100.0, baseY * 100.0)
            ),
            tt = if (matted) 1 else null
        )
    }

    // ------------------------------------------------------------------ text

    private fun buildText(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
        element: TextElement
    ) {
        val text: String
        val sizePx: Double
        val weight: Int
        val caseTransform: String
        val alpha: Double
        val color: List<Double>
        when (element.field) {
            TextFieldRef.NAME -> {
                text = cfg.nameText
                sizePx = layout.nameSizePx
                weight = cfg.nameWeight
                caseTransform = cfg.nameTransform
            }
            TextFieldRef.INFO -> {
                text = cfg.infoText
                sizePx = layout.infoSizePx
                weight = cfg.infoWeight
                caseTransform = cfg.infoTransform
            }
        }
        val paintRole = element.colorRole
            ?: if (element.field == TextFieldRef.NAME) ColorRole.NAME else ColorRole.INFO
        alpha = layout.roleAlpha(paintRole)
        color = layout.roleColor(paintRole)
        val (w, h) = layout.resolveSize(element)
        val justify = layout.justify()
        val rest = layout.resolve(element.placement)

        val mask = element.maskReveal
        if (mask != null) {
            buildTextMask(builder, cfg, layout, element, mask, rest, w, sizePx, justify)
        }

        builder.addFont(cfg.fontFamily, weight)
        val animator = element.animator
        val textData = if (animator == null) {
            makeTextData(text, cfg.fontFamily, sizePx, weight, color, caseTransform, justify)
        } else {
            val animatorJson = when (animator.kind) {
                TextAnimatorKind.SEQUENTIAL_REVEAL -> makeTextRevealAnimator(
                    animator.startPct, animator.endPct, layout.em(animator.posOffsetEm),
                    builder.inFrames, builder.holdFrames, builder.outFrames
                )
                TextAnimatorKind.RANDOM_FADE -> makeRandomFadeAnimator(
                    animator.startPct, animator.endPct,
                    builder.inFrames, builder.holdFrames, builder.outFrames
                )
            }
            makeTextDataWithAnimators(
                text, cfg.fontFamily, sizePx, weight, color, caseTransform, justify,
                listOf(animatorJson)
            )
        }

        builder.addTextLayer(
            element.name, textData,
            LottieBuilder.defaultTransform(
                opacity = opacityProp(builder, cfg, element, restOpacity = alpha),
                rotation = rotationProp(builder, cfg, element),
                position = positionProp(builder, cfg, layout, element, rest, w, h)
            ),
            tt = if (mask != null) 1 else null
        )
    }

    /**
     * Builds the td=1 mask layer for a text element: text-derived or explicit size,
     * optional parallelogram skew, optional POSITION_OFFSET tracks on the mask itself
     * (the wipe pattern — mask sweeps, text stays).
     */
    private fun buildTextMask(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
        element: TextElement,
        mask: MaskRevealSpec,
        rest: SpecPoint,
        textW: Double,
        sizePx: Double,
        justify: Int
    ) {
        val extentCenterX = when (justify) {
            1 -> rest.x - textW / 2
            2 -> rest.x
            else -> rest.x + textW / 2
        }
        val flow = layout.flowSign(element.placement)
        val pad = mask.padEm?.let { layout.em(it) } ?: mask.padPx
        val maskW = mask.widthEm?.let { layout.em(it) } ?: (textW + pad)
        val maskH = mask.heightEm?.let { layout.em(it) } ?: (sizePx * mask.heightFactor)
        val skewPx = layout.em(mask.skewXEm) * flow

        val maskShape = if (skewPx == 0.0) {
            makeRect(maskW, maskH, 0.0)
        } else {
            makePath(
                listOf(
                    listOf(-maskW / 2 + skewPx, -maskH / 2),
                    listOf(maskW / 2 + skewPx, -maskH / 2),
                    listOf(maskW / 2 - skewPx, maskH / 2),
                    listOf(-maskW / 2 - skewPx, maskH / 2)
                )
            )
        }
        val maskRest = SpecPoint(
            extentCenterX + layout.em(mask.offsetXEm) * flow,
            rest.y + sizePx * mask.yOffsetFactor + layout.em(mask.offsetYEm)
        )
        val positionTrack = mask.tracks.firstOrNull { it.property == AnimProperty.POSITION_OFFSET }
        val position = if (positionTrack == null) {
            LottieBuilder.staticPropArray(maskRest.x, maskRest.y, 0.0)
        } else {
            val kfs = compileTrack(builder, cfg, positionTrack) { values ->
                val dx = offsetToPx(values.getOrElse(0) { 0.0 }, positionTrack.offsetUnit, layout, maskW, maskH)
                val dy = offsetToPx(values.getOrElse(1) { 0.0 }, positionTrack.offsetUnit, layout, maskW, maskH)
                jsonArrayOf(maskRest.x + flow * dx, maskRest.y + dy, 0.0)
            }
            LottieBuilder.animatedProp(kfs)
        }

        builder.addShapeLayer(
            "${element.name} Mask",
            buildJsonArray {
                add(makeGroup(listOf(maskShape, makeFill(listOf(1.0, 1.0, 1.0)))))
            },
            LottieBuilder.defaultTransform(position = position),
            td = 1
        )
    }

    // ---------------------------------------------------------------- shapes

    private fun backgroundPaint(layout: SpecLayoutContext, element: BackgroundElement): PaintSpec {
        if (element.paint.stroke != null) return element.paint
        if (element.borderFromConfig && layout.borderPx > 0) {
            return element.paint.copy(stroke = StrokeSpec(ColorRole.BORDER, StrokeWidthSpec.FromConfig))
        }
        return element.paint
    }

    private fun buildShape(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
        element: ElementSpec,
        size: SizeSpec,
        paint: PaintSpec,
        corner: CornerSpec,
        growFrom: GrowOrigin,
        repeat: RepeatSpec?
    ) {
        val (w, h) = layout.sizeOf(size)
        val rest = layout.resolve(element.placement).let { point ->
            // Full-canvas bands are always horizontally centered on the canvas.
            if (size is SizeSpec.CanvasWidth) SpecPoint(layout.canvasW / 2, point.y) else point
        }
        val cornerPx = layout.cornerPx(corner)
        val sizeTrack = trackFor(element, AnimProperty.RECT_SIZE)

        val shapeItem = if (element is EllipseElement) {
            makeEllipse(w, h)
        } else if (sizeTrack != null) {
            val sizeKFs = compileTrack(builder, cfg, sizeTrack) { values ->
                jsonArrayOf(values.getOrElse(0) { 1.0 } * w, values.getOrElse(1) { 1.0 } * h)
            }
            makeAnimatedRect(sizeKFs, cornerPx)
        } else {
            makeRect(w, h, cornerPx)
        }

        val items = mutableListOf(shapeItem)
        items.addAll(paintItems(builder, cfg, layout, element, paint))
        trimItem(builder, cfg, element)?.let { items.add(it) }
        repeat?.let { items.add(repeaterItem(layout, element, it)) }

        val edgeGrow = sizeTrack != null && growFrom == GrowOrigin.ALIGN_EDGE && !layout.isCenter &&
            trackFor(element, AnimProperty.POSITION_OFFSET) == null
        val transform = if (edgeGrow) {
            // Keep the alignment-side edge fixed while the rect grows inward: synthesize
            // position keyframes matching the size track's timing (pivot not applicable).
            val inward = layout.flowSign(element.placement)
            val edgeX = rest.x - inward * w / 2
            val posKFs = compileTrack(builder, cfg, sizeTrack) { values ->
                val wNow = values.getOrElse(0) { 1.0 } * w
                jsonArrayOf(edgeX + inward * wNow / 2, rest.y, 0.0)
            }
            LottieBuilder.defaultTransform(
                opacity = opacityProp(builder, cfg, element, restOpacity = 100.0),
                rotation = rotationProp(builder, cfg, element),
                position = LottieBuilder.animatedProp(posKFs),
                scale = scaleProp(builder, cfg, element, baseScale = 100.0)
            )
        } else {
            shapeTransform(builder, cfg, layout, element, rest, w, h)
        }

        builder.addShapeLayer(element.name, buildJsonArray { add(makeGroup(items)) }, transform)
    }

    private fun buildPolygon(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
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
        items.addAll(paintItems(builder, cfg, layout, element, element.paint))
        trimItem(builder, cfg, element)?.let { items.add(it) }
        element.repeat?.let { items.add(repeaterItem(layout, element, it)) }

        builder.addShapeLayer(
            element.name,
            buildJsonArray { add(makeGroup(items)) },
            shapeTransform(builder, cfg, layout, element, rest, w, h)
        )
    }

    private fun buildPath(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
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
        items.addAll(paintItems(builder, cfg, layout, element, element.paint))
        trimItem(builder, cfg, element)?.let { items.add(it) }
        element.repeat?.let { items.add(repeaterItem(layout, element, it)) }

        builder.addShapeLayer(
            element.name,
            buildJsonArray { add(makeGroup(items)) },
            shapeTransform(builder, cfg, layout, element, rest, w, h)
        )
    }

    /**
     * Common shape-layer transform. A non-zero pivot moves the layer anchor (and shifts
     * the position identically, so the rest pose is unchanged) — rotation and scale
     * tracks then orbit the pivot instead of the element center.
     */
    private fun shapeTransform(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
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
            opacity = opacityProp(builder, cfg, element, restOpacity = 100.0),
            rotation = rotationProp(builder, cfg, element),
            position = positionProp(builder, cfg, layout, element, shiftedRest, w, h),
            anchor = if (hasPivot) LottieBuilder.staticPropArray(pivotX, pivotY, 0.0) else null,
            scale = scaleProp(builder, cfg, element, baseScale = 100.0)
        )
    }

    /** Compiles a TRIM track into a Trim Paths shape item (null when the element has none). */
    private fun trimItem(builder: LottieBuilder, cfg: LottieGenConfig, element: ElementSpec): JsonObject? {
        val track = trackFor(element, AnimProperty.TRIM) ?: return null
        val startKFs = compileTrack(builder, cfg, track) { values ->
            jsonArrayOf(values.getOrElse(0) { 0.0 } * 100.0)
        }
        val endKFs = compileTrack(builder, cfg, track) { values ->
            jsonArrayOf(values.getOrElse(1) { 1.0 } * 100.0)
        }
        return makeTrimPath(startKFs = startKFs, endKFs = endKFs)
    }

    private fun repeaterItem(layout: SpecLayoutContext, element: ElementSpec, repeat: RepeatSpec): JsonObject {
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

    private fun paintItems(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
        element: ElementSpec,
        paint: PaintSpec
    ): List<JsonObject> {
        val items = mutableListOf<JsonObject>()
        val fill = paint.fill
        if (fill != null) {
            val color = layout.roleColor(fill.role)
            val alpha = layout.roleAlpha(fill.role) * fill.alphaFactor
            val gradient = fill.gradient
            items.add(
                if (gradient == null) {
                    makeFill(color, alpha)
                } else {
                    val flow = layout.flowSign(element.placement)
                    makeGradientFill(
                        color, alpha,
                        listOf(layout.em(gradient.startXEm) * flow, layout.em(gradient.startYEm)),
                        listOf(layout.em(gradient.endXEm) * flow, layout.em(gradient.endYEm))
                    )
                }
            )
        }
        val stroke = paint.stroke
        if (stroke != null) {
            val widthPx = layout.strokeWidthPx(stroke.width)
            if (widthPx > 0) {
                val color = layout.roleColor(stroke.role)
                val alpha = layout.roleAlpha(stroke.role) * stroke.alphaFactor
                val dashPx = layout.em(stroke.dashEm)
                val widthTrack = trackFor(element, AnimProperty.STROKE_WIDTH)
                if (widthTrack != null) {
                    val widthKFs = compileTrack(builder, cfg, widthTrack) { values ->
                        jsonArrayOf(values.getOrElse(0) { 1.0 } * widthPx)
                    }
                    items.add(makeAnimatedStroke(color, widthKFs, alpha, dashPx))
                } else {
                    makeStroke(color, widthPx, alpha, dashPx)?.let { items.add(it) }
                }
            }
        }
        return items
    }

    // ------------------------------------------------------- track compilation

    private fun trackFor(element: ElementSpec, property: AnimProperty): AnimTrack? =
        element.tracks.firstOrNull { it.property == property }

    private fun easingOf(kind: EasingKind): JsonObject = when (kind) {
        EasingKind.DEFAULT -> Easing.DEFAULT
        EasingKind.LINEAR -> Easing.LINEAR
    }

    /** Compiles a track's keyframes (align override applied) via the shared builder. */
    private fun compileTrack(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        track: AnimTrack,
        toValue: (List<Double>) -> JsonArray
    ): JsonArray {
        val keyframes = track.alignOverrides[cfg.align] ?: track.keyframes
        val inputs = keyframes.map { KeyframeInput(it.pct, toValue(it.values)) }
        return buildKeyframes(
            inputs, builder.inFrames, builder.holdFrames, builder.outFrames, easingOf(track.easing)
        )
    }

    private fun positionProp(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        layout: SpecLayoutContext,
        element: ElementSpec,
        rest: SpecPoint,
        elementW: Double,
        elementH: Double
    ): JsonObject {
        val track = trackFor(element, AnimProperty.POSITION_OFFSET)
            ?: return LottieBuilder.staticPropArray(rest.x, rest.y, 0.0)
        val flow = layout.flowSign(element.placement)
        val kfs = compileTrack(builder, cfg, track) { values ->
            val dx = offsetToPx(values.getOrElse(0) { 0.0 }, track.offsetUnit, layout, elementW, elementH)
            val dy = offsetToPx(values.getOrElse(1) { 0.0 }, track.offsetUnit, layout, elementW, elementH)
            jsonArrayOf(rest.x + flow * dx, rest.y + dy, 0.0)
        }
        return LottieBuilder.animatedProp(kfs)
    }

    private fun offsetToPx(
        value: Double,
        unit: OffsetUnit,
        layout: SpecLayoutContext,
        elementW: Double,
        elementH: Double
    ): Double = when (unit) {
        OffsetUnit.EM -> layout.em(value)
        OffsetUnit.ELEMENT_WIDTH -> value * elementW
        OffsetUnit.ELEMENT_HEIGHT -> value * elementH
    }

    /**
     * Layer opacity. Shapes rest at 100 (their fill already carries the role alpha);
     * text/logo rest at the field's alpha, and track values scale against it so a
     * keyframe value of 100 means "fully at the configured alpha".
     */
    private fun opacityProp(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        element: ElementSpec,
        restOpacity: Double
    ): JsonObject {
        val track = trackFor(element, AnimProperty.OPACITY)
            ?: return LottieBuilder.staticProp(restOpacity)
        val kfs = compileTrack(builder, cfg, track) { values ->
            jsonArrayOf(values.getOrElse(0) { 100.0 } * restOpacity / 100.0)
        }
        return LottieBuilder.animatedProp(kfs)
    }

    private fun rotationProp(builder: LottieBuilder, cfg: LottieGenConfig, element: ElementSpec): JsonObject? {
        val track = trackFor(element, AnimProperty.ROTATION) ?: return null
        val kfs = compileTrack(builder, cfg, track) { values ->
            jsonArrayOf(values.getOrElse(0) { 0.0 })
        }
        return LottieBuilder.animatedProp(kfs)
    }

    private fun scaleProp(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        element: ElementSpec,
        baseScale: Double
    ): JsonObject = scalePropXY(builder, cfg, element, baseScale, baseScale)

    /** Like [scaleProp] but with a per-axis base (image STRETCH sizing). */
    private fun scalePropXY(
        builder: LottieBuilder,
        cfg: LottieGenConfig,
        element: ElementSpec,
        baseScaleX: Double,
        baseScaleY: Double
    ): JsonObject {
        val track = trackFor(element, AnimProperty.SCALE)
            ?: return LottieBuilder.staticPropArray(baseScaleX, baseScaleY, 100.0)
        val kfs = compileTrack(builder, cfg, track) { values ->
            val sx = values.getOrElse(0) { 100.0 } * baseScaleX / 100.0
            val sy = values.getOrElse(1) { values.getOrElse(0) { 100.0 } } * baseScaleY / 100.0
            jsonArrayOf(sx, sy, 100.0)
        }
        return LottieBuilder.animatedProp(kfs)
    }

    companion object {
        /**
         * Loads a spec bundled as a classpath resource (shipped spec-based styles).
         * Fails fast with a clear message — a broken bundled spec must never silently
         * render an empty composition.
         */
        fun fromResource(path: String): SpecStyleGenerator {
            val stream = SpecStyleGenerator::class.java.getResourceAsStream(path)
                ?: throw IllegalStateException("Bundled style spec not found: $path")
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return SpecStyleGenerator(SpecJson.decode(text))
        }
    }
}
