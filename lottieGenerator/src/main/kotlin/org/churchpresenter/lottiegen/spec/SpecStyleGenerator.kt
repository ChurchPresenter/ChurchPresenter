package org.churchpresenter.lottiegen.spec

import org.churchpresenter.lottiegen.lottie.TextRun
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.PERCENT_SCALE
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeRandomFadeAnimator
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.makeTextDataWithAnimators
import org.churchpresenter.lottiegen.lottie.makeTextRevealAnimator
import org.churchpresenter.lottiegen.lottie.styles.StyleGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Whether this element is drawn at all for the current config: hidden by a visibility rule, or
 * hidden by an override for the alignment in use.
 */
private fun ElementSpec.isDrawn(layout: SpecLayoutContext, cfg: LottieGenConfig): Boolean =
    layout.visible(visibleWhen) && placement.alignOverrides[cfg.align]?.hidden != true

/** A text run's resting place and the measurements its mask is cut from. */
internal class PlacedText(
    val rest: SpecPoint,
    val textW: Double,
    val sizePx: Double,
    val justify: Int,
)

/**
 * Renders a [StyleSpec] through the exact same LottieBuilder/buildKeyframes pipeline
 * the hand-written styles use. One instance serves both the developer Style Editor's
 * live preview and -- for shipped spec-based styles -- the user-facing generator.
 */
class SpecStyleGenerator(private val spec: StyleSpec) : StyleGenerator {

    /** Warnings from the most recent [generate] call (editor status display). */
    var lastWarnings: List<String> = emptyList()
        private set

    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        SpecBuild(spec, builder, cfg).run()
            .also { lastWarnings = it }
    }

    companion object {
        /**
         * Loads a spec bundled as a classpath resource (shipped spec-based styles).
         * Fails fast with a clear message -- a broken bundled spec must never silently
         * render an empty composition.
         */
        fun fromResource(path: String): SpecStyleGenerator {
            val stream = SpecStyleGenerator::class.java.getResourceAsStream(path)
                ?: error("Bundled style spec not found: $path")
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return SpecStyleGenerator(SpecJson.decode(text))
        }
    }
}

/**
 * One render of one spec. Holds the three values every builder needs -- the Lottie builder, the
 * config and the resolved layout -- so they stop being the first three arguments of everything.
 */
