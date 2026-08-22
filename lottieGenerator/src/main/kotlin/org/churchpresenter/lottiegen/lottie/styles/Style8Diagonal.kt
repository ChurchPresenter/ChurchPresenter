package org.churchpresenter.lottiegen.lottie.styles

import org.churchpresenter.lottiegen.lottie.TextRun
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextDataWithAnimators
import org.churchpresenter.lottiegen.lottie.makeTextRevealAnimator
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig

/** Padding inside the bar, in em. */
private const val PAD_X_EM = 1.0
private const val PAD_Y_EM = 0.6

/** How far the bar's top edge falls across the canvas, in em. Centre alignment is flat. */
private const val SLANT_EM = 2.5

/** The bar runs off both sides of the canvas by this much, in em, so its ends are never seen. */
private const val OVERFLOW_EM = 2.0

/** How far the filled bar sits below its top edge, in em -- negative when centred. */
private const val BG_INSET_CENTRE_EM = -0.3
private const val BG_INSET_EM = 1.5

/** Border thickness is configured 0..n and scaled onto the base size by this. */
private const val BORDER_THICKNESS_FACTOR = 0.15

/** The two lines sit close together: the gap is a small fraction of the configured spacing. */
private const val LINE_GAP_FACTOR = 0.05

/** Vertical padding is counted in whole multiples of PAD_Y_EM. */
private const val CONTENT_PAD_MULTIPLE = 5
private const val TOP_PAD_MULTIPLE = 2.0

/** Where each line's baseline sits within the content block, as a fraction of its own size. */
private const val INFO_BASELINE_FACTOR = 0.4
private const val NAME_BASELINE_FACTOR = 0.7

/** Letters rise into place from this far below, as a fraction of their line size. */
private const val REVEAL_RISE_FACTOR = 0.6

/** Timing, as percentages of the animation. */
private const val BAR_SETTLED_PCT = 40.0
private const val INFO_REVEAL_FROM_PCT = 30.0
private const val INFO_REVEAL_TO_PCT = 65.0
private const val NAME_REVEAL_FROM_PCT = 35.0
private const val NAME_REVEAL_TO_PCT = 70.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style8Diagonal's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class DiagonalGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
    val inF = builder.inFrames
    val holdF = builder.holdFrames
    val outF = builder.outFrames

    val baseSize = cfg.baseSize.toDouble()
    val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
    val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
    private val paddingX = emToPx(PAD_X_EM, baseSize)
    private val paddingY = emToPx(PAD_Y_EM, baseSize)
    private val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    private val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    val borderPx = cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR

    val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()
    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"
    private val slant = if (isCenter) 0.0 else emToPx(SLANT_EM, baseSize)

    val bgLottie = hexToLottie(cfg.bgColor)
    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    private val overflow = emToPx(OVERFLOW_EM, baseSize)
    private val barLeft = -overflow
    private val barRight = canvasW + overflow

    private val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
    private val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
    private val gap = if (!cfg.hideName && !cfg.hideInfo) lineSpacingPx * LINE_GAP_FACTOR else 0.0
    private val contentH = infoBlockH + gap + nameBlockH + paddingY * CONTENT_PAD_MULTIPLE
    private val barH = contentH + slant

    val barFinalY = canvasH - barH / 2
    val slideFrom = canvasH + barH

    private val halfW = (barRight - barLeft) / 2
    private val halfH = barH / 2

    /**
     * The four corners of the bar. The top edge falls left-to-right or right-to-left depending on
     * the alignment, and the whole top edge is then dropped by the inset so the fill sits below it.
     */
    val vertices: List<List<Double>> = run {
        val topLeftY = if (isRight) -halfH + slant else -halfH
        val topRightY = if (isRight) -halfH else -halfH + slant
        val inset = if (isCenter) emToPx(BG_INSET_CENTRE_EM, baseSize) else emToPx(BG_INSET_EM, baseSize)
        listOf(
            listOf(-halfW, topLeftY + inset),
            listOf(halfW, topRightY + inset),
            listOf(halfW, halfH),
            listOf(-halfW, halfH),
        )
    }

    /** The stroked top edge is the first two corners of the same shape. */
    val topEdge = vertices.take(2)

    val justify = when {
        isCenter -> JUSTIFY_CENTRE
        isRight -> JUSTIFY_RIGHT
        else -> JUSTIFY_LEFT
    }
    val textX = when {
        isCenter -> canvasW / 2
        isRight -> canvasW - marginHPx - paddingX
        else -> marginHPx + paddingX
    }

    private val contentTopY = -halfH + slant + paddingY * TOP_PAD_MULTIPLE
    val infoRelY = if (cfg.hideInfo) contentTopY else contentTopY + infoSizePx * INFO_BASELINE_FACTOR
    val nameRelY = infoRelY +
        (if (cfg.hideInfo) 0.0 else infoBlockH + gap) +
        (if (cfg.hideName) 0.0 else nameSizePx * NAME_BASELINE_FACTOR)

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)

    /**
     * The bar's rise. Everything drawn on the bar shares it, offset by where it sits on the bar --
     * hence the duplicated frame 0, which holds the start until the rise begins.
     */
    fun risingKeyframes(relY: Double) = keyframes(
        KeyframeInput(0.0, jsonArrayOf(canvasW / 2, slideFrom + relY, 0.0)),
        KeyframeInput(0.0, jsonArrayOf(canvasW / 2, slideFrom + relY, 0.0)),
        KeyframeInput(BAR_SETTLED_PCT, jsonArrayOf(canvasW / 2, barFinalY + relY, 0.0)),
    )

    fun risingTextKeyframes(relY: Double) = keyframes(
        KeyframeInput(0.0, jsonArrayOf(textX, slideFrom + relY, 0.0)),
        KeyframeInput(0.0, jsonArrayOf(textX, slideFrom + relY, 0.0)),
        KeyframeInput(BAR_SETTLED_PCT, jsonArrayOf(textX, barFinalY + relY, 0.0)),
    )
}