internal class SpecBuild(
    private val spec: StyleSpec,
    private val builder: LottieBuilder,
    private val cfg: LottieGenConfig,
) {
    private val layout = SpecLayoutContext(spec, cfg)
    private val tracks = SpecTracks(builder, cfg, layout)
    private val shapes = SpecShapes(builder, layout, tracks)

    /** Draws every visible element and returns the layout's warnings. */
    fun run(): List<String> {
        for (element in spec.elements.filter { it.isDrawn(layout, cfg) }) {
            when (element) {
                is LogoElement -> buildLogo(element)
                is ImageElement -> buildImage(element)
                is TextElement -> buildText(element)
                is RectElement -> shapes.buildShape(
                    element,
                    ShapeAttrs(element.size, element.corner, element.growFrom, element.repeat),
                    element.paint,
                )
                is BackgroundElement -> shapes.buildShape(
                    element,
                    ShapeAttrs(element.size, element.corner, element.growFrom, null),
                    backgroundPaint(element),
                )
                is EllipseElement -> shapes.buildShape(
                    element,
                    ShapeAttrs(element.size, CornerSpec.None, GrowOrigin.CENTER, element.repeat),
                    element.paint,
                )
                is PolygonElement -> shapes.buildPolygon(element)
                is PathElement -> shapes.buildPath(element)
            }
        }
        return layout.warnings.toList()
    }

    private fun buildLogo(
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
                opacity = tracks.opacityProp(element, restOpacity = 100.0),
                rotation = tracks.rotationProp(element),
                position = tracks.positionProp(element, rest, w, h),
                anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                scale = tracks.scaleProp(element, baseScale)
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
                                makeRect(w, h, layout.paint.cornerPx(element.corner)),
                                makeFill(listOf(1.0, 1.0, 1.0))
                            )
                        )
                    )
                },
                LottieBuilder.defaultTransform(
                    rotation = tracks.rotationProp(element),
                    position = tracks.positionProp(element, rest, w, h)
                ),
                td = 1
            )
        }
        builder.addImageLayer(
            element.name, assetId,
            LottieBuilder.defaultTransform(
                opacity = tracks.opacityProp(element, restOpacity = 100.0 * element.alphaFactor),
                rotation = tracks.rotationProp(element),
                position = tracks.positionProp(element, rest, w, h),
                anchor = LottieBuilder.staticPropArray(element.naturalW / 2.0, element.naturalH / 2.0, 0.0),
                scale = tracks.scalePropXY(element, baseX * PERCENT_SCALE, baseY * PERCENT_SCALE)
            ),
            tt = if (matted) 1 else null
        )
    }

    // ------------------------------------------------------------------ text


    private fun buildText(
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
        alpha = layout.paint.roleAlpha(paintRole)
        color = layout.paint.roleColor(paintRole)
        val (w, h) = layout.resolveSize(element)
        val justify = layout.justify()
        val rest = layout.resolve(element.placement)

        val mask = element.maskReveal
        if (mask != null) {
            buildTextMask(element, mask, PlacedText(rest, w, sizePx, justify))
        }

        builder.addFont(cfg.fontFamily, weight)
        val animator = element.animator
        val textData = if (animator == null) {
            makeTextData(TextRun(text, cfg.fontFamily, sizePx, weight, color, caseTransform, justify))
        } else {
            val animatorJson = when (animator.kind) {
                TextAnimatorKind.SEQUENTIAL_REVEAL -> makeTextRevealAnimator(
                    animator.startPct, animator.endPct, layout.em(animator.posOffsetEm), builder
                )
                TextAnimatorKind.RANDOM_FADE -> makeRandomFadeAnimator(
                    animator.startPct, animator.endPct,
                    builder.inFrames, builder.holdFrames, builder.outFrames
                )
            }
            makeTextDataWithAnimators(
                TextRun(text, cfg.fontFamily, sizePx, weight, color, caseTransform, justify),
                listOf(animatorJson),
            )
        }

        builder.addTextLayer(
            element.name, textData,
            LottieBuilder.defaultTransform(
                opacity = tracks.opacityProp(element, restOpacity = alpha),
                rotation = tracks.rotationProp(element),
                position = tracks.positionProp(element, rest, w, h)
            ),
            tt = if (mask != null) 1 else null
        )
    }

    /**
     * Builds the td=1 mask layer for a text element: text-derived or explicit size,
     * optional parallelogram skew, optional POSITION_OFFSET tracks on the mask itself
     * (the wipe pattern — mask sweeps, text stays).
     */

    private fun buildTextMask(element: TextElement, mask: MaskRevealSpec, placed: PlacedText) {
        val rest = placed.rest
        val textW = placed.textW
        val sizePx = placed.sizePx
        val justify = placed.justify
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
            val kfs = tracks.compileTrack(positionTrack) { values ->
                val dx = tracks.offsetToPx(values.getOrElse(0) { 0.0 }, positionTrack.offsetUnit, maskW, maskH)
                val dy = tracks.offsetToPx(values.getOrElse(1) { 0.0 }, positionTrack.offsetUnit, maskW, maskH)
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


    private fun backgroundPaint(element: BackgroundElement): PaintSpec {
        if (element.paint.stroke != null) return element.paint
        if (element.borderFromConfig && layout.borderPx > 0) {
            return element.paint.copy(stroke = StrokeSpec(ColorRole.BORDER, StrokeWidthSpec.FromConfig))
        }
        return element.paint
    }

}