/** One of the two lines: they differ in their config half, their reveal window and their offset. */
private class DiagonalLine(g: DiagonalGeometry, isName: Boolean) {
    val layerName = if (isName) "Name" else "Info"
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val relY = if (isName) g.nameRelY else g.infoRelY
    val revealFrom = if (isName) NAME_REVEAL_FROM_PCT else INFO_REVEAL_FROM_PCT
    val revealTo = if (isName) NAME_REVEAL_TO_PCT else INFO_REVEAL_TO_PCT
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val color = if (isName) g.nameCLottie else g.infoCLottie
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val alpha = if (isName) g.cfg.nameColorAlpha else g.cfg.infoColorAlpha
}

class Style8Diagonal : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = DiagonalGeometry(builder, cfg)
        if (!cfg.hideInfo) builder.addRevealedLine(g, DiagonalLine(g, isName = false))
        if (!cfg.hideName) builder.addRevealedLine(g, DiagonalLine(g, isName = true))
        if (g.borderPx > 0) builder.addTopEdge(g)
        if (cfg.bgEnabled) builder.addBar(g)
    }
}

/** A line whose letters rise into view as the bar arrives under them. */
private fun LottieBuilder.addRevealedLine(g: DiagonalGeometry, line: DiagonalLine) {
    val animator = makeTextRevealAnimator(
        line.revealFrom, line.revealTo, -line.sizePx * REVEAL_RISE_FACTOR, this,
    )
    val data = makeTextDataWithAnimators(
        TextRun(line.text, g.cfg.fontFamily, line.sizePx, line.weight, line.color, line.transform, g.justify),
        listOf(animator),
    )
    addFont(g.cfg.fontFamily, line.weight)
    addTextLayer(
        line.layerName, data,
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.alpha),
            position = LottieBuilder.animatedProp(g.risingTextKeyframes(line.relY)),
        ),
    )
}

/** The stroked diagonal along the bar's top edge. */
private fun LottieBuilder.addTopEdge(g: DiagonalGeometry) {
    val stroke = makeStroke(g.borderLottie, g.borderPx, g.cfg.borderColorAlpha.toDouble()) ?: return
    addShapeLayer(
        "Diagonal Line",
        buildJsonArray { add(makeGroup(listOf(makePath(g.topEdge, false), stroke))) },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.risingKeyframes(0.0))),
    )
}

/** The filled bar itself. */
private fun LottieBuilder.addBar(g: DiagonalGeometry) {
    addShapeLayer(
        "Diagonal BG",
        buildJsonArray {
            add(makeGroup(listOf(makePath(g.vertices), makeFill(g.bgLottie, g.cfg.bgColorAlpha.toDouble()))))
        },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.risingKeyframes(0.0))),
    )
}
